package store

import (
	"context"
	"errors"
	"fmt"
	"time"

	"arisu/server/internal/model"
	"github.com/jackc/pgx/v5"
)

// postSelect：帖子 + 作者简报 + 当前用户互动标记。$1 = viewer id
const postSelect = `SELECT p.id, p.board, p.title, p.body, p.intro, p.cover_url, p.pinned, p.essence,
	p.up_count, p.comment_count, p.created_at, p.updated_at, (p.updated_at > p.created_at + interval '2 seconds') AS edited,
	u.id, u.nickname, u.role,
	CASE WHEN u.active_avatar_enabled AND u.active_avatar_url<>'' THEN u.active_avatar_url ELSE u.fixed_avatar_url END,
	EXISTS(SELECT 1 FROM post_ups WHERE post_id=p.id AND user_id=$1),
	EXISTS(SELECT 1 FROM post_tracks WHERE post_id=p.id AND user_id=$1),
	EXISTS(SELECT 1 FROM collection_posts cp JOIN collections c ON c.id=cp.collection_id
		WHERE cp.post_id=p.id AND c.user_id=$1),
	(SELECT count(*) FROM collection_posts sc WHERE sc.post_id=p.id),
	p.offer_total,
	EXISTS(SELECT 1 FROM offerings o WHERE o.post_id=p.id AND o.user_id=$1)
	FROM posts p JOIN users u ON u.id=p.author_id`

// commentSelect：$1 = viewer id；追加以 $2 为过滤条件
const commentSelect = `SELECT c.id, c.post_id, c.parent_id, c.content, c.image_url, c.like_count, c.created_at,
	u.id, u.nickname, u.role,
	CASE WHEN u.active_avatar_enabled AND u.active_avatar_url<>'' THEN u.active_avatar_url ELSE u.fixed_avatar_url END,
	COALESCE(ru.id, 0), (ru.id IS NOT NULL), COALESCE(ru.nickname,''), COALESCE(ru.role,''),
	COALESCE(CASE WHEN ru.active_avatar_enabled AND ru.active_avatar_url<>'' THEN ru.active_avatar_url ELSE ru.fixed_avatar_url END, ''),
	EXISTS(SELECT 1 FROM comment_likes WHERE comment_id=c.id AND user_id=$1)
	FROM comments c
	JOIN users u ON u.id=c.author_id
	LEFT JOIN users ru ON ru.id=c.reply_to_user_id`

const userBriefCols = `u.id, u.nickname, u.role,
	CASE WHEN u.active_avatar_enabled AND u.active_avatar_url<>'' THEN u.active_avatar_url ELSE u.fixed_avatar_url END`

func scanBrief(aID int64, aNick, aRole, aAvatar string) model.UserBrief {
	return model.UserBrief{ID: aID, No: model.FormatUserNo(aID), Nickname: aNick, Role: aRole, Avatar: aAvatar}
}

func scanPost(row pgx.Row) (*model.Post, error) {
	var p model.Post
	var aID int64
	var aNick, aRole, aAvatar string
	var created, updated time.Time
	err := row.Scan(&p.ID, &p.Board, &p.Title, &p.Body, &p.Intro, &p.CoverURL, &p.Pinned, &p.Essence,
		&p.UpCount, &p.CommentCount, &created, &updated, &p.Edited,
		&aID, &aNick, &aRole, &aAvatar, &p.Upped, &p.Tracked, &p.Favorited, &p.SaveCount, &p.OfferTotal, &p.Offered)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	p.No = model.FormatPostNo(p.ID)
	p.Author = scanBrief(aID, aNick, aRole, aAvatar)
	p.CreatedAt = Unix(created)
	p.UpdatedAt = Unix(updated)
	return &p, nil
}

type PostListQuery struct {
	ViewerID int64
	Board    string // main|gensokyo|dev|空
	Sort     string // new|hot|essence
	AuthorID int64
	Limit    int
	Offset   int
}

func postsWhere(board, sort string, authorID int64, viewerFiltered bool) string {
	where := ` WHERE p.deleted_at IS NULL`
	if viewerFiltered {
		where += blockFilter("p.author_id")
	}
	if board != "" {
		where += ` AND p.board='` + sanitizeEnum(board, []string{"main", "gensokyo", "dev"}) + `'`
	}
	if sort == "essence" {
		where += ` AND p.essence=TRUE`
	}
	if authorID > 0 {
		where += fmt.Sprintf(` AND p.author_id=%d`, authorID)
	}
	return where
}

func (s *Store) PostsPage(ctx context.Context, q PostListQuery) (*model.PostPage, error) {
	if q.Limit <= 0 || q.Limit > 50 {
		q.Limit = 20
	}
	where := postsWhere(q.Board, q.Sort, q.AuthorID, true)
	order := ` ORDER BY p.pinned DESC, p.created_at DESC`
	if q.Sort == "hot" {
		order = ` ORDER BY p.pinned DESC, (p.up_count*2 + p.comment_count + CASE WHEN p.essence THEN 10 ELSE 0 END)
			/ power(EXTRACT(EPOCH FROM (now()-p.created_at))/3600 + 2, 0.8) DESC`
	}
	rows, err := s.Pool.Query(ctx, postSelect+where+order+fmt.Sprintf(` LIMIT %d OFFSET %d`, q.Limit, q.Offset), q.ViewerID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	page := &model.PostPage{Page: model.Page{Page: q.Offset/q.Limit + 1, PageSize: q.Limit}}
	for rows.Next() {
		p, err := scanPost(rows)
		if err != nil {
			return nil, err
		}
		page.Items = append(page.Items, *p)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	page.Total, err = s.countPosts(ctx, q.Board, q.Sort, q.AuthorID, q.ViewerID)
	if err != nil {
		return nil, err
	}
	s.FillPostMentions(ctx, page.Items)
	return page, nil
}

func (s *Store) countPosts(ctx context.Context, board, sort string, authorID, viewerID int64) (int64, error) {
	var total int64
	err := s.Pool.QueryRow(ctx, `SELECT count(*) FROM posts p`+postsWhere(board, sort, authorID, true), viewerID).Scan(&total)
	return total, err
}

// PostsByRelation 个人页内容切换：posts|tracked|liked|favorited。
// offset 支撑翻页——此前固定第一页，超过一页的内容永远看不到（2026-10-03 体检修复）
func (s *Store) PostsByRelation(ctx context.Context, viewerID int64, relation string, ownerID int64, collectionID int64, limit, offset int) ([]model.Post, int64, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}
	join := ""
	switch relation {
	case "tracked":
		join = ` JOIN post_tracks r ON r.post_id=p.id AND r.user_id=` + fmt.Sprint(ownerID)
	case "liked":
		join = ` JOIN post_ups r ON r.post_id=p.id AND r.user_id=` + fmt.Sprint(ownerID)
	case "favorited":
		if collectionID > 0 {
			join = ` JOIN collection_posts r ON r.post_id=p.id
				JOIN collections c3 ON c3.id=r.collection_id AND c3.user_id=` + fmt.Sprint(ownerID)
		} else {
			join = ` JOIN collection_posts r ON r.post_id=p.id JOIN collections c2 ON c2.id=r.collection_id AND c2.user_id=` + fmt.Sprint(ownerID)
		}
	default: // posts
		join = ``
	}
	where := postsWhere("", "new", 0, true)
	if relation == "posts" {
		where = ` WHERE p.deleted_at IS NULL AND p.author_id=` + fmt.Sprint(ownerID) + blockFilter("p.author_id")
	}
	// 指定收藏夹时把夹子过滤真正拼进查询——此前 collectionID 只换了表别名，
	// ?collection_id=N 会返回全部收藏，夹子页永远显示所有帖子
	if relation == "favorited" && collectionID > 0 {
		where += ` AND c3.id=` + fmt.Sprint(collectionID)
	}
	q := postSelect + join + where + ` ORDER BY p.pinned DESC, p.created_at DESC LIMIT ` + fmt.Sprint(limit) + ` OFFSET ` + fmt.Sprint(offset)
	rows, err := s.Pool.Query(ctx, q, viewerID)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()
	var out []model.Post
	for rows.Next() {
		p, err := scanPost(rows)
		if err != nil {
			return nil, 0, err
		}
		out = append(out, *p)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	// total 用同款 join/where 单独数一次，客户端据此判断还有没有下一页。
	// 该查询没有 viewer 参数（postSelect 才有 $1），不能把 viewerID 传进去
	var total int64
	err = s.Pool.QueryRow(ctx, `SELECT count(*) FROM posts p`+join+where, viewerID).Scan(&total)
	if err != nil {
		return nil, 0, err
	}
	s.FillPostMentions(ctx, out)
	return out, total, nil
}

func (s *Store) PostByID(ctx context.Context, viewerID, id int64) (*model.Post, error) {
	p, err := scanPost(s.Pool.QueryRow(ctx,
		postSelect+` WHERE p.id=$2 AND p.deleted_at IS NULL`+blockFilter("p.author_id"), viewerID, id))
	if err != nil {
		return nil, err
	}
	s.fillMentions(ctx, 1, func(int) string { return p.Body },
		func(_ int, m model.UserBrief) { p.Mentions = append(p.Mentions, m) })
	return p, nil
}

type PostCreate struct {
	AuthorID int64
	Board    string
	Title    string
	Body     string
	Intro    string
	Cover    string
}

func (s *Store) CreatePost(ctx context.Context, c PostCreate) (*model.Post, error) {
	board := sanitizeEnum(c.Board, []string{"main", "gensokyo", "dev"})
	var id int64
	err := s.ExecTx(ctx, func(tx pgx.Tx) error {
		if err := tx.QueryRow(ctx, `INSERT INTO posts(author_id, board, title, body, intro, cover_url) VALUES($1,$2,$3,$4,$5,$6) RETURNING id`,
			c.AuthorID, board, c.Title, c.Body, c.Intro, c.Cover).Scan(&id); err != nil {
			return err
		}
		if err := bumpActivity(tx, ctx, c.AuthorID, "post"); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `INSERT INTO achievements(user_id, code) VALUES($1,'first_post') ON CONFLICT DO NOTHING`, c.AuthorID); err != nil {
			return err
		}
		return notifyFollowersOfNewPost(tx, ctx, c.AuthorID, id, c.Title)
	})
	if err != nil {
		return nil, err
	}
	return s.PostByID(ctx, c.AuthorID, id)
}

type PostUpdate struct {
	Title *string
	Body  *string
	Intro *string
	Board *string
	Cover *string
}

func (s *Store) UpdatePost(ctx context.Context, id int64, u PostUpdate) error {
	// 多字段编辑包进事务：中途失败会留下改了一半的帖子
	return s.ExecTx(ctx, func(tx pgx.Tx) error {
		if u.Title != nil {
			if _, err := tx.Exec(ctx, `UPDATE posts SET title=$1, updated_at=now() WHERE id=$2`, *u.Title, id); err != nil {
				return err
			}
		}
		if u.Body != nil {
			if _, err := tx.Exec(ctx, `UPDATE posts SET body=$1, updated_at=now() WHERE id=$2`, *u.Body, id); err != nil {
				return err
			}
		}
		if u.Intro != nil {
			if _, err := tx.Exec(ctx, `UPDATE posts SET intro=$1, updated_at=now() WHERE id=$2`, *u.Intro, id); err != nil {
				return err
			}
		}
		if u.Cover != nil {
			if _, err := tx.Exec(ctx, `UPDATE posts SET cover_url=$1, updated_at=now() WHERE id=$2`, *u.Cover, id); err != nil {
				return err
			}
		}
		if u.Board != nil {
			board := sanitizeEnum(*u.Board, []string{"main", "gensokyo", "dev"})
			if _, err := tx.Exec(ctx, `UPDATE posts SET board=$1, updated_at=now() WHERE id=$2`, board, id); err != nil {
				return err
			}
		}
		return nil
	})
}

// DeletePost 软删除。mod=true 时通知作者（删帖理由必填）
func (s *Store) DeletePost(ctx context.Context, id, operatorID int64, mod bool, reason string) error {
	return s.ExecTx(ctx, func(tx pgx.Tx) error {
		var authorID int64
		var title string
		err := tx.QueryRow(ctx, `UPDATE posts SET deleted_at=now(), deleted_by=$2 WHERE id=$1 AND deleted_at IS NULL
			RETURNING author_id, title`, id, operatorID).Scan(&authorID, &title)
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		if mod && authorID != operatorID {
			return createNotice(tx, ctx, NoticeInput{
				UserID: authorID, Kind: "system", Category: "system",
				Content: fmt.Sprintf("您的帖子《%s》因违反社区规则已被删除。理由：%s", title, reason),
			})
		}
		return nil
	})
}

// TogglePostFlag 原子翻转置顶/精华并返回翻转后的值。
// 此前 handler 先读旧值再写反值，并发双击会该落 false 却落 true
func (s *Store) TogglePostFlag(ctx context.Context, postID int64, flag string) (bool, error) {
	if flag != "pinned" && flag != "essence" {
		return false, ErrBadRequest
	}
	var on bool
	err := s.Pool.QueryRow(ctx, `UPDATE posts SET `+flag+`=NOT `+flag+`
		WHERE id=$1 AND deleted_at IS NULL RETURNING `+flag, postID).Scan(&on)
	if errors.Is(err, pgx.ErrNoRows) {
		return false, ErrNotFound
	}
	return on, err
}

func (s *Store) ToggleUp(ctx context.Context, postID, userID int64, hotThreshold int) (active bool, count int64, err error) {
	err = s.ExecTx(ctx, func(tx pgx.Tx) error {
		// 同用户锁：先查后改的窗口在双端同点并发时会让响应与落库态相反（同 ToggleFavorite）
		if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, userID); err != nil {
			return err
		}
		// 软删/不存在的帖子不再收顶（此前照常累加计数）；标题顺手取好，被顶通知要带
		var alive bool
		var title string
		if err := tx.QueryRow(ctx, `SELECT deleted_at IS NULL, title FROM posts WHERE id=$1`, postID).Scan(&alive, &title); err != nil {
			return ErrNotFound
		}
		if !alive {
			return ErrNotFound
		}
		var n int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM post_ups WHERE post_id=$1 AND user_id=$2`, postID, userID).Scan(&n); err != nil {
			return err
		}
		if n > 0 {
			if _, err := tx.Exec(ctx, `DELETE FROM post_ups WHERE post_id=$1 AND user_id=$2`, postID, userID); err != nil {
				return err
			}
			active = false
		} else {
			if _, err := tx.Exec(ctx, `INSERT INTO post_ups(post_id, user_id) VALUES($1,$2) ON CONFLICT DO NOTHING`, postID, userID); err != nil {
				return err
			}
			active = true
		}
		var authorID int64
		if err := tx.QueryRow(ctx, `UPDATE posts SET up_count=(SELECT count(*) FROM post_ups WHERE post_id=$1) WHERE id=$1 RETURNING up_count, author_id`, postID).Scan(&count, &authorID); err != nil {
			return err
		}
		if active && count >= int64(hotThreshold) {
			if _, err := tx.Exec(ctx, `INSERT INTO achievements(user_id, code) VALUES($1,'hot_author') ON CONFLICT DO NOTHING`, authorID); err != nil {
				return err
			}
		}
		// 被顶通知：只在真正点上时发，取消顶不打扰；自己顶自己不通知。
		// 去重键固定（不随天变化），同人反复顶/取消只聚合刷新那一条
		if active && authorID != userID {
			if err := createNotice(tx, ctx, NoticeInput{
				UserID: authorID, Kind: "up", Category: "reply",
				Content:     fmt.Sprintf("%s 顶了您的帖子《%s》", nicknameOf(tx, ctx, userID), title),
				TargetRoute: model.FormatPostNo2(postID),
				DedupeKey:   fmt.Sprintf("up:%d:%d", postID, userID),
				ActorID:     userID,
			}); err != nil {
				return err
			}
		}
		return nil
	})
	return active, count, err
}

func (s *Store) ToggleTrack(ctx context.Context, postID, userID int64) (bool, error) {
	tracked := false
	err := s.ExecTx(ctx, func(tx pgx.Tx) error {
		// alive 检查与写入同事务+同用户锁：此前检查在事务外，
		// 窗口内刚被软删的帖也会被落上追踪
		if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, userID); err != nil {
			return err
		}
		var alive bool
		if err := tx.QueryRow(ctx, `SELECT deleted_at IS NULL FROM posts WHERE id=$1`, postID).Scan(&alive); err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				return ErrNotFound
			}
			return err
		}
		if !alive {
			return ErrNotFound
		}
		var n int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM post_tracks WHERE post_id=$1 AND user_id=$2`, postID, userID).Scan(&n); err != nil {
			return err
		}
		if n > 0 {
			_, err := tx.Exec(ctx, `DELETE FROM post_tracks WHERE post_id=$1 AND user_id=$2`, postID, userID)
			return err
		}
		if _, err := tx.Exec(ctx, `INSERT INTO post_tracks(post_id, user_id) VALUES($1,$2) ON CONFLICT DO NOTHING`, postID, userID); err != nil {
			return err
		}
		tracked = true
		return nil
	})
	return tracked, err
}

// ---- 收藏 ----

func (s *Store) EnsureDefaultCollection(ctx context.Context, userID int64) (int64, error) {
	var id int64
	err := s.Pool.QueryRow(ctx, `SELECT id FROM collections WHERE user_id=$1 AND is_default=TRUE`, userID).Scan(&id)
	if err == nil {
		return id, nil
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		return 0, err
	}
	// 并发首藏：两个请求同时 miss 后各插一条会出现两个默认收藏夹，
	// ON CONFLICT 收敛到同一行（DO UPDATE 是为了让冲突方也拿到 id）
	err = s.Pool.QueryRow(ctx, `INSERT INTO collections(user_id, name, is_default) VALUES($1,'默认收藏夹',TRUE)
		ON CONFLICT (user_id, name) DO UPDATE SET name=EXCLUDED.name RETURNING id`,
		userID).Scan(&id)
	return id, err
}

func (s *Store) ToggleFavorite(ctx context.Context, userID, postID int64, collectionID *int64) (bool, error) {
	// 此前完全不校验帖子：不存在的 id 也走收藏流程，软删帖也能收
	var alive bool
	if err := s.Pool.QueryRow(ctx, `SELECT deleted_at IS NULL FROM posts WHERE id=$1`, postID).Scan(&alive); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return false, ErrNotFound
		}
		return false, err
	}
	if !alive {
		return false, ErrNotFound
	}
	// collection_id=0 是「移出收藏」的显式指令：不管当前躺在哪个夹，删掉即成功。
	// 此前客户端移出只能传默认夹 id，帖子躺在别的夹时会被悄悄搬进默认夹、
	// 界面却提示「已移出收藏」
	removeAny := collectionID != nil && *collectionID == 0
	cid := int64(0)
	switch {
	case removeAny:
		// 显式移出不需要目标夹
	case collectionID == nil:
		var err error
		cid, err = s.EnsureDefaultCollection(ctx, userID)
		if err != nil {
			return false, err
		}
	default:
		// 属主校验：只能收藏进自己的收藏夹（防横向越权写）
		var owner int64
		err := s.Pool.QueryRow(ctx, `SELECT user_id FROM collections WHERE id=$1`, *collectionID).Scan(&owner)
		if errors.Is(err, pgx.ErrNoRows) || (err == nil && owner != userID) {
			return false, ErrNotFound
		}
		if err != nil {
			return false, err
		}
		cid = *collectionID
	}
	// B 站式：一份内容同一时刻只在一个收藏夹
	added := false
	err := s.ExecTx(ctx, func(tx pgx.Tx) error {
		// 用户级咨询锁：并发双击/双端收藏时「先查后写」会各自 INSERT，
		// 同一帖落进两个收藏夹（PK 是 (collection_id, post_id)，拦不住）
		if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, userID); err != nil {
			return err
		}
		var existing int64
		err := tx.QueryRow(ctx, `SELECT cp.collection_id FROM collection_posts cp
			JOIN collections c ON c.id=cp.collection_id
			WHERE cp.post_id=$1 AND c.user_id=$2 LIMIT 1`, postID, userID).Scan(&existing)
		if errors.Is(err, pgx.ErrNoRows) {
			if removeAny {
				added = false // 本就没收藏，移出按已达成处理
				return nil
			}
			added = true
			_, err = tx.Exec(ctx, `INSERT INTO collection_posts(collection_id, post_id) VALUES($1,$2) ON CONFLICT DO NOTHING`, cid, postID)
			return err
		}
		if err != nil {
			return err
		}
		if removeAny || existing == cid {
			added = false
			_, err = tx.Exec(ctx, `DELETE FROM collection_posts WHERE collection_id=$1 AND post_id=$2`, existing, postID)
			return err
		}
		added = true
		// 目标夹可能因历史双插已含此帖：先清掉目标夹里的旧行再搬，
		// 否则 UPDATE 直接撞 (collection_id, post_id) 主键 500
		if _, err := tx.Exec(ctx, `DELETE FROM collection_posts WHERE collection_id=$1 AND post_id=$2`, cid, postID); err != nil {
			return err
		}
		_, err = tx.Exec(ctx, `UPDATE collection_posts SET collection_id=$1 WHERE collection_id=$2 AND post_id=$3`, cid, existing, postID)
		return err
	})
	return added, err
}

func (s *Store) Collections(ctx context.Context, userID int64) ([]model.Collection, error) {
	rows, err := s.Pool.Query(ctx, `SELECT c.id, c.name, c.is_default,
		(SELECT count(*) FROM collection_posts cp JOIN posts p_cnt ON p_cnt.id=cp.post_id AND p_cnt.deleted_at IS NULL
			WHERE cp.collection_id=c.id),
		COALESCE((SELECT p_body.body FROM collection_posts cp JOIN posts p_body ON p_body.id=cp.post_id
			WHERE cp.collection_id=c.id AND p_body.deleted_at IS NULL ORDER BY cp.created_at DESC LIMIT 1), ''),
		COALESCE((SELECT p_body.cover_url FROM collection_posts cp JOIN posts p_body ON p_body.id=cp.post_id
			WHERE cp.collection_id=c.id AND p_body.deleted_at IS NULL ORDER BY cp.created_at DESC LIMIT 1), ''),
		COALESCE((SELECT CASE WHEN u.active_avatar_enabled AND u.active_avatar_url<>'' THEN u.active_avatar_url ELSE u.fixed_avatar_url END
			FROM collection_posts cp JOIN posts p_body ON p_body.id=cp.post_id JOIN users u ON u.id=p_body.author_id
			WHERE cp.collection_id=c.id AND p_body.deleted_at IS NULL ORDER BY cp.created_at DESC LIMIT 1), '')
		FROM collections c WHERE c.user_id=$1 ORDER BY c.is_default DESC, c.created_at`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []model.Collection
	for rows.Next() {
		var c model.Collection
		var body, cover, authorAvatar string
		if err := rows.Scan(&c.ID, &c.Name, &c.IsDefault, &c.Count, &body, &cover, &authorAvatar); err != nil {
			return nil, err
		}
		// 收藏夹封面与帖子封面同一套规则：封面图优先，回落正文首图，再回落作者头像
		c.CoverURL = cover
		if c.CoverURL == "" {
			c.CoverURL = firstImageURL(body)
		}
		if c.CoverURL == "" {
			c.CoverURL = authorAvatar
		}
		out = append(out, c)
	}
	return out, rows.Err()
}

func (s *Store) CreateCollection(ctx context.Context, userID int64, name string) (*model.Collection, error) {
	var c model.Collection
	var isDefault bool
	var count int64
	var cover string
	// 上限校验与插入同锁同事务：count-then-insert 在并发下可突破 20 上限
	err := s.ExecTx(ctx, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, userID); err != nil {
			return err
		}
		var n int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM collections WHERE user_id=$1`, userID).Scan(&n); err != nil {
			return err
		}
		if n >= 20 {
			return ErrTooMany
		}
		return tx.QueryRow(ctx, `INSERT INTO collections(user_id, name) VALUES($1,$2)
			RETURNING id, name, is_default, 0, ''`, userID, name).Scan(&c.ID, &c.Name, &isDefault, &count, &cover)
	})
	if isUniqueErr(err) {
		return nil, ErrConflict
	}
	if errors.Is(err, ErrTooMany) {
		return nil, ErrTooMany
	}
	if err != nil {
		return nil, err
	}
	c.CoverURL = cover
	return &c, nil
}

// CollectionCount 用户的收藏夹总数（建夹上限用）
func (s *Store) CollectionCount(ctx context.Context, userID int64) (int, error) {
	var n int
	err := s.Pool.QueryRow(ctx, `SELECT count(*) FROM collections WHERE user_id=$1`, userID).Scan(&n)
	return n, err
}

// DeleteCollection 删除自建收藏夹：夹内帖子移回默认收藏夹（已在默认夹的映射直接丢弃），
// 默认收藏夹本身不可删。ErrNotFound=夹不存在或不是本人的
func (s *Store) DeleteCollection(ctx context.Context, userID, collectionID int64) error {
	return s.ExecTx(ctx, func(tx pgx.Tx) error {
		var isDefault bool
		if err := tx.QueryRow(ctx, `SELECT is_default FROM collections WHERE id=$1 AND user_id=$2`,
			collectionID, userID).Scan(&isDefault); err != nil {
			if errors.Is(err, pgx.ErrNoRows) {
				return ErrNotFound
			}
			return err
		}
		if isDefault {
			return ErrBadRequest
		}
		var n int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM collection_posts WHERE collection_id=$1`, collectionID).Scan(&n); err != nil {
			return err
		}
		if n > 0 {
			// 默认夹可能还不存在（光建夹没收藏过的人），按需补建（同 EnsureDefaultCollection 语义）
			var defID int64
			if err := tx.QueryRow(ctx, `INSERT INTO collections(user_id, name, is_default) VALUES($1,'默认收藏夹',TRUE)
				ON CONFLICT (user_id, name) DO UPDATE SET name=EXCLUDED.name RETURNING id`, userID).Scan(&defID); err != nil {
				return err
			}
			if _, err := tx.Exec(ctx, `INSERT INTO collection_posts(collection_id, post_id)
				SELECT $2, post_id FROM collection_posts WHERE collection_id=$1
				ON CONFLICT DO NOTHING`, collectionID, defID); err != nil {
				return err
			}
			if _, err := tx.Exec(ctx, `DELETE FROM collection_posts WHERE collection_id=$1`, collectionID); err != nil {
				return err
			}
		}
		_, err := tx.Exec(ctx, `DELETE FROM collections WHERE id=$1`, collectionID)
		return err
	})
}

// ---- 评论 ----

func scanComment(row pgx.Row) (*model.Comment, error) {
	var c model.Comment
	var aID, rID int64
	var aNick, aRole, aAvatar string
	var rNick, rRole, rAvatar string
	var parentID *int64
	var created time.Time
	var hasReplyTo bool
	err := row.Scan(&c.ID, &c.PostID, &parentID, &c.Content, &c.ImageURL, &c.LikeCount, &created,
		&aID, &aNick, &aRole, &aAvatar,
		&rID, &hasReplyTo, &rNick, &rRole, &rAvatar,
		&c.Liked)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	c.ParentID = parentID
	c.CreatedAt = Unix(created)
	c.Author = scanBrief(aID, aNick, aRole, aAvatar)
	if hasReplyTo && rID != 0 {
		ub := scanBrief(rID, rNick, rRole, rAvatar)
		c.ReplyToUser = &ub
	}
	return &c, nil
}

func (s *Store) CommentsOf(ctx context.Context, viewerID, postID int64) ([]model.Comment, error) {
	// 上限兜底：热帖评论再多也不允许单请求全量拉取（后续按需分页）
	// JOIN posts 过滤软删帖：此前已知 post id 就能绕过 getPost 的 404 读到全部评论
	rows, err := s.Pool.Query(ctx, commentSelect+
		` JOIN posts p ON p.id=c.post_id AND p.deleted_at IS NULL WHERE c.post_id=$2 AND c.deleted_at IS NULL`+
		blockFilter("c.author_id")+` ORDER BY c.created_at LIMIT 500`,
		viewerID, postID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []model.Comment
	for rows.Next() {
		c, err := scanComment(rows)
		if err != nil {
			return nil, err
		}
		c.Mine = c.Author.ID == viewerID
		out = append(out, *c)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	s.FillCommentMentions(ctx, out)
	return out, nil
}

type CommentCreate struct {
	PostID        int64
	AuthorID      int64
	ParentID      *int64
	ReplyToUserID *int64
	Content       string
	ImageURL      string
}

func (s *Store) CreateComment(ctx context.Context, c CommentCreate) (*model.Comment, error) {
	var id int64
	err := s.ExecTx(ctx, func(tx pgx.Tx) error {
		// 软删帖子不再可评论：此前删帖后评论照常累加、计数器照常增长、
		// 通知照常发，已知 post id 即可绕过 getPost 的 404
		var postAuthor int64
		var postTitle string
		var postAlive bool
		if err := tx.QueryRow(ctx, `SELECT author_id, title, deleted_at IS NULL FROM posts WHERE id=$1`, c.PostID).
			Scan(&postAuthor, &postTitle, &postAlive); err != nil {
			return ErrNotFound
		}
		if !postAlive {
			return ErrNotFound
		}
		// parent 必须是同一帖子下的评论，否则评论树会跨帖悬挂
		if c.ParentID != nil {
			var parentPost int64
			if err := tx.QueryRow(ctx, `SELECT post_id FROM comments WHERE id=$1 AND deleted_at IS NULL`, *c.ParentID).
				Scan(&parentPost); err != nil || parentPost != c.PostID {
				return ErrBadRequest
			}
		}
		// reply_to 必须是真实存在的用户，否则 FK 违反变成 500
		if c.ReplyToUserID != nil {
			var exists bool
			if err := tx.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM users WHERE id=$1)`, *c.ReplyToUserID).Scan(&exists); err != nil || !exists {
				return ErrBadRequest
			}
		}
		if err := tx.QueryRow(ctx, `INSERT INTO comments(post_id, author_id, parent_id, reply_to_user_id, content, image_url)
			VALUES($1,$2,$3,$4,$5,$6) RETURNING id`,
			c.PostID, c.AuthorID, c.ParentID, c.ReplyToUserID, c.Content, c.ImageURL).Scan(&id); err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE posts SET comment_count=comment_count+1 WHERE id=$1`, c.PostID); err != nil {
			return err
		}
		if err := bumpActivity(tx, ctx, c.AuthorID, "comment"); err != nil {
			return err
		}
		// 成就：社务常客（评论数达到 50）
		var cCount int64
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM comments WHERE author_id=$1`, c.AuthorID).Scan(&cCount); err != nil {
			return err
		}
		if cCount >= 50 {
			if _, err := tx.Exec(ctx, `INSERT INTO achievements(user_id, code) VALUES($1,'chatter') ON CONFLICT DO NOTHING`, c.AuthorID); err != nil {
				return err
			}
		}
		preview := PreviewOf(c.Content)
		actor := nicknameOf(tx, ctx, c.AuthorID)
		if postAuthor != c.AuthorID {
			if err := createNotice(tx, ctx, NoticeInput{
				UserID: postAuthor, Kind: "reply", Category: "reply",
				Content:     fmt.Sprintf("%s 评论了您的帖子《%s》：%s", actor, postTitle, preview),
				TargetRoute: model.FormatPostNo2(c.PostID),
				ActorID:     c.AuthorID,
			}); err != nil {
				return err
			}
		}
		if c.ReplyToUserID != nil && *c.ReplyToUserID != c.AuthorID && *c.ReplyToUserID != postAuthor {
			if err := createNotice(tx, ctx, NoticeInput{
				UserID: *c.ReplyToUserID, Kind: "reply", Category: "reply",
				Content:     fmt.Sprintf("%s 在《%s》中回复了您：%s", actor, postTitle, preview),
				TargetRoute: model.FormatPostNo2(c.PostID),
				ActorID:     c.AuthorID,
			}); err != nil {
				return err
			}
		}
		// 楼中楼只带 parent_id 时不填 reply_to_user_id，父评论作者会毫无感知——按 parent 补发
		if c.ReplyToUserID == nil && c.ParentID != nil {
			var parentAuthor int64
			if err := tx.QueryRow(ctx, `SELECT author_id FROM comments WHERE id=$1 AND deleted_at IS NULL`,
				*c.ParentID).Scan(&parentAuthor); err == nil && parentAuthor != c.AuthorID && parentAuthor != postAuthor {
				if err := createNotice(tx, ctx, NoticeInput{
					UserID: parentAuthor, Kind: "reply", Category: "reply",
					Content:     fmt.Sprintf("%s 在《%s》中回复了您：%s", actor, postTitle, preview),
					TargetRoute: model.FormatPostNo2(c.PostID),
					ActorID:     c.AuthorID,
				}); err != nil {
					return err
				}
			}
		}
		// 评论者自己若也追踪本帖，不给自己发「您追踪的帖子有新评论」
		return notifyTrackers(tx, ctx, c.PostID, postTitle, actor, preview, c.AuthorID, c.AuthorID)
	})
	if err != nil {
		return nil, err
	}
	rows, err := s.Pool.Query(ctx, commentSelect+` WHERE c.id=$2`, c.AuthorID, id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	if rows.Next() {
		cm, err := scanComment(rows)
		if err != nil {
			return nil, err
		}
		cm.Mine = true
		s.fillMentions(ctx, 1, func(int) string { return cm.Content },
			func(_ int, m model.UserBrief) { cm.Mentions = append(cm.Mentions, m) })
		return cm, nil
	}
	return nil, ErrNotFound
}

func (s *Store) ToggleCommentLike(ctx context.Context, commentID, userID int64) (active bool, count int64, err error) {
	err = s.ExecTx(ctx, func(tx pgx.Tx) error {
		// 同用户锁（同 ToggleFavorite/ToggleUp）：先查后改在并发下响应会与落库态相反
		if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock($1)`, userID); err != nil {
			return err
		}
		// 软删/不存在的评论不再可点赞；所属帖子已删时同样拒掉
		var alive bool
		if err := tx.QueryRow(ctx, `SELECT c.deleted_at IS NULL AND p.deleted_at IS NULL
			FROM comments c JOIN posts p ON p.id=c.post_id WHERE c.id=$1`, commentID).Scan(&alive); err != nil {
			return ErrNotFound
		}
		if !alive {
			return ErrNotFound
		}
		var n int
		if err := tx.QueryRow(ctx, `SELECT count(*) FROM comment_likes WHERE comment_id=$1 AND user_id=$2`, commentID, userID).Scan(&n); err != nil {
			return err
		}
		if n > 0 {
			if _, err := tx.Exec(ctx, `DELETE FROM comment_likes WHERE comment_id=$1 AND user_id=$2`, commentID, userID); err != nil {
				return err
			}
			active = false
		} else {
			if _, err := tx.Exec(ctx, `INSERT INTO comment_likes(comment_id, user_id) VALUES($1,$2) ON CONFLICT DO NOTHING`, commentID, userID); err != nil {
				return err
			}
			active = true
		}
		return tx.QueryRow(ctx, `UPDATE comments SET like_count=(SELECT count(*) FROM comment_likes WHERE comment_id=$1) WHERE id=$1 RETURNING like_count`, commentID).Scan(&count)
	})
	return active, count, err
}

// DeleteComment 作者或奉仕及以上；管理删除需理由并通知作者
func (s *Store) DeleteComment(ctx context.Context, commentID, operatorID int64, mod bool, reason string) error {
	return s.ExecTx(ctx, func(tx pgx.Tx) error {
		var postID, authorID int64
		err := tx.QueryRow(ctx, `UPDATE comments SET deleted_at=now(), deleted_by=$2 WHERE id=$1 AND deleted_at IS NULL
			RETURNING post_id, author_id`, commentID, operatorID).Scan(&postID, &authorID)
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		// 子楼级联软删：父楼没了子楼还挂着 parent 悬空，读出来渲染断裂；
		// 本楼在上面已置 deleted_at，这里的 RowsAffected 是新删掉的子楼数
		tag, err := tx.Exec(ctx, `WITH RECURSIVE subtree AS (
				SELECT id FROM comments WHERE id=$1
				UNION ALL
				SELECT c.id FROM comments c JOIN subtree st ON c.parent_id=st.id WHERE c.deleted_at IS NULL
			)
			UPDATE comments SET deleted_at=now(), deleted_by=$2
			WHERE id IN (SELECT id FROM subtree) AND deleted_at IS NULL`, commentID, operatorID)
		if err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `UPDATE posts SET comment_count=GREATEST(comment_count-$2,0) WHERE id=$1`,
			postID, 1+tag.RowsAffected()); err != nil {
			return err
		}
		if mod && authorID != operatorID {
			return createNotice(tx, ctx, NoticeInput{
				UserID: authorID, Kind: "system", Category: "system",
				Content: "您的评论因违反社区规则已被删除。理由：" + reason,
			})
		}
		return nil
	})
}
