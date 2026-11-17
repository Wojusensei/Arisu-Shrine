package web

import (
	"strings"
	"testing"
)

// HTML 预处理（normalizeHTML）行为与安全基线：
// 对齐安卓 Markdown.kt:120-250，GitHub 式 README 的常见 HTML 转等价 Markdown。
// 安全：未知标签剥壳，用户文字一律经 html.EscapeString，不产生可执行标签。

func TestNormalizeHTMLEffects(t *testing.T) {
	cases := []struct{ in, want string }{
		{"a<br>b", "a\nb"},
		{"<!-- c -->text", "text"},
		{`<img src="https://x/a.png" alt="A">`, `![A](https://x/a.png)`},
		{`<a href="https://x">链接</a>`, `[链接](https://x)`},
		{"&amp;&lt;", "&<"},
		{"<b>粗</b>", "粗"},
	}
	for _, c := range cases {
		got := normalizeHTML(c.in)
		if !containsStr(got, c.want) {
			t.Errorf("normalizeHTML(%q) = %q, want contains %q", c.in, got, c.want)
		}
	}
}

func TestHTMLPreprocessSecurity(t *testing.T) {
	// 1. script 标签不能产出可执行标签
	got := mdRender("正文<script>alert(1)</script>完")
	if strings.Contains(got, "<script") {
		t.Errorf("script tag leaked: %q", got)
	}
	// 2. onerror 等事件属性不能被保留
	got2 := mdRender(`<img src="x" onerror="alert(1)">`)
	if strings.Contains(got2, "onerror") {
		t.Errorf("onerror leaked: %q", got2)
	}
	// 3. javascript: 链接必须被拒
	got3 := mdRender(`<a href="javascript:alert(1)">点</a>`)
	if strings.Contains(got3, "javascript:") && strings.Contains(got3, `href="javascript`) {
		t.Errorf("javascript href leaked: %q", got3)
	}
}

func containsStr(s, sub string) bool {
	return sub == "" || strings.Contains(s, sub)
}
