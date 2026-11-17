package store

import (
	"testing"
)

// PreviewOf 决定通知/评论摘要的截断，圆头省略号必须按 rune 切，
// 按 byte 切会把中文腰斩成乱码。
func TestPreviewOf(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"短文本", "短文本"},
		{"", ""},
		{"   多  段  空白   ", "多 段 空白"}, // 连续空白压成一个
	}
	for _, c := range cases {
		if got := PreviewOf(c.in); got != c.want {
			t.Errorf("PreviewOf(%q) = %q, want %q", c.in, got, c.want)
		}
	}
	long := "一二三四五六七八九十一二三四五六七八九十一二三四五" // 25 rune
	got := PreviewOf(long)
	if got != "一二三四五六七八九十一二三四五六七八九十一二三四"+"…" {
		t.Errorf("PreviewOf(25字) = %q, 应截断到24字+省略号", got)
	}
	// 24 字整：不追加省略号
	exact := "一二三四五六七八九十一二三四五六七八九十一二三四"
	if got := PreviewOf(exact); got != exact {
		t.Errorf("PreviewOf(24字) = %q, 不应截断", got)
	}
}

// firstImageURL 从 markdown 里抠第一张图，抠错会把封面上成任意文本
func TestFirstImageURL(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"![封面](https://example.com/a.png)", "https://example.com/a.png"},
		{"前文 ![a](https://e.com/1.jpg) 后文 ![b](https://e.com/2.jpg)", "https://e.com/1.jpg"},
		{"没有图", ""},
		{"![空地址]()", ""},
		{"![含空格地址](https://e.com/a b.png)", "https://e.com/a"}, // 地址在空格处截断（正则 [^)\s]+ 的现状）
	}
	for _, c := range cases {
		if got := firstImageURL(c.in); got != c.want {
			t.Errorf("firstImageURL(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}
