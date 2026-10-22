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
