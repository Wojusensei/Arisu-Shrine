package web

import (
	"errors"
	"net/http"
	"strings"
	"time"

	"arisu/server/internal/model"
	"arisu/server/internal/store"
	"arisu/server/internal/svc"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

type authCodeReq struct {
	Email   string `json:"email"`
	Purpose string `json:"purpose"` // register | reset
}

// sendCode 发送邮箱验证码。收件邮箱不做任何域名限制（Outlook/Hotmail/QQ/163/Gmail 等均可）。
func (w *Web) sendCode(c *gin.Context) {
	var req authCodeReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	email := svc.NormalizeEmail(req.Email)
	purpose := req.Purpose
	if purpose != "register" && purpose != "reset" {
		purpose = "register"
	}
	// 生产模式 SMTP 未配置时直接拒绝：验证码只进日志、用户永远收不到，
	// 接口却返回成功，问题无从排查。开发模式继续走 dev_code 通路
	if !w.Cfg.DevMode && !w.Mailer.Configured() {
		w.err(c, http.StatusServiceUnavailable, "邮件服务暂不可用，请稍后再试")
		return
	}
	code, err := svc.SendVerificationCode(c.Request.Context(), w.RDB, w.Mailer, w.Log,
		purpose, email, w.clientIP(c), w.Cfg.Limits.CodePerEmailPerDay, w.Cfg.Limits.CodePerIPPerDay)
	if err != nil {
		status := http.StatusBadRequest
		if errors.Is(err, svc.ErrCooldown) || errors.Is(err, svc.ErrTooMany) {
			status = http.StatusTooManyRequests
		}
		w.err(c, status, err.Error())
		return
	}
	resp := gin.H{"ok": true}
	// 开发模式（dev_mode: true）时把验证码带回，方便本机联调；生产必须关闭
	if w.Cfg.DevMode {
		resp["dev_code"] = code
	}
	c.JSON(http.StatusOK, resp)
}

type registerReq struct {
	Email    string `json:"email"`
	Code     string `json:"code"`
	Password string `json:"password"`
	Nickname string `json:"nickname"`
}

func (w *Web) register(c *gin.Context) {
	var req registerReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	email := svc.NormalizeEmail(req.Email)
	nickname := strings.TrimSpace(req.Nickname)
	switch {
	case !svc.ValidateEmail(email):
		w.bad(c, "邮箱格式不正确")
		return
	case len(req.Password) < 8:
		w.bad(c, "密码至少 8 位")
		return
	case len(req.Password) > 72:
		w.bad(c, "密码最多 72 字符")
		return
	case nickname == "" || len([]rune(nickname)) > 20:
		w.bad(c, "昵称需为 1~20 个字符")
		return
	case nicknameReserved(nickname):
		w.bad(c, "该昵称已被使用，请换一个")
		return
	}
	if err := svc.VerifyCode(c.Request.Context(), w.RDB, "register", email, strings.TrimSpace(req.Code)); err != nil {
		w.bad(c, err.Error())
		return
	}
	hash, err := svc.HashPassword(req.Password)
	if err != nil {
		w.err(c, 500, "内部错误")
		return
	}
	u, err := w.St.CreateUser(c.Request.Context(), email, hash, nickname)
	if err != nil {
		if errors.Is(err, store.ErrNicknameTaken) {
			w.bad(c, "该昵称已被使用，请换一个")
			return
		}
		if errors.Is(err, store.ErrConflict) {
			w.bad(c, "该邮箱已注册，请直接登录")
			return
		}
		w.err(c, 500, "注册失败，请稍后重试")
		return
	}
	w.issueTokens(c, u)
}

type loginReq struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

func (w *Web) login(c *gin.Context) {
	var req loginReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	email := svc.NormalizeEmail(req.Email)
	if w.loginLocked(c, email) {
		return
	}
	u, err := w.St.UserByEmail(c.Request.Context(), email)
	if errors.Is(err, store.ErrNotFound) {
		// 对不存在账号做一次等价 bcrypt 比较，拉平响应时间，防账号枚举
		svc.DummyBcrypt()
		w.loginFailRecord(c, email)
		w.err(c, http.StatusUnauthorized, "邮箱或密码不正确")
		return
	}
	if err == nil && u.PasswordHash != "" && !svc.CheckPassword(u.PasswordHash, req.Password) {
		w.loginFailRecord(c, email)
		w.err(c, http.StatusUnauthorized, "邮箱或密码不正确")
		return
	}
	if err != nil {
		w.err(c, 500, "内部错误")
		return
	}
	if u.PasswordHash == "" {
		// 已注销账号（哈希已清空）：不验密码就回「账号已注销」会泄露账号状态；
		// 计数要跟上——不然连试 9 次后，429 的是不存在账号、持续 401 的是已注销账号，
		// 登录接口就成了可区分账号状态的弱枚举 oracle
		svc.DummyBcrypt()
		w.loginFailRecord(c, email)
		w.err(c, http.StatusUnauthorized, "邮箱或密码不正确")
		return
	}
	if u.Banned {
		w.err(c, http.StatusForbidden, "账号已被封禁")
		return
	}
	// 冷静期内登录 = 取消注销
	if u.DeactivatedAt != nil {
		_ = w.St.CancelDeactivation(c.Request.Context(), u.ID)
		u.DeactivatedAt = nil
	}
	// 成就：参拜者（入社满 100 天）
	if time.Since(u.CreatedAt) >= 100*24*time.Hour {
		w.St.GrantAchievement(c.Request.Context(), u.ID, "veteran")
	}
	w.loginFailClear(email)
	w.issueTokens(c, u)
}

type resetReq struct {
	Email    string `json:"email"`
	Code     string `json:"code"`
	Password string `json:"password"`
}

func (w *Web) resetPassword(c *gin.Context) {
	var req resetReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	email := svc.NormalizeEmail(req.Email)
	if len(req.Password) < 8 {
		w.bad(c, "密码至少 8 位")
		return
	}
	if len(req.Password) > 72 {
		w.bad(c, "密码最多 72 字符")
		return
	}
	if err := svc.VerifyCode(c.Request.Context(), w.RDB, "reset", email, strings.TrimSpace(req.Code)); err != nil {
		w.bad(c, err.Error())
		return
	}
	u, err := w.St.UserByEmail(c.Request.Context(), email)
	if errors.Is(err, store.ErrNotFound) {
		w.bad(c, "该邮箱尚未注册")
		return
	}
	if err != nil {
		w.err(c, 500, "内部错误")
		return
	}
	hash, err := svc.HashPassword(req.Password)
	if err != nil {
		w.err(c, 500, "内部错误")
		return
	}
	if err := w.St.UpdatePassword(c.Request.Context(), u.ID, hash); err != nil {
		w.err(c, 500, "重置失败")
		return
	}
	// 重置成功后踢掉所有旧会话
	_ = w.St.RevokeRefreshTokens(c.Request.Context(), u.ID)
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

type refreshReq struct {
	RefreshToken string `json:"refresh_token"`
}

func (w *Web) refresh(c *gin.Context) {
	var req refreshReq
	if err := c.ShouldBindJSON(&req); err != nil || req.RefreshToken == "" {
		w.bad(c, "参数错误")
		return
	}
	refresh, err := svc.NewRefreshToken()
	if err != nil {
		w.err(c, 500, "签发凭证失败")
		return
	}
	// 原子轮换：删旧插新一条语句完成。旧的「查→删→插」三步无事务，并发双刷
	// 同一 token 的窗口内可克隆出多个并行会话；现在第二个并发请求与重放都拿不到行
	userID, err := w.St.RotateRefreshToken(c.Request.Context(), req.RefreshToken, refresh,
		deviceNameFromHeader(c), int64(w.Cfg.JWT.RefreshTTL.Seconds()))
	if err != nil {
		// 只有「这枚令牌确实不存在」才算会话失效。数据库抖动等一等一过的错误
		// 也回 401 的话，客户端只会照 401 的约定把用户踢回登录页（2026-10-17
		// 事故：任何一次续签失败都等于当场登出），所以这里分开报错并留痕
		if !errors.Is(err, store.ErrNotFound) {
			w.Log.Error("refresh 轮换失败", zap.Error(err))
			w.err(c, http.StatusServiceUnavailable, "服务暂时不可用，请稍后再试")
			return
		}
		w.err(c, http.StatusUnauthorized, "登录已过期，请重新登录")
		return
	}
	u, err := w.St.UserByID(c.Request.Context(), userID)
	if err != nil || u.Banned || u.PasswordHash == "" {
		w.err(c, http.StatusUnauthorized, "登录已过期，请重新登录")
		return
	}
	access, err := svc.MakeAccessToken(w.Cfg.JWT.Secret, u.ID, u.Role, w.Cfg.JWT.AccessTTL)
	if err != nil {
		w.err(c, 500, "签发凭证失败")
		return
	}
	c.JSON(http.StatusOK, model.AuthResult{
		AccessToken:  access,
		RefreshToken: refresh,
		Me:           u.ToMe(),
	})
}

func (w *Web) logout(c *gin.Context) {
	var req refreshReq
	_ = c.ShouldBindJSON(&req)
	if req.RefreshToken != "" {
		_ = w.St.DeleteRefreshToken(c.Request.Context(), req.RefreshToken)
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

// issueTokens 注册/登录/刷新共用的发 token 出口。
// 轮换会删旧行插新行，所以设备名必须每次签发都随 X-Device-Name 头带上，
// 否则一台设备只要续签过一次，设备管理里就变回「未知设备」
func (w *Web) issueTokens(c *gin.Context, u *store.UserRow) {
	access, err := svc.MakeAccessToken(w.Cfg.JWT.Secret, u.ID, u.Role, w.Cfg.JWT.AccessTTL)
	if err != nil {
		w.err(c, 500, "签发凭证失败")
		return
	}
	refresh, err := svc.NewRefreshToken()
	if err != nil {
		w.err(c, 500, "签发凭证失败")
		return
	}
	device := deviceNameFromHeader(c)
	if err := w.St.SaveRefreshToken(c.Request.Context(), u.ID, refresh, int64(w.Cfg.JWT.RefreshTTL.Seconds()), device); err != nil {
		w.err(c, 500, "签发凭证失败")
		return
	}
	c.JSON(http.StatusOK, model.AuthResult{
		AccessToken:  access,
		RefreshToken: refresh,
		Me:           u.ToMe(),
	})
}

// deviceNameFromHeader 取客户端自报设备名，超长截断；空串由客户端兜底显示
func deviceNameFromHeader(c *gin.Context) string {
	d := strings.TrimSpace(c.GetHeader("X-Device-Name"))
	if r := []rune(d); len(r) > 60 {
		d = string(r[:60])
	}
	return d
}

// ---- 会话设备管理：查看/踢出当前登录该账号的设备 ----

type sessionItem struct {
	ID       int64  `json:"id"`
	Device   string `json:"device"`
	LastUsed int64  `json:"last_used"`
	Current  bool   `json:"current"`
}

func (w *Web) listSessions(c *gin.Context) {
	v := w.viewer(c)
	rows, err := w.St.Sessions(c.Request.Context(), v.ID, c.GetHeader("X-Refresh-Token"))
	if err != nil {
		w.err(c, 500, "加载失败，点击重试")
		return
	}
	items := make([]sessionItem, 0, len(rows))
	for _, r := range rows {
		items = append(items, sessionItem{
			ID:       r.ID,
			Device:   r.Device,
			LastUsed: r.CreatedAt.Unix(),
			Current:  r.Current,
		})
	}
	c.JSON(http.StatusOK, gin.H{"items": items})
}

func (w *Web) kickSession(c *gin.Context) {
	v := w.viewer(c)
	id, ok := pathID(c, "id")
	if !ok {
		w.bad(c, "参数错误")
		return
	}
	kicked, err := w.St.DeleteSession(c.Request.Context(), v.ID, id)
	if err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	if !kicked {
		w.notFound(c)
		return
	}
	c.JSON(http.StatusOK, gin.H{"ok": true})
}

// ---- 我的信息 ----

func (w *Web) getMe(c *gin.Context) {
	v := w.viewer(c)
	me := v.User.ToMe()
	_ = w.St.FillUserCounts(c.Request.Context(), &me.User, v.ID)
	// 每日赛钱挂在这：冷启动第一个 /me 顺手把今天的 50 円领了（幂等）
	if b, err := w.St.GrantDailyOffer(c.Request.Context(), v.ID); err == nil {
		me.User.BalanceTenths = b
	}
	c.JSON(http.StatusOK, me)
}

type patchMeReq struct {
	Nickname            *string         `json:"nickname"`
	Signature           *string         `json:"signature"`
	StatusEmoji         *string         `json:"status_emoji"`
	StatusText          *string         `json:"status_text"`
	BlogURL             *string         `json:"blog_url"`
	CustomMarkdown      *string         `json:"custom_markdown"`
	CustomCSS           *string         `json:"custom_css"`
	FixedAvatarURL      *string         `json:"fixed_avatar_url"`
	ActiveAvatarURL     *string         `json:"active_avatar_url"`
	ActiveAvatarEnabled *bool           `json:"active_avatar_enabled"`
	ShowFields          map[string]bool `json:"show_fields"`
	NotifyScope         map[string]bool `json:"notify_scope"`
}

func (w *Web) patchMe(c *gin.Context) {
	v := w.viewer(c)
	var req patchMeReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	// 禁言拦「他人可见」的资料内容：签名/状态/自定义区域/头像/博客都渲染在公开主页上，
	// 此前只拦了帖子区，禁言形同虚设；展示开关与通知范围等隐私设置不禁。
	// active_avatar_enabled 是头像生效开关，禁言前存好的 URL 也能靠它亮出来，所以一并拦
	mutedContent := req.Nickname != nil || req.Signature != nil || req.StatusEmoji != nil ||
		req.StatusText != nil || req.BlogURL != nil || req.CustomMarkdown != nil ||
		req.CustomCSS != nil || req.FixedAvatarURL != nil || req.ActiveAvatarURL != nil ||
		req.ActiveAvatarEnabled != nil
	if mutedContent && w.mutedErr(c, v) {
		return
	}
	if req.Nickname != nil {
		nick := strings.TrimSpace(*req.Nickname)
		if nick == "" || len([]rune(nick)) > 20 {
			w.bad(c, "昵称需为 1~20 个字符")
			return
		}
		if nicknameReserved(nick) {
			w.bad(c, "该昵称已被使用，请换一个")
			return
		}
		req.Nickname = &nick
	}
	if req.Signature != nil && len([]rune(*req.Signature)) > 50 {
		w.bad(c, "个性签名最多 50 字")
		return
	}
	if req.StatusEmoji != nil && len([]rune(*req.StatusEmoji)) > 16 {
		w.bad(c, "状态表情过长")
		return
	}
	if req.StatusText != nil && len([]rune(*req.StatusText)) > 10 {
		w.bad(c, "状态文字最多 10 字")
		return
	}
	// 此前除昵称/签名/状态文字外的字段全部直通：单用户可存数 MB 的 CSS/Markdown
	// 原样下发，blog_url 也不校验 scheme（可存 javascript: 伪协议）
	checkURL := func(p *string, what string) bool {
		if p == nil {
			return true
		}
		u := strings.TrimSpace(*p)
		// 空串=清空该栏；非空才要求 http(s) 与长度
		if u == "" {
			*p = ""
			return true
		}
		if len([]rune(u)) > 300 {
			w.bad(c, what+"最多 300 字符")
			return false
		}
		lower := strings.ToLower(u)
		if !strings.HasPrefix(lower, "http://") && !strings.HasPrefix(lower, "https://") {
			w.bad(c, what+"需以 http(s):// 开头")
			return false
		}
		*p = u
		return true
	}
	if req.BlogURL != nil && !checkURL(req.BlogURL, "博客地址") {
		return
	}
	if req.FixedAvatarURL != nil && !checkURL(req.FixedAvatarURL, "头像地址") {
		return
	}
	if req.ActiveAvatarURL != nil && !checkURL(req.ActiveAvatarURL, "头像地址") {
		return
	}
	if req.CustomMarkdown != nil && len([]rune(*req.CustomMarkdown)) > 20000 {
		w.bad(c, "自定义区域最多 20000 字")
		return
	}
	if req.CustomCSS != nil && len([]rune(*req.CustomCSS)) > 20000 {
		w.bad(c, "自定义样式最多 20000 字符")
		return
	}
	oldNickname := v.User.Nickname
	oldAvatar := v.User.DisplayAvatar()
	u, err := w.St.UpdateProfile(c.Request.Context(), v.ID, store.ProfileUpdate{
		Nickname: req.Nickname, Signature: req.Signature,
		StatusEmoji: req.StatusEmoji, StatusText: req.StatusText,
		BlogURL: req.BlogURL, CustomMarkdown: req.CustomMarkdown, CustomCSS: req.CustomCSS,
		FixedAvatarURL: req.FixedAvatarURL, ActiveAvatarURL: req.ActiveAvatarURL,
		ActiveAvatarEnabled: req.ActiveAvatarEnabled,
		ShowFields:          req.ShowFields, NotifyScope: req.NotifyScope,
	})
	if err != nil {
		if errors.Is(err, store.ErrNicknameTaken) {
			w.bad(c, "该昵称已被使用，请换一个")
			return
		}
		w.err(c, 500, "保存失败")
		return
	}
	// 关注通知：昵称/头像变更
	if req.Nickname != nil && *req.Nickname != oldNickname {
		_ = w.St.NotifyFollowersProfileChange(c.Request.Context(), v.ID, "nickname")
	}
	if req.FixedAvatarURL != nil || req.ActiveAvatarURL != nil || req.ActiveAvatarEnabled != nil {
		if u.DisplayAvatar() != oldAvatar {
			_ = w.St.NotifyFollowersProfileChange(c.Request.Context(), v.ID, "avatar")
		}
	}
	me := u.ToMe()
	_ = w.St.FillUserCounts(c.Request.Context(), &me.User, v.ID)
	c.JSON(http.StatusOK, me)
}

type changePasswordReq struct {
	OldPassword string `json:"old_password"`
	NewPassword string `json:"new_password"`
}

func (w *Web) changePassword(c *gin.Context) {
	v := w.viewer(c)
	var req changePasswordReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	if !svc.CheckPassword(v.User.PasswordHash, req.OldPassword) {
		w.bad(c, "旧密码不正确")
		return
	}
	if len(req.NewPassword) < 8 {
		w.bad(c, "新密码至少 8 位")
		return
	}
	if len(req.NewPassword) > 72 {
		w.bad(c, "新密码最多 72 字符")
		return
	}
	hash, err := svc.HashPassword(req.NewPassword)
	if err != nil {
		w.err(c, 500, "内部错误")
		return
	}
	if err := w.St.UpdatePassword(c.Request.Context(), v.ID, hash); err != nil {
		w.err(c, 500, "修改失败")
		return
	}
	// 其他设备的会话全部吊销；当前设备换发新 token 保持登录
	_ = w.St.RevokeRefreshTokens(c.Request.Context(), v.ID)
	w.issueTokens(c, v.User)
}

type emailChangeReq struct {
	NewEmail string `json:"new_email"`
	Code     string `json:"code"`
}

// emailChangeRequest 向「新邮箱」发送换绑验证码
func (w *Web) emailChangeRequest(c *gin.Context) {
	v := w.viewer(c)
	var req struct {
		NewEmail string `json:"new_email"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	newEmail := svc.NormalizeEmail(req.NewEmail)
	if !svc.ValidateEmail(newEmail) {
		w.bad(c, "邮箱格式不正确")
		return
	}
	if newEmail == v.User.Email {
		w.bad(c, "新邮箱与当前邮箱相同")
		return
	}
	// 生产模式 SMTP 未配置时直接拒绝：验证码只进日志、用户永远收不到，
	// 接口却返回成功，问题无从排查。开发模式继续走 dev_code 通路
	if !w.Cfg.DevMode && !w.Mailer.Configured() {
		w.err(c, http.StatusServiceUnavailable, "邮件服务暂不可用，请稍后再试")
		return
	}
	// 先走验证码配额（60s 冷却 + 每日上限）再做存在性检查：反过来时本端点
	// 没有任何消耗，登录用户可高速枚举任意邮箱是否已注册
	code, err := svc.SendVerificationCode(c.Request.Context(), w.RDB, w.Mailer, w.Log,
		"email_change", newEmail, w.clientIP(c), w.Cfg.Limits.CodePerEmailPerDay, w.Cfg.Limits.CodePerIPPerDay)
	if err != nil {
		status := http.StatusBadRequest
		if errors.Is(err, svc.ErrCooldown) || errors.Is(err, svc.ErrTooMany) {
			status = http.StatusTooManyRequests
		}
		w.err(c, status, err.Error())
		return
	}
	if _, err := w.St.UserByEmail(c.Request.Context(), newEmail); err == nil {
		w.bad(c, "该邮箱已被其他社员使用")
		return
	}
	resp := gin.H{"ok": true}
	if w.Cfg.DevMode {
		resp["dev_code"] = code
	}
	c.JSON(http.StatusOK, resp)
}

func (w *Web) emailChange(c *gin.Context) {
	v := w.viewer(c)
	var req emailChangeReq
	if err := c.ShouldBindJSON(&req); err != nil {
		w.bad(c, "参数错误")
		return
	}
	newEmail := svc.NormalizeEmail(req.NewEmail)
	if !svc.ValidateEmail(newEmail) {
		w.bad(c, "邮箱格式不正确")
		return
	}
	if newEmail == v.User.Email {
		w.bad(c, "新邮箱与当前邮箱相同")
		return
	}
	if err := svc.VerifyCode(c.Request.Context(), w.RDB, "email_change", newEmail, strings.TrimSpace(req.Code)); err != nil {
		w.bad(c, err.Error())
		return
	}
	if err := w.St.UpdateEmail(c.Request.Context(), v.ID, newEmail); err != nil {
		if errors.Is(err, store.ErrConflict) {
			w.bad(c, "该邮箱已被其他社员使用")
			return
		}
		w.err(c, 500, "换绑失败，请稍后重试")
		return
	}
	u, err := w.St.UserByID(c.Request.Context(), v.ID)
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	// 邮箱是找回密码的通道，换绑成功后必须吊销会话（与改密码对齐）。
	// 宁全踢勿漏：带头且属于本人 → 保当前设备踢其余；不带头（旧客户端）
	// 或带头但 token 不属于本人 → 全部吊销，所有设备重新登录
	if token := c.GetHeader("X-Refresh-Token"); token != "" {
		_ = w.St.RevokeRefreshTokensExcept(c.Request.Context(), v.ID, token)
	} else {
		_ = w.St.RevokeRefreshTokens(c.Request.Context(), v.ID)
	}
	me := u.ToMe()
	_ = w.St.FillUserCounts(c.Request.Context(), &me.User, v.ID)
	c.JSON(http.StatusOK, me)
}

func (w *Web) deactivate(c *gin.Context) {
	v := w.viewer(c)
	if err := w.St.Deactivate(c.Request.Context(), v.ID); err != nil {
		w.err(c, 500, "操作失败")
		return
	}
	// 吊销全部 refresh token：注销后的设备不能再用旧令牌续命 30 天
	_ = w.St.RevokeRefreshTokens(c.Request.Context(), v.ID)
	c.JSON(http.StatusOK, gin.H{"ok": true, "cooldown_hours": int(w.Cfg.Limits.DeactivateCooldown.Hours())})
}

func (w *Web) exportPosts(c *gin.Context) {
	v := w.viewer(c)
	md, err := w.St.ExportUserPostsMarkdown(c.Request.Context(), v.ID)
	if err != nil {
		w.err(c, 500, "导出失败")
		return
	}
	c.Header("Content-Disposition", "attachment; filename=arisu_posts_export.md")
	c.Data(http.StatusOK, "text/markdown; charset=utf-8", []byte(md))
}

func (w *Web) myAchievements(c *gin.Context) {
	v := w.viewer(c)
	list, err := w.St.Achievements(c.Request.Context(), v.ID)
	if err != nil {
		w.err(c, 500, "查询失败")
		return
	}
	c.JSON(http.StatusOK, gin.H{"items": list})
}

type heartbeatReq struct {
	ActiveSeconds int `json:"active_seconds"`
}

func (w *Web) heartbeat(c *gin.Context) {
	v := w.viewer(c)
	var req heartbeatReq
	_ = c.ShouldBindJSON(&req)
	// 每次心跳都计时（应用户决定：在线时长不做防刷，有用户是好事）
	if err := w.St.Heartbeat(c.Request.Context(), v.ID, req.ActiveSeconds); err != nil {
		w.err(c, 500, "上报失败")
		return
	}
	// 未读数搭心跳的车：客户端只保留心跳一条轮询，角标随每跳刷新
	unread, _ := w.St.UnreadNoticeCount(c.Request.Context(), v.ID)
	// 余额同车：被奉纳的收入也能在一分钟内反映到界面上
	balanceTenths, _ := w.St.GrantDailyOffer(c.Request.Context(), v.ID)
	st, _ := w.St.OnlineStats(c.Request.Context())
	c.JSON(http.StatusOK, gin.H{"ok": true, "online_now": st.OnlineNow, "server_time": time.Now().Unix(), "unread": unread, "balance_tenths": balanceTenths})
}
