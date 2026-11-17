-- 御神签改幸运值制后，角色/签文/幸运物不再必填
ALTER TABLE omikuji_draws ALTER COLUMN character DROP NOT NULL;
ALTER TABLE omikuji_draws ALTER COLUMN fortune DROP NOT NULL;
ALTER TABLE omikuji_draws ALTER COLUMN lucky_item DROP NOT NULL;
ALTER TABLE omikuji_draws ALTER COLUMN series DROP NOT NULL;
