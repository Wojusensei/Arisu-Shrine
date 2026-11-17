-- 发版记录唯一化：同版本同渠道只留一条，重复发布转为更新（ON CONFLICT）。
-- 此前重复 POST /releases 会插重复行，客户端更新日志按 version+channel 做 key 直接崩
DELETE FROM releases a USING releases b
  WHERE a.channel = b.channel AND a.version = b.version AND a.id > b.id;
ALTER TABLE releases ADD CONSTRAINT releases_version_channel_unique UNIQUE (version, channel);
