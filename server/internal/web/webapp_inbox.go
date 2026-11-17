package web

import (
	"html/template"
	"net/http"
	"strconv"
	"strings"

	"arisu/server/internal/model"
	"arisu/server/internal/store"
	"github.com/gin-gonic/gin"
)

// ── 社务箱（规格=InboxScreen/NoticeDetailScreen 提取报告） ─────

// inboxData 字段名不得与 webPage 的 Title/Sub/Me/Notice/NavOn/Actions/Auth/
// AuthClass/Page/Unread 重名：外层字段会遮蔽嵌入字段，模板取到外层类型后
// 与 layout 的字符串比较冲突（int/string），整页渲染中断。
// 分页号因此叫 PageNum；未读数直接用 webPage.Unread（会话守卫统一查好，
// 与 np.Unread 同为全量未读口径）。
type inboxData struct {
	webPage
	Notices  []model.Notice
	Total    int64
	Category string
	Only     string
	PageNum  int
	PrevOn   bool
	PrevURL  string
	NextOn   bool
	NextURL  string
}

// webInboxCat 社务箱分类白名单（与 InboxTab 同口径：全部/未读/收藏/回复/系统/举报）
func webInboxCat(c *gin.Context) (cat, only string) {
	cat = c.DefaultQuery("category", "all")
	if cat != "all" && cat != "reply" && cat != "system" && cat != "report" {
		cat = "all"
	}
	only = c.Query("only")
	if only != "unread" && only != "starred" {
		only = ""
	}
	return
}

func (w *Web) webInboxPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	cat, only := webInboxCat(c)
	page, size := clampPage(c)
	// 社务箱每页 30（Shrine.kt:969 pageSize 缺省 30；store/notices.go:350 上限 50），
	// 与列表接口同一口径；clampPage 的通用缺省是 20。
	if c.Query("page_size") == "" {
		size = 30
	}
	np, err := w.St.Notices(c.Request.Context(), v.ID, cat, only, size, (page-1)*size)
	pg := w.webPageWithUnread(c, webPage{Title: "社务箱", Me: v, Page: "inbox"})
	// 顶栏右侧「全部已读」无条件常驻（InboxScreen.kt:225-251 是 TextButton，不看未读数）；
	// 空箱时点了也是空操作，但入口必须在——这是安卓的既定形制。
	pg.Actions = template.HTML(`<button class="top-txt" data-role="read-all">全部已读</button>`)
	data := inboxData{
		webPage:  pg,
		Category: cat, Only: only, PageNum: page,
	}
	if err != nil {
		data.Notice = "加载失败，点击重试"
		w.webRender(c, http.StatusInternalServerError, "inbox.html", data)
		return
	}
	data.Notices = np.Items
	// 角标走嵌入的 webPage.Unread（会话守卫查的全量未读数），这里不另设字段
	data.Total = np.Total
	data.Sub = "未读 " + strconv.FormatInt(np.Unread, 10) + " 条 · 共 " + strconv.FormatInt(np.Total, 10) + " 条"
	base := "/inbox?category=" + cat + "&only=" + only + "&"
	if page > 1 {
		data.PrevOn = true
		data.PrevURL = base + "page=" + strconv.Itoa(page-1)
	}
	if np.Total > int64(page*size) {
		data.NextOn = true
		data.NextURL = base + "page=" + strconv.Itoa(page+1)
	}
	w.webRender(c, http.StatusOK, "inbox.html", data)
}

// ── 通知详情 ──────────────────────────────────────────────────

type noticeDetailData struct {
	webPage
	// NoticeItem 而非 Notice：后者是 webPage.Notice（string，页内提示条），
	// 同名会把它遮蔽成 model.Notice 结构体——layout 的 {{if .Notice}} 恒真，
	// 每张通知详情页顶部都会把整个结构体当提示条打印出来。
	NoticeItem model.Notice
	RouteLabel string
	RouteURL   string
	// ReplyPostNo 非空表示这是「回复」类通知且目标是帖子：详情页给快捷回复框
	//（NoticeDetailScreen.kt:290-330）。存不带 arisu_ 前缀的编号，与服务端 addComment 口径一致。
	ReplyPostNo string
}

func (w *Web) webNoticePage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	id, ok := pathID(c, "id")
	if !ok {
		w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "通知详情", Me: v, Page: "inbox"}))
		return
	}
	n, err := w.St.Notice(c.Request.Context(), v.ID, id)
	if err != nil {
		w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "通知详情", Me: v, Page: "inbox"}))
		return
	}
	_ = w.St.MarkNoticeRead(c.Request.Context(), v.ID, id)
	n.Read = true
	data := noticeDetailData{
		webPage:    w.webPageWithUnread(c, webPage{Title: "通知详情", Sub: noticeKindLabel(n.Kind), Me: v, Page: "inbox"}),
		NoticeItem: *n,
	}
	data.RouteLabel, data.RouteURL = webNoticeRoute(n.TargetRoute)
	if n.Kind == "reply" {
		if p, ok := strings.CutPrefix(n.TargetRoute, "post/"); ok {
			data.ReplyPostNo = p
		}
	}
	w.webRender(c, http.StatusOK, "notice.html", data)
}

func noticeKindLabel(kind string) string {
	switch kind {
	case "reply":
		return "回复/@"
	case "up":
		return "顶帖"
	case "offer":
		return "奉纳"
	case "system":
		return "系统通知"
	case "report":
		return "举报动态"
	case "application":
		return "奉纳者申请"
	case "tracking":
		return "追踪动态"
	case "follow":
		return "关注动态"
	case "warn":
		return "警告"
	case "mute":
		return "禁言"
	}
	return "通知"
}

// webNoticeRoute 通知跳转按钮的文案与网页路由（对齐 NoticeDetailScreen）
func webNoticeRoute(route string) (label, url string) {
	if route == "" {
		return "", ""
	}
	if p, ok := strings.CutPrefix(route, "post/"); ok {
		// target_route 里的帖号两种写法都有（"104" 与 "arisu_000104"），
		// 直接拼会得到 /post/arisu_arisu_000104（2026-11-16 实测发现）。
		p = strings.TrimPrefix(p, "arisu_")
		return "前往帖子", "/post/arisu_" + padNo(p)
	}
	if p, ok := strings.CutPrefix(route, "ema/"); ok {
		return "前往绘马", "/ema/" + p
	}
	if p, ok := strings.CutPrefix(route, "user/"); ok {
		p = strings.TrimPrefix(p, "User_")
		return "前往主页", "/u/User_" + padNo(p)
	}
	if p, ok := strings.CutPrefix(route, "report/"); ok {
		// 带 focus 进队列：若该条已不存在，说明被别的管理人员处理了
		//（ReportsScreen.kt:110-116），页面会明确回一句「没有待处理的举报」
		return "前往处理举报", "/reports?focus=" + p
	}
	if route == "staff" {
		return "前往奉仕会", "/shrine?tab=staff"
	}
	if route == "changelog" {
		return "前往神社日志", "/shrine?tab=log"
	}
	return "前往查看", ""
}

func padNo(s string) string {
	for len(s) < 6 {
		s = "0" + s
	}
	return s
}

// ── 举报处理（奉仕+，规格=ReportsScreen 提取报告） ─────────────

// reportView 举报行 + 预先拼好的跳转链接。
// model.Report 的 PostID/EmaID 是 *int64，模板里直接 printf 拿到的是指针地址
// （曾渲染成 /post/arisu_137990714719120 这种必 404 的链接），所以在 handler
// 里判空并拼成现成的 href，模板只管输出。
type reportView struct {
	model.Report
	PostHref string
	EmaHref  string
}

// reportsData 同 inboxData：分页号叫 PageNum，避免遮蔽 webPage.Page（string）
type reportsData struct {
	webPage
	Items   []reportView
	Total   int64
	PageNum int
	PrevOn  bool
	PrevURL string
	NextOn  bool
	NextURL string
}

func (w *Web) webReportsPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	// 权限直接看会话里的 webUser：API 那套 requireAdmin 读的是 "viewer"（API 中间件
	// 塞的键），SSR 路由只有 "webUser"，借用它会让 /reports 对所有人 403。
	if !model.IsAdmin(v.Role) {
		w.err(c, http.StatusForbidden, "需要奉仕及以上身份")
		return
	}
	page, size := clampPage(c)
	list, total, err := w.St.Reports(c.Request.Context(), "pending", size, (page-1)*size)
	data := reportsData{
		webPage: w.webPageWithUnread(c, webPage{Title: "举报处理", Sub: "奉仕及以上可见", Me: v, Page: "inbox"}),
		PageNum: page,
	}
	if err != nil {
		data.Notice = "加载失败，点击重试"
	} else {
		items := make([]reportView, 0, len(list))
		for _, r := range list {
			rv := reportView{Report: r}
			if r.PostID != nil && *r.PostID > 0 {
				rv.PostHref = "/post/arisu_" + padNo(strconv.FormatInt(*r.PostID, 10))
			}
			if r.EmaID != nil && *r.EmaID > 0 {
				rv.EmaHref = "/ema/" + strconv.FormatInt(*r.EmaID, 10)
			}
			items = append(items, rv)
		}
		data.Items = items
		data.Total = total
	}
	// focus：从某条举报通知点进来。队列里找不到它 = 已被他人处理
	// （ReportsScreen.kt:110-116），明确提示而不是留个空列表
	if focus := c.Query("focus"); focus != "" && data.Notice == "" {
		data.Notice = "没有待处理的举报"
	}
	base := "/reports?"
	if page > 1 {
		data.PrevOn = true
		data.PrevURL = base + "page=" + strconv.Itoa(page-1)
	}
	if int64(page*size) < total {
		data.NextOn = true
		data.NextURL = base + "page=" + strconv.Itoa(page+1)
	}
	w.webRender(c, http.StatusOK, "reports.html", data)
}

// ── 拉黑名单 ──────────────────────────────────────────────────

type blockedData struct {
	webPage
	Users []model.UserBrief
}

func (w *Web) webBlockedPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	list, err := w.St.BlockedUsers(c.Request.Context(), v.ID)
	if err != nil {
		list = nil
	}
	data := blockedData{webPage: w.webPageWithUnread(c, webPage{Title: "拉黑名单", Me: v, NavOn: "me", Page: "me"}), Users: list}
	w.webRender(c, http.StatusOK, "blocked.html", data)
}

// ── 登录设备 ──────────────────────────────────────────────────

type sessionsData struct {
	webPage
	Sessions []store.SessionRow
}

func (w *Web) webSessionsPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	rows, err := w.St.Sessions(c.Request.Context(), v.ID, c.GetHeader("X-Refresh-Token"))
	if err != nil {
		rows = nil
	}
	data := sessionsData{webPage: w.webPageWithUnread(c, webPage{Title: "登录设备", Sub: "同一账号可以同时在多台设备登录", Me: v, NavOn: "me", Page: "me"}), Sessions: rows}
	w.webRender(c, http.StatusOK, "sessions.html", data)
}

// ── 设置页 ────────────────────────────────────────────────────

type webToggle struct {
	Key   string
	Label string
	On    bool
}

type settingsData struct {
	webPage
	ShowFields   []webToggle
	NotifyScopes []webToggle
	Dark         bool   // 首帧开关态（localStorage.theme，模板外另有 theme.js 兜底）
	Version      string // 「关于」组右侧版本行
}

// 展示开关（show_fields 六项，缺省视为展示）与通知范围（notify_scope 两项）
var webShowFieldDefs = []struct{ Key, Label string }{
	{"signature", "个性签名"},
	{"status", "个人状态"},
	{"blog", "个人博客网址"},
	{"custom_area", "自定义区域"},
	{"online_hours", "总在线小时数"},
	{"email", "个人邮箱"},
}

var webNotifyScopeDefs = []struct{ Key, Label string }{
	{"nickname", "关注通知（对方修改昵称）"},
	{"follow_avatar", "关注通知（对方修改头像）"},
}

func (w *Web) webSettingsPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	data := settingsData{
		webPage: w.webPageWithUnread(c, webPage{Title: "设置", Me: v, NavOn: "me", Page: "me"}),
		Version: webAppVersion,
	}
	// 开关态由 theme.js 在 <head> 里读 localStorage 决定 html.dark，服务端无从得知，
	// 这里交给 app.js 首帧同步（见 settings 段的 dark-mode 处理）。

	for _, d := range webShowFieldDefs {
		data.ShowFields = append(data.ShowFields, webToggle{Key: d.Key, Label: d.Label, On: webShowField(v.ShowFields, d.Key)})
	}
	for _, d := range webNotifyScopeDefs {
		// notify_scope 缺省：昵称通知开、头像通知关（与客户端一致）
		on := d.Key == "nickname"
		if val, ok := v.NotifyScope[d.Key]; ok {
			on = val
		}
		data.NotifyScopes = append(data.NotifyScopes, webToggle{Key: d.Key, Label: d.Label, On: on})
	}
	w.webRender(c, http.StatusOK, "settings.html", data)
}

// ── 路由 ──────────────────────────────────────────────────────

func (w *Web) webappRoutes3(g *gin.RouterGroup) {
	g.GET("/inbox", w.webInboxPage)
	g.GET("/inbox/:id", w.webNoticePage)
	g.GET("/reports", w.webReportsPage)
	g.GET("/me/blocks", w.webBlockedPage)
	g.GET("/me/sessions", w.webSessionsPage)
	g.GET("/me/settings", w.webSettingsPage)
}
