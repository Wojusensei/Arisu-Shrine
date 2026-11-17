-- 网页端多账号档案（对齐安卓 AccountVault.kt）
--
-- 安卓把多账号档案存在本机 DataStore：只存续期令牌、不存密码，
-- 切换账号 = 把对应令牌装回会话槽位。网页端的会话令牌在 HttpOnly cookie 里，
-- JS 读不到，所以档案必须落在服务端。
--
-- 归属：用「当前登录的这条 refresh token 的 hash」作为档案夹的主键。
-- 这样一台设备（一个浏览器）一份账号夹，和安卓 DataStore 的「本机」语义等价：
--   · 登录成功 → 把新账号的 refresh 写进当前夹
--   · 切换     → 从夹里取目标的 refresh，装回 cookie
--   · 移除     → 从夹里删掉，并把该账号在本设备的 refresh 令牌作废
--
-- 令牌同样只存哈希（与 refresh_tokens 一致），泄露库也拿不到可用的令牌。
CREATE TABLE IF NOT EXISTS web_account_vault (
    -- 档案夹所属的 refresh token 哈希（已 hash，不可逆）
    owner_hash    TEXT        NOT NULL,
    -- 档案指向的账号
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- 该账号在本设备的 refresh 令牌（AES-GCM 密文，密钥由 jwt.secret 派生）。
    -- 为什么不是只存哈希：免密切换需要把明文装回 cookie，哈希不可逆；
    -- 为什么不是明文：拖库即可接管账号。加密后拖库者没有 jwt.secret 解不开。
    token_sealed  TEXT        NOT NULL,
    -- 最近使用时间：夹内按它倒序展示
    saved_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_hash, user_id)
);

-- 夹内按最近使用排序
CREATE INDEX IF NOT EXISTS idx_web_vault_owner_saved
    ON web_account_vault (owner_hash, saved_at DESC);

-- 一个令牌只能属于一个账号的档案，避免切换时串号
CREATE UNIQUE INDEX IF NOT EXISTS idx_web_vault_token
    ON web_account_vault (token_sealed);
