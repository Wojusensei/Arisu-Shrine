# 有栖神社后端

Go + Gin + PostgreSQL + Redis。单仓库单二进制，迁移内嵌在程序里，首次启动自动建表。

## 本机运行

```bash
docker compose up -d postgres redis   # 数据库（127.0.0.1:5432 / 6379）
go run ./cmd/arisu                    # 服务 :8080
```

配置在 `config.yaml`，可用环境变量覆盖（前缀 `ARISU_`，如 `ARISU_SMTP_PASSWORD`）。

## 接口一览（/api 前缀）

| 组 | 端点 |
|---|---|
| 认证 | `POST auth/code`（发验证码）· `POST auth/register` · `POST auth/login` · `POST auth/reset` · `POST auth/refresh` · `POST auth/logout` |
| 我 | `GET/PATCH me` · `PATCH me/password` · `POST me/deactivate[/cancel]` · `GET me/export` · `GET me/achievements` · `GET me/room` · `GET me/posts?tab=` · `GET/POST me/collections` · `GET me/following|followers` · `POST me/heartbeat` |
| 用户 | `GET users/{id}` · `GET users/{id}/tiles|posts` · `POST users/{id}/follow` · `DELETE users/{id}/follow` · `POST users/{id}/special` · `POST users/{id}/report` · `POST users/{id}/warn`(奉仕+) · `POST users/{id}/mute`(宫司+) · `POST users/{id}/ban`(宫司+) · `POST users/{id}/role`(神主) |
| 帖子 | `GET/POST posts` · `GET/PATCH/DELETE posts/{id}` · `POST posts/{id}/up|track|favorite|essence|pin|report` · `GET/POST posts/{id}/comments` · `POST comments/{id}/like` · `DELETE comments/{id}` |
| 绘马 | `GET/POST emas` · `GET/PATCH/DELETE emas/{id}` · `POST emas/{id}/state`(奉仕+) · `GET/POST emas/{id}/replies` |
| 奉仕会 | `GET staff` · `POST staff/apply` · `GET staff/my-application` · `GET staff/applications`(宫司+) · `POST staff/applications/{id}/decide`(宫司+) |
| 社务箱 | `GET notices` · `GET notices/unread` · `POST notices/{id}/read` · `POST notices/read-all` · `DELETE notices/{id}` |
| 举报 | `GET reports`(奉仕+) · `POST reports/{id}/resolve`(奉仕+) |
| 搜索 | `GET search?q=&regex=` |
| 游会 | `GET play/online` · `POST play/omikuji`（每日一签）· `GET play/omikuji/history` |
| 日志 | `GET releases` · `GET releases/check?version=` · `POST releases`(宫司+) |
| 其他 | `POST upload`（multipart，jpg/png/gif/webp ≤10MB）· `GET /uploads/*` · `GET /api/health` |

错误统一为 `{"error": "中文提示"}` + 对应 HTTP 状态码。

## 业务规则速查

- **首位注册用户自动成为神主**（`User_000001`），其余为社员
- 验证码：6 位、10 分钟有效、60s 重发冷却、每邮箱每日 10 次、每 IP 每日 30 次、输错 5 次作废；
  收件邮箱**不做任何域名限制**（Outlook/Hotmail/QQ/163/Gmail/edu 均可）
- SMTP 未配置 → 验证码打印日志（开发模式），接口同时返回 `dev_code` 方便联调；生产务必配置
- JWT：access 24h + refresh 30 天滑动续期（每次刷新旧 token 作废）；登出/注销即吊销
- 频控：发帖 30s（注册 24h 内 120s）、评论 8s（`limits` 段可调）
- 禁言：`muted_until`，永久禁言用远期时间；发布类接口直接 403 并返回剩余时间
- 注销：`deactivated_at` 冷静期 24h，期间任何登录自动取消；超时由后台任务匿名化（帖子保留）
- 通知聚合：追踪互动每帖每日一条（`dedupe_key`），关注通知受接收方 `notify_scope` 控制，
  特别关注不受限
- 成就：`founder`（首位注册）/ `first_post` / `hot_author`（单帖顶数达标）/ `first_ema` / `omikuji`

## 上云部署（香港 VPS + Ubuntu）

```bash
# 服务器上
cp -r server/ /opt/arisu && cd /opt/arisu
vim config.yaml        # base_url 填域名、jwt.secret 换长随机串、smtp 填正式账号
docker compose up -d --build
```

Caddy 反代模板见 `Caddyfile.example`（自动 Let's Encrypt）。数据都在 compose 的 named
volume（`pgdata`/`redisdata`/`uploads`），备份 = `docker run --rm -v arisu_pgdata:/data -v
$(pwd):/backup alpine tar czf /backup/pg.tgz /data`。

## 冒烟脚本

```bash
BASE=http://127.0.0.1:8080
# 1) 发码（开发模式直接回 dev_code）
CODE=$(curl -s $BASE/api/auth/code -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","purpose":"register"}' | jq -r .dev_code)
# 2) 注册（第一位 = 神主）
curl -s $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"you@example.com\",\"code\":\"$CODE\",\"password\":\"secret123\",\"nickname\":\"有栖\"}"
# 3) 登录拿 token，后续请求带 Authorization: Bearer <access_token>
```
