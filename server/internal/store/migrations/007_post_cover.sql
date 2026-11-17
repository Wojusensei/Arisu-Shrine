-- 帖子封面：发帖时上传的封面图（选填，展示时缺省回落作者头像）
ALTER TABLE posts ADD COLUMN IF NOT EXISTS cover_url TEXT NOT NULL DEFAULT '';
