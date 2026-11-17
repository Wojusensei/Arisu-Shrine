package web

import (
	"regexp"
	"strconv"

	"github.com/gin-gonic/gin"

	"arisu/server/internal/model"
)

// ---- @提及 ----
// 语法固定为 @User_编号（昵称不唯一，无法消歧；编号是全站唯一标识，
// 客户端提及选择器插入的就是这个形态）。解析按大小写不敏感、容忍多余前导零。

var mentionRe = regexp.MustCompile(`(?i)@user_0*(\d{1,12})`)

// mentionCap 单条内容最多提及人数：正文 32000 字理论上能塞几千个 @，
// 无封顶等于给通知表开了条注水管道
const mentionCap = 10

// parseMentionIDs 从文本解析被提及的用户编号：去重、剔除非法、封顶 mentionCap
func parseMentionIDs(text string) []int64 {
	seen := make(map[int64]bool, 4)
	var out []int64
	for _, m := range mentionRe.FindAllStringSubmatch(text, 128) {
		id, err := strconv.ParseInt(m[1], 10, 64)
		if err != nil || id <= 0 || seen[id] {
			continue
		}
		seen[id] = true
		out = append(out, id)
		if len(out) >= mentionCap {
			break
		}
	}
	return out
}

// notifyMentions 给帖子/评论中被提及的社员发通知。内容与目标路由由调用方给：
// route 即 TargetRoute，也进去重键——同帖同被提及者按天聚合，反复编辑/追评不刷屏
func (w *Web) notifyMentions(c *gin.Context, v *viewerCtx, text, content, route string) {
	ids := parseMentionIDs(text)
	if len(ids) == 0 {
		return
	}
	_ = w.St.NotifyMentions(c.Request.Context(), v.ID, ids, content, route)
}

// mentionPostContent 帖子正文提及的通知文案
func mentionPostContent(nickname, title string) string {
	return nickname + " 在帖子《" + title + "》中提及了您"
}

// mentionCommentContent 评论提及的通知文案（带评论预览，同追踪通知的样式）
func mentionCommentContent(nickname, preview string) string {
	return nickname + " 在评论中提及了您：" + preview
}

// mentionRoute 帖子路由（提及通知的跳转目标与去重键成分）
func mentionRoute(postID int64) string {
	return model.FormatPostNo2(postID)
}
