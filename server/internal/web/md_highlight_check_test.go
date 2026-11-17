package web

import (
	"strings"
	"testing"
)

func TestCodeHighlight(t *testing.T) {
	src := "```go\nfunc main() {\n    // c\n    x := 42\n    fmt.Println(\"hi\")\n}\n```"
	got := mdRender(src)
	for _, want := range []string{`class="c-kw"`, `class="c-cmt"`, `class="c-num"`, `class="c-str"`} {
		if !strings.Contains(got, want) {
			t.Errorf("missing %s in %q", want, got)
		}
	}
	// 未闭合/未知语言退回纯文本，且不能把 `<` 漏成标签
	raw := mdRender("```\n<script>alert(1)</script>\n```")
	if strings.Contains(raw, "<script") {
		t.Errorf("script leaked: %q", raw)
	}
	// 别名归一
	if !strings.Contains(mdRender("```js\nconst a = 1\n```"), `class="c-kw"`) {
		t.Error("js alias not normalized")
	}
}
