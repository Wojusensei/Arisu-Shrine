package web

import (
	"regexp"
	"strings"
)

// 网页端 markdown 纯文本摘要（与安卓 MarkdownPlain.kt 同一把尺）：
// 帖子卡在介绍语缺位时用它从正文提取可读文字。

var (
	mdpFenceRe   = regexp.MustCompile("(?s)```[\\s\\S]*?(?:```|$)")
	mdpImageRe   = regexp.MustCompile("!\\[([^]]*)]\\([^)]*\\)")
	mdpLinkRe    = regexp.MustCompile("\\[([^]]*)]\\([^)]*\\)")
	mdpHeadingRe = regexp.MustCompile("(?m)^\\s{0,3}#{1,6}\\s*")
	mdpQuoteRe   = regexp.MustCompile("(?m)^\\s*>\\s?")
	mdpListRe    = regexp.MustCompile("(?m)^\\s*(?:[-*+]|\\d+\\.)\\s+")
	mdpTagRe     = regexp.MustCompile("<[^>]+>")
	mdpSpaceRe   = regexp.MustCompile("\\n{3,}")
	// *_ 的词边界清除不走正则：RE2 没有 lookbehind，见 mdStripLonelyMarks
)

// mdStripLonelyMarks 去掉不成对的 * _（词边界判定与安卓 (?<!\w)[*_]|[*_](?!\w)
// 同语义：两侧都是词字符才保留——User_000001 的下划线不能被动）。
// Go 的 RE2 没有 lookbehind，手写扫描。
func mdStripLonelyMarks(s string) string {
	rs := []rune(s)
	var b strings.Builder
	isWord := func(r rune) bool {
		return r == '_' || (r >= '0' && r <= '9') || (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z')
	}
	for i, r := range rs {
		if r == '*' || r == '_' {
			prevOK := i > 0 && isWord(rs[i-1])
			nextOK := i+1 < len(rs) && isWord(rs[i+1])
			if prevOK && nextOK {
				b.WriteRune(r)
			}
			continue
		}
		b.WriteRune(r)
	}
	return b.String()
}

// mdPlain 把 markdown 压成纯文本；keepImages=false 时图片记号整体去掉（分享卡口径）。
func mdPlain(markdown string, keepImages bool) string {
	s := mdpFenceRe.ReplaceAllStringFunc(markdown, func(m string) string {
		// 与安卓同口径：摘掉围栏记号与语言行，保留代码正文
		return strings.SplitN(strings.TrimSuffix(strings.TrimPrefix(m, "```"), "```"), "\n", 2)[1]
	})
	if keepImages {
		s = mdpImageRe.ReplaceAllString(s, "$1")
	} else {
		s = mdpImageRe.ReplaceAllString(s, "")
	}
	s = mdpLinkRe.ReplaceAllString(s, "$1")
	s = mdpHeadingRe.ReplaceAllString(s, "")
	s = mdpQuoteRe.ReplaceAllString(s, "")
	s = mdpListRe.ReplaceAllString(s, "")
	s = strings.ReplaceAll(s, "**", "")
	s = strings.ReplaceAll(s, "__", "")
	s = strings.ReplaceAll(s, "~~", "")
	s = strings.ReplaceAll(s, "`", "")
	s = mdStripLonelyMarks(s)
	s = mdpTagRe.ReplaceAllString(s, "")
	s = mdpSpaceRe.ReplaceAllString(s, "\n\n")
	return strings.TrimSpace(s)
}
