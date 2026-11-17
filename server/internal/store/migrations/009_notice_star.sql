-- 通知收藏：社务箱邮件功能区（收藏 / 取消收藏 / 按收藏过滤）
ALTER TABLE notices ADD COLUMN IF NOT EXISTS starred_at TIMESTAMPTZ;

-- 收藏箱查询：按人过滤收藏通知
CREATE INDEX IF NOT EXISTS notices_starred_idx ON notices(user_id, created_at DESC) WHERE starred_at IS NOT NULL;
