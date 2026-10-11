CREATE TABLE IF NOT EXISTS uploads (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name TEXT NOT NULL,
    kind TEXT NOT NULL DEFAULT 'image',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_uploads_user ON uploads(user_id, created_at);

UPDATE collections c SET name = c.name || '·' || c.id
FROM (
    SELECT user_id, name, min(id) AS keep_id
    FROM collections GROUP BY user_id, name HAVING count(*) > 1
) d
WHERE c.user_id = d.user_id AND c.name = d.name AND c.id <> d.keep_id;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_collections_user_name ON collections(user_id, name);
