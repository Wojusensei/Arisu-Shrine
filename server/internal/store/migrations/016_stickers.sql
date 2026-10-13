CREATE TABLE IF NOT EXISTS stickers (
    id         BIGSERIAL PRIMARY KEY,
    owner_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    url        TEXT NOT NULL,
    animated   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_id, url)
);
CREATE INDEX IF NOT EXISTS stickers_owner_idx ON stickers(owner_id, id DESC);
