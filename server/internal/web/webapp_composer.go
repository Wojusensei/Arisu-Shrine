package web

import (
	"html/template"
	"net/http"
	"strconv"
	"strings"

	"arisu/server/internal/model"
	"arisu/server/internal/store"
	"arisu/server/internal/svc"
	"github.com/gin-gonic/gin"
)

// ── 发帖/编辑帖子（SSR 表单 + JS 走 /api） ─────────────────────

type postFormData struct {
	webPage
	ID int64
	// FormTitle 而非 Title：Title 已被嵌入的 webPage.Title（页面标题）占用，
	// 同名会遮蔽它——此前 handler 里 data.Title 先填帖子标题再改成「编辑帖子」，
	// 写的是同一个字段，编辑页标题输入框因此永远显示「编辑帖子」。
	FormTitle string
	Intro     string
	Body      string
	Board      string
	BoardLabel string // 「发送到板块」右侧的当前板块名（NewPostScreen.kt:678）
	CoverURL   string
	Boards     []string
}

// webPostFormEntry /post/new 与 /post/:id/edit 共用入口（gin 通配段冲突规避）
func (w *Web) webPostFormEntry(c *gin.Context) {
	if c.Param("id") == "new" {
		w.webPostFormPage(c)
		return
	}
	w.webPostFormPage(c)
}

func (w *Web) webPostFormPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	data := postFormData{
		webPage: w.webPageWithUnread(c, webPage{Title: "撰写帖子", Sub: "当前板块：正殿", Me: v, NavOn: "chat", Page: "post"}),
		Boards:  postBoards,
		Board:   c.DefaultQuery("board", "main"),
	}
	// /post/new 路由合并到 /post/:id：param 为 "new" 时是新建，跳过编辑预填。
	// 帖号容忍 arisu_ 前缀——与 /post/:id 详情页同一口径，否则
	// /post/arisu_000934/edit 会被当非法 ID 打 404。
	if idStr := c.Param("id"); idStr != "" && idStr != "new" {
		id, err := strconv.ParseInt(strings.TrimPrefix(idStr, "arisu_"), 10, 64)
		if err != nil || id <= 0 {
			w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的帖子", Me: v, Page: "post"}))
			return
		}
		p, err := w.St.PostByID(c.Request.Context(), v.ID, id)
		if err != nil {
			w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的帖子", Me: v, Page: "post"}))
			return
		}
		if p.Author.ID != v.ID {
			w.webRender(c, http.StatusForbidden, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的帖子", Me: v, Page: "post"}))
			return
		}
		data.ID = id
		data.FormTitle = p.Title
		data.Intro = p.Intro
		data.Body = p.Body
		data.Board = p.Board
		data.CoverURL = p.CoverURL
		data.Sub = "编辑 " + p.No
		data.Title = "编辑帖子"
	}
	// 板块中文标签给模板
	data.BoardLabel = webBoardLabel(data.Board)
	// 顶栏右侧「发布 / 保存」（PublishButton，NewPostScreen.kt:541-555）：
	// 80×40 圆角胶囊、实底 primary、labelLarge；编辑态文案是「保存」
	label := "发布"
	if data.ID > 0 {
		label = "保存"
	}
	data.Actions = template.HTML(`<button class="top-publish" type="submit" form="f-post">` + label + `</button>`)
	w.webRender(c, http.StatusOK, "post_form.html", data)
}

// ── 编辑资料（SSR 表单 + JS 走 PATCH /api/me） ─────────────────

type editProfileData struct {
	webPage
	Nickname    string
	Signature   string
	StatusEmoji string
	StatusText  string
	BlogURL     string
	CustomMD    string
	CustomCSS   string
	Email       string
	// 双头像（EditProfileScreen.kt:320-355）：固定头像恒显示；活跃头像在
	// active_avatar_enabled 时替换所有位置的显示头像。
	FixedAvatar    string
	ActiveAvatar   string
	ActiveAvatarOn bool
	Role           string   // 头像角色环色
	StatusEmojis   []string // 16 枚可选状态表情（EditProfileScreen.kt:98-101）
}

// webStatusEmojis 安卓的 16 枚状态表情，顺序照抄（EditProfileScreen.kt:98-101）
var webStatusEmojis = []string{
	"🎉", "⚙️", "🔧", "⛩️", "💻", "📚", "☕", "🌸",
	"😄", "😊", "😴", "🤔", "😎", "🥺", "🚀", "🌙",
}

func (w *Web) webEditProfilePage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	me := v.ToMe()
	data := editProfileData{
		webPage:        w.webPageWithUnread(c, webPage{Title: "编辑资料", Me: v, NavOn: "me", Page: "me"}),
		Role:           me.User.Role,
		StatusEmojis:   webStatusEmojis,
		Nickname:       me.User.Nickname,
		Signature:      me.User.Signature,
		StatusEmoji:    me.User.StatusEmoji,
		StatusText:     me.User.StatusText,
		BlogURL:        me.User.BlogURL,
		CustomMD:       me.User.CustomMarkdown,
		CustomCSS:      me.User.CustomCSS,
		Email:          me.Email,
		FixedAvatar:    webLocalUpload(me.FixedAvatarURL),
		ActiveAvatar:   webLocalUpload(me.ActiveAvatarURL),
		ActiveAvatarOn: me.ActiveAvatarEnabled,
	}
	w.webRender(c, http.StatusOK, "edit_profile.html", data)
}

// ── 草稿箱 / 浏览足迹（纯本地数据，页面壳交给 JS 渲染） ─────────
//
// 安卓两者都存在 filesDir 的 JSON 文件里（DraftStore.kt / HistoryStore.kt），
// 不经后端；网页对应 localStorage 的 arisu:drafts / arisu:history。
// 服务端只发一张空壳，列表由 app.js 渲染，因此这里不需要任何 store 调用。

type localListData struct {
	webPage
	// 计数由客户端回填（「共 N 条」在副标题上），服务端只知道页面标题
	LoadingHint string
}

func (w *Web) webDraftsPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	w.webRender(c, http.StatusOK, "drafts.html", localListData{
		webPage: w.webPageWithUnread(c, webPage{Title: "草稿箱", Me: v, NavOn: "me", Page: "me"}),
	})
}

func (w *Web) webHistoryPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	w.webRender(c, http.StatusOK, "history.html", localListData{
		webPage: w.webPageWithUnread(c, webPage{Title: "浏览足迹", Me: v, NavOn: "me", Page: "me"}),
	})
}

// ── 收藏夹管理 ────────────────────────────────────────────────// ── 收藏夹管理 ────────────────────────────────────────────────

type collectionsData struct {
	webPage
	Collections []webCollectionCard
	// OpenID 当前展开的收藏夹（?open=），0 表示都收起。
	// 安卓的展开是行内就地拉取（CollectionsScreen.kt:200-260），网页用一次导航换同一效果，
	// 展开态因此不进 JS 状态，刷新/返回都不丢。
	OpenID int64
}

// webCollectionCard 一张收藏夹卡：夹信息 + 展开后的最近 4 条帖子（CollectionsScreen.kt:270-380）
type webCollectionCard struct {
	model.Collection
	Open   bool
	Failed bool
	Posts  []webCard
}

// webCollectionCardOf 把首页/接口里的封面折成同源相对路径（CSP 只认同源与 https）
// uid 是查看者 id：网页 SSR 的登录态在 webUser 里，w.viewer 只在 /api 中间件里才有。
func (w *Web) webCollectionCards(c *gin.Context, uid int64, list []model.Collection, openID int64) []webCollectionCard {
	out := make([]webCollectionCard, 0, len(list))
	for _, c0 := range list {
		card := webCollectionCard{Collection: c0, Open: openID != 0 && c0.ID == openID}
		if card.Open {
			// 只取最近 4 条：卡内列表最多显示 4 行（:340 take(4)）
			items, _, err := w.St.PostsByRelation(c.Request.Context(), uid, "favorited", uid, c0.ID, 4, 0)
			if err != nil {
				// 拉不到内容不能伪装成「这个收藏夹还没有内容」（:357-392 的 loadFailed）
				card.Failed = true
			} else {
				for _, p := range items {
					card.Posts = append(card.Posts, webCardOf(p))
				}
			}
		}
		out = append(out, card)
	}
	return out
}

func (w *Web) webCollectionsPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	// 与 /api/me/collections 同口径：先保证默认收藏夹存在
	_, _ = w.St.EnsureDefaultCollection(c.Request.Context(), v.ID)
	list, err := w.St.Collections(c.Request.Context(), v.ID)
	if err != nil {
		list = nil
	}
	var openID int64
	if s0 := c.Query("open"); s0 != "" {
		openID, _ = strconv.ParseInt(s0, 10, 64)
	}
	pg := w.webPageWithUnread(c, webPage{Title: "收藏夹管理", Me: v, NavOn: "me", Page: "me"})
	// 顶栏右上「新建」（CollectionsScreen.kt:143-154）：primary 实底胶囊、h14/v6、labelMedium
	pg.Actions = template.HTML(`<button class="top-new" data-role="new-coll-open">新建</button>`)
	data := collectionsData{
		webPage:     pg,
		Collections: w.webCollectionCards(c, v.ID, list, openID),
		OpenID:      openID,
	}
	w.webRender(c, http.StatusOK, "collections.html", data)
}

// ── 表情包管理 ────────────────────────────────────────────────

type stickersData struct {
	webPage
	Items []model.Sticker
	Limit int64
}

func (w *Web) webStickersPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	items, err := w.St.Stickers(c.Request.Context(), v.ID)
	if err != nil {
		items = nil
	}
	data := stickersData{
		webPage: w.webPageWithUnread(c, webPage{Title: "表情包设置", Sub: "长按拖动排序 · 点「添加」收新表情", Me: v, NavOn: "me", Page: "me"}),
		Items:   items, Limit: store.StickerLimit,
	}
	w.webRender(c, http.StatusOK, "stickers.html", data)
}

// ── 切换账号（对齐安卓 AccountManageScreen.kt + AccountVault.kt） ──
//
// 网页端的会话令牌在 HttpOnly cookie 里，JS 读不到，所以档案落在服务端表
// web_account_vault：一台浏览器一份夹（主键 = 当前 refresh 令牌哈希），
// 令牌以 AES-GCM 密文保存（svc.SealToken），拖库者没有 jwt.secret 解不开。

type accountsData struct {
	webPage
	Accounts []store.VaultAccount
}

func (w *Web) webAccountsPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	data := accountsData{webPage: w.webPageWithUnread(c, webPage{
		Title: "切换账号", Sub: "本机保存的账号可免密快速切换", Me: v, NavOn: "me", Page: "me",
	})}
	// 夹主键来自 vault cookie（登录/续期时写入）；拿不到就只显示当前账号自己
	if owner := webVaultOwnerOf(c); owner != "" {
		if list, lerr := w.St.VaultList(c.Request.Context(), owner, v.ID); lerr == nil {
			data.Accounts = list
		}
	}
	if len(data.Accounts) == 0 {
		data.Accounts = []store.VaultAccount{{
			UserID: v.ID, No: model.FormatUserNo(v.ID), Nickname: v.Nickname,
			Email: v.Email, Role: v.Role, Current: true,
		}}
	}
	w.webRender(c, http.StatusOK, "accounts.html", data)
}

// webAccountSwap 免密切换到档案里的另一个账号。
//
// 做法与安卓同构：把目标账号存档里的 refresh 令牌装回 cookie；access 现场签发。
// 当前账号的会话不销毁——它的令牌还在档案里，可以随时切回来（安卓同语义）。
func (w *Web) webAccountSwap(c *gin.Context) {
	// 这两个是 API 路由（authRequired 只设 "viewer"；"webUser" 是 SSR 守卫的键），
	// 借用 webUser 会在同一处 panic——与 /reports 那次同型。
	v := w.viewer(c)
	target, ok := pathID(c, "id")
	if !ok || target == v.ID {
		w.bad(c, "参数错误")
		return
	}
	owner := webVaultOwnerOf(c)
	if owner == "" {
		w.bad(c, "当前会话已失效，请重新登录")
		return
	}
	sealed, err := w.St.VaultSealedToken(c.Request.Context(), owner, target)
	if err != nil || sealed == "" {
		w.bad(c, "该账号需要重新登录，请用密码登录后再试")
		return
	}
	token := svc.OpenToken(w.Cfg.JWT.Secret, sealed)
	if token == "" {
		w.bad(c, "该账号需要重新登录，请用密码登录后再试")
		return
	}
	// 令牌可能已被服务端轮换/吊销：先确认它还在有效期内
	u, err := w.St.UserByRefreshToken(c.Request.Context(), token)
	if err != nil || u == nil || u.Banned || u.DeactivatedAt != nil || u.PasswordHash == "" {
		// 不删档案：令牌失效通常是「这台设备的会话被轮换过」，
		// 用户重新用密码登一次就会把新令牌写回同一条档案。删档会让那条记录
		// 从列表里消失，用户连「这个账号我存过」都看不到了。
		w.bad(c, "该账号需要重新登录，请用密码登录后再试")
		return
	}
	access, aerr := svc.MakeAccessToken(w.Cfg.JWT.Secret, u.ID, u.Role, w.Cfg.JWT.AccessTTL)
	if aerr != nil {
		w.err(c, http.StatusInternalServerError, "签发会话失败")
		return
	}
	// 把目标账号的令牌装回 cookie。不轮换它——轮换会让档案里那枚作废，
	// 连切两次就撞 401（安卓也不轮换：切换只是把档案里的令牌装回槽位）。
	w.setWebSession(c, access, token)
	// 最近使用时间刷新，列表按它倒序
	if s2, serr := svc.SealToken(w.Cfg.JWT.Secret, token); serr == nil {
		_ = w.St.VaultUpdateSealed(c.Request.Context(), owner, target, s2)
		_ = w.St.VaultUpsert(c.Request.Context(), owner, target, s2)
	}
	c.JSON(http.StatusOK, gin.H{"ok": true, "next": "/me"})
}

// webAccountRemove 从档案夹里移除某账号（不动它的其他设备会话）
func (w *Web) webAccountRemove(c *gin.Context) {
	v := w.viewer(c)
	target, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	if target == v.ID {
		w.bad(c, "不能移除正在使用的账号")
		return
	}
	owner := webVaultOwnerOf(c)
	if owner == "" {
		w.bad(c, "当前会话已失效，请重新登录")
		return
	}
	// 顺手把这台设备上该账号的会话作废，否则档案删了会话还挂着
	if sealed, serr := w.St.VaultSealedToken(c.Request.Context(), owner, target); serr == nil && sealed != "" {
		if token := svc.OpenToken(w.Cfg.JWT.Secret, sealed); token != "" {
			_ = w.St.DeleteRefreshToken(c.Request.Context(), token)
		}
	}
	if err := w.St.VaultRemove(c.Request.Context(), owner, target); err != nil {
		w.err(c, http.StatusInternalServerError, "移除失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}
