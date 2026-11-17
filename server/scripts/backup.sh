#!/bin/sh
# 备份：数据库全量 dump + uploads 附件（头像/封面/APK），落到 server/backup/。
# 默认保留 14 天（可被 .env 的 BACKUP_KEEP_DAYS 覆盖）。
# 挂 crontab 每天跑（见 docs/部署.md）：
#   0 4 * * * cd /path/to/arisu/server && ./scripts/backup.sh >> backup/backup.log 2>&1
# Redis 不备：里面只有限流计数和验证码，全是可丢的短命数据。
set -eu
cd "$(dirname "$0")/.."
# 备份产物是整库 dump：默认 umask 022 写成 644，同机任何账户都读得到。收紧到 600
umask 077

C="docker compose -f docker-compose.prod.yml"
STAMP=$(date +%Y%m%d-%H%M%S)
KEEP=$(grep -E '^BACKUP_KEEP_DAYS=' .env 2>/dev/null | cut -d= -f2 || true)
KEEP=${KEEP:-14}

mkdir -p backup
$C exec -T postgres pg_dump -U arisu --no-owner arisu | gzip > "backup/arisu-db-$STAMP.sql.gz"
$C exec -T arisu tar cf - -C /app uploads | gzip > "backup/arisu-uploads-$STAMP.tar.gz"

find backup -type f -name 'arisu-*' -mtime +"$KEEP" -delete
echo "[$(date '+%F %T')] 备份完成：arisu-db-$STAMP.sql.gz ($(du -h "backup/arisu-db-$STAMP.sql.gz" | cut -f1)) + arisu-uploads-$STAMP.tar.gz ($(du -h "backup/arisu-uploads-$STAMP.tar.gz" | cut -f1))，保留 $KEEP 天"
