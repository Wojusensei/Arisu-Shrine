#!/usr/bin/env bash

set -euo pipefail

CHAIN="ARISU-CF-ALLOW"
RANGE_FILE="/opt/arisu/cf_ranges.txt"
LOG="/var/log/arisu_harden.log"
SELF_PATH="/opt/arisu/harden_origin_firewall.sh"
MAX_AGE_DAYS=7

FALLBACK_V4="173.245.48.0/20 103.21.244.0/22 103.22.200.0/22 103.31.4.0/22 141.101.64.0/18 108.162.192.0/18 190.93.240.0/20 188.114.96.0/20 197.234.240.0/22 198.41.128.0/17 162.158.0.0/15 104.16.0.0/13 104.24.0.0/14 172.64.0.0/13 131.0.72.0/22"
FALLBACK_V6="2400:cb00::/32 2606:4700::/32 2803:f800::/32 2405:b500::/32 2405:8100::/32 2a06:98c0::/29 2c0f:f248::/32"

log() { printf '%s %s\n' "$(date -Is)" "$*" | tee -a "$LOG"; }

has_v6_global() { ip -6 addr show scope global 2>/dev/null | grep -q "inet6"; }

fetch_ranges() {
  local v4 v6 n4 tmp
  v4=$(curl -fsS --max-time 15 https://www.cloudflare.com/ips-v4 2>/dev/null || true)
  v6=$(curl -fsS --max-time 15 https://www.cloudflare.com/ips-v6 2>/dev/null || true)
  v4=$(printf '%s\n' "$v4" | grep -E '^[0-9]{1,3}(\.[0-9]{1,3}){3}/[0-9]{1,2}$' | tr '\n' ' ')
  v6=$(printf '%s\n' "$v6" | grep -E '^[0-9a-fA-F:]+/[0-9]{1,3}$' | tr '\n' ' ')
  n4=$(printf '%s' "$v4" | wc -w)
  if [ "$n4" -lt 10 ]; then
    log "CF 网段拉取失败或残缺（v4 $n4 条），沿用现有清单"
    return 1
  fi
  tmp=$(mktemp)
  {
    echo "# Cloudflare 回源网段，$(date -Is) 取自 cloudflare.com/ips-v4 与 ips-v6"
    echo "V4=\"$v4\""
    echo "V6=\"$v6\""
  } > "$tmp"
  mv "$tmp" "$RANGE_FILE"
  log "CF 网段已更新：v4 $n4 条 / v6 $(printf '%s' "$v6" | wc -w) 条"
}

load_ranges() {
  local age_days=999
  if [ -f "$RANGE_FILE" ]; then
    age_days=$(( ( $(date +%s) - $(stat -c %Y "$RANGE_FILE") ) / 86400 ))
  fi
  if [ ! -f "$RANGE_FILE" ] || [ "$age_days" -ge "$MAX_AGE_DAYS" ]; then
    fetch_ranges || true
  fi
  V4=""
  V6=""
  if [ -f "$RANGE_FILE" ]; then

    . "$RANGE_FILE"
  fi
  V4="${V4:-$FALLBACK_V4}"
  V6="${V6:-$FALLBACK_V6}"
}

apply() {
  load_ranges
  local i
  for i in $(seq 1 30); do
    iptables -L DOCKER-USER -n >/dev/null 2>&1 && break
    sleep 2
  done
  if ! iptables -L DOCKER-USER -n >/dev/null 2>&1; then
    log "DOCKER-USER 链不存在（docker 未就绪？），放弃以免规则落空"
    exit 1
  fi

  iptables -N "$CHAIN" 2>/dev/null || true
  iptables -F "$CHAIN"

  local net
  for net in 127.0.0.0/8 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16; do
    iptables -A "$CHAIN" -s "$net" -j RETURN
  done

  local ip
  while read -r ip; do
    [ -n "$ip" ] && iptables -A "$CHAIN" -s "$ip" -j RETURN
  done < <(ip -4 addr show scope global 2>/dev/null | awk '/inet /{split($2,a,"/"); print a[1]}')

  local n=0
  for net in $V4; do
    iptables -A "$CHAIN" -s "$net" -j RETURN
    n=$((n + 1))
  done
  iptables -A "$CHAIN" -j DROP

  if ! iptables -C DOCKER-USER -p tcp -m multiport --dports 80,443 -j "$CHAIN" 2>/dev/null; then
    iptables -I DOCKER-USER 1 -p tcp -m multiport --dports 80,443 -j "$CHAIN"
  fi
  if ! iptables -C DOCKER-USER -p udp --dport 443 -j "$CHAIN" 2>/dev/null; then
    iptables -I DOCKER-USER 1 -p udp --dport 443 -j "$CHAIN"
  fi

  local n6=0
  if has_v6_global; then
    ip6tables -N "$CHAIN" 2>/dev/null || true
    ip6tables -F "$CHAIN"
    ip6tables -A "$CHAIN" -s ::1/128 -j RETURN
    for net in $V6; do
      ip6tables -A "$CHAIN" -s "$net" -j RETURN
      n6=$((n6 + 1))
    done
    ip6tables -A "$CHAIN" -j DROP
    if ! ip6tables -C DOCKER-USER -p tcp -m multiport --dports 80,443 -j "$CHAIN" 2>/dev/null; then
      ip6tables -I DOCKER-USER 1 -p tcp -m multiport --dports 80,443 -j "$CHAIN"
    fi
    if ! ip6tables -C DOCKER-USER -p udp --dport 443 -j "$CHAIN" 2>/dev/null; then
      ip6tables -I DOCKER-USER 1 -p udp --dport 443 -j "$CHAIN"
    fi
  fi

  log "源站收口已应用：放行 $n 个 CF v4 网段（v6 $n6 个）+ 本机与私网，其余来源在 80/443 丢包"
}

status() {
  echo "== 放行链 $CHAIN（IPv4）=="
  iptables -L "$CHAIN" -n --line-numbers 2>/dev/null || echo "(未安装)"
  echo
  echo "== DOCKER-USER 挂点 =="
  iptables -L DOCKER-USER -n --line-numbers 2>/dev/null
  echo
  echo "== 网段清单缓存 =="
  if [ -f "$RANGE_FILE" ]; then
    head -2 "$RANGE_FILE"
    echo "本地缓存条目：v4 $(grep -o 'V4=' "$RANGE_FILE" >/dev/null && sed -n 's/^V4="\(.*\)"$/\1/p' "$RANGE_FILE" | wc -w) / v6 $(sed -n 's/^V6="\(.*\)"$/\1/p' "$RANGE_FILE" | wc -w)"
  else
    echo "(无缓存，apply 时会现拉)"
  fi
}

install_units() {
  if [ "$(id -u)" != "0" ]; then
    echo "需要 root" >&2
    exit 1
  fi
  if [ "$(readlink -f "$0")" != "$SELF_PATH" ]; then
    echo "请先把脚本放到 $SELF_PATH 再装服务" >&2
    exit 1
  fi
  cat > /etc/systemd/system/arisu-firewall.service <<EOF
[Unit]
Description=有栖神社源站防火墙（80/443 只放行 Cloudflare 回源）
After=docker.service network-online.target
Requires=docker.service
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=$SELF_PATH apply

[Install]
WantedBy=multi-user.target
EOF
  cat > /etc/systemd/system/arisu-firewall.timer <<EOF
[Unit]
Description=每周刷新 Cloudflare 回源网段（有栖神社源站防火墙）

[Timer]
OnCalendar=Mon 04:40
Persistent=true

[Install]
WantedBy=timers.target
EOF
  systemctl daemon-reload
  systemctl enable --now arisu-firewall.service
  systemctl enable --now arisu-firewall.timer
  echo "已装：arisu-firewall.service（开机重放规则）+ arisu-firewall.timer（每周一 04:40 刷新网段）"
}

case "${1:-apply}" in
  apply) apply ;;
  refresh) fetch_ranges || true; apply ;;
  status) status ;;
  install-units) install_units ;;
  *)
    echo "用法: $0 [apply|refresh|status|install-units]" >&2
    exit 2
    ;;
esac
