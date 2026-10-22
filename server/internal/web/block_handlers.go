package web

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

func (w *Web) blockUser(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	if id == v.ID {
		w.bad(c, "不能拉黑您自己")
		return
	}
	if _, err := w.St.UserByID(c.Request.Context(), id); err != nil {
		w.notFound(c)
		return
	}
	if err := w.St.BlockUser(c.Request.Context(), v.ID, id); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true, "blocked": true})
}

func (w *Web) unblockUser(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	if err := w.St.UnblockUser(c.Request.Context(), v.ID, id); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true, "blocked": false})
}

func (w *Web) myBlocks(c *gin.Context) {
	v := w.viewer(c)
	list, err := w.St.BlockedUsers(c.Request.Context(), v.ID)
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}
