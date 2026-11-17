-- 020: 回填通知的 actor_id（拉黑的读过滤依赖它）。
-- 019 加列到本迁移之间，顶帖/回复/关注三类互动通知漏带触发者落成 NULL，
-- 一旦将来有人拉黑，这批旧通知躲过过滤继续出现在社务箱。
-- 只回填能从去重键无损推导的行：up:<帖>:<触发者>、follow:<收件人>:<触发者>、
-- tracked:<帖>:<事件>:<天>（编辑/删除类事件，触发者=帖作者，帖子软删行仍在）。
-- 无去重键的旧互动通知（回复/新帖广播）推不出确定人选，保持 NULL 维持「历史一律可见」。

UPDATE notices SET actor_id = substring(dedupe_key from '^up:[0-9]+:([0-9]+)$')::bigint
WHERE actor_id IS NULL AND dedupe_key ~ '^up:[0-9]+:[0-9]+$';

UPDATE notices SET actor_id = substring(dedupe_key from '^follow:[0-9]+:([0-9]+)$')::bigint
WHERE actor_id IS NULL AND dedupe_key ~ '^follow:[0-9]+:[0-9]+$';

UPDATE notices n SET actor_id = p.author_id
FROM posts p
WHERE n.actor_id IS NULL
  AND n.dedupe_key ~ '^tracked:[0-9]+:'
  AND p.id = substring(n.dedupe_key from '^tracked:([0-9]+):')::int;

UPDATE notices SET actor_id = substring(dedupe_key from '^profile:([0-9]+):')::bigint
WHERE actor_id IS NULL AND dedupe_key ~ '^profile:[0-9]+:';
