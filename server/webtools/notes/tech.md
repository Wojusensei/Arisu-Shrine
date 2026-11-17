---
slug: tech
title: 技术栈
summary: 客户端、网页版、服务端与部署环境使用的主要技术。
section: project
order: 10
related: web, contact, download
---

本页列出的版本号取自当前仓库的构建文件。依赖升级后，请同步本页。

## 客户端

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| Kotlin | 2.1.20 | 客户端语言 |
| Android Gradle Plugin | 8.9.1 | 构建 |
| Jetpack Compose | BOM 2025.04.01 | UI |
| Material 3 | BOM 管理 | 设计组件 |
| Navigation Compose | 2.8.9 | 页面导航 |
| Lifecycle / ViewModel | 2.8.7 | 生命周期与状态 |
| Activity Compose | 1.10.1 | 宿主 Activity |
| Coil | 3.1.0 | 图片加载（Compose / OkHttp / SVG / GIF） |
| kotlinx.serialization | 1.8.0 | JSON 序列化 |
| Retrofit | 2.11.0 | HTTP 客户端 |
| OkHttp | 4.12.0 | 网络传输与日志 |
| DataStore Preferences | 1.1.3 | 本地偏好与令牌 |
| 协程 + Flow | Kotlin 2.1.20 | 异步与数据流 |
| JUnit / Robolectric / Roborazzi | 4.13.2 / 4.14.1 / 1.46.1 | 单元、UI 与截图测试 |

- `compileSdk` 35、`targetSdk` 35、`minSdk` 26，Java 17。
- 当前版本：`versionCode 62`、`versionName 1.1.2.0`。
- Markdown 渲染、新拟物组件、图片取景与表情包面板由项目内代码实现。

## 服务端

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| Go | 1.24.0 | 服务端语言 |
| Gin | 1.11.0 | HTTP 路由与中间件 |
| pgx | 5.7.6 | PostgreSQL 驱动 |
| PostgreSQL | 16 | 主数据库 |
| Redis | 7 | 缓存、限频与验证码 |
| go-redis | 9.17.0 | Redis 客户端 |
| golang-jwt | 5.3.0 | access / refresh token |
| bcrypt（x/crypto） | 0.43.0 | 密码哈希 |
| Viper | 1.21.0 | 配置加载 |
| zap | 1.27.0 | 结构化日志 |
| go-mail | 0.7.2 | SMTP 验证码邮件 |

- 服务端以 REST + JSON 提供接口，数据库迁移随二进制启动执行。
- 图片上传后由服务端生成缩略图；上传目录与数据库分开存放。

## 网页版

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| Go | 1.24.0 | 服务端渲染语言 |
| html/template + go:embed | Go 标准库 | 模板渲染与静态资产内嵌 |
| Gin | 1.11.0 | HTTP 路由与中间件 |
| 原生 JavaScript | 无框架 | 交互与局部更新 |
| 手写 CSS | 无框架 | 新拟物主题，与客户端同一套色值与字号 |
| Cookie 会话 | — | access / refresh 双令牌，透明续期 |

- 页面由服务端渲染，禁用脚本仍可阅读与提交表单；脚本只负责局部更新与动效。
- 手机与桌面共用同一套模板，靠 CSS 断点在 900 像素处切换底部导航与左侧栏。
- 文档站与落地页是独立的静态站点，由同一台 Caddy 托管。

## 部署

| 组件 | 说明 |
| --- | --- |
| 服务器 | 香港 VPS，Debian 12 |
| 容器 | Docker Compose |
| 反向代理 | Caddy 2，自动申请 Let's Encrypt 证书 |
| 边缘网络 | Cloudflare DNS 与 CDN |
| 数据库 | PostgreSQL 16，容器内网访问 |
| 缓存 | Redis 7，容器内网访问 |
| 备份 | 每日 `pg_dump`，保留 7 份 |
| 远程访问 | SSH 仅密钥登录 |

## 构建与发布

- 客户端使用 Gradle Kotlin DSL 构建签名 APK；发布包上传到站点的 `uploads` 目录，并写入更新记录。
- 服务端、数据库、缓存与 Caddy 由 Docker Compose 编排；静态页面由 Caddy 直接托管。
- 公开仓库：[Wojusensei/Arisu-Shrine](https://github.com/Wojusensei/Arisu-Shrine)。

::: note
本页不列出服务器地址、密钥与第三方账号信息。
:::
