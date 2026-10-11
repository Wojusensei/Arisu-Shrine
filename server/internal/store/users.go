package store

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var ErrNotFound = errors.New("not found")
var ErrConflict = errors.New("conflict")
var ErrBadRequest = errors.New("bad request")
var ErrTooMany = errors.New("too many")

type UserRow struct {
	ID                  int64
	Email               string
	PasswordHash        string
	Nickname            string
	Role                string
	Signature           string
	StatusEmoji         string
	StatusText          string
	BlogURL             string
	FixedAvatarURL      string
	ActiveAvatarURL     string
	ActiveAvatarEnabled bool
	CustomMarkdown      string
	CustomCSS           string
	ShowFields          map[string]bool
	NotifyScope         map[string]bool
	OnlineMinutes       int64
	MutedUntil          *time.Time
	Banned              bool
	DeactivatedAt       *time.Time
	CreatedAt           time.Time
}

const userColsRaw = `id,email,password_hash,nickname,role,signature,status_emoji,status_text,
	blog_url,fixed_avatar_url,active_avatar_url,active_avatar_enabled,custom_markdown,custom_css,
	show_fields,notify_scope,online_minutes,muted_until,banned,deactivated_at,created_at`

func qcols(prefix string) string {
	parts := strings.Split(userColsRaw, ",")
	for i := range parts {
		part := strings.TrimSpace(parts[i])
		if prefix != "" {
			part = prefix + "." + part
		}
		parts[i] = part
	}
	return strings.Join(parts, ",")
}

var userCols = qcols("")

func scanUser(row pgx.Row) (*UserRow, error) {
	var u UserRow
	err := row.Scan(&u.ID, &u.Email, &u.PasswordHash, &u.Nickname, &u.Role, &u.Signature,
		&u.StatusEmoji, &u.StatusText, &u.BlogURL, &u.FixedAvatarURL, &u.ActiveAvatarURL,
		&u.ActiveAvatarEnabled, &u.CustomMarkdown, &u.CustomCSS, &u.ShowFields, &u.NotifyScope,
		&u.OnlineMinutes, &u.MutedUntil, &u.Banned, &u.DeactivatedAt, &u.CreatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	return &u, err
}

func (u *UserRow) show(key string) bool {
	if v, ok := u.ShowFields[key]; ok {
		return v
	}
	return true
}

func (u *UserRow) DisplayAvatar() string {
	if u.ActiveAvatarEnabled && u.ActiveAvatarURL != "" {
		return u.ActiveAvatarURL
	}
	return u.FixedAvatarURL
}

func (u *UserRow) MutedUntilActive() *time.Time {
	if u.MutedUntil == nil || u.MutedUntil.Before(time.Now()) {
		return nil
	}
	return u.MutedUntil
}

func (u *UserRow) ToPublic() *model.User {
	m := &model.User{
		Signature:      u.Signature,
		StatusEmoji:    u.StatusEmoji,
		StatusText:     u.StatusText,
		BlogURL:        u.BlogURL,
		CustomMarkdown: u.CustomMarkdown,
		CustomCSS:      u.CustomCSS,
		OnlineMinutes:  u.OnlineMinutes,
	}
	if !u.show("signature") {
		m.Signature = ""
	}
	if !u.show("status") {
		m.StatusEmoji, m.StatusText = "", ""
	}
	if !u.show("blog") {
		m.BlogURL = ""
	}
	if !u.show("custom_area") {
		m.CustomMarkdown, m.CustomCSS = "", ""
	}
	if !u.show("online_hours") {
		m.OnlineMinutes = 0
	}

	if v, ok := u.ShowFields["email"]; ok && v {
		m.Email = u.Email
	}
	m.ID = u.ID
	m.No = model.FormatUserNo(u.ID)
	m.Nickname = u.Nickname
	m.Role = u.Role
	m.Avatar = u.DisplayAvatar()
	m.JoinedAt = Unix(u.CreatedAt)
	return m
}

func (u *UserRow) ToBrief() model.UserBrief {
	return model.UserBrief{
		ID: u.ID, No: model.FormatUserNo(u.ID), Nickname: u.Nickname,
		Role: u.Role, Avatar: u.DisplayAvatar(),
	}
}

func (u *UserRow) ToMe() *model.Me {
	pub := *u.ToPublic()

	pub.Signature = u.Signature
	pub.StatusEmoji, pub.StatusText = u.StatusEmoji, u.StatusText
	pub.BlogURL = u.BlogURL
	pub.CustomMarkdown, pub.CustomCSS = u.CustomMarkdown, u.CustomCSS
	pub.OnlineMinutes = u.OnlineMinutes
	return &model.Me{
		User:                pub,
		Email:               u.Email,
		FixedAvatarURL:      u.FixedAvatarURL,
		ActiveAvatarURL:     u.ActiveAvatarURL,
		ActiveAvatarEnabled: u.ActiveAvatarEnabled,
		ShowFields:          u.ShowFields,
		NotifyScope:         u.NotifyScope,
		MutedUntil:          UnixPtr(u.MutedUntilActive()),
		DeactivatedAt:       UnixPtr(u.DeactivatedAt),
	}
}

func (s *Store) UserByID(ctx context.Context, id int64) (*UserRow, error) {
	return scanUser(s.Pool.QueryRow(ctx, `SELECT `+userCols+` FROM users WHERE id=$1`, id))
}

func (s *Store) UserByEmail(ctx context.Context, email string) (*UserRow, error) {
	return scanUser(s.Pool.QueryRow(ctx, `SELECT `+userCols+` FROM users WHERE email=$1`, email))
}

func (s *Store) UserCount(ctx context.Context) (int64, error) {
	var n int64
	err := s.Pool.QueryRow(ctx, `SELECT count(*) FROM users`).Scan(&n)
	return n, err
}

func (s *Store) CreateUser(ctx context.Context, email, passwordHash, nickname string) (*UserRow, error) {

	row := s.Pool.QueryRow(ctx, `INSERT INTO users(email,password_hash,nickname,role)
		VALUES($1,$2,$3,CASE WHEN NOT EXISTS(SELECT 1 FROM users) THEN $4 ELSE $5 END)
		RETURNING `+userCols, email, passwordHash, nickname, model.RoleKannushi, model.RoleMember)
	u, err := scanUser(row)
	if err != nil {
		if isUniqueErr(err) {
			return nil, ErrConflict
		}
		return nil, err
	}

	if u.ID <= 100 {
		_ = s.GrantAchievement(ctx, u.ID, "founder")
	}
	return u, nil
}

func isUniqueErr(err error) bool {
	var pgErr interface{ SQLState() string }
	if errors.As(err, &pgErr) {
		return pgErr.SQLState() == "23505"
	}
	return false
}

type ProfileUpdate struct {
	Nickname            *string
	Signature           *string
	StatusEmoji         *string
	StatusText          *string
	BlogURL             *string
	CustomMarkdown      *string
	CustomCSS           *string
	FixedAvatarURL      *string
	ActiveAvatarURL     *string
	ActiveAvatarEnabled *bool
	ShowFields          map[string]bool
	NotifyScope         map[string]bool
}

func (s *Store) UpdateProfile(ctx context.Context, userID int64, p ProfileUpdate) (*UserRow, error) {
	sets := []string{}
	args := []any{}
	i := 1
	add := func(col string, val any) {
		sets = append(sets, fmt.Sprintf("%s=$%d", col, i))
		args = append(args, val)
		i++
	}
	if p.Nickname != nil {
		add("nickname", *p.Nickname)
	}
	if p.Signature != nil {
		add("signature", *p.Signature)
	}
	if p.StatusEmoji != nil {
		add("status_emoji", *p.StatusEmoji)
	}
	if p.StatusText != nil {
		add("status_text", *p.StatusText)
	}
	if p.BlogURL != nil {
		add("blog_url", *p.BlogURL)
	}
	if p.CustomMarkdown != nil {
		add("custom_markdown", *p.CustomMarkdown)
	}
	if p.CustomCSS != nil {
		add("custom_css", *p.CustomCSS)
	}
	if p.FixedAvatarURL != nil {
		add("fixed_avatar_url", *p.FixedAvatarURL)
	}
	if p.ActiveAvatarURL != nil {
		add("active_avatar_url", *p.ActiveAvatarURL)
	}
	if p.ActiveAvatarEnabled != nil {
		add("active_avatar_enabled", *p.ActiveAvatarEnabled)
	}
	if p.ShowFields != nil {

		sets = append(sets, fmt.Sprintf("show_fields = COALESCE(show_fields,'{}'::jsonb) || $%d", i))
		args = append(args, p.ShowFields)
		i++
	}
	if p.NotifyScope != nil {
		sets = append(sets, fmt.Sprintf("notify_scope = COALESCE(notify_scope,'{}'::jsonb) || $%d", i))
		args = append(args, p.NotifyScope)
		i++
	}
	if len(sets) == 0 {
		return s.UserByID(ctx, userID)
	}
	args = append(args, userID)
	q := `UPDATE users SET ` + strings.Join(sets, ", ") + ` WHERE id=$` + fmt.Sprint(i) + ` RETURNING ` + userCols
	return scanUser(s.Pool.QueryRow(ctx, q, args...))
}

func (s *Store) UpdateEmail(ctx context.Context, userID int64, email string) error {
	_, err := s.Pool.Exec(ctx, `UPDATE users SET email=$2 WHERE id=$1`, userID, email)
	if isUniqueErr(err) {
		return ErrConflict
	}
	return err
}

func (s *Store) UpdatePassword(ctx context.Context, userID int64, hash string) error {
	_, err := s.Pool.Exec(ctx, `UPDATE users SET password_hash=$1 WHERE id=$2`, hash, userID)
	return err
}

func (s *Store) Deactivate(ctx context.Context, userID int64) error {
	_, err := s.Pool.Exec(ctx, `UPDATE users SET deactivated_at=now() WHERE id=$1 AND anonymized=FALSE`, userID)
	return err
}

func (s *Store) CancelDeactivation(ctx context.Context, userID int64) error {
	_, err := s.Pool.Exec(ctx, `UPDATE users SET deactivated_at=NULL WHERE id=$1 AND anonymized=FALSE`, userID)
	return err
}

func (s *Store) AnonymizeExpiredDeactivations(ctx context.Context, cooldownSeconds int64) (int64, error) {
	tag, err := s.Pool.Exec(ctx, `UPDATE users SET
		email='deleted_'||id||'@invalid', nickname='已注销的社员', password_hash='',
		signature='', status_emoji='', status_text='', blog_url='',
		fixed_avatar_url='', active_avatar_url='', custom_markdown='', custom_css='',
		show_fields='{}'::jsonb,
		role='member',
		anonymized=TRUE, active_avatar_enabled=FALSE
		WHERE deactivated_at IS NOT NULL AND anonymized=FALSE
		  AND deactivated_at < now() - ($1 * interval '1 second')`, cooldownSeconds)
	if err != nil {
		return 0, err
	}
	return tag.RowsAffected(), nil
}

func (s *Store) FillUserCounts(ctx context.Context, m *model.User) error {
	return s.Pool.QueryRow(ctx, `SELECT
			(SELECT count(*) FROM follows WHERE follower_id=$1),
			(SELECT count(*) FROM follows WHERE followee_id=$1),
			(SELECT count(*) FROM posts WHERE author_id=$1 AND deleted_at IS NULL)`,
		m.ID).Scan(&m.FollowingCount, &m.FollowerCount, &m.PostCount)
}

func (s *Store) IsFollowing(ctx context.Context, followerID, followeeID int64) (bool, error) {
	var one int
	err := s.Pool.QueryRow(ctx, `SELECT 1 FROM follows WHERE follower_id=$1 AND followee_id=$2`,
		followerID, followeeID).Scan(&one)
	if errors.Is(err, pgx.ErrNoRows) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return true, nil
}

func (s *Store) Follow(ctx context.Context, followerID, followeeID int64) error {
	_, err := s.Pool.Exec(ctx, `INSERT INTO follows(follower_id, followee_id) VALUES($1,$2)
		ON CONFLICT (follower_id, followee_id) DO NOTHING`, followerID, followeeID)
	return err
}

func (s *Store) Unfollow(ctx context.Context, followerID, followeeID int64) error {
	_, err := s.Pool.Exec(ctx, `DELETE FROM follows WHERE follower_id=$1 AND followee_id=$2`, followerID, followeeID)
	return err
}

func (s *Store) FollowList(ctx context.Context, userID int64, followers bool) ([]model.UserBrief, error) {
	var q string
	if followers {
		q = `SELECT ` + qcols("u") + ` FROM users u JOIN follows f ON f.follower_id=u.id WHERE f.followee_id=$1 ORDER BY f.created_at DESC LIMIT 200`
	} else {
		q = `SELECT ` + qcols("u") + ` FROM users u JOIN follows f ON f.followee_id=u.id WHERE f.follower_id=$1 ORDER BY f.created_at DESC LIMIT 200`
	}
	rows, err := s.Pool.Query(ctx, q, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	followed := map[int64]bool{}
	if followers {
		frows, ferr := s.Pool.Query(ctx, `SELECT followee_id FROM follows WHERE follower_id=$1`, userID)
		if ferr == nil {
			for frows.Next() {
				var fid int64
				if frows.Scan(&fid) == nil {
					followed[fid] = true
				}
			}
			frows.Close()
		}
	}
	var out []model.UserBrief
	for rows.Next() {
		u, err := scanUser(rows)
		if err != nil {
			return nil, err
		}
		b := u.ToBrief()
		b.Following = followed[u.ID]
		out = append(out, b)
	}
	return out, rows.Err()
}

func (s *Store) Heartbeat(ctx context.Context, userID int64, activeSeconds int) error {
	if activeSeconds <= 0 {
		activeSeconds = 1
	}
	if activeSeconds > 600 {
		activeSeconds = 600
	}
	minutes := activeSeconds / 60
	return s.ExecTx(ctx, func(tx pgx.Tx) error {

		if time.Now().In(shanghaiLoc).Hour() == 3 {
			if _, err := tx.Exec(ctx, `INSERT INTO achievements(user_id, code) VALUES($1,'night_owl') ON CONFLICT DO NOTHING`, userID); err != nil {
				return err
			}
		}
		if _, err := tx.Exec(ctx, `INSERT INTO online_heartbeats(user_id, beat_at) VALUES($1, now())
			ON CONFLICT (user_id) DO UPDATE SET beat_at=now()`, userID); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE users SET online_minutes=online_minutes+$2 WHERE id=$1`, userID, minutes); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `INSERT INTO activity_days(user_id, day, minutes) VALUES($1, (now() AT TIME ZONE 'Asia/Shanghai')::date, $2)
			ON CONFLICT (user_id, day) DO UPDATE SET minutes = activity_days.minutes + $2`, userID, minutes); err != nil {
			return err
		}
		var online int64
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM online_heartbeats WHERE beat_at > now() - interval '5 minutes'`).Scan(&online); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `INSERT INTO daily_stats(day, peak_online) VALUES((now() AT TIME ZONE 'Asia/Shanghai')::date, $1)
			ON CONFLICT (day) DO UPDATE SET peak_online = GREATEST(daily_stats.peak_online, $1)`, online)
		return err
	})
}

func (s *Store) OnlineStats(ctx context.Context) (*model.OnlineStats, error) {
	st := &model.OnlineStats{}
	err := s.Pool.QueryRow(ctx, `SELECT
			(SELECT count(*) FROM online_heartbeats WHERE beat_at > now() - interval '5 minutes'),
			(SELECT count(*) FROM users),
			(COALESCE((SELECT peak_online FROM daily_stats WHERE day=(now() AT TIME ZONE 'Asia/Shanghai')::date), 0)),
			(COALESCE((SELECT sum(minutes) FROM activity_days WHERE day=(now() AT TIME ZONE 'Asia/Shanghai')::date), 0)),
			(SELECT COALESCE(sum(online_minutes),0) FROM users)`).Scan(
		&st.OnlineNow, &st.Registered, &st.TodayPeak, &st.TodayMinutes, &st.TotalMinutes)
	return st, err
}

func (s *Store) Tiles(ctx context.Context, userID int64) ([]model.Tile, error) {
	rows, err := s.Pool.Query(ctx, `SELECT day, minutes, posts, comments FROM activity_days
		WHERE user_id=$1 AND day > (now() AT TIME ZONE 'Asia/Shanghai')::date - 371`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	byDay := map[string][3]int{}
	for rows.Next() {
		var day time.Time
		var m, p, c int
		if err := rows.Scan(&day, &m, &p, &c); err != nil {
			return nil, err
		}
		byDay[day.Format("2006-01-02")] = [3]int{m, p, c}
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	out := make([]model.Tile, 0, 371)
	today := time.Now().In(shanghaiLoc)
	for i := 370; i >= 0; i-- {
		d := today.AddDate(0, 0, -i)
		key := d.Format("2006-01-02")
		t := model.Tile{Date: key}
		if v, ok := byDay[key]; ok {
			t.Minutes, t.Posts, t.Comments = v[0], v[1], v[2]
			t.Score = tileLevel(v[0], v[1], v[2])
		}
		out = append(out, t)
	}
	return out, nil
}

func tileLevel(minutes, posts, comments int) int {
	score := minutes/15 + posts*3 + comments
	switch {
	case score == 0:
		return 0
	case score <= 2:
		return 1
	case score <= 5:
		return 2
	case score <= 10:
		return 3
	default:
		return 4
	}
}

func bumpActivity(tx pgx.Tx, ctx context.Context, userID int64, kind string) error {
	col := "posts"
	if kind == "comment" {
		col = "comments"
	}
	_, err := tx.Exec(ctx, `INSERT INTO activity_days(user_id, day, `+col+`) VALUES($1, `+OmikujiTodayExpr+`, 1)
		ON CONFLICT (user_id, day) DO UPDATE SET `+col+` = activity_days.`+col+` + 1`, userID)
	return err
}

func (s *Store) SetMute(ctx context.Context, targetID int64, until *time.Time) error {
	_, err := s.Pool.Exec(ctx, `UPDATE users SET muted_until=$2 WHERE id=$1`, targetID, until)
	return err
}

func (s *Store) SetBan(ctx context.Context, targetID int64, banned bool) error {
	_, err := s.Pool.Exec(ctx, `UPDATE users SET banned=$2 WHERE id=$1`, targetID, banned)
	return err
}

func (s *Store) SetRole(ctx context.Context, targetID int64, role string) error {
	_, err := s.Pool.Exec(ctx, `UPDATE users SET role=$2 WHERE id=$1`, targetID, role)
	return err
}

func (s *Store) GrantAchievement(ctx context.Context, userID int64, code string) error {
	_, err := s.Pool.Exec(ctx, `INSERT INTO achievements(user_id, code) VALUES($1,$2)
		ON CONFLICT (user_id, code) DO NOTHING`, userID, code)
	return err
}

func (s *Store) Achievements(ctx context.Context, userID int64) ([]model.Achievement, error) {
	rows, err := s.Pool.Query(ctx, `SELECT code, unlocked_at FROM achievements WHERE user_id=$1`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []model.Achievement
	for rows.Next() {
		var a model.Achievement
		var t time.Time
		if err := rows.Scan(&a.Code, &t); err != nil {
			return nil, err
		}
		a.UnlockedAt = Unix(t)
		out = append(out, a)
	}
	return out, rows.Err()
}

func hashToken(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}

func (s *Store) SaveRefreshToken(ctx context.Context, userID int64, token string, ttlSeconds int64, device string) error {
	_, err := s.Pool.Exec(ctx, `INSERT INTO refresh_tokens(token_hash, user_id, expires_at, device_label)
		VALUES($1,$2, now() + ($3 * interval '1 second'), $4)`, hashToken(token), userID, ttlSeconds, device)
	if err != nil {
		return err
	}

	_, err = s.Pool.Exec(ctx, `DELETE FROM refresh_tokens WHERE expires_at < now()`)
	return err
}

type SessionRow struct {
	ID        int64
	Device    string
	CreatedAt time.Time
	Current   bool
}

func (s *Store) Sessions(ctx context.Context, userID int64, currentToken string) ([]SessionRow, error) {
	rows, err := s.Pool.Query(ctx, `SELECT id, device_label, created_at, (token_hash=$2)
		FROM refresh_tokens
		WHERE user_id=$1 AND expires_at > now() ORDER BY created_at DESC`, userID, hashToken(currentToken))
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []SessionRow
	for rows.Next() {
		var r SessionRow
		if err := rows.Scan(&r.ID, &r.Device, &r.CreatedAt, &r.Current); err != nil {
			return nil, err
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

func (s *Store) DeleteSession(ctx context.Context, userID, sessionID int64) (bool, error) {
	tag, err := s.Pool.Exec(ctx, `DELETE FROM refresh_tokens WHERE id=$1 AND user_id=$2`, sessionID, userID)
	if err != nil {
		return false, err
	}
	return tag.RowsAffected() > 0, nil
}

func (s *Store) RefreshTokenUser(ctx context.Context, token string) (*UserRow, error) {
	return scanUser(s.Pool.QueryRow(ctx, `SELECT `+qcols("u")+` FROM users u
		JOIN refresh_tokens rt ON rt.user_id=u.id
		WHERE rt.token_hash=$1 AND rt.expires_at > now()
		  AND u.banned=FALSE AND u.anonymized=FALSE AND u.password_hash<>''`, hashToken(token)))
}

func (s *Store) RevokeRefreshTokens(ctx context.Context, userID int64) error {
	_, err := s.Pool.Exec(ctx, `DELETE FROM refresh_tokens WHERE user_id=$1`, userID)
	return err
}

func (s *Store) RevokeRefreshTokensExcept(ctx context.Context, userID int64, keepToken string) error {
	_, err := s.Pool.Exec(ctx,
		`DELETE FROM refresh_tokens WHERE user_id=$1 AND token_hash<>$2`,
		userID, hashToken(keepToken))
	return err
}

func (s *Store) DeleteRefreshToken(ctx context.Context, token string) error {
	_, err := s.Pool.Exec(ctx, `DELETE FROM refresh_tokens WHERE token_hash=$1`, hashToken(token))
	return err
}

func (s *Store) RotateRefreshToken(ctx context.Context, oldToken, newToken, device string, ttlSeconds int64) (int64, error) {
	var userID int64
	err := s.Pool.QueryRow(ctx, `WITH dead AS (
		DELETE FROM refresh_tokens rt
		USING users u
		WHERE rt.token_hash=$1 AND rt.expires_at > now()
		  AND u.id=rt.user_id AND u.banned=FALSE AND u.anonymized=FALSE AND u.password_hash<>''
		RETURNING rt.user_id
	) INSERT INTO refresh_tokens(token_hash, user_id, expires_at, device_label)
	SELECT $2, user_id, now() + ($3 * interval '1 second'), $4 FROM dead
	RETURNING user_id`,
		hashToken(oldToken), hashToken(newToken), ttlSeconds, device).Scan(&userID)
	if errors.Is(err, pgx.ErrNoRows) {
		return 0, ErrNotFound
	}
	return userID, err
}

func (s *Store) StaffGroups(ctx context.Context) ([]model.StaffGroup, error) {
	rows, err := s.Pool.Query(ctx, `SELECT `+userCols+` FROM users
		WHERE role IN ('temp_houshi','houshi','guuji','kannushi') AND anonymized=FALSE
		ORDER BY created_at`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	byRole := map[string][]model.StaffMember{}
	order := []string{model.RoleKannushi, model.RoleGuuji, model.RoleHoushi, model.RoleTempHoushi}
	for rows.Next() {
		u, err := scanUser(rows)
		if err != nil {
			return nil, err
		}

		sig := ""
		if u.show("signature") {
			sig = u.Signature
		}
		byRole[u.Role] = append(byRole[u.Role], model.StaffMember{
			UserBrief: u.ToBrief(), Signature: sig, JoinedAt: Unix(u.CreatedAt),
		})
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	out := make([]model.StaffGroup, 0, 4)
	for _, role := range order {
		out = append(out, model.StaffGroup{
			Role: role, RoleName: model.RoleName(role), Members: byRole[role],
		})
	}
	return out, nil
}

func (s *Store) CreateStaffApplication(ctx context.Context, userID int64, reason, weeklyHours string) error {
	_, err := s.Pool.Exec(ctx, `INSERT INTO staff_applications(user_id, reason, weekly_hours) VALUES($1,$2,$3)`,
		userID, reason, weeklyHours)
	return err
}

func (s *Store) LastStaffApplication(ctx context.Context, userID int64) (*model.StaffApplication, error) {
	var a model.StaffApplication
	var uid int64
	var created time.Time
	err := s.Pool.QueryRow(ctx, `SELECT id, user_id, reason, weekly_hours, status, created_at
		FROM staff_applications WHERE user_id=$1 ORDER BY created_at DESC LIMIT 1`, userID).Scan(
		&a.ID, &uid, &a.Reason, &a.WeeklyHours, &a.Status, &created)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	a.CreatedAt = Unix(created)
	return &a, nil
}

func (s *Store) PendingStaffApplications(ctx context.Context) ([]model.StaffApplication, error) {
	rows, err := s.Pool.Query(ctx, `SELECT sa.id, sa.user_id, sa.reason, sa.weekly_hours, sa.status, sa.created_at
		FROM staff_applications sa WHERE sa.status='pending' ORDER BY sa.created_at`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []model.StaffApplication
	for rows.Next() {
		var a model.StaffApplication
		var uid int64
		var created time.Time
		if err := rows.Scan(&a.ID, &uid, &a.Reason, &a.WeeklyHours, &a.Status, &created); err != nil {
			return nil, err
		}
		a.CreatedAt = Unix(created)
		u, err := s.UserByID(ctx, uid)
		if err != nil {
			return nil, err
		}
		a.User = u.ToBrief()
		out = append(out, a)
	}
	return out, rows.Err()
}

func (s *Store) DecideStaffApplication(ctx context.Context, appID, approverID int64, approve bool) (int64, string, error) {
	var uid int64
	var status string

	err := s.ExecTx(ctx, func(tx pgx.Tx) error {
		if err := tx.QueryRow(ctx, `UPDATE staff_applications
			SET status=CASE WHEN $2 THEN 'approved' ELSE 'rejected' END, decided_by=$3, decided_at=now()
			WHERE id=$1 AND status='pending' RETURNING user_id, status`, appID, approve, approverID).Scan(&uid, &status); err != nil {
			return err
		}
		if approve {

			if _, err := tx.Exec(ctx, `UPDATE users SET role=$2 WHERE id=$1 AND role=$3`,
				uid, model.RoleTempHoushi, model.RoleMember); err != nil {
				return err
			}
		}
		return nil
	})
	if errors.Is(err, pgx.ErrNoRows) {
		return 0, "", ErrNotFound
	}
	if err != nil {
		return 0, "", err
	}
	return uid, status, nil
}

func (s *Store) ExportUserPostsMarkdown(ctx context.Context, userID int64) (string, error) {
	u, err := s.UserByID(ctx, userID)
	if err != nil {
		return "", err
	}
	rows, err := s.Pool.Query(ctx, `SELECT id, title, body, created_at FROM posts
		WHERE author_id=$1 AND deleted_at IS NULL ORDER BY created_at`, userID)
	if err != nil {
		return "", err
	}
	defer rows.Close()
	md := fmt.Sprintf("# %s 的帖子导出\n\n> 导出于 %s\n", u.Nickname, time.Now().Format("2006-01-02 15:04"))
	for rows.Next() {
		var id int64
		var title, body string
		var t time.Time
		if err := rows.Scan(&id, &title, &body, &t); err != nil {
			return "", err
		}
		md += fmt.Sprintf("\n---\n\n## %s\n\n%s\n\n> %s · %s\n", title, body, model.FormatPostNo(id), t.Format("2006-01-02 15:04"))
	}
	return md, rows.Err()
}

func NewStore(pool *pgxpool.Pool) *Store { return &Store{Pool: pool} }
