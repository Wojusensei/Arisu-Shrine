package web

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"

	"arisu/server/internal/model"
	"arisu/server/internal/store"
	"github.com/gin-gonic/gin"
)

func (w *Web) listNotices(c *gin.Context) {
	v := w.viewer(c)
	page, size := clampPage(c)
	np, err := w.St.Notices(c.Request.Context(), v.ID, c.Query("category"), c.Query("only"), size, (page-1)*size)
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	if np.Items == nil {
		np.Items = []model.Notice{}
	}
	c.JSON(http.StatusOK, np)
}

func (w *Web) getNotice(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	n, err := w.St.Notice(c.Request.Context(), v.ID, id)
	if err != nil {
		w.err(c, 404, "通知不存在或已被删除")
		return
	}
	c.JSON(http.StatusOK, n)
}

type noticeStarReq struct {
	Starred bool `json:"starred"`
}

func (w *Web) starNotice(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	var req noticeStarReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	if err := w.St.StarNotice(c.Request.Context(), v.ID, id, req.Starred); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) markNoticeUnread(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	if err := w.St.MarkNoticeUnread(c.Request.Context(), v.ID, id); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) unreadCount(c *gin.Context) {
	v := w.viewer(c)
	np, err := w.St.Notices(c.Request.Context(), v.ID, "", "", 1, 0)
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"unread": np.Unread})
}

func (w *Web) readNotice(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	if err := w.St.MarkNoticeRead(c.Request.Context(), v.ID, id); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) readAllNotices(c *gin.Context) {
	v := w.viewer(c)
	if err := w.St.MarkAllNoticesRead(c.Request.Context(), v.ID); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) deleteNotice(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	if err := w.St.DeleteNotice(c.Request.Context(), v.ID, id); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) listReports(c *gin.Context) {
	if !w.requireAdmin(c) {
		return
	}
	page, size := clampPage(c)
	list, total, err := w.St.Reports(c.Request.Context(), c.DefaultQuery("status", "pending"), size, (page-1)*size)
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	if list == nil {
		list = []model.Report{}
	}
	c.JSON(http.StatusOK, gin.H{"items": list, "total": total, "page": page, "page_size": size})
}

type resolveReq struct {
	Approve      bool   `json:"approve"`
	DeleteTarget bool   `json:"delete_target"`
	Reason       string `json:"reason"`
}

func (w *Web) resolveReport(c *gin.Context) {
	if !w.requireAdmin(c) {
		return
	}
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	var req resolveReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	if req.DeleteTarget && strings.TrimSpace(req.Reason) == "" {
		w.bad(c, "删除内容时理由必填")
		return
	}
	if reasonTooLong(req.Reason) {
		w.bad(c, "理由过长")
		return
	}

	if req.Approve && req.DeleteTarget {
		authorRole, isComment, hasTarget, err := w.St.ReportTargetAuthorRole(c.Request.Context(), id)
		if errors.Is(err, store.ErrNotFound) {
			w.notFound(c)
			return
		}
		if err != nil {
			w.err(c, 500, "查询失败")
			return
		}
		if isComment && hasTarget && !w.requireOver(c, authorRole) {
			return
		}
	}
	if err := w.St.ResolveReport(c.Request.Context(), id, v.ID, req.Approve, req.DeleteTarget, req.Reason); err != nil {

		if errors.Is(err, store.ErrNotFound) {
			w.notFound(c)
			return
		}
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func (w *Web) search(c *gin.Context) {
	v := w.viewer(c)

	if !w.intervalRateLimit(c, v, 2*time.Second) {
		return
	}
	q := strings.TrimSpace(c.Query("q"))
	if q == "" {
		w.bad(c, "请输入关键词")
		return
	}
	if len(q) > 100 {
		w.bad(c, "关键词太长了")
		return
	}
	useRegex := c.Query("regex") == "1" || c.Query("regex") == "true"
	result := &model.SearchResult{Users: []model.UserBrief{}, Posts: []model.Post{}}
	posts, err := w.St.SearchPosts(c.Request.Context(), v.ID, q, useRegex, 30)
	if err != nil {
		if useRegex {
			w.bad(c, "正则表达式不合法或无法解析")
			return
		}
		w.err(c, 500, "搜索失败，点击重试")
		return
	}
	users, err := w.St.SearchUsers(c.Request.Context(), v.ID, q, 20)
	if err != nil {
		w.err(c, 500, "搜索失败，点击重试")
		return
	}
	result.Posts = posts
	result.Users = users
	if result.Posts == nil {
		result.Posts = []model.Post{}
	}
	c.JSON(http.StatusOK, result)
}

func (w *Web) onlineStats(c *gin.Context) {
	st, err := w.St.OnlineStats(c.Request.Context())
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, st)
}

func (w *Web) omikujiToday(c *gin.Context) {
	v := w.viewer(c)
	drawn, luck := w.St.OmikujiToday(c.Request.Context(), v.ID)
	c.JSON(http.StatusOK, gin.H{"drawn": drawn, "luck": luck})
}

func (w *Web) drawOmikuji(c *gin.Context) {
	v := w.viewer(c)
	o, err := w.St.DrawOmikuji(c.Request.Context(), v.ID)
	if err != nil {
		w.err(c, 500, "求签失败，请稍后重试")
		return
	}
	c.JSON(http.StatusOK, o)
}

func (w *Web) omikujiHistory(c *gin.Context) {
	v := w.viewer(c)
	list, err := w.St.OmikujiHistory(c.Request.Context(), v.ID, 30)
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

func (w *Web) omikujiLeaderboard(c *gin.Context) {
	list, err := w.St.OmikujiLeaderboard(c.Request.Context(), w.viewer(c).ID, 10)
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

func (w *Web) listReleases(c *gin.Context) {
	list, err := w.St.Releases(c.Request.Context())
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

type releaseReq struct {
	Version    string `json:"version"`
	Channel    string `json:"channel"`
	ChangeType string `json:"change_type"`
	Body       string `json:"body"`
}

func (w *Web) createRelease(c *gin.Context) {
	if !w.requireGuuji(c) {
		return
	}
	v := w.viewer(c)
	var req releaseReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	if !versionRe.MatchString(strings.TrimSpace(req.Version)) {
		w.bad(c, "版本号格式如 v0.3.1")
		return
	}
	if strings.TrimSpace(req.Body) == "" {
		w.bad(c, "更新内容不能为空")
		return
	}
	if reasonTooLong(req.Body) {
		w.bad(c, "更新内容过长")
		return
	}
	r := model.Release{Version: strings.TrimSpace(req.Version), Channel: req.Channel,
		ChangeType: req.ChangeType, Body: req.Body}
	if err := w.St.CreateRelease(c.Request.Context(), r, v.ID); err != nil {
		w.err(c, 500, "发布失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func apkFileFor(dir, version string) string {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return ""
	}
	for _, e := range entries {
		if e.IsDir() || !strings.EqualFold(filepath.Ext(e.Name()), ".apk") {
			continue
		}
		for _, want := range []string{version, strings.TrimPrefix(version, "v")} {
			if want != "" && strings.Contains(e.Name(), want) {
				return e.Name()
			}
		}
	}
	return ""
}

func (w *Web) checkUpdate(c *gin.Context) {
	mine := c.Query("version")
	channel := c.DefaultQuery("channel", "stable")
	releases, err := w.St.Releases(c.Request.Context())
	if err != nil {
		w.err(c, 500, "检查失败，请稍后重试")
		return
	}
	var latest *model.Release
	for i := range releases {
		r := releases[i]
		if r.Channel == channel {
			latest = &r
			break
		}
	}
	resp := gin.H{"update_available": false}
	if latest != nil {
		resp["latest_version"] = latest.Version
		resp["channel"] = latest.Channel
		available := compareVersions(latest.Version, mine) > 0
		resp["update_available"] = available
		if available {
			resp["release"] = *latest

			if apk := apkFileFor(w.Cfg.Upload.Dir, latest.Version); apk != "" {
				resp["download_url"] = strings.TrimSuffix(w.Cfg.BaseURL, "/") + "/uploads/" + apk
			}
		}
	}
	c.JSON(http.StatusOK, resp)
}

var versionRe = regexp.MustCompile(`^v?\d+(\.\d+){0,4}(-[\w.]+)?$`)

func compareVersions(a, b string) int {
	pa, pb := parseVersion(a), parseVersion(b)
	for i := 0; i < 5; i++ {
		if pa[i] != pb[i] {
			if pa[i] > pb[i] {
				return 1
			}
			return -1
		}
	}
	return 0
}

func parseVersion(v string) [5]int {
	v = strings.TrimPrefix(strings.TrimSpace(v), "v")
	if i := strings.IndexByte(v, '-'); i >= 0 {
		v = v[:i]
	}
	var out [5]int
	for i, part := range strings.SplitN(v, ".", 5) {
		if i > 4 {
			break
		}
		out[i], _ = strconv.Atoi(strings.TrimSpace(part))
	}
	return out
}

var allowedExt = map[string]bool{".jpg": true, ".jpeg": true, ".png": true, ".gif": true, ".webp": true}

func (w *Web) upload(c *gin.Context) {
	v := w.viewer(c)

	if w.mutedErr(c, v) {
		return
	}
	fh, err := c.FormFile("file")
	if err != nil {
		w.bad(c, "请选择文件")
		return
	}
	if fh.Size > w.Cfg.Upload.MaxBytes {
		w.bad(c, "图片不能超过 10MB")
		return
	}
	ext := strings.ToLower(filepath.Ext(fh.Filename))
	if !allowedExt[ext] {
		w.bad(c, "仅支持 jpg/png/gif/webp 图片")
		return
	}
	src, err := fh.Open()
	if err != nil {
		w.err(c, 500, "读取失败")
		return
	}
	defer src.Close()

	head := make([]byte, 512)
	nread, _ := io.ReadFull(src, head)

	if nread == 0 {
		w.bad(c, "文件内容不是图片")
		return
	}
	if ct := http.DetectContentType(head[:nread]); !strings.HasPrefix(ct, "image/") {
		w.bad(c, "文件内容不是图片")
		return
	}
	if _, err := src.Seek(0, 0); err != nil {
		w.err(c, 500, "读取失败")
		return
	}

	day := time.Now().Format("20060102")
	qk := "upload:day:" + itoa(int(v.ID)) + ":" + day
	if n, err := w.RDB.Incr(c.Request.Context(), qk).Result(); err != nil {

		println("upload quota incr failed:", err.Error())
	} else {
		if n == 1 {
			w.RDB.Expire(c.Request.Context(), qk, 24*time.Hour)
		}
		if n > 100 {
			w.err(c, http.StatusTooManyRequests, "今日上传次数已达上限（100 张）")
			return
		}
	}

	refundQuota := func() {
		w.RDB.Decr(c.Request.Context(), qk)
	}
	buf := make([]byte, 8)
	_, _ = rand.Read(buf)
	name := time.Now().Format("200601") + "/" + hex.EncodeToString(buf) + ext
	dst := filepath.Join(w.Cfg.Upload.Dir, name)
	if err := os.MkdirAll(filepath.Dir(dst), 0o755); err != nil {
		refundQuota()
		w.err(c, 500, "保存失败")
		return
	}
	out, err := os.Create(dst)
	if err != nil {
		refundQuota()
		w.err(c, 500, "保存失败")
		return
	}
	defer out.Close()
	if _, err := io.Copy(out, src); err != nil {
		refundQuota()
		w.err(c, 500, "保存失败")
		return
	}
	if err := w.St.SaveUpload(c.Request.Context(), v.ID, name, "image"); err != nil {

		println("upload audit failed:", err.Error())
	}
	c.JSON(http.StatusOK, gin.H{"url": strings.TrimSuffix(w.Cfg.BaseURL, "/") + "/uploads/" + name})
}
