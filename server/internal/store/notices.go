package store

import (
	"context"
	"errors"
	"fmt"
	"regexp"
	"strings"
	"time"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type NoticeInput struct {
	UserID      int64
	Kind        string
	Category    string
	Content     string
	TargetRoute string
	DedupeKey   string

	ActorID int64
}

type rowQuerier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

func blockedFrom(ctx context.Context, q rowQuerier, target, actor int64) bool {
	if actor == 0 || target == actor {
		return false
	}
	var one int
	if err := q.QueryRow(ctx,
		`SELECT 1 FROM blocks WHERE blocker_id=$1 AND blocked_id=$2`, target, actor).Scan(&one); err != nil {
		return false
	}
	return true
}

func createNoticeDirect(ctx context.Context, pool *pgxpool.Pool, n NoticeInput) error {
	if blockedFrom(ctx, pool, n.UserID, n.ActorID) {
		return nil
	}
	if n.DedupeKey != "" {

		_, err := pool.Exec(ctx, `INSERT INTO notices(user_id, kind, category, content, target_route, dedupe_key, actor_id)
			VALUES($1,$2,$3,$4,$5,$6,NULLIF($7,0))
			ON CONFLICT (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL DO UPDATE SET content=EXCLUDED.content, created_at=now(), read_at=NULL, actor_id=EXCLUDED.actor_id`,
			n.UserID, n.Kind, n.Category, n.Content, n.TargetRoute, n.DedupeKey, n.ActorID)
		return err
	}
	_, err := pool.Exec(ctx, `INSERT INTO notices(user_id, kind, category, content, target_route, dedupe_key, actor_id)
		VALUES($1,$2,$3,$4,$5,NULL,NULLIF($6,0))`,
		n.UserID, n.Kind, n.Category, n.Content, n.TargetRoute, n.ActorID)
	return err
}

func createNotice(tx pgx.Tx, ctx context.Context, n NoticeInput) error {
	if blockedFrom(ctx, tx, n.UserID, n.ActorID) {
		return nil
	}
	if n.DedupeKey != "" {

		_, err := tx.Exec(ctx, `INSERT INTO notices(user_id, kind, category, content, target_route, dedupe_key, actor_id)
			VALUES($1,$2,$3,$4,$5,$6,NULLIF($7,0))
			ON CONFLICT (user_id, dedupe_key) WHERE dedupe_key IS NOT NULL DO UPDATE SET content=EXCLUDED.content, created_at=now(), read_at=NULL, actor_id=EXCLUDED.actor_id`,
			n.UserID, n.Kind, n.Category, n.Content, n.TargetRoute, n.DedupeKey, n.ActorID)
		return err
	}
	_, err := tx.Exec(ctx, `INSERT INTO notices(user_id, kind, category, content, target_route, actor_id)
		VALUES($1,$2,$3,$4,$5,NULLIF($6,0))`, n.UserID, n.Kind, n.Category, n.Content, n.TargetRoute, n.ActorID)
	return err
}

func (s *Store) CreateNotice(ctx context.Context, n NoticeInput) error {
	return s.ExecTx(ctx, func(tx pgx.Tx) error { return createNotice(tx, ctx, n) })
}

func (s *Store) UnreadNoticeCount(ctx context.Context, userID int64) (int64, error) {
	var n int64
	err := s.Pool.QueryRow(ctx, `SELECT count(*) FROM notices WHERE user_id=$1 AND read_at IS NULL`+actorBlockFilter(), userID).Scan(&n)
	return n, err
}

func nicknameOf(tx pgx.Tx, ctx context.Context, userID int64) string {
	var nickname string
	if err := tx.QueryRow(ctx, `SELECT nickname FROM users WHERE id=$1`, userID).Scan(&nickname); err != nil {
		return "有人"
	}
	return nickname
}

func notifyTrackers(tx pgx.Tx, ctx context.Context, postID int64, title, actor, preview string, skipID, actorID int64) error {
	rows, err := tx.Query(ctx, `SELECT user_id FROM post_tracks WHERE post_id=$1`, postID)
	if err != nil {
		return err
	}
	defer rows.Close()
	var ids []int64
	for rows.Next() {
		var id int64
		if err := rows.Scan(&id); err != nil {
			return err
		}
		ids = append(ids, id)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	for _, id := range ids {
		if id == skipID {
			continue
		}

		if err := createNotice(tx, ctx, NoticeInput{
			UserID: id, Kind: "tracking", Category: "reply",
			Content:     fmt.Sprintf("%s 在您追踪的帖子《%s》发表了新评论：%s", actor, title, preview),
			TargetRoute: model.FormatPostNo2(postID),
			ActorID:     actorID,
		}); err != nil {
			return err
		}
	}
	return nil
}

func NotifyTrackersEvent(ctx context.Context, pool *pgxpool.Pool, postID int64, title, event string, authorID int64) {
	rows, err := pool.Query(ctx, `SELECT user_id FROM post_tracks WHERE post_id=$1`, postID)
	if err != nil {
		return
	}
	defer rows.Close()
	var ids []int64
	for rows.Next() {
		var id int64
		if rows.Scan(&id) == nil {
			ids = append(ids, id)
		}
	}
	rows.Close()

	day := time.Now().Format("20060102")
	for _, id := range ids {
		_ = createNoticeDirect(ctx, pool, NoticeInput{
			UserID: id, Kind: "tracking", Category: "reply",
			Content:     fmt.Sprintf("您追踪的帖子《%s》%s", title, event),
			TargetRoute: model.FormatPostNo2(postID),
			ActorID:     authorID,
			DedupeKey:   fmt.Sprintf("tracked:%d:%s:%s", postID, event, day),
		})
	}
}

func notifyFollowersOfNewPost(tx pgx.Tx, ctx context.Context, authorID int64, postID int64, title string) error {
	rows, err := tx.Query(ctx, `SELECT f.follower_id, u.notify_scope
		FROM follows f JOIN users u ON u.id=f.follower_id WHERE f.followee_id=$1
		AND NOT EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id=f.follower_id AND b.blocked_id=$1)`, authorID)
	if err != nil {
		return err
	}
	defer rows.Close()
	type follower struct {
		id    int64
		scope map[string]bool
	}
	var list []follower
	for rows.Next() {
		var f follower
		if err := rows.Scan(&f.id, &f.scope); err != nil {
			return err
		}
		list = append(list, f)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	author := nicknameOf(tx, ctx, authorID)
	for _, f := range list {
		allow, ok := f.scope["follow_update"]
		if !ok {
			allow = true
		}
		if !allow {
			continue
		}
		if err := createNotice(tx, ctx, NoticeInput{
			UserID: f.id, Kind: "follow", Category: "system",
			Content:     fmt.Sprintf("您关注的 %s 发布了新帖子《%s》", author, title),
			TargetRoute: model.FormatPostNo2(postID),
			ActorID:     authorID,
		}); err != nil {
			return err
		}
	}
	return nil
}

func (s *Store) NotifyFollowersProfileChange(ctx context.Context, userID int64, what string) error {
	return s.ExecTx(ctx, func(tx pgx.Tx) error {
		rows, err := tx.Query(ctx, `SELECT f.follower_id, u.notify_scope
			FROM follows f JOIN users u ON u.id=f.follower_id WHERE f.followee_id=$1
			AND NOT EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id=f.follower_id AND b.blocked_id=$1)`, userID)
		if err != nil {
			return err
		}
		defer rows.Close()
		type follower struct {
			id    int64
			scope map[string]bool
		}
		var list []follower
		for rows.Next() {
			var f follower
			if err := rows.Scan(&f.id, &f.scope); err != nil {
				return err
			}
			list = append(list, f)
		}
		if err := rows.Err(); err != nil {
			return err
		}
		name := nicknameOf(tx, ctx, userID)
		key := "nickname"
		if what == "avatar" {
			key = "follow_avatar"
		}
		for _, f := range list {
			allow, ok := f.scope[key]
			if !ok {
				allow = key == "nickname"
			}
			if !allow {
				continue
			}
			msg := fmt.Sprintf("您关注的 %s 修改了昵称", name)
			if what == "avatar" {
				msg = fmt.Sprintf("您关注的 %s 更换了头像", name)
			}
			if err := createNotice(tx, ctx, NoticeInput{
				UserID: f.id, Kind: "follow", Category: "system",
				Content:     msg,
				TargetRoute: fmt.Sprintf("user/%d", userID),
				ActorID:     userID,
				DedupeKey:   fmt.Sprintf("profile:%d:%s:%s", userID, what, time.Now().Format("20060102")),
			}); err != nil {
				return err
			}
		}
		return nil
	})
}

func (s *Store) NotifyStaff(ctx context.Context, n NoticeInput) error {
	rows, err := s.Pool.Query(ctx,
		`SELECT id FROM users WHERE role IN ('houshi','guuji','kannushi')
	 AND banned=FALSE AND anonymized=FALSE AND deactivated_at IS NULL AND password_hash<>''`)
	if err != nil {
		return err
	}
	defer rows.Close()
	var ids []int64
	for rows.Next() {
		var id int64
		if err := rows.Scan(&id); err != nil {
			return err
		}
		ids = append(ids, id)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	return s.ExecTx(ctx, func(tx pgx.Tx) error {
		for _, staffID := range ids {
			n.UserID = staffID
			if err := createNotice(tx, ctx, n); err != nil {
				return err
			}
		}
		return nil
	})
}

func (s *Store) NotifyMentions(ctx context.Context, actorID int64, ids []int64, content, route string) error {
	if len(ids) == 0 {
		return nil
	}
	rows, err := s.Pool.Query(ctx, `SELECT id FROM users
		WHERE id = ANY($1) AND id <> $2 AND anonymized=FALSE AND banned=FALSE
		AND deactivated_at IS NULL AND password_hash<>''
		AND NOT EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id=users.id AND b.blocked_id=$2)`, ids, actorID)
	if err != nil {
		return err
	}
	defer rows.Close()
	var targets []int64
	for rows.Next() {
		var id int64
		if err := rows.Scan(&id); err != nil {
			return err
		}
		targets = append(targets, id)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	if len(targets) == 0 {
		return nil
	}
	day := time.Now().Format("20060102")
	return s.ExecTx(ctx, func(tx pgx.Tx) error {
		for _, id := range targets {
			if err := createNotice(tx, ctx, NoticeInput{
				UserID: id, Kind: "mention", Category: "reply",
				Content:     content,
				TargetRoute: route,
				DedupeKey:   fmt.Sprintf("mention:%s:%d:%s", route, id, day),
				ActorID:     actorID,
			}); err != nil {
				return err
			}
		}
		return nil
	})
}

func (s *Store) Notices(ctx context.Context, userID int64, category, only string, limit, offset int) (*model.NoticePage, error) {
	if limit <= 0 || limit > 50 {
		limit = 30
	}
	where := ` WHERE user_id=$1` + actorBlockFilter()
	args := []any{userID}
	if category != "" && category != "all" {
		if category == "reply" || category == "system" || category == "report" {
			where += fmt.Sprintf(` AND category='%s'`, category)
		}
	}
	switch only {
	case "unread":
		where += ` AND read_at IS NULL`
	case "starred":
		where += ` AND starred_at IS NOT NULL`
	}
	var unread int64
	if err := s.Pool.QueryRow(ctx, `SELECT count(*) FROM notices WHERE user_id=$1 AND read_at IS NULL`+actorBlockFilter(), userID).Scan(&unread); err != nil {
		return nil, err
	}
	var total int64
	if err := s.Pool.QueryRow(ctx, `SELECT count(*) FROM notices`+where, args...).Scan(&total); err != nil {
		return nil, err
	}
	rows, err := s.Pool.Query(ctx, `SELECT id, kind, category, content, target_route, read_at IS NOT NULL, starred_at IS NOT NULL, created_at
		FROM notices`+where+fmt.Sprintf(` ORDER BY created_at DESC LIMIT %d OFFSET %d`, limit, offset), args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	page := &model.NoticePage{Page: model.Page{Page: offset/limit + 1, PageSize: limit}, Unread: unread}
	for rows.Next() {
		var n model.Notice
		var created time.Time
		if err := rows.Scan(&n.ID, &n.Kind, &n.Category, &n.Content, &n.TargetRoute, &n.Read, &n.Starred, &created); err != nil {
			return nil, err
		}
		n.CreatedAt = Unix(created)
		page.Items = append(page.Items, n)
	}
	page.Total = total
	return page, rows.Err()
}

func (s *Store) Notice(ctx context.Context, userID, noticeID int64) (*model.Notice, error) {
	var n model.Notice
	var created time.Time
	err := s.Pool.QueryRow(ctx, `SELECT id, kind, category, content, target_route, read_at IS NOT NULL, starred_at IS NOT NULL, created_at
		FROM notices WHERE user_id=$1 AND id=$2`+actorBlockFilter(), userID, noticeID).
		Scan(&n.ID, &n.Kind, &n.Category, &n.Content, &n.TargetRoute, &n.Read, &n.Starred, &created)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	n.CreatedAt = Unix(created)
	return &n, nil
}

func (s *Store) MarkNoticeRead(ctx context.Context, userID, noticeID int64) error {
	_, err := s.Pool.Exec(ctx, `UPDATE notices SET read_at=now() WHERE id=$1 AND user_id=$2 AND read_at IS NULL`, noticeID, userID)
	return err
}

func (s *Store) MarkNoticeUnread(ctx context.Context, userID, noticeID int64) error {
	_, err := s.Pool.Exec(ctx, `UPDATE notices SET read_at=NULL WHERE id=$1 AND user_id=$2 AND read_at IS NOT NULL`, noticeID, userID)
	return err
}

func (s *Store) StarNotice(ctx context.Context, userID, noticeID int64, starred bool) error {
	_, err := s.Pool.Exec(ctx, `UPDATE notices SET starred_at = CASE WHEN $3 THEN now() ELSE NULL END
		WHERE id=$1 AND user_id=$2`, noticeID, userID, starred)
	return err
}

func (s *Store) MarkAllNoticesRead(ctx context.Context, userID int64) error {
	_, err := s.Pool.Exec(ctx, `UPDATE notices SET read_at=now() WHERE user_id=$1 AND read_at IS NULL`, userID)
	return err
}

func (s *Store) DeleteNotice(ctx context.Context, userID, noticeID int64) error {
	_, err := s.Pool.Exec(ctx, `DELETE FROM notices WHERE id=$1 AND user_id=$2`, noticeID, userID)
	return err
}

var imgRe = regexp.MustCompile(`!\[[^\]]*\]\(([^)\s]+)`)

func firstImageURL(body string) string {
	m := imgRe.FindStringSubmatch(body)
	if len(m) >= 2 {
		return m[1]
	}
	return ""
}

var spaceRe = regexp.MustCompile(`\s+`)

func PreviewOf(s string) string {
	s = spaceRe.ReplaceAllString(strings.TrimSpace(s), " ")
	r := []rune(s)
	if len(r) > 24 {
		return string(r[:24]) + "…"
	}
	return s
}

func (s *Store) PostAuthorID(ctx context.Context, postID int64) (int64, error) {
	var id int64
	err := s.Pool.QueryRow(ctx, `SELECT author_id FROM posts WHERE id=$1`, postID).Scan(&id)
	if errors.Is(err, pgx.ErrNoRows) {
		return 0, ErrNotFound
	}
	return id, err
}
