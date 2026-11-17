package store

import (
	"context"
	"regexp"
	"strconv"

	"arisu/server/internal/model"
)

// ---- @提及的读侧解析：把正文里 @User_编号 提到的社员解析成简报附在 DTO 上，
// 客户端渲染时用昵称顶替编号展示。正文原文不动——编辑回显仍是编号形态，
// 昵称不唯一无法反解析，编号才是提及的稳定锚点 ----

// mentionIDRe 与 web 层通知解析同一把尺：@User_编号（大小写不敏感、容忍前导零）
var mentionIDRe = regexp.MustCompile(`(?i)@user_0*(\d{1,12})`)

// mentionScanCap 单条内容最多扫描的提及匹配数，防恶意正文把解析拖长
const mentionScanCap = 64

// parseMentionIDsOrdered 单条文本的提及编号（去重、保持出现顺序）
func parseMentionIDsOrdered(text string) []int64 {
	seen := map[int64]bool{}
	var out []int64
	for _, m := range mentionIDRe.FindAllStringSubmatch(text, mentionScanCap) {
		id, err := strconv.ParseInt(m[1], 10, 64)
		if err != nil || id <= 0 || seen[id] {
			continue
		}
		seen[id] = true
		out = append(out, id)
	}
	return out
}

// mentionBriefs 批量取提及者简报；解析失败静默返回空（展示回退为编号，不影响读帖）
func (s *Store) mentionBriefs(ctx context.Context, ids []int64) (map[int64]model.UserBrief, error) {
	out := make(map[int64]model.UserBrief, len(ids))
	if len(ids) == 0 {
		return out, nil
	}
	rows, err := s.Pool.Query(ctx, `SELECT `+userBriefCols+` FROM users u WHERE u.id = ANY($1)`, ids)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	for rows.Next() {
		var id int64
		var nick, role, avatar string
		if err := rows.Scan(&id, &nick, &role, &avatar); err != nil {
			return nil, err
		}
		out[id] = scanBrief(id, nick, role, avatar)
	}
	return out, rows.Err()
}

// fillMentions 的通用骨架：整页合并一次编号去重、一条 IN 查询，再按条落回各自 DTO
func (s *Store) fillMentions(ctx context.Context, n int, text func(i int) string, assign func(i int, m model.UserBrief)) {
	perItem := make([][]int64, n)
	unique := make([]int64, 0, 8)
	seen := map[int64]bool{}
	for i := 0; i < n; i++ {
		for _, id := range parseMentionIDsOrdered(text(i)) {
			perItem[i] = append(perItem[i], id)
			if !seen[id] {
				seen[id] = true
				unique = append(unique, id)
			}
		}
	}
	if len(unique) == 0 {
		return
	}
	briefs, err := s.mentionBriefs(ctx, unique)
	if err != nil {
		return
	}
	for i := 0; i < n; i++ {
		for _, id := range perItem[i] {
			if b, ok := briefs[id]; ok {
				assign(i, b)
			}
		}
	}
}

// FillPostMentions 给一页帖子附上各自正文里的提及者简报
func (s *Store) FillPostMentions(ctx context.Context, posts []model.Post) {
	s.fillMentions(ctx, len(posts), func(i int) string { return posts[i].Body },
		func(i int, m model.UserBrief) { posts[i].Mentions = append(posts[i].Mentions, m) })
}

// FillCommentMentions 给一页评论附上各自内容里的提及者简报
func (s *Store) FillCommentMentions(ctx context.Context, comments []model.Comment) {
	s.fillMentions(ctx, len(comments), func(i int) string { return comments[i].Content },
		func(i int, m model.UserBrief) { comments[i].Mentions = append(comments[i].Mentions, m) })
}
