-- 022: 账面改以 0.1 円（tenths）记账。
-- 修复 10% 收入的整除截断：此前作者收入按 amount/10 向下取整，
-- 连续小额奉纳的零头被吞（9円×10 次作者实收 0 而非 9）。
-- 改为 balance_tenths（BIGINT，单位 0.1 円）后：付款扣 amount×10、
-- 作者收 amount（恰为 10%，无截断），奉纳入口仍只收整数円。
-- 初始 100 円 = 1000 tenths，每日 +50 円 = +500 tenths。

ALTER TABLE users RENAME COLUMN balance TO balance_tenths;
ALTER TABLE users ALTER COLUMN balance_tenths SET DEFAULT 1000;
UPDATE users SET balance_tenths = balance_tenths * 10;
