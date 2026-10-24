package web

import (
	"context"
	"errors"
	"net"
	"net/http"
	"strings"
	"time"

	"arisu/server/internal/model"
	"arisu/server/internal/store"
	"arisu/server/internal/svc"
	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

type viewerCtx struct {
	ID   int64
	Role string
	User *store.UserRow
}

func (w *Web) viewer(c *gin.Context) *viewerCtx {
	v, _ := c.Get("viewer")
	if v == nil {
		return nil
	}
	return v.(*viewerCtx)
}

func (w *Web) authRequired() gin.HandlerFunc {
	return func(c *gin.Context) {
		header := c.GetHeader("Authorization")
		token := strings.TrimPrefix(header, "Bearer ")
		if token == "" || header == token {
			w.err(c, http.StatusUnauthorized, "请先登录")
			return
		}
		claims, err := svc.ParseAccessToken(w.Cfg.JWT.Secret, token)
		if err != nil {
			w.err(c, http.StatusUnauthorized, "登录已过期，请重新登录")
			return
		}
		u, err := w.St.UserByID(c.Request.Context(), claims.UserID)
		if err != nil {
			w.err(c, http.StatusUnauthorized, "账号不存在")
			return
		}
		if u.Banned {
			w.err(c, http.StatusForbidden, "账号已被封禁")
			return
		}

		if u.PasswordHash == "" {
			w.err(c, http.StatusUnauthorized, "账号已注销")
			return
		}

		if u.DeactivatedAt != nil {
			w.err(c, http.StatusForbidden, "账号处于注销冷静期，登录即可恢复")
			return
		}
		c.Set("viewer", &viewerCtx{ID: u.ID, Role: u.Role, User: u})
		c.Next()
	}
}

func (w *Web) mutedErr(c *gin.Context, v *viewerCtx) bool {
	mu := v.User.MutedUntilActive()
	if mu == nil {
		return false
	}
	if mu.Year() > 9000 {
		w.err(c, http.StatusForbidden, "您已被永久禁言，如有疑问请联系神主")
	} else {
		minutes := int(time.Until(*mu).Minutes())
		if minutes < 1 {
			minutes = 1
		}
		w.err(c, http.StatusForbidden, "您已被禁言，剩余时间："+formatRemaining(minutes))
	}
	return true
}

func formatRemaining(minutes int) string {
	days := minutes / 1440
	hours := (minutes % 1440) / 60
	mins := minutes % 60
	switch {
	case days > 0:
		return itoa(days) + "天" + itoa(hours) + "小时"
	case hours > 0:
		return itoa(hours) + "小时" + itoa(mins) + "分钟"
	default:
		return itoa(mins) + "分钟"
	}
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	neg := n < 0
	if neg {
		n = -n
	}
	var buf [20]byte
	i := len(buf)
	for n > 0 {
		i--
		buf[i] = byte('0' + n%10)
		n /= 10
	}
	if neg {
		i--
		buf[i] = '-'
	}
	return string(buf[i:])
}

func (w *Web) requireAdmin(c *gin.Context) bool {
	v := w.viewer(c)
	if v == nil || !model.IsAdmin(v.Role) {
		w.err(c, http.StatusForbidden, "需要奉仕及以上身份")
		return false
	}
	return true
}

func (w *Web) requireGuuji(c *gin.Context) bool {
	v := w.viewer(c)
	if v == nil || !model.IsGuuji(v.Role) {
		w.err(c, http.StatusForbidden, "需要宫司及以上身份")
		return false
	}
	return true
}

func (w *Web) postingRateLimit(c *gin.Context, v *viewerCtx, isNewbieInterval bool) bool {
	interval := w.Cfg.Limits.PostInterval
	if isNewbieInterval && time.Since(v.User.CreatedAt) < 24*time.Hour {
		interval = w.Cfg.Limits.NewbiePostInterval
	}
	return w.intervalRateLimit(c, v, interval)
}

func (w *Web) intervalRateLimit(c *gin.Context, v *viewerCtx, interval time.Duration) bool {
	return w.intervalRateLimitMsg(c, v, interval, "发布太频繁，休息一下再试")
}

func (w *Web) intervalRateLimitMsg(c *gin.Context, v *viewerCtx, interval time.Duration, msg string) bool {
	key := "rate:" + c.FullPath() + ":" + itoa(int(v.ID))
	ok, err := w.RDB.SetNX(c.Request.Context(), key, 1, interval).Result()
	if err != nil {

		println("rate-limit redis unavailable, allowing:", err.Error())
		return true
	}
	if !ok {
		w.err(c, http.StatusTooManyRequests, msg)
		return false
	}
	return true
}

const reportDailyCap = 10

func (w *Web) reportGuard(c *gin.Context, v *viewerCtx, targetExpr string, targetID int64) bool {
	var n int64
	if err := w.St.Pool.QueryRow(c.Request.Context(),
		`SELECT count(*) FROM reports WHERE reporter_id=$1 AND created_at > now() - interval '24 hours'`, v.ID).Scan(&n); err == nil && n >= reportDailyCap {
		w.err(c, http.StatusTooManyRequests, "今日举报次数已达上限（10 条）")
		return false
	}
	var dup bool
	if err := w.St.Pool.QueryRow(c.Request.Context(),
		`SELECT EXISTS(SELECT 1 FROM reports WHERE reporter_id=$1 AND created_at > now() - interval '24 hours' AND `+targetExpr+`=$2)`,
		v.ID, targetID).Scan(&dup); err == nil && dup {
		w.bad(c, "该内容今天已举报过，请耐心等待处理结果")
		return false
	}
	return true
}

func (w *Web) requireOver(c *gin.Context, authorRole string) bool {
	v := w.viewer(c)
	if v == nil || model.RoleRank(v.Role) <= model.RoleRank(authorRole) {
		w.err(c, http.StatusForbidden, "级别不够，无法删除该内容")
		return false
	}
	return true
}

func (w *Web) clientIP(c *gin.Context) string {
	if h := w.Cfg.ClientIPHeader; h != "" {
		if v := strings.TrimSpace(c.GetHeader(h)); net.ParseIP(v) != nil {
			return v
		}
	}
	return c.ClientIP()
}

func (w *Web) floodGuard() gin.HandlerFunc {
	limit := int64(w.Cfg.Limits.FloodPerMin)
	return func(c *gin.Context) {

		if limit <= 0 {
			c.Next()
			return
		}
		key := "rl:flood:" + w.clientIP(c) + ":" + time.Now().Format("200601021504")
		n, err := w.RDB.Incr(c.Request.Context(), key).Result()
		if err != nil {
			w.Log.Warn("洪泛闸门 Redis 写入失败，放行", zap.Error(err))
			c.Next()
			return
		}
		if n == 1 {
			w.RDB.Expire(c.Request.Context(), key, 2*time.Minute)
		}
		if n > limit {
			w.err(c, http.StatusTooManyRequests, "请求过于频繁，请稍后再试")
			c.Abort()
			return
		}
		c.Next()
	}
}

func (w *Web) refreshRateLimit() gin.HandlerFunc {
	return func(c *gin.Context) {
		key := "rl:refresh:" + w.clientIP(c) + ":" + time.Now().Format("200601021504")
		n, err := w.RDB.Incr(c.Request.Context(), key).Result()
		if err != nil {
			w.Log.Warn("refresh 限流 Redis 写入失败，放行", zap.Error(err))
			c.Next()
			return
		}
		if n == 1 {
			w.RDB.Expire(c.Request.Context(), key, 2*time.Minute)
		}
		if n > refreshPerMinLimit {
			w.err(c, http.StatusTooManyRequests, "请求过于频繁，请稍后再试")
			c.Abort()
			return
		}
		c.Next()
	}
}

const refreshPerMinLimit = 60

func (w *Web) uploadsGuard() gin.HandlerFunc {
	return func(c *gin.Context) {
		key := "rl:uploads:" + w.clientIP(c) + ":" + time.Now().Format("200601021504")
		n, err := w.RDB.Incr(c.Request.Context(), key).Result()
		if err != nil {
			w.Log.Warn("uploads 限流 Redis 写入失败，放行", zap.Error(err))
			c.Next()
			return
		}
		if n == 1 {
			w.RDB.Expire(c.Request.Context(), key, 2*time.Minute)
		}
		if n > uploadsPerMinLimit {
			c.AbortWithStatus(http.StatusTooManyRequests)
			return
		}
		c.Next()
	}
}

const uploadsPerMinLimit = 1200

const (
	loginFailAcctMax  = 8
	loginFailIPMax    = 40
	loginFailDelayMax = 30
)

func (w *Web) loginLocked(c *gin.Context, email string) bool {
	day := time.Now().Format("20060102")
	acct, err := w.RDB.Get(c.Request.Context(), "login:fail:acct:"+email+":"+day).Int()
	if err != nil && !errors.Is(err, redis.Nil) {
		w.Log.Warn("登录锁 Redis 读取失败，账号防爆破降级", zap.Error(err))
	}
	ipn, err := w.RDB.Get(c.Request.Context(), "login:fail:ip:"+w.clientIP(c)+":"+day).Int()
	if err != nil && !errors.Is(err, redis.Nil) {
		w.Log.Warn("登录锁 Redis 读取失败，IP 防爆破降级", zap.Error(err))
	}
	if ipn >= loginFailIPMax {
		w.err(c, http.StatusTooManyRequests, "尝试次数过多，请 15 分钟后再试")
		return true
	}
	if acct >= loginFailAcctMax {
		last, _ := w.RDB.Get(c.Request.Context(), "login:fail:last:"+email).Int64()
		wait := (acct - loginFailAcctMax + 1) * 5
		if wait > loginFailDelayMax {
			wait = loginFailDelayMax
		}
		if time.Now().Unix()-last < int64(wait) {
			w.err(c, http.StatusTooManyRequests, "尝试过于频繁，请稍后再试")
			return true
		}
	}
	return false
}

func (w *Web) loginFailRecord(c *gin.Context, email string) {
	day := time.Now().Format("20060102")
	for _, k := range []string{
		"login:fail:acct:" + email + ":" + day,
		"login:fail:ip:" + w.clientIP(c) + ":" + day,
	} {
		if err := w.RDB.Incr(c.Request.Context(), k).Err(); err != nil {
			w.Log.Warn("登录失败计数写入失败", zap.Error(err))
		}
		w.RDB.Expire(c.Request.Context(), k, 15*time.Minute)
	}

	w.RDB.Set(c.Request.Context(), "login:fail:last:"+email, time.Now().Unix(), 15*time.Minute)
}

func (w *Web) loginFailClear(email string) {
	day := time.Now().Format("20060102")
	w.RDB.Del(context.Background(), "login:fail:acct:"+email+":"+day)
}

var errBadRequest = errors.New("bad request")
