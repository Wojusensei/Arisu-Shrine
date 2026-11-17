package store

import (
	"context"
	"errors"
	"strconv"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
)

// ---- 赛钱（奉纳）----
// 每日上线 +50（初始 100）；给帖子奉纳任意整数（不得超余额），
// 作者收取 10% 向下取整，其余沉没——沉没部分是通缩阀，防通胀。

// ErrInsufficientBalance 余额不够，web 层翻译成友好文案
var ErrInsufficientBalance = errors.New("insufficient balance")

// ErrOfferCapPerPost 单帖累计奉纳超上限
var ErrOfferCapPerPost = errors.New("offer cap per post")

// OfferPerPostCap 每人给单个帖子的累计奉纳上限（円）
const OfferPerPostCap = 1000

// GrantDailyOffer 每日首次上线发 50 円，幂等（同一天再调不发）。
// 日界与求签同一把尺：Asia/Shanghai 的自然日。
func (s *Store) GrantDailyOffer(ctx context.Context, userID int64) (int64, error) {
	var tenths int64
	err := s.Pool.QueryRow(ctx, `
		UPDATE users SET balance_tenths = balance_tenths + 500, offer_day = `+OmikujiTodayExpr+`
		WHERE id=$1 AND (offer_day IS NULL OR offer_day < `+OmikujiTodayExpr+`)
		RETURNING balance_tenths`, userID).Scan(&tenths)
	if errors.Is(err, pgx.ErrNoRows) {
		// 今天已领过：回当前余额
		err = s.Pool.QueryRow(ctx, `SELECT balance_tenths FROM users WHERE id=$1`, userID).Scan(&tenths)
	}
	return tenths, err
}

// OfferPost 向帖子奉纳。扣款/入账/计数/流水/通知同事务；
// 并发双花用同用户锁挡（同 ToggleUp 的做法）。
func (s *Store) OfferPost(ctx context.Context, postID, userID, amount int64) (balanceTenths, offerTotal int64, err error) {
	err = s.ExecTx(ctx, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, userID); err != nil {
			return err
		}
		// 单帖上限：同一人对同一帖的累计奉纳封顶（心意有度，灌水无门）
		var offeredYet int64
		if err := tx.QueryRow(ctx,
			`SELECT COALESCE(SUM(amount),0) FROM offerings WHERE post_id=$1 AND user_id=$2`,
			postID, userID).Scan(&offeredYet); err != nil {
			return err
		}
		if offeredYet+amount > OfferPerPostCap {
			return ErrOfferCapPerPost
		}
		// 先扣自己：整数円付款 = 扣 amount×10 tenths；不足在更新行数上挡掉，不会负数
		tag, err := tx.Exec(ctx, `UPDATE users SET balance_tenths = balance_tenths - $2 * 10
			WHERE id=$1 AND balance_tenths >= $2 * 10`,
			userID, amount)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return ErrInsufficientBalance
		}
		// 帖子计数顺带取作者与标题（通知文案用）；软删帖不可奉纳
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
		// 作者收 10%：amount 円的十分之一恰为 amount 个 tenths——精确累积，零头不丢
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

// BalanceOf 当前账面（单位 0.1 円；奉纳响应与 /me 刷新用）
func (s *Store) BalanceOf(ctx context.Context, userID int64) (int64, error) {
	var b int64
	err := s.Pool.QueryRow(ctx, `SELECT balance_tenths FROM users WHERE id=$1`, userID).Scan(&b)
	return b, err
}
