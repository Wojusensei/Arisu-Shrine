#!/bin/sh

set -eu
cd "$(dirname "$0")/.."

if [ -f .env ]; then
    echo "拒绝覆盖：.env 已存在（内含真实密钥）。确认作废请先手动删除。" >&2
    exit 1
fi
DOMAIN=${1:?用法：./scripts/init_server_env.sh <域名> <ACME邮箱>}
EMAIL=${2:?缺少 ACME 邮箱参数}
command -v openssl >/dev/null 2>&1 || { echo "需要 openssl 命令"; exit 1; }

cat > .env <<EOF
DOMAIN=$DOMAIN
ACME_EMAIL=$EMAIL

JWT_SECRET=$(openssl rand -hex 32)
POSTGRES_PASSWORD=$(openssl rand -hex 16)
REDIS_PASSWORD=$(openssl rand -hex 16)

ARISU_SMTP_HOST=
ARISU_SMTP_PORT=465
ARISU_SMTP_USERNAME=
ARISU_SMTP_PASSWORD=
ARISU_SMTP_FROM=

EOF
chmod 600 .env

echo ".env 已生成（权限 600）。"
echo "接下来："
echo "  1. 确认域名 A 记录已指向本机（dig +short ${DOMAIN}）——没生效证书会签不下来"
echo "  2. 编辑 .env 填 ARISU_SMTP_*（不配 SMTP 验证码只打日志，用户无法注册/找回密码）"
echo "  3. docker compose -f docker-compose.prod.yml up -d --build"
echo "  4. 验证：curl https://${DOMAIN}/api/health"
