package web

import (
	"html"
	"strings"
)

// 代码块语法高亮（对齐 Markdown.kt 依赖的 Highlight.kt：236 行同一套规则）。
// 轻量：关键词 / 字符串 / 注释 / 数字 / 注解 五类认得出就够读，不追求语言规范。

type hlKind int

const (
	hlKeyword hlKind = iota
	hlString
	hlComment
	hlNumber
	hlAnnotation
)

type hlSpan struct {
	start, end int
	kind       hlKind
}

type hlSpec struct {
	keywords         map[string]bool
	lineComments     []string
	blockOpen        string
	blockClose       string
	stringDelims     string
	tripleQuotes     bool
	backtickString   bool
	annotationPrefix bool
	caseInsensitive  bool
}

func mkSet(words ...string) map[string]bool {
	m := make(map[string]bool, len(words))
	for _, w := range words {
		m[w] = true
	}
	return m
}

var cFamily = mkSet(
	"abstract", "break", "case", "catch", "class", "const", "continue", "default", "do",
	"else", "enum", "extends", "final", "finally", "for", "fun", "if", "implements",
	"import", "instanceof", "interface", "internal", "is", "native", "new", "object",
	"package", "private", "protected", "public", "return", "sealed", "static", "super",
	"suspend", "switch", "this", "throw", "throws", "try", "typealias", "val", "var",
	"when", "while", "data", "companion", "init", "override", "open",
	"true", "false", "null", "void",
	"boolean", "byte", "char", "double", "float", "int", "long", "short", "string",
)

var goLang = mkSet(
	"break", "case", "chan", "const", "continue", "default", "defer", "else",
	"fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map",
	"package", "range", "return", "select", "struct", "switch", "type", "var",
	"true", "false", "nil", "error", "string", "int", "int8", "int16", "int32",
	"int64", "uint", "uint8", "uint64", "float32", "float64", "bool", "byte", "rune",
)

var jsLang = mkSet(
	"async", "await", "break", "case", "catch", "class", "const", "continue",
	"default", "delete", "do", "else", "export", "extends", "finally", "for",
	"function", "if", "import", "in", "instanceof", "interface", "let", "new",
	"of", "return", "static", "super", "switch", "this", "throw", "try", "type",
	"typeof", "var", "void", "while", "yield", "enum",
	"true", "false", "null", "undefined",
)

var pythonLang = mkSet(
	"and", "as", "assert", "async", "await", "break", "class", "continue", "def",
	"del", "elif", "else", "except", "finally", "for", "from", "global", "if",
	"import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
	"return", "try", "while", "with", "yield",
	"True", "False", "None", "self",
)

var sqlLang = mkSet(
	"select", "from", "where", "and", "or", "not", "null", "insert", "into", "values",
	"update", "set", "delete", "create", "table", "index", "drop", "alter", "add",
	"column", "primary", "key", "foreign", "references", "join", "left", "right",
	"inner", "outer", "on", "group", "by", "order", "having", "limit", "offset",
	"as", "distinct", "count", "sum", "avg", "min", "max", "case", "when", "then",
	"else", "end", "exists", "in", "between", "like", "is", "true", "false",
	"default", "constraint", "unique", "check", "returning", "conflict", "do",
	"nothing", "now", "interval",
)

var bashLang = mkSet(
	"if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until",
	"case", "esac", "function", "return", "local", "export", "echo", "exit",
	"source", "set", "cd",
)

var hlLangs = map[string]*hlSpec{
	"kotlin":     {keywords: cFamily, lineComments: []string{"//"}, blockOpen: "/*", blockClose: "*/", backtickString: true, annotationPrefix: true},
	"java":       {keywords: cFamily, lineComments: []string{"//"}, blockOpen: "/*", blockClose: "*/", annotationPrefix: true},
	"go":         {keywords: goLang, lineComments: []string{"//"}, blockOpen: "/*", blockClose: "*/", backtickString: true},
	"javascript": {keywords: jsLang, lineComments: []string{"//"}, blockOpen: "/*", blockClose: "*/", backtickString: true},
	"c":          {keywords: cFamily, lineComments: []string{"//"}, blockOpen: "/*", blockClose: "*/"},
	"python":     {keywords: pythonLang, lineComments: []string{"#"}, tripleQuotes: true},
	"json":       {keywords: mkSet("true", "false", "null")},
	"sql":        {keywords: sqlLang, lineComments: []string{"--"}, blockOpen: "/*", blockClose: "*/", caseInsensitive: true},
	"bash":       {keywords: bashLang, lineComments: []string{"#"}},
	"yaml":       {keywords: mkSet(), lineComments: []string{"#"}},
	"xml":        {keywords: mkSet(), blockOpen: "<!--", blockClose: "-->"},
}

// hlNormalizeLang 语言别名归一（Highlight.kt:127-139 同一张表）
func hlNormalizeLang(raw string) string {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "js", "ts", "tsx", "jsx", "typescript", "mjs":
		return "javascript"
	case "golang":
		return "go"
	case "c++", "cpp", "cxx", "hpp", "cc":
		return "c"
	case "py", "python3":
		return "python"
	case "sh", "shell", "zsh":
		return "bash"
	case "yml":
		return "yaml"
	case "html", "htm", "svg":
		return "xml"
	case "kts":
		return "kotlin"
	case "postgres", "postgresql", "psql":
		return "sql"
	}
	return strings.ToLower(strings.TrimSpace(raw))
}

func hlIdentPart(b byte) bool {
	return b == '_' || (b >= '0' && b <= '9') || (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || b >= 0x80
}

func hlIsDigit(b byte) bool { return b >= '0' && b <= '9' }

// hlTokens 扫出五类词元（区间左闭右开）。字符串/注释先到先得，
// 所以「注释符号写在字符串里」不会被误判成注释。
func hlTokens(code, rawLang string) []hlSpan {
	spec := hlLangs[hlNormalizeLang(rawLang)]
	if spec == nil {
		return nil
	}
	var lowered map[string]bool
	if spec.caseInsensitive {
		lowered = make(map[string]bool, len(spec.keywords))
		for w := range spec.keywords {
			lowered[strings.ToLower(w)] = true
		}
	}
	var spans []hlSpan
	n := len(code)
	for i := 0; i < n; {
		c := code[i]
		switch {
		case hlHasPrefixAny(code, i, spec.lineComments):
			start := i
			for i < n && code[i] != '\n' {
				i++
			}
			spans = append(spans, hlSpan{start, i, hlComment})

		case spec.blockOpen != "" && strings.HasPrefix(code[i:], spec.blockOpen):
			start := i
			i += len(spec.blockOpen)
			for i < n && !strings.HasPrefix(code[i:], spec.blockClose) {
				i++
			}
			if i < n {
				i += len(spec.blockClose)
			}
			spans = append(spans, hlSpan{start, i, hlComment})

		case spec.tripleQuotes && (strings.HasPrefix(code[i:], `"""`) || strings.HasPrefix(code[i:], "'''")):
			delim := code[i : i+3]
			start := i
			i += 3
			for i < n && !strings.HasPrefix(code[i:], delim) {
				i++
			}
			if i < n {
				i += 3
			}
			spans = append(spans, hlSpan{start, i, hlString})

		case c == '"' || c == '\'' || (spec.backtickString && c == '`'):
			multiline := spec.backtickString && c == '`'
			start := i
			i++
			for i < n {
				ch := code[i]
				if ch == '\\' && i+1 < n {
					i += 2
					continue
				}
				if ch == c {
					i++
					break
				}
				if !multiline && ch == '\n' {
					break
				}
				i++
			}
			spans = append(spans, hlSpan{start, i, hlString})

		case hlIsDigit(c) && (i == 0 || !hlIdentPart(code[i-1])):
			start := i
			for i < n {
				b := code[i]
				if hlIdentPart(b) || b == '.' {
					i++
					continue
				}
				if b == '-' && i > start && (code[i-1] == 'e' || code[i-1] == 'E' || code[i-1] == 'x' || code[i-1] == 'X') {
					i++
					continue
				}
				break
			}
			spans = append(spans, hlSpan{start, i, hlNumber})

		case spec.annotationPrefix && c == '@' && i+1 < n && (hlIdentPart(code[i+1]) && !hlIsDigit(code[i+1])):
			start := i
			i++
			for i < n && hlIdentPart(code[i]) {
				i++
			}
			spans = append(spans, hlSpan{start, i, hlAnnotation})

		case hlIdentPart(c) && !hlIsDigit(c):
			start := i
			for i < n && hlIdentPart(code[i]) {
				i++
			}
			word := code[start:i]
			hit := false
			if spec.caseInsensitive {
				hit = lowered[strings.ToLower(word)]
			} else {
				hit = spec.keywords[word]
			}
			if hit {
				spans = append(spans, hlSpan{start, i, hlKeyword})
			}

		default:
			i++
		}
	}
	return spans
}

func hlHasPrefixAny(s string, i int, prefixes []string) bool {
	for _, p := range prefixes {
		if p != "" && strings.HasPrefix(s[i:], p) {
			return true
		}
	}
	return false
}

// hlRender 把代码按词元转成带 span 的 HTML（样式类 c-kw / c-str / c-cmt / c-num / c-ann）
func hlRender(code, lang string) string {
	spans := hlTokens(code, lang)
	if len(spans) == 0 {
		return html.EscapeString(code)
	}
	var b strings.Builder
	pos := 0
	cls := map[hlKind]string{
		hlKeyword: "c-kw", hlString: "c-str", hlComment: "c-cmt",
		hlNumber: "c-num", hlAnnotation: "c-ann",
	}
	for _, sp := range spans {
		if sp.start > pos {
			b.WriteString(html.EscapeString(code[pos:sp.start]))
		}
		b.WriteString(`<span class="` + cls[sp.kind] + `">` + html.EscapeString(code[sp.start:sp.end]) + `</span>`)
		pos = sp.end
	}
	if pos < len(code) {
		b.WriteString(html.EscapeString(code[pos:]))
	}
	return b.String()
}
