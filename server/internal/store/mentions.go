package store

import (
	"context"
	"regexp"
	"strconv"

	"arisu/server/internal/model"
)

var mentionIDRe = regexp.MustCompile(`(?i)@user_0*(\d{1,12})`)

const mentionScanCap = 64

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

func (s *Store) FillPostMentions(ctx context.Context, posts []model.Post) {
	s.fillMentions(ctx, len(posts), func(i int) string { return posts[i].Body },
		func(i int, m model.UserBrief) { posts[i].Mentions = append(posts[i].Mentions, m) })
}

func (s *Store) FillCommentMentions(ctx context.Context, comments []model.Comment) {
	s.fillMentions(ctx, len(comments), func(i int) string { return comments[i].Content },
		func(i int, m model.UserBrief) { comments[i].Mentions = append(comments[i].Mentions, m) })
}
