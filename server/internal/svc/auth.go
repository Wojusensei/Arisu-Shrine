package svc

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"math/big"
	"regexp"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
	"golang.org/x/crypto/bcrypt"
)

// ---- 密码 ----

// bcryptCost 12：默认 10 在当前硬件下偏低，注册/登录/重置共用这一档；
// 校验侧 bcrypt 自动读 hash 内的 cost，新旧 hash 混存无需迁移
const bcryptCost = 12

func HashPassword(plain string) (string, error) {
	b, err := bcrypt.GenerateFromPassword([]byte(plain), bcryptCost)
	return string(b), err
}

func CheckPassword(hash, plain string) bool {
	return bcrypt.CompareHashAndPassword([]byte(hash), []byte(plain)) == nil
}

// DummyBcrypt 对不存在账号也做一次等价 bcrypt 比较，拉平登录响应时间，防账号枚举。
// cost 必须与真实签发一致，否则不存在账号的响应时间可观测地更短
var dummyHash, _ = bcrypt.GenerateFromPassword([]byte("arisu-dummy-account"), bcryptCost)

func DummyBcrypt() {
	_ = bcrypt.CompareHashAndPassword(dummyHash, []byte("arisu-not-the-password"))
}

// ---- JWT access token ----

type Claims struct {
	UserID int64  `json:"uid"`
	Role   string `json:"role"`
	jwt.RegisteredClaims
}

func MakeAccessToken(secret string, userID int64, role string, ttl time.Duration) (string, error) {
	claims := Claims{
		UserID: userID, Role: role,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(ttl)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			Issuer:    "arisu-shrine",
		},
	}
	return jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString([]byte(secret))
}

func ParseAccessToken(secret, token string) (*Claims, error) {
	t, err := jwt.ParseWithClaims(token, &Claims{}, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, errors.New("bad signing method")
		}
		return []byte(secret), nil
	})
	if err != nil {
		return nil, err
	}
	claims, ok := t.Claims.(*Claims)
	if !ok || !t.Valid {
		return nil, errors.New("invalid token")
	}
	return claims, nil
}

func NewRefreshToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

// ---- 邮箱验证码（Redis） ----

var emailRe = regexp.MustCompile(`^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$`)

// ValidateEmail 通用邮箱校验：不设域名白名单，Outlook/Hotmail/QQ/163/126/Gmail/edu 等均可
func ValidateEmail(s string) bool {
	return len(s) <= 254 && emailRe.MatchString(s)
}

const codeTTL = 10 * time.Minute

func codeKey(purpose, email string) string { return "vcode:" + purpose + ":" + email }
func coolKey(email string) string          { return "vcode:cool:" + email }
func dayKey(email, day string) string      { return "vcode:day:" + email + ":" + day }
func ipDayKey(ip, day string) string       { return "vcode:ip:" + ip + ":" + day }
func tryKey(purpose, email string) string  { return "vcode:try:" + purpose + ":" + email }

var ErrCooldown = errors.New("请求太频繁，请 60 秒后再试")
var ErrTooMany = errors.New("今日验证码请求次数已达上限")
var ErrBadCode = errors.New("验证码不正确或已过期")

// SendVerificationCode 生成并发送验证码；devMode 且 SMTP 未配置时把验证码带回给调用方（打印日志/接口返回）
func SendVerificationCode(ctx context.Context, rdb redis.UniversalClient, mailer *Mailer,
	log *zap.Logger, purpose, email, ip string, perDay, perIPDay int) (code string, err error) {

	if !ValidateEmail(email) {
		return "", errors.New("邮箱格式不正确")
	}
	day := time.Now().Format("20060102")
	// 60s 冷却
	if n, _ := rdb.Exists(ctx, coolKey(email)).Result(); n > 0 {
		return "", ErrCooldown
	}
	// 每邮箱每日上限
	if n, _ := rdb.Get(ctx, dayKey(email, day)).Result(); toInt(n) >= int64(perDay) {
		return "", ErrTooMany
	}
	// 每 IP 每日上限
	if n, _ := rdb.Get(ctx, ipDayKey(ip, day)).Result(); toInt(n) >= int64(perIPDay) {
		return "", ErrTooMany
	}

	code = genCode()
	if err := rdb.Set(ctx, codeKey(purpose, email), code, codeTTL).Err(); err != nil {
		// Redis 故障时只记日志：原样返回会把连接串等内部信息经 err.Error() 下发客户端
		log.Error("验证码写入失败", zap.String("to", email), zap.Error(err))
		return "", errors.New("发送失败，请稍后重试")
	}
	rdb.Set(ctx, coolKey(email), 1, 60*time.Second)
	rdb.Incr(ctx, dayKey(email, day))
	rdb.ExpireAt(ctx, dayKey(email, day), time.Now().Add(24*time.Hour))
	rdb.Incr(ctx, ipDayKey(ip, day))
	rdb.ExpireAt(ctx, ipDayKey(ip, day), time.Now().Add(24*time.Hour))

	if err := mailer.SendCode(email, code); err != nil {
		log.Error("验证码邮件发送失败", zap.String("to", email), zap.Error(err))
		return "", errors.New("邮件发送失败，请稍后重试")
	}
	if !mailer.Configured() {
		log.Info("验证码（开发模式）", zap.String("to", email), zap.String("code", code), zap.String("purpose", purpose))
	}
	return code, nil
}

// VerifyCode 校验并消费验证码；失败次数超 5 次作废
func VerifyCode(ctx context.Context, rdb redis.UniversalClient, purpose, email, code string) error {
	key := codeKey(purpose, email)
	stored, err := rdb.Get(ctx, key).Result()
	if errors.Is(err, redis.Nil) {
		return ErrBadCode
	}
	if err != nil {
		return ErrBadCode
	}
	if stored != code {
		n, _ := rdb.Incr(ctx, tryKey(purpose, email)).Result()
		rdb.ExpireAt(ctx, tryKey(purpose, email), time.Now().Add(codeTTL))
		if n >= 5 {
			rdb.Del(ctx, key)
		}
		return ErrBadCode
	}
	// 验证通过后作废：Del 失败不把 Redis 错误原文带给客户端（码已对，当成功处理）
	if err := rdb.Del(ctx, key).Err(); err != nil {
		return nil
	}
	return nil
}

func genCode() string {
	n, _ := rand.Int(rand.Reader, big.NewInt(1000000))
	return fmt.Sprintf("%06d", n.Int64())
}

func toInt(s string) int64 {
	var n int64
	fmt.Sscanf(s, "%d", &n)
	return n
}
