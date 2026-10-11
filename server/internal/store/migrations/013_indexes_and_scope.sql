CREATE INDEX IF NOT EXISTS collection_posts_post_idx ON collection_posts(post_id);
CREATE INDEX IF NOT EXISTS comments_author_idx ON comments(author_id);
CREATE INDEX IF NOT EXISTS comments_parent_idx ON comments(parent_id);
CREATE INDEX IF NOT EXISTS ema_replies_ema_idx ON ema_replies(ema_id);
CREATE INDEX IF NOT EXISTS reports_reporter_idx ON reports(reporter_id, created_at);
CREATE INDEX IF NOT EXISTS reports_status_idx ON reports(status);
CREATE INDEX IF NOT EXISTS refresh_tokens_expires_idx ON refresh_tokens(expires_at);

UPDATE users SET notify_scope = (notify_scope - 'follow_nickname' - 'system' - 'report') ||
  CASE WHEN notify_scope ? 'follow_nickname' AND NOT (notify_scope ? 'nickname')
       THEN jsonb_build_object('nickname', notify_scope->'follow_nickname')
       ELSE '{}'::jsonb END
WHERE notify_scope ? 'follow_nickname' OR notify_scope ? 'system' OR notify_scope ? 'report';
