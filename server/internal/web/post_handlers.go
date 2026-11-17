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
	"github.com/jackc/pgx/v5"
)

// postBoards 帖子板块白名单（与 store.postsWhere 同一份名单）
var postBoards = []string{"main", "gensokyo", "dev"}

type postBodyReq struct {
	Board string `json:"board"`
	Title string `json:"title"`
	Body  string `json:"body"`
	// Intro 用指针：编辑时客户端会显式发空串表示「清空介绍语」，
	// 值类型会把空串当成「未提供」跳过，介绍语一旦设置就永远删不掉
	Intro  *string `json:"intro"`
	Cover  *string `json:"cover"`
	Reason string  `json:"reason"`
}

// boardValid 板块白名单：非法值直接 400，不走 sanitizeEnum 的静默回落——
// 那会把坏请求的帖子悄悄挪进 main 板块
func boardValid(b string) bool {
	if b == "" {
		return true // 空=默认 main
	}
	for _, x := range postBoards {
		if b == x {
			return true
		}
	}
	return false
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
	if !boardValid(req.Board) {
		w.bad(c, "参数错误")
		return
	}
	intro := ""
	if req.Intro != nil {
		intro = strings.TrimSpace(*req.Intro)
		if len([]rune(intro)) > 60 {
			w.bad(c, "介绍语最多 60 字")
			return
		}
	}
	var cover string
	if req.Cover != nil {
		cover = strings.TrimSpace(*req.Cover)
		if len([]rune(cover)) > 300 {
			w.bad(c, "封面地址过长")
			return
		}
		// 封面会下发进收藏夹封面等公开位置，与博客/头像地址同规：仅 http(s)
		lower := strings.ToLower(cover)
		if cover != "" && !strings.HasPrefix(lower, "http://") && !strings.HasPrefix(lower, "https://") {
			w.bad(c, "参数错误")
			return
		}
	}
	// 限频在参数校验全部通过后才开始计时：
	// 此前提交非法 JSON/超长正文也会烧掉整个窗口，合法用户被自己的失败请求限频
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
	// 禁言者改自己旧帖绕过 mutedErr 的发布拦截，禁言就形同虚设
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
	if req.Intro != nil {
		// 空串=清空介绍语（客户端编辑页清掉输入框后显式发 ""）
		intro := strings.TrimSpace(*req.Intro)
		if len([]rune(intro)) > 60 {
			w.bad(c, "介绍语最多 60 字")
			return
		}
		up.Intro = &intro
	}
	if req.Board != "" {
		// 板块白名单：store 的 sanitizeEnum 会把非法值静默回落成 main，
		// 编辑走那条路等于一条坏请求就把帖子挪了板块
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
		// 与 createPost 同规：仅 http(s)
		lower := strings.ToLower(cover)
		if cover != "" && !strings.HasPrefix(lower, "http://") && !strings.HasPrefix(lower, "https://") {
			w.bad(c, "参数错误")
			return
		}
		up.Cover = &cover
	}
	// 编辑也要限频（评论级间隔）：追踪通知已按天聚合，这里拦的是端点本身
	// 被脚本化反复编辑打写库
	if !w.intervalRateLimit(c, v, w.Cfg.Limits.CommentInterval) {
		return
	}
	if err := w.St.UpdatePost(c.Request.Context(), id, up); err != nil {
		w.err(c, 500, "保存失败")
		return
	}
	if np, err := w.St.PostByID(c.Request.Context(), v.ID, id); err == nil {
		store.NotifyTrackersEvent(c.Request.Context(), w.St.Pool, id, np.Title, "有新的编辑", np.Author.ID)
		w.notifyMentions(c, v, req.Body,
			mentionPostContent(v.User.Nickname, np.Title), mentionRoute(id))
		c.JSON(http.StatusOK, np)
		return
	}
	// 重取失败但保存已成功：回更新前的帖子保住 id——客户端契约要拿编号做跳转，
	// 回 {"ok":true} 会被解析成零值帖、跳到 post/0 的 404；旧内容随后会被详情页重取覆盖
	c.JSON(http.StatusOK, p)
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
		// 帖子是整体：违规帖连同里面所有人的评论一起删，任何维护人员可执行（2026-10-02 用户裁决）。
		// 「下不能管上」只约束单条评论的删除
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
		store.NotifyTrackersEvent(c.Request.Context(), w.St.Pool, id, p.Title, "已被管理删除： "+req.Reason, p.Author.ID)
		c.JSON(http.StatusOK, gin.H{"ok": true})
		return
	}
	if err := w.St.DeletePost(c.Request.Context(), id, v.ID, false, ""); err != nil {
		w.err(c, 500, "删除失败")
		return
	}
	store.NotifyTrackersEvent(c.Request.Context(), w.St.Pool, id, p.Title, "已被作者删除", p.Author.ID)
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

type flagResp struct {
	OK      bool  `json:"ok"`
	Upped   bool  `json:"upped,omitempty"`
	Tracked bool  `json:"tracked,omitempty"`
	Favored bool  `json:"favorited,omitempty"`
	Count   int64 `json:"count"`
}

// offerPost 向帖子奉纳赛钱：整数、不超余额、不能给自己；作者收 10%
func (w *Web) offerPost(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	var req struct {
		Amount int64 `json:"amount"`
	}
	if err := c.ShouldBindJSON(&req); err != nil || req.Amount <= 0 || req.Amount > 1_000_000 {
		w.bad(c, "奉纳金额需为 1 到 100 万之间的整数")
		return
	}
	// 奉纳间隔限频：连点是手滑，不是心意
	if !w.intervalRateLimitMsg(c, v, 2*time.Second, "奉纳太频繁，稍候片刻") {
		return
	}
	balanceTenths, offerTotal, err := w.St.OfferPost(c.Request.Context(), id, v.ID, req.Amount)
	switch {
	case errors.Is(err, store.ErrNotFound):
		w.notFound(c)
	case errors.Is(err, store.ErrBadRequest):
		w.bad(c, "不能向自己的帖子奉纳")
	case errors.Is(err, store.ErrInsufficientBalance):
		w.bad(c, "赛钱不足，请明日再来")
	case errors.Is(err, store.ErrOfferCapPerPost):
		w.bad(c, "单个帖子每名社员累计最多奉纳 1000 円")
	case err != nil:
		w.err(c, 500, "操作失败")
	default:
		c.JSON(http.StatusOK, gin.H{"balance_tenths": balanceTenths, "offer_total": offerTotal})
	}
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
	// 目标必须存在且未删（否则 FK 违反变 500）
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

// reportComment 举报单条评论（与帖子举报分流，处理者能看到具体对象）
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
	// 目标必须存在且未删，所属帖子也得活着；顺带记录评论所属帖子，「前往查看」才能直达
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

// commentImageOK 校验评论附图：只收本站图床产物（相对 /uploads/ 或本站 base_url 前缀的绝对地址），
// 外链一律拒绝——评论区不做任意图床的转发板。空串 = 没附图。
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
	// 限频在参数校验通过后才开始计时（同 createPost）
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
	// 查作者
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
