-- 018: 昵称唯一。
-- 列表卡片、评论头、主页身份区只显示「昵称 + 身份标签 + 头像」，用户编号只在 @ 提及里出现，
-- 昵称重名就等于给了冒用身份的口子。按归一化值（小写 + 去首尾空格）建唯一索引。
-- 已注销账号共用占位名「已注销的社员」，用部分索引把匿名化行排除在外。
-- 自建实例可能有历史重名：先给后注册的重名者加序号（与 005 收藏夹去重同一思路），避免迁移失败。

WITH dup AS (
    SELECT id, row_number() OVER (PARTITION BY lower(btrim(nickname)) ORDER BY id) AS rn
    FROM users WHERE anonymized = FALSE
)
UPDATE users u SET nickname = u.nickname || '·' || u.id
FROM dup WHERE u.id = dup.id AND dup.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_users_nickname
    ON users (lower(btrim(nickname))) WHERE anonymized = FALSE;
