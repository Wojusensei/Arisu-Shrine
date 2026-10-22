WITH dup AS (
    SELECT id, row_number() OVER (PARTITION BY lower(btrim(nickname)) ORDER BY id) AS rn
    FROM users WHERE anonymized = FALSE
)
UPDATE users u SET nickname = u.nickname || '·' || u.id
FROM dup WHERE u.id = dup.id AND dup.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_users_nickname
    ON users (lower(btrim(nickname))) WHERE anonymized = FALSE;
