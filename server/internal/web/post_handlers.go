package web

import (
	"errors"
	"net/http"
	"strconv"
	"strings"

	"arisu/server/internal/model"
	"arisu/server/internal/store"
	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5"
)

var postBoards = []string{"main", "gensokyo", "dev"}

type postBodyReq struct {
	Board  string `json:"board"`
	Title  string `json:"title"`
	Body   string `json:"body"`
	Intro  string `json:"intro"`
	Cover  *string `json:"cover"`
	Reason string `json:"reason"`
}

func (w *Web) listPosts(c *gin.Context) {
	v := w.viewer(c)
	page, size := clampPage(c)
	q := store.PostListQuery{
		ViewerID: v.ID,
		Board:    c.Query("board"),
		Sort:     c.DefaultQuery("sort", "new"),
		Limit:    size,
		Offset:   (page - 1) * size,
	}
	pp, err := w.St.PostsPage(c.Request.Context(), q)
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, pp)
}

func (w *Web) createPost(c *gin.Context) {
	v := w.viewer(c)
	if w.mutedErr(c, v) {
		return
	}
	var req postBodyReq
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
	if len([]rune(req.Body)) > 32000 {
		w.bad(c, "正文最多 32000 字")
		return
	}
	intro := strings.TrimSpace(req.Intro)
	if len([]rune(intro)) > 60 {
		w.bad(c, "介绍语最多 60 字")
		return
	}
	var cover string
	if req.Cover != nil {
		cover = strings.TrimSpace(*req.Cover)
		if len([]rune(cover)) > 300 {
			w.bad(c, "封面地址过长")
			return
		}

		lower := strings.ToLower(cover)
		if cover != "" && !strings.HasPrefix(lower, "http://") && !strings.HasPrefix(lower, "https://") {
			w.bad(c, "参数错误")
			return
		}
	}

	if !w.postingRateLimit(c, v, true) {
		return
	}
	p, err := w.St.CreatePost(c.Request.Context(), store.PostCreate{
		AuthorID: v.ID, Board: req.Board, Title: title, Body: req.Body, Intro: intro, Cover: cover,
	})
	if err != nil {
		w.err(c, 500, "发布失败，请稍后重试")
		return
	}
	w.notifyMentions(c, v, req.Body,
		mentionPostContent(v.User.Nickname, title), mentionRoute(p.ID))
	c.JSON(http.StatusOK, p)
}

func (w *Web) getPost(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	p, err := w.St.PostByID(c.Request.Context(), v.ID, id)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, p)
}

func (w *Web) updatePost(c *gin.Context) {
	v := w.viewer(c)

	if w.mutedErr(c, v) {
		return
	}
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	p, err := w.St.PostByID(c.Request.Context(), v.ID, id)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	if p.Author.ID != v.ID {
		w.err(c, http.StatusForbidden, "只有作者可以编辑")
		return
	}
	var req postBodyReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	up := store.PostUpdate{}
	if req.Title != "" {
		t := strings.TrimSpace(req.Title)
		if t == "" || len([]rune(t)) > 60 {
			w.bad(c, "标题需为 1~60 字")
			return
		}
		up.Title = &t
	}
	if req.Body != "" {
		if len([]rune(req.Body)) > 32000 {
			w.bad(c, "正文最多 32000 字")
			return
		}
		up.Body = &req.Body
	}
	if req.Intro != "" {
		intro := strings.TrimSpace(req.Intro)
		if len([]rune(intro)) > 60 {
			w.bad(c, "介绍语最多 60 字")
			return
		}
		up.Intro = &intro
	}
	if req.Board != "" {

		valid := false
		for _, b := range postBoards {
			if req.Board == b {
				valid = true
				break
			}
		}
		if !valid {
			w.bad(c, "参数错误")
			return
		}
		up.Board = &req.Board
	}
	if req.Cover != nil {
		cover := strings.TrimSpace(*req.Cover)
		if len([]rune(cover)) > 300 {
			w.bad(c, "封面地址过长")
			return
		}

		lower := strings.ToLower(cover)
		if cover != "" && !strings.HasPrefix(lower, "http://") && !strings.HasPrefix(lower, "https://") {
			w.bad(c, "参数错误")
			return
		}
		up.Cover = &cover
	}

	if !w.intervalRateLimit(c, v, w.Cfg.Limits.CommentInterval) {
		return
	}
	if err := w.St.UpdatePost(c.Request.Context(), id, up); err != nil {
		w.err(c, 500, "保存失败")
		return
	}
	if np, err := w.St.PostByID(c.Request.Context(), v.ID, id); err == nil {
		store.NotifyTrackersEvent(c.Request.Context(), w.St.Pool, id, np.Title, "有新的编辑")
		w.notifyMentions(c, v, req.Body,
			mentionPostContent(v.User.Nickname, np.Title), mentionRoute(id))
		c.JSON(http.StatusOK, np)
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) deletePost(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	p, err := w.St.PostByID(c.Request.Context(), v.ID, id)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	mod := p.Author.ID != v.ID
	if mod {
		if !w.requireAdmin(c) {
			return
		}

		var req postBodyReq
		_ = c.ShouldBindJSON(&req)
		if strings.TrimSpace(req.Reason) == "" {
			w.bad(c, "删除理由必填，将随通知发送给作者")
			return
		}
		if reasonTooLong(req.Reason) {
			w.bad(c, "理由过长")
			return
		}
		if err := w.St.DeletePost(c.Request.Context(), id, v.ID, true, req.Reason); err != nil {
			w.err(c, 500, "删除失败")
			return
		}
		store.NotifyTrackersEvent(c.Request.Context(), w.St.Pool, id, p.Title, "已被管理删除： "+req.Reason)
		c.JSON(http.StatusOK, gin.H{"ok": true})
		return
	}
	if err := w.St.DeletePost(c.Request.Context(), id, v.ID, false, ""); err != nil {
		w.err(c, 500, "删除失败")
		return
	}
	store.NotifyTrackersEvent(c.Request.Context(), w.St.Pool, id, p.Title, "已被作者删除")
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

type flagResp struct {
	OK      bool  `json:"ok"`
	Upped   bool  `json:"upped,omitempty"`
	Tracked bool  `json:"tracked,omitempty"`
	Favored bool  `json:"favorited,omitempty"`
	Count   int64 `json:"count"`
}

func (w *Web) toggleUp(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	active, count, err := w.St.ToggleUp(c.Request.Context(), id, v.ID, w.Cfg.Limits.HotAuthorUpThresh)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"upped": active, "up_count": count})
}

func (w *Web) toggleTrack(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	tracked, err := w.St.ToggleTrack(c.Request.Context(), id, v.ID)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"tracked": tracked})
}

type favoriteReq struct {
	CollectionID *int64 `json:"collection_id"`
}

func (w *Web) toggleFavorite(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	var req favoriteReq
	_ = c.ShouldBindJSON(&req)
	fav, err := w.St.ToggleFavorite(c.Request.Context(), v.ID, id, req.CollectionID)
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"favorited": fav})
}

func (w *Web) toggleEssence(c *gin.Context) {
	if !w.requireGuuji(c) {
		return
	}
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	on, err := w.St.TogglePostFlag(c.Request.Context(), id, "essence")
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"essence": on})
}

func (w *Web) togglePin(c *gin.Context) {
	if !w.requireGuuji(c) {
		return
	}
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	on, err := w.St.TogglePostFlag(c.Request.Context(), id, "pinned")
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"pinned": on})
}

func (w *Web) reportPost(c *gin.Context) {
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
		`SELECT deleted_at IS NULL FROM posts WHERE id=$1`, id).Scan(&alive); err != nil || !alive {
		w.notFound(c)
		return
	}
	if !w.reportGuard(c, v, "post_id", id) {
		return
	}
	if !w.intervalRateLimit(c, v, w.Cfg.Limits.CommentInterval) {
		return
	}
	pid := id
	reportID, err := w.St.CreateReport(c.Request.Context(), v.ID, &pid, nil, nil, nil, req.Reason)
	if err != nil {
		w.err(c, 500, "举报失败")
		return
	}
	_ = w.St.NotifyStaff(c.Request.Context(), store.NoticeInput{
		UserID: v.ID, Kind: "report", Category: "report",
		Content:     "新的举报：帖子 " + strconv.FormatInt(id, 10) + " — " + req.Reason,
		TargetRoute: "report/" + strconv.FormatInt(reportID, 10),
	})
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) reportComment(c *gin.Context) {
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

	var parentPost int64
	var cAlive bool
	err := w.St.Pool.QueryRow(c.Request.Context(),
		`SELECT c.post_id, c.deleted_at IS NULL AND p.deleted_at IS NULL
		 FROM comments c JOIN posts p ON p.id=c.post_id WHERE c.id=$1`, id).Scan(&parentPost, &cAlive)
	if err != nil || !cAlive {
		w.notFound(c)
		return
	}
	if !w.reportGuard(c, v, "comment_id", id) {
		return
	}
	if !w.intervalRateLimit(c, v, w.Cfg.Limits.CommentInterval) {
		return
	}
	cid := id
	var parentRef *int64
	if parentPost > 0 {
		parentRef = &parentPost
	}
	reportID, err := w.St.CreateReport(c.Request.Context(), v.ID, parentRef, &cid, nil, nil, req.Reason)
	if err != nil {
		w.err(c, 500, "举报失败")
		return
	}
	_ = w.St.NotifyStaff(c.Request.Context(), store.NoticeInput{
		UserID: v.ID, Kind: "report", Category: "report",
		Content:     "新的举报：评论 " + strconv.FormatInt(id, 10) + " — " + req.Reason,
		TargetRoute: "report/" + strconv.FormatInt(reportID, 10),
	})
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) listComments(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	list, err := w.St.CommentsOf(c.Request.Context(), v.ID, id)
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	if list == nil {
		list = []model.Comment{}
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

type commentReq struct {
	Content       string `json:"content"`
	ParentID      *int64 `json:"parent_id"`
	ReplyToUserID *int64 `json:"reply_to_user_id"`
	ImageURL      string `json:"image_url"`
}

func (w *Web) commentImageOK(u string) bool {
	if u == "" {
		return true
	}
	if len(u) > 300 {
		return false
	}
	rel := u
	if base := strings.TrimSuffix(w.Cfg.BaseURL, "/"); base != "" && strings.HasPrefix(u, base) {
		rel = strings.TrimPrefix(u, base)
	}
	if !strings.HasPrefix(rel, "/uploads/") {
		return false
	}
	return !strings.ContainsAny(rel, "\"'()<>\n\r\t ")
}

func (w *Web) createComment(c *gin.Context) {
	v := w.viewer(c)
	if w.mutedErr(c, v) {
		return
	}
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	var req commentReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	content := strings.TrimSpace(req.Content)
	if content == "" {
		w.bad(c, "评论内容不能为空")
		return
	}
	if len([]rune(content)) > 2000 {
		w.bad(c, "评论最多 2000 字")
		return
	}
	imageURL := strings.TrimSpace(req.ImageURL)
	if !w.commentImageOK(imageURL) {
		w.bad(c, "参数错误")
		return
	}

	if !w.intervalRateLimit(c, v, w.Cfg.Limits.CommentInterval) {
		return
	}
	cm, err := w.St.CreateComment(c.Request.Context(), store.CommentCreate{
		PostID: id, AuthorID: v.ID, ParentID: req.ParentID, ReplyToUserID: req.ReplyToUserID,
		Content: content, ImageURL: imageURL,
	})
	if errors.Is(err, store.ErrBadRequest) {
		w.bad(c, "回复目标不合法")
		return
	}
	if errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "发布失败，请稍后重试")
		return
	}
	w.notifyMentions(c, v, content,
		mentionCommentContent(v.User.Nickname, store.PreviewOf(content)), mentionRoute(id))
	c.JSON(http.StatusOK, cm)
}

func (w *Web) toggleCommentLike(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	liked, count, err := w.St.ToggleCommentLike(c.Request.Context(), id, v.ID)
	if errors.Is(err, pgx.ErrNoRows) || errors.Is(err, store.ErrNotFound) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"liked": liked, "like_count": count})
}

type deleteReasonReq struct {
	Reason string `json:"reason"`
}

func (w *Web) deleteComment(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}

	var authorID int64
	var authorRole string
	err := w.St.Pool.QueryRow(c.Request.Context(),
		`SELECT c.author_id, u.role FROM comments c JOIN users u ON u.id=c.author_id
		WHERE c.id=$1 AND c.deleted_at IS NULL`, id).Scan(&authorID, &authorRole)
	if errors.Is(err, store.ErrNotFound) || errors.Is(err, pgx.ErrNoRows) {
		w.notFound(c)
		return
	}
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	mod := authorID != v.ID
	if mod && !w.requireAdmin(c) {
		return
	}
	if mod && !w.requireOver(c, authorRole) {
		return
	}
	reason := ""
	if mod {
		var req deleteReasonReq
		_ = c.ShouldBindJSON(&req)
		reason = strings.TrimSpace(req.Reason)
		if reason == "" {
			w.bad(c, "删除理由必填，将随通知发送给作者")
			return
		}
		if reasonTooLong(reason) {
			w.bad(c, "理由过长")
			return
		}
	}
	if err := w.St.DeleteComment(c.Request.Context(), id, v.ID, mod, reason); err != nil {
		w.err(c, 500, "删除失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}
