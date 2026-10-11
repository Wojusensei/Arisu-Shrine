package store

import (
	"testing"
)

func TestPreviewOf(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"短文本", "短文本"},
		{"", ""},
		{"   多  段  空白   ", "多 段 空白"},
	}
	for _, c := range cases {
		if got := PreviewOf(c.in); got != c.want {
			t.Errorf("PreviewOf(%q) = %q, want %q", c.in, got, c.want)
		}
	}
	long := "一二三四五六七八九十一二三四五六七八九十一二三四五"
	got := PreviewOf(long)
	if got != "一二三四五六七八九十一二三四五六七八九十一二三四"+"…" {
		t.Errorf("PreviewOf(25字) = %q, 应截断到24字+省略号", got)
	}

	exact := "一二三四五六七八九十一二三四五六七八九十一二三四"
	if got := PreviewOf(exact); got != exact {
		t.Errorf("PreviewOf(24字) = %q, 不应截断", got)
	}
}

func TestFirstImageURL(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"![封面](https://example.com/a.png)", "https://example.com/a.png"},
		{"前文 ![a](https://e.com/1.jpg) 后文 ![b](https://e.com/2.jpg)", "https://e.com/1.jpg"},
		{"没有图", ""},
		{"![空地址]()", ""},
		{"![含空格地址](https://e.com/a b.png)", "https://e.com/a"},
	}
	for _, c := range cases {
		if got := firstImageURL(c.in); got != c.want {
			t.Errorf("firstImageURL(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}
