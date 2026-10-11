# 有栖神社（Arisu Shrine）

建造于人间之里外的飘渺存在——一个东方 Project 主题的中文论坛社区。

客户端 Kotlin + Jetpack Compose，服务端 Go + Gin + PostgreSQL + Redis，Docker Compose 一键部署，Caddy 自动 HTTPS。

## 功能一览

- 帖子与评论：Markdown 正文、图片直传、外部 SVG 徽章/统计卡渲染
- 板块：有栖茶话（综合）、神社祝部（绘马）、神社游会、我的客房（个人主页）
- 个人主页自定义区域：Markdown + CSS 自定义、徽章墙
- 社务箱（站内信）、关注与粉丝、收藏夹、搜索
- 邮箱验证码注册/登录、设备会话管理、角色体系（神主/奉仕/社员）
- 每日签到运势、热帖机制、新人限频等社区治理细节

## 仓库结构

```
app/          Android 客户端（Kotlin + Jetpack Compose）
server/       服务端（Go + Gin + PostgreSQL + Redis）
web-preview/  网页端设计原型（静态 HTML）
```

## 客户端构建

要求：JDK 17、Android SDK（compileSdk 35）、Gradle 8.13。

```bash
./gradlew :app:assembleDebug     # 调试包
./gradlew :app:assembleRelease   # 发布包
```

发布签名（可选）：在 `app/signing/keystore.properties` 提供
`storeFile / storePassword / keyAlias / keyPassword`，缺省时发布包不签名。

## 服务端部署

要求：一台服务器（境外机器可免域名备案）、Docker 与 Docker Compose、一个已解析到服务器的域名。

```bash
cd server
./scripts/init_server_env.sh <你的域名> <证书提醒邮箱>   # 生成 .env，三个密钥随机生成
docker compose -f docker-compose.prod.yml up -d --build
```

- Caddy 会自动签发并续期 Let's Encrypt 证书，对外只开 80/443
- 数据库与 Redis 不发布宿主端口，仅容器内网可达
- 邮件验证码：在 `.env` 填 `ARISU_SMTP_*` 五项（推荐阿里云邮件推送
  `smtpdm.aliyun.com:465`，发信地址形如 `noreply@你的域名`）；
  未配置时生产模式会拒绝注册验证码请求
- 日常备份：`./scripts/backup.sh`（可挂 crontab），数据库全量 + uploads 打包

## 数据库迁移

服务端启动时自动执行 `server/internal/store/migrations/` 下的迁移，按文件名顺序、幂等。

## 说明

- 本仓库为开源版，正式发行版本可能与开源版存在差异
- 本项目为东方 Project 二次创作作品，与上海爱丽丝幻乐团无隶属关系

## 许可证

[GPL-3.0](LICENSE)
