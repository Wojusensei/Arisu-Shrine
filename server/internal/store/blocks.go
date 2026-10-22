package store

import (
	"context"
	"errors"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
)

func blockFilter(authorExpr string) string {
	return ` AND NOT EXISTS (SELECT 1 FROM blocks bl WHERE bl.blocker_id=$1 AND bl.blocked_id=` + authorExpr + `)`
}

func actorBlockFilter() string {
	return ` AND (actor_id IS NULL OR NOT EXISTS (SELECT 1 FROM blocks bl WHERE bl.blocker_id=$1 AND bl.blocked_id=actor_id))`
}

func (s *Store) Blocks(ctx context.Context, blockerID, blockedID int64) (bool, error) {
	if blockerID == 0 || blockedID == 0 || blockerID == blockedID {
		return false, nil
	}
	var one int
	err := s.Pool.QueryRow(ctx,
		`SELECT 1 FROM blocks WHERE blocker_id=$1 AND blocked_id=$2`, blockerID, blockedID).Scan(&one)
	if errors.Is(err, pgx.ErrNoRows) {
		return false, nil
	}
	return err == nil, err
}

func (s *Store) BlockUser(ctx context.Context, blockerID, blockedID int64) error {
	_, err := s.Pool.Exec(ctx,
		`INSERT INTO blocks(blocker_id, blocked_id) VALUES($1,$2) ON CONFLICT DO NOTHING`,
		blockerID, blockedID)
	return err
}

func (s *Store) UnblockUser(ctx context.Context, blockerID, blockedID int64) error {
	_, err := s.Pool.Exec(ctx,
		`DELETE FROM blocks WHERE blocker_id=$1 AND blocked_id=$2`, blockerID, blockedID)
	return err
}

func (s *Store) BlockedUsers(ctx context.Context, viewerID int64) ([]model.UserBrief, error) {
	rows, err := s.Pool.Query(ctx, `SELECT `+userBriefCols+` FROM users u
		JOIN blocks b ON b.blocked_id=u.id
		WHERE b.blocker_id=$1 ORDER BY b.created_at DESC`, viewerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []model.UserBrief{}
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
