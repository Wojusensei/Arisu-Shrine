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
		// 安卓走 Authorization 头；网页端（SSR + fetch 动作）走 httpOnly cookie。
		// cookie 分支只服务同源页面，跨站一律拒绝（SameSite=Lax 之外的第二道闸）。
		header := c.GetHeader("Authorization")
		token := strings.TrimPrefix(header, "Bearer ")
		var u *store.UserRow
		if token != "" && header != token {
			claims, err := svc.ParseAccessToken(w.Cfg.JWT.Secret, token)
			if err != nil {
				w.err(c, http.StatusUnauthorized, "登录已过期，请重新登录")
				return
			}
			u, err = w.St.UserByID(c.Request.Context(), claims.UserID)
			if err != nil {
				w.err(c, http.StatusUnauthorized, "账号不存在")
				return
			}
			if u.Banned {
				w.err(c, http.StatusForbidden, "账号已被封禁")
				return
			}
			// 匿名化账号（冷静期结束已注销）密码已被清空，拒绝一切会话
			if u.PasswordHash == "" {
				w.err(c, http.StatusUnauthorized, "账号已注销")
				return
			}
			// 冷静期内：拒绝一切 API 请求（此前任意一个心跳都会把注销悄悄取消，
			// 注销功能形同虚设）；反悔请走登录，登录即撤销注销
			if u.DeactivatedAt != nil {
				w.err(c, http.StatusForbidden, "账号处于注销冷静期，登录即可恢复")
				return
			}
		} else {
			if !w.webSameOrigin(c) {
				w.err(c, http.StatusForbidden, "跨站请求被拒绝")
				return
			}
			u = w.webResolveViewer(c)
			if u == nil {
				w.err(c, http.StatusUnauthorized, "请先登录")
				return
			}
		}
		c.Set("viewer", &viewerCtx{ID: u.ID, Role: u.Role, User: u})
		c.Next()
	}
}

// mutedErr 非空表示被禁言
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

// requireAdmin 奉仕及以上（删帖/处理举报/警告）
func (w *Web) requireAdmin(c *gin.Context) bool {
	v := w.viewer(c)
	if v == nil || !model.IsAdmin(v.Role) {
		w.err(c, http.StatusForbidden, "需要奉仕及以上身份")
		return false
	}
	return true
}

// requireGuuji 宫司及以上（置顶精华/禁言/封禁/发日志/审批）
func (w *Web) requireGuuji(c *gin.Context) bool {
	v := w.viewer(c)
	if v == nil || !model.IsGuuji(v.Role) {
		w.err(c, http.StatusForbidden, "需要宫司及以上身份")
		return false
	}
	return true
}

// postingRateLimit 发帖频次限制（新社员更慢，Discourse 式新人冷却）
func (w *Web) postingRateLimit(c *gin.Context, v *viewerCtx, isNewbieInterval bool) bool {
	interval := w.Cfg.Limits.PostInterval
	if isNewbieInterval && time.Since(v.User.CreatedAt) < 24*time.Hour {
		interval = w.Cfg.Limits.NewbiePostInterval
	}
	return w.intervalRateLimit(c, v, interval)
}

// intervalRateLimit 评论/绘马回复等使用独立间隔
func (w *Web) intervalRateLimit(c *gin.Context, v *viewerCtx, interval time.Duration) bool {
	return w.intervalRateLimitMsg(c, v, interval, "发布太频繁，休息一下再试")
}

// intervalRateLimitMsg 带自定义提示的间隔限频（奉纳这类非发布动作用）
func (w *Web) intervalRateLimitMsg(c *gin.Context, v *viewerCtx, interval time.Duration, msg string) bool {
	key := "rate:" + c.FullPath() + ":" + itoa(int(v.ID))
	ok, err := w.RDB.SetNX(c.Request.Context(), key, 1, interval).Result()
	if err != nil {
		// Redis 故障放行：限频是防滥用闸门不是安全边界，
		// 别把缓存抖动放大成全站禁写（2026-10-02 用户裁决）
		println("rate-limit redis unavailable, allowing:", err.Error())
		return true
	}
	if !ok {
		w.err(c, http.StatusTooManyRequests, msg)
		return false
	}
	return true
}

// reportDailyCap 单人 24 小时内的举报受理上限
const reportDailyCap = 10

// reportGuard 举报防轰炸：单人日上限 + 同一目标 24 小时只受理一次。
// targetExpr 只允许调用方传入白名单列名（post_id/comment_id/ema_id/reported_user_id）。
// 查询失败一律放行（与 Redis 限频同一哲学：防滥用闸门不是安全边界，DB 抖动不放大成功能不可用）；
// 并发窗口内理论上可双插/超限一条，量级由人工节奏决定，不值得为它上唯一索引
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

// requireOver 管理删除的层级规则：下级不可动上级的言论（作者本人删除不受影响）
func (w *Web) requireOver(c *gin.Context, authorRole string) bool {
	v := w.viewer(c)
	if v == nil || model.RoleRank(v.Role) <= model.RoleRank(authorRole) {
		w.err(c, http.StatusForbidden, "级别不够，无法删除该内容")
		return false
	}
	return true
}

// ---- 来源地址：反代部署下按配置头取，其余一律不信 ----

// clientIP 取来访地址。配了 client_ip_header 就按该头计数（Cloudflare 部署填
// CF-Connecting-IP：客户端自带的同值会被边缘覆盖掉，且源站防火墙只放行回源段，
// 伪造无从下手）；头缺失或不是合法 IP 时落回 gin 的判定——可信网段为空即 TCP 对端
func (w *Web) clientIP(c *gin.Context) string {
	if h := w.Cfg.ClientIPHeader; h != "" {
		if v := strings.TrimSpace(c.GetHeader(h)); net.ParseIP(v) != nil {
			return v
		}
	}
	return c.ClientIP()
}

// ---- 洪泛闸门：单个来源每分钟的请求总量封顶 ----

// floodGuard 拦的是「把接口当刷子用」：正常使用撞不到上限（App 冷启动连拉十余个
// 接口、心跳 60 秒一发，图片走 /uploads 不经此处），脚本洪泛则一头撞上。
// 与 intervalRateLimit 同一哲学：Redis 故障放行，不把缓存抖动放大成全站禁访
func (w *Web) floodGuard() gin.HandlerFunc {
	limit := int64(w.Cfg.Limits.FloodPerMin)
	return func(c *gin.Context) {
		// 0/负值=关闭：配置缺项时宁可少一道闸，也不能误伤全站
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

// ---- refresh 限流：refresh 无需登录态，是令牌爆破/喷洒的直接入口 ----

// refreshRateLimit 每 IP 每分钟 60 次：正常续签频率远低于此（access 24h 一签），
// 高频只可能来自脚本。桶按分钟分片，Redis 故障放行（防滥用闸门不是安全边界，
// 不把缓存抖动放大成全员掉线），但必须留告警
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

// uploadsGuard 图片直出路径的独立闸门：一屏帖子+头像就是几十个请求，
// 预算得远高于 API 的洪泛闸；Redis 故障放行，与 floodGuard 同一哲学
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

// ---- 登录防爆破：按账号与 IP 计失败数，超阈值临时锁 15 分钟 ----

const (
	loginFailAcctMax  = 8  // 账号维度：达到后进入渐进延迟，不再产生第三方可触发的硬锁
	loginFailIPMax    = 40 // IP 维度：保留硬锁——锁的是攻击者自己的出口
	loginFailDelayMax = 30 // 账号渐进延迟上限（秒）
)

// loginLocked 登录节流。账号维度原先是 8 次失败硬 429：第三方拿任意邮箱
// 每 15 分钟打 8 次错误密码，就能把受害者永久锁在门外（零成本账号 DoS，
// 实测 1 请求/113 秒即可全天锁死）。现改为渐进延迟：失败越多、距上次失败
// 需等待越久（5s 起步、封顶 30s）——拖慢爆破者的同时，正常用户最多多等半分钟。
// IP 维度保留硬锁。Redis 故障时计数读作 0（fail-open）：登录不阻断，但必须
// 留告警日志——这道闸同时守护验证码与限频，静默失效会让爆破防护无声消失
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
	// 渐进延迟的基准：距上次失败的时间（与计数键同 15 分钟窗口）
	w.RDB.Set(c.Request.Context(), "login:fail:last:"+email, time.Now().Unix(), 15*time.Minute)
}

func (w *Web) loginFailClear(email string) {
	day := time.Now().Format("20060102")
	w.RDB.Del(context.Background(), "login:fail:acct:"+email+":"+day)
}

var errBadRequest = errors.New("bad request")
