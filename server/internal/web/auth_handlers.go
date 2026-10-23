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
	Purpose string `json:"purpose"`
}

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

		svc.DummyBcrypt()
		w.loginFailRecord(c, email)
		w.err(c, http.StatusUnauthorized, "邮箱或密码不正确")
		return
	}
	if u.Banned {
		w.err(c, http.StatusForbidden, "账号已被封禁")
		return
	}

	if u.DeactivatedAt != nil {
		_ = w.St.CancelDeactivation(c.Request.Context(), u.ID)
		u.DeactivatedAt = nil
	}

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

	userID, err := w.St.RotateRefreshToken(c.Request.Context(), req.RefreshToken, refresh,
		deviceNameFromHeader(c), int64(w.Cfg.JWT.RefreshTTL.Seconds()))
	if err != nil {

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

func deviceNameFromHeader(c *gin.Context) string {
	d := strings.TrimSpace(c.GetHeader("X-Device-Name"))
	if r := []rune(d); len(r) > 60 {
		d = string(r[:60])
	}
	return d
}

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

func (w *Web) getMe(c *gin.Context) {
	v := w.viewer(c)
	me := v.User.ToMe()
	_ = w.St.FillUserCounts(c.Request.Context(), &me.User, v.ID)
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

	checkURL := func(p *string, what string) bool {
		if p == nil {
			return true
		}
		u := strings.TrimSpace(*p)

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

	_ = w.St.RevokeRefreshTokens(c.Request.Context(), v.ID)
	w.issueTokens(c, v.User)
}

type emailChangeReq struct {
	NewEmail string `json:"new_email"`
	Code     string `json:"code"`
}

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

	if !w.Cfg.DevMode && !w.Mailer.Configured() {
		w.err(c, http.StatusServiceUnavailable, "邮件服务暂不可用，请稍后再试")
		return
	}

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

	if err := w.St.Heartbeat(c.Request.Context(), v.ID, req.ActiveSeconds); err != nil {
		w.err(c, 500, "上报失败")
		return
	}
	st, _ := w.St.OnlineStats(c.Request.Context())
	c.JSON(http.StatusOK, gin.H{"ok": true, "online_now": st.OnlineNow, "server_time": time.Now().Unix()})
}
