-- 特别关注退场：统一为关注，通知范围对所有关注者一致生效
ALTER TABLE follows DROP COLUMN IF EXISTS special;
