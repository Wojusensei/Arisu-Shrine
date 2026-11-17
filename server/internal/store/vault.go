package store

import (
	"context"
	"errors"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
)

// 网页端多账号档案（对齐安卓 AccountVault.kt）。
//
// 与安卓的差别只有一处：安卓把令牌存在本机 DataStore，网页端的 cookie 是
// HttpOnly（JS 读不到），所以档案落在服务端。
//
// 档案夹归属：用「当前 refresh 令牌的哈希」作主键——一台浏览器一份夹，
// 与安卓的「本机」语义等价。
//
// 令牌本身以 AES-GCM 密文入库（svc.SealToken/OpenToken），由调用方加解密；
// 本包只管存取密文，不接触明文。

// VaultAccount 一条账号档案（展示用；不含令牌）
type VaultAccount struct {
	UserID   int64  `json:"user_id"`
	No       string `json:"no"`
	Nickname string `json:"nickname"`
	Email    string `json:"email"`
	Avatar   string `json:"avatar"`
	Role     string `json:"role"`
	SavedAt  int64  `json:"saved_at"`
	Current  bool   `json:"current"`
}

// VaultList 某个档案夹里的全部账号，最近使用在前
func (s *Store) VaultList(ctx context.Context, ownerRefresh string, currentUser int64) ([]VaultAccount, error) {
	rows, err := s.Pool.Query(ctx, `
		SELECT v.user_id, u.nickname, u.role,
		       COALESCE(NULLIF(CASE WHEN u.active_avatar_enabled AND u.active_avatar_url <> ''
		                            THEN u.active_avatar_url ELSE u.fixed_avatar_url END, ''), ''),
		       u.email, EXTRACT(EPOCH FROM v.saved_at)::bigint
		FROM web_account_vault v
		JOIN users u ON u.id = v.user_id
		WHERE v.owner_hash = $1
		  AND u.anonymized = FALSE AND u.banned = FALSE AND u.password_hash <> ''
		ORDER BY v.saved_at DESC`, ownerRefresh)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]VaultAccount, 0, 8)
	for rows.Next() {
		var a VaultAccount
		if err := rows.Scan(&a.UserID, &a.Nickname, &a.Role, &a.Avatar, &a.Email, &a.SavedAt); err != nil {
			return nil, err
		}
		a.No = model.FormatUserNo(a.UserID)
		a.Current = a.UserID == currentUser
		out = append(out, a)
	}
	return out, rows.Err()
}

// VaultUpsert 记一条档案：同账号覆盖并置顶（最近使用的在前）。
// 不设数量上限——登录第 N+1 个账号时静默拒存会变成「明明登录了却切不出来」。
func (s *Store) VaultUpsert(ctx context.Context, ownerRefresh string, userID int64, sealed string) error {
	if _, err := s.Pool.Exec(ctx,
		`DELETE FROM web_account_vault WHERE token_sealed = $1 OR (owner_hash = $2 AND user_id = $3)`,
		sealed, ownerRefresh, userID); err != nil {
		return err
	}
	_, err := s.Pool.Exec(ctx, `
		INSERT INTO web_account_vault (owner_hash, user_id, token_sealed, saved_at)
		VALUES ($1, $2, $3, now())`,
		ownerRefresh, userID, sealed)
	return err
}

// VaultSealedToken 取某账号档案里的令牌密文（切换账号用）
func (s *Store) VaultSealedToken(ctx context.Context, ownerRefresh string, userID int64) (string, error) {
	var sealed string
	err := s.Pool.QueryRow(ctx, `
		SELECT token_sealed FROM web_account_vault
		WHERE owner_hash = $1 AND user_id = $2`,
		ownerRefresh, userID).Scan(&sealed)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", ErrNotFound
	}
	return sealed, err
}

// VaultRemove 把某账号从档案夹里删掉
func (s *Store) VaultRemove(ctx context.Context, ownerRefresh string, userID int64) error {
	_, err := s.Pool.Exec(ctx,
		`DELETE FROM web_account_vault WHERE owner_hash = $1 AND user_id = $2`,
		ownerRefresh, userID)
	return err
}

// 注意：本文件的 ownerRefresh 参数一律是「已归一的主键」（web 层已 hash），
// 不再在这里做二次哈希。

// VaultRotateOwner 档案夹的 refresh 被轮换时整夹换主键，
// 否则续期一次「本机账号」列表就凭空清空
func (s *Store) VaultRotateOwner(ctx context.Context, oldRefresh, newRefresh string) error {
	if oldRefresh == newRefresh {
		return nil
	}
	if _, err := s.Pool.Exec(ctx,
		`DELETE FROM web_account_vault WHERE owner_hash = $1`, hashToken(newRefresh)); err != nil {
		return err
	}
	_, err := s.Pool.Exec(ctx,
		`UPDATE web_account_vault SET owner_hash = $1 WHERE owner_hash = $2`,
		hashToken(newRefresh), hashToken(oldRefresh))
	return err
}

// VaultUpdateSealed 某账号的令牌换了（重新登录/轮换），同步密文
func (s *Store) VaultUpdateSealed(ctx context.Context, ownerRefresh string, userID int64, sealed string) error {
	_, err := s.Pool.Exec(ctx,
		`UPDATE web_account_vault SET token_sealed = $1, saved_at = now()
		 WHERE owner_hash = $2 AND user_id = $3`,
		sealed, ownerRefresh, userID)
	return err
}

// VaultDeleteBySealed 令牌作废时，把引用它的档案清掉
func (s *Store) VaultDeleteBySealed(ctx context.Context, sealed string) error {
	_, err := s.Pool.Exec(ctx,
		`DELETE FROM web_account_vault WHERE token_sealed = $1`, sealed)
	return err
}
