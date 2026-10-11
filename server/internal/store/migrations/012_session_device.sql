CREATE SEQUENCE IF NOT EXISTS refresh_tokens_id_seq;
ALTER TABLE refresh_tokens ADD COLUMN id BIGINT NOT NULL DEFAULT nextval('refresh_tokens_id_seq');
ALTER SEQUENCE refresh_tokens_id_seq OWNED BY refresh_tokens.id;
CREATE UNIQUE INDEX refresh_tokens_id_idx ON refresh_tokens(id);
ALTER TABLE refresh_tokens ADD COLUMN device_label TEXT NOT NULL DEFAULT '';
