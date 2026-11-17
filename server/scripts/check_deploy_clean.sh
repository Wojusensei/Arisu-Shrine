#!/bin/sh
# 部署后自检：确认代码目录没有被 macOS 元数据污染。
#
# 2026-11-17 事故：从 macOS 传代码到生产后，internal/store/migrations/ 下多出
# 223 个 AppleDouble 伴生文件（`._001_init.sql` 之类），迁移器把它们当 SQL 执行，
# 报 `invalid message format (SQLSTATE 08P01)`，后端反复重启起不来。
# 部署后跑一遍这个脚本，或把它接进 update.sh 的尾部。
set -eu
cd "$(dirname "$0")/.."

BAD=$(find . -name '._*' -not -path './uploads/*' | wc -l)
if [ "$BAD" -gt 0 ]; then
    echo "发现 $BAD 个 AppleDouble 文件，清理中..."
    find . -name '._*' -delete
fi
STRAY=$(find . -name '.DS_Store' | wc -l)
if [ "$STRAY" -gt 0 ]; then
    find . -name '.DS_Store' -delete
    echo "清理 $STRAY 个 .DS_Store"
fi

N=$(ls internal/store/migrations/*.sql 2>/dev/null | wc -l)
echo "迁移脚本 $N 个"
if [ "$N" -lt 20 ]; then
    echo "迁移脚本数量异常（应 ≥20），检查是否漏传" >&2
    exit 1
fi
echo "自检通过"
