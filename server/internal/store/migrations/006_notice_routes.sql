-- 006: 修复历史通知路由格式（旧版服务端把路由写成帖号/社员编号，客户端按前缀分发匹配不上）

-- arisu_000013 → post/13
UPDATE notices SET target_route = 'post/' || substring(target_route from 8)::int::text
WHERE target_route ~ '^arisu_[0-9]{6}$';

-- User_000001 → user/1
UPDATE notices SET target_route = 'user/' || substring(target_route from 6)::int::text
WHERE target_route ~ '^User_[0-9]{6}$';
