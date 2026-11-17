-- 御神签改为幸运值制（1-100），档位由分数段映射
ALTER TABLE omikuji_draws ADD COLUMN IF NOT EXISTS luck INT NOT NULL DEFAULT 50;
