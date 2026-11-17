package web

import (
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"arisu/server/internal/model"
	"arisu/server/internal/store"
	"fmt"
	"github.com/gin-gonic/gin"
)

// userDetail 他人主页：资料 + 关注关系 + 计数
func (w *Web) userDetail(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	u, err := w.St.UserByID(c.Request.Context(), id)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	pub := u.ToPublic()
	_ = w.St.FillUserCounts(c.Request.Context(), pub, v.ID)
	following, _ := w.St.IsFollowing(c.Request.Context(), v.ID, id)
	followsMe, _ := w.St.IsFollowing(c.Request.Context(), id, v.ID)
	// 只回「我有没有拉黑 TA」；TA 有没有拉黑我绝不下发（静默）
	blocked, _ := w.St.Blocks(c.Request.Context(), v.ID, id)
	// 禁言与封禁状态仅本人与管理角色可见，不对访客暴露
	muted := v.ID == id || model.IsAdmin(v.Role)
	banned := v.ID == id || model.IsAdmin(v.Role)
	c.JSON(http.StatusOK, gin.H{
		"user":       pub,
		"following":  following,
		"follows_me": followsMe,
		"blocked":    blocked,
		"muted":      muted && u.MutedUntilActive() != nil,
		"banned":     banned && u.Banned,
	})
}

func (w *Web) userTiles(c *gin.Context) {
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	// 拉黑某人后不再展示对方的参拜热度（与「看不到对方的内容」同一把尺）
	if blocked, _ := w.St.Blocks(c.Request.Context(), w.viewer(c).ID, id); blocked {
		c.JSON(http.StatusOK, gin.H{"tiles": []model.Tile{}})
		return
	}
	tiles, err := w.St.Tiles(c.Request.Context(), id)
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"tiles": tiles})
}

func (w *Web) userPosts(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	tab := c.DefaultQuery("tab", "posts")
	if tab != "posts" && id != v.ID {
		// 追踪/喜欢/收藏列表仅本人可见
		w.err(c, http.StatusForbidden, "仅本人可见")
		return
	}
	page, size := clampPage(c)
	q := store.PostListQuery{ViewerID: v.ID, Sort: "new", AuthorID: id, Limit: size, Offset: (page - 1) * size}
	if tab != "posts" {
		items, total, err := w.relationPosts(c, tab, v.ID, id, 0, int64(size), int64((page-1)*size))
		if err != nil {
			w.err(c, 500, "查询失败")
			return
		}
		c.JSON(http.StatusOK, gin.H{"items": items, "total": total})
		return
	}
	pp, err := w.St.PostsPage(c.Request.Context(), q)
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	c.JSON(http.StatusOK, pp)
}

func (w *Web) relationPosts(c *gin.Context, tab string, viewerID, ownerID, collectionID, limit, offset int64) ([]model.Post, int64, error) {
	switch tab {
	case "tracked":
		return w.St.PostsByRelation(c.Request.Context(), viewerID, "tracked", ownerID, 0, int(limit), int(offset))
	case "liked":
		return w.St.PostsByRelation(c.Request.Context(), viewerID, "liked", ownerID, 0, int(limit), int(offset))
	case "favorited":
		return w.St.PostsByRelation(c.Request.Context(), viewerID, "favorited", ownerID, collectionID, int(limit), int(offset))
	default:
		return w.St.PostsByRelation(c.Request.Context(), viewerID, "posts", ownerID, 0, int(limit), int(offset))
	}
}

func (w *Web) follow(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok || id == v.ID {
		w.bad(c, "参数错误")
		return
	}
	if _, err := w.St.UserByID(c.Request.Context(), id); err != nil {
		w.notFound(c)
		return
	}
	// 拉黑是静默的：对方拉黑了您 → 照常回成功但不落库；您拉黑了对方 → 不做任何事
	if blocked, _ := w.St.Blocks(c.Request.Context(), id, v.ID); blocked {
		c.JSON(http.StatusOK, gin.H{"following": true})
		return
	}
	if blocked, _ := w.St.Blocks(c.Request.Context(), v.ID, id); blocked {
		c.JSON(http.StatusOK, gin.H{"following": false})
		return
	}
	if err := w.St.Follow(c.Request.Context(), v.ID, id); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	// 关注通知防轰炸：按「目标+发起人」聚合去重，取关再关注不再反复刷对方社务箱
	_ = w.St.CreateNotice(c.Request.Context(), store.NoticeInput{
		UserID: id, Kind: "follow", Category: "system",
		Content:     w.viewer(c).User.Nickname + " 关注了您",
		TargetRoute: fmt.Sprintf("user/%d", v.ID),
		DedupeKey:   fmt.Sprintf("follow:%d:%d", id, v.ID),
		ActorID:     v.ID,
	})
	c.JSON(http.StatusOK, gin.H{"following": true})
}

func (w *Web) unfollow(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	if err := w.St.Unfollow(c.Request.Context(), v.ID, id); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"following": false})
}

func (w *Web) myFollowing(c *gin.Context) {
	v := w.viewer(c)
	list, err := w.St.FollowList(c.Request.Context(), v.ID, false)
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

func (w *Web) myFollowers(c *gin.Context) {
	v := w.viewer(c)
	list, err := w.St.FollowList(c.Request.Context(), v.ID, true)
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

// myRoom 我的客房聚合数据
func (w *Web) myRoom(c *gin.Context) {
	v := w.viewer(c)
	ctx := c.Request.Context()
	me := v.User.ToMe()
	_ = w.St.FillUserCounts(ctx, &me.User, v.ID)
	tiles, _ := w.St.Tiles(ctx, v.ID)
	st, _ := w.St.OnlineStats(ctx)
	achievements, _ := w.St.Achievements(ctx, v.ID)
	today := gin.H{"minutes": 0, "posts": 0, "comments": 0}
	// 今日三项
	var minutes, posts, comments int
	_ = w.St.Pool.QueryRow(ctx, `SELECT COALESCE(sum(minutes),0), COALESCE(sum(posts),0), COALESCE(sum(comments),0)
		FROM activity_days WHERE user_id=$1 AND day=`+store.OmikujiTodayExpr+``, v.ID).Scan(&minutes, &posts, &comments)
	today = gin.H{"minutes": minutes, "posts": posts, "comments": comments}
	c.JSON(http.StatusOK, gin.H{
		"me":           me,
		"tiles":        tiles,
		"today":        today,
		"online":       st,
		"achievements": achievements,
	})
}

func (w *Web) myPosts(c *gin.Context) {
	v := w.viewer(c)
	tab := c.DefaultQuery("tab", "posts")
	page, size := clampPage(c)
	if tab == "posts" {
		pp, err := w.St.PostsPage(c.Request.Context(), store.PostListQuery{
			ViewerID: v.ID, Sort: "new", AuthorID: v.ID, Limit: size, Offset: (page - 1) * size})
		if err != nil {
			w.err(c, 500, "查询失败")
			return
		}
		c.JSON(http.StatusOK, pp)
		return
	}
	var collectionID int64
	if tab == "favorited" {
		if cs := c.Query("collection_id"); cs != "" {
			collectionID, _ = strconv.ParseInt(cs, 10, 64)
		}
	}
	items, total, err := w.relationPosts(c, tab, v.ID, v.ID, collectionID, int64(size), int64((page-1)*size))
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": items, "total": total})
}

func (w *Web) myCollections(c *gin.Context) {
	v := w.viewer(c)
	// 读列表前先保证默认收藏夹存在：安卓的收藏面板里总有一枚「默认收藏夹」，
	// 网页若只靠「收藏动作」去建，从没收藏过的社员打开面板会看到空列表。
	_, _ = w.St.EnsureDefaultCollection(c.Request.Context(), v.ID)
	list, err := w.St.Collections(c.Request.Context(), v.ID)
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

type newCollectionReq struct {
	Name string `json:"name"`
}

func (w *Web) newCollection(c *gin.Context) {
	v := w.viewer(c)
	var req newCollectionReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	name := strings.TrimSpace(req.Name)
	if name == "" || len([]rune(name)) > 20 {
		w.bad(c, "收藏夹名需为 1~20 个字符")
		return
	}
	// 「默认收藏夹」是保留名：首藏时 EnsureDefaultCollection 按 (user_id, name)
	// 冲突收敛，用户手动占了这个名，不带 collection_id 的收藏会永久落进这只非默认夹
	if name == "默认收藏夹" {
		w.bad(c, "已有同名收藏夹")
		return
	}
	// 建夹上限：无限建夹会变成另一种垃圾内容（2026-10-02 用户裁决，20 个）
	// 上限校验在 CreateCollection 的锁内事务里做，这里只兜并发外的友好提示
	n, err := w.St.CollectionCount(c.Request.Context(), v.ID)
	if err == nil && n >= 20 {
		w.bad(c, "收藏夹最多 20 个")
		return
	}
	col, err := w.St.CreateCollection(c.Request.Context(), v.ID, name)
	if errors.Is(err, store.ErrConflict) {
		w.bad(c, "已有同名收藏夹")
		return
	}
	if errors.Is(err, store.ErrTooMany) {
		w.bad(c, "收藏夹最多 20 个")
		return
	}
	if err != nil {
		w.err(c, 500, "创建失败")
		return
	}
	c.JSON(http.StatusOK, col)
}

// deleteCollection 删除自建收藏夹（默认收藏夹不可删）：夹内帖子移回默认收藏夹
func (w *Web) deleteCollection(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	err := w.St.DeleteCollection(c.Request.Context(), v.ID, id)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if errors.Is(err, store.ErrBadRequest) {
		w.bad(c, "默认收藏夹不能删除")
		return
	}
	if err != nil {
		w.err(c, 500, "删除失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

// ---- 管理：警告 / 禁言 / 封禁 / 角色 ----

type warnReq struct {
	Content string `json:"content"`
}

func (w *Web) warnUser(c *gin.Context) {
	if !w.requireAdmin(c) {
		return
	}
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	// 与 muteUser 同一套保护：不能警告自己、对方身份不得高于自己
	if id == v.ID {
		w.bad(c, "不能对您自己执行该操作")
		return
	}
	// 目标先确认存在：此前不存在时短路了层级比较，warning 的通知 FK 直接 500
	tgt, uerr := w.St.UserByID(c.Request.Context(), id)
	if uerr != nil {
		w.notFound(c)
		return
	}
	if model.RoleRank(tgt.Role) >= model.RoleRank(v.Role) {
		w.err(c, http.StatusForbidden, "对方身份不低于您，无法执行该操作")
		return
	}
	var req warnReq
	if err := c.ShouldBindJSON(&req); err != nil || strings.TrimSpace(req.Content) == "" {
		w.bad(c, "警告内容不能为空")
		return
	}
	if len([]rune(strings.TrimSpace(req.Content))) > 500 {
		w.bad(c, "警告内容过长")
		return
	}
	if err := w.St.CreateNotice(c.Request.Context(), store.NoticeInput{
		UserID: id, Kind: "warn", Category: "system",
		Content: "您收到一条警告：" + req.Content,
	}); err != nil {
		w.err(c, 500, "发送失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

type muteReq struct {
	Hours  int    `json:"hours"` // 1|24|168|720|0=永久
	Reason string `json:"reason"`
}

func (w *Web) muteUser(c *gin.Context) {
	if !w.requireGuuji(c) {
		return
	}
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	if id == v.ID {
		w.bad(c, "不能对您自己执行该操作")
		return
	}
	// 目标先确认存在：此前不存在时短路了层级比较，warning 的通知 FK 直接 500
	tgt, uerr := w.St.UserByID(c.Request.Context(), id)
	if uerr != nil {
		w.notFound(c)
		return
	}
	if model.RoleRank(tgt.Role) >= model.RoleRank(v.Role) {
		w.err(c, http.StatusForbidden, "对方身份不低于您，无法执行该操作")
		return
	}
	var req muteReq
	if err := c.ShouldBindJSON(&req); err != nil || strings.TrimSpace(req.Reason) == "" {
		w.bad(c, "禁言理由必填")
		return
	}
	if req.Hours < 0 {
		w.bad(c, "禁言时长不合法")
		return
	}
	if reasonTooLong(req.Reason) {
		w.bad(c, "理由过长")
		return
	}
	var until *time.Time
	label := ""
	if req.Hours > 0 {
		hours := req.Hours
		// 10 年封顶：hours 过大时 Duration 溢出为负，禁言会静默失效
		if hours > 87600 {
			hours = 87600
		}
		t := time.Now().Add(time.Duration(hours) * time.Hour)
		until = &t
		label = strconv.Itoa(hours) + " 小时"
	} else {
		// 永久禁言写哨兵年份（mutedErr 按 Year>9000 识别）。
		// 此前 hours=0 落成 muted_until=NULL——NULL 的语义是「未禁言」，
		// 点「永久」会发一条永久禁言通知然后把人解禁
		t := time.Date(9999, 12, 31, 23, 59, 59, 0, time.UTC)
		until = &t
		label = "永久"
	}
	if err := w.St.SetMute(c.Request.Context(), id, until); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	_ = w.St.CreateNotice(c.Request.Context(), store.NoticeInput{
		UserID: id, Kind: "mute", Category: "system",
		Content: "您被禁言 " + label + "。理由：" + req.Reason,
	})
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

type banReq struct {
	Reason string `json:"reason"`
}

func (w *Web) banUser(c *gin.Context) {
	if !w.requireGuuji(c) {
		return
	}
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok || id == v.ID {
		w.bad(c, "参数错误")
		return
	}
	// 目标先确认存在：此前不存在时短路了层级比较，warning 的通知 FK 直接 500
	tgt, uerr := w.St.UserByID(c.Request.Context(), id)
	if uerr != nil {
		w.notFound(c)
		return
	}
	if model.RoleRank(tgt.Role) >= model.RoleRank(v.Role) {
		w.err(c, http.StatusForbidden, "对方身份不低于您，无法执行该操作")
		return
	}
	var req banReq
	if err := c.ShouldBindJSON(&req); err != nil || strings.TrimSpace(req.Reason) == "" {
		w.bad(c, "封禁理由必填")
		return
	}
	if reasonTooLong(req.Reason) {
		w.bad(c, "理由过长")
		return
	}
	if err := w.St.SetBan(c.Request.Context(), id, true); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	_ = w.St.RevokeRefreshTokens(c.Request.Context(), id)
	_ = w.St.CreateNotice(c.Request.Context(), store.NoticeInput{
		UserID: id, Kind: "system", Category: "system",
		Content: "您的账号已被封禁。理由：" + req.Reason,
	})
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

type roleReq struct {
	Role string `json:"role"`
}

// unmuteUser 解除禁言：与禁言同权限（宫司+、目标级别低于操作者），幂等——
// 对未禁言的人调用同样返回成功，客户端不必先查状态
func (w *Web) unmuteUser(c *gin.Context) {
	if !w.requireGuuji(c) {
		return
	}
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	tgt, uerr := w.St.UserByID(c.Request.Context(), id)
	if uerr != nil {
		w.notFound(c)
		return
	}
	if model.RoleRank(tgt.Role) >= model.RoleRank(v.Role) {
		w.err(c, http.StatusForbidden, "对方身份不低于您，无法执行该操作")
		return
	}
	if err := w.St.SetMute(c.Request.Context(), id, nil); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	_ = w.St.CreateNotice(c.Request.Context(), store.NoticeInput{
		UserID: id, Kind: "system", Category: "system",
		Content: "您的禁言已被解除。",
	})
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

// unbanUser 解除封禁：与封禁同权限（宫司+、目标级别低于操作者），幂等
func (w *Web) unbanUser(c *gin.Context) {
	if !w.requireGuuji(c) {
		return
	}
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	tgt, uerr := w.St.UserByID(c.Request.Context(), id)
	if uerr != nil {
		w.notFound(c)
		return
	}
	if model.RoleRank(tgt.Role) >= model.RoleRank(v.Role) {
		w.err(c, http.StatusForbidden, "对方身份不低于您，无法执行该操作")
		return
	}
	if err := w.St.SetBan(c.Request.Context(), id, false); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	_ = w.St.CreateNotice(c.Request.Context(), store.NoticeInput{
		UserID: id, Kind: "system", Category: "system",
		Content: "您的账号已解除封禁。",
	})
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) setRole(c *gin.Context) {
	v := w.viewer(c)
	if v.Role != model.RoleKannushi {
		w.err(c, http.StatusForbidden, "只有神主可以调整身份")
		return
	}
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	if id == v.ID {
		w.bad(c, "不能调整自己的身份")
		return
	}
	// 层级校验与 warn/mute/ban 同规：不能动平级及以上，
	// 否则神主可被另一神主降级、member 可被直提 kannushi 造成权限自我复制
	var targetRole string
	if err := w.St.Pool.QueryRow(c.Request.Context(),
		`SELECT role FROM users WHERE id=$1`, id).Scan(&targetRole); err != nil {
		w.notFound(c)
		return
	}
	if model.RoleRank(v.Role) <= model.RoleRank(targetRole) {
		w.err(c, http.StatusForbidden, "级别不够，无法调整该社员的身份")
		return
	}
	var req roleReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	switch req.Role {
	case model.RoleMember, model.RoleTempHoushi, model.RoleHoushi, model.RoleGuuji, model.RoleKannushi:
	default:
		w.bad(c, "未知身份")
		return
	}
	if err := w.St.SetRole(c.Request.Context(), id, req.Role); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	_ = w.St.CreateNotice(c.Request.Context(), store.NoticeInput{
		UserID: id, Kind: "system", Category: "system",
		Content: "您的身份被调整为「" + model.RoleName(req.Role) + "」",
	})
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

type reportTargetReq struct {
	Reason string `json:"reason"`
}

func (w *Web) reportUser(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	var req reportTargetReq
	if err := c.ShouldBindJSON(&req); err != nil || strings.TrimSpace(req.Reason) == "" {
		w.bad(c, "举报理由必填")
		return
	}
	if len([]rune(strings.TrimSpace(req.Reason))) > 500 {
		w.bad(c, "举报理由过长")
		return
	}
	// 目标必须真实存在，且不能举报自己——
	// 此前目标连存在性都不校验，举报记录里也丢了被举报对象，只能空驳
	var exists bool
	if err := w.St.Pool.QueryRow(c.Request.Context(),
		`SELECT EXISTS(SELECT 1 FROM users WHERE id=$1 AND anonymized=false)`, id).Scan(&exists); err != nil || !exists {
		w.notFound(c)
		return
	}
	if id == v.ID {
		w.bad(c, "不能举报自己")
		return
	}
	if !w.reportGuard(c, v, "reported_user_id", id) {
		return
	}
	if !w.intervalRateLimit(c, v, w.Cfg.Limits.CommentInterval) {
		return
	}
	uid := id
	reportID, err := w.St.CreateReport(c.Request.Context(), v.ID, nil, nil, nil, &uid, req.Reason)
	if err != nil {
		w.err(c, 500, "举报失败")
		return
	}
	_ = w.St.NotifyStaff(c.Request.Context(), store.NoticeInput{
		UserID: v.ID, Kind: "report", Category: "report",
		Content:     "新的举报：用户 " + model.FormatUserNo(id) + " — " + req.Reason,
		TargetRoute: "report/" + strconv.FormatInt(reportID, 10),
	})
	c.JSON(http.StatusOK, gin.H{"ok": true})
}
