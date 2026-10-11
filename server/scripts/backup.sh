#!/bin/sh

set -eu
cd "$(dirname "$0")/.."

C="docker compose -f docker-compose.prod.yml"
STAMP=$(date +%Y%m%d-%H%M%S)
KEEP=$(grep -E '^BACKUP_KEEP_DAYS=' .env 2>/dev/null | cut -d= -f2 || true)
KEEP=${KEEP:-14}

mkdir -p backup
$C exec -T postgres pg_dump -U arisu --no-owner arisu | gzip > "backup/arisu-db-$STAMP.sql.gz"
$C exec -T arisu tar cf - -C /app uploads | gzip > "backup/arisu-uploads-$STAMP.tar.gz"

find backup -type f -name 'arisu-*' -mtime +"$KEEP" -delete
echo "[$(date '+%F %T')] 备份完成：arisu-db-$STAMP.sql.gz ($(du -h "backup/arisu-db-$STAMP.sql.gz" | cut -f1)) + arisu-uploads-$STAMP.tar.gz ($(du -h "backup/arisu-uploads-$STAMP.tar.gz" | cut -f1))，保留 $KEEP 天"
