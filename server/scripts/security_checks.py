#!/usr/bin/env python3
"""安全自检（交付前全跑）：生产闸在位、敏感物不入库、客户端明文与令牌加固。
与 api_checks.py 同级交付门；退出码非 0 即有安全项缺失。详见 docs/测试清单.md。"""
import os, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PASS, FAIL = [], []

def check(label, ok, detail=""):
    (PASS if ok else FAIL).append(label)
    print(("PASS " if ok else "FAIL ") + label + (("  " + detail) if (detail and not ok) else ""))

def read(rel):
    try:
        return open(os.path.join(ROOT, rel), encoding="utf-8").read()
    except OSError:
        return ""

def git(*args):
    return subprocess.run(["git", "-C", ROOT, *args], capture_output=True, text=True).stdout

# ---- 生产闸（服务端）----
yaml = read("server/config.yaml")
check("S1 jwt.secret 只留占位符", 'secret: "change-me-to-a-long-random-string-please"' in yaml)
check("S2 仓库配置无真实密钥形态", "13f41480b380" not in yaml)
main_go = read("server/cmd/arisu/main.go")
check("S3 dev_mode 需 ARISU_ALLOW_DEV_MODE=1 显式放行", "ARISU_ALLOW_DEV_MODE" in main_go)
check("S4 占位符 secret 启动即拒", "change-me-to-a-long-random-string-please" in main_go)
check("S5 ARISU_JWT_SECRET 显式 BindEnv", "BindEnv(\"jwt.secret\")" in read("server/internal/cfg/config.go"))
web_go = read("server/internal/web/web.go")
check("S6 可信代理走配置（留空不信任）", "SetTrustedProxies(w.Cfg.TrustedProxies)" in web_go)
check("S7 refresh 挂每 IP 限流", 'auth.POST("/refresh", w.refreshRateLimit(), w.refresh)' in web_go)
check("S8 bcrypt cost=12", "const bcryptCost = 12" in read("server/internal/svc/auth.go"))
check("S9 compose 端口只绑回环", '"127.0.0.1:8080:8080"' in read("server/docker-compose.yml"))
check("S10 gitignore 锚定二进制且忽略 .jwt-secret", "/arisu" in read("server/.gitignore") and ".jwt-secret" in read("server/.gitignore"))

# ---- 敏感物不入库 ----
tracked = git("ls-files")
check("S11 .jwt-secret 未入库", ".jwt-secret" not in tracked)
check("S12 签名密钥/签名配置未入库", "app/signing/" not in tracked and "debug-keystore.properties" not in tracked)
check("S13 已泄露旧密钥不在当前任何已跟踪文件", subprocess.run(
    ["git", "-C", ROOT, "grep", "-q", "13f41480b380", "--", ":(exclude)server/scripts/security_checks.py"],
    capture_output=True).returncode != 0)

# ---- 客户端加固 ----
manifest = read("app/src/main/AndroidManifest.xml")
check("S14 正式清单无 usesCleartextTraffic", 'usesCleartextTraffic="true"' not in manifest)
check("S15 清单挂 networkSecurityConfig", 'android:networkSecurityConfig="@xml/network_security_config"' in manifest)
check("S16 main 明文策略=禁", 'cleartextTrafficPermitted="false"' in read("app/src/main/res/xml/network_security_config.xml"))
check("S17 debug 覆盖=放行（开发服是 http）", 'cleartextTrafficPermitted="true"' in read("app/src/debug/res/xml/network_security_config.xml"))
check("S18 TokenCrypto 存在", os.path.exists(os.path.join(ROOT, "app/src/main/java/dev/arisu/shrine/data/TokenCrypto.kt")))
store = read("app/src/main/java/dev/arisu/shrine/data/SessionStore.kt")
vault = read("app/src/main/java/dev/arisu/shrine/data/AccountVault.kt")
check("S19 会话令牌读写过 TokenCrypto", "TokenCrypto.decrypt" in store and "TokenCrypto.encrypt" in store)
check("S20 账号档案令牌读写过 TokenCrypto", "TokenCrypto.decrypt" in vault and "TokenCrypto.encrypt" in vault)
check("S21 更新链接 scheme 白名单", 'scheme == "http" || scheme == "https"' in read(
    "app/src/main/java/dev/arisu/shrine/feature/shrine/ShrineBoardScreen.kt"))

# ---- 生产部署物（T97 部署 runbook）----
prod = read("server/docker-compose.prod.yml")
check("S22 生产编排只暴露 Caddy 80/443，DB/Redis/应用零宿主端口",
      '"80:80"' in prod and '"443:443"' in prod
      and "5432:5432" not in prod and "6379:6379" not in prod and "8080:8080" not in prod)
check("S23 生产编排密钥缺项拒启（:? 语法）", prod.count(":?") >= 3)
check("S24 .env 已忽略且未入库", ".env" in read("server/.gitignore")
      and "server/.env" not in [l for l in tracked.splitlines() if l != "server/.env.example"])
check("S25 Caddyfile 域名/邮箱走 .env 注入，无硬编码域名",
      "{$DOMAIN}" in read("server/Caddyfile.prod") and "example.com" not in read("server/Caddyfile.prod"))
check("S26 深度健康+优雅停机在位", "Pool.Ping" in web_go and "srv.Shutdown" in read("server/cmd/arisu/main.go"))
check("S27 备份/恢复/更新/init 四脚本在位", all(
    os.access(os.path.join(ROOT, "server/scripts", n), os.X_OK) for n in
    ("backup.sh", "restore.sh", "update.sh", "init_server_env.sh")))

print(f"\n== 安全自检 通过 {len(PASS)} / {len(PASS)+len(FAIL)} ==")
sys.exit(1 if FAIL else 0)
