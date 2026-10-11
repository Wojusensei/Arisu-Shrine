package svc

import (
	"crypto/tls"
	"fmt"
	"strings"

	"github.com/wneessen/go-mail"
	"go.uber.org/zap"
)

type Mailer struct {
	Host     string
	Port     int
	Username string
	Password string
	From     string
	Log      *zap.Logger
}

func (m *Mailer) Configured() bool {
	return m.Host != "" && m.From != ""
}

func (m *Mailer) SendCode(to, code string) error {
	if !m.Configured() {
		m.Log.Warn("SMTP 未配置，验证码仅打印到日志（开发模式）", zap.String("to", to), zap.String("code", code))
		return nil
	}
	msg := mail.NewMsg()
	if err := msg.From(m.From); err != nil {
		return err
	}
	if err := msg.To(to); err != nil {
		return err
	}
	msg.Subject("有栖神社 · 验证码")
	msg.SetBodyString(mail.TypeTextPlain, fmt.Sprintf(
		"巫女向人间之里递来了信笺。\n\n你的验证码是：%s\n\n十分钟内有效。若非本人操作，请忽略这封信。\n\n—— 有栖神社", code))

	c, err := mail.NewClient(m.Host, mail.WithPort(m.Port), mail.WithSMTPAuth(mail.SMTPAuthPlain),
		mail.WithUsername(m.Username), mail.WithPassword(m.Password),
		mail.WithTLSPolicy(mail.TLSMandatory))
	if err != nil {
		return err
	}

	if m.Port == 465 {
		c.SetTLSPolicy(mail.TLSMandatory)
		c.SetSSLPort(true, false)
	} else {

		c.SetTLSPolicy(mail.TLSMandatory)
		c.SetTLSConfig(&tls.Config{ServerName: m.Host, MinVersion: tls.VersionTLS12})
	}
	return c.DialAndSend(msg)
}

func NormalizeEmail(s string) string {
	return strings.ToLower(strings.TrimSpace(s))
}
