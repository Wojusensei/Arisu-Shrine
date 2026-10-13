ALTER TABLE stickers ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

UPDATE stickers SET sort_order = -id WHERE sort_order = 0;
CREATE INDEX IF NOT EXISTS stickers_owner_order_idx ON stickers(owner_id, sort_order, id);
