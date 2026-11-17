package web

import (
	"html"
	"regexp"
	"strconv"
	"strings"

	"arisu/server/internal/model"
)

// 网页端 markdown 渲染（与安卓 Compose 渲染器同一把尺的 Go 实现）。
// 里程碑 1 覆盖核心子集：围栏代码/标题/分割线/表格/任务列表/有序无序列表/
// 引用/段落（软换行保留为 <br>、段内图抽出）/行内样式/链接/图片/提及。
// HTML 预处理（normalizeHTML，对齐安卓 Markdown.kt:120-250）：注释剥离、<br> 转软换行、
// <img>/<picture> 转 Markdown 图、<a> 转链接、<table>/<h*>/<blockquote> 转等价 Markdown、
// 居中容器转块标记、<details> 转加粗摘要 + 引用；其余标签剥壳留文本，实体解码。
// 脚注（[^1]: 定义）尚未实现——安卓有此能力，但生产与本地都无人使用，留作已知差异。
//
// 安全基线：所有文本节点经 html 转义；URL 只放行 http(s) 与站内相对路径；
// 标签全部由本文件生成，用户内容永远进不了标签/属性位。

type mdBlockKind int

const (
	mdParag mdBlockKind = iota
	mdFence
	mdHeading
	mdRule
	mdTable
	mdTasks
	mdBullets
	mdOrdered
	mdQuote
	mdImage
	mdFootnotes
)

type mdBlock struct {
	kind  mdBlockKind
	lines []string // 段落/引用/表格的原始行；围栏的语言与代码体也放这里
	level int      // 标题级别
	lang  string
	alt   string // 图片 alt
	url   string // 图片地址
	// notes 收集 [^key]: 内容 形式的脚注，按出现顺序（linkedMap 语义），
	// 全部正文解析完后作为最后一个块输出（Markdown.kt:291,448）。
	notes [][2]string
}

var (
	// 脚注定义 $1=键 $2=内容（Markdown.kt:118 同一正则）
	mdFootnoteRe = regexp.MustCompile(`^\[\^(\w+)\]:\s*(.*)$`)
	mdImageRe    = regexp.MustCompile(`!\[([^\]]*)]\(([^)\s]+)\)`)
	mdInlineRe   = regexp.MustCompile(`(!\[[^\]]*]\([^)\s]+\))|(\*\*\*[^*]+\*\*\*)|(\*\*[^*]+\*\*)|(\*[^*\n]+\*)|(` + "`" + `[^` + "`" + `]+` + "`" + `)|(\[[^\]]+]\([^)\s]+\))|(\[\^\w+\])|(~~[^~]+~~)|(@[\w\x{4e00}-\x{9fa5}·]+)`)
	mdHeadingRe  = regexp.MustCompile(`^(#{1,6})\s+(.*)$`)
	mdBulletRe   = regexp.MustCompile(`^\s*[-*+]\s+(.*)$`)
	mdOrderedRe  = regexp.MustCompile(`^\s*\d+[.)]\s+(.*)$`)
	mdQuoteRe    = regexp.MustCompile(`^\s*>\s?(.*)$`)
	mdTaskRe     = regexp.MustCompile(`^\s*[-*+]\s+\[([xX])\]\s+(.*)$`)
	mdMentionRe  = regexp.MustCompile(`(?i)@user_0*(\d{1,12})`)
)

// mdRender 渲染正文。返回值只含本文件生成的标签，可安全放进 template.HTML。
func mdRender(src string) string {
	var b strings.Builder
	blocks := mdParse(src)
	for i, blk := range blocks {
		if i > 0 {
			b.WriteString("\n")
		}
		mdRenderBlock(&b, blk)
	}
	return b.String()
}

// ── HTML 预处理 ──────────────────────────────────────────────
// 对齐 Markdown.kt:120-250：GitHub 式 README 里常见的 HTML 在进块级解析前
// 转成等价 Markdown，避免整段被当成纯文本。

var (
	mdHTMLComment = regexp.MustCompile(`(?s)<!--.*?-->`)
	mdHTMLBR      = regexp.MustCompile(`(?i)<br\s*/?>`)
	mdHTMLDetails = regexp.MustCompile(`(?is)<details\b[^>]*>(.*?)</details\s*>`)
	mdHTMLSummary = regexp.MustCompile(`(?is)<summary\b[^>]*>(.*?)</summary\s*>`)
	mdHTMLPicture = regexp.MustCompile(`(?is)<picture\b[^>]*>(.*?)</picture\s*>`)
	mdHTMLSource  = regexp.MustCompile(`(?i)<source\b[^>]*srcset\s*=\s*"([^"]*)"[^>]*>`)
	mdHTMLImg     = regexp.MustCompile(`(?i)<img\b[^>]*>`)
	mdHTMLImgAttr = regexp.MustCompile(`(?i)(src|alt)\s*=\s*"([^"]*)"`)
	mdHTMLA       = regexp.MustCompile(`(?is)<a\b[^>]*href\s*=\s*"([^"]*)"[^>]*>(.*?)</a\s*>`)
	mdHTMLTable   = regexp.MustCompile(`(?is)<table\b[^>]*>(.*?)</table\s*>`)
	mdHTMLTr      = regexp.MustCompile(`(?is)<tr\b[^>]*>(.*?)</tr\s*>`)
	mdHTMLCell    = regexp.MustCompile(`(?is)<t[dh]\b[^>]*>(.*?)</t[dh]\s*>`)
	mdHTMLHeading = regexp.MustCompile(`(?is)<h([1-6])\b[^>]*>(.*?)</h[1-6]\s*>`)
	mdHTMLQuote   = regexp.MustCompile(`(?is)<blockquote\b[^>]*>(.*?)</blockquote\s*>`)
	mdHTMLAnyTag  = regexp.MustCompile(`</?[a-zA-Z][^>]*>`)
)

var mdUnwrapTags = []string{"sub", "sup", "kbd", "mark", "u", "small", "center", "font", "span", "p", "div", "section", "article", "figure", "figcaption", "summary"}

var mdEntities = [][2]string{
	{"&amp;", "&"}, {"&lt;", "<"}, {"&gt;", ">"}, {"&quot;", "\""},
	{"&#39;", "'"}, {"&apos;", "'"}, {"&nbsp;", " "},
}

func mdDecodeEntities(t string) string {
	for _, e := range mdEntities {
		t = strings.ReplaceAll(t, e[0], e[1])
	}
	return t
}

// mdAttr 从标签里取某个属性的值
func mdAttr(tag, name string) string {
	lower := strings.ToLower(tag)
	i := strings.Index(lower, name+"=")
	if i < 0 {
		return ""
	}
	rest := tag[i+len(name)+1:]
	rest = strings.TrimLeft(rest, " ")
	if rest == "" {
		return ""
	}
	switch rest[0] {
	case '"':
		if j := strings.IndexByte(rest[1:], '"'); j >= 0 {
			return rest[1 : 1+j]
		}
	case '\'':
		if j := strings.IndexByte(rest[1:], '\''); j >= 0 {
			return rest[1 : 1+j]
		}
	default:
		j := strings.IndexAny(rest, " \t>/")
		if j > 0 {
			return rest[:j]
		}
	}
	return ""
}

func normalizeHTML(source string) string {
	if !strings.Contains(source, "<") {
		return mdDecodeEntities(source)
	}
	// 代码围栏内不碰（先抽出，处理完再放回）
	var fences []string
	source = regexp.MustCompile("(?s)"+"```[\\s\\S]*?(?:```|$)").ReplaceAllStringFunc(source, func(m string) string {
		fences = append(fences, m)
		return "\x00" + strconv.Itoa(len(fences)-1) + "\x00"
	})
	text := source

	text = mdHTMLComment.ReplaceAllString(text, "")
	text = mdHTMLBR.ReplaceAllString(text, "\n")

	// <picture> 取第一个 <source srcset>，否则退回内部 <img>
	text = mdHTMLPicture.ReplaceAllStringFunc(text, func(m string) string {
		if sm := mdHTMLSource.FindStringSubmatch(m); sm != nil {
			return "![](" + sm[1] + ")"
		}
		if im := mdHTMLImg.FindString(m); im != "" {
			src := mdAttr(im, "src")
			if src != "" {
				return "![](" + src + ")"
			}
		}
		return m
	})

	text = mdHTMLImg.ReplaceAllStringFunc(text, func(m string) string {
		src := mdAttr(m, "src")
		if src == "" {
			return ""
		}
		return "![" + mdAttr(m, "alt") + "](" + src + ")"
	})

	text = mdHTMLA.ReplaceAllString(text, "[$2]($1)")

	// <table> → Markdown 表格
	text = mdHTMLTable.ReplaceAllStringFunc(text, func(m string) string {
		inner := mdHTMLTable.FindStringSubmatch(m)[1]
		var rows []string
		for _, tr := range mdHTMLTr.FindAllStringSubmatch(inner, -1) {
			var cells []string
			for _, c := range mdHTMLCell.FindAllStringSubmatch(tr[1], -1) {
				cells = append(cells, strings.TrimSpace(mdHTMLAnyTag.ReplaceAllString(c[1], "")))
			}
			if len(cells) > 0 {
				rows = append(rows, "| "+strings.Join(cells, " | ")+" |")
			}
		}
		if len(rows) == 0 {
			return ""
		}
		// 补表头分隔行
		cols := strings.Count(rows[0], "|") - 1
		sep := "|" + strings.Repeat(" --- |", cols)
		return "\n" + rows[0] + "\n" + sep + "\n" + strings.Join(rows[1:], "\n") + "\n"
	})

	text = mdHTMLHeading.ReplaceAllString(text, "\n$1#$2\n")
	text = regexp.MustCompile(`\n([1-6])#`).ReplaceAllString(text, "\n$1#")

	text = mdHTMLQuote.ReplaceAllStringFunc(text, func(m string) string {
		inner := mdHTMLQuote.FindStringSubmatch(m)[1]
		var out []string
		for _, l := range strings.Split(strings.TrimSpace(inner), "\n") {
			if strings.TrimSpace(l) == "" {
				out = append(out, ">")
			} else {
				out = append(out, "> "+l)
			}
		}
		return "\n" + strings.Join(out, "\n") + "\n"
	})

	// <details><summary>x</summary>body</details> → 加粗摘要 + 引用正文
	text = mdHTMLDetails.ReplaceAllStringFunc(text, func(m string) string {
		inner := mdHTMLDetails.FindStringSubmatch(m)[1]
		summary := "详情"
		if sm := mdHTMLSummary.FindStringSubmatch(inner); sm != nil {
			if s := strings.TrimSpace(mdHTMLAnyTag.ReplaceAllString(sm[1], "")); s != "" {
				summary = s
			}
			inner = strings.Replace(inner, sm[0], "", 1)
		}
		var out []string
		for _, l := range strings.Split(strings.TrimSpace(inner), "\n") {
			if strings.TrimSpace(l) == "" {
				out = append(out, ">")
			} else {
				out = append(out, "> "+l)
			}
		}
		return "\n> **▸ " + summary + "**\n" + strings.Join(out, "\n") + "\n"
	})

	// 剥掉其余标签（保留文字）
	for _, t := range mdUnwrapTags {
		re := regexp.MustCompile(`(?i)</?` + t + `\b[^>]*>`)
		text = re.ReplaceAllString(text, "")
	}
	text = mdHTMLAnyTag.ReplaceAllString(text, "")
	text = mdDecodeEntities(text)

	// 放回代码围栏
	for i, f := range fences {
		text = strings.Replace(text, "\x00"+strconv.Itoa(i)+"\x00", f, 1)
	}
	return text
}

func mdParse(src string) []mdBlock {
	lines := strings.Split(normalizeHTML(src), "\n")
	var footnotes [][2]string
	var out []mdBlock
	i := 0
	for i < len(lines) {
		line := lines[i]
		trimmed := strings.TrimSpace(line)
		switch {
		case strings.TrimSpace(line) == "":
			i++

		case strings.HasPrefix(strings.TrimLeft(line, " \t"), "```"):
			lang := strings.TrimSpace(strings.TrimPrefix(strings.TrimLeft(line, " \t"), "```"))
			var body []string
			i++
			for i < len(lines) && !strings.HasPrefix(strings.TrimLeft(lines[i], " \t"), "```") {
				body = append(body, lines[i])
				i++
			}
			i++ // 跳过收尾围栏（未闭合时 consumed 到末尾）
			out = append(out, mdBlock{kind: mdFence, lang: lang, lines: body})

		case mdFootnoteRe.MatchString(trimmed):
			// [^key]: 内容（Markdown.kt:118,322-326）——收集起来，末尾统一输出
			if m := mdFootnoteRe.FindStringSubmatch(trimmed); m != nil {
				footnoteKey := m[1]
				footnoteVal := strings.TrimSpace(m[2])
				replaced := false
				for i := range footnotes {
					if footnotes[i][0] == footnoteKey {
						footnotes[i][1] = footnoteVal
						replaced = true
						break
					}
				}
				if !replaced {
					footnotes = append(footnotes, [2]string{footnoteKey, footnoteVal})
				}
			}
			i++

		case trimmed == "---" || trimmed == "***":
			out = append(out, mdBlock{kind: mdRule})
			i++

		case mdHeadingRe.MatchString(line):
			m := mdHeadingRe.FindStringSubmatch(line)
			out = append(out, mdBlock{kind: mdHeading, level: len(m[1]), lines: []string{m[2]}})
			i++

		case strings.HasPrefix(strings.TrimLeft(line, " \t"), "|"):
			var tbl []string
			for i < len(lines) && strings.HasPrefix(strings.TrimLeft(lines[i], " \t"), "|") {
				tbl = append(tbl, lines[i])
				i++
			}
			out = append(out, mdBlock{kind: mdTable, lines: tbl})

		case mdTaskRe.MatchString(line):
			var tasks []string
			for i < len(lines) && mdTaskRe.MatchString(lines[i]) {
				tasks = append(tasks, lines[i])
				i++
			}
			out = append(out, mdBlock{kind: mdTasks, lines: tasks})

		case mdBulletRe.MatchString(line):
			var items []string
			for i < len(lines) && mdBulletRe.MatchString(lines[i]) {
				items = append(items, mdBulletRe.FindStringSubmatch(lines[i])[1])
				i++
			}
			out = append(out, mdBlock{kind: mdBullets, lines: items})

		case mdOrderedRe.MatchString(line):
			var items []string
			for i < len(lines) && mdOrderedRe.MatchString(lines[i]) {
				items = append(items, mdOrderedRe.FindStringSubmatch(lines[i])[1])
				i++
			}
			out = append(out, mdBlock{kind: mdOrdered, lines: items})

		case mdQuoteRe.MatchString(line):
			var inner []string
			for i < len(lines) && mdQuoteRe.MatchString(lines[i]) {
				inner = append(inner, mdQuoteRe.FindStringSubmatch(lines[i])[1])
				i++
			}
			out = append(out, mdBlock{kind: mdQuote, lines: inner})

		default:
			// 段落吞到下一个块级起点；软换行保留，段内图抽出成独立图块（与客户端一致）
			var para []string
			for i < len(lines) &&
				strings.TrimSpace(lines[i]) != "" &&
				!mdHeadingRe.MatchString(lines[i]) &&
				!mdBulletRe.MatchString(lines[i]) &&
				!mdOrderedRe.MatchString(lines[i]) &&
				!mdQuoteRe.MatchString(lines[i]) &&
				!strings.HasPrefix(strings.TrimLeft(lines[i], " \t"), "```") &&
				!strings.HasPrefix(strings.TrimLeft(lines[i], " \t"), "|") {
				para = append(para, lines[i])
				i++
			}
			joined := strings.Join(para, "\n")
			last := 0
			for _, m := range mdImageRe.FindAllStringSubmatchIndex(joined, -1) {
				// 图前后的文字块照客户端口径修剪空白：图与文字之间不残留空行
				if gap := strings.TrimSpace(joined[last:m[0]]); gap != "" {
					out = append(out, mdBlock{kind: mdParag, lines: []string{gap}})
				}
				out = append(out, mdImageBlock(joined[m[2]:m[3]], joined[m[4]:m[5]]))
				last = m[1]
			}
			if tail := strings.TrimSpace(joined[last:]); tail != "" {
				out = append(out, mdBlock{kind: mdParag, lines: []string{tail}})
			}
		}
	}
	// 脚注统一附在末尾（Markdown.kt:448），空则不追加
	if len(footnotes) > 0 {
		out = append(out, mdBlock{kind: mdFootnotes, notes: footnotes})
	}
	return out
}

func mdImageBlock(alt, url string) mdBlock {
	return mdBlock{kind: mdImage, alt: alt, url: url}
}

func mdRenderBlock(b *strings.Builder, blk mdBlock) {
	switch blk.kind {
	case mdFence:
		b.WriteString("<pre><code")
		if blk.lang != "" {
			b.WriteString(` class="language-` + html.EscapeString(blk.lang) + `"`)
		}
		b.WriteString(">")
		// 语法高亮（Highlight.kt 同一套五类词元）；不认识的方言退回纯文本
		b.WriteString(hlRender(strings.Join(blk.lines, "\n"), blk.lang))
		b.WriteString("</code></pre>")

	case mdRule:
		b.WriteString("<hr>")

	case mdHeading:
		level := blk.level
		if level < 1 || level > 6 {
			level = 6
		}
		tag := "h" + strconv.Itoa(level)
		b.WriteString("<" + tag + ">")
		mdRenderInline(b, strings.Join(blk.lines, "\n"))
		b.WriteString("</" + tag + ">")

	case mdTable:
		cells := func(l string) []string {
			l = strings.TrimSpace(l)
			l = strings.Trim(l, "|")
			// 裸竖线切列、\| 还原成字面竖线（RE2 没有 lookbehind，手写扫描；
			// 本文件把 HTML 表格单元格里的 | 转义成 \|，不认转义就会自产自切）
			var parts []string
			var cur strings.Builder
			for i := 0; i < len(l); i++ {
				if l[i] == '\\' && i+1 < len(l) && l[i+1] == '|' {
					cur.WriteByte('|')
					i++
					continue
				}
				if l[i] == '|' {
					parts = append(parts, strings.TrimSpace(cur.String()))
					cur.Reset()
					continue
				}
				cur.WriteByte(l[i])
			}
			parts = append(parts, strings.TrimSpace(cur.String()))
			return parts
		}
		header := []string{}
		if len(blk.lines) > 0 {
			header = cells(blk.lines[0])
		}
		b.WriteString("<table><thead><tr>")
		for _, c := range header {
			b.WriteString("<th>")
			mdRenderInline(b, c)
			b.WriteString("</th>")
		}
		b.WriteString("</tr></thead><tbody>")
		for _, raw := range blk.lines[1:] {
			trimmed := strings.Map(func(r rune) rune {
				switch r {
				case '|', '-', ':', ' ':
					return -1
				}
				return r
			}, raw)
			if trimmed == "" {
				continue // 分隔行
			}
			row := cells(raw)
			// 列数对齐表头：少了补空列、多了截断（与客户端同规）
			for len(row) < len(header) {
				row = append(row, "")
			}
			if len(row) > len(header) {
				row = row[:len(header)]
			}
			b.WriteString("<tr>")
			for _, c := range row {
				b.WriteString("<td>")
				mdRenderInline(b, c)
				b.WriteString("</td>")
			}
			b.WriteString("</tr>")
		}
		b.WriteString("</tbody></table>")

	case mdTasks:
		b.WriteString("<ul>")
		for _, raw := range blk.lines {
			m := mdTaskRe.FindStringSubmatch(raw)
			checked := ""
			if m[1] == "x" || m[1] == "X" {
				checked = " checked"
			}
			b.WriteString(`<li class="task"><input type="checkbox" disabled` + checked + `>`)
			mdRenderInline(b, m[2])
			b.WriteString("</li>")
		}
		b.WriteString("</ul>")

	case mdBullets:
		b.WriteString("<ul>")
		for _, item := range blk.lines {
			b.WriteString("<li>")
			mdRenderInline(b, item)
			b.WriteString("</li>")
		}
		b.WriteString("</ul>")

	case mdOrdered:
		b.WriteString("<ol>")
		for _, item := range blk.lines {
			b.WriteString("<li>")
			mdRenderInline(b, item)
			b.WriteString("</li>")
		}
		b.WriteString("</ol>")

	case mdQuote:
		b.WriteString("<blockquote><p>")
		for i, l := range blk.lines {
			if i > 0 {
				b.WriteString("<br>")
			}
			mdRenderInline(b, l)
		}
		b.WriteString("</p></blockquote>")

	case mdFootnotes:
		// 脚注区（Markdown.kt:891-900）：每条「[key] 内容」labelSmall、InkFaint、上下 2dp
		b.WriteString(`<div class="md-footnotes">`)
		for _, n := range blk.notes {
			b.WriteString("<p>[" + html.EscapeString(n[0]) + "] " + html.EscapeString(n[1]) + "</p>")
		}
		b.WriteString(`</div>`)

	case mdImage:
		b.WriteString("<figure>")
		b.WriteString(mdWriteImg(blk.url, blk.alt))
		if blk.alt != "" {
			b.WriteString("<figcaption>" + html.EscapeString(blk.alt) + "</figcaption>")
		}
		b.WriteString("</figure>")

	case mdParag:
		b.WriteString("<p>")
		for i, l := range blk.lines {
			if i > 0 {
				b.WriteString("<br>")
			}
			// 段内软换行（图抽出后剩下的文字仍可能带 \n）按客户端语义保留为折行
			b.WriteString(strings.ReplaceAll(mdRenderInlineStr(l), "\n", "<br>"))
		}
		b.WriteString("</p>")
	}
}

// mdRenderInline 行内样式与链接。正则分组序与客户端 inlineRe 一致：
// 1 图 / 2 粗斜 / 3 粗 / 4 斜 / 5 行内码 / 6 链接 / 7 脚注 / 8 删除线 / 9 提及
func mdRenderInline(b *strings.Builder, text string) {
	b.WriteString(mdRenderInlineStr(text))
}

func mdRenderInlineStr(text string) string {
	var b strings.Builder
	last := 0
	for _, m := range mdInlineRe.FindAllStringSubmatchIndex(text, -1) {
		if m[0] > last {
			b.WriteString(html.EscapeString(text[last:m[0]]))
		}
		token := text[m[0]:m[1]]
		switch {
		case m[2] >= 0: // 图片（行内残留；块级图已在解析时抽出）
			alt, url := mdImageArgs(token)
			b.WriteString(mdWriteImg(url, alt))
		case m[4] >= 0:
			b.WriteString("<b><i>" + html.EscapeString(strings.TrimSuffix(strings.TrimPrefix(token, "***"), "***")) + "</i></b>")
		case m[6] >= 0:
			b.WriteString("<b>" + html.EscapeString(strings.TrimSuffix(strings.TrimPrefix(token, "**"), "**")) + "</b>")
		case m[8] >= 0:
			b.WriteString("<i>" + html.EscapeString(strings.TrimSuffix(strings.TrimPrefix(token, "*"), "*")) + "</i>")
		case m[10] >= 0:
			b.WriteString("<code>" + html.EscapeString(strings.TrimSuffix(strings.TrimPrefix(token, "`"), "`")) + "</code>")
		case m[12] >= 0: // 链接
			label := mdAfter(token, "[", "](")
			url := strings.TrimSuffix(mdAfterPlain(token, "]("), ")")
			if mdSafeURL(url) {
				b.WriteString(`<a href="` + html.EscapeString(url) + `" rel="noopener noreferrer">` + html.EscapeString(label) + "</a>")
			} else {
				b.WriteString(`<span style="color:var(--accent)">` + html.EscapeString(label) + "</span>")
			}
		case m[14] >= 0: // 脚注记号按原样显示（脚注清单后续里程碑）
			b.WriteString(html.EscapeString(token))
		case m[16] >= 0:
			b.WriteString("<s>" + html.EscapeString(strings.TrimSuffix(strings.TrimPrefix(token, "~~"), "~~")) + "</s>")
		case m[18] >= 0: // 提及：与服务端同尺的 find 语义，尾巴留作正文
			matched := mdMentionRe.FindString(token)
			if matched != "" {
				no := model.FormatUserNo(mdMentionID(token))
				b.WriteString(`<a class="mention" href="/u/` + no + `">` + html.EscapeString(matched) + "</a>")
				if tail := token[len(matched):]; tail != "" {
					b.WriteString(html.EscapeString(tail))
				}
			} else {
				b.WriteString(html.EscapeString(token))
			}
		}
		last = m[1]
	}
	if last < len(text) {
		b.WriteString(html.EscapeString(text[last:]))
	}
	return b.String()
}

func mdImageArgs(token string) (alt, url string) {
	m := mdImageRe.FindStringSubmatch(token)
	if m == nil {
		return "", ""
	}
	return m[1], m[2]
}

func mdWriteImg(url, alt string) string {
	if !mdSafeURL(url) {
		return html.EscapeString("![图片](" + url + ")")
	}
	// 站内上传产物折成同源相对路径。正文里存的是历史绝对地址
	// （https://arisushrine.com/uploads/...），直出会让本地或换了域名的
	// 环境去连外网，CSP 也会拦；生产同源时两者等价。
	return `<img src="` + html.EscapeString(webLocalUpload(url)) + `" alt="` + html.EscapeString(alt) + `" loading="lazy" referrerpolicy="no-referrer">`
}

// mdSafeURL 只放行 http(s) 与站内相对地址（/uploads/... 等）。
// javascript:/data:/vbscript: 一律拒绝，链接退化为带色文本、图片退化为原文。
func mdSafeURL(raw string) bool {
	u := strings.TrimSpace(raw)
	if u == "" {
		return false
	}
	lower := strings.ToLower(u)
	if strings.HasPrefix(lower, "http://") || strings.HasPrefix(lower, "https://") {
		return !strings.ContainsAny(u, "\"'<> ")
	}
	if strings.HasPrefix(u, "/") {
		return !strings.ContainsAny(u, "\"'<> ")
	}
	return false
}

func mdAfter(s, after, before string) string {
	i := strings.Index(s, after)
	if i < 0 {
		return ""
	}
	rest := s[i+len(after):]
	j := strings.Index(rest, before)
	if j < 0 {
		return rest
	}
	return rest[:j]
}

func mdAfterPlain(s, sep string) string {
	i := strings.Index(s, sep)
	if i < 0 {
		return ""
	}
	return s[i+len(sep):]
}

func mdMentionID(token string) int64 {
	m := mdMentionRe.FindStringSubmatch(token)
	if m == nil {
		return 0
	}
	n, _ := strconv.ParseInt(m[1], 10, 64)
	return n
}
