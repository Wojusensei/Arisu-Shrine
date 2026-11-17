-- 御神签每日榜按 day 查询：omikuji_draws 主键是 (user_id, day)，day 居第二列用不上，
-- 每次进游会页的榜单查询原本全表扫描 + 排序；(day, luck DESC) 让「当日按幸运值倒序」
-- 直接走索引免排序。与 013 同款写法（当前表量级无需 CONCURRENTLY）
CREATE INDEX IF NOT EXISTS idx_omikuji_draws_day_luck ON omikuji_draws(day, luck DESC);
