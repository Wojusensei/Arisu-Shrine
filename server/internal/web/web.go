package web

import (
	"context"
	"io/fs"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"

	"arisu/server/internal/cfg"
	"arisu/server/internal/store"
	"arisu/server/internal/svc"
)

type Web struct {
	St     *store.Store
	Cfg    *cfg.Config
	RDB    redis.UniversalClient
	Mailer *svc.Mailer
	Log    *zap.Logger
}

func (w *Web) err(c *gin.Context, status int, msg string) {
	c.AbortWithStatusJSON(status, gin.H{"error": msg})
}

func (w *Web) notFound(c *gin.Context) {
	w.err(c, http.StatusNotFound, "内容不存在或已被删除")
}

func (w *Web) bad(c *gin.Context, msg string) {
	w.err(c, http.StatusBadRequest, msg)
}

// pathID 解析路径参数里的正整数 ID。
// 除纯数字外还容忍 "User_000123" 形式：模板里所有用户链接都由
// model.FormatUserNo 生成的 User_%06d 拼出（/u/User_000001），只认数字
// 会让全站用户主页 404。前缀按大小写不敏感剥离，其余格式仍拒绝。
func pathID(c *gin.Context, name string) (int64, bool) {
	raw := c.Param(name)
	id, err := strconv.ParseInt(raw, 10, 64)
	if err != nil {
		const userPrefix = "User_"
		if len(raw) > len(userPrefix) && strings.EqualFold(raw[:len(userPrefix)], userPrefix) {
			id, err = strconv.ParseInt(raw[len(userPrefix):], 10, 64)
		}
	}
	if err != nil || id <= 0 {
		return 0, false
	}
	return id, true
}

func clampPage(c *gin.Context) (page, size int) {
	page, _ = strconv.Atoi(c.DefaultQuery("page", "1"))
	size, _ = strconv.Atoi(c.DefaultQuery("page_size", "20"))
	if page < 1 {
		page = 1
	}
	// 深页封顶：page 无上界时 (page-1)*size 会整型溢出成负 OFFSET，
	// Postgres 直接语法错误变 500，且每次深翻页都要跑两条全量 COUNT
	if page > 10000 {
		page = 10000
	}
	if size < 1 || size > 50 {
		size = 20
	}
	return page, size
}

// reasonTooLong 管理理由/举报理由统一 500 字上限（与客户端输入框一致），
// 超长内容会原样进 notices.content 并随列表全量下发
func reasonTooLong(s string) bool {
	return len([]rune(strings.TrimSpace(s))) > 500
}

// ---- 路由 ----

func (w *Web) Router() *gin.Engine {
	r := gin.New()
	// ClientIP 信任网段走配置 trusted_proxies：直连部署留空=不信任任何代理头
	// （gin 默认信任全网段，请求方可随意伪造 X-Forwarded-For 让登录防爆破的
	// IP 锁与验证码每 IP 限额全部失效）；反代部署显式配可信网段。
	// 上游自己重写来源地址的部署（如 Cloudflare）改用 client_ip_header，见 clientIP
	_ = r.SetTrustedProxies(w.Cfg.TrustedProxies)
	// 全局请求体上限：15MB，防止超大 JSON/表单打满内存与磁盘
	r.Use(gin.Recovery(), w.accessLog(), func(c *gin.Context) {
		c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, 15<<20)
		c.Next()
	})
	r.MaxMultipartMemory = 12 << 20

	// 全站每来源请求总量闸门挂在这一组上：健康检查也吃它——
	// 那是最便宜却最贵的端点（每次两条外部探活），洪泛时先被掐掉。
	// 接口响应按登录者返回（御签榜的附近窗口、拉黑过滤、未读数都因人而异），
	// 明确禁止中间层缓存，避免边缘节点把 A 的响应发给 B
	api := r.Group("/api", w.floodGuard(), func(c *gin.Context) {
		c.Header("Cache-Control", "no-store")
		c.Next()
	})

	// 深度健康检查：不只回进程活着，DB/Redis 探不通回 503，
	// 监控和 docker healthcheck 才能区分「进程在」和「服务真的能用」
	api.GET("/health", func(c *gin.Context) {
		hctx, cancel := context.WithTimeout(c.Request.Context(), time.Second)
		defer cancel()
		dbOK := w.St.Pool.Ping(hctx) == nil
		redisOK := w.RDB.Ping(hctx).Err() == nil
		status := http.StatusOK
		if !dbOK || !redisOK {
			status = http.StatusServiceUnavailable
		}
		c.JSON(status, gin.H{"ok": status == http.StatusOK, "db": dbOK, "redis": redisOK, "time": time.Now().Unix()})
	})
	// 静态图片：禁用目录列表 + nosniff；HEAD 也要认（下载器探测、gin 未注册方法回 404）
	// 不挂 API 的洪泛闸（预算按接口算的，会误伤图片密集的浏览），改挂独立的宽松闸
	r.GET("/uploads/*filepath", w.uploadsGuard(), serveUploads(w))
	r.HEAD("/uploads/*filepath", w.uploadsGuard(), serveUploads(w))

	// 网页端（SSR）：cookie 会话 + 服务端渲染页面，见 webapp.go
	w.webappRoutes(r)

	// 认证（无需登录）
	auth := api.Group("/auth")
	auth.POST("/code", w.sendCode)
	auth.POST("/register", w.register)
	auth.POST("/login", w.login)
	auth.POST("/reset", w.resetPassword)
	auth.POST("/refresh", w.refreshRateLimit(), w.refresh)

	// 以下均需登录
	guarded := api.Group("", w.authRequired())
	{
		guarded.POST("/auth/logout", w.logout)
		guarded.GET("/me", w.getMe)
		guarded.PATCH("/me", w.patchMe)
		guarded.PATCH("/me/password", w.changePassword)
		guarded.POST("/me/email/change-request", w.emailChangeRequest)
		guarded.POST("/me/email/change", w.emailChange)
		guarded.POST("/me/deactivate", w.deactivate)
		guarded.POST("/me/accounts/:id/swap", w.webAccountSwap)
		guarded.DELETE("/me/accounts/:id", w.webAccountRemove)
		guarded.GET("/me/export", w.exportPosts)
		guarded.GET("/me/achievements", w.myAchievements)
		guarded.GET("/me/collections", w.myCollections)
		guarded.POST("/me/collections", w.newCollection)
		guarded.DELETE("/me/collections/:id", w.deleteCollection)
		guarded.GET("/me/following", w.myFollowing)
		guarded.GET("/me/followers", w.myFollowers)
		guarded.GET("/me/room", w.myRoom)
		guarded.GET("/me/posts", w.myPosts)
		guarded.GET("/me/blocks", w.myBlocks)
		guarded.GET("/me/sessions", w.listSessions)
		guarded.DELETE("/me/sessions/:id", w.kickSession)
		guarded.POST("/me/heartbeat", w.heartbeat)

		guarded.GET("/users/:id", w.userDetail)
		guarded.GET("/users/:id/tiles", w.userTiles)
		guarded.GET("/users/:id/posts", w.userPosts)
		guarded.POST("/users/:id/follow", w.follow)
		guarded.POST("/users/:id/block", w.blockUser)
		guarded.DELETE("/users/:id/block", w.unblockUser)
		guarded.DELETE("/users/:id/follow", w.unfollow)
		guarded.POST("/users/:id/report", w.reportUser)
		guarded.POST("/users/:id/warn", w.warnUser)     // 奉仕+
		guarded.POST("/users/:id/mute", w.muteUser)     // 宫司+
		guarded.POST("/users/:id/unmute", w.unmuteUser) // 宫司+
		guarded.POST("/users/:id/ban", w.banUser)       // 宫司+
		guarded.POST("/users/:id/unban", w.unbanUser)   // 宫司+
		guarded.POST("/users/:id/role", w.setRole)      // 神主

		guarded.GET("/posts", w.listPosts)
		guarded.POST("/posts", w.createPost)
		guarded.GET("/posts/:id", w.getPost)
		guarded.PATCH("/posts/:id", w.updatePost)
		guarded.DELETE("/posts/:id", w.deletePost)
		guarded.POST("/posts/:id/up", w.toggleUp)
		guarded.POST("/posts/:id/offer", w.offerPost)
		guarded.POST("/posts/:id/track", w.toggleTrack)
		guarded.POST("/posts/:id/favorite", w.toggleFavorite)
		guarded.POST("/posts/:id/essence", w.toggleEssence) // 宫司+
		guarded.POST("/posts/:id/pin", w.togglePin)         // 宫司+
		guarded.POST("/posts/:id/report", w.reportPost)
		guarded.GET("/posts/:id/comments", w.listComments)
		guarded.POST("/posts/:id/comments", w.createComment)
		guarded.POST("/comments/:id/like", w.toggleCommentLike)
		guarded.POST("/comments/:id/report", w.reportComment)
		guarded.DELETE("/comments/:id", w.deleteComment)

		guarded.GET("/emas", w.listEmas)
		guarded.POST("/emas", w.createEma)
		guarded.GET("/emas/:id", w.getEma)
		guarded.PATCH("/emas/:id", w.updateEma)
		guarded.DELETE("/emas/:id", w.deleteEma)
		guarded.POST("/emas/:id/state", w.setEmaState) // 奉仕+
		guarded.GET("/emas/:id/replies", w.listEmaReplies)
		guarded.POST("/emas/:id/replies", w.addEmaReply)
		guarded.DELETE("/emas/:id/replies/:rid", w.deleteEmaReply)
		guarded.POST("/emas/:id/report", w.reportEma)

		guarded.GET("/staff", w.staff)
		guarded.POST("/staff/apply", w.applyStaff)
		guarded.GET("/staff/my-application", w.myStaffApplication)
		guarded.GET("/staff/applications", w.pendingApplications)           // 宫司+
		guarded.POST("/staff/applications/:id/decide", w.decideApplication) // 宫司+

		guarded.GET("/notices", w.listNotices)
		guarded.GET("/notices/unread", w.unreadCount)
		guarded.GET("/notices/:id", w.getNotice)
		guarded.POST("/notices/:id/read", w.readNotice)
		guarded.POST("/notices/:id/unread", w.markNoticeUnread)
		guarded.POST("/notices/:id/star", w.starNotice)
		guarded.POST("/notices/read-all", w.readAllNotices)
		guarded.DELETE("/notices/:id", w.deleteNotice)

		guarded.GET("/reports", w.listReports)                // 奉仕+
		guarded.POST("/reports/:id/resolve", w.resolveReport) // 奉仕+

		guarded.GET("/search", w.search)
		guarded.GET("/search/users", w.searchMentionUsers)
		guarded.GET("/play/online", w.onlineStats)
		guarded.POST("/play/omikuji", w.drawOmikuji)
		guarded.GET("/play/omikuji/today", w.omikujiToday)
		guarded.GET("/play/omikuji/history", w.omikujiHistory)
		guarded.GET("/play/omikuji/leaderboard", w.omikujiLeaderboard)
		guarded.GET("/releases", w.listReleases)
		guarded.GET("/releases/check", w.checkUpdate)
		guarded.POST("/releases", w.createRelease) // 宫司+
		guarded.POST("/upload", w.upload)

		guarded.GET("/stickers", w.myStickers)
		guarded.POST("/stickers", w.addSticker)
		guarded.POST("/stickers/reorder", w.reorderStickers)
		guarded.DELETE("/stickers/:id", w.deleteSticker)
		guarded.GET("/users/:id/stickers", w.userStickers)
	}
	return r
}

// serveUploads 静态文件交给 FileServer；GET 与 HEAD 共用
func serveUploads(w *Web) gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("X-Content-Type-Options", "nosniff")
		// 上传文件名落盘时随机生成，内容即身份、永不改写：给足一年，让
		// Cloudflare 边缘与客户端磁盘缓存长期持有（此前没有任何缓存头，
		// CF 只按默认给 4 小时，过期就回香港源站重拉）。APK 按版本号命名、
		// 同版本重传会换内容，只给一天。
		if strings.HasSuffix(strings.ToLower(c.Request.URL.Path), ".apk") {
			c.Header("Cache-Control", "public, max-age=86400")
		} else {
			c.Header("Cache-Control", "public, max-age=31536000, immutable")
		}
		// ?w=400/800 走缩略图（jpg/png 才生成，其余格式原样下发）
		if width := thumbWidthOf(c.Query("w")); width > 0 {
			if w.serveThumb(c, width) {
				return
			}
		}
		// 安装包必须按 APK 报 MIME：FileServer 的扩展名表里没有 .apk，会嗅探成
		// application/zip，浏览器照 MIME 存成「xxx.apk.zip」，用户装不上
		if strings.HasSuffix(strings.ToLower(c.Request.URL.Path), ".apk") {
			c.Header("Content-Type", "application/vnd.android.package-archive")
		}
		// 必须剥掉 /uploads 前缀再交给 FileServer，否则全部 404
		http.StripPrefix("/uploads", http.FileServer(noDirListFS{http.Dir(w.Cfg.Upload.Dir)})).ServeHTTP(c.Writer, c.Request)
	}
}

// noDirListFS 包装 http.Dir：目录访问一律 404，不暴露文件列表
type noDirListFS struct{ root http.FileSystem }

func (n noDirListFS) Open(name string) (http.File, error) {
	f, err := n.root.Open(name)
	if err != nil {
		return nil, err
	}
	st, err := f.Stat()
	if err != nil {
		f.Close()
		return nil, err
	}
	if st.IsDir() {
		f.Close()
		return nil, fs.ErrNotExist
	}
	return f, nil
}

func (w *Web) accessLog() gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next()
		if c.FullPath() == "" {
			return
		}
		w.Log.Info("req",
			zap.String("m", c.Request.Method), zap.String("p", c.Request.URL.Path),
			zap.Int("s", c.Writer.Status()), zap.Duration("d", time.Since(start)),
			zap.String("ip", w.clientIP(c)),
			zap.String("ua", c.Request.UserAgent()),
			zap.Int64("cl", c.Request.ContentLength))
	}
}
