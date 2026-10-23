package store

import (
	"context"
	"errors"
	"fmt"
	"math/rand"
	"strconv"
	"strings"
	"time"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

func escapeLike(q string) string {
	r := strings.NewReplacer(`\`, `\\`, `%`, `\%`, `_`, `\_`)
	return r.Replace(q)
}

func (s *Store) SearchPosts(ctx context.Context, viewerID int64, q string, useRegex bool, limit int) ([]model.Post, error) {
	if limit <= 0 || limit > 50 {
		limit = 30
	}
	var cond string
	args := []any{viewerID}
	if useRegex {
		cond = ` p.title ~ $2`
		args = append(args, q)
	} else {

		cond = ` p.title ILIKE '%' || $2 || '%'`
		args = append(args, escapeLike(q))
	}

	if id, ok := parsePostNo(q); ok {
		rows, err := s.Pool.Query(ctx, postSelect+` WHERE p.id=$2 AND p.deleted_at IS NULL`+blockFilter("p.author_id"), viewerID, id)
		if err != nil {
			return nil, err
		}
		defer rows.Close()
		var out []model.Post
		for rows.Next() {
			p, err := scanPost(rows)
			if err != nil {
				return nil, err
			}
			out = append(out, *p)
		}
		if err := rows.Err(); err != nil {
			return nil, err
		}
		if len(out) > 0 {
			return out, nil
		}
	}
	rows, err := s.Pool.Query(ctx, postSelect+` WHERE`+cond+` AND p.deleted_at IS NULL`+
		blockFilter("p.author_id")+` ORDER BY p.created_at DESC LIMIT `+fmt.Sprint(limit), args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []model.Post
	for rows.Next() {
		p, err := scanPost(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *p)
	}
	return out, rows.Err()
}

func parsePostNo(q string) (int64, bool) {
	t := strings.TrimSpace(strings.ToLower(q))
	t = strings.TrimPrefix(t, "arisu_")
	n, err := strconv.ParseInt(t, 10, 64)
	if err != nil || n <= 0 {
		return 0, false
	}
	return n, true
}

func (s *Store) SearchUsers(ctx context.Context, viewerID int64, q string, limit int) ([]model.UserBrief, error) {
	if limit <= 0 || limit > 50 {
		limit = 20
	}

	if strings.HasPrefix(strings.ToLower(q), "user_") {
		n, err := strconv.ParseInt(strings.TrimPrefix(strings.ToLower(q), "user_"), 10, 64)
		if err == nil && n > 0 {
			var id int64
			var nick, role, avatar string
			err := s.Pool.QueryRow(ctx, `SELECT `+userBriefCols+` FROM users u
				WHERE u.id=$1 AND u.anonymized=FALSE
				AND NOT EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id=$2 AND b.blocked_id=u.id)`,
				n, viewerID).Scan(&id, &nick, &role, &avatar)
			if errors.Is(err, pgx.ErrNoRows) {
				return nil, nil
			}
			if err != nil {
				return nil, err
			}
			return []model.UserBrief{scanBrief(id, nick, role, avatar)}, nil
		}
	}
	rows, err := s.Pool.Query(ctx, `SELECT `+userBriefCols+` FROM users u
		WHERE nickname ILIKE '%' || $1 || '%' AND anonymized=FALSE
		AND NOT EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id=$2 AND b.blocked_id=u.id)
		ORDER BY id LIMIT `+fmt.Sprint(limit), escapeLike(q), viewerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []model.UserBrief
	for rows.Next() {
		var id int64
		var nick, role, avatar string
		if err := rows.Scan(&id, &nick, &role, &avatar); err != nil {
			return nil, err
		}
		out = append(out, scanBrief(id, nick, role, avatar))
	}
	return out, rows.Err()
}

func (s *Store) Releases(ctx context.Context) ([]model.Release, error) {
	rows, err := s.Pool.Query(ctx, `SELECT id, version, channel, change_type, body, created_at FROM releases ORDER BY created_at DESC`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []model.Release
	for rows.Next() {
		var r model.Release
		var t time.Time
		if err := rows.Scan(&r.ID, &r.Version, &r.Channel, &r.ChangeType, &r.Body, &t); err != nil {
			return nil, err
		}
		r.CreatedAt = Unix(t)
		out = append(out, r)
	}
	return out, rows.Err()
}

func (s *Store) CreateRelease(ctx context.Context, r model.Release, creatorID int64) error {

	_, err := s.Pool.Exec(ctx, `INSERT INTO releases(version, channel, change_type, body, created_by) VALUES($1,$2,$3,$4,$5)
		ON CONFLICT (version, channel) DO UPDATE SET change_type=EXCLUDED.change_type, body=EXCLUDED.body, created_by=EXCLUDED.created_by, created_at=now()`,
		r.Version, sanitizeEnum(r.Channel, []string{"stable", "beta"}),
		sanitizeEnum(r.ChangeType, []string{"feature", "fix", "improvement", "breaking"}), r.Body, creatorID)
	return err
}

func luckBand(v int) string {
	switch {
	case v <= 10:
		return "kyou"
	case v <= 30:
		return "suekichi"
	case v <= 50:
		return "shoukichi"
	case v <= 70:
		return "chuukichi"
	case v <= 90:
		return "kichi"
	default:
		return "daikichi"
	}
}

const OmikujiTodayExpr = `(now() AT TIME ZONE 'Asia/Shanghai')::date`

var shanghaiLoc = mustShanghai()

func mustShanghai() *time.Location {
	loc, err := time.LoadLocation("Asia/Shanghai")
	if err != nil {
		return time.FixedZone("CST", 8*3600)
	}
	return loc
}

func (s *Store) OmikujiToday(ctx context.Context, userID int64) (bool, int64) {
	var luck int64
	err := s.Pool.QueryRow(ctx, `SELECT luck FROM omikuji_draws WHERE user_id=$1 AND day=`+OmikujiTodayExpr, userID).Scan(&luck)
	if err != nil {
		return false, 0
	}
	return true, luck
}

type OmikujiLeaderRow struct {
	Nickname string `json:"nickname"`
	Role     string `json:"role"`
	Avatar   string `json:"avatar"`
	Luck     int    `json:"luck"`
	Rank     int    `json:"rank"`
}

func (s *Store) OmikujiLeaderboard(ctx context.Context, viewerID int64, topRanks int) ([]OmikujiLeaderRow, error) {
	rows, err := s.Pool.Query(ctx, `
		SELECT u.nickname, u.role,
		       CASE WHEN u.active_avatar_enabled AND u.active_avatar_url <> '' THEN u.active_avatar_url ELSE u.fixed_avatar_url END,
		       d.luck
		FROM omikuji_draws d
		JOIN users u ON u.id = d.user_id
		WHERE d.day = `+OmikujiTodayExpr+` AND u.anonymized = FALSE AND u.deactivated_at IS NULL
		AND NOT EXISTS (SELECT 1 FROM blocks bl WHERE bl.blocker_id=$1 AND bl.blocked_id=u.id)
		ORDER BY d.luck DESC, d.drawn_at ASC
		LIMIT 200`, viewerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := make([]OmikujiLeaderRow, 0, topRanks)
	prevLuck := -1
	rank := 0
	for rows.Next() {
		var r OmikujiLeaderRow
		if err := rows.Scan(&r.Nickname, &r.Role, &r.Avatar, &r.Luck); err != nil {
			return nil, err
		}
		if r.Luck != prevLuck {
			rank = len(out) + 1
			prevLuck = r.Luck
		}
		if rank > topRanks {
			break
		}
		r.Rank = rank
		out = append(out, r)
	}
	return out, rows.Err()
}

func (s *Store) DrawOmikuji(ctx context.Context, userID int64) (*model.Omikuji, error) {
	var o model.Omikuji
	var day time.Time
	var drawnAt time.Time
	err := s.Pool.QueryRow(ctx, `SELECT luck, result, drawn_at, day
		FROM omikuji_draws WHERE user_id=$1 AND day=`+OmikujiTodayExpr, userID).Scan(
		&o.Luck, &o.Result, &drawnAt, &day)
	if err == nil {
		o.DrawnAt = Unix(drawnAt)
		return &o, nil
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		return nil, err
	}
	luck := rand.Intn(100) + 1
	err = s.Pool.QueryRow(ctx, `INSERT INTO omikuji_draws(user_id, day, result, luck)
		VALUES($1, `+OmikujiTodayExpr+`, $2, $3)
		RETURNING drawn_at`,
		userID, luckBand(luck), luck).Scan(&day)
	if err != nil {

		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == "23505" {
			return s.DrawOmikuji(ctx, userID)
		}
		return nil, err
	}
	if luck == 100 {
		_, _ = s.Pool.Exec(ctx, `INSERT INTO achievements(user_id, code) VALUES($1,'omikuji') ON CONFLICT DO NOTHING`, userID)
	}
	o.Luck = luck
	o.Result = luckBand(luck)
	o.DrawnAt = Unix(day)
	return &o, nil
}

func (s *Store) OmikujiHistory(ctx context.Context, userID int64, limit int) ([]model.Omikuji, error) {
	if limit <= 0 || limit > 60 {
		limit = 30
	}
	rows, err := s.Pool.Query(ctx, `SELECT result, luck, drawn_at FROM omikuji_draws
		WHERE user_id=$1 ORDER BY day DESC LIMIT `+fmt.Sprint(limit), userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []model.Omikuji
	for rows.Next() {
		var o model.Omikuji
		var t time.Time
		if err := rows.Scan(&o.Result, &o.Luck, &t); err != nil {
			return nil, err
		}
		o.DrawnAt = Unix(t)
		out = append(out, o)
	}
	return out, rows.Err()
}

func (s *Store) CreateReport(ctx context.Context, reporterID int64, postID, commentID, emaID, userID *int64, reason string) (int64, error) {
	var id int64
	err := s.Pool.QueryRow(ctx, `INSERT INTO reports(reporter_id, post_id, comment_id, ema_id, reported_user_id, reason)
		VALUES($1,$2,$3,$4,$5,$6) RETURNING id`, reporterID, postID, commentID, emaID, userID, reason).Scan(&id)
	return id, err
}

func (s *Store) Reports(ctx context.Context, status string, limit, offset int) ([]model.Report, int64, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}
	where := ""
	if status != "" && status != "all" {
		where = ` WHERE r.status='` + sanitizeEnum(status, []string{"pending", "resolved", "rejected"}) + `'`
	}
	rows, err := s.Pool.Query(ctx, `SELECT r.id, r.reason, r.status, r.created_at, r.post_id, r.comment_id, r.ema_id, r.reported_user_id,
		u.id, u.nickname, u.role,
		CASE WHEN u.active_avatar_enabled AND u.active_avatar_url<>'' THEN u.active_avatar_url ELSE u.fixed_avatar_url END
		FROM reports r JOIN users u ON u.id=r.reporter_id`+where+` ORDER BY r.created_at DESC LIMIT `+fmt.Sprint(limit)+` OFFSET `+fmt.Sprint(offset))
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var out []model.Report
	for rows.Next() {
		var rp model.Report
		var aID int64
		var aNick, aRole, aAvatar string
		var t time.Time
		if err := rows.Scan(&rp.ID, &rp.Reason, &rp.Status, &t, &rp.PostID, &rp.CommentID, &rp.EmaID, &rp.ReportedUserID,
			&aID, &aNick, &aRole, &aAvatar); err != nil {
			return nil, 0, err
		}
		rp.Reporter = scanBrief(aID, aNick, aRole, aAvatar)
		rp.CreatedAt = Unix(t)
		out = append(out, rp)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	var total int64
	if err := s.Pool.QueryRow(ctx, `SELECT count(*) FROM reports r`+where).Scan(&total); err != nil {
		return nil, 0, err
	}
	return out, total, nil
}

func (s *Store) ReportTargetAuthorRole(ctx context.Context, reportID int64) (string, bool, bool, error) {
	var role *string
	var isComment bool
	err := s.Pool.QueryRow(ctx, `SELECT CASE
			WHEN r.comment_id IS NOT NULL THEN true ELSE false END,
		COALESCE(
			(SELECT u.role FROM comments c JOIN users u ON u.id=c.author_id WHERE c.id=r.comment_id),
			(SELECT u.role FROM posts p JOIN users u ON u.id=p.author_id WHERE p.id=r.post_id),
			(SELECT u.role FROM emas e JOIN users u ON u.id=e.author_id WHERE e.id=r.ema_id))
		FROM reports r WHERE r.id=$1`, reportID).Scan(&isComment, &role)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", false, false, ErrNotFound
	}
	if err != nil {
		return "", false, false, err
	}
	if role == nil {
		return "", isComment, false, nil
	}
	return *role, isComment, true, nil
}

func (s *Store) ResolveReport(ctx context.Context, reportID, handlerID int64, approve bool, deleteTarget bool, reason string) error {
	var postID, commentID, emaID *int64
	var reporterID int64
	err := s.Pool.QueryRow(ctx, `SELECT reporter_id, post_id, comment_id, ema_id FROM reports
		WHERE id=$1 AND status='pending'`, reportID).Scan(&reporterID, &postID, &commentID, &emaID)
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrNotFound
	}
	if err != nil {
		return err
	}

	if approve && deleteTarget {

		if commentID != nil {
			if err := s.DeleteComment(ctx, *commentID, handlerID, true, reason); err != nil && !errors.Is(err, ErrNotFound) {
				return err
			}
		} else if postID != nil {
			if err := s.DeletePost(ctx, *postID, handlerID, true, reason); err != nil && !errors.Is(err, ErrNotFound) {
				return err
			}
		}
		if emaID != nil {
			if err := s.DeleteEma(ctx, *emaID, handlerID, true, reason); err != nil && !errors.Is(err, ErrNotFound) {
				return err
			}
		}
	}
	tag, err := s.Pool.Exec(ctx, `UPDATE reports SET status=CASE WHEN $2 THEN 'resolved' ELSE 'rejected' END,
		handled_by=$3, handled_at=now()
		WHERE id=$1 AND status='pending'`, reportID, approve, handlerID)
	if err != nil {
		return err
	}

	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}

	statusText := "已驳回"
	if approve {
		statusText = "已处理"
	}
	input := NoticeInput{
		UserID: reporterID, Kind: "report", Category: "report",
		Content: "您的举报" + statusText + "。感谢对神社秩序的守护。",
	}
	if !deleteTarget {
		switch {
		case postID != nil:
			input.TargetRoute = "post/" + strconv.FormatInt(*postID, 10)
		case emaID != nil:
			input.TargetRoute = "ema/" + strconv.FormatInt(*emaID, 10)
		case commentID != nil:
			var pid int64
			if err := s.Pool.QueryRow(ctx, `SELECT post_id FROM comments WHERE id=$1`, *commentID).Scan(&pid); err == nil && pid > 0 {
				input.TargetRoute = "post/" + strconv.FormatInt(pid, 10)
			}
		}
	}
	return s.CreateNotice(ctx, input)
}

func (s *Store) SaveUpload(ctx context.Context, userID int64, file, kind string) error {

	_, err := s.Pool.Exec(ctx, `INSERT INTO uploads(user_id, name, kind) VALUES($1,$2,$3)`, userID, file, kind)
	return err
}
