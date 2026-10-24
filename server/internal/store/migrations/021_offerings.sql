ALTER TABLE users ADD COLUMN balance BIGINT NOT NULL DEFAULT 100;
ALTER TABLE users ADD COLUMN offer_day DATE;

CREATE TABLE offerings (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES posts(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX offerings_post_idx ON offerings(post_id);
CREATE INDEX offerings_user_idx ON offerings(user_id);

ALTER TABLE posts ADD COLUMN offer_total BIGINT NOT NULL DEFAULT 0;
