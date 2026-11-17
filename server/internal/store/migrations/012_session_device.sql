-- 012: 会话设备标识。多设备登录本就各占一行 refresh_token，
-- 此前没有设备信息：用户看不到自己登录中的设备、也无法远程踢掉某一台（手机丢了只能改密码）。
-- id 用于逐行寻址；device_label 是登录端自报的设备名（Build.MODEL），空串=旧会话/未上报。
CREATE SEQUENCE IF NOT EXISTS refresh_tokens_id_seq;
ALTER TABLE refresh_tokens ADD COLUMN id BIGINT NOT NULL DEFAULT nextval('refresh_tokens_id_seq');
ALTER SEQUENCE refresh_tokens_id_seq OWNED BY refresh_tokens.id;
CREATE UNIQUE INDEX refresh_tokens_id_idx ON refresh_tokens(id);
ALTER TABLE refresh_tokens ADD COLUMN device_label TEXT NOT NULL DEFAULT '';
