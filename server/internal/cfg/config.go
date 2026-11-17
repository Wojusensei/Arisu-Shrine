package cfg

import (
	"os"
	"strings"
	"time"

	"github.com/spf13/viper"
)

type Config struct {
	Addr    string `mapstructure:"addr"`
	BaseURL string `mapstructure:"base_url"` // 对外可访问地址，用于拼图片/链接
	DevMode bool   `mapstructure:"dev_mode"` // 开发模式：验证码回显给接口、gin 调试日志；生产必须 false
	// 反代部署填可信网段（如 ["10.0.0.0/8"]）；留空=不信任任何代理头，
	// ClientIP 即 TCP 对端，登录锁/限频的 IP 维度才不会被伪造头绕过
	TrustedProxies []string `mapstructure:"trusted_proxies"`
	// 上游会重写来源地址时填头名（Cloudflare 部署填 CF-Connecting-IP），
	// 限频/防爆破/洪泛闸门按它计数；留空则按上面的可信网段规则判定。
	// 采信的前提是源站只有反代可达，直连没封的部署填了等于交出计数维度
	ClientIPHeader string `mapstructure:"client_ip_header"`
	DB             struct {
		DSN string `mapstructure:"dsn"`
	} `mapstructure:"db"`
	Redis struct {
		Addr     string `mapstructure:"addr"`
		Password string `mapstructure:"password"`
		DB       int    `mapstructure:"db"`
	} `mapstructure:"redis"`
	JWT struct {
		Secret     string        `mapstructure:"secret"`
		AccessTTL  time.Duration `mapstructure:"access_ttl"`
		RefreshTTL time.Duration `mapstructure:"refresh_ttl"`
	} `mapstructure:"jwt"`
	SMTP struct {
		Host     string `mapstructure:"host"`
		Port     int    `mapstructure:"port"`
		Username string `mapstructure:"username"`
		Password string `mapstructure:"password"`
		From     string `mapstructure:"from"`
	} `mapstructure:"smtp"`
	Upload struct {
		Dir      string `mapstructure:"dir"`
		MaxBytes int64  `mapstructure:"max_bytes"`
	} `mapstructure:"upload"`
	Limits struct {
		CodePerEmailPerDay int           `mapstructure:"code_per_email_per_day"`
		CodePerIPPerDay    int           `mapstructure:"code_per_ip_per_day"`
		PostInterval       time.Duration `mapstructure:"post_interval"`
		CommentInterval    time.Duration `mapstructure:"comment_interval"`
		NewbiePostInterval time.Duration `mapstructure:"newbie_post_interval"`
		StaffApplyCooldown time.Duration `mapstructure:"staff_apply_cooldown"`
		DeactivateCooldown time.Duration `mapstructure:"deactivate_cooldown"`
		HotAuthorUpThresh  int           `mapstructure:"hot_author_up_threshold"`
		// 单个来源每分钟的请求总量上限（洪泛闸门）；0 或负数=关闭该闸门
		FloodPerMin int `mapstructure:"flood_per_min"`
	} `mapstructure:"limits"`
}

func Load(path string) (*Config, error) {
	v := viper.New()
	v.SetConfigFile(path)
	v.SetConfigType("yaml")
	// 支持环境变量覆盖：ARISU_SMTP_PASSWORD 等
	v.SetEnvPrefix("ARISU")
	v.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))
	v.AutomaticEnv()
	// jwt.secret 必须显式绑定：viper 的 AutomaticEnv 对 Unmarshal 不可靠，
	// 不绑定的话 ARISU_JWT_SECRET 会静默不生效，服务带着仓库里的旧密钥就起服了
	if err := v.BindEnv("jwt.secret"); err != nil {
		return nil, err
	}
	// 同一个坑：client_ip_header 也要显式绑定，否则 .env 里配了也不生效，
	// 现象是「闸门装上了但计数维度还是反代地址」，最难查的那种静默失效
	if err := v.BindEnv("client_ip_header"); err != nil {
		return nil, err
	}
	setDefaults(v)

	if err := v.ReadInConfig(); err != nil {
		return nil, err
	}
	var c Config
	if err := v.Unmarshal(&c); err != nil {
		return nil, err
	}
	if c.Addr == "" {
		c.Addr = ":8080"
	}
	return &c, nil
}

func setDefaults(v *viper.Viper) {
	v.SetDefault("addr", ":8080")
	v.SetDefault("base_url", "http://127.0.0.1:8080")
	v.SetDefault("db.dsn", "postgres://arisu:arisu@127.0.0.1:5432/arisu?sslmode=disable")
	v.SetDefault("redis.addr", "127.0.0.1:6379")
	v.SetDefault("redis.db", 0)
	v.SetDefault("jwt.access_ttl", 24*time.Hour)
	v.SetDefault("jwt.refresh_ttl", 30*24*time.Hour)
	v.SetDefault("upload.dir", "uploads")
	v.SetDefault("upload.max_bytes", 10<<20)
	v.SetDefault("limits.code_per_email_per_day", 10)
	v.SetDefault("limits.code_per_ip_per_day", 30)
	v.SetDefault("limits.post_interval", 30*time.Second)
	v.SetDefault("limits.comment_interval", 8*time.Second)
	v.SetDefault("limits.newbie_post_interval", 120*time.Second)
	v.SetDefault("limits.staff_apply_cooldown", 7*24*time.Hour)
	v.SetDefault("limits.deactivate_cooldown", 24*time.Hour)
	v.SetDefault("limits.hot_author_up_threshold", 20)
	// 300 是「只有脚本才撞得到」的量级：一个人一分钟点不出几十个请求
	v.SetDefault("limits.flood_per_min", 300)
	v.SetDefault("client_ip_header", "")
}

// SMTPConfigured 未配置 SMTP 时验证码回落打印日志（开发模式）
func (c *Config) SMTPConfigured() bool {
	return os.Getenv("ARISU_SMTP_HOST") != "" ||
		(c.SMTP.Host != "" && c.SMTP.From != "")
}
