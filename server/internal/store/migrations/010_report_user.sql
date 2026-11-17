-- 举报用户：此前 reportUser 完全丢弃被举报对象，处理者无法定位被举报者
ALTER TABLE reports ADD COLUMN IF NOT EXISTS reported_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
