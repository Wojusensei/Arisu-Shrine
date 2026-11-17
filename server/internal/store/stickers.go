package store

import (
	"context"
	"time"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
)

// StickerLimit 每人表情夹上限（产品定值，客户端同值画「n/64」）
const StickerLimit = 64

// Stickers 某人的表情夹：按自定义顺序排（sort_order 小的在前），同值按 id 兜底
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

// AddSticker 收一张表情（自己的，或从别人那儿存下来的）。到上限返回 ErrTooMany。
// 上限校验与插入同锁同事务：并发连点不会突破 64
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
		// 新收的排在最前：sort_order 比现有最小值再小一格（0 是「没排过序」的哨兵，
		// 所以从 -1 起步往下走），用户拖拽过顺序后仍然保持「新收的先看见」
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

// DeleteSticker 只能删自己的；不是本人的返回 ErrNotFound
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

// ReorderStickers 按给定顺序重写 sort_order（拖拽排序落库）。
// 只认自己的表情；ids 里出现别人的 id 直接 ErrNotFound，不做半截写入。
// 列表可能落后于服务端（比如刚在别处收了一张）：只重排给到的这些，其余保持原序号
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
