package store

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"time"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
)

var EmaStates = []string{"open", "in_progress", "resolved", "closed"}
var emaTags = []string{"enhance", "bug", "questions", "tips"}

const emaSelect = `SELECT e.id, e.title, e.body, e.tags, e.state, e.reply_count, e.created_at, e.updated_at,
	u.id, u.nickname, u.role,
	CASE WHEN u.active_avatar_enabled AND u.active_avatar_url<>'' THEN u.active_avatar_url ELSE u.fixed_avatar_url END
	FROM emas e JOIN users u ON u.id=e.author_id`

func scanEma(row pgx.Row) (*model.Ema, error) {
	var e model.Ema
	var aID int64
	var aNick, aRole, aAvatar string
	var created, updated time.Time
	err := row.Scan(&e.ID, &e.Title, &e.Body, &e.Tags, &e.State, &e.ReplyCount, &created, &updated,
		&aID, &aNick, &aRole, &aAvatar)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	e.Author = scanBrief(aID, aNick, aRole, aAvatar)
	e.CreatedAt = Unix(created)
	e.UpdatedAt = Unix(updated)
	return &e, nil
}

type EmaListQuery struct {
	Tag    string
	Tags   []string
	State  string
	States []string
	Sort   string
	Limit  int
	Offset int
}

func (s *Store) EmasPage(ctx context.Context, q EmaListQuery) (*model.EmaPage, error) {
	if q.Limit <= 0 || q.Limit > 50 {
		q.Limit = 20
	}
	where := ` WHERE e.deleted_at IS NULL`
	if q.Tag != "" && q.Tag != "all" {
		q.Tags = append(q.Tags, sanitizeEnum(q.Tag, emaTags))
	}
	if len(q.Tags) > 0 {

		where += ` AND e.tags && ARRAY[`
		for i, t := range q.Tags {
			if i > 0 {
				where += ","
			}
			where += `'` + sanitizeEnum(t, emaTags) + `'`
		}
		where += `]::text[]`
	}
	if q.State != "" && q.State != "all" {
		q.States = append(q.States, sanitizeEnum(q.State, EmaStates))
	}
	if len(q.States) > 0 {
		where += ` AND e.state IN (`
		for i, st := range q.States {
			if i > 0 {
				where += ","
			}
			where += `'` + sanitizeEnum(st, EmaStates) + `'`
		}
		where += `)`
	}
	order := ` ORDER BY e.created_at DESC`
	if q.Sort == "old" {
		order = ` ORDER BY e.created_at`
	}
	rows, err := s.Pool.Query(ctx, emaSelect+where+order+fmt.Sprintf(` LIMIT %d OFFSET %d`, q.Limit, q.Offset))
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	page := &model.EmaPage{Page: model.Page{Page: q.Offset/q.Limit + 1, PageSize: q.Limit}}
	for rows.Next() {
		e, err := scanEma(rows)
		if err != nil {
			return nil, err
		}
		page.Items = append(page.Items, *e)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	err = s.Pool.QueryRow(ctx, `SELECT count(*) FROM emas e`+where).Scan(&page.Total)
	return page, err
}

func (s *Store) EmaByID(ctx context.Context, id int64) (*model.Ema, error) {
	return scanEma(s.Pool.QueryRow(ctx, emaSelect+` WHERE e.id=$1 AND e.deleted_at IS NULL`, id))
}

type EmaCreate struct {
	AuthorID int64
	Title    string
	Body     string
	Tags     []string
}

func (s *Store) CreateEma(ctx context.Context, c EmaCreate) (*model.Ema, error) {
	tags := sanitizeTags(c.Tags)
	var id int64
	err := s.ExecTx(ctx, func(tx pgx.Tx) error {
		if err := tx.QueryRow(ctx, `INSERT INTO emas(author_id, title, body, tags) VALUES($1,$2,$3,$4) RETURNING id`,
			c.AuthorID, c.Title, c.Body, tags).Scan(&id); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `INSERT INTO achievements(user_id, code) VALUES($1,'first_ema') ON CONFLICT DO NOTHING`, c.AuthorID)
		return err
	})
	if err != nil {
		return nil, err
	}
	return s.EmaByID(ctx, id)
}

func sanitizeTags(tags []string) []string {
	out := make([]string, 0, len(emaTags))
	seen := make(map[string]struct{}, len(emaTags))
	for _, t := range tags {
		legal := false
		for _, a := range emaTags {
			if t == a {
				legal = true
				break
			}
		}
		if !legal {
			continue
		}
		if _, dup := seen[t]; dup {
			continue
		}
		seen[t] = struct{}{}
		out = append(out, t)
		if len(out) == len(emaTags) {
			break
		}
	}
	return out
}

type EmaUpdate struct {
	Title *string
	Body  *string
	Tags  *[]string
}

func (s *Store) UpdateEma(ctx context.Context, id int64, u EmaUpdate) error {

	return s.ExecTx(ctx, func(tx pgx.Tx) error {
		if u.Title != nil {
			if _, err := tx.Exec(ctx, `UPDATE emas SET title=$1, updated_at=now() WHERE id=$2`, *u.Title, id); err != nil {
				return err
			}
		}
		if u.Body != nil {
			if _, err := tx.Exec(ctx, `UPDATE emas SET body=$1, updated_at=now() WHERE id=$2`, *u.Body, id); err != nil {
				return err
			}
		}
		if u.Tags != nil {
			if _, err := tx.Exec(ctx, `UPDATE emas SET tags=$1, updated_at=now() WHERE id=$2`, sanitizeTags(*u.Tags), id); err != nil {
				return err
			}
		}
		return nil
	})
}

func (s *Store) SetEmaState(ctx context.Context, id int64, state string) error {
	_, err := s.Pool.Exec(ctx, `UPDATE emas SET state=$2, updated_at=now() WHERE id=$1`,
		id, sanitizeEnum(state, EmaStates))
	return err
}

func (s *Store) DeleteEma(ctx context.Context, id, operatorID int64, mod bool, reason string) error {
	return s.ExecTx(ctx, func(tx pgx.Tx) error {
		var authorID int64

		err := tx.QueryRow(ctx, `UPDATE emas SET deleted_at=now() WHERE id=$1 AND deleted_at IS NULL
			RETURNING author_id`, id).Scan(&authorID)
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		if mod && authorID != operatorID {
			return createNotice(tx, ctx, NoticeInput{
				UserID: authorID, Kind: "system", Category: "system",
				Content: fmt.Sprintf("您的绘马 #%d 因违反社区规则已被删除。理由：%s", id, reason),
			})
		}
		return nil
	})
}

func (s *Store) EmaReplies(ctx context.Context, emaID int64) ([]model.EmaReply, error) {
	rows, err := s.Pool.Query(ctx, `SELECT r.id, r.content, r.created_at,
		u.id, u.nickname, u.role,
		CASE WHEN u.active_avatar_enabled AND u.active_avatar_url<>'' THEN u.active_avatar_url ELSE u.fixed_avatar_url END
		FROM ema_replies r JOIN users u ON u.id=r.author_id
		WHERE r.ema_id=$1 AND r.deleted_at IS NULL ORDER BY r.created_at`, emaID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []model.EmaReply
	for rows.Next() {
		var r model.EmaReply
		var aID int64
		var aNick, aRole, aAvatar string
		var created time.Time
		if err := rows.Scan(&r.ID, &r.Content, &created, &aID, &aNick, &aRole, &aAvatar); err != nil {
			return nil, err
		}
		r.EmaID = emaID
		r.Author = scanBrief(aID, aNick, aRole, aAvatar)
		r.CreatedAt = Unix(created)
		out = append(out, r)
	}
	return out, rows.Err()
}

func (s *Store) AddEmaReply(ctx context.Context, emaID, authorID int64, content string) (*model.EmaReply, error) {
	var id int64
	err := s.ExecTx(ctx, func(tx pgx.Tx) error {

		var emaAuthor int64
		var title string
		var alive bool
		if err := tx.QueryRow(ctx, `SELECT author_id, title, deleted_at IS NULL FROM emas WHERE id=$1`, emaID).
			Scan(&emaAuthor, &title, &alive); err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				return ErrNotFound
			}
			return err
		}
		if !alive {
			return ErrNotFound
		}
		if err := tx.QueryRow(ctx, `INSERT INTO ema_replies(ema_id, author_id, content) VALUES($1,$2,$3) RETURNING id`,
			emaID, authorID, content).Scan(&id); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE emas SET reply_count=reply_count+1 WHERE id=$1`, emaID); err != nil {
			return err
		}
		if emaAuthor != authorID {
			return createNotice(tx, ctx, NoticeInput{
				UserID: emaAuthor, Kind: "reply", Category: "reply",
				Content:     fmt.Sprintf("%s 回复了您的绘马《%s》：%s", nicknameOf(tx, ctx, authorID), title, PreviewOf(content)),
				TargetRoute: "ema/" + strconv.FormatInt(emaID, 10),
			})
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	replies, err := s.EmaReplies(ctx, emaID)
	if err != nil {
		return nil, err
	}
	for _, r := range replies {
		if r.ID == id {
			return &r, nil
		}
	}
	return nil, ErrNotFound
}

func (s *Store) DeleteEmaReply(ctx context.Context, emaID, replyID, operatorID int64, mod bool, reason string) error {
	return s.ExecTx(ctx, func(tx pgx.Tx) error {
		var authorID int64
		err := tx.QueryRow(ctx, `UPDATE ema_replies SET deleted_at=now()
			WHERE id=$1 AND ema_id=$2 AND deleted_at IS NULL RETURNING author_id`,
			replyID, emaID).Scan(&authorID)
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE emas SET reply_count=GREATEST(reply_count-1,0) WHERE id=$1`, emaID); err != nil {
			return err
		}
		if mod && authorID != operatorID {
			return createNotice(tx, ctx, NoticeInput{
				UserID: authorID, Kind: "system", Category: "system",
				Content: "您的绘马回复因违反社区规则已被删除。理由：" + reason,
			})
		}
		return nil
	})
}
