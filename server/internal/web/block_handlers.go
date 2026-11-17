package web

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

// 拉黑（静默）：被拉黑方不会收到任何提示；管理侧没有查看「谁拉黑了谁」的接口。

// blockUser 拉黑某人
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

// unblockUser 解除拉黑（幂等）
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

// myBlocks 我拉黑的人
func (w *Web) myBlocks(c *gin.Context) {
	v := w.viewer(c)
	list, err := w.St.BlockedUsers(c.Request.Context(), v.ID)
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}
