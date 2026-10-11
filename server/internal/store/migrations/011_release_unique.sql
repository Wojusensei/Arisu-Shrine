DELETE FROM releases a USING releases b
  WHERE a.channel = b.channel AND a.version = b.version AND a.id > b.id;
ALTER TABLE releases ADD CONSTRAINT releases_version_channel_unique UNIQUE (version, channel);
