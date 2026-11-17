-- 019: 用户拉黑（静默）。
-- 语义（2026-10-18 用户定案）：
--   拉黑者看不到对方的内容与通知；对方仍能看到拉黑者，但关注/回复/提及对拉黑者不生效。
--   静默：被拉黑方不被告知；管理侧不提供任何「谁拉黑了谁」的查看接口。
-- notices.actor_id 记录通知的触发者，用于读路径过滤与写路径拦截（历史行为 NULL）。
CREATE TABLE IF NOT EXISTS blocks (
    blocker_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT blocks_not_self CHECK (blocker_id <> blocked_id)
);
CREATE INDEX IF NOT EXISTS blocks_blocked_idx ON blocks(blocked_id);

ALTER TABLE notices ADD COLUMN IF NOT EXISTS actor_id BIGINT;
CREATE INDEX IF NOT EXISTS notices_actor_idx ON notices(user_id, actor_id) WHERE actor_id IS NOT NULL;
