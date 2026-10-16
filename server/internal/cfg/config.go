package cfg

import (
	"os"
	"strings"
	"time"

	"github.com/spf13/viper"
)

type Config struct {
	Addr    string `mapstructure:"addr"`
	BaseURL string `mapstructure:"base_url"`
	DevMode bool   `mapstructure:"dev_mode"`

	TrustedProxies []string `mapstructure:"trusted_proxies"`

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

		FloodPerMin int `mapstructure:"flood_per_min"`
	} `mapstructure:"limits"`
}

func Load(path string) (*Config, error) {
	v := viper.New()
	v.SetConfigFile(path)
	v.SetConfigType("yaml")

	v.SetEnvPrefix("ARISU")
	v.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))
	v.AutomaticEnv()

	if err := v.BindEnv("jwt.secret"); err != nil {
		return nil, err
	}

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

	v.SetDefault("limits.flood_per_min", 300)
	v.SetDefault("client_ip_header", "")
}

func (c *Config) SMTPConfigured() bool {
	return os.Getenv("ARISU_SMTP_HOST") != "" ||
		(c.SMTP.Host != "" && c.SMTP.From != "")
}
