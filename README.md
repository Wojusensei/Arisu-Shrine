# ⛩️ 有栖神社_Arisu Shrine

建造于人间之里外的飘渺存在——由 *东方有栖神社开发组* 研制
- 用户可加入官方 QQ 用户交流群：`1126676443`
- 有意向加入开发组 *东方有栖神社* 可加入官方 QQ 用户交流群后添加管理员为好友进行申请，审核通过可入开发者群

# ⚙️ 开发

## 仓库结构

```
app/          Android 客户端（Kotlin + Jetpack Compose）
server/       服务端（Go + Gin + PostgreSQL + Redis）
web-preview/  网页端设计原型（静态 HTML）
```

## 客户端构建

依赖：JDK 17、Android SDK（compileSdk 35）、Gradle 8.13。

```bash
./gradlew :app:assembleDebug     # 调试包
./gradlew :app:assembleRelease   # 发布包
```

- 发布签名在 `app/signing/keystore.properties` 提供
- `storeFile / storePassword / keyAlias / keyPassword`，缺省发布包不签名

## 服务端部署

```bash
cd server
./scripts/init_server_env.sh <域名> <证书提醒邮箱>   # 生成 .env，随机生成三密钥
docker compose -f docker-compose.prod.yml up -d --build
```

- Caddy 自动签发及续期 Let's Encrypt 证书，对外开放 80/443
- PostergreSQL 与 Redis 不发布宿主端口，仅容器内网可达
- 邮件验证码在 `.env` 填 `ARISU_SMTP_*` 五项（推荐阿里云邮件推送
  `smtpdm.aliyun.com:465`，发信地址参考 `noreply@域名`）；
  未配置时生产模式会拒绝注册验证码请求
- 日常备份：`./scripts/backup.sh`（可挂 crontab）数据库全量以及 uploads 打包

## 数据库迁移

服务端启动时自动执行 `server/internal/store/migrations/` 下的迁移

## 说明

- 本仓库为开源版，正式发行版本可能与开源版存在一点差异，内部开发采用私密仓库托管代码
- 本项目包含东方 Project 二次创作元素，但与上海爱丽丝幻乐团**无隶属关系**

## 许可证

[GPL-3.0](LICENSE)
