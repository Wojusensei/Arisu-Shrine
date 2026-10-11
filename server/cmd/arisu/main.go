package main

import (
	"context"
	"errors"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"

	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"

	"arisu/server/internal/cfg"
	"arisu/server/internal/store"
	"arisu/server/internal/svc"
	"arisu/server/internal/web"
)

func main() {
	log, _ := zap.NewProduction()
	defer log.Sync()

	cfgPath := "config.yaml"
	if p := os.Getenv("ARISU_CONFIG"); p != "" {
		cfgPath = p
	}
	c, err := cfg.Load(cfgPath)
	if err != nil {
		log.Fatal("读取配置失败", zap.String("path", cfgPath), zap.Error(err))
	}
	if c.JWT.Secret == "" || len(c.JWT.Secret) < 32 || c.JWT.Secret == "change-me-to-a-long-random-string-please" {
		log.Fatal("jwt.secret 不安全：请设置为至少 32 字符的随机串（openssl rand -hex 32），不能保留示例占位符")
	}

	if c.DevMode && os.Getenv("ARISU_ALLOW_DEV_MODE") != "1" {
		log.Fatal("dev_mode=true 需要环境变量 ARISU_ALLOW_DEV_MODE=1 显式放行（生产部署必须 dev_mode: false）")
	}

	ctx := context.Background()
	st, err := store.New(ctx, c.DB.DSN)
	if err != nil {
		log.Fatal("连接 PostgreSQL 失败", zap.Error(err))
	}
	if err := st.Migrate(ctx); err != nil {
		log.Fatal("数据库迁移失败", zap.Error(err))
	}
	log.Info("数据库就绪")

	rdb := redis.NewClient(&redis.Options{Addr: c.Redis.Addr, Password: c.Redis.Password, DB: c.Redis.DB})
	if err := rdb.Ping(ctx).Err(); err != nil {
		log.Fatal("连接 Redis 失败", zap.Error(err))
	}
	log.Info("Redis 就绪")

	if err := os.MkdirAll(c.Upload.Dir, 0o755); err != nil {
		log.Fatal("创建上传目录失败", zap.Error(err))
	}

	mailer := &svc.Mailer{
		Host: c.SMTP.Host, Port: c.SMTP.Port, Username: c.SMTP.Username,
		Password: c.SMTP.Password, From: c.SMTP.From, Log: log,
	}
	if !mailer.Configured() {
		log.Warn("SMTP 未配置：验证码将打印到日志（开发模式），生产环境请在 config.yaml 配置 smtp")
	}

	go func() {
		ticker := time.NewTicker(10 * time.Minute)
		defer ticker.Stop()
		for range ticker.C {
			if n, err := st.AnonymizeExpiredDeactivations(ctx, int64(c.Limits.DeactivateCooldown.Seconds())); err == nil && n > 0 {
				log.Info("已匿名化注销账号", zap.Int64("count", n))
			}
		}
	}()

	if !c.DevMode {
		gin.SetMode(gin.ReleaseMode)
	}

	w := &web.Web{St: st, Cfg: c, RDB: rdb, Mailer: mailer, Log: log}
	srv := &http.Server{Addr: c.Addr, Handler: w.Router()}
	go func() {
		log.Info("有栖神社后端启动", zap.String("addr", c.Addr), zap.String("base_url", c.BaseURL))
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatal("服务退出", zap.Error(err))
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, os.Interrupt, syscall.SIGTERM)
	<-quit
	log.Info("收到退出信号，优雅停机中（在途请求最多等 15s）")
	stopCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := srv.Shutdown(stopCtx); err != nil {
		log.Error("停机超时，强制退出", zap.Error(err))
	}
	st.Close()
	_ = rdb.Close()
	log.Info("已退出")
}
