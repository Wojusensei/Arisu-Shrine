package web

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestCompareVersions(t *testing.T) {
	cases := []struct {
		a, b string
		want int
	}{
		{"0.0.1.21", "0.0.1.20", 1},
		{"0.0.1.20", "0.0.1.21", -1},
		{"v1.2.3", "1.2.3", 0},
		{"v0.3.1", "v0.3.0", 1},
		{"v0.3.0", "v0.3.1", -1},
		{"v0.10.0", "v0.9.0", 1},
		{"v1.0.0", "v0.9.9", 1},
		{"0.0.1", "0.0.1", 0},
		{"v2.0.0-beta", "v2.0.0", 0},
		{"  v1.0.0  ", "1.0.0", 0},
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
		{"0.0.1.21", [5]int{0, 0, 1, 21, 0}},
		{"v1.0.0-beta.2", [5]int{1, 0, 0, 0, 0}},
		{" v1.2.3 ", [5]int{1, 2, 3, 0, 0}},
		{"", [5]int{0, 0, 0, 0, 0}},
		{"v1", [5]int{1, 0, 0, 0, 0}},
		{"v1.2", [5]int{1, 2, 0, 0, 0}},
		{"v1.2.3.4.5", [5]int{1, 2, 3, 4, 5}},
		{"vX.Y.Z", [5]int{0, 0, 0, 0, 0}},
	}
	for _, c := range cases {
		got := parseVersion(c.in)
		if got != c.want {
			t.Errorf("parseVersion(%q) = %v, want %v", c.in, got, c.want)
		}
	}
}

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
	mk("arisu-0.0.1.23.apk", old)
	mk("app-debug.apk", time.Now())
	mk("notes.txt", time.Now())
	mk("sub", time.Now())

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
