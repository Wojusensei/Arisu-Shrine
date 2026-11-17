-- 021: 赛钱（奉纳）系统。
-- 每人初始 100 円；每日首次上线 +50（Asia/Shanghai 日界，与求签同一把尺）；
-- 给帖子奉纳任意整数金额（不得超出余额），作者收取 10%（向下取整），
-- 其余沉没——只进不出的部分是通缩阀，防经济通胀。

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
