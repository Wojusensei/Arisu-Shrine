ALTER TABLE notices ADD COLUMN IF NOT EXISTS starred_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS notices_starred_idx ON notices(user_id, created_at DESC) WHERE starred_at IS NOT NULL;
