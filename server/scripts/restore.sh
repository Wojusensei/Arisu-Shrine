#!/bin/sh
# 恢复：把 backup.sh 的产物灌回数据库/附件。危险操作——会先停应用、清空当前库。
# 用法：./scripts/restore.sh backup/arisu-db-YYYYMMDD-HHMMSS.sql.gz [backup/arisu-uploads-YYYYMMDD-HHMMSS.tar.gz]
set -eu
cd "$(dirname "$0")/.."

DB=${1:?用法：./scripts/restore.sh <数据库dump.gz> [uploads.tar.gz]}
UPLOADS=${2:-}
[ -f "$DB" ] || { echo "找不到 $DB" >&2; exit 1; }
[ -z "$UPLOADS" ] || [ -f "$UPLOADS" ] || { echo "找不到 $UPLOADS" >&2; exit 1; }

C="docker compose -f docker-compose.prod.yml"
echo "!! 3 秒后开始恢复，当前数据库数据将被清空：$DB"
sleep 3

$C stop arisu
$C exec -T postgres psql -U arisu -d postgres -c \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='arisu' AND pid<>pg_backend_pid();"
$C exec -T postgres psql -U arisu -d postgres -c "DROP DATABASE IF EXISTS arisu;"
$C exec -T postgres psql -U arisu -d postgres -c "CREATE DATABASE arisu OWNER arisu;"
gunzip -c "$DB" | $C exec -T postgres psql -U arisu -d arisu -q

if [ -n "$UPLOADS" ]; then
    # 不走 compose exec——应用容器此刻是停着的；用一次性容器直接挂卷恢复。
    # 借用栈内固定引用的 postgres:16-alpine 当工具箱（自带 tar），不额外拉镜像
    # （卷名 = <项目名>_<卷名>，项目名由 compose 文件内 name: 固定为 arisu-prod）
    gunzip -c "$UPLOADS" | docker run --rm -i -v arisu-prod_uploads:/work postgres:16-alpine tar xf - -C /work
fi

$C up -d arisu
echo "恢复完成。应用已重启，迁移幂等自跑，健康检查：$C ps"
