package web

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

// compareVersions 是「检查更新」的裁判：写错会把新版本判成旧版本，
// 客户端要么永远不更新、要么无限提示。这里把边界一次性钉死。
func TestCompareVersions(t *testing.T) {
	cases := []struct {
		a, b string
		want int
	}{
		{"0.0.1.21", "0.0.1.20", 1}, // 四段：客户端 versionName 的第四段必须参与比较
		{"0.0.1.20", "0.0.1.21", -1},
		{"v1.2.3", "1.2.3", 0}, // v 前缀可带可不带
		{"v0.3.1", "v0.3.0", 1},
		{"v0.3.0", "v0.3.1", -1},
		{"v0.10.0", "v0.9.0", 1}, // 数字比较而非字符串（"10" < "9" 是字符串坑）
		{"v1.0.0", "v0.9.9", 1},  // 进位
		{"0.0.1", "0.0.1", 0},
		{"v2.0.0-beta", "v2.0.0", 0}, // 后缀截断后相等
		{"  v1.0.0  ", "1.0.0", 0},   // 两边都容忍空白
	}
	for _, c := range cases {
		if got := compareVersions(c.a, c.b); got != c.want {
			t.Errorf("compareVersions(%q, %q) = %d, want %d", c.a, c.b, got, c.want)
		}
	}
}

func TestParseVersion(t *testing.T) {
	cases := []struct {
		in   string
		want [5]int
	}{
		{"v0.3.1", [5]int{0, 3, 1, 0, 0}},
		{"1.2.3", [5]int{1, 2, 3, 0, 0}},
		{"0.0.1.21", [5]int{0, 0, 1, 21, 0}},     // 客户端四段 versionName
		{"v1.0.0-beta.2", [5]int{1, 0, 0, 0, 0}}, // 后缀截掉
		{" v1.2.3 ", [5]int{1, 2, 3, 0, 0}},
		{"", [5]int{0, 0, 0, 0, 0}},
		{"v1", [5]int{1, 0, 0, 0, 0}}, // 段数不足补零
		{"v1.2", [5]int{1, 2, 0, 0, 0}},
		{"v1.2.3.4.5", [5]int{1, 2, 3, 4, 5}}, // 五段也认
		{"vX.Y.Z", [5]int{0, 0, 0, 0, 0}},     // 非数字段落零容忍
	}
	for _, c := range cases {
		got := parseVersion(c.in)
		if got != c.want {
			t.Errorf("parseVersion(%q) = %v, want %v", c.in, got, c.want)
		}
	}
}

// apkFileFor 决定「立即更新」下载哪个包：选错就是把旧包装进用户手机。
// 这里钉死三条：版本号匹配优先、给版本号时绝不下发旧包、v 前缀兼容
func TestApkFileFor(t *testing.T) {
	dir := t.TempDir()
	mk := func(name string, mod time.Time) {
		p := filepath.Join(dir, name)
		if err := os.WriteFile(p, []byte("apk"), 0o644); err != nil {
			t.Fatal(err)
		}
		if err := os.Chtimes(p, mod, mod); err != nil {
			t.Fatal(err)
		}
	}
	old := time.Now().Add(-time.Hour)
	mk("arisu-0.0.1.23.apk", old)   // 版本对，但时间旧
	mk("app-debug.apk", time.Now()) // 时间新，但与版本无关
	mk("notes.txt", time.Now())     // 非 apk，忽略
	mk("sub", time.Now())           // 目录，忽略

	if got := apkFileFor(dir, "0.0.1.23"); got != "arisu-0.0.1.23.apk" {
		t.Errorf("apkFileFor 版本匹配 = %q, want arisu-0.0.1.23.apk（目录序/时间新都不得抢）", got)
	}
	if got := apkFileFor(dir, "v0.0.1.23"); got != "arisu-0.0.1.23.apk" {
		t.Errorf("apkFileFor v 前缀 = %q, want arisu-0.0.1.23.apk", got)
	}
	if got := apkFileFor(dir, "0.0.1.99"); got != "" {
		t.Errorf("apkFileFor 无匹配包 = %q, want 空（不下发旧包）", got)
	}
	if got := apkFileFor(dir, ""); got != "" {
		t.Errorf("apkFileFor 空版本号 = %q, want 空（版本号必填，不给版本号就不挑包）", got)
	}
	if got := apkFileFor(filepath.Join(dir, "missing"), "0.0.1.23"); got != "" {
		t.Errorf("apkFileFor 目录不存在 = %q, want 空", got)
	}
}

// 目录里同时有正式包与 beta 包时，精确包名必须赢过子串扫描——
// "arisu-1.1.2.0-beta.apk" 的 '-' 排在 "arisu-1.1.2.0.apk" 的 '.' 之前，
// 纯子串匹配会让 stable 渠道下到 beta 构建
func TestApkFileForPrefersExactName(t *testing.T) {
	dir := t.TempDir()
	mk := func(name string) {
		if err := os.WriteFile(filepath.Join(dir, name), []byte("apk"), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	mk("arisu-1.1.2.0-beta.apk")
	mk("arisu-1.1.2.0.apk")

	if got := apkFileFor(dir, "1.1.2.0"); got != "arisu-1.1.2.0.apk" {
		t.Errorf("apkFileFor stable 精确匹配 = %q, want arisu-1.1.2.0.apk（不得按目录序命中 beta 包）", got)
	}
	if got := apkFileFor(dir, "v1.1.2.0"); got != "arisu-1.1.2.0.apk" {
		t.Errorf("apkFileFor v 前缀精确匹配 = %q, want arisu-1.1.2.0.apk", got)
	}
	if got := apkFileFor(dir, "1.1.2.0-beta"); got != "arisu-1.1.2.0-beta.apk" {
		t.Errorf("apkFileFor beta 精确匹配 = %q, want arisu-1.1.2.0-beta.apk", got)
	}
}
