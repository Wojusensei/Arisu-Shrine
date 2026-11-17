package web

import (
	"html/template"
	"net/http"
	"strconv"
	"strings"
	"time"

	"arisu/server/internal/model"
	"arisu/server/internal/store"
	"github.com/gin-gonic/gin"
)

// ── 神社祝部（绘马 / 奉仕会 / 神社日志） + 绘马详情与表单 ──────
// 规格来源：ShrineBoardScreen / EmaDetailScreen / NewEmaScreen /
// ChangelogDetailScreen 的逐字提取报告（结构、文案、权限门控）。

var emaTags = []string{"enhance", "bug", "questions", "tips"}
var emaStates = []string{"open", "in_progress", "resolved", "closed"}

func webEmaStateLabel(s string) string {
	switch s {
	case "open":
		return "open"
	case "in_progress":
		return "in progress"
	case "resolved":
		return "resolved"
	case "closed":
		return "closed"
	}
	return s
}

// webKnownTags 只留安卓 EmaTag 里的四个键（Models.kt:228-233）。
// 库里还留着「祈愿/杂谈/学业」这类历史数据：安卓的枚举认不出，整行按无标签渲染
// （留一枚隐形占位胶囊撑行高）；网页照抄原文就会平白多出一行胶囊。
func webKnownTags(tags []string) []string {
	out := make([]string, 0, len(tags))
	for _, t := range tags {
		for _, k := range emaTags {
			if t == k {
				out = append(out, t)
				break
			}
		}
	}
	return out
}

// webEmaStateTint 状态胶囊的语义色（StateOpen=Indigo / Progress=Gold / Resolved=Celadon / Closed=Slate）
func webEmaStateTint(s string) string {
	switch s {
	case "open":
		return "indigo"
	case "in_progress":
		return "gold"
	case "resolved":
		return "celadon"
	default:
		return "slate"
	}
}

// webTagTint 标签胶囊（enhance=Plum / bug=朱 / questions=Indigo / tips=Celadon）
func webTagTint(t string) string {
	switch t {
	case "enhance":
		return "plum"
	case "bug":
		return "vermilion"
	case "questions":
		return "indigo"
	case "tips":
		return "celadon"
	}
	return "slate"
}

type webEmaCard struct {
	ID         int64
	Title      string
	Author     model.UserBrief
	TimeText   string
	State      string
	Tags       []string
	ReplyCount int64
	Excerpt    string
}

func webEmaCardOf(e model.Ema) webEmaCard {
	return webEmaCard{
		ID: e.ID, Title: e.Title, Author: e.Author,
		TimeText: webTime(e.CreatedAt), State: e.State,
		Tags: e.Tags, ReplyCount: e.ReplyCount,
		Excerpt: mdPlain(e.Body, true),
	}
}

type shrineData struct {
	webPage
	Tab        string // ema|staff|log
	AllTags    []string
	Tag        string
	States     []string
	Sort       string
	Emas       []webEmaCard
	Staff      []model.StaffGroup
	Pending    []model.StaffApplication
	MyApp      *model.StaffApplication
	ApplyState string // ok|pending|approved|cooldown
	ApplyLabel string
	Releases   []webReleaseCard
	Update     *model.Release
	Version    string
	Total      int
	PageNum    int
	PrevOn     bool
	PrevURL    string
	NextOn     bool
	NextURL    string
	CanCurate  bool // 宫司+：待审申请卡
}

// FilterURL 分类页签换 tag 回第一页（保留状态筛选与排序）
func (d shrineData) FilterURL(tag string) string {
	return "/shrine?tab=ema&tag=" + tag + "&states=" + strings.Join(d.States, ",") + "&sort=" + d.Sort
}

// StateURL 勾选/取消一个状态筛选（多选，最多 4 个；store/emas.go 同一上限）
func (d shrineData) StateURL(state string) string {
	out := make([]string, 0, len(d.States)+1)
	toggled := false
	for _, s := range d.States {
		if s == state {
			toggled = true
			continue
		}
		out = append(out, s)
	}
	if !toggled && len(out) < 4 {
		out = append(out, state)
	}
	return "/shrine?tab=ema&tag=" + d.Tag + "&states=" + strings.Join(out, ",") + "&sort=" + d.Sort
}

// SortURL 换排序回第一页（保留 tag 与状态筛选）
func (d shrineData) SortURL(sort string) string {
	return "/shrine?tab=ema&tag=" + d.Tag + "&states=" + strings.Join(d.States, ",") + "&sort=" + sort
}

// HasState 状态 chip 的选中态
func (d shrineData) HasState(state string) bool {
	for _, s := range d.States {
		if s == state {
			return true
		}
	}
	return false
}

// PageURL 绘马列表第 n 页
func (d shrineData) PageURL(n int) string {
	return "/shrine?tab=ema&tag=" + d.Tag + "&states=" + strings.Join(d.States, ",") + "&sort=" + d.Sort + "&page=" + strconv.Itoa(n)
}

func (w *Web) webShrinePage(c *gin.Context) {
	u := c.MustGet("webUser").(*store.UserRow)
	tab := webEnum(c.DefaultQuery("tab", "ema"), []string{"ema", "staff", "log"})
	data := shrineData{
		webPage:   w.webPageWithUnread(c, webPage{Title: "神社祝部", Sub: "绘马 · 奉仕会 · 神社日志", Me: u, NavOn: "shrine", Page: "shrine", Tab: true}),
		Tab:       tab,
		AllTags:   emaTags,
		CanCurate: model.IsGuuji(u.Role),
	}
	// 顶栏筛选钮只在绘马页（ShrineBoardScreen.kt:242 TopBarIcon FilterAlt）；
	// 点开是底部弹层（:575-625「筛选绘马」），与安卓同一形态。
	if tab == "ema" {
		data.Actions = template.HTML(`<button class="top-ic" data-role="ema-filter" aria-label="筛选">` + webIconFilter + `</button>`)
	}
	switch tab {
	case "staff":
		groups, err := w.St.StaffGroups(c.Request.Context())
		if err != nil {
			data.Notice = "加载失败，点击重试"
		}
		data.Staff = groups
		if app, err := w.St.LastStaffApplication(c.Request.Context(), u.ID); err == nil {
			data.MyApp = app
			// 按钮四态（与 applyStaff 的校验同口径）：等待审核 / 已是成员 / 7 天冷却 / 可申请
			switch {
			case app.Status == "pending":
				data.ApplyState, data.ApplyLabel = "pending", "已申请，等待审核"
			case app.Status == "approved":
				data.ApplyState, data.ApplyLabel = "approved", "您已是奉仕会成员"
			case app.Status == "rejected" && time.Since(time.Unix(app.CreatedAt, 0)) < w.Cfg.Limits.StaffApplyCooldown:
				days := int((w.Cfg.Limits.StaffApplyCooldown-time.Since(time.Unix(app.CreatedAt, 0))).Seconds()+86399) / 86400
				data.ApplyState, data.ApplyLabel = "cooldown", "冷却中，"+strconv.Itoa(days)+" 天后可再次申请"
			}
		}
		if data.ApplyState == "" {
			data.ApplyState, data.ApplyLabel = "ok", "申请担任临时奉仕"
		}
		if model.IsGuuji(u.Role) {
			if pending, err := w.St.PendingStaffApplications(c.Request.Context()); err == nil {
				data.Pending = pending
			}
		}
	case "log":
		if rel, err := w.St.Releases(c.Request.Context()); err == nil {
			data.Releases = webReleaseCards(rel)
			data.Version = webAppVersion
			for i := range rel {
				r := rel[i]
				if r.Channel == "stable" && data.Update == nil {
					data.Update = &rel[i] // 检查更新用（log tab 内的按钮）
				}
			}
		} else {
			data.Notice = "加载失败，点击重试"
		}
	default:
		tag := c.Query("tag")
		if tag != "" && tag != "all" {
			data.Tag = tag
		}
		sort := "new"
		if c.Query("sort") == "old" {
			sort = "old"
		}
		data.Sort = sort
		for _, st := range strings.Split(c.Query("states"), ",") {
			st = strings.TrimSpace(st)
			if st != "" && len(data.States) < 4 {
				data.States = append(data.States, webEnum(st, emaStates))
			}
		}
		page := 1
		if p, err := strconv.Atoi(c.DefaultQuery("page", "1")); err == nil && p > 1 {
			page = p
		}
		q := store.EmaListQuery{
			Tag:    data.Tag,
			States: data.States,
			Sort:   sort,
			Limit:  20,
			Offset: (page - 1) * 20,
		}
		data.PageNum = page
		pp, err := w.St.EmasPage(c.Request.Context(), u.ID, q)
		if err != nil {
			data.Notice = "加载失败，点击重试"
		}
		for i := range pp.Items {
			data.Emas = append(data.Emas, webEmaCardOf(pp.Items[i]))
		}
		data.Total = int(pp.Total)
		if page > 1 {
			data.PrevOn = true
			data.PrevURL = data.PageURL(page - 1)
		}
		if page*20 < data.Total {
			data.NextOn = true
			data.NextURL = data.PageURL(page + 1)
		}
	}
	w.webRender(c, http.StatusOK, "shrine.html", data)
}

// ── 绘马详情 ──────────────────────────────────────────────────

type emaReplyView struct {
	ID        int64 // 模板 data-rid 与删除接口都要它；缺了会在 range 首个回复处中断整页
	Author    model.UserBrief
	TimeText  string
	BodyHTML  template.HTML
	Mine      bool
	CanDelete bool
}

type emaData struct {
	webPage
	Ema         emaView
	Replies     []emaReplyView
	CanModerate bool
	Mine        bool
	CanDelete   bool
}

type emaView struct {
	ID         int64
	Title      string
	State      string
	StateLabel string
	StateTint  string
	Tags       []string
	BodyHTML   template.HTML
	Author     model.UserBrief
	TimeText   string
	ReplyCount int64
}

func (w *Web) webEmaPage(c *gin.Context) {
	u := c.MustGet("webUser").(*store.UserRow)
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的绘马", Me: u, Page: "shrine"}))
		return
	}
	e, err := w.St.EmaByID(c.Request.Context(), u.ID, id)
	if err != nil {
		w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的绘马", Me: u, Page: "shrine"}))
		return
	}
	replies, err := w.St.EmaReplies(c.Request.Context(), u.ID, id)
	if err != nil {
		replies = nil
	}
	viewerRank := model.RoleRank(u.Role)
	canModerate := model.IsAdmin(u.Role)
	var replyViews []emaReplyView
	for i := range replies {
		r := replies[i]
		replyViews = append(replyViews, emaReplyView{
			ID:        r.ID,
			Author:    r.Author,
			TimeText:  webTime(r.CreatedAt),
			BodyHTML:  template.HTML(mdRender(r.Content)),
			Mine:      r.Author.ID == u.ID,
			CanDelete: r.Author.ID == u.ID || (canModerate && model.RoleRank(r.Author.Role) < viewerRank),
		})
	}
	data := emaData{
		webPage: w.webPageWithUnread(c, webPage{Title: "绘马详情", Sub: "#" + strconv.FormatInt(id, 10), Me: u, NavOn: "shrine", Page: "ema"}),
		Ema: emaView{
			ID: e.ID, Title: e.Title, State: e.State,
			StateLabel: webEmaStateLabel(e.State), StateTint: webEmaStateTint(e.State),
			Tags: e.Tags, BodyHTML: template.HTML(mdRender(e.Body)),
			Author: e.Author, TimeText: webTime(e.CreatedAt), ReplyCount: e.ReplyCount,
		},
		Replies:     replyViews,
		CanModerate: canModerate,
		Mine:        e.Author.ID == u.ID,
		CanDelete:   e.Author.ID == u.ID || canModerate,
	}
	w.webRender(c, http.StatusOK, "ema.html", data)
}

type emaMoreItem struct {
	ID      int64    `json:"id"`
	Title   string   `json:"title"`
	State   string   `json:"state"`
	StateCN string   `json:"state_label"`
	Tags    []string `json:"tags"`
	Excerpt string   `json:"excerpt"`
	Author  string   `json:"author"`
	Role    string   `json:"role"`
	Time    string   `json:"time"`
	Replies int64    `json:"replies"`
}

// webShrineMore 绘马列表的滚动加载片段（ShrineBoardScreen.kt:450-472）
func (w *Web) webShrineMore(c *gin.Context) {
	u := c.MustGet("webUser").(*store.UserRow)
	tag := webEnum(c.Query("tag"), emaTags)
	var states []string
	for _, st := range strings.Split(c.Query("states"), ",") {
		if st != "" && len(states) < 4 {
			states = append(states, webEnum(st, emaStates))
		}
	}
	sort := webEnum(c.DefaultQuery("sort", "new"), []string{"new", "hot"})
	page := 1
	if p, err := strconv.Atoi(c.DefaultQuery("page", "1")); err == nil && p > 1 {
		page = p
	}
	pp, err := w.St.EmasPage(c.Request.Context(), u.ID, store.EmaListQuery{
		Tag: tag, States: states, Sort: sort, Limit: 20, Offset: (page - 1) * 20,
	})
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "加载更多失败，点击重试"})
		return
	}
	items := make([]emaMoreItem, 0, len(pp.Items))
	for i := range pp.Items {
		e := pp.Items[i]
		items = append(items, emaMoreItem{
			ID: e.ID, Title: e.Title, State: e.State,
			StateCN: webEmaStateLabel(e.State), Tags: e.Tags,
			Excerpt: mdPlain(e.Body, true), Author: e.Author.Nickname, Role: e.Author.Role,
			Time: webTime(e.CreatedAt), Replies: e.ReplyCount,
		})
	}
	c.JSON(http.StatusOK, gin.H{
		"items": items, "total": pp.Total, "has_more": int64(page*20) < pp.Total,
	})
}

// ── 发起/编辑绘马 ─────────────────────────────────────────────

type emaFormData struct {
	webPage
	ID int64
	// FormTitle 而非 Title：Title 已被嵌入的 webPage.Title（页面标题）占用，
	// 同名会遮蔽它——此前 data.Title 先填绘马标题再改成「编辑绘马」，
	// 编辑页标题输入框因此永远显示「编辑绘马」。
	FormTitle string
	Body      string
	Tags      []string
	AllTags   []string
}

func (w *Web) webEmaFormPage(c *gin.Context, id int64) {
	u := c.MustGet("webUser").(*store.UserRow)
	pg := w.webPageWithUnread(c, webPage{Title: "发绘马", Sub: "提交后自动分配编号", Me: u, NavOn: "shrine", Page: "ema"})
	// 顶栏右侧「提交」（SubmitAction，NewEmaScreen.kt:361-383）：12dp 圆角实底、
	// h15/v7、labelLarge SemiBold
	pg.Actions = template.HTML(`<button class="top-submit" type="submit" form="f-ema">提交</button>`)
	data := emaFormData{webPage: pg, AllTags: emaTags}
	if id > 0 {
		e, err := w.St.EmaByID(c.Request.Context(), u.ID, id)
		if err != nil {
			w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的绘马", Me: u, Page: "shrine"}))
			return
		}
		if e.Author.ID != u.ID {
			w.webRender(c, http.StatusForbidden, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的绘马", Me: u, Page: "shrine"}))
			return
		}
		data.ID = id
		data.FormTitle = e.Title
		data.Body = e.Body
		data.Tags = e.Tags
		data.Sub = "编辑 #" + strconv.FormatInt(id, 10)
		data.Title = "编辑绘马"
	}
	w.webRender(c, http.StatusOK, "ema_form.html", data)
}

// webReleaseKinds 变更类型的展示名（ChangeKind：Models.kt:286-291）：
// 新功能=FEATURE / 修复=FIX / 优化=IMPROVE / 破坏性变更=BREAKING。
// 服务端把多个类型存成逗号串，逐段翻译，全都不认识时回落原串。
func webReleaseKinds(changeType string) []string {
	labels := map[string]string{"feature": "新功能", "fix": "修复", "improvement": "优化", "improve": "优化", "breaking": "破坏性变更"}
	out := make([]string, 0, 2)
	for _, k := range strings.Split(changeType, ",") {
		if l, ok := labels[strings.TrimSpace(k)]; ok {
			out = append(out, l)
		}
	}
	if len(out) == 0 && strings.TrimSpace(changeType) != "" {
		out = append(out, changeType)
	}
	return out
}

// webReleaseCard 日志卡：版本 / 渠道胶囊 / 日期 / 类型胶囊行 / Markdown 正文
// （ReleaseNoteCard，ShrineBoardScreen.kt:1324-1400）
type webReleaseCard struct {
	model.Release
	Kinds    []string
	BodyHTML template.HTML
}

// webReleaseCards 列表与详情共用同一套视图
func webReleaseCards(list []model.Release) []webReleaseCard {
	out := make([]webReleaseCard, 0, len(list))
	for _, r := range list {
		out = append(out, webReleaseCard{Release: r, Kinds: webReleaseKinds(r.ChangeType), BodyHTML: template.HTML(mdRender(r.Body))})
	}
	return out
}

// ── 神社日志详情 ──────────────────────────────────────────────

type logData struct {
	webPage
	Release model.Release
	Latest  bool
	Kinds   []string
}

func (w *Web) webLogPage(c *gin.Context) {
	u := c.MustGet("webUser").(*store.UserRow)
	// v 前缀可有可无；带渠道后缀的（v1.1.2-beta）按主版本号匹配
	version := strings.ToLower(strings.TrimPrefix(c.Param("version"), "v"))
	if j := strings.IndexByte(version, '-'); j >= 0 {
		version = version[:j]
	}
	rel, err := w.St.Releases(c.Request.Context())
	if err != nil {
		w.webRender(c, http.StatusInternalServerError, "missing.html", w.webPageWithUnread(c, webPage{Title: "神社日志", Me: u, Page: "shrine"}))
		return
	}
	var found *model.Release
	var latest bool
	for i := range rel {
		rv := strings.ToLower(strings.TrimPrefix(rel[i].Version, "v"))
		if j := strings.IndexByte(rv, '-'); j >= 0 {
			rv = rv[:j]
		}
		if rv == version {
			found = &rel[i]
			latest = i == 0
			break
		}
	}
	if found == nil {
		w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "没有这条日志", Me: u, Page: "shrine"}))
		return
	}
	kinds := webReleaseKinds(found.ChangeType)
	// 顶栏与安卓一致（ChangelogDetailScreen.kt:69-73）：
	// title = 版本号，subtitle = 「神社日志」。
	data := logData{
		webPage: w.webPageWithUnread(c, webPage{Title: found.Version, Sub: "神社日志", Me: u, NavOn: "shrine", Page: "shrine"}),
		Release: *found, Latest: latest, Kinds: kinds,
	}
	w.webRender(c, http.StatusOK, "log.html", data)
}

// ── 路由 ──────────────────────────────────────────────────────
// gin 的静态段 /ema/new 与通配 /ema/:id 冲突：统一走 /ema/:id，
// param 为 "new" 时渲染表单（此时必然是新建，解析交给表单分支）

func (w *Web) webEmaEntry(c *gin.Context) {
	if c.Param("id") == "new" {
		w.webEmaFormPage(c, 0)
		return
	}
	w.webEmaPage(c)
}

func (w *Web) webEmaEditEntry(c *gin.Context) {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		w.webRender(c, http.StatusNotFound, "missing.html", webPage{Title: "不见了的绘马", Me: w.viewer(c).User, Page: "shrine"})
		return
	}
	w.webEmaFormPage(c, id)
}

func (w *Web) webappRoutes2(g *gin.RouterGroup) {
	g.GET("/shrine", w.webShrinePage)
	g.GET("/shrine/more", w.webShrineMore)
	g.GET("/ema/:id", w.webEmaEntry)
	g.GET("/ema/:id/edit", w.webEmaEditEntry)
	g.GET("/log/:version", w.webLogPage)
	g.GET("/play", w.webPlayPage)
	g.GET("/me", w.webMePage)
	g.GET("/u/:no", w.webUserPage)
	g.GET("/me/follow/:mode", w.webFollowPage)
	g.GET("/post/:id/edit", w.webPostFormEntry)
	g.GET("/me/edit", w.webEditProfilePage)
	g.GET("/me/collections", w.webCollectionsPage)
	g.GET("/me/stickers", w.webStickersPage)
	g.GET("/me/drafts", w.webDraftsPage)
	g.GET("/me/history", w.webHistoryPage)
	g.GET("/me/accounts", w.webAccountsPage)
}
