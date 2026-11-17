package web

import (
	"crypto/rand"
	"crypto/sha256"
	"embed"
	"encoding/hex"
	"errors"
	"html"
	"html/template"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"

	"arisu/server/internal/model"
	"arisu/server/internal/store"
	"arisu/server/internal/svc"
	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

// ── 网页端（SSR）：cookie 会话 + 服务端渲染 ─────────────────────
//
// 与 /api 的 Bearer 流程并存：中间件认 Authorization 头优先（安卓），
// 其次认 cookie（网页）。登录/续签/登出对网页下发 httpOnly Cookie，
// SameSite=Lax——Lax 让「从 QQ 点分享链接跳进来」的顶层 GET 导航能带上
// 会话（Strict 会把这种导航当跨站拒发，体验等于永远没登录）；
// 跨站 POST 在 Lax 下不带 cookie，再叠加 webCSRF 的 Origin 校验兜底。
//
// 透明续签是 SSR 独有的便宜：页面导航中途发现 access 过期，直接用
// refresh cookie 原地轮换、下发新 cookie 后继续渲染，用户无感。
// 轮换语义与安卓共用同一套 store 逻辑，零改动。

//go:embed templates/*.html
var webTplFS embed.FS

//go:embed app-assets
var webAssetFS embed.FS

const (
	webAccessCookie  = "arisu_web_access"
	webRefreshCookie = "arisu_web_refresh"
	webDeviceLabel   = "web"
)

// fontFiles 网页端字体的白名单（键=文件名去掉 .woff）：
// 四字重 × 两档。common＝常用字（首屏必下），rare＝GB2312 二级生僻字（按需）。
// 白名单式而不是前缀匹配，是为了不让 /app-assets/fonts/ 变成任意文件读取口。
var fontFiles = map[string]bool{
	"noto-serif-400-common": true,
	"noto-serif-500-common": true,
	"noto-serif-600-common": true,
	"noto-serif-700-common": true,
	"noto-serif-400-rare":   true,
	"noto-serif-500-rare":   true,
	"noto-serif-600-rare":   true,
	"noto-serif-700-rare":   true,
}

var (
	webTemplateOnce sync.Once
	webTemplates    *template.Template
)

func (w *Web) webTmpl() *template.Template {
	webTemplateOnce.Do(func() {
		webTemplates = template.Must(template.New("web").Funcs(template.FuncMap{
			"initial":          webInitial,
			"rolename":         model.RoleName,
			"cn":               cnCount,
			"webEmaStateLabel": webEmaStateLabel,
			"webKnownTags":     webKnownTags,
			"hasState": func(list []string, want string) bool {
				for _, v := range list {
					if v == want {
						return true
					}
				}
				return false
			},
			"webTime":          webTime,
			"webLocalUpload":   webLocalUpload,
			"deref": func(v *int64) int64 {
				if v == nil {
					return 0
				}
				return *v
			},
		}).ParseFS(webTplFS, "templates/*.html"))
		// 懒转义竞态防御：并发请求各自首次执行不同模板时，
		// html/template 的 lookupAndEscapeTemplate 会数据竞争 panic。
		// 解析完成立即逐个空执行逼出转义，之后 Execute 即并发安全。
		for _, tpl := range webTemplates.Templates() {
			_ = tpl.Execute(io.Discard, nil)
		}
	})
	return webTemplates
}

// webPageWithUnread 构造带未读数的页面基数据（侧栏角标用）
func (w *Web) webPageWithUnread(c *gin.Context, p webPage) webPage {
	if v, ok := c.Get("webUnread"); ok {
		if n, ok2 := v.(int64); ok2 {
			p.Unread = n
		}
	}
	return p
}

func (w *Web) webRender(c *gin.Context, status int, name string, data any) {
	c.Status(status)
	if err := w.webTmpl().ExecuteTemplate(c.Writer, name, data); err != nil {
		w.Log.Error("web 模板渲染失败", zap.String("tpl", name), zap.Error(err))
	}
}

// ── 会话：解析 / 透明续签 / cookie 读写 ────────────────────────

func (w *Web) webSessionSecure() bool {
	return strings.HasPrefix(strings.ToLower(w.Cfg.BaseURL), "https://")
}

func (w *Web) setWebSession(c *gin.Context, access, refresh string) {
	http.SetCookie(c.Writer, &http.Cookie{
		Name: webAccessCookie, Value: access, Path: "/",
		MaxAge:   int(w.Cfg.JWT.AccessTTL.Seconds()),
		HttpOnly: true, Secure: w.webSessionSecure(), SameSite: http.SameSiteLaxMode,
	})
	http.SetCookie(c.Writer, &http.Cookie{
		Name: webRefreshCookie, Value: refresh, Path: "/",
		MaxAge:   int(w.Cfg.JWT.RefreshTTL.Seconds()),
		HttpOnly: true, Secure: w.webSessionSecure(), SameSite: http.SameSiteLaxMode,
	})
}

func (w *Web) clearWebSession(c *gin.Context) {
	for _, name := range []string{webAccessCookie, webRefreshCookie} {
		http.SetCookie(c.Writer, &http.Cookie{
			Name: name, Value: "", Path: "/", MaxAge: -1,
			HttpOnly: true, Secure: w.webSessionSecure(), SameSite: http.SameSiteLaxMode,
		})
	}
}

// webLoadUser 账号可用性同 authRequired 一把尺：封禁/已注销/匿名化一律不可用
func (w *Web) webLoadUser(c *gin.Context, id int64) *store.UserRow {
	u, err := w.St.UserByID(c.Request.Context(), id)
	if err != nil {
		return nil
	}
	if u.Banned || u.PasswordHash == "" || u.DeactivatedAt != nil {
		return nil
	}
	return u
}

// webResolveViewer cookie → viewer；access 缺失/过期时用 refresh cookie 原地轮换续签
func (w *Web) webResolveViewer(c *gin.Context) *store.UserRow {
	if t, err := c.Cookie(webAccessCookie); err == nil && t != "" {
		if claims, perr := svc.ParseAccessToken(w.Cfg.JWT.Secret, t); perr == nil {
			if u := w.webLoadUser(c, claims.UserID); u != nil {
				return u
			}
			// token 签名对但账号不可用（封禁/注销/匿名化）：会话就此终结，
			// 不该再拿 refresh 换新——refresh 路径对不可用账号同样拒绝，落到这里清掉
			w.clearWebSession(c)
			return nil
		}
		// access 过期/损坏：落进下面的 refresh 路径透明续签
	}
	rt, rerr := c.Cookie(webRefreshCookie)
	if rerr != nil || rt == "" {
		return nil
	}
	newRefresh, terr := svc.NewRefreshToken()
	if terr != nil {
		return nil
	}
	uid, err := w.St.RotateRefreshToken(c.Request.Context(), rt, newRefresh, webDeviceLabel, int64(w.Cfg.JWT.RefreshTTL.Seconds()))
	if err != nil {
		w.clearWebSession(c)
		return nil
	}
	u := w.webLoadUser(c, uid)
	if u == nil {
		w.clearWebSession(c)
		return nil
	}
	access, aerr := svc.MakeAccessToken(w.Cfg.JWT.Secret, uid, u.Role, w.Cfg.JWT.AccessTTL)
	if aerr != nil {
		return nil
	}
	w.setWebSession(c, access, newRefresh)
	// 档案夹主键是 refresh 哈希：续期换了令牌就得把夹整夹换主键，
	// 否则下一次续期后「本机账号」列表会凭空清空（安卓 AccountVault.refreshTokens 同责）。
	// 同时把本账号档案里的密文换成新令牌，切换回来才不会拿着已作废的旧令牌撞 401。
	// 档案夹主键是「这台浏览器的固定夹 id」，不随会话令牌轮换而变——
	// 早前把它绑在 refresh 上，续期一次夹就换主键，账号列表当场清空。
	// 夹 id 只在首次登录时生成（见 webIssueSession），续期只更新夹内
	// 当前账号的令牌密文。
	ctx := c.Request.Context()
	if owner := webVaultOwnerOf(c); owner != "" {
		if sealed, serr := svc.SealToken(w.Cfg.JWT.Secret, newRefresh); serr == nil {
			_ = w.St.VaultUpdateSealed(ctx, owner, u.ID, sealed)
			// 注意：轮换后旧令牌立刻作废，档案里若还留着旧密文，
			// 下次切回这个账号必然撞 401。UpdateSealed 已按 (owner,user) 覆盖。
		}
	} else if rt != "" {
		// 老会话（升级前签发、还没有 vault cookie）续期时补一个夹，
		// 让老用户也能用上多账号，不必重新登录。
		owner := webVaultHashOf(rt)
		w.setWebVaultOwner(c, owner)
		if sealed, serr := svc.SealToken(w.Cfg.JWT.Secret, newRefresh); serr == nil {
			_ = w.St.VaultUpsert(ctx, owner, u.ID, sealed)
		}
	}
	return u
}

// webSession 受保护页面的守卫：无会话重定向登录页并记住来路
func (w *Web) webSession() gin.HandlerFunc {
	return func(c *gin.Context) {
		u := w.webResolveViewer(c)
		if u == nil {
			c.Redirect(http.StatusFound, "/login?next="+url.QueryEscape(c.Request.URL.RequestURI()))
			c.Abort()
			return
		}
		c.Set("webUser", u)
		// 侧栏/顶栏的未读角标：会话守卫统一查一次，页面数据不必各自传
		if n, err := w.St.UnreadNoticeCount(c.Request.Context(), u.ID); err == nil {
			c.Set("webUnread", n)
		}
		c.Next()
	}
}

// webSameOrigin cookie 认证的请求必须同源：Sec-Fetch-Site / Origin 双闸
// （与 webCSRF 同一套判定，抽出来给 /api 的 cookie 分支共用）
func (w *Web) webSameOrigin(c *gin.Context) bool {
	site := c.GetHeader("Sec-Fetch-Site")
	if site != "" {
		return site == "same-origin" || site == "none"
	}
	if origin := c.GetHeader("Origin"); origin != "" {
		u, err := url.Parse(origin)
		return err == nil && u.Host == c.Request.Host
	}
	return true
}

// webCSRF 跨站 POST 兜底：Sec-Fetch-Site / Origin 必须同源。
// Cookie 本身 SameSite=Lax 已挡住跨站携带，这里防的是老浏览器与显式伪造
func (w *Web) webCSRF() gin.HandlerFunc {
	return func(c *gin.Context) {
		if !w.webSameOrigin(c) {
			c.AbortWithStatus(http.StatusForbidden)
			return
		}
		c.Next()
	}
}

// webSafeNext 只认站内路径，防开放重定向。
//
// 只查 `//` 前缀不够：WHATWG URL 规范把 `\` 与 `/` 同等对待，浏览器解析
// `/\evil.com`（以及 `/\\evil.com`）得到的 origin 就是 https://evil.com——
// 反斜杠直接被当成 authority 分隔符。所以凡是反斜杠一律不放行。
// 另外拒掉换行/回车与控制字符（历史上有 UA 会截断在这个位置）。
func webSafeNext(s string) string {
	if !strings.HasPrefix(s, "/") {
		return ""
	}
	if strings.HasPrefix(s, "//") || strings.Contains(s, "\\") {
		return ""
	}
	if strings.Contains(s, "://") {
		return ""
	}
	for _, r := range s {
		if r < 0x20 || r == 0x7f {
			return ""
		}
	}
	return s
}

// webLoginThrottled 与 loginLocked 同键同阈值同渐进延迟，只是回 HTML。
// （loginLocked 本体回 JSON，直接挂在网页表单上会让人看到裸响应）
func (w *Web) webLoginThrottled(c *gin.Context, email string) bool {
	day := time.Now().Format("20060102")
	acct, err := w.RDB.Get(c.Request.Context(), "login:fail:acct:"+email+":"+day).Int()
	if err != nil && !errors.Is(err, redis.Nil) {
		w.Log.Warn("网页登录锁 Redis 读取失败", zap.Error(err))
	}
	ipn, err := w.RDB.Get(c.Request.Context(), "login:fail:ip:"+w.clientIP(c)+":"+day).Int()
	if err != nil && !errors.Is(err, redis.Nil) {
		w.Log.Warn("网页登录锁 Redis 读取失败", zap.Error(err))
	}
	if ipn >= loginFailIPMax {
		w.webRender(c, http.StatusTooManyRequests, "login.html", loginData{
			webPage: webPage{Title: "登录", Auth: true, AuthClass: "login-page", Page: "auth",
				Notice: "尝试次数过多，请 15 分钟后再试"},
		})
		return true
	}
	if acct >= loginFailAcctMax {
		last, _ := w.RDB.Get(c.Request.Context(), "login:fail:last:"+email).Int64()
		wait := (acct - loginFailAcctMax + 1) * 5
		if wait > loginFailDelayMax {
			wait = loginFailDelayMax
		}
		if time.Now().Unix()-last < int64(wait) {
			w.webRender(c, http.StatusTooManyRequests, "login.html", loginData{
				webPage: webPage{Title: "登录", Auth: true, AuthClass: "login-page", Page: "auth",
					Notice: "尝试过于频繁，请稍后再试"},
			})
			return true
		}
	}
	return false
}

// webEnum 路径/query 参数白名单（store 的 sanitizeEnum 不出包，这里同名同构）
func webEnum(v string, allowed []string) string {
	for _, a := range allowed {
		if v == a {
			return a
		}
	}
	return allowed[0]
}

func webNextOr(s, def string) string {
	return firstNonEmpty(webSafeNext(s), def)
}

// ── 认证页面 ──────────────────────────────────────────────────

// webPage 所有页面数据结构的公共页头字段。
//
// 【嵌入约定】各页面数据结构以 `webPage` 匿名嵌入复用这些字段；
// Go 的方法/字段提升规则是「外层字段优先」，外层若定义同名（且类型不同）
// 的字段会遮蔽嵌入字段——模板里 {{.Page}} 会解析成外层那个 int，layout
// 的 `eq .Page "chat"` 当场 int/string 类型不符中断渲染（inbox 页历史事故）。
// 因此新增页面数据结构时，字段名不得与以下任一重名：
// Title / Sub / Me / Notice / NavOn / Actions / Auth / AuthClass / Page / Unread / Tab
// 需要「标题」「未读」这类业务字段时请另取名（如 FormTitle、UnreadCount）。
type webPage struct {
	Title     string
	Sub       string
	Me        *store.UserRow
	Notice    string
	NavOn     string // chat|shrine|play|me：底部四栏的选中态
	Actions   template.HTML
	Auth      bool   // 认证页：藏页头、上品牌区背景装饰
	AuthClass string // login-page / register-page / reset-page：水印形制分流
	Page      string // body[data-page]：动作层 JS 的页面分流
	Unread    int64  // 侧栏社务箱角标
	// Tab 主 tab 页标记：只有它为真才渲染底部四栏。
	// 安卓的门控是「当前路由属于 ArisuTab」（ArisuApp.kt:155-166）——
	// 帖子/绘马/社务箱/设置这些从主 tab 推进来的二级页没有底栏。
	Tab bool
}

// webIconSearch 页头动作图标（ActionsHTML 里内联，省一次模板动态取名）
const webIconSearch = `<svg class="ic" viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>`

// webIconEdit 发帖入口（编辑笔，对齐 ChatBoardScreen 顶栏「发帖」）
const webIconEdit = `<svg class="ic" viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.9959.9959 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>`

// webIconFilter 祝部绘马页顶栏的筛选钮（FilterAlt，ShrineBoardScreen.kt:242）
const webIconFilter = `<svg class="ic" viewBox="0 0 24 24"><path d="M4.25 5.61C6.27 8.2 10 13 10 13v6c0 .55.45 1 1 1h2c.55 0 1-.45 1-1v-6s3.72-4.8 5.74-7.39c.51-.66.04-1.61-.79-1.61H5.04c-.83 0-1.3.95-.79 1.61z"/></svg>`

// webAppVersion 网页端展示的版本号（与安卓 releases 表里的最新 stable 同源口径）
const webAppVersion = "v1.1.2.0"

// webIconShare 游会顶栏的分享御签（PlayScreen.kt:386-394；抽中当天才出现）
const webIconShare = `<svg class="ic" viewBox="0 0 24 24"><path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z"/></svg>`

// webIconMail / webIconGear 客房顶栏两枚动作（MyRoomScreen.kt:206-218：社务箱 + 设置）
const webIconMail = `<svg class="ic" viewBox="0 0 24 24"><path d="M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z"/></svg>`

const webIconGear = `<svg class="ic" viewBox="0 0 24 24"><path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/></svg>`

type loginData struct {
	webPage
	Email string
	Next  string
	Error string
	// Recent 本机保存过的账号（最多 5 条，最近使用在前）：
	// 安卓登录页的「最近登录」行（AuthScreens.kt:308-365）。
	Recent []store.VaultAccount
}

func (w *Web) webLoginPage(c *gin.Context) {
	// park=1：「添加账号」入口（AccountScreens.kt:65「保留当前会话进登录页」）。
	// 已登录也照常渲染登录页，当前会话留在档案夹里，登录新账号后仍可免密切回。
	if w.webResolveViewer(c) != nil && c.Query("park") != "1" {
		c.Redirect(http.StatusFound, webNextOr(c.Query("next"), "/b/main"))
		return
	}
	data := loginData{
		webPage: webPage{Title: "登录", Notice: c.Query("m"), Auth: true, AuthClass: "login-page", Page: "auth"},
		Next:    webSafeNext(c.Query("next")),
	}
	// 「最近登录」：只认本机的档案夹（vault cookie），没有就不渲染这一块
	if owner := webVaultOwnerOf(c); owner != "" {
		if list, err := w.St.VaultList(c.Request.Context(), owner, 0); err == nil {
			if len(list) > 5 {
				list = list[:5]
			}
			data.Recent = list
		}
	}
	w.webRender(c, http.StatusOK, "login.html", data)
}

func (w *Web) webLoginPost(c *gin.Context) {
	email := svc.NormalizeEmail(strings.TrimSpace(c.PostForm("email")))
	password := c.PostForm("password")
	next := webSafeNext(c.PostForm("next"))
	// Auth/AuthClass 必须带：它控制认证页外壳（藏顶栏底栏侧栏）与整套认证页
	// 定位。漏了会让 auth-* 的绝对/固定定位落在普通页面外壳里，整个登录页
	// 叠成一团——2026-11-17 生产上「登录失败一次就崩」就是这个。
	fail := func(msg string) {
		w.webRender(c, http.StatusBadRequest, "login.html", loginData{
			webPage: webPage{Title: "登录", Auth: true, AuthClass: "login-page", Page: "auth"},
			Email:   email, Next: next, Error: msg,
		})
	}
	if email == "" || password == "" {
		fail("邮箱与密码不能为空")
		return
	}
	if w.webLoginThrottled(c, email) {
		return
	}
	u, err := w.St.UserByEmail(c.Request.Context(), email)
	if err != nil {
		svc.DummyBcrypt()
		w.loginFailRecord(c, email)
		fail("邮箱或密码不正确")
		return
	}
	if u.PasswordHash == "" || !svc.CheckPassword(u.PasswordHash, password) {
		svc.DummyBcrypt()
		w.loginFailRecord(c, email)
		fail("邮箱或密码不正确")
		return
	}
	if u.Banned {
		fail("账号已被封禁")
		return
	}
	if u.DeactivatedAt != nil {
		_ = w.St.CancelDeactivation(c.Request.Context(), u.ID)
		u.DeactivatedAt = nil
	}
	// 成就：参拜者（入社满 100 天），与 API 登录同一口径
	if time.Since(u.CreatedAt) >= 100*24*time.Hour {
		_ = w.St.GrantAchievement(c.Request.Context(), u.ID, "veteran")
	}
	w.loginFailClear(email)
	if err := w.webIssueSession(c, u); err != nil {
		fail("登录失败，请稍后重试")
		return
	}
	c.Redirect(http.StatusFound, firstNonEmpty(next, "/b/main"))
}

// 档案夹主键 cookie。
//
// 为什么不能直接用 refresh cookie 当夹主键：登录页那次请求往往没有旧会话
// （或者旧会话属于另一个账号），于是每次登录都开一个新夹，「本机账号」列表
// 永远只有一条。这里额外用一个独立 cookie 记住「本浏览器属于哪个夹」，
// 登录/切换时写它、续期时跟着换。
//
// 值是该浏览器主会话 refresh 令牌的哈希（与表里 owner_hash 同源），
// 不是可用凭据：拿到它只能读到这台设备的账号列表，登不进去任何账号。
const webVaultCookie = "arisu_web_vault"

// webVaultOwnerOf 本次请求该归属哪个档案夹（返回已归一的主键）：
//  1. 有 vault cookie → 用它（登录页/切换页都带着，夹能延续）
//  2. 否则用当前 refresh 令牌（首次登录，开新夹）
//
// webVaultHashOf 升级前签发的旧会话没有 vault cookie，续期时用旧 refresh 的哈希
// 现开一个夹，让老用户也能用上多账号（不必重新登录一次）。
func webVaultHashOf(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}

// 只认 vault cookie：夹 id 与会话令牌刻意解耦，没有 cookie 就是「还没有夹」，
// 由 webIssueSession 在登录成功时生成一个。
func webVaultOwnerOf(c *gin.Context) string {
	if v, err := c.Cookie(webVaultCookie); err == nil && v != "" {
		return v
	}
	return ""
}

// setWebVaultOwner 把夹主键写进 cookie（HttpOnly，与主会话同寿命）
func (w *Web) setWebVaultOwner(c *gin.Context, owner string) {
	if owner == "" {
		return
	}
	http.SetCookie(c.Writer, &http.Cookie{
		Name: webVaultCookie, Value: owner, Path: "/",
		MaxAge:   int(w.Cfg.JWT.RefreshTTL.Seconds()),
		HttpOnly: true, Secure: w.webSessionSecure(), SameSite: http.SameSiteLaxMode,
	})
}

// clearWebVaultOwner 登出时一起清
func (w *Web) clearWebVaultOwner(c *gin.Context) {
	http.SetCookie(c.Writer, &http.Cookie{
		Name: webVaultCookie, Value: "", Path: "/", MaxAge: -1,
		HttpOnly: true, Secure: w.webSessionSecure(), SameSite: http.SameSiteLaxMode,
	})
}

func (w *Web) webIssueSession(c *gin.Context, u *store.UserRow) error {
	access, err := svc.MakeAccessToken(w.Cfg.JWT.Secret, u.ID, u.Role, w.Cfg.JWT.AccessTTL)
	if err != nil {
		return err
	}
	refresh, err := svc.NewRefreshToken()
	if err != nil {
		return err
	}
	if err := w.St.SaveRefreshToken(c.Request.Context(), u.ID, refresh, int64(w.Cfg.JWT.RefreshTTL.Seconds()), webDeviceLabel); err != nil {
		return err
	}
	w.setWebSession(c, access, refresh)
	// 多账号档案（AccountVault）：登录成功即把本账号记进「这台浏览器」的档案夹。
	// 夹主键用上一枚 refresh（如果这次是「切换账号」再登录，夹会延续下去）；
	// 首次登录没有旧会话，就用新令牌自身开一个新夹。
	// 夹归属：已有 vault cookie 就延续那个夹；没有就生成一个新的随机夹 id。
	// 夹 id 与会话令牌解耦——绑令牌的话每次续期/重登都会换夹，账号列表跟着清空。
	ctx := c.Request.Context()
	owner := webVaultOwnerOf(c)
	if owner == "" {
		buf := make([]byte, 16)
		if _, rerr := rand.Read(buf); rerr != nil {
			return rerr
		}
		owner = hex.EncodeToString(buf)
		w.setWebVaultOwner(c, owner)
	}
	if sealed, serr := svc.SealToken(w.Cfg.JWT.Secret, refresh); serr == nil {
		_ = w.St.VaultUpsert(ctx, owner, u.ID, sealed)
	}
	return nil
}

type registerData struct {
	webPage
	Email    string
	CodeSent string
	Error    string
}

func (w *Web) webRegisterPage(c *gin.Context) {
	w.webRender(c, http.StatusOK, "register.html", registerData{webPage: webPage{Title: "注册", Auth: true, AuthClass: "register-page", Page: "auth"}})
}

func (w *Web) webSendCode(c *gin.Context, purpose string) (email, sent, errMsg string) {
	email = svc.NormalizeEmail(strings.TrimSpace(c.PostForm("email")))
	if !svc.ValidateEmail(email) {
		return email, "", "邮箱格式不正确"
	}
	if !w.Cfg.DevMode && !w.Mailer.Configured() {
		return email, "", "邮件服务暂不可用，请稍后再试"
	}
	code, err := svc.SendVerificationCode(c.Request.Context(), w.RDB, w.Mailer, w.Log,
		purpose, email, w.clientIP(c), w.Cfg.Limits.CodePerEmailPerDay, w.Cfg.Limits.CodePerIPPerDay)
	if err != nil {
		return email, "", err.Error()
	}
	if w.Cfg.DevMode {
		// 注册页与重置页的开发模式提示文案在安卓端本来就不同（AuthScreens.kt:174、534）
		if purpose == "register" {
			return email, "开发模式：验证码 " + code + "（服务器未配置 SMTP，直接填入即可）", ""
		}
		return email, "开发模式：验证码 " + code + "（直接填入即可）", ""
	}
	return email, "验证码已寄出，请到邮箱查收（注意垃圾箱）", ""
}

func (w *Web) webRegisterCodePost(c *gin.Context) {
	email, sent, errMsg := w.webSendCode(c, "register")
	// 页面内的发送验证码走 fetch：原地出提示 + 倒计时，输入不丢（安卓同款）。
	// 无 JS 时仍旧整页渲染，功能不降级。
	if c.GetHeader("X-Requested-With") == "fetch" {
		c.JSON(http.StatusOK, gin.H{"notice": sent, "error": errMsg, "email": email})
		return
	}
	data := registerData{webPage: webPage{Title: "注册", Auth: true, AuthClass: "register-page", Page: "auth"}, Email: email, CodeSent: sent, Error: errMsg}
	w.webRender(c, http.StatusOK, "register.html", data)
}

func (w *Web) webRegisterPost(c *gin.Context) {
	email := svc.NormalizeEmail(strings.TrimSpace(c.PostForm("email")))
	code := strings.TrimSpace(c.PostForm("code"))
	password := c.PostForm("password")
	nickname := strings.TrimSpace(c.PostForm("nickname"))
	fail := func(msg string, sent string) {
		w.webRender(c, http.StatusBadRequest, "register.html", registerData{
			webPage: webPage{Title: "注册", Auth: true, AuthClass: "register-page", Page: "auth"}, Email: email, Error: msg, CodeSent: sent,
		})
	}
	// 两次密码一致：JS 拦了前端这道，直连 API 的请求在这里再拦一次
	if c.PostForm("password_confirm") != "" && c.PostForm("password_confirm") != password {
		fail("两次输入的密码不一致", "")
		return
	}
	switch {
	case !svc.ValidateEmail(email):
		fail("邮箱格式不正确", "")
		return
	case len(password) < 8:
		fail("密码至少 8 位", "")
		return
	case len(password) > 72:
		fail("密码最多 72 字符", "")
		return
	case nickname == "" || runeLen(nickname) > 20:
		fail("昵称需为 1~20 个字符", "")
		return
	case nicknameReserved(nickname):
		fail("该昵称已被使用，请换一个", "")
		return
	}
	if err := svc.VerifyCode(c.Request.Context(), w.RDB, "register", email, code); err != nil {
		fail(err.Error(), "")
		return
	}
	hash, err := svc.HashPassword(password)
	if err != nil {
		fail("注册失败，请稍后重试", "")
		return
	}
	u, err := w.St.CreateUser(c.Request.Context(), email, hash, nickname)
	if err != nil {
		switch {
		case err == store.ErrNicknameTaken:
			fail("该昵称已被使用，请换一个", "")
		case err == store.ErrConflict:
			fail("该邮箱已注册，请直接登录", "")
		default:
			fail("注册失败，请稍后重试", "")
		}
		return
	}
	if err := w.webIssueSession(c, u); err != nil {
		fail("注册失败，请稍后重试", "")
		return
	}
	c.Redirect(http.StatusFound, "/b/main")
}

type resetData struct {
	webPage
	Email    string
	CodeSent string
	Error    string
}

func (w *Web) webResetPage(c *gin.Context) {
	w.webRender(c, http.StatusOK, "reset.html", resetData{webPage: webPage{Title: "重置密码", Auth: true, AuthClass: "reset-page", Page: "auth"}})
}

func (w *Web) webResetCodePost(c *gin.Context) {
	email, sent, errMsg := w.webSendCode(c, "reset")
	if c.GetHeader("X-Requested-With") == "fetch" {
		c.JSON(http.StatusOK, gin.H{"notice": sent, "error": errMsg, "email": email})
		return
	}
	data := resetData{webPage: webPage{Title: "重置密码", Auth: true, AuthClass: "reset-page", Page: "auth"}, Email: email, CodeSent: sent, Error: errMsg}
	w.webRender(c, http.StatusOK, "reset.html", data)
}

func (w *Web) webResetPost(c *gin.Context) {
	email := svc.NormalizeEmail(strings.TrimSpace(c.PostForm("email")))
	code := strings.TrimSpace(c.PostForm("code"))
	password := c.PostForm("password")
	if c.PostForm("password_confirm") != "" && c.PostForm("password_confirm") != password {
		w.webRender(c, http.StatusBadRequest, "reset.html", resetData{
			webPage: webPage{Title: "重置密码", Auth: true, AuthClass: "reset-page", Page: "auth"}, Email: email, Error: "两次输入的密码不一致",
		})
		return
	}
	fail := func(msg string) {
		w.webRender(c, http.StatusBadRequest, "reset.html", resetData{
			webPage: webPage{Title: "重置密码", Auth: true, AuthClass: "reset-page", Page: "auth"}, Email: email, Error: msg,
		})
	}
	if len(password) < 8 || len(password) > 72 {
		fail("密码需为 8~72 字符")
		return
	}
	if err := svc.VerifyCode(c.Request.Context(), w.RDB, "reset", email, code); err != nil {
		fail(err.Error())
		return
	}
	u, err := w.St.UserByEmail(c.Request.Context(), email)
	if err != nil {
		fail("该邮箱尚未注册")
		return
	}
	hash, err := svc.HashPassword(password)
	if err != nil {
		fail("重置失败，请稍后重试")
		return
	}
	if err := w.St.UpdatePassword(c.Request.Context(), u.ID, hash); err != nil {
		fail("重置失败，请稍后重试")
		return
	}
	_ = w.St.RevokeRefreshTokens(c.Request.Context(), u.ID)
	c.Redirect(http.StatusFound, "/login?m="+url.QueryEscape("密码已重置，用新密码登录吧"))
}

func (w *Web) webLogoutPost(c *gin.Context) {
	if rt, err := c.Cookie(webRefreshCookie); err == nil && rt != "" {
		_ = w.St.DeleteRefreshToken(c.Request.Context(), rt)
	}
	w.clearWebSession(c)
	c.Redirect(http.StatusFound, webNextOr(c.PostForm("next"), "/"))
}

// ── 阅读页 ────────────────────────────────────────────────────

var webBoards = []string{"main", "gensokyo", "dev"}

func webBoardLabel(board string) string {
	switch board {
	case "gensokyo":
		return "幻想乡闻"
	case "dev":
		return "/dev."
	default:
		return "正殿"
	}
}

// webInitial 头像占位：安卓 ArisuAvatar 在无人像时一律画人形剪影（Avatar.kt:97-104），
// 不取昵称首字。名字参数留着是为了模板调用点不必改签名。
func webInitial(name string) template.HTML {
	return template.HTML(`<svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>`)
}

// cnCount 大数计数，与客户端 countCn 同口径（234569 → "23.5万"）
func cnCount(n int64) string {
	if n < 10000 {
		return strconv.FormatInt(n, 10)
	}
	w := float64(n) / 10000.0
	var s string
	if w >= 100 {
		s = strconv.FormatInt(int64(w), 10)
	} else {
		s = strings.TrimSuffix(strconv.FormatFloat(w, 'f', 1, 64), ".0")
	}
	return s + "万"
}

var webLoc = mustWebLoc()

func mustWebLoc() *time.Location {
	loc, err := time.LoadLocation("Asia/Shanghai")
	if err != nil {
		return time.FixedZone("CST", 8*3600)
	}
	return loc
}

// webTime 与客户端 TimeFmt.epoch 逐分支同语义（app/core/util/TimeFmt.kt:17-43）：
// 未来时间与 1 分钟内「刚刚」、2-59 分钟「N 分钟前」、今天 1-6 小时「N 小时前」、
// 今天其余「今天 HH:mm」、昨天「昨天 HH:mm」、7 天内「N 天前」、
// 同年「M月d日」、跨年「yyyy年M月d日」。
// 时区口径：服务端固定 Asia/Shanghai（webLoc），安卓端用设备时区——
// 海外设备的「今天/昨天」边界会与网页不同，这是服务端既定的固定时区选择。
func webTime(epoch int64) string {
	return webTimeAt(epoch, time.Now().In(webLoc))
}

// webTimeAt 抽出可注入的 now 供单测逐分支校验（客户端 TimeFmt.now 同款设计）
func webTimeAt(epoch int64, now time.Time) string {
	if epoch <= 0 {
		return ""
	}
	now = now.In(webLoc)
	t := time.Unix(epoch, 0).In(webLoc)
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, webLoc)
	day := time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, webLoc)
	hm := t.Format("15:04")
	// Kotlin Duration.toMinutes 与 Go time.Duration 整数除法同为向零截断；
	// 同一天内的未来时间戳得到负分钟数，会一路落到「今天 HH:mm」分支（与安卓一致）
	minutes := int64(now.Sub(t) / time.Minute)
	switch {
	case day.After(today):
		return "刚刚"
	case minutes >= 0 && minutes <= 1:
		return "刚刚"
	case minutes >= 2 && minutes <= 59:
		return strconv.FormatInt(minutes, 10) + " 分钟前"
	case day.Equal(today) && minutes >= 60 && minutes <= 119:
		return "1 小时前"
	case day.Equal(today) && minutes >= 120 && minutes <= 6*60:
		return strconv.FormatInt(minutes/60, 10) + " 小时前"
	case day.Equal(today):
		return "今天 " + hm
	case day.Equal(today.AddDate(0, 0, -1)):
		return "昨天 " + hm
	case day.After(today.AddDate(0, 0, -7)):
		// 与安卓 today.toEpochDay()-date.toEpochDay() 同口径：按日历日相减
		// （两个零点都建在 webLoc，Asia/Shanghai 无夏令时，天数恒为整数）
		days := int64(today.Sub(day) / (24 * time.Hour))
		return strconv.FormatInt(days, 10) + " 天前"
	case day.Year() == today.Year():
		return strconv.Itoa(int(t.Month())) + "月" + strconv.Itoa(t.Day()) + "日"
	default:
		return strconv.Itoa(t.Year()) + "年" + strconv.Itoa(int(t.Month())) + "月" + strconv.Itoa(t.Day()) + "日"
	}
}

// webThumb 站内 /uploads/ 图按宽度取缩略档（客户端 thumbUrl 同款）；外链图原样
// webThumb 给站内上传产物拼缩略图参数。库里存的是 base_url + "/uploads/..." 的
// 绝对地址（上传接口按 base_url 拼，misc_handlers.go:578），网页端一律折回同源的
// 相对路径：反代/内网 IP 变更时不会跨源，CSP 也不必为联调环境放行额外源。
func webThumb(u string, width int) string {
	if u == "" || !strings.Contains(u, "/uploads/") {
		return u
	}
	if i := strings.Index(u, "/uploads/"); i > 0 {
		u = u[i:]
	}
	sep := "?"
	if strings.Contains(u, "?") {
		sep = "&"
	}
	return u + sep + "w=" + strconv.Itoa(width)
}

// webLocalUpload 把站内上传产物的绝对地址折成同源相对路径（模板直出 src 用）。
// 只处理带 /uploads/ 的站内地址，外链原样返回。
func webLocalUpload(u string) string {
	if u == "" {
		return u
	}
	if i := strings.Index(u, "/uploads/"); i > 0 {
		return u[i:]
	}
	return u
}

func runeLen(s string) int { return len([]rune(s)) }

func firstNonEmpty(a, b string) string {
	if a != "" {
		return a
	}
	return b
}

// webCard 帖子卡的网页视图（Cards.kt PostCard 口径），茶话列表与搜索共用
type webCard struct {
	No           string
	Title        string
	Author       model.UserBrief
	TimeText     string
	UpCount      int64
	CommentCount int64
	SaveCount    int64
	Upped        bool
	Pinned       bool
	Essence      bool
	Excerpt      string
	CoverThumb   string
	AvatarThumb  string
}

func webCardOf(p model.Post) webCard {
	excerpt := p.Intro
	if strings.TrimSpace(excerpt) == "" {
		excerpt = mdPlain(p.Body, false)
	}
	return webCard{
		No: p.No, Title: p.Title, Author: p.Author,
		TimeText: webTime(p.CreatedAt),
		UpCount:  p.UpCount, CommentCount: p.CommentCount, SaveCount: p.SaveCount,
		Upped: p.Upped, Pinned: p.Pinned, Essence: p.Essence,
		Excerpt:     excerpt,
		CoverThumb:  webThumb(p.CoverURL, 800),
		AvatarThumb: webThumb(p.Author.Avatar, 400),
	}
}

type webPager struct {
	Page, Total int
	PrevOn      bool
	PrevURL     string
	NextOn      bool
	NextURL     string
}

// webBoardMore 滚动加载的 JSON 片段（对齐安卓 PrefetchOnApproach + loadMore）
type boardMoreItem struct {
	No      string `json:"no"`
	Avatar  string `json:"avatar"`
	Title   string `json:"title"`
	Excerpt string `json:"excerpt"`
	Author  string `json:"author"`
	Role    string `json:"role"`
	Time    string `json:"time"`
	Cover   string `json:"cover"`
	Up      int64  `json:"up"`
	Comment int64  `json:"comment"`
	Pinned  bool   `json:"pinned"`
	Essence bool   `json:"essence"`
	Board   string `json:"board"`
}

func (w *Web) webBoardMore(c *gin.Context) {
	u := c.MustGet("webUser").(*store.UserRow)
	board := webEnum(c.Param("board"), webBoards)
	sort := webEnum(c.DefaultQuery("sort", "new"), []string{"new", "hot", "essence"})
	page, size := clampPage(c)
	pp, err := w.St.PostsPage(c.Request.Context(), store.PostListQuery{
		ViewerID: u.ID, Board: board, Sort: sort, Limit: size, Offset: (page - 1) * size,
	})
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "加载更多失败，点击重试"})
		return
	}
	items := make([]boardMoreItem, 0, len(pp.Items))
	for i := range pp.Items {
		// 与首屏 webCardOf 同一套口径，避免「加载更多」后的摘要/缩略图和首屏不一致
		cd := webCardOf(pp.Items[i])
		items = append(items, boardMoreItem{
			No: cd.No, Title: cd.Title, Excerpt: cd.Excerpt,
			Author: cd.Author.Nickname, Role: cd.Author.Role, Time: cd.TimeText,
			Cover: cd.CoverThumb, Avatar: cd.AvatarThumb,
			Up: cd.UpCount, Comment: cd.CommentCount,
			Pinned: cd.Pinned, Essence: cd.Essence,
			Board: webBoardLabel(pp.Items[i].Board),
		})
	}
	c.JSON(http.StatusOK, gin.H{
		"items": items, "total": pp.Total,
		"has_more": int64(page*size) < pp.Total,
	})
}

type boardData struct {
	webPage
	Board string
	Sort  string
	Posts []webCard
	Pager webPager
}

// SortURL 版块内换排序回到第一页
func (d boardData) SortURL(sort string) string {
	return "/b/" + d.Board + "?sort=" + sort
}

func (w *Web) webBoardPage(c *gin.Context) {
	u := c.MustGet("webUser").(*store.UserRow)
	board := webEnum(c.Param("board"), webBoards)
	sort := webEnum(c.DefaultQuery("sort", "new"), []string{"new", "hot", "essence"})
	page, size := clampPage(c)
	pp, err := w.St.PostsPage(c.Request.Context(), store.PostListQuery{
		ViewerID: u.ID, Board: board, Sort: sort, Limit: size, Offset: (page - 1) * size,
	})
	data := boardData{
		webPage: w.webPageWithUnread(c, webPage{Title: "有栖茶话", Sub: "有栖神社 · 社员闲聊之处", Me: u, NavOn: "chat", Page: "chat", Tab: true,
			Actions: template.HTML(`<a class="top-ic" href="/search" aria-label="搜索">` + webIconSearch + `</a>` +
				`<a class="top-ic" href="/post/new?board=` + board + `" aria-label="发帖">` + webIconEdit + `</a>`)}),
		Board: board, Sort: sort,
	}
	if err != nil {
		data.Notice = "加载失败，点击重试"
		w.webRender(c, http.StatusInternalServerError, "board.html", data)
		return
	}
	for i := range pp.Items {
		data.Posts = append(data.Posts, webCardOf(pp.Items[i]))
	}
	pager := webPager{Page: page, Total: int(pp.Total)}
	if page > 1 {
		pager.PrevOn = true
		pager.PrevURL = "/b/" + board + "?sort=" + sort + "&page=" + strconv.Itoa(page-1)
	}
	if page*size < int(pp.Total) {
		pager.NextOn = true
		pager.NextURL = "/b/" + board + "?sort=" + sort + "&page=" + strconv.Itoa(page+1)
	}
	data.Pager = pager
	w.webRender(c, http.StatusOK, "board.html", data)
}

type postView struct {
	IDNum        int64
	No           string
	Title        string
	Intro        string
	BoardLabel   string
	TimeText     string
	Edited       bool
	Pinned       bool
	Essence      bool
	CoverURL     string
	Author       model.UserBrief
	UpCount      int64
	CommentCount int64
	SaveCount    int64
	OfferTotal   int64
	Favorited    bool
	Upped        bool
	Tracked      bool
	Mine         bool
	CanDelete    bool
}

type commentView struct {
	ID          int64
	Author      model.UserBrief
	TimeText    string
	BodyHTML    template.HTML
	Nested      bool
	ReplyToName string
	Mine        bool
	CanDelete   bool
	AuthorRank  int
	Liked       bool
	LikeCount   int64
}

type postData struct {
	webPage
	Post          postView
	BodyHTML      template.HTML
	Comments      []commentView
	ViewerRank    int
	BalanceTenths int64
}

// webGroupComments 与客户端 groupedByThread 同一把尺：回复挪到顶级祖先名下，
// 祖先已被删/不可见的原位保留，总条数不变只调次序。
// viewerRank/viewerID 供行内按钮显隐（删除 = 自己或奉仕+ 且下不能管上）。
func webGroupComments(cs []model.Comment, viewerID int64, viewerRank int) []commentView {
	byID := map[int64]model.Comment{}
	for _, c := range cs {
		byID[c.ID] = c
	}
	rootOf := func(c model.Comment) int64 {
		// 与客户端 CommentGrouping.rootOf 逐轮对齐：每轮先看当前节点有没有父，
		// 没有就是根；13 轮走不满（父链 ≥13 层）按孤儿处理。
		// 旧实现耗尽后返回「第 13 层节点的 id」，而输出循环只发射顶级评论的
		// 回复桶——深链 ≥14 层的评论会整条消失（对账 #1）。
		for depth := 0; depth < 13; depth++ {
			if c.ParentID == nil {
				return c.ID
			}
			parent, ok := byID[*c.ParentID]
			if !ok {
				return 0 // 祖先不可见
			}
			c = parent
		}
		return 0 // 父链超过 13 层：与安卓 repeat(13) 同语义，原位保留
	}
	repliesByRoot := map[int64][]model.Comment{}
	orphan := map[int64]bool{}
	for _, c := range cs {
		if c.ParentID == nil {
			continue
		}
		root := rootOf(c)
		if root == 0 {
			orphan[c.ID] = true
			continue
		}
		repliesByRoot[root] = append(repliesByRoot[root], c)
	}
	var out []commentView
	appendView := func(c model.Comment, nested bool) {
		replyName := ""
		if c.ReplyToUser != nil {
			replyName = c.ReplyToUser.Nickname
		} else {
			replyName = c.Author.Nickname
		}
		var body strings.Builder
		body.WriteString(mdRender(c.Content))
		if c.ImageURL != "" && mdSafeURL(c.ImageURL) {
			// 附图地址同样折同源：库里存的是上传当时的绝对地址
			body.WriteString(`<img src="` + html.EscapeString(webLocalUpload(c.ImageURL)) + `" alt="评论附图" loading="lazy" referrerpolicy="no-referrer">`)
		}
		out = append(out, commentView{
			ID:          c.ID,
			Author:      c.Author,
			TimeText:    webTime(c.CreatedAt),
			BodyHTML:    template.HTML(body.String()),
			Nested:      nested,
			ReplyToName: replyName,
			Mine:        c.Mine || c.Author.ID == viewerID,
			AuthorRank:  model.RoleRank(c.Author.Role),
			Liked:       c.Liked,
			LikeCount:   c.LikeCount,
			CanDelete:   c.Author.ID == viewerID || (viewerRank >= model.RoleRank(model.RoleHoushi) && model.RoleRank(c.Author.Role) < viewerRank),
		})
	}
	for _, c := range cs {
		if c.ParentID == nil {
			appendView(c, false)
			for _, r := range repliesByRoot[c.ID] {
				appendView(r, true)
			}
			continue
		}
		if orphan[c.ID] {
			appendView(c, true)
		}
	}
	return out
}

func (w *Web) webPostPage(c *gin.Context) {
	u := c.MustGet("webUser").(*store.UserRow)
	if c.Param("id") == "new" {
		w.webPostFormPage(c)
		return
	}
	id, err := strconv.ParseInt(strings.TrimPrefix(c.Param("id"), "arisu_"), 10, 64)
	if err != nil || id <= 0 {
		w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的帖子", Me: u}))
		return
	}
	p, err := w.St.PostByID(c.Request.Context(), u.ID, id)
	if err != nil {
		w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的帖子", Me: u}))
		return
	}
	comments, err := w.St.CommentsOf(c.Request.Context(), u.ID, id)
	if err != nil {
		comments = nil // 正文照常渲染，评论挂了不整页报错
	}
	viewerRank := model.RoleRank(u.Role)
	data := postData{
		webPage:       w.webPageWithUnread(c, webPage{Title: "帖子详情", Sub: webBoardLabel(p.Board) + " · " + p.No, Me: u, NavOn: "chat", Page: "post"}),
		BodyHTML:      template.HTML(mdRender(p.Body)),
		ViewerRank:    viewerRank,
		BalanceTenths: u.BalanceTenths,
		Post: postView{
			IDNum: p.ID, No: p.No, Title: p.Title, Intro: p.Intro,
			BoardLabel: webBoardLabel(p.Board), TimeText: webTime(p.CreatedAt),
			Edited: p.Edited, Pinned: p.Pinned, Essence: p.Essence,
			CoverURL: webLocalUpload(p.CoverURL), Author: p.Author,
			UpCount: p.UpCount, CommentCount: p.CommentCount, SaveCount: p.SaveCount, OfferTotal: p.OfferTotal,
			Favorited: p.Favorited, Upped: p.Upped, Tracked: p.Tracked,
			Mine:      p.Author.ID == u.ID,
			CanDelete: p.Author.ID == u.ID || model.IsAdmin(u.Role),
		},
		Comments: webGroupComments(comments, u.ID, viewerRank),
	}
	w.webRender(c, http.StatusOK, "post.html", data)
}

// ── 搜索（与 ChatBoardScreen 的搜索入口同一套 store 查询） ────

type searchData struct {
	webPage
	Query    string
	Regex    bool
	Searched bool
	Users    []model.UserBrief
	Posts    []webCard
	// 错误行是否可点重试（SearchScreen.kt:203）：只有网络类错误可点，
	// 正则不合法那行不可点，所以文案和可点性都从 Notice 一起带下来。
	NoticeRetry bool
	// ExactPost / ExactUser 命中的编号（SearchScreen.kt:132-140）：
	// 关键词以 arisu_/user_ 开头，或结果里确有编号完全相等的那条时，
	// 对应条目上方挂一枚「编号精确匹配」胶囊。
	ExactPost string
	ExactUser string
}

// webSearchThrottled SSR 搜索与 /api/search 同一套 2 秒间隔闸门
// （misc_handlers.go:245 的 intervalRateLimit）。差异只在呈现：SSR 不返回
// 429 JSON，而是整页渲染 + 顶部提示。Redis 故障放行（与 mw.go:174-178 同哲学）。
func (w *Web) webSearchThrottled(c *gin.Context, userID int64) bool {
	// key 形制与 intervalRateLimitMsg（"rate:"+c.FullPath()+":"+uid）一致，
	// 只是 FullPath 换成 SSR 路由 "/search"；与 /api/search 各自独立计桶。
	key := "rate:/search:" + strconv.FormatInt(userID, 10)
	ok, err := w.RDB.SetNX(c.Request.Context(), key, 1, 2*time.Second).Result()
	if err != nil {
		println("rate-limit redis unavailable, allowing:", err.Error())
		return false
	}
	return !ok
}

func (w *Web) webSearchPage(c *gin.Context) {
	u := c.MustGet("webUser").(*store.UserRow)
	q := strings.TrimSpace(c.Query("q"))
	// 正则口径与 /api/search 一致（misc_handlers.go:258）：regex=1/true 才开
	useRegex := c.Query("regex") == "1" || c.Query("regex") == "true"
	data := searchData{webPage: w.webPageWithUnread(c, webPage{Me: u, NavOn: "chat", Page: "search"}), Query: q, Regex: useRegex}
	if q != "" {
		switch {
		case len([]rune(q)) > 100:
			// 安卓输入框超过 100 字直接拒绝继续输入（SearchScreen.kt:192-193），
			// 落不到服务端；真落到服务端时（/api/search）报的就是这句，
			// 网页端不再静默截断。
			data.webPage.Notice = "关键词太长了"
		case w.webSearchThrottled(c, u.ID):
			// 文案与 /api/search 撞限频时 Android 收到的错误体逐字相同
			// （mw.go:167 intervalRateLimit 的缺省提示，misc_handlers.go:245 调用）
			data.webPage.Notice = "发布太频繁，休息一下再试"
			data.NoticeRetry = true
		default:
			data.Searched = true
			posts, err := w.St.SearchPosts(c.Request.Context(), u.ID, q, useRegex, 30)
			if err != nil {
				// 与安卓 SearchScreen 的文案一致（SearchScreen.kt:217）：正则不合法
				// 单独提示，且不再退化成包含匹配；用户结果也一并丢弃，避免
				// 「正则错了但社员列表还在」这种安卓不会出现的结果
				if useRegex {
					data.webPage.Notice = "正则表达式不合法。请社员注意，正则不是必须，如若掌握不熟悉可直接搜索关键词"
				} else {
					data.webPage.Notice = "搜索失败，点击重试"
					data.NoticeRetry = true
				}
				break
			}
			for i := range posts {
				data.Posts = append(data.Posts, webCardOf(posts[i]))
			}
			if users, err := w.St.SearchUsers(c.Request.Context(), u.ID, q, 20); err == nil {
				data.Users = users
			}
			// 编号精确匹配（SearchScreen.kt:132-140）：兜底口径与安卓同序——
			// 先看结果里有没有编号完全相等的，没有再看关键词本身是不是编号形态
			lower := strings.ToLower(q)
			upper := strings.ToUpper(q)
			for i := range posts {
				if strings.EqualFold(posts[i].No, q) {
					data.ExactPost = posts[i].No
					break
				}
			}
			if data.ExactPost == "" && strings.HasPrefix(lower, "arisu_") {
				data.ExactPost = lower
			}
			for i := range data.Users {
				if strings.EqualFold(data.Users[i].No, q) {
					data.ExactUser = data.Users[i].No
					break
				}
			}
			if data.ExactUser == "" && strings.HasPrefix(upper, "USER_") {
				data.ExactUser = upper
			}
		}
	}
	w.webRender(c, http.StatusOK, "search.html", data)
}

// ── 路由与静态资产 ────────────────────────────────────────────

// webCSP 网页端页面统一内容安全策略：无内联脚本、脚本只认同源；
// 图片放行 https 外链（正文图床/外链图既定规则）与站内；样式放行内联
// （模板里的少量 style 属性）。SSR 无框架载荷，策略可以从紧起步
func (w *Web) webCSP() gin.HandlerFunc {
	// 上传产物按 base_url 拼绝对地址下发（misc_handlers.go:578）。生产 base_url 是
	// https，光靠 img-src 的 https: 就够；本机联调 base_url 是 http://内网IP:8080，
	// 不放行这个源的话封面/头像/正文图全被 CSP 拦成空白。
	origin := ""
	if u, err := url.Parse(strings.TrimSuffix(w.Cfg.BaseURL, "/")); err == nil && u.Host != "" {
		origin = " " + u.Scheme + "://" + u.Host
	}
	// blob: 给图片裁剪器用（选中的本地图以 blob URL 解码，绝不外发）；
	// 预览与上传产物都是同源或 blob，不需要更多来源
	imgSrc := "img-src 'self' https: data: blob:" + origin
	connectSrc := "connect-src 'self' blob:" + origin
	return func(c *gin.Context) {
		c.Header("Content-Security-Policy",
			"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "+
				imgSrc+"; "+connectSrc+"; frame-ancestors 'none'; base-uri 'self'")
		c.Header("X-Content-Type-Options", "nosniff")
		c.Header("Referrer-Policy", "no-referrer-when-downgrade")
		c.Next()
	}
}

func (w *Web) webappRoutes(r *gin.Engine) {
	web := r.Group("", w.webCSP())
	web.GET("/app-assets/app.css", func(c *gin.Context) {
		b, _ := webAssetFS.ReadFile("app-assets/app.css")
		c.Header("Cache-Control", "max-age=600")
		c.Data(http.StatusOK, "text/css; charset=utf-8", b)
	})
	// 站点图标：layout 的 <link rel="icon" href="/logo_red.png"> 一直指向不存在的
	// 路由，每个页面都打一条 404（浏览器控制台可见，日志里因为静态资产不进访问日志
	// 所以只在浏览器侧显示）。图标走 embed，与 CSS/JS 同一套缓存策略。
	// 浏览器默认还会盲取 /favicon.ico：同源返回同一枚图标，省掉一条必然 404
	web.GET("/favicon.ico", func(c *gin.Context) {
		c.Redirect(http.StatusMovedPermanently, "/logo_red.png")
	})
	web.GET("/logo_red.png", func(c *gin.Context) {
		b, err := webAssetFS.ReadFile("app-assets/logo_red.png")
		if err != nil {
			c.Status(http.StatusNotFound)
			return
		}
		c.Header("Cache-Control", "max-age=86400")
		c.Data(http.StatusOK, "image/png", b)
	})
	// 动作层脚本：所有按钮/弹层/请求都靠它；缺这条路由则交互全哑
	web.GET("/app-assets/theme.js", func(c *gin.Context) {
		b, err := webAssetFS.ReadFile("app-assets/theme.js")
		if err != nil {
			c.Status(http.StatusNotFound)
			return
		}
		c.Header("Cache-Control", "max-age=600")
		c.Data(http.StatusOK, "application/javascript; charset=utf-8", b)
	})
	web.GET("/app-assets/app.js", func(c *gin.Context) {
		b, err := webAssetFS.ReadFile("app-assets/app.js")
		if err != nil {
			c.Status(http.StatusNotFound)
			return
		}
		c.Header("Cache-Control", "max-age=600")
		c.Data(http.StatusOK, "application/javascript; charset=utf-8", b)
	})
	// 御签卡二维码矩阵（qrcode 库的 core 打成的 IIFE，只在游会页加载）。
	// 内容随版本号变更，产物换代就换 URL，可以长缓存。
	web.GET("/app-assets/qr.js", func(c *gin.Context) {
		b, err := webAssetFS.ReadFile("app-assets/qr.js")
		if err != nil {
			c.Status(http.StatusNotFound)
			return
		}
		c.Header("Cache-Control", "public, max-age=31536000, immutable")
		c.Data(http.StatusOK, "application/javascript; charset=utf-8", b)
	})
	web.GET("/app-assets/fonts/:name", func(c *gin.Context) {
		name := c.Param("name")
		// 四字重（400/500/600/700）× 两档（common/rare）都要放行：
		// 少放行任何一档，对应字重就会回落系统字体、与安卓不一致。
		// common＝常用字（首屏必下），rare＝GB2312 二级字（由 CSS unicode-range 按需触发）。
		if !fontFiles[strings.TrimSuffix(name, ".woff")] {
			c.Status(http.StatusNotFound)
			return
		}
		b, _ := webAssetFS.ReadFile("app-assets/fonts/" + name)
		// 字体文件名带内容版本（app.css/image 里 ?v=2）：换代就是换 URL，
		// 所以可以放心 immutable 一年。否则每次发布都要靠 LRU 重新校验，
		// 7.7MB 的回访代价全落在首字延迟上。
		c.Header("Cache-Control", "public, max-age=31536000, immutable")
		c.Data(http.StatusOK, "font/woff", b)
	})

	// 站点根：论坛接管首页（落地页让到 /home）。两个目标都是硬编码常量，
	// 不读任何查询参数，因此不存在开放重定向面。
	web.GET("/", func(c *gin.Context) {
		if w.webResolveViewer(c) != nil {
			c.Redirect(http.StatusFound, "/b/main")
			return
		}
		c.Redirect(http.StatusFound, "/login")
	})

	// 认证（无需会话）
	web.GET("/login", w.webLoginPage)
	web.POST("/login", w.webCSRF(), w.webLoginPost)
	web.GET("/register", w.webRegisterPage)
	web.POST("/register/code", w.webCSRF(), w.webRegisterCodePost)
	web.POST("/register", w.webCSRF(), w.webRegisterPost)
	web.GET("/reset", w.webResetPage)
	web.POST("/reset/code", w.webCSRF(), w.webResetCodePost)
	web.POST("/reset", w.webCSRF(), w.webResetPost)
	web.POST("/logout", w.webCSRF(), w.webLogoutPost)

	// 登录后的阅读页
	guarded := web.Group("", w.webSession())
	guarded.GET("/b/:board", w.webBoardPage)
	// 滚动加载用：同样的列表，返回 JSON 片段（安卓是滚动到底自动加载，
	// 网页用 JS 追加；不返回整页 HTML 以省带宽）
	guarded.GET("/b/:board/more", w.webBoardMore)
	guarded.GET("/post/:id", w.webPostPage)
	guarded.GET("/search", w.webSearchPage)
	w.webappRoutes2(guarded)
	w.webappRoutes3(guarded)
}
