-- 表情包自由排序：sort_order 小的排在前，同值时按 id 兜底
ALTER TABLE stickers ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;
-- 老数据没有序号：用 -id 落初始值，等价于原来的「新收的在前」（id 大的排前面）
UPDATE stickers SET sort_order = -id WHERE sort_order = 0;
CREATE INDEX IF NOT EXISTS stickers_owner_order_idx ON stickers(owner_id, sort_order, id);
