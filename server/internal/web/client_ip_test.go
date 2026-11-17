package web

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"arisu/server/internal/cfg"
	"github.com/gin-gonic/gin"
)

// clientIP 是登录锁、验证码配额、洪泛闸门共用的计数维度：头缺失或不是合法 IP
// 时必须落回对端地址，否则一个畸形头要么让所有人挤进同一个桶，
// 要么让攻击者每次换桶逃逸出限额
func TestClientIP(t *testing.T) {
	cases := []struct {
		name   string
		header string
		value  string
		want   string
	}{
		{"采信配置头", "CF-Connecting-IP", "203.0.113.7", "203.0.113.7"},
		{"带空白也认", "CF-Connecting-IP", " 203.0.113.7 ", "203.0.113.7"},
		{"IPv6 也认", "CF-Connecting-IP", "2001:db8::1", "2001:db8::1"},
		{"非 IP 落回对端", "CF-Connecting-IP", "not-an-ip", "198.51.100.9"},
		{"多值列表落回对端", "CF-Connecting-IP", "203.0.113.7, 10.0.0.1", "198.51.100.9"},
		{"未配置头时不看该头", "", "203.0.113.7", "198.51.100.9"},
	}
	for _, tc := range cases {
		gin.SetMode(gin.TestMode)
		r := gin.New()
		w := &Web{Cfg: &cfg.Config{ClientIPHeader: tc.header}}
		var got string
		r.GET("/t", func(c *gin.Context) { got = w.clientIP(c) })
		req := httptest.NewRequest(http.MethodGet, "/t", nil)
		req.RemoteAddr = "198.51.100.9:12345"
		if tc.header != "" && tc.value != "" {
			req.Header.Set(tc.header, tc.value)
		}
		r.ServeHTTP(httptest.NewRecorder(), req)
		if got != tc.want {
			t.Errorf("%s: clientIP = %q, want %q", tc.name, got, tc.want)
		}
	}
}

// 闸门关闭态（0/负值）不许碰 Redis：配置缺项本意是少一道闸，
// 要是走到 null 客户端上就是全站 500——小配置失误放大成停机
func TestFloodGuardDisabledPassesThrough(t *testing.T) {
	gin.SetMode(gin.TestMode)
	var conf cfg.Config
	conf.Limits.FloodPerMin = 0
	w := &Web{Cfg: &conf}
	r := gin.New()
	r.Use(w.floodGuard())
	r.GET("/t", func(c *gin.Context) { c.Status(http.StatusNoContent) })
	rec := httptest.NewRecorder()
	r.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/t", nil))
	if rec.Code != http.StatusNoContent {
		t.Errorf("闸门关闭时应原样放行，got %d", rec.Code)
	}
}
