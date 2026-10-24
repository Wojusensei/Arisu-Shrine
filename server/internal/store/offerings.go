package store

import (
	"context"
	"errors"
	"strconv"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
)

var ErrInsufficientBalance = errors.New("insufficient balance")

var ErrOfferCapPerPost = errors.New("offer cap per post")

const OfferPerPostCap = 1000

func (s *Store) GrantDailyOffer(ctx context.Context, userID int64) (int64, error) {
	var tenths int64
	err := s.Pool.QueryRow(ctx, `
		UPDATE users SET balance_tenths = balance_tenths + 500, offer_day = `+OmikujiTodayExpr+`
		WHERE id=$1 AND (offer_day IS NULL OR offer_day < `+OmikujiTodayExpr+`)
		RETURNING balance_tenths`, userID).Scan(&tenths)
	if errors.Is(err, pgx.ErrNoRows) {

		err = s.Pool.QueryRow(ctx, `SELECT balance_tenths FROM users WHERE id=$1`, userID).Scan(&tenths)
	}
	return tenths, err
}

func (s *Store) OfferPost(ctx context.Context, postID, userID, amount int64) (balanceTenths, offerTotal int64, err error) {
	err = s.ExecTx(ctx, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, userID); err != nil {
			return err
		}

		var offeredYet int64
		if err := tx.QueryRow(ctx,
			`SELECT COALESCE(SUM(amount),0) FROM offerings WHERE post_id=$1 AND user_id=$2`,
			postID, userID).Scan(&offeredYet); err != nil {
			return err
		}
		if offeredYet+amount > OfferPerPostCap {
			return ErrOfferCapPerPost
		}

		tag, err := tx.Exec(ctx, `UPDATE users SET balance_tenths = balance_tenths - $2 * 10
			WHERE id=$1 AND balance_tenths >= $2 * 10`,
			userID, amount)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return ErrInsufficientBalance
		}

		var authorID int64
		var title string
		err = tx.QueryRow(ctx, `
			UPDATE posts SET offer_total = offer_total + $2
			WHERE id=$1 AND deleted_at IS NULL
			RETURNING author_id, title, offer_total`, postID, amount).Scan(&authorID, &title, &offerTotal)
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		if authorID == userID {
			return ErrBadRequest
		}

		if _, err := tx.Exec(ctx, `UPDATE users SET balance_tenths = balance_tenths + $2 WHERE id=$1`,
			authorID, amount); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `INSERT INTO offerings(post_id, user_id, amount) VALUES($1,$2,$3)`,
			postID, userID, amount); err != nil {
			return err
		}
		if err := createNotice(tx, ctx, NoticeInput{
			UserID:      authorID,
			Kind:        "offer",
			Category:    "reply",
			Content:     nicknameOf(tx, ctx, userID) + " 向您的帖子《" + title + "》奉纳了 " + strconv.FormatInt(amount, 10) + " 円",
			TargetRoute: model.FormatPostNo2(postID),
			ActorID:     userID,
		}); err != nil {
			return err
		}
		return nil
	})
	if err != nil {
		return 0, 0, err
	}
	balanceTenths, err = s.BalanceOf(ctx, userID)
	if err != nil {
		return 0, offerTotal, err
	}
	return balanceTenths, offerTotal, nil
}

func (s *Store) BalanceOf(ctx context.Context, userID int64) (int64, error) {
	var b int64
	err := s.Pool.QueryRow(ctx, `SELECT balance_tenths FROM users WHERE id=$1`, userID).Scan(&b)
	return b, err
}
