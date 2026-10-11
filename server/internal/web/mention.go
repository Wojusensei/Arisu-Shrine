package web

import (
	"regexp"
	"strconv"

	"github.com/gin-gonic/gin"

	"arisu/server/internal/model"
)

var mentionRe = regexp.MustCompile(`(?i)@user_0*(\d{1,12})`)

const mentionCap = 10

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

func (w *Web) notifyMentions(c *gin.Context, v *viewerCtx, text, content, route string) {
	ids := parseMentionIDs(text)
	if len(ids) == 0 {
		return
	}
	_ = w.St.NotifyMentions(c.Request.Context(), v.ID, ids, content, route)
}

func mentionPostContent(nickname, title string) string {
	return nickname + " 在帖子《" + title + "》中提及了您"
}

func mentionCommentContent(nickname, preview string) string {
	return nickname + " 在评论中提及了您：" + preview
}

func mentionRoute(postID int64) string {
	return model.FormatPostNo2(postID)
}
