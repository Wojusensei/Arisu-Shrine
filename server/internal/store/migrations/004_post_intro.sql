-- 帖子介绍语：列表封面展示的一句话介绍（选填）
ALTER TABLE posts ADD COLUMN IF NOT EXISTS intro TEXT NOT NULL DEFAULT '';
