package web

import (
	"errors"
	"fmt"
	"net/http"
	"strings"

	"arisu/server/internal/model"
	"arisu/server/internal/store"
	"github.com/gin-gonic/gin"
)

// ---- 表情包 ----

// stickerAnimated 动图判定：只认 gif（webp 动图要解码才看得出，不值当）
func stickerAnimated(u string) bool {
	return strings.HasSuffix(strings.ToLower(u), ".gif")
}

// myStickers 自己的表情夹；带 limit 让客户端直接画「n/64」，不用硬编码
func (w *Web) myStickers(c *gin.Context) {
	v := w.viewer(c)
	items, err := w.St.Stickers(c.Request.Context(), v.ID)
	if err != nil {
		w.err(c, http.StatusInternalServerError, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, gin.H{"limit": store.StickerLimit, "total": len(items), "items": items})
}

// userStickers 别人的表情夹：可以整夹翻看，挑着存进自己的
func (w *Web) userStickers(c *gin.Context) {
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	// 拉黑后不再翻得到对方的表情夹（与 tiles 同一把尺）
	if blocked, _ := w.St.Blocks(c.Request.Context(), w.viewer(c).ID, id); blocked {
		c.JSON(http.StatusOK, gin.H{"limit": store.StickerLimit, "total": 0, "items": []model.Sticker{}})
		return
	}
	items, err := w.St.Stickers(c.Request.Context(), id)
	if err != nil {
		w.err(c, http.StatusInternalServerError, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, gin.H{"limit": store.StickerLimit, "total": len(items), "items": items})
}

// addSticker 收一张表情。只收本站图床产物（与评论附图同一条规矩：不做外链转发板）
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
		w.bad(c, "参数错误")
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

// reorderStickers 拖拽排序落库：按提交的顺序重写序号
func (w *Web) reorderStickers(c *gin.Context) {
	v := w.viewer(c)
	var req struct {
		IDs []int64 `json:"ids"`
	}
	if err := c.ShouldBindJSON(&req); err != nil || len(req.IDs) == 0 || len(req.IDs) > store.StickerLimit {
		w.bad(c, "请求格式不对")
		return
	}
	err := w.St.ReorderStickers(c.Request.Context(), v.ID, req.IDs)
	switch {
	case errors.Is(err, store.ErrNotFound):
		w.err(c, http.StatusNotFound, "表情夹已变化，请重新打开设置")
	case errors.Is(err, store.ErrBadRequest):
		w.bad(c, "请求格式不对")
	case err != nil:
		w.err(c, http.StatusInternalServerError, "排序保存失败，点击重试")
	default:
		c.JSON(http.StatusOK, gin.H{"ok": true})
	}
}
