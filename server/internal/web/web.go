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

func pathID(c *gin.Context, name string) (int64, bool) {
	id, err := strconv.ParseInt(c.Param(name), 10, 64)
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

	if page > 10000 {
		page = 10000
	}
	if size < 1 || size > 50 {
		size = 20
	}
	return page, size
}

func reasonTooLong(s string) bool {
	return len([]rune(strings.TrimSpace(s))) > 500
}

func (w *Web) Router() *gin.Engine {
	r := gin.New()

	_ = r.SetTrustedProxies(w.Cfg.TrustedProxies)

	r.Use(gin.Recovery(), w.accessLog(), func(c *gin.Context) {
		c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, 15<<20)
		c.Next()
	})
	r.MaxMultipartMemory = 12 << 20

	api := r.Group("/api", w.floodGuard())

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

	r.GET("/uploads/*filepath", serveUploads(w))
	r.HEAD("/uploads/*filepath", serveUploads(w))

	auth := api.Group("/auth")
	auth.POST("/code", w.sendCode)
	auth.POST("/register", w.register)
	auth.POST("/login", w.login)
	auth.POST("/reset", w.resetPassword)
	auth.POST("/refresh", w.refreshRateLimit(), w.refresh)

	guarded := api.Group("", w.authRequired())
	{
		guarded.POST("/auth/logout", w.logout)
		guarded.GET("/me", w.getMe)
		guarded.PATCH("/me", w.patchMe)
		guarded.PATCH("/me/password", w.changePassword)
		guarded.POST("/me/email/change-request", w.emailChangeRequest)
		guarded.POST("/me/email/change", w.emailChange)
		guarded.POST("/me/deactivate", w.deactivate)
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
		guarded.POST("/users/:id/warn", w.warnUser)
		guarded.POST("/users/:id/mute", w.muteUser)
		guarded.POST("/users/:id/unmute", w.unmuteUser)
		guarded.POST("/users/:id/ban", w.banUser)
		guarded.POST("/users/:id/unban", w.unbanUser)
		guarded.POST("/users/:id/role", w.setRole)

		guarded.GET("/posts", w.listPosts)
		guarded.POST("/posts", w.createPost)
		guarded.GET("/posts/:id", w.getPost)
		guarded.PATCH("/posts/:id", w.updatePost)
		guarded.DELETE("/posts/:id", w.deletePost)
		guarded.POST("/posts/:id/up", w.toggleUp)
		guarded.POST("/posts/:id/track", w.toggleTrack)
		guarded.POST("/posts/:id/favorite", w.toggleFavorite)
		guarded.POST("/posts/:id/essence", w.toggleEssence)
		guarded.POST("/posts/:id/pin", w.togglePin)
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
		guarded.POST("/emas/:id/state", w.setEmaState)
		guarded.GET("/emas/:id/replies", w.listEmaReplies)
		guarded.POST("/emas/:id/replies", w.addEmaReply)
		guarded.DELETE("/emas/:id/replies/:rid", w.deleteEmaReply)
		guarded.POST("/emas/:id/report", w.reportEma)

		guarded.GET("/staff", w.staff)
		guarded.POST("/staff/apply", w.applyStaff)
		guarded.GET("/staff/my-application", w.myStaffApplication)
		guarded.GET("/staff/applications", w.pendingApplications)
		guarded.POST("/staff/applications/:id/decide", w.decideApplication)

		guarded.GET("/notices", w.listNotices)
		guarded.GET("/notices/unread", w.unreadCount)
		guarded.GET("/notices/:id", w.getNotice)
		guarded.POST("/notices/:id/read", w.readNotice)
		guarded.POST("/notices/:id/unread", w.markNoticeUnread)
		guarded.POST("/notices/:id/star", w.starNotice)
		guarded.POST("/notices/read-all", w.readAllNotices)
		guarded.DELETE("/notices/:id", w.deleteNotice)

		guarded.GET("/reports", w.listReports)
		guarded.POST("/reports/:id/resolve", w.resolveReport)

		guarded.GET("/search", w.search)
		guarded.GET("/play/online", w.onlineStats)
		guarded.POST("/play/omikuji", w.drawOmikuji)
		guarded.GET("/play/omikuji/today", w.omikujiToday)
		guarded.GET("/play/omikuji/history", w.omikujiHistory)
		guarded.GET("/play/omikuji/leaderboard", w.omikujiLeaderboard)
		guarded.GET("/releases", w.listReleases)
		guarded.GET("/releases/check", w.checkUpdate)
		guarded.POST("/releases", w.createRelease)
		guarded.POST("/upload", w.upload)

		guarded.GET("/stickers", w.myStickers)
		guarded.POST("/stickers", w.addSticker)
		guarded.POST("/stickers/reorder", w.reorderStickers)
		guarded.DELETE("/stickers/:id", w.deleteSticker)
		guarded.GET("/users/:id/stickers", w.userStickers)
	}
	return r
}

func serveUploads(w *Web) gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("X-Content-Type-Options", "nosniff")

		if strings.HasSuffix(strings.ToLower(c.Request.URL.Path), ".apk") {
			c.Header("Cache-Control", "public, max-age=86400")
		} else {
			c.Header("Cache-Control", "public, max-age=31536000, immutable")
		}

		if width := thumbWidthOf(c.Query("w")); width > 0 {
			if w.serveThumb(c, width) {
				return
			}
		}

		if strings.HasSuffix(strings.ToLower(c.Request.URL.Path), ".apk") {
			c.Header("Content-Type", "application/vnd.android.package-archive")
		}

		http.StripPrefix("/uploads", http.FileServer(noDirListFS{http.Dir(w.Cfg.Upload.Dir)})).ServeHTTP(c.Writer, c.Request)
	}
}

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
