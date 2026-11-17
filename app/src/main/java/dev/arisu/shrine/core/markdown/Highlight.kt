package dev.arisu.shrine.core.markdown

/**
 * 轻量语法高亮：不追求贴住语言规范，关键词/字符串/注释/数字/注解五类认得出就够读。
 * 纯字符串进出（无 Compose 依赖），渲染端拿 [TokenSpan] 列表上色，这里只管切词。
 */

enum class TokenKind { KEYWORD, STRING, COMMENT, NUMBER, ANNOTATION }

data class TokenSpan(val start: Int, val end: Int, val kind: TokenKind)

internal class LangSpec(
    val keywords: Set<String>,
    val lineComments: List<String> = emptyList(),
    val blockComment: Pair<String, String>? = null,
    val stringDelims: List<String> = listOf("\"", "'"),
    /** 三引号串（python 文档串）跨行有效 */
    val tripleQuotes: Boolean = false,
    /** 反引号模板串（kotlin/js）跨行有效 */
    val backtickString: Boolean = false,
    /** @ 前缀注解（java/kotlin） */
    val annotationPrefix: Boolean = false,
    val caseInsensitive: Boolean = false,
)

private val C_FAMILY = setOf(
    "abstract", "break", "case", "catch", "class", "const", "continue", "default", "do",
    "else", "enum", "extends", "final", "finally", "for", "fun", "if", "implements",
    "import", "instanceof", "interface", "internal", "is", "native", "new", "object",
    "package", "private", "protected", "public", "return", "sealed", "static", "super",
    "suspend", "switch", "this", "throw", "throws", "try", "typealias", "val", "var",
    "when", "while", "data", "companion", "init", "override", "open",
    "true", "false", "null", "void",
    "boolean", "byte", "char", "double", "float", "int", "long", "short", "string",
)

private val GO_LANG = setOf(
    "break", "case", "chan", "const", "continue", "default", "defer", "else",
    "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map",
    "package", "range", "return", "select", "struct", "switch", "type", "var",
    "true", "false", "nil", "error", "string", "int", "int8", "int16", "int32",
    "int64", "uint", "uint8", "uint64", "float32", "float64", "bool", "byte", "rune",
)

private val JS_LANG = setOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue",
    "default", "delete", "do", "else", "export", "extends", "finally", "for",
    "function", "if", "import", "in", "instanceof", "interface", "let", "new",
    "of", "return", "static", "super", "switch", "this", "throw", "try", "type",
    "typeof", "var", "void", "while", "yield", "enum",
    "true", "false", "null", "undefined",
)

private val PYTHON_LANG = setOf(
    "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
    "del", "elif", "else", "except", "finally", "for", "from", "global", "if",
    "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
    "return", "try", "while", "with", "yield",
    "True", "False", "None", "self",
)

private val SQL_LANG = setOf(
    "select", "from", "where", "and", "or", "not", "null", "insert", "into", "values",
    "update", "set", "delete", "create", "table", "index", "drop", "alter", "add",
    "column", "primary", "key", "foreign", "references", "join", "left", "right",
    "inner", "outer", "on", "group", "by", "order", "having", "limit", "offset",
    "as", "distinct", "count", "sum", "avg", "min", "max", "case", "when", "then",
    "else", "end", "exists", "in", "between", "like", "is", "true", "false",
    "default", "constraint", "unique", "check", "returning", "conflict", "do",
    "nothing", "now", "interval",
)

private val BASH_LANG = setOf(
    "if", "then", "else", "elif", "fi", "for", "in", "do", "done", "while", "until",
    "case", "esac", "function", "return", "local", "export", "echo", "exit",
    "source", "set", "cd",
)

private val LANGS: Map<String, LangSpec> = mapOf(
    "kotlin" to LangSpec(
        C_FAMILY, lineComments = listOf("//"), blockComment = "/" + "*" to "*" + "/",
        backtickString = true, annotationPrefix = true,
    ),
    "java" to LangSpec(
        C_FAMILY, lineComments = listOf("//"), blockComment = "/" + "*" to "*" + "/",
        annotationPrefix = true,
    ),
    "go" to LangSpec(
        GO_LANG, lineComments = listOf("//"), blockComment = "/" + "*" to "*" + "/",
        backtickString = true,
    ),
    "javascript" to LangSpec(
        JS_LANG, lineComments = listOf("//"), blockComment = "/" + "*" to "*" + "/",
        backtickString = true,
    ),
    "c" to LangSpec(
        C_FAMILY, lineComments = listOf("//"), blockComment = "/" + "*" to "*" + "/",
    ),
    "python" to LangSpec(
        PYTHON_LANG, lineComments = listOf("#"), tripleQuotes = true,
    ),
    "json" to LangSpec(
        setOf("true", "false", "null"),
    ),
    "sql" to LangSpec(
        SQL_LANG, lineComments = listOf("--"), blockComment = "/" + "*" to "*" + "/",
        caseInsensitive = true,
    ),
    "bash" to LangSpec(
        BASH_LANG, lineComments = listOf("#"),
    ),
    "yaml" to LangSpec(
        emptySet(), lineComments = listOf("#"),
    ),
    "xml" to LangSpec(
        emptySet(), blockComment = "<!--" to "-->",
    ),
)

/** 语言别名归一：js/ts/sh/py/yml/html/cpp 这些常见写法都落到就近的方言上 */
internal fun normalizeLang(raw: String): String = when (raw.trim().lowercase()) {
    "js", "ts", "tsx", "jsx", "typescript", "mjs" -> "javascript"
    "golang" -> "go"
    "c++", "cpp", "cxx", "hpp", "cc" -> "c"
    "py", "python3" -> "python"
    "sh", "shell", "zsh" -> "bash"
    "yml" -> "yaml"
    "html", "htm", "svg" -> "xml"
    "kts" -> "kotlin"
    "postgres", "postgresql", "psql" -> "sql"
    else -> raw.trim().lowercase()
}

private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_'

/**
 * 扫描出一段代码里的五类词元（区间为左闭右开）。
 * 逐字符线性扫描：字符串/注释先到先得，所以「注释符号在字符串里」不会被误判。
 * 不认识的方言返回空表（按纯文本渲染）。
 */
internal fun highlightTokens(code: String, rawLang: String): List<TokenSpan> {
    val spec = LANGS[normalizeLang(rawLang)] ?: return emptyList()
    val loweredKeywords = if (spec.caseInsensitive) {
        spec.keywords.asSequence().map { it.lowercase() }.toSet()
    } else {
        emptySet()
    }
    val spans = ArrayList<TokenSpan>()
    var i = 0
    val n = code.length
    while (i < n) {
        val c = code[i]
        when {
            // 行注释：到行尾
            spec.lineComments.any { code.startsWith(it, i) } -> {
                val start = i
                while (i < n && code[i] != '\n') i++
                spans.add(TokenSpan(start, i, TokenKind.COMMENT))
            }

            // 块注释：吃到收尾串（跨行有效）
            spec.blockComment != null && code.startsWith(spec.blockComment.first, i) -> {
                val start = i
                val close = spec.blockComment.second
                i += spec.blockComment.first.length
                while (i < n && !code.startsWith(close, i)) i++
                if (i < n) i += close.length
                spans.add(TokenSpan(start, i, TokenKind.COMMENT))
            }

            // python 三引号串
            spec.tripleQuotes && (code.startsWith("\"\"\"", i) || code.startsWith("'''", i)) -> {
                val delim = code.substring(i, i + 3)
                val start = i
                i += 3
                while (i < n && !code.startsWith(delim, i)) i++
                if (i < n) i += 3
                spans.add(TokenSpan(start, i, TokenKind.STRING))
            }

            // 字符串：单行串不吃换行（三引号/模板串例外），反斜杠转义跳一格
            c == '"' || c == '\'' || (spec.backtickString && c == '`') -> {
                val multiline = spec.backtickString && c == '`'
                val start = i
                i++
                while (i < n) {
                    val ch = code[i]
                    if (ch == '\\' && i + 1 < n) {
                        i += 2
                        continue
                    }
                    if (ch == c) {
                        i++
                        break
                    }
                    if (!multiline && ch == '\n') break
                    i++
                }
                spans.add(TokenSpan(start, i, TokenKind.STRING))
            }

            // 数字：十六进制/小数/下划线分隔都整体吃
            c.isDigit() && (i == 0 || !isIdentPart(code[i - 1])) -> {
                val start = i
                while (i < n && (code[i].isLetterOrDigit() || code[i] == '_' ||
                        code[i] == '.' || code[i] == '-' && i > start && code[i - 1] in "eExX")
                ) i++
                spans.add(TokenSpan(start, i, TokenKind.NUMBER))
            }

            // @注解（java/kotlin）
            spec.annotationPrefix && c == '@' && i + 1 < n && (code[i + 1].isLetter() || code[i + 1] == '_') -> {
                val start = i
                i++
                while (i < n && isIdentPart(code[i])) i++
                spans.add(TokenSpan(start, i, TokenKind.ANNOTATION))
            }

            // 标识符：命中语言词表才算关键词
            c.isLetter() || c == '_' -> {
                val start = i
                while (i < n && isIdentPart(code[i])) i++
                val word = code.substring(start, i)
                val hit = if (spec.caseInsensitive) {
                    word.lowercase() in loweredKeywords
                } else {
                    word in spec.keywords
                }
                if (hit) spans.add(TokenSpan(start, i, TokenKind.KEYWORD))
            }

            else -> i++
        }
    }
    return spans
}
