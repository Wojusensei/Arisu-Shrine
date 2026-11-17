#!/bin/sh
# 更新部署：拉代码 → 重建镜像 → 滚动替换 → 等健康检查通过。
# 数据库迁移在容器启动时自动执行（幂等），无需单独跑。
# 用法：./scripts/update.sh
set -eu
cd "$(dirname "$0")/.."

C="docker compose -f docker-compose.prod.yml"

git pull --ff-only
$C build arisu
$C up -d
docker image prune -f >/dev/null

printf "等待健康检查"
i=0
until [ "$i" -ge 40 ]; do
    if $C exec -T arisu wget -qO- http://127.0.0.1:8080/api/health 2>/dev/null | grep -q '"ok":true'; then
        echo " ✓ 服务健康"
        exit 0
    fi
    printf "."
    i=$((i + 1))
    sleep 3
done
echo ""
echo "健康检查 2 分钟未通过，查日志：docker compose -f docker-compose.prod.yml logs --tail=100 arisu" >&2
exit 1
