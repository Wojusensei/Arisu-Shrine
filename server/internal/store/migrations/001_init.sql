CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    email                TEXT UNIQUE NOT NULL,
    password_hash        TEXT NOT NULL,
    nickname             TEXT NOT NULL,
    role                 TEXT NOT NULL DEFAULT 'member',
    signature            TEXT NOT NULL DEFAULT '',
    status_emoji         TEXT NOT NULL DEFAULT '',
    status_text          TEXT NOT NULL DEFAULT '',
    blog_url             TEXT NOT NULL DEFAULT '',
    fixed_avatar_url     TEXT NOT NULL DEFAULT '',
    active_avatar_url    TEXT NOT NULL DEFAULT '',
    active_avatar_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    custom_markdown      TEXT NOT NULL DEFAULT '',
    custom_css           TEXT NOT NULL DEFAULT '',
    show_fields          JSONB NOT NULL DEFAULT '{}'::jsonb,
    notify_scope         JSONB NOT NULL DEFAULT '{"follow_update":true,"nickname":true,"follow_avatar":false}'::jsonb,
    online_minutes       BIGINT NOT NULL DEFAULT 0,
    muted_until          TIMESTAMPTZ,
    banned               BOOLEAN NOT NULL DEFAULT FALSE,
    deactivated_at       TIMESTAMPTZ,
    anonymized           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    token_hash  TEXT PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens(user_id);

CREATE TABLE posts (
    id            BIGSERIAL PRIMARY KEY,
    author_id     BIGINT NOT NULL REFERENCES users(id),
    board         TEXT NOT NULL,
    title         TEXT NOT NULL,
    body          TEXT NOT NULL,
    pinned        BOOLEAN NOT NULL DEFAULT FALSE,
    essence       BOOLEAN NOT NULL DEFAULT FALSE,
    up_count      INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMPTZ,
    deleted_by    BIGINT REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX posts_board_idx ON posts(board, created_at DESC);
CREATE INDEX posts_author_idx ON posts(author_id, created_at DESC);

CREATE TABLE post_ups (
    post_id    BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

CREATE TABLE post_tracks (
    post_id    BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

CREATE TABLE comments (
    id              BIGSERIAL PRIMARY KEY,
    post_id         BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id       BIGINT NOT NULL REFERENCES users(id),
    parent_id       BIGINT REFERENCES comments(id),
    reply_to_user_id BIGINT REFERENCES users(id),
    content         TEXT NOT NULL,
    like_count      INT NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMPTZ,
    deleted_by      BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX comments_post_idx ON comments(post_id, created_at);

CREATE TABLE comment_likes (
    comment_id BIGINT NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (comment_id, user_id)
);

CREATE TABLE emas (
    id          BIGSERIAL PRIMARY KEY,
    author_id   BIGINT NOT NULL REFERENCES users(id),
    title       TEXT NOT NULL,
    body        TEXT NOT NULL,
    tags        TEXT[] NOT NULL DEFAULT '{}',
    state       TEXT NOT NULL DEFAULT 'open',
    reply_count INT NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX emas_state_idx ON emas(state, created_at DESC);

CREATE TABLE ema_replies (
    id         BIGSERIAL PRIMARY KEY,
    ema_id     BIGINT NOT NULL REFERENCES emas(id) ON DELETE CASCADE,
    author_id  BIGINT NOT NULL REFERENCES users(id),
    content    TEXT NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE collections (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE collection_posts (
    collection_id BIGINT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    post_id       BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (collection_id, post_id)
);

CREATE TABLE follows (
    follower_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    special     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id)
);
CREATE INDEX follows_followee_idx ON follows(followee_id);

CREATE TABLE notices (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind         TEXT NOT NULL,
    category     TEXT NOT NULL,
    content      TEXT NOT NULL,
    target_route TEXT NOT NULL DEFAULT '',
    dedupe_key   TEXT,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX notices_user_idx ON notices(user_id, created_at DESC);
CREATE UNIQUE INDEX notices_dedupe_idx ON notices(user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;

CREATE TABLE reports (
    id          BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES users(id),
    post_id     BIGINT REFERENCES posts(id) ON DELETE SET NULL,
    comment_id  BIGINT REFERENCES comments(id) ON DELETE SET NULL,
    ema_id      BIGINT REFERENCES emas(id) ON DELETE SET NULL,
    reason      TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'pending',
    handled_by  BIGINT REFERENCES users(id),
    handled_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE staff_applications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id),
    reason       TEXT NOT NULL,
    weekly_hours TEXT NOT NULL,
    status       TEXT NOT NULL DEFAULT 'pending',
    decided_by   BIGINT REFERENCES users(id),
    decided_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE releases (
    id          BIGSERIAL PRIMARY KEY,
    version     TEXT NOT NULL,
    channel     TEXT NOT NULL DEFAULT 'stable',
    change_type TEXT NOT NULL,
    body        TEXT NOT NULL,
    created_by  BIGINT REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE omikuji_draws (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day         DATE NOT NULL,
    result      TEXT NOT NULL,
    character   TEXT NOT NULL,
    series      TEXT NOT NULL DEFAULT '东方Project',
    fortune     TEXT NOT NULL,
    lucky_item  TEXT NOT NULL,
    drawn_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, day)
);

CREATE TABLE achievements (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code        TEXT NOT NULL,
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, code)
);

CREATE TABLE online_heartbeats (
    user_id  BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    beat_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE activity_days (
    user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    day      DATE NOT NULL,
    minutes  INT NOT NULL DEFAULT 0,
    posts    INT NOT NULL DEFAULT 0,
    comments INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);

CREATE TABLE daily_stats (
    day         DATE PRIMARY KEY,
    peak_online INT NOT NULL DEFAULT 0
);
