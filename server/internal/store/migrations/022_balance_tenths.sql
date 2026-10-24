ALTER TABLE users RENAME COLUMN balance TO balance_tenths;
ALTER TABLE users ALTER COLUMN balance_tenths SET DEFAULT 1000;
UPDATE users SET balance_tenths = balance_tenths * 10;
