package web

import (
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"arisu/server/internal/model"
	"arisu/server/internal/store"
	"github.com/gin-gonic/gin"
)

func (w *Web) reportEma(c *gin.Context) {
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

	var alive bool
	if err := w.St.Pool.QueryRow(c.Request.Context(),
		`SELECT deleted_at IS NULL FROM emas WHERE id=$1`, id).Scan(&alive); err != nil || !alive {
		w.notFound(c)
		return
	}
	if !w.reportGuard(c, v, "ema_id", id) {
		return
	}
	if !w.intervalRateLimit(c, v, w.Cfg.Limits.CommentInterval) {
		return
	}
	eid := id
	reportID, err := w.St.CreateReport(c.Request.Context(), v.ID, nil, nil, &eid, nil, req.Reason)
	if err != nil {
		w.err(c, 500, "举报失败")
		return
	}
	_ = w.St.NotifyStaff(c.Request.Context(), store.NoticeInput{
		UserID: v.ID, Kind: "report", Category: "report",
		Content:     "新的举报：绘马 #" + strconv.FormatInt(id, 10) + " — " + req.Reason,
		TargetRoute: "report/" + strconv.FormatInt(reportID, 10),
	})
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) listEmas(c *gin.Context) {
	page, size := clampPage(c)
	q := store.EmaListQuery{
		Tag:    c.Query("tag"),
		State:  c.Query("state"),
		Sort:   c.DefaultQuery("sort", "new"),
		Limit:  size,
		Offset: (page - 1) * size,
	}

	for _, t := range strings.Split(c.Query("tags"), ",") {
		if t != "" && len(q.Tags) < 4 {
			q.Tags = append(q.Tags, t)
		}
	}
	for _, st := range strings.Split(c.Query("states"), ",") {
		if st != "" && len(q.States) < 4 {
			q.States = append(q.States, st)
		}
	}
	pp, err := w.St.EmasPage(c.Request.Context(), w.viewer(c).ID, q)
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	if pp.Items == nil {
		pp.Items = []model.Ema{}
	}
	c.JSON(http.StatusOK, pp)
}

type emaBodyReq struct {
	Title  string   `json:"title"`
	Body   string   `json:"body"`
	Tags   []string `json:"tags"`
	State  string   `json:"state"`
	Reason string   `json:"reason"`
}

func (w *Web) createEma(c *gin.Context) {
	v := w.viewer(c)
	if w.mutedErr(c, v) {
		return
	}
	var req emaBodyReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	title := strings.TrimSpace(req.Title)
	if title == "" || len([]rune(title)) > 60 {
		w.bad(c, "标题需为 1~60 字")
		return
	}
	if strings.TrimSpace(req.Body) == "" {
		w.bad(c, "正文不能为空")
		return
	}
	if len([]rune(req.Body)) > 16000 {
		w.bad(c, "正文最多 16000 字")
		return
	}

	if !w.postingRateLimit(c, v, true) {
		return
	}
	e, err := w.St.CreateEma(c.Request.Context(), store.EmaCreate{
		AuthorID: v.ID, Title: title, Body: req.Body, Tags: req.Tags,
	})
	if err != nil {
		w.err(c, 500, "提交失败，请稍后重试")
		return
	}
	c.JSON(http.StatusOK, e)
}

func (w *Web) getEma(c *gin.Context) {
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	e, err := w.St.EmaByID(c.Request.Context(), w.viewer(c).ID, id)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, e)
}

func (w *Web) updateEma(c *gin.Context) {
	v := w.viewer(c)

	if w.mutedErr(c, v) {
		return
	}
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	e, err := w.St.EmaByID(c.Request.Context(), w.viewer(c).ID, id)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	if e.Author.ID != v.ID {
		w.err(c, http.StatusForbidden, "只有发起人可以编辑")
		return
	}
	var req emaBodyReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	up := store.EmaUpdate{}
	if req.Title != "" {
		t := strings.TrimSpace(req.Title)
		if t == "" || len([]rune(t)) > 60 {
			w.bad(c, "标题需为 1~60 字")
			return
		}
		up.Title = &t
	}
	if req.Body != "" {

		if len([]rune(req.Body)) > 16000 {
			w.bad(c, "正文最多 16000 字")
			return
		}
		up.Body = &req.Body
	}
	if req.Tags != nil {
		up.Tags = &req.Tags
	}

	if !w.intervalRateLimit(c, v, w.Cfg.Limits.CommentInterval) {
		return
	}
	if err := w.St.UpdateEma(c.Request.Context(), id, up); err != nil {
		w.err(c, 500, "保存失败")
		return
	}
	ne, _ := w.St.EmaByID(c.Request.Context(), w.viewer(c).ID, id)
	c.JSON(http.StatusOK, ne)
}

func (w *Web) deleteEma(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	e, err := w.St.EmaByID(c.Request.Context(), w.viewer(c).ID, id)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	mod := e.Author.ID != v.ID
	if mod {
		if !w.requireAdmin(c) {
			return
		}

		var req emaBodyReq
		_ = c.ShouldBindJSON(&req)
		if strings.TrimSpace(req.Reason) == "" {
			w.bad(c, "删除理由必填")
			return
		}
		if reasonTooLong(req.Reason) {
			w.bad(c, "理由过长")
			return
		}
		if err := w.St.DeleteEma(c.Request.Context(), id, v.ID, true, req.Reason); err != nil {
			w.err(c, 500, "删除失败")
			return
		}
		c.JSON(http.StatusOK, gin.H{"ok": true})
		return
	}
	if err := w.St.DeleteEma(c.Request.Context(), id, v.ID, false, ""); err != nil {
		w.err(c, 500, "删除失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) deleteEmaReply(c *gin.Context) {
	v := w.viewer(c)
	emaID, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	rid, ok := pathID(c, "rid")
	if !ok {
		w.bad(c, "参数错误")
		return
	}

	var authorID int64
	var authorRole string
	if err := w.St.Pool.QueryRow(c.Request.Context(),
		`SELECT r.author_id, u.role FROM ema_replies r JOIN users u ON u.id=r.author_id
		WHERE r.id=$1 AND r.ema_id=$2 AND r.deleted_at IS NULL`, rid, emaID).Scan(&authorID, &authorRole); err != nil {
		w.notFound(c)
		return
	}
	mod := authorID != v.ID
	if mod {
		if !w.requireAdmin(c) {
			return
		}
		if !w.requireOver(c, authorRole) {
			return
		}
		var req deleteReasonReq
		_ = c.ShouldBindJSON(&req)
		if strings.TrimSpace(req.Reason) == "" {
			w.bad(c, "删除理由必填，将随通知发送给作者")
			return
		}
		if reasonTooLong(req.Reason) {
			w.bad(c, "理由过长")
			return
		}
		if err := w.St.DeleteEmaReply(c.Request.Context(), emaID, rid, v.ID, true, req.Reason); err != nil {
			w.err(c, 500, "删除失败")
			return
		}
		c.JSON(http.StatusOK, gin.H{"ok": true})
		return
	}
	if err := w.St.DeleteEmaReply(c.Request.Context(), emaID, rid, v.ID, false, ""); err != nil {
		w.err(c, 500, "删除失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) setEmaState(c *gin.Context) {
	if !w.requireAdmin(c) {
		return
	}
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	var req emaBodyReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}

	valid := false
	for _, s := range store.EmaStates {
		if req.State == s {
			valid = true
			break
		}
	}
	if !valid {
		w.bad(c, "参数错误")
		return
	}
	e, err := w.St.EmaByID(c.Request.Context(), w.viewer(c).ID, id)
	if err != nil {
		w.notFound(c)
		return
	}
	if err := w.St.SetEmaState(c.Request.Context(), id, req.State); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	if e.Author.ID != v.ID {
		_ = w.St.CreateNotice(c.Request.Context(), store.NoticeInput{
			UserID: e.Author.ID, Kind: "system", Category: "system",
			Content:     "您的绘马 #" + strconv.FormatInt(id, 10) + " 状态变更为「" + emaStateName(req.State) + "」",
			TargetRoute: "ema/" + strconv.FormatInt(id, 10),
		})
	}
	c.JSON(http.StatusOK, gin.H{"state": req.State})
}

func emaStateName(s string) string {
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

func (w *Web) listEmaReplies(c *gin.Context) {
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	list, err := w.St.EmaReplies(c.Request.Context(), w.viewer(c).ID, id)
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	if list == nil {
		list = []model.EmaReply{}
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

func (w *Web) addEmaReply(c *gin.Context) {
	v := w.viewer(c)
	if w.mutedErr(c, v) {
		return
	}

	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	var req struct {
		Content string `json:"content"`
	}
	if err := c.ShouldBindJSON(&req); err != nil || strings.TrimSpace(req.Content) == "" {
		w.bad(c, "回复内容不能为空")
		return
	}
	if len([]rune(req.Content)) > 1000 {
		w.bad(c, "回复最多 1000 字")
		return
	}

	if !w.intervalRateLimit(c, v, w.Cfg.Limits.CommentInterval) {
		return
	}
	r, err := w.St.AddEmaReply(c.Request.Context(), id, v.ID, strings.TrimSpace(req.Content))
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "发布失败，请稍后重试")
		return
	}
	c.JSON(http.StatusOK, r)
}

func (w *Web) staff(c *gin.Context) {
	groups, err := w.St.StaffGroups(c.Request.Context())
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, gin.H{"groups": groups})
}

type applyReq struct {
	Reason      string `json:"reason"`
	WeeklyHours string `json:"weekly_hours"`
}

func (w *Web) applyStaff(c *gin.Context) {
	v := w.viewer(c)
	var req applyReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	reason := strings.TrimSpace(req.Reason)
	if reason == "" {
		w.bad(c, "申请理由不能为空")
		return
	}

	if len([]rune(reason)) > 500 {
		w.bad(c, "申请理由过长")
		return
	}
	if len(req.WeeklyHours) > 20 {
		w.bad(c, "参数错误")
		return
	}
	if req.WeeklyHours == "" {
		w.bad(c, "请选择每周可投入时间")
		return
	}
	if model.IsAdmin(v.Role) {
		w.bad(c, "您已经是维护人员了")
		return
	}

	if v.Role == model.RoleTempHoushi {
		w.bad(c, "您已经是临时奉仕了，无需再申请")
		return
	}

	if last, err := w.St.LastStaffApplication(c.Request.Context(), v.ID); err == nil {
		if last.Status == "pending" {
			w.bad(c, "已提交申请，等待审核中")
			return
		}
		if time.Since(time.Unix(last.CreatedAt, 0)) < w.Cfg.Limits.StaffApplyCooldown {
			w.bad(c, "一周内不可再次申请")
			return
		}
	}
	if err := w.St.CreateStaffApplication(c.Request.Context(), v.ID, reason, req.WeeklyHours); err != nil {
		w.err(c, 500, "提交失败")
		return
	}

	_ = w.St.NotifyStaff(c.Request.Context(), store.NoticeInput{
		UserID: v.ID, Kind: "application", Category: "system",
		Content:     w.viewer(c).User.Nickname + " 申请担任临时奉仕，理由：" + store.PreviewOf(reason),
		TargetRoute: "staff",
		DedupeKey:   "staffapp:" + strconv.FormatInt(v.ID, 10),
	})
	c.JSON(http.StatusOK, gin.H{"status": "pending"})
}

func (w *Web) myStaffApplication(c *gin.Context) {
	v := w.viewer(c)
	app, err := w.St.LastStaffApplication(c.Request.Context(), v.ID)
	if errors.Is(err, store.ErrNotFound) {
		c.JSON(http.StatusOK, gin.H{"applied": false})
		return
	}
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"applied": true, "status": app.Status, "created_at": app.CreatedAt})
}

func (w *Web) pendingApplications(c *gin.Context) {
	if !w.requireGuuji(c) {
		return
	}
	list, err := w.St.PendingStaffApplications(c.Request.Context())
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

type decideReq struct {
	Approve bool   `json:"approve"`
	Reason  string `json:"reason"`
}

func (w *Web) decideApplication(c *gin.Context) {
	if !w.requireGuuji(c) {
		return
	}
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	var req decideReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	if !req.Approve && strings.TrimSpace(req.Reason) == "" {
		w.bad(c, "驳回需填写理由")
		return
	}
	if reasonTooLong(req.Reason) {
		w.bad(c, "理由过长")
		return
	}
	uid, status, err := w.St.DecideStaffApplication(c.Request.Context(), id, w.viewer(c).ID, req.Approve)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	if req.Approve {
		_ = w.St.CreateNotice(c.Request.Context(), store.NoticeInput{
			UserID: uid, Kind: "application", Category: "system",
			Content:     "您的临时奉仕申请已批准，欢迎加入奉仕会。",
			TargetRoute: "staff",
		})
	} else {
		_ = w.St.CreateNotice(c.Request.Context(), store.NoticeInput{
			UserID: uid, Kind: "application", Category: "system",
			Content:     "您的临时奉仕申请被驳回。理由：" + req.Reason,
			TargetRoute: "staff",
		})
	}
	c.JSON(http.StatusOK, gin.H{"status": status})
}
