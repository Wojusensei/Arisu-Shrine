package store

import (
	"context"
	"errors"
	"fmt"
	"math"
	"math/rand"
	"strconv"
	"strings"
	"time"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

// ---- 搜索（标题 ILIKE / 正则；编号精确直达） ----

// escapeLike 转义 ILIKE 通配符与转义符本身（默认转义符是反斜杠）
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
		// ILIKE 通配符转义：输入 %/_ 会被当通配符，搜「%」等于全表匹配
		cond = ` p.title ILIKE '%' || $2 || '%'`
		args = append(args, escapeLike(q))
	}
	// 编号精确匹配：arisu_000013 / 纯数字
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
			s.FillPostMentions(ctx, out)
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
	if err := rows.Err(); err != nil {
		return nil, err
	}
	s.FillPostMentions(ctx, out)
	return out, nil
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

// SearchMentionUsers 提及选择器专用：只查社员，不搜帖子。
// 通用搜索每 2 秒一次限频、还要顺带查 30 篇帖子，逐键补全时又慢又容易被挡；
// 这里只扫 users 表，权重按「编号精确 > 昵称全等 > 昵称前缀 > 去空格前缀 > 包含」排。
func (s *Store) SearchMentionUsers(ctx context.Context, viewerID int64, q string, limit int) ([]model.UserBrief, error) {
	if limit <= 0 || limit > 20 {
		limit = 8
	}
	raw := strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(q), "@"))
	if raw == "" {
		return nil, nil
	}
	// 编号只认 User_000012 与纯数字；昵称里夹的数字不当编号，免得搜「灵梦2」冒出 User_000002
	idText := ""
	lower := strings.ToLower(raw)
	if strings.HasPrefix(lower, "user_") {
		idText = strings.TrimPrefix(lower, "user_")
	} else if isDigits(raw) {
		idText = raw
	}
	if n, err := strconv.ParseInt(idText, 10, 64); err == nil && n > 0 {
		// 前导零归一：User_000012 与 12 查到同一个
		idText = strconv.FormatInt(n, 10)
	} else {
		idText = ""
	}
	// 相册式的「雾雨 魔理沙」与「雾雨魔理沙」互相认得：比较前去掉半角/全角空格
	norm := strings.NewReplacer(" ", "", "　", "").Replace(raw)
	if norm == "" {
		norm = raw
	}
	rows, err := s.Pool.Query(ctx, `SELECT `+userBriefCols+` FROM users u
		WHERE u.anonymized=FALSE
		AND NOT EXISTS (SELECT 1 FROM blocks b WHERE b.blocker_id=$2 AND b.blocked_id=u.id)
		AND (
			u.nickname ILIKE '%' || $1 || '%'
			OR replace(replace(lower(u.nickname), ' ', ''), '　', '') ILIKE '%' || $3 || '%'
			OR ($4 <> '' AND u.id::text LIKE $4 || '%')
		)
		ORDER BY
			CASE
				WHEN $4 <> '' AND u.id::text=$4 THEN 0
				WHEN lower(u.nickname)=lower($1) THEN 1
				WHEN u.nickname ILIKE $1 || '%' THEN 2
				WHEN replace(replace(lower(u.nickname), ' ', ''), '　', '') LIKE $3 || '%' THEN 3
				WHEN u.nickname ILIKE '%' || $1 || '%' THEN 4
				ELSE 5
			END,
			u.id
		LIMIT `+fmt.Sprint(limit),
		escapeLike(raw), viewerID, escapeLike(norm), idText)
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

func isDigits(s string) bool {
	if s == "" {
		return false
	}
	for _, r := range s {
		if r < '0' || r > '9' {
			return false
		}
	}
	return true
}

func (s *Store) SearchUsers(ctx context.Context, viewerID int64, q string, limit int) ([]model.UserBrief, error) {
	if limit <= 0 || limit > 50 {
		limit = 20
	}
	// 编号直达：User_000001（与昵称分支同一把尺：匿名化与被拉黑者都当不存在）
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

// ---- 版本日志 ----

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
	// 同版本同渠道重复发布=更新既有记录（迁移 011 唯一约束）：
	// 重复行会让客户端更新日志按 version+channel 做 key 直接崩
	_, err := s.Pool.Exec(ctx, `INSERT INTO releases(version, channel, change_type, body, created_by) VALUES($1,$2,$3,$4,$5)
		ON CONFLICT (version, channel) DO UPDATE SET change_type=EXCLUDED.change_type, body=EXCLUDED.body, created_by=EXCLUDED.created_by, created_at=now()`,
		r.Version, sanitizeEnum(r.Channel, []string{"stable", "beta"}),
		sanitizeEnum(r.ChangeType, []string{"feature", "fix", "improvement", "breaking"}), r.Body, creatorID)
	return err
}

// ---- 御神签（每日一签） ----

// luckBand 幸运值 1-100 → 吉凶档位
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

// 每日幸运值取截断正态（均值 68、标准差 25，范围 1-100）。
// 均匀抽取时凶与末吉合计占三成；正态化后约 0.8% 与 6.2%，
// 中吉与吉合计约六成，大吉仍接近一成。档位占比由 drawLuck 的测试锁定。
const (
	omikujiLuckMean   = 68.0
	omikujiLuckStdDev = 25.0
)

func drawLuck() int {
	for {
		v := int(math.Round(omikujiLuckMean + omikujiLuckStdDev*rand.NormFloat64()))
		if v >= 1 && v <= 100 {
			return v
		}
	}
}

// 「今天」按社员的时区（Asia/Shanghai）判定：数据库容器跑在 UTC，
// CURRENT_DATE 会在北京时间 0 点到 8 点之间给出前一天的日期，
// 造成「明明没求过签却提示今日已求」。全项目统一用同一表达式。
const OmikujiTodayExpr = `(now() AT TIME ZONE 'Asia/Shanghai')::date`

// shanghaiLoc 时区敏感的本地判定（成就、瓷砖）一律用它，与 SQL 侧的
// OmikujiTodayExpr 保持同一时区，别再用服务器本地时间
var shanghaiLoc = mustShanghai()

func mustShanghai() *time.Location {
	loc, err := time.LoadLocation("Asia/Shanghai")
	if err != nil {
		return time.FixedZone("CST", 8*3600)
	}
	return loc
}

// OmikujiToday 只查今日是否已抽，绝不落库（供客户端进页时判断，不消耗当日签）。
// 只有「无记录」返回未抽；查询故障原样上抛——把一切错误当成未抽会让客户端
// 把「今日已抽」覆盖成可点的「今日求签」，与 DrawOmikuji 的幂等回读叠加后观感反复横跳
func (s *Store) OmikujiToday(ctx context.Context, userID int64) (bool, int64, error) {
	var luck int64
	err := s.Pool.QueryRow(ctx, `SELECT luck FROM omikuji_draws WHERE user_id=$1 AND day=`+OmikujiTodayExpr, userID).Scan(&luck)
	if errors.Is(err, pgx.ErrNoRows) {
		return false, 0, nil
	}
	if err != nil {
		return false, 0, err
	}
	return true, luck, nil
}

// OmikujiLeaderRow 御签榜条目；rank 在服务端算好，客户端只管展示
type OmikujiLeaderRow struct {
	Nickname string `json:"nickname"`
	Role     string `json:"role"`
	Avatar   string `json:"avatar"`
	Luck     int    `json:"luck"`
	Rank     int    `json:"rank"`
	IsMe     bool   `json:"is_me"`
}

// OmikujiStanding 自己的今日名次；未求签时榜单里没有这一行
type OmikujiStanding struct {
	Rank int `json:"rank"`
	Luck int `json:"luck"`
}

// OmikujiBoard 今日御签榜：前十名次 + 自己附近的名次。
// Items 保持旧字段名，老客户端忽略 Nearby/Me 也能照常显示前十。
type OmikujiBoard struct {
	Items  []OmikujiLeaderRow `json:"items"`
	Nearby []OmikujiLeaderRow `json:"nearby"`
	Me     *OmikujiStanding   `json:"me,omitempty"`
}

// 榜单永远只展示前 10 名；自己的小榜是「我 + 前后各 2 人」，共 5 人。
// 小榜允许与前十重复：第 11、12 名的上方邻居也在十强榜里，两处都摆，
// 保证小榜任何时候都是完整的 5 行（榜首/榜尾按实际人数收缩）。
const (
	leaderboardTopRanks  = 10
	leaderboardNeighbors = 2
)

// OmikujiLeaderboard 今日御签榜：幸运值前 10 名 + 自己前后各 2 人。
// 同分按抽签先后严格排位（先抽在前，最后以 user_id 兜底保证确定性），
// 名次 1..N 唯一——「我的第 30 名」才有确定含义。
// 排名先在全量有效社员上算好，再隐藏拉黑的行：名次是当日的公共属性，
// 不因谁屏蔽了谁而变，代价是榜单里偶尔缺一行。
// 自己在前十时不再返回 Nearby（本人已在高亮的前十里）；前十之外则返回完整
// 五人小榜，落在前十内的邻居允许在 Items/Nearby 里各出现一次。
func (s *Store) OmikujiLeaderboard(ctx context.Context, viewerID int64) (*OmikujiBoard, error) {
	rows, err := s.Pool.Query(ctx, `
		WITH ranked AS (
			SELECT u.id, u.nickname, u.role,
			       CASE WHEN u.active_avatar_enabled AND u.active_avatar_url <> '' THEN u.active_avatar_url ELSE u.fixed_avatar_url END AS avatar,
			       d.luck, d.drawn_at,
			       row_number() OVER (ORDER BY d.luck DESC, d.drawn_at ASC, d.user_id ASC) AS rn
			FROM omikuji_draws d
			JOIN users u ON u.id = d.user_id
			WHERE d.day = `+OmikujiTodayExpr+` AND u.anonymized = FALSE AND u.deactivated_at IS NULL
		), me AS (
			SELECT rn, luck FROM ranked WHERE id=$1
		)
		SELECT r.nickname, r.role, r.avatar, r.luck, r.rn, r.id=$1 AS is_me,
		       (r.rn <= $2) AS in_top,
		       COALESCE(me.rn > $2 AND r.rn BETWEEN me.rn - $3 AND me.rn + $3, FALSE) AS in_window
		FROM ranked r
		LEFT JOIN me ON TRUE
		WHERE (r.rn <= $2
		       OR (me.rn > $2 AND r.rn BETWEEN me.rn - $3 AND me.rn + $3))
		  AND NOT EXISTS (SELECT 1 FROM blocks bl WHERE bl.blocker_id=$1 AND bl.blocked_id=r.id)
		ORDER BY r.rn`, viewerID, leaderboardTopRanks, leaderboardNeighbors)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	board := &OmikujiBoard{
		Items:  make([]OmikujiLeaderRow, 0, leaderboardTopRanks),
		Nearby: make([]OmikujiLeaderRow, 0, leaderboardNeighbors*2+1),
	}
	for rows.Next() {
		var (
			r        OmikujiLeaderRow
			rank     int
			inTop    bool
			inWindow bool
		)
		if err := rows.Scan(&r.Nickname, &r.Role, &r.Avatar, &r.Luck, &rank, &r.IsMe, &inTop, &inWindow); err != nil {
			return nil, err
		}
		r.Rank = rank
		if r.IsMe {
			board.Me = &OmikujiStanding{Rank: rank, Luck: r.Luck}
		}
		if inTop {
			board.Items = append(board.Items, r)
		}
		if inWindow {
			board.Nearby = append(board.Nearby, r)
		}
	}
	return board, rows.Err()
}

// DrawOmikuji 每日运势：幸运值 1-100，当天已抽直接返回
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
	luck := drawLuck()
	err = s.Pool.QueryRow(ctx, `INSERT INTO omikuji_draws(user_id, day, result, luck)
		VALUES($1, `+OmikujiTodayExpr+`, $2, $3)
		RETURNING drawn_at`,
		userID, luckBand(luck), luck).Scan(&day)
	if err != nil {
		// 双端同秒首抽：第二个 INSERT 撞 (user_id, day) 主键会 500，
		// 而且当日签其实已被先到的请求落库——回读直接返回那一签
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

// ---- 举报 ----

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

// ResolveReport 处理举报；deleteTarget=true 时联动删除目标内容（管理删除，通知作者）
// ReportTargetAuthorRole 举报目标的作者身份（处置删除的层级校验用）。
// 第二个返回值=目标是否评论；第三个=false 表示没有可删除的内容目标（举报用户等场景）。
// 评论举报的 post_id 里存的是父帖 id（仅作「前往查看」路由），
// 层级必须按评论作者算——post 子查询放后面会被父帖作者抢走。
// 评论删除适用「下不能管上」；帖子/绘马整体删除任何维护人员可执行（用户裁决）
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
	// 先删内容再结案：删除失败时举报保持待处理可重试。
	// 此前顺序相反，删除绘马报错（占位符 bug）会把举报错误终结成「已处理」，
	// 内容却没删、举报人也收不到结果通知，且状态机不允许再来一次
	if approve && deleteTarget {
		// 目标已不存在（作者自行删除等）则跳过删除，处置照常结案。
		// 只删举报真正指向的那一个目标：评论举报的 post_id 是父帖 id（路由参考），
		// 一并删除会把整篇帖子连同其他评论一起带走
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
	// 并发处置同一举报时只有一方成功
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	// 通知举报人处理结果；未删除内容时附上原内容路由，「前往查看」才能直达
	// （此前结果通知不带路由，举报人只能干看文案）
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

// ---- 上传记录 ----

func (s *Store) SaveUpload(ctx context.Context, userID int64, file, kind string) error {
	// 005 里这张表的列叫 name——此前写成 file，每次都静默失败，审计表永远是空的
	_, err := s.Pool.Exec(ctx, `INSERT INTO uploads(user_id, name, kind) VALUES($1,$2,$3)`, userID, file, kind)
	return err
}
