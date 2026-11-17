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

// ── 神社游会（求签 / 御签榜 / 在线统计，规格=PlayScreen 提取报告） ──

type playData struct {
	webPage
	Luck       int // 0=未抽或未知（与客户端三态口径：未知时不渲染可点按钮）
	LuckKnown  bool
	Leader     *webLeaderboard
	Stats      *model.OnlineStats
	Streak     int
	TotalDays  int
	TotalHours int
	TotalMins  int
	Band       string // 抽中档位：凶/末吉/…/大吉（SoftPill，PlayScreen.kt:530-535）
}

// webLeaderboard 御签榜的网页视图。安卓把 Items 前三摆上领奖台、第 4 起铺全宽行，
// 自己不在前十时再单列一段「我的位置」（前/后各 2 人）。这些切分在 handler 里做完，
// 模板只管输出，避免模板里写算术。
type webLeaderboard struct {
	HasAny               bool // 今日有签才渲染整块（空榜不渲染，PlayScreen.kt:564）
	First, Second, Third *webLeaderRow
	Rest                 []webLeaderRow
	Nearby               []webLeaderRow
	ShowNearby           bool
}

type webLeaderRow struct {
	Nickname string
	Role     string
	Avatar   string // 已折成同源相对路径
	Luck     int
	Rank     int
	IsMe     bool
	Band     string // 凶/末吉/小吉/中吉/吉/大吉（luckBand：PlayScreen.kt:1020-1027）
}

// webLuckBand 与客户端 luckBand 逐档同值（PlayScreen.kt:1020-1027）
func webLuckBand(v int) string {
	switch {
	case v <= 10:
		return "凶"
	case v <= 30:
		return "末吉"
	case v <= 50:
		return "小吉"
	case v <= 70:
		return "中吉"
	case v <= 90:
		return "吉"
	}
	return "大吉"
}

func webLeaderboardOf(b *store.OmikujiBoard) *webLeaderboard {
	if b == nil {
		return nil
	}
	conv := func(rows []store.OmikujiLeaderRow) []webLeaderRow {
		out := make([]webLeaderRow, 0, len(rows))
		for _, r := range rows {
			out = append(out, webLeaderRow{
				Nickname: r.Nickname, Role: r.Role, Avatar: webThumb(r.Avatar, 200),
				Luck: r.Luck, Rank: r.Rank, IsMe: r.IsMe, Band: webLuckBand(r.Luck),
			})
		}
		return out
	}
	items := conv(b.Items)
	lb := &webLeaderboard{Rest: nil, Nearby: conv(b.Nearby)}
	if len(items) > 0 {
		lb.First = &items[0]
	}
	if len(items) > 1 {
		lb.Second = &items[1]
	}
	if len(items) > 2 {
		lb.Third = &items[2]
	}
	if len(items) > 3 {
		lb.Rest = items[3:]
	}
	// 自己在前十之内时前十已含本人与邻居，服务端也会把 Nearby 收空；
	// 两道门都过才单列「我的位置」（PlayScreen.kt:632-643）
	lb.ShowNearby = b.Me != nil && b.Me.Rank > 10 && len(lb.Nearby) > 0
	lb.HasAny = len(items) > 0
	return lb
}

func (w *Web) webPlayPage(c *gin.Context) {
	u := c.MustGet("webUser").(*store.UserRow)
	data := playData{webPage: w.webPageWithUnread(c, webPage{Title: "神社游会", Sub: "求签 · 看板 · 全端统计", Me: u, NavOn: "play", Page: "play", Tab: true})}
	drawn, luck, err := w.St.OmikujiToday(c.Request.Context(), u.ID)
	if err == nil {
		data.LuckKnown = true
		if drawn {
			data.Luck = int(luck)
		}
	}
	if board, err := w.St.OmikujiLeaderboard(c.Request.Context(), u.ID); err == nil {
		data.Leader = webLeaderboardOf(board)
	}
	// 结果卡的档位印记（SoftPill，PlayScreen.kt:530-535）：分档同 luckBand
	if data.Luck > 0 {
		data.Band = webLuckBand(data.Luck)
	}
	if st, err := w.St.OnlineStats(c.Request.Context()); err == nil {
		data.Stats = st
		// 全端总在线时长（PlayScreen.kt:712-760）：天/时/分三段显示
		data.TotalDays = int(st.TotalMinutes / 1440)
		data.TotalHours = int((st.TotalMinutes % 1440) / 60)
		data.TotalMins = int(st.TotalMinutes % 60)
	}
	// 顶栏分享入口：结果出来后常驻（PlayScreen.kt:386-394），滚动到哪儿都不用回头找
	if data.Luck > 0 {
		data.Actions = template.HTML(`<button class="top-ic" data-role="share-fortune" aria-label="分享御签">` + webIconShare + `</button>`)
	}
	if hist, err := w.St.OmikujiHistory(c.Request.Context(), u.ID, 30); err == nil {
		data.Streak = webStreakDays(hist, mustWebLoc())
	}
	w.webRender(c, http.StatusOK, "play.html", data)
}

// ── 我的客房（规格=MyRoomScreen 提取报告） ────────────────────

// meRoomData 客房数据。字段名一律避开 webPage 的 Title/Sub/Me/Notice/NavOn/
// Actions/Auth/AuthClass/Page/Unread —— 同名外层字段会把嵌入字段遮蔽成别的
// 类型，layout 里 {{if .Notice}} / eq .NavOn "me" 直接类型冲突，整页渲染中断。
type meRoomData struct {
	webPage
	Owner         *model.User // 客房主人（同样避开 webPage.Me）
	Tiles         []model.Tile
	HeatWeeks     []webHeatWeek // 热度图按周切好的列（周一起始，最后一格是今天）
	Achievements  []webAchievement
	Counters      map[string]int // tracked/liked/favorited 篇数
	Tab           string         // posts|tracked|liked|favorited（ProfileTab 四个内容标签）
	Posts         []webCard
	TodayMin      int
	TodayPosts    int
	TodayCom      int
	Following     int
	Follower      int
	PostCount     int
	Balance       string
	OnlineHours   int
	OnlineHidden  bool
	ActiveAvatar  bool
	UnlockedCount int
	CustomHTML    template.HTML
	CustomStyle   string
	HasCustom     bool
	Email         string
	Nickname      string
	Signature     string
	StatusText    string
	BlogURL       string
}

// webHeatWeek 热度图一列：自周一起的 7 格，缺的格 Level=-1（渲染成空槽）
type webHeatWeek struct {
	Days []webHeatDay
}

type webHeatDay struct {
	Level int
	Date  string
	Empty bool
}

type webAchievement struct {
	Code     string
	Name     string
	Desc     string
	Unlocked bool
}

// webAchievementCatalog 与客户端 achievementCatalog 同一份目录
var webAchievementCatalog = []struct{ Code, Name, Desc string }{
	{"founder", "神社出仕", "前 100 位入社的社员"},
	{"first_post", "第一帖", "发出了第一篇帖子"},
	{"hot_author", "捧帖", "单帖顶数达到 20"},
	{"first_ema", "挂绘马", "提交第一个绘马"},
	{"omikuji", "上上签", "第一次求签求到 100 分"},
	{"chatter", "社务常客", "评论数达到 50"},
	{"veteran", "参拜者", "入社满 100 天"},
	{"night_owl", "夜行性", "凌晨三点还在神社"},
}

func (w *Web) webMePage(c *gin.Context) {
	u := c.MustGet("webUser").(*store.UserRow)
	ctx := c.Request.Context()
	meModel := u.ToMe().User
	tab := webEnum(c.DefaultQuery("tab", "posts"), []string{"posts", "tracked", "liked", "favorited"})
	page := w.webPageWithUnread(c, webPage{Title: "我的客房", Sub: model.FormatUserNo(u.ID), Me: u, NavOn: "me", Page: "me", Tab: true})
	// 安卓顶栏两枚图标（MyRoomScreen.kt:206-218）：社务箱（带未读角标）+ 设置。
	// Actions 是纯 HTML 串，用不了模板 define，角标自己拼；首帧数字就位，
	// 之后由 app.js 心跳按 /api/me/unread 接管（与 layout 的 unread-badge 同口径）。
	page.Actions = template.HTML(`<a class="top-ic" href="/inbox" aria-label="社务箱">` + webIconMail + webUnreadBadgeHTML(page.Unread) + `</a>` +
		`<a class="top-ic" href="/me/settings" aria-label="设置">` + webIconGear + `</a>`)
	data := meRoomData{
		webPage: page,
		Owner:   &meModel, Tab: tab,
		Nickname: u.Nickname, Signature: u.Signature, StatusText: u.StatusText,
		Email: u.Email, BlogURL: u.BlogURL, ActiveAvatar: u.ActiveAvatarEnabled,
	}
	if err := w.St.FillUserCounts(ctx, &meModel, u.ID); err == nil {
		data.Following = int(meModel.FollowingCount)
		data.Follower = int(meModel.FollowerCount)
	}
	data.PostCount = int(meModel.PostCount)
	if tiles, err := w.St.Tiles(ctx, u.ID); err == nil {
		data.Tiles = tiles
	}
	if st, err := w.St.OnlineStats(ctx); err == nil {
		_ = st
	}
	var minutes, posts, comments int
	if err := w.St.Pool.QueryRow(ctx, `SELECT COALESCE(sum(minutes),0), COALESCE(sum(posts),0), COALESCE(sum(comments),0)
		FROM activity_days WHERE user_id=$1 AND day=`+store.OmikujiTodayExpr, u.ID).Scan(&minutes, &posts, &comments); err == nil {
		data.TodayMin, data.TodayPosts, data.TodayCom = minutes, posts, comments
	}
	unlocked := map[string]bool{}
	if list, err := w.St.Achievements(ctx, u.ID); err == nil {
		for _, a := range list {
			unlocked[a.Code] = true
		}
	}
	for _, cat := range webAchievementCatalog {
		data.Achievements = append(data.Achievements, webAchievement{
			Code: cat.Code, Name: cat.Name, Desc: cat.Desc, Unlocked: unlocked[cat.Code],
		})
		if unlocked[cat.Code] {
			data.UnlockedCount++
		}
	}
	data.HeatWeeks = webHeatWeeks(data.Tiles)
	// 内容 tab 的帖子：安卓 CollapsiblePostList 一次性拿全量再折叠分页
	// （Shrine.postsByAuthor 单次 page_size=50），这里同样取第一页 50 条
	if items, _, err := w.St.PostsByRelation(ctx, u.ID, tab, u.ID, 0, 50, 0); err == nil {
		for i := range items {
			data.Posts = append(data.Posts, webCardOf(items[i]))
		}
	}
	data.Counters = map[string]int{}
	for _, key := range []string{"tracked", "liked", "favorited"} {
		// 与客户端同一把尺（MyRoomScreen.kt:123-135,164-177）：安卓取
		// Shrine.postsByAuthor 第一页列表的 size 当计数，page_size 缺省 50
		// （ApiService.kt:74），超过 50 条也只显示 50。这里同样取第一页 50 条
		// 再取 len，而不是服务端 total——total 会透出真实条数与安卓不同。
		items, _, err := w.St.PostsByRelation(ctx, u.ID, key, u.ID, 0, 50, 0)
		if err == nil {
			data.Counters[key] = len(items)
		} else {
			data.Counters[key] = 0
		}
	}
	if u.CustomMarkdown != "" || u.CustomCSS != "" {
		css := webParseSafeCss(u.CustomCSS)
		data.CustomStyle = customAreaStyle(css)
		data.CustomHTML = template.HTML(mdRender(u.CustomMarkdown))
		data.HasCustom = u.CustomMarkdown != ""
	}
	data.Balance = yenText(u.BalanceTenths)
	data.OnlineHours = int(u.OnlineMinutes / 60)
	_, hidden := u.ShowFields["online_hours"]
	data.OnlineHidden = hidden && !u.ShowFields["online_hours"]
	w.webRender(c, http.StatusOK, "me.html", data)
}

// ── 他人主页（规格=UserProfileScreen 提取报告） ───────────────

type userPageData struct {
	webPage
	User        *model.User
	Tiles       []model.Tile
	HeatWeeks   []webHeatWeek
	CanCurate   bool
	CanModerate bool
	CanAssign   bool
	Following   bool
	FollowsMe   bool
	Blocked     bool
	Banned      bool
	Muted       bool
	IsSelf      bool // 看的是自己的公开主页（UserProfileScreen.kt:739 isSelf）
	Tab         int  // 0 帖子 / 1 追踪 / 2 喜欢 / 3 收藏（仅 IsSelf 时可用）
	Posts       []webCard
	PostCount   int
	CustomHTML  template.HTML
	CustomStyle string
	HasCustom   bool
	OnlineHours int
	// 注意：这里没有 OnlineHidden。安卓他人主页（UserProfileScreen.kt:720）读
	// user.showFields["online_hours"]，但他人资料 DTO UserDto（ApiDtos.kt:20-39）
	// 根本不含 show_fields，map 恒为空、该分支不可达；同时服务端 ToPublic
	// （store/users.go:125-127）已把隐藏者的 OnlineMinutes 清零，所以安卓在
	// 他人视角实际显示的是 "0"。网页端照此对齐（模板直接渲染 0，不再显示已隐藏）。
}

// parseSafeCssValues Go 版：白名单 4 键（与 app.css/编辑预览同一把尺），越界丢弃
type safeCSS struct {
	Radius     string
	Background string
	Color      string
	FontSize   string
}

func webParseSafeCss(css string) safeCSS {
	var out safeCSS
	for _, d := range strings.Split(css, ";") {
		i := strings.Index(d, ":")
		if i <= 0 {
			continue
		}
		k := strings.ToLower(strings.TrimSpace(d[:i]))
		v := strings.TrimSpace(d[i+1:])
		if v == "" {
			continue
		}
		switch k {
		case "border-radius", "font-size":
			if n := cssNum(v); n >= 0 && n <= 400 {
				if k == "border-radius" {
					out.Radius = strconv.Itoa(n) + "px"
				} else {
					out.FontSize = strconv.Itoa(n) + "px"
				}
			}
		case "background", "background-color", "color":
			c := cssHex(v)
			if c != "" {
				if k == "color" {
					out.Color = c
				} else {
					out.Background = c
				}
			}
		}
	}
	return out
}

func cssNum(v string) int {
	n := 0
	for _, r := range v {
		if r < '0' || r > '9' {
			// 允许一个小数点：粗化取整
			if r == '.' {
				continue
			}
			return -1
		}
		n = n*10 + int(r-'0')
	}
	return n
}

// cssHex 3/6 位 hex 归一成 #RRGGBB（8 位按 CSS RRGGBBAA 折成不透明色不够严谨，网页端只收 3/6 位）
func cssHex(v string) string {
	h := strings.TrimPrefix(strings.TrimSpace(v), "#")
	if len(h) == 3 {
		h = string([]byte{h[0], h[0], h[1], h[1], h[2], h[2]})
	}
	if len(h) != 6 {
		return ""
	}
	for _, c := range h {
		if !strings.ContainsRune("0123456789abcdefABCDEF", c) {
			return ""
		}
	}
	return "#" + strings.ToLower(h)
}

func (w *Web) webUserPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	id, ok := pathID(c, "no")
	if !ok {
		w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的社员", Me: v, NavOn: "me", Page: "user"}))
		return
	}
	tu, err := w.St.UserByID(c.Request.Context(), id)
	if err != nil {
		w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的社员", Me: v, Page: "user"}))
		return
	}
	if tu.Banned && !model.IsAdmin(v.Role) {
		w.webRender(c, http.StatusNotFound, "missing.html", w.webPageWithUnread(c, webPage{Title: "不见了的社员", Me: v, Page: "user"}))
		return
	}
	ctx := c.Request.Context()
	pub := tu.ToPublic()
	_ = w.St.FillUserCounts(ctx, pub, v.ID)
	following, _ := w.St.IsFollowing(ctx, v.ID, id)
	followsMe, _ := w.St.IsFollowing(ctx, id, v.ID)
	blocked, _ := w.St.Blocks(ctx, v.ID, id)
	blockedBy := tu.Banned // 占位：屏蔽视角在 authRequired 已拦
	_ = blockedBy
	muted := v.ID == id || model.IsAdmin(v.Role)
	// 看自己的公开主页：与安卓 isSelf 同语义（UserProfileScreen.kt:739）。
	// 这时标题用「我的帖子」并给四个标签页；他人只展示「TA 的帖子」。
	isSelf := v.ID == id
	rel := "posts"
	switch strings.TrimSpace(c.Query("tab")) {
	case "1", "tracked":
		if isSelf {
			rel = "tracked"
		}
	case "2", "liked":
		if isSelf {
			rel = "liked"
		}
	case "3", "favorited":
		if isSelf {
			rel = "favorited"
		}
	}
	tabIdx := 0
	switch rel {
	case "tracked":
		tabIdx = 1
	case "liked":
		tabIdx = 2
	case "favorited":
		tabIdx = 3
	}
	posts, total, err := w.St.PostsByRelation(ctx, v.ID, rel, id, 0, 20, 0)
	if err != nil {
		posts = nil
	}
	var cards []webCard
	for i := range posts {
		cards = append(cards, webCardOf(posts[i]))
	}
	var tiles []model.Tile
	if bl, _ := w.St.Blocks(ctx, v.ID, id); !bl {
		tiles, _ = w.St.Tiles(ctx, id)
	}
	data := userPageData{
		webPage: w.webPageWithUnread(c, webPage{
			Title: tu.Nickname, Sub: model.RoleName(tu.Role) + " · " + model.FormatUserNo(id),
			Me: v, NavOn: "", Page: "user",
		}),
		User:        pub,
		Tiles:       tiles,
		HeatWeeks:   webHeatWeeks(tiles),
		CanCurate:   model.IsGuuji(v.Role),
		CanModerate: model.IsAdmin(v.Role),
		CanAssign:   v.Role == model.RoleKannushi,
		Following:   following,
		FollowsMe:   followsMe,
		Blocked:     blocked,
		Banned:      tu.Banned,
		Muted:       muted && tu.MutedUntilActive() != nil,
		IsSelf:      isSelf,
		Tab:         tabIdx,
		Posts:       cards,
		PostCount:   int(total),
		// 取 ToPublic 后的 pub 而非原始 tu：隐藏开关的裁剪发生在 ToPublic
		// （store/users.go:125-127 把 OnlineMinutes 清零），安卓拿到的就是这个
		// 清零值（UserDto.online_minutes），于是他人视角显示 0。用 tu 会漏出真值。
		OnlineHours: int(pub.OnlineMinutes / 60),
	}
	data.CustomStyle, data.HasCustom = "", tu.CustomMarkdown != "" || tu.CustomCSS != ""
	if webShowField(tu.ShowFields, "custom_area") {
		css := webParseSafeCss(tu.CustomCSS)
		data.CustomStyle = customAreaStyle(css)
		data.CustomHTML = template.HTML(mdRender(tu.CustomMarkdown))
		data.HasCustom = tu.CustomMarkdown != ""
	}
	w.webRender(c, http.StatusOK, "user.html", data)
}

func customAreaStyle(css safeCSS) string {
	var b strings.Builder
	b.WriteString("border-radius:" + orDefault(css.Radius, "14px") + ";")
	b.WriteString("background:" + orDefault(css.Background, "var(--neu-base-high)") + ";")
	if css.Color != "" {
		b.WriteString("color:" + css.Color + ";")
	}
	if css.FontSize != "" {
		b.WriteString("font-size:" + css.FontSize + ";")
	}
	return b.String()
}

func orDefault(v, def string) string {
	if v == "" {
		return def
	}
	return v
}

// ── 关注列表 ──────────────────────────────────────────────────

// webFollowUser 关注/粉丝列表的一行（FollowListScreen.kt:179-223）：
// 昵称 + 签名 + 关注态 + 是否本人（本人那行不出关注钮）。
type webFollowUser struct {
	model.UserBrief
	Signature string
	IsMe      bool
}

type followListData struct {
	webPage
	Mode  string // following|followers
	Users []webFollowUser
}

func (w *Web) webFollowPage(c *gin.Context) {
	v := c.MustGet("webUser").(*store.UserRow)
	mode := webEnum(c.Param("mode"), []string{"following", "followers"})
	list, err := w.St.FollowList(c.Request.Context(), v.ID, mode == "followers")
	if err != nil {
		list = nil
	}
	users := make([]webFollowUser, 0, len(list))
	for _, u := range list {
		// UserBrief 不带签名，而行里要显示签名（:203-212），逐个补一次
		sig := ""
		if full, ferr := w.St.UserByID(c.Request.Context(), u.ID); ferr == nil && full != nil {
			sig = full.Signature
		}
		users = append(users, webFollowUser{UserBrief: u, Signature: sig, IsMe: u.ID == v.ID})
	}
	data := followListData{
		webPage: w.webPageWithUnread(c, webPage{Title: "我的关注", Me: v, NavOn: "me", Page: "me"}),
		Mode:    mode, Users: users,
	}
	// 顶栏右侧「共 N 人」（:119-126）
	data.Actions = template.HTML(`<span class="top-count">共 ` + strconv.Itoa(len(users)) + ` 人</span>`)
	if mode == "followers" {
		data.Title = "我的粉丝"
	}
	w.webRender(c, http.StatusOK, "follow.html", data)
}

// ── 时间小助手（求签连续天数用） ──────────────────────────────

func timeNowIn(loc *time.Location) time.Time {
	return time.Now().In(loc)
}

func timeFromUnix(sec int64) time.Time {
	return time.Unix(sec, 0)
}

// webUnreadBadgeHTML 未读角标（与 layout 的 unread-badge define 同结构与同口径：
// >99 收口 99+，=0 输出 hidden 空 span 供心跳接管）
func webUnreadBadgeHTML(n int64) string {
	if n <= 0 {
		return `<span class="unread-badge" data-unread-badge hidden></span>`
	}
	if n > 99 {
		return `<span class="unread-badge" data-unread-badge>99+</span>`
	}
	return `<span class="unread-badge" data-unread-badge>` + strconv.FormatInt(n, 10) + `</span>`
}

// webHeatWeeks 把 371 块瓷砖按周一起始切成 53 列（Heatmap.kt:170-180 weekColumns）：
// 首列前补到周一的空槽。服务端从「今天往前 370 天」连续发砖，起点星期不固定，
// 不补齐整张表会随起点整体错位。
func webHeatWeeks(tiles []model.Tile) []webHeatWeek {
	if len(tiles) == 0 {
		return nil
	}
	pad := 0
	if d, err := time.Parse("2006-01-02", tiles[0].Date); err == nil {
		pad = int(d.Weekday()+6) % 7 // 周一=0 … 周日=6
	}
	out := make([]webHeatWeek, 0, len(tiles)/7+1)
	var week webHeatWeek
	for i := 0; i < pad; i++ {
		week.Days = append(week.Days, webHeatDay{Empty: true})
	}
	for _, t := range tiles {
		week.Days = append(week.Days, webHeatDay{Level: t.Score, Date: t.Date})
		if len(week.Days) == 7 {
			out = append(out, week)
			week = webHeatWeek{}
		}
	}
	for len(week.Days) > 0 && len(week.Days) < 7 {
		week.Days = append(week.Days, webHeatDay{Empty: true})
	}
	if len(week.Days) == 7 {
		out = append(out, week)
	}
	return out
}

// webStreakDays 连续求签天数（与客户端 omikujiStreak 同口径：上海时区、缺天即断）
func webStreakDays(hist []model.Omikuji, loc *time.Location) int {
	streak := 0
	day := time.Now().In(loc)
	for i := 0; i < 30; i++ {
		key := day.Format("2006-01-02")
		found := false
		for _, h := range hist {
			if time.Unix(h.DrawnAt, 0).In(loc).Format("2006-01-02") == key {
				found = true
				break
			}
		}
		if !found {
			break
		}
		streak++
		day = day.AddDate(0, 0, -1)
	}
	return streak
}

// yenText 账面 0.1 円 → 展示文（与客户端 Long.yenText 同口径）
// webShowField 展示开关：缺省视为展示（与 UserRow.show 同语义，store.show 不出包）
func webShowField(fields map[string]bool, key string) bool {
	if v, ok := fields[key]; ok {
		return v
	}
	return true
}

func yenText(tenths int64) string {
	whole := tenths / 10
	frac := tenths % 10
	if frac == 0 {
		return strconv.FormatInt(whole, 10)
	}
	return strconv.FormatInt(whole, 10) + "." + strconv.FormatInt(frac, 10)
}
