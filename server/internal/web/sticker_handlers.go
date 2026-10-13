package web

import (
	"errors"
	"fmt"
	"net/http"
	"strings"

	"arisu/server/internal/store"
	"github.com/gin-gonic/gin"
)

func stickerAnimated(u string) bool {
	return strings.HasSuffix(strings.ToLower(u), ".gif")
}

func (w *Web) myStickers(c *gin.Context) {
	v := w.viewer(c)
	items, err := w.St.Stickers(c.Request.Context(), v.ID)
	if err != nil {
		w.err(c, http.StatusInternalServerError, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, gin.H{"limit": store.StickerLimit, "total": len(items), "items": items})
}

func (w *Web) userStickers(c *gin.Context) {
	id, ok := pathID(c, "id")
	if !ok {
		return
	}
	items, err := w.St.Stickers(c.Request.Context(), id)
	if err != nil {
		w.err(c, http.StatusInternalServerError, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, gin.H{"limit": store.StickerLimit, "total": len(items), "items": items})
}

func (w *Web) addSticker(c *gin.Context) {
	v := w.viewer(c)
	var req struct {
		URL string `json:"url"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "请求格式不对")
		return
	}
	req.URL = strings.TrimSpace(req.URL)
	if req.URL == "" || !w.commentImageOK(req.URL) {
		w.bad(c, "表情只能来自本站图床")
		return
	}
	st, err := w.St.AddSticker(c.Request.Context(), v.ID, req.URL, stickerAnimated(req.URL))
	switch {
	case errors.Is(err, store.ErrTooMany):
		w.bad(c, fmt.Sprintf("表情夹最多 %d 张，先删几张再加", store.StickerLimit))
	case errors.Is(err, store.ErrConflict):
		w.err(c, http.StatusConflict, "这张表情已经收过了")
	case err != nil:
		w.err(c, http.StatusInternalServerError, "保存失败，点击重试")
	default:
		c.JSON(http.StatusOK, st)
	}
}

func (w *Web) deleteSticker(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		return
	}
	err := w.St.DeleteSticker(c.Request.Context(), v.ID, id)
	switch {
	case errors.Is(err, store.ErrNotFound):
		w.err(c, http.StatusNotFound, "这张表情已经不在了")
	case err != nil:
		w.err(c, http.StatusInternalServerError, "删除失败，点击重试")
	default:
		c.JSON(http.StatusOK, gin.H{"ok": true})
	}
}
