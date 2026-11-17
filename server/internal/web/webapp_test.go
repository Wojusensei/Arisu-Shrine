package web

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"arisu/server/internal/model"
	"github.com/gin-gonic/gin"
)

func TestWebSafeNext(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"/b/main", "/b/main"},
		{"/post/arisu_000013?x=1", "/post/arisu_000013?x=1"},
		{"//evil.com/x", ""},
		{"https://evil.com", ""},
		{"javascript:alert(1)", ""},
		{"", ""},
		// 反斜杠：WHATWG URL 把它等同正斜杠，浏览器会解析成外站 origin
		{`/\evil.com`, ""},
		{`/\\evil.com`, ""},
		{`/b/main\@evil.com`, ""},
		// 控制字符：部分 UA 会在此截断
		{"/b/main\nSet-Cookie: x", ""},
		{"/b/main\r", ""},
		{"/b/main\x00", ""},
		// 站内带查询串与中文仍放行
		{"/search?q=显卡", "/search?q=显卡"},
		{"/me/settings?a=1&b=2", "/me/settings?a=1&b=2"},
	}
	for _, c := range cases {
		if got := webSafeNext(c.in); got != c.want {
			t.Errorf("webSafeNext(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestCnCount(t *testing.T) {
	// 与客户端 countCn 同口径：234569 → 23.5万、10000 → 1万、9999 原样
	cases := []struct {
		in   int64
		want string
	}{
		{0, "0"}, {9999, "9999"}, {10000, "1万"}, {10500, "1.1万"},
		{234569, "23.5万"}, {1234567, "123万"},
	}
	for _, c := range cases {
		if got := cnCount(c.in); got != c.want {
			t.Errorf("cnCount(%d) = %q, want %q", c.in, got, c.want)
		}
	}
}

// webCSRF：Sec-Fetch-Site / Origin 必须同源，否则 403
func TestWebCSRF(t *testing.T) {
	gin.SetMode(gin.TestMode)
	run := func(headers map[string]string) int {
		w := &Web{}
		r := gin.New()
		r.POST("/x", w.webCSRF(), func(c *gin.Context) { c.String(http.StatusOK, "ok") })
		req := httptest.NewRequest(http.MethodPost, "/x", nil)
		for k, v := range headers {
			req.Header.Set(k, v)
		}
		req.Host = "arisushrine.com"
		rec := httptest.NewRecorder()
		r.ServeHTTP(rec, req)
		return rec.Code
	}
	cases := []struct {
		name    string
		headers map[string]string
		want    int
	}{
		{"same-origin fetch", map[string]string{"Sec-Fetch-Site": "same-origin"}, 200},
		{"cross-site fetch", map[string]string{"Sec-Fetch-Site": "cross-site"}, 403},
		{"no fetch meta, same origin", map[string]string{"Origin": "https://arisushrine.com"}, 200},
		{"no fetch meta, foreign origin", map[string]string{"Origin": "https://evil.com"}, 403},
		{"plain form post (no headers)", nil, 200},
	}
	for _, c := range cases {
		if got := run(c.headers); got != c.want {
			t.Errorf("%s: status = %d, want %d", c.name, got, c.want)
		}
	}
}

func TestWebEnum(t *testing.T) {
	allowed := []string{"new", "hot", "essence"}
	if got := webEnum("hot", allowed); got != "hot" {
		t.Errorf("webEnum legal = %q", got)
	}
	if got := webEnum("hacked'--", allowed); got != "new" {
		t.Errorf("webEnum illegal must fall back: %q", got)
	}
}

func TestWebGroupCommentsReplyName(t *testing.T) {
	pid := int64(1)
	cs := []model.Comment{
		{ID: 1, Content: "主楼", Author: model.UserBrief{Nickname: "楼主"}},
		{ID: 2, Content: "回复", ParentID: &pid, Author: model.UserBrief{Nickname: "客人"}, ReplyToUser: &model.UserBrief{Nickname: "楼主"}},
	}
	views := webGroupComments(cs, 0, 0)
	if views[1].ReplyToName != "楼主" {
		t.Errorf("reply name = %q, want 楼主", views[1].ReplyToName)
	}
	if !strings.Contains(string(views[1].BodyHTML), "回复") {
		t.Errorf("body missing: %q", views[1].BodyHTML)
	}
}

// webCommentChain 造 n 层父链：ID 1 为顶级，ID k 的父为 k-1。
func webCommentChain(n int) []model.Comment {
	out := make([]model.Comment, 0, n)
	for i := 1; i <= n; i++ {
		c := model.Comment{ID: int64(i), Content: "层", Author: model.UserBrief{Nickname: "社员"}}
		if i > 1 {
			pid := int64(i - 1)
			c.ParentID = &pid
		}
		out = append(out, c)
	}
	return out
}

func webCommentContains(views []commentView, id int64) bool {
	for _, v := range views {
		if v.ID == id {
			return true
		}
	}
	return false
}

// webCommentAllOnce 断言 1..n 每条恰好出现一次（不丢、不重）
func webCommentAllOnce(t *testing.T, views []commentView, n int) {
	t.Helper()
	seen := map[int64]int{}
	for _, v := range views {
		seen[v.ID]++
	}
	for i := int64(1); i <= int64(n); i++ {
		if seen[i] != 1 {
			t.Errorf("ID %d 出现 %d 次，want 1", i, seen[i])
		}
	}
}

// 对账 #1：父链 ≥13 层时网页端曾把最深回复挂进非顶级节点的桶 → 永不发射、
// 整条消失。安卓 CommentGrouping.rootOf（CommentGrouping.kt:15-23）在
// repeat(13) 耗尽后返回 null 判孤儿、原位保留；Go 侧必须同语义。
func TestWebGroupCommentsDeepChain(t *testing.T) {
	t.Run("13 层整链不丢", func(t *testing.T) {
		views := webGroupComments(webCommentChain(13), 0, 0)
		if len(views) != 13 {
			t.Fatalf("13 层：输出 %d 条，want 13（总条数必须不变）", len(views))
		}
		webCommentAllOnce(t, views, 13)
		if !webCommentContains(views, 13) {
			t.Errorf("13 层：最深层 ID 13 丢失")
		}
	})
	t.Run("14 层最深层判孤儿原位保留", func(t *testing.T) {
		views := webGroupComments(webCommentChain(14), 0, 0)
		if len(views) != 14 {
			t.Fatalf("14 层：输出 %d 条，want 14", len(views))
		}
		webCommentAllOnce(t, views, 14)
		if !webCommentContains(views, 14) {
			t.Errorf("14 层：最深层 ID 14 丢失（安卓应判孤儿原位保留）")
		}
	})
	t.Run("15 层父链最深层仍可见且总数不变", func(t *testing.T) {
		cs := webCommentChain(15)
		// 打乱输入顺序：回复先于祖先出现，验证归组不依赖下发次序
		cs[0], cs[14] = cs[14], cs[0]
		views := webGroupComments(cs, 0, 0)
		if len(views) != 15 {
			t.Fatalf("15 层：输出 %d 条，want 15（总条数不变，与安卓 groupedByThread 一致）", len(views))
		}
		webCommentAllOnce(t, views, 15)
		if !webCommentContains(views, 15) {
			t.Errorf("15 层：最深层 ID 15 整条消失")
		}
		// 14、15 层都是孤儿，应有回复前缀（Nested）
		for _, v := range views {
			if (v.ID == 14 || v.ID == 15) && !v.Nested {
				t.Errorf("ID %d 应按孤儿原位保留为 nested", v.ID)
			}
		}
	})
}

// 对账 #3：webTime 必须逐分支等于 TimeFmt.epoch（TimeFmt.kt:17-43）
func TestWebTimeMatchesAndroidTimeFmt(t *testing.T) {
	// 固定上海时区 2026-11-14 10:00（周六）
	now := time.Date(2026, 11, 14, 10, 0, 0, 0, webLoc)
	epochAt := func(y int, mo time.Month, d, h, mi int) int64 {
		return time.Date(y, mo, d, h, mi, 0, 0, webLoc).Unix()
	}
	cases := []struct {
		name  string
		epoch int64
		want  string
	}{
		{"零值", 0, ""},
		{"负值", -1, ""},
		{"将来的日期", epochAt(2026, 11, 15, 12, 0), "刚刚"},
		{"同一时刻", now.Unix(), "刚刚"},
		{"1 分钟前", now.Add(-time.Minute).Unix(), "刚刚"},
		{"2 分钟前", now.Add(-2 * time.Minute).Unix(), "2 分钟前"},
		{"59 分钟前", now.Add(-59 * time.Minute).Unix(), "59 分钟前"},
		{"60 分钟前", now.Add(-60 * time.Minute).Unix(), "1 小时前"},
		{"119 分钟前", now.Add(-119 * time.Minute).Unix(), "1 小时前"},
		{"120 分钟前", now.Add(-120 * time.Minute).Unix(), "2 小时前"},
		{"6 小时前", now.Add(-360 * time.Minute).Unix(), "6 小时前"},
		{"361 分钟前落今天 HH:mm", now.Add(-361 * time.Minute).Unix(), "今天 03:59"},
		{"今天零点后", epochAt(2026, 11, 14, 0, 0), "今天 00:00"},
		{"昨天深夜", epochAt(2026, 11, 13, 23, 0), "昨天 23:00"},
		{"2 天前", epochAt(2026, 11, 12, 12, 0), "2 天前"},
		{"6 天前", epochAt(2026, 11, 8, 12, 0), "6 天前"},
		{"7 天前落同年月日", epochAt(2026, 11, 7, 12, 0), "11月7日"},
		{"同年更早", epochAt(2026, 1, 5, 8, 0), "1月5日"},
		{"跨年", epochAt(2025, 12, 31, 23, 0), "2025年12月31日"},
	}
	for _, c := range cases {
		if got := webTimeAt(c.epoch, now); got != c.want {
			t.Errorf("%s: webTimeAt(%d) = %q, want %q", c.name, c.epoch, got, c.want)
		}
	}
}

// 时区固定 Asia/Shanghai：同一 epoch 在网页端恒按东八区判定「今天/昨天」，
// 与安卓设备本地时区不同（TimeFmt.kt:15-21）——这是已知且有意保留的差异。
func TestWebTimeFixedZone(t *testing.T) {
	if webLoc.String() != "Asia/Shanghai" && webLoc.String() != "CST" {
		t.Fatalf("webLoc = %q，应固定东八区", webLoc.String())
	}
	now := time.Date(2026, 11, 14, 0, 30, 0, 0, webLoc)
	// UTC 2026-11-13 16:30 = 上海 2026-11-14 00:30：上海视角是「刚刚」
	epoch := time.Date(2026, 11, 13, 16, 30, 0, 0, time.UTC).Unix()
	if got := webTimeAt(epoch, now); got != "刚刚" {
		t.Errorf("上海时区边界：got %q, want 刚刚", got)
	}
}
