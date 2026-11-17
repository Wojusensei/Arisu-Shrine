package web

import (
	"strings"
	"testing"
)

func TestFootnotes(t *testing.T) {
	// 基本：定义行被收集，末尾输出「[key] 内容」
	got := mdRender("正文[^1]\n\n[^1]: 这是脚注")
	if !strings.Contains(got, `class="md-footnotes"`) {
		t.Errorf("missing footnotes block: %q", got)
	}
	if !strings.Contains(got, "[1] 这是脚注") {
		t.Errorf("missing footnote text: %q", got)
	}
	// 定义行不该作为普通段落出现
	if strings.Contains(got, "[^1]: 这是脚注") {
		t.Errorf("definition leaked as paragraph: %q", got)
	}
	// 多条按出现顺序
	got2 := mdRender("x\n\n[^b]: 第二\n[^a]: 第一")
	ib := strings.Index(got2, "第二")
	ia := strings.Index(got2, "第一")
	if ib < 0 || ia < 0 || ib > ia {
		t.Errorf("order wrong: %q", got2)
	}
	// 没有脚注时不输出空块
	if strings.Contains(mdRender("普通正文"), "md-footnotes") {
		t.Error("empty footnotes block emitted")
	}
}
