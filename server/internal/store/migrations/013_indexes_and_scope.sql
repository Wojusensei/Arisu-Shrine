-- 走查补缺：热查询索引 + notify_scope 键名归一
-- 索引对照走查清单：postSelect 的收藏 EXISTS/SaveCount 按 post_id 过滤、
-- 发评论的作者成就计数、评论级联软删按 parent_id、绘马回复按 ema_id、
-- 举报 guard/队列按 reporter+时间与 status、登录时清扫过期会话
CREATE INDEX IF NOT EXISTS collection_posts_post_idx ON collection_posts(post_id);
CREATE INDEX IF NOT EXISTS comments_author_idx ON comments(author_id);
CREATE INDEX IF NOT EXISTS comments_parent_idx ON comments(parent_id);
CREATE INDEX IF NOT EXISTS ema_replies_ema_idx ON ema_replies(ema_id);
CREATE INDEX IF NOT EXISTS reports_reporter_idx ON reports(reporter_id, created_at);
CREATE INDEX IF NOT EXISTS reports_status_idx ON reports(status);
CREATE INDEX IF NOT EXISTS refresh_tokens_expires_idx ON refresh_tokens(expires_at);

-- 初版默认值把「改昵称通知」写成了 follow_nickname，客户端与服务端实际读写
-- 的键是 nickname：旧键一并归一（值保留），system/report 两个死键顺手清掉
UPDATE users SET notify_scope = (notify_scope - 'follow_nickname' - 'system' - 'report') ||
  CASE WHEN notify_scope ? 'follow_nickname' AND NOT (notify_scope ? 'nickname')
       THEN jsonb_build_object('nickname', notify_scope->'follow_nickname')
       ELSE '{}'::jsonb END
WHERE notify_scope ? 'follow_nickname' OR notify_scope ? 'system' OR notify_scope ? 'report';
