package store

import (
	"context"
	"time"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
)

const StickerLimit = 64

func (s *Store) Stickers(ctx context.Context, ownerID int64) ([]model.Sticker, error) {
	rows, err := s.Pool.Query(ctx, `SELECT id, url, animated, created_at FROM stickers
		WHERE owner_id=$1 ORDER BY sort_order, id`, ownerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []model.Sticker{}
	for rows.Next() {
		var st model.Sticker
		var created time.Time
		if err := rows.Scan(&st.ID, &st.URL, &st.Animated, &created); err != nil {
			return nil, err
		}
		st.CreatedAt = Unix(created)
		out = append(out, st)
	}
	return out, rows.Err()
}

func (s *Store) AddSticker(ctx context.Context, ownerID int64, url string, animated bool) (*model.Sticker, error) {
	var st model.Sticker
	var created time.Time
	err := s.ExecTx(ctx, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, ownerID); err != nil {
			return err
		}
		var n int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM stickers WHERE owner_id=$1`, ownerID).Scan(&n); err != nil {
			return err
		}
		if n >= StickerLimit {
			return ErrTooMany
		}

		return tx.QueryRow(ctx, `INSERT INTO stickers(owner_id, url, animated, sort_order)
			VALUES($1,$2,$3, COALESCE((SELECT MIN(sort_order) FROM stickers WHERE owner_id=$1), 0) - 1)
			RETURNING id, url, animated, created_at`, ownerID, url, animated).
			Scan(&st.ID, &st.URL, &st.Animated, &created)
	})
	if isUniqueErr(err) {
		return nil, ErrConflict
	}
	if err != nil {
		return nil, err
	}
	st.CreatedAt = Unix(created)
	return &st, nil
}

func (s *Store) DeleteSticker(ctx context.Context, ownerID, stickerID int64) error {
	tag, err := s.Pool.Exec(ctx, `DELETE FROM stickers WHERE id=$1 AND owner_id=$2`, stickerID, ownerID)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (s *Store) ReorderStickers(ctx context.Context, ownerID int64, ids []int64) error {
	if len(ids) == 0 {
		return ErrBadRequest
	}
	return s.ExecTx(ctx, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, ownerID); err != nil {
			return err
		}
		var owned int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM stickers WHERE owner_id=$1 AND id = ANY($2)`,
			ownerID, ids).Scan(&owned); err != nil {
			return err
		}
		if owned != len(ids) {
			return ErrNotFound
		}
		for i, id := range ids {
			if _, err := tx.Exec(ctx, `UPDATE stickers SET sort_order=$3 WHERE id=$1 AND owner_id=$2`,
				id, ownerID, i+1); err != nil {
				return err
			}
		}
		return nil
	})
}
