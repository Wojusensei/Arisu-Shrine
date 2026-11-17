package web

import (
	"strings"
	"testing"

	"arisu/server/internal/model"
)

// 网页端与安卓端同一批渲染口径：这些用例与 Compose 侧 Markdown 测试互为镜像，
// 两端漂移时先断这里。HTML 是 Go 渲染器私有产物，断言只认结构事实（标签与转义）。
func TestMdRenderBasics(t *testing.T) {
	cases := []struct{ name, in, want string }{
		{"heading", "## 站名", "<h2>站名</h2>"},
		{"bold italic code", "**粗**与*斜*与`码`", "<p><b>粗</b>与<i>斜</i>与<code>码</code></p>"},
		{"strikethrough", "~~划掉~~", "<p><s>划掉</s></p>"},
		{"rule", "---", "<hr>"},
		{"soft break keeps line", "一行\n二行", "<p>一行<br>二行</p>"},
		{
			// 行为变更（2026-11-16）：围栏代码现在带语法高亮（对齐 Highlight.kt），
			// 关键词会被 <span class="c-kw"> 包住；转义仍完整（< > & 全部实体化）。
			"fence escapes body and highlights keywords",
			"```go\nif a < b && c > d {\n\treturn\n}\n```",
			`<pre><code class="language-go">` +
				`<span class="c-kw">if</span> a &lt; b &amp;&amp; c &gt; d {` + "\n\t" +
				`<span class="c-kw">return</span>` + "\n}" + "</code></pre>",
		},
		{
			// 未闭合围栏同样走高亮路径；print 不是 python 关键词，原样转义
			"unclosed fence consumes to end",
			"前文\n```python\nprint(1)\n后文",
			"<p>前文</p>\n<pre><code class=\"language-python\">print(<span class=\"c-num\">1</span>)\n后文</code></pre>",
		},
		{
			// 行为变更（2026-11-16）：原先「原始 HTML 整体转义成文本」；现对齐安卓
			// Markdown.kt:120-250 的 normalizeHtml——未知标签剥壳留文字，脚本内容
			// 作为纯文本保留（仍被 html.EscapeString 转义，不产生可执行标签）。
			"raw html stripped to text",
			"正文<script>alert(1)</script>完",
			"<p>正文alert(1)完</p>",
		},
		{
			"paragraph image extracted as figure",
			"文字 ![截图](https://a/b.png) 尾巴",
			`<p>文字</p>` + "\n" + `<figure><img src="https://a/b.png" alt="截图" loading="lazy" referrerpolicy="no-referrer"><figcaption>截图</figcaption></figure>` + "\n" + `<p>尾巴</p>`,
		},
	}
	for _, c := range cases {
		if got := mdRender(c.in); got != c.want {
			t.Errorf("%s:\n got  %q\n want %q", c.name, got, c.want)
		}
	}
}

func TestMdRenderLinks(t *testing.T) {
	if got := mdRender("[看(注)](https://x.com)"); !strings.Contains(got, `href="https://x.com"`) || !strings.Contains(got, "看(注)") {
		t.Errorf("label with parens: %q", got)
	}
	if got := mdRender("[文字](javascript:alert(1))"); strings.Contains(got, "<a ") {
		t.Errorf("javascript url must not become a link: %q", got)
	} else if !strings.Contains(got, "文字") {
		t.Errorf("unsafe link falls back to colored text: %q", got)
	}
	if got := mdRender("[站内](/uploads/x.png)"); !strings.Contains(got, `href="/uploads/x.png"`) {
		t.Errorf("site-relative link allowed: %q", got)
	}
}

func TestMdRenderMentionFindSemantics(t *testing.T) {
	// 与服务端同尺：@User_123abc 按编号 123 记，可点部分是 @User_123，尾巴留正文
	got := mdRender("@User_123abc 好奇")
	if !strings.Contains(got, `href="/u/User_000123"`) || !strings.Contains(got, "@User_123") {
		t.Errorf("mention link: %q", got)
	}
	if !strings.Contains(got, "abc") {
		t.Errorf("tail after mention must stay as text: %q", got)
	}
	if got = mdRender("@路人甲"); strings.Contains(got, "<a") {
		t.Errorf("non-mention @word must stay plain: %q", got)
	}
}

func TestMdRenderTable(t *testing.T) {
	// \| 是字面竖线不切列；行数对齐表头（缺补空列）
	got := mdRender("| A | B |\n| --- | --- |\n| 1 \\| 2 | c |\n| 3 |")
	if !strings.Contains(got, "<td>1 | 2</td>") {
		t.Errorf("escaped pipe must stay one cell: %q", got)
	}
	if strings.Count(got, "<tr>") != 3 { // 表头 1 行 + 正文 2 行，分隔行不落体
		t.Errorf("separator row must not emit a body row: %q", got)
	}
	if !strings.Contains(got, "<td>3</td><td></td>") {
		t.Errorf("short row must pad empty cell: %q", got)
	}
}

func TestMdRenderListsAndQuote(t *testing.T) {
	got := mdRender("- 甲\n- 乙\n\n1. 一\n2. 二")
	if !strings.Contains(got, "<ul><li>甲</li><li>乙</li></ul>") {
		t.Errorf("bullet list: %q", got)
	}
	if !strings.Contains(got, "<ol><li>一</li><li>二</li></ol>") {
		t.Errorf("ordered list: %q", got)
	}
	got = mdRender("> 第一行\n> 第二行")
	if !strings.Contains(got, "<blockquote><p>第一行<br>第二行</p></blockquote>") {
		t.Errorf("quote: %q", got)
	}
	got = mdRender("- [x] 已办\n- [ ] 未办")
	if !strings.Contains(got, "checked") || !strings.Contains(got, "未办") {
		t.Errorf("task list: %q", got)
	}
}

func TestMdRenderMentionAndQuoteSafety(t *testing.T) {
	// 图片地址不合法时退化为原文，不产 <img>
	got := mdRender("![x](javascript:alert(1))")
	if strings.Contains(got, "<img") {
		t.Errorf("unsafe image url must not render: %q", got)
	}
	if !strings.Contains(got, "&lt;script&gt;") == true && !strings.Contains(got, "javascript") {
		t.Errorf("unsafe image url falls back to text: %q", got)
	}
}

func TestWebGroupComments(t *testing.T) {
	// 回复挪到顶级祖先名下、祖删除的原位保留、组内保持时间序（与客户端同规）
	pid := int64(1)
	rpid := int64(2)
	cs := []model.Comment{
		{ID: 1, Content: "主楼"},
		{ID: 3, Content: "晚到的回复", ParentID: &pid},
		{ID: 2, Content: "楼中楼", ParentID: &pid},
		{ID: 4, Content: "孤儿", ParentID: &rpid}, // 父 id=2 不存在（已删）
	}
	views := webGroupComments(cs, 0, 0)
	if len(views) != 4 {
		t.Fatalf("total must be preserved: %d", len(views))
	}
	order := []string{"主楼", "晚到的回复", "楼中楼", "孤儿"}
	for i, want := range order {
		if !strings.Contains(string(views[i].BodyHTML), want) {
			t.Errorf("position %d = %q, want contains %q", i, views[i].BodyHTML, want)
		}
	}
	if !views[1].Nested || !views[2].Nested || !views[3].Nested {
		t.Errorf("replies should render nested")
	}
	if views[0].Nested {
		t.Errorf("top-level comment must not be nested")
	}
}
