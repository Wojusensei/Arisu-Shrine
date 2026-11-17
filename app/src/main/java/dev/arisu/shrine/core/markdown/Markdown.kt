package dev.arisu.shrine.core.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.design.ReadingScale
import dev.arisu.shrine.core.design.Celadon
import dev.arisu.shrine.core.design.Gold
import dev.arisu.shrine.core.design.Indigo
import dev.arisu.shrine.core.design.OnAccent
import dev.arisu.shrine.core.design.Plum
import dev.arisu.shrine.core.design.InkFaint
import dev.arisu.shrine.core.design.PlaceholderTeal
import dev.arisu.shrine.core.design.Vermilion

/* ══════════════════════════════════════════════════════════════════
 * 块级解析
 * ══════════════════════════════════════════════════════════════════ */

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String, val centered: Boolean = false) : MdBlock
    data class Paragraph(val text: String, val centered: Boolean = false) : MdBlock
    data class Bullet(val items: List<String>) : MdBlock
    data class Ordered(val items: List<String>) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    /** link 非空是 [![alt](图)](地址) 徽章链接：点图开外链而不是开图 */
    data class Image(val alt: String, val url: String, val link: String? = null, val centered: Boolean = false) : MdBlock
    /** 同一段里只隔空白相连的多张图，GitHub 会排成一行（徽章墙的常态） */
    data class ImageRow(val items: List<Image>, val centered: Boolean = false) : MdBlock
    data class Tasks(val items: List<Pair<Boolean, String>>) : MdBlock
    data class Footnotes(val notes: Map<String, String>) : MdBlock
    data object Divider : MdBlock
}

/**
 * 表情包的 alt 标记：`![表情](url)` 按表情的小尺寸行内显示（见 [MdBlock.Image] 的渲染分支），
 * 与正文大图区别开——发帖页插表情、以后别处插表情都认这一个词。
 */
const val MARKDOWN_STICKER_ALT = "表情"

/** 徽章链接在前：`[![alt](图)](地址)`；裸图片在后：`![alt](图)` */
private val imageTokenRe = Regex(
    """\[!\[([^\]]*)]\(([^)\s]+)\)]\(([^)\s]+)\)""" +
        """|!\[([^\]]*)]\(([^)\s]+)\)""",
)

private fun MutableList<MdBlock.Image>.toImageBlock(centered: Boolean = false): MdBlock =
    if (size == 1) this[0] else MdBlock.ImageRow(toList(), centered)

/** MatchResult → 图片块：第 3 组（徽章链接地址）参与匹配即视为徽章 */
private fun MatchResult.toImage(centered: Boolean = false): MdBlock.Image =
    if (groups[3] != null) MdBlock.Image(groupValues[1], groupValues[2], link = groupValues[3], centered = centered)
    else MdBlock.Image(groupValues[4], groupValues[5], centered = centered)

private val headingRe = Regex("""^(#{1,6})\s+(.*)$""")
private val bulletRe = Regex("""^\s*[-*+]\s+(.*)$""")
private val orderedRe = Regex("""^\s*\d+[.)]\s+(.*)$""")
private val quoteRe = Regex("^\\s*>\\s?(.*)$")
private val taskRe = Regex("^\\s*[-*+]\\s+\\[([ xX])\\]\\s+(.*)$")
private val footnoteRe = Regex("^\\[\\^(\\w+)\\]:\\s*(.*)$")

/* ══════════════════════════════════════════════════════════════════
 * HTML 预处理：GitHub 式 README 里常见的 HTML 写法
 * （注释、<br>、<img>/<picture>、<table>、<details>、格式标签）
 * 在进入块级解析前转换成等价 Markdown；不支持的标签剥壳留文本。
 * ══════════════════════════════════════════════════════════════════ */

private val htmlComment = Regex("""<!--[\s\S]*?-->""")
private val htmlDetails = Regex("""<details\b[^>]*>([\s\S]*?)</details\s*>""", RegexOption.IGNORE_CASE)
private val htmlSummary = Regex("""<summary\b[^>]*>([\s\S]*?)</summary\s*>""", RegexOption.IGNORE_CASE)
private val htmlPicture = Regex("""<picture\b[^>]*>([\s\S]*?)</picture\s*>""", RegexOption.IGNORE_CASE)
private val htmlSource = Regex("""<source\b[^>]*srcset\s*=\s*"([^"]*)"[^>]*>""", RegexOption.IGNORE_CASE)
private val htmlImg = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val imgAttr = Regex("""(src|alt)\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
private val htmlATag = Regex("""<a\b[^>]*href\s*=\s*"([^"]*)"[^>]*>([\s\S]*?)</a\s*>""", RegexOption.IGNORE_CASE)
private val htmlTable = Regex("""<table\b[^>]*>([\s\S]*?)</table\s*>""", RegexOption.IGNORE_CASE)
private val htmlTr = Regex("""<tr\b[^>]*>([\s\S]*?)</tr\s*>""", RegexOption.IGNORE_CASE)
private val htmlCell = Regex("""<t[dh]\b[^>]*>([\s\S]*?)</t[dh]\s*>""", RegexOption.IGNORE_CASE)
private val htmlHeading = Regex("""<h([1-6])\b[^>]*>([\s\S]*?)</h[1-6]\s*>""", RegexOption.IGNORE_CASE)
private val htmlBlockquote = Regex("""<blockquote\b[^>]*>([\s\S]*?)</blockquote\s*>""", RegexOption.IGNORE_CASE)
private val unwrapTags = listOf("sub", "sup", "kbd", "mark", "u", "small", "center", "font", "span", "p", "div", "section", "article", "figure", "figcaption", "summary")
private val anyTag = Regex("""</?[a-zA-Z][^>]*>""")
private val entities = listOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&nbsp;" to " ")

private fun decodeEntities(t: String): String {
    var out = t
    entities.forEach { (k, v) -> out = out.replace(k, v) }
    return out
}

internal fun normalizeHtml(source: String): String {
    // 纯 Markdown 也可能带实体（&nbsp; 是 GitHub 徽章墙的标准间隔），不能跳过解码
    if (!source.contains('<')) return decodeEntities(source)
    val fences = mutableListOf<String>()
    var text = source.replace(Regex("""```[\s\S]*?(?:```|$)""")) { m ->
        fences += m.value
        "\u0000${fences.lastIndex}\u0000"
    }
    text = text.replace(htmlComment, "")
    text = text.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")

    // 居中容器（<div align="center">/<center>）先换成成对标记：里面的块按居中对齐渲染，
    // 其余标签照旧拆掉。GitHub 主页 README 几乎都用它包 logo 与站名，不认就整段左对齐
    text = text.replace(centerBlockRe) { m ->
        val inner = m.groupValues[2].ifBlank { m.groupValues[3] }
        "\n$CENTER_OPEN\n$inner\n$CENTER_CLOSE\n"
    }

    // <details>：摘要加粗 + 正文块引用，正文递归转换
    text = text.replace(htmlDetails) { m ->
        val body = m.groupValues[1]
        val sm = htmlSummary.find(body)
        val summary = sm?.groupValues?.get(1)?.trim().orEmpty().ifBlank { "详情" }
        val inner = sm?.let { body.removeRange(it.range) } ?: body
        val innerMd = normalizeHtml(inner).trim().lines().joinToString("\n") { if (it.isBlank()) ">" else "> $it" }
        "\n> **▸ $summary**\n$innerMd\n"
    }
    // <picture>：优先 <img src>，否则取第一个 <source srcset>
    // 图片就地内联、不加换行：`<a href><img></a>` 才能嵌成 [![](图)](地址)，
    // 一行多个徽章 <img> 也留在同一行，后面按“只隔空白就连排”成组
    text = text.replace(htmlPicture) { m ->
        val inner = m.groupValues[1]
        val img = htmlImg.find(inner)?.value
        val src = img?.let { imgAttr.find(it)?.groupValues?.getOrNull(2) }
            ?: htmlSource.find(inner)?.groupValues?.get(1)?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
        val alt = img?.let { imgAttr.findAll(it).firstOrNull { a -> a.groupValues[1].equals("alt", true) }?.groupValues?.get(2) } ?: ""
        if (src.isNullOrBlank()) "" else "![${alt}](${src})"
    }
    text = text.replace(htmlImg) { m ->
        val attrs = imgAttr.findAll(m.value)
        val src = attrs.firstOrNull { it.groupValues[1].equals("src", true) }?.groupValues?.get(2)
        val alt = attrs.firstOrNull { it.groupValues[1].equals("alt", true) }?.groupValues?.get(2) ?: ""
        if (src.isNullOrBlank()) "" else "![${alt}](${src})"
    }
    // <a href> → [text](href)
    text = text.replace(htmlATag) { m ->
        val href = m.groupValues[1].trim()
        val label = normalizeHtml(m.groupValues[2]).trim()
        if (href.isBlank()) label else "[${label}](${href})"
    }
    // <table> → Markdown 表格
    text = text.replace(htmlTable) { m ->
        val rows = htmlTr.findAll(m.groupValues[1]).mapNotNull { tr ->
            htmlCell.findAll(tr.groupValues[1]).map { c ->
                normalizeHtml(c.groupValues[1]).trim().replace("\n", " ").replace("|", "\\|")
            }.toList().takeIf { it.isNotEmpty() }
        }.toList()
        // 拼行时不把字符串写进模板串里：导出开源仓库时注释剥离器会把嵌套引号认成串尾，
        // 后面的 // 被当成行尾注释，整块会被吃掉（表格转换曾因此在公仓里整段消失）
        val cells = rows.map { row -> row.joinToString(" | ") }
        if (cells.size < 2) ""
        else "\n" + cells.joinToString("\n") { "| " + it + " |" } +
            "\n| " + rows[0].joinToString(" | ") { "---" } + " |\n"
    }
    // <h1-6> → #
    text = text.replace(htmlHeading) { m ->
        "\n" + "#".repeat(m.groupValues[1].toInt()) + " " + m.groupValues[2].trim() + "\n"
    }
    // <blockquote> → >
    text = text.replace(htmlBlockquote) { m ->
        val inner = normalizeHtml(m.groupValues[1]).trim()
        "\n" + inner.lines().joinToString("\n") { if (it.isBlank()) ">" else "> $it" } + "\n"
    }
    // 格式标签成对替换
    text = text.replace(Regex("""</?(?:b|strong)\s*>""", RegexOption.IGNORE_CASE), "**")
        .replace(Regex("""</?(?:i|em)\s*>""", RegexOption.IGNORE_CASE), "*")
        .replace(Regex("""</?(?:s|del|strike)\s*>""", RegexOption.IGNORE_CASE), "~~")
        .replace(Regex("""<code\s*>""", RegexOption.IGNORE_CASE), "`")
        .replace(Regex("""</code\s*>""", RegexOption.IGNORE_CASE), "`")
    // 不支持的标签剥壳留文本；<hr> → ---
    text = text.replace(Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE), "\n---\n")
    unwrapTags.forEach { tag ->
        text = text.replace(Regex("""</?$tag\b[^>]*>""", RegexOption.IGNORE_CASE), "")
    }
    text = text.replace(anyTag, "")
    text = decodeEntities(text)
    // 代码块还原
    fences.forEachIndexed { idx, code -> text = text.replace("\u0000$idx\u0000", code) }
    return text.replace(Regex("""\n{3,}"""), "\n\n")
}

private val plainCodeFence = Regex("```[\\s\\S]*?(?:```|$)")
private val plainImage = Regex("!\\[[^\\]]*]\\(([^)\\s]+)\\)")
private val plainLink = Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)")
private val plainPrefix = Regex("^[#>*\\-+]+\\s*", RegexOption.MULTILINE)
private val plainTableRow = Regex("^\\s*\\|.*\\|\\s*$", RegexOption.MULTILINE)
private val plainEmphasis = Regex("[*_~`]+")
private val plainSpaces = Regex("\\s+")

private val plainCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 64
}

/** 抽正文纯文本：卡片摘要用，图片以「[图片]」占位。结果按原文缓存，列表滚动不重跑正则。 */
fun markdownToPlain(source: String): String {
    synchronized(plainCache) { plainCache[source]?.let { return it } }
    var t = normalizeHtml(source)
    t = t.replace(plainCodeFence, " ")
    t = t.replace(plainImage, " [图片] ")
    t = t.replace(plainLink, "$1")
    t = t.replace(plainPrefix, "")
    t = t.replace(plainTableRow, "")
    t = t.replace(plainEmphasis, "")
    t = t.replace(plainSpaces, " ").trim()
    synchronized(plainCache) { plainCache[source] = t }
    return t
}

/**
 * 解析 Markdown 为块列表。
 * profileMode（个人主页/编辑预览）：对齐 GitHub 的 README 观感——
 * 只隔空白（含软换行）相连的图片排成一行，`[![](图)](地址)` 徽章可点开外链。
 */
private val markdownCache = object : LinkedHashMap<String, List<MdBlock>>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MdBlock>>) = size > 32
}

internal fun parseMarkdown(source: String, profileMode: Boolean = false): List<MdBlock> {
    // 长帖/长评论的块解析是正则流水线；滚出屏幕再回来不重跑
    val key = if (profileMode) "p\u0000$source" else "n\u0000$source"
    synchronized(markdownCache) { markdownCache.get(key) }?.let { return it }
    val parsed = parseMarkdownUncached(source, profileMode)
    synchronized(markdownCache) { markdownCache.put(key, parsed) }
    return parsed
}

private fun parseMarkdownUncached(source: String, profileMode: Boolean = false): List<MdBlock> {
    val lines = normalizeHtml(source).replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0
    // GitHub 主页 README 爱用 <div align="center"> 包住 logo 与标题：块要按居中渲染
    var inCenter = false
    val footnotes = linkedMapOf<String, String>()

    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()

        when {
            line == CENTER_OPEN -> { inCenter = true; i++ }

            line == CENTER_CLOSE -> { inCenter = false; i++ }

            line.isBlank() -> i++

            line.startsWith("```") -> {
                val lang = line.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                i++ // 跳过结束的 ```
                blocks += MdBlock.Code(lang, code.toString())
            }

            line.trim() == "---" || line.trim() == "***" -> {
                blocks += MdBlock.Divider
                i++
            }

            footnoteRe.matches(line.trim()) -> {
                val m = footnoteRe.find(line.trim())!!
                footnotes[m.groupValues[1]] = m.groupValues[2]
                i++
            }

            headingRe.matches(line) -> {
                val m = headingRe.find(line)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim(), centered = inCenter)
                i++
            }

            imageTokenRe.matchEntire(line.trim()) != null -> {
                val first = imageTokenRe.matchEntire(line.trim())!!.toImage(inCenter)
                if (profileMode) {
                    // GitHub 把段内软换行当空格：连续的整行图片收敛为一行徽章
                    val group = mutableListOf(first)
                    i++
                    while (i < lines.size) {
                        val next = imageTokenRe.matchEntire(lines[i].trim()) ?: break
                        group += next.toImage(inCenter)
                        i++
                    }
                    blocks += if (group.size == 1) group[0] else MdBlock.ImageRow(group, centered = inCenter)
                } else {
                    blocks += first
                    i++
                }
            }

            line.trimStart().startsWith("|") -> {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    tableLines += lines[i]
                    i++
                }
                blocks += buildTable(tableLines)
            }

            taskRe.matches(line) -> {
                val tasks = mutableListOf<Pair<Boolean, String>>()
                while (i < lines.size && taskRe.matches(lines[i])) {
                    val m = taskRe.find(lines[i])!!
                    tasks += (m.groupValues[1].lowercase() == "x") to m.groupValues[2]
                    i++
                }
                blocks += MdBlock.Tasks(tasks)
            }

            bulletRe.matches(line) -> {
                val items = mutableListOf<String>()
                while (i < lines.size && bulletRe.matches(lines[i])) {
                    items += bulletRe.find(lines[i])!!.groupValues[1]
                    i++
                }
                blocks += MdBlock.Bullet(items)
            }

            orderedRe.matches(line) -> {
                val items = mutableListOf<String>()
                while (i < lines.size && orderedRe.matches(lines[i])) {
                    items += orderedRe.find(lines[i])!!.groupValues[1]
                    i++
                }
                blocks += MdBlock.Ordered(items)
            }

            quoteRe.matches(line) -> {
                val parts = mutableListOf<String>()
                while (i < lines.size && quoteRe.matches(lines[i])) {
                    parts += quoteRe.find(lines[i])!!.groupValues[1]
                    i++
                }
                blocks += MdBlock.Quote(parts.joinToString("\n"))
            }

            else -> {
                val para = mutableListOf<String>()
                while (i < lines.size &&
                    lines[i].isNotBlank() &&
                    !headingRe.matches(lines[i]) &&
                    !bulletRe.matches(lines[i]) &&
                    !orderedRe.matches(lines[i]) &&
                    !quoteRe.matches(lines[i]) &&
                    !lines[i].trimStart().startsWith("```") &&
                    !lines[i].trimStart().startsWith("|")
                ) {
                    para += lines[i]
                    i++
                }
                if (para.isNotEmpty()) {
                    // 段落里夹着图片时拆开：文字归段落，图片单独成块/成行
                    val joined = para.joinToString("\n")
                    if (profileMode) {
                        // GitHub 语义：图与图之间只隔空白（含软换行）就连成一行徽章
                        var last = 0
                        var row = mutableListOf<MdBlock.Image>()
                        for (m in imageTokenRe.findAll(joined)) {
                            val gap = joined.substring(last, m.range.first)
                            if (gap.isNotBlank()) {
                                if (row.isNotEmpty()) { blocks += row.toImageBlock(inCenter); row = mutableListOf() }
                                blocks += MdBlock.Paragraph(gap.trim(), centered = inCenter)
                            }
                            row += m.toImage(inCenter)
                            last = m.range.last + 1
                        }
                        if (row.isNotEmpty()) blocks += row.toImageBlock(inCenter)
                        val tail = joined.substring(last).trim()
                        if (tail.isNotEmpty()) blocks += MdBlock.Paragraph(tail, centered = inCenter)
                    } else {
                        var last = 0
                        for (m in imageTokenRe.findAll(joined)) {
                            if (m.range.first > last) {
                                val before = joined.substring(last, m.range.first).trim()
                                if (before.isNotEmpty()) blocks += MdBlock.Paragraph(before, centered = inCenter)
                            }
                            blocks += m.toImage(inCenter)
                            last = m.range.last + 1
                        }
                        val tail = joined.substring(last).trim()
                        if (tail.isNotEmpty()) blocks += MdBlock.Paragraph(tail, centered = inCenter)
                    }
                }
            }
        }
    }
    if (footnotes.isNotEmpty()) blocks += MdBlock.Footnotes(footnotes)
    return blocks
}

private fun buildTable(lines: List<String>): MdBlock.Table {
    // GitHub 语义：\| 是字面竖线不切列——本文件把 HTML 表格单元格里的 | 转义成 \|，
    // 不认转义就会把自己产出的单元格再切碎
    fun cells(l: String) = l.trim().trim('|')
        .split(Regex("""(?<!\\)\|"""))
        .map { it.trim().replace("\\|", "|") }
    val header = if (lines.isNotEmpty()) cells(lines[0]) else emptyList()
    val body = lines.drop(1)
        .filterNot { it.replace("|", "").replace("-", "").replace(":", "").isBlank() }
        .map { cells(it) }
        .map { row ->
            // 列数对齐表头：少了补空列、多了截断，否则每行 weight(1f) 平分宽度时整表错位
            when {
                header.isEmpty() -> row
                row.size < header.size -> row + List(header.size - row.size) { "" }
                else -> row.take(header.size)
            }
        }
    return MdBlock.Table(header, body)
}

/* ══════════════════════════════════════════════════════════════════
 * 行内解析
 * ══════════════════════════════════════════════════════════════════ */

private val inlineRe = Regex(
    """(!\[[^\]]*]\([^)\s]+\))""" +
        """|(\*\*\*[^*]+\*\*\*)""" +
        """|(\*\*[^*]+\*\*)""" +
        """|(\*[^*\n]+\*)""" +
        """|(`[^`]+`)""" +
        """|(\[[^\]]+]\([^)\s]+\))""" +
        """|(\[\^\w+\])""" +
        """|(~~[^~]+~~)""" +
        """|(@[\w\u4e00-\u9fa5·]+)"""
)

// 与服务端同一把尺：@User_编号（大小写不敏感、容忍前导零）才是可解析的提及
internal val mentionIdRe = Regex("""@user_0*(\d{1,12})""", RegexOption.IGNORE_CASE)

/**
 * 只放行 http(s)：ACTION_VIEW 对任意 scheme 都可能被外层应用当成输入
 * （javascript:/intent: 等），徽章外链与下载地址同一把锁。
 */
internal fun openMarkdownLink(context: android.content.Context, url: String) {
    val scheme = runCatching { android.net.Uri.parse(url).scheme?.lowercase() }.getOrNull()
    if (scheme != "http" && scheme != "https") return
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 把一行 Markdown 文本转成带样式的 AnnotatedString。 */
/**
 * 代码块上色：五类词元映射到神社色板——Vermilion 关键词、Celadon 字符串、
 * InkFaint 注释、Gold 数字、Indigo 注解；不认识的方言词元为空，按纯文本出
 */
@Composable
private fun codeAnnotated(code: String, lang: String): AnnotatedString {
    val keyword = Vermilion
    val string = Celadon
    val comment = InkFaint
    val number = Gold
    val annotation = Indigo
    return remember(code, lang, keyword, string, comment, number, annotation) {
        buildAnnotatedString {
            append(code)
            for (t in highlightTokens(code, lang)) {
                val color = when (t.kind) {
                    TokenKind.KEYWORD -> keyword
                    TokenKind.STRING -> string
                    TokenKind.COMMENT -> comment
                    TokenKind.NUMBER -> number
                    TokenKind.ANNOTATION -> annotation
                }
                addStyle(SpanStyle(color = color), t.start, t.end)
            }
        }
    }
}

@Composable
internal fun renderInline(
    text: String,
    baseColor: Color,
    codeBg: Color,
    codeColor: Color,
    onMention: ((String) -> Unit)? = null,
    mentionNames: Map<String, String>? = null,
): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, baseColor, codeBg, codeColor, linkColor, onMention, mentionNames) {
        buildAnnotatedString {
            var last = 0
            for (m in inlineRe.findAll(text)) {
                if (m.range.first > last) append(text.substring(last, m.range.first))
                val token = m.value
                when {
                    token.startsWith("***") -> withStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    ) { append(token.removeSurrounding("***")) }

                    token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(token.removeSurrounding("**"))
                    }

                    token.startsWith("[^") -> withStyle(
                        SpanStyle(
                            color = linkColor,
                            fontSize = 10.sp,
                        )
                    ) { append(token) }

                    token.startsWith("~~") -> withStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough, color = InkFaint)
                    ) { append(token.removeSurrounding("~~")) }

                    token.startsWith("`") -> withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            background = codeBg,
                            color = codeColor,
                        )
                    ) { append(token.removeSurrounding("`")) }

                    token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.removeSurrounding("*"))
                    }

                    token.startsWith("@") -> {
                        // @User_编号 可点跳社员主页；其余 @词只高亮（服务端只解析编号形态）。
                        // 帖子/评论 DTO 带了提及者昵称时，展示用昵称顶替编号（原文不动，编辑仍是编号）。
                        // 与服务端同尺的 find 语义：@User_123abc 服务端按编号 123 记通知，
                        // 这里同样只把 @User_123 做成可点提及，尾巴 abc 留作正文
                        val m = if (onMention != null || mentionNames != null) mentionIdRe.find(token) else null
                        if (m != null) {
                            val no = "User_" + "%06d".format(m.groupValues[1].toLong())
                            val matched = m.value
                            val shown = mentionNames?.get(no)?.let { "@$it" } ?: matched
                            if (onMention != null) {
                                withLink(
                                    LinkAnnotation.Clickable(
                                        no,
                                        TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)),
                                    ) { onMention(no) },
                                ) {
                                    append(shown)
                                }
                            } else {
                                withStyle(
                                    SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)
                                ) { append(shown) }
                            }
                            val tail = token.substring(matched.length)
                            if (tail.isNotEmpty()) append(tail)
                        } else {
                            withStyle(
                                SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)
                            ) { append(token) }
                        }
                    }

                    token.startsWith("[") -> {
                        // url 从 ]( 分隔符后取到 token 末尾再剥右括号：label/url 里的
                        // 括号（[看(注)]、维基式 A_(b)）不再被第一个括号截断
                        val label = token.substringAfter('[').substringBefore("](")
                        val url = token.substringAfter("](").removeSuffix(")")
                        // 只放行 http(s)：怪 scheme（ftp:/自定义协议）交给系统处理
                        // 在部分设备上直接抛 ActivityNotFoundException
                        val safe = url.startsWith("http://") || url.startsWith("https://")
                        if (safe) {
                            withLink(LinkAnnotation.Url(url)) {
                                withStyle(
                                    SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    )
                                ) { append(label) }
                            }
                        } else {
                            withStyle(SpanStyle(color = linkColor)) { append(label) }
                        }
                    }

                    else -> append(token)
                }
                last = m.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 渲染
 * ══════════════════════════════════════════════════════════════════ */

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onImageClick: ((String) -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,
    /** 个人主页 README 观感：图片按真实尺寸展示、相邻图连排（帖子正文保持原样） */
    naturalImages: Boolean = false,
    /** @User_编号 到昵称的映射：有它时提及展示昵称而非编号 */
    mentionNames: Map<String, String>? = null,
) {
    val blocks = remember(markdown, naturalImages) { parseMarkdown(markdown, naturalImages) }
    // 正文字号只作用于内容块；短块用均衡换行，避免末行只剩一个字
    val bodyStyle = ReadingScale.of(style)
    fun readingStyle(text: String): TextStyle =
        if (text.length <= 60) ReadingScale.of(style, balanced = true) else bodyStyle
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val context = androidx.compose.ui.platform.LocalContext.current

    fun onImageTap(block: MdBlock.Image) {
        val link = block.link
        if (link != null) openMarkdownLink(context, link) else onImageClick?.invoke(block.url)
    }

    SelectionContainer {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MdBlock.Heading -> {
                        val (baseSize, weight) = when (block.level) {
                            1 -> 22.sp to FontWeight.Bold
                            2 -> 18.sp to FontWeight.Bold
                            3 -> 16.sp to FontWeight.SemiBold
                            else -> 15.sp to FontWeight.SemiBold
                        }
                        val size = ReadingScale.sp(baseSize)
                        CenteredBlock(block.centered) {
                        Column {
                            if (block.level <= 2) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(width = 3.dp, height = size.value.dp * 1.1f)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = renderInline(block.text, color, codeBg, codeColor, onMentionClick, mentionNames),
                                        style = style.copy(
                                            fontSize = size,
                                            fontWeight = weight,
                                            lineHeight = size * 1.35f,
                                            lineBreak = LineBreak.Heading,
                                        ),
                                        color = color,
                                    )
                                }
                            } else {
                                Text(
                                    text = renderInline(block.text, color, codeBg, codeColor, onMentionClick, mentionNames),
                                    style = style.copy(
                                        fontSize = size,
                                        fontWeight = weight,
                                        lineHeight = size * 1.35f,
                                        lineBreak = LineBreak.Heading,
                                    ),
                                    color = color,
                                )
                            }
                        }
                        }
                    }

                    is MdBlock.Paragraph -> CenteredBlock(block.centered) {
                        Text(
                            text = renderInline(block.text, color, codeBg, codeColor, onMentionClick, mentionNames),
                            style = readingStyle(block.text),
                            color = color,
                            textAlign = if (block.centered) androidx.compose.ui.text.style.TextAlign.Center
                            else androidx.compose.ui.text.style.TextAlign.Start,
                            modifier = if (block.centered) Modifier.fillMaxWidth() else Modifier,
                        )
                    }

                    is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        block.items.forEach { item ->
                            Row {
                                Box(
                                    Modifier
                                        .padding(top = bodyStyle.fontSize.value.dp * 0.62f)
                                        .size(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    text = renderInline(item, color, codeBg, codeColor, onMentionClick, mentionNames),
                                    style = readingStyle(item),
                                    color = color,
                                )
                            }
                        }
                    }

                    is MdBlock.Ordered -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row {
                                Text(
                                    text = "${index + 1}.",
                                    style = bodyStyle.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = renderInline(item, color, codeBg, codeColor, onMentionClick, mentionNames),
                                    style = readingStyle(item),
                                    color = color,
                                )
                            }
                        }
                    }

                    is MdBlock.Code -> {
                        var copied by remember { mutableStateOf(false) }
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        LaunchedEffect(copied) {
                            if (copied) {
                                kotlinx.coroutines.delay(1200)
                                copied = false
                            }
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (block.lang.isNotBlank()) {
                                    Text(
                                        text = block.lang,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(start = 12.dp, top = 4.dp)
                                            .weight(1f),
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(block.code))
                                        copied = true
                                    },
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Icon(
                                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                        contentDescription = "复制代码",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                            Text(
                                text = codeAnnotated(block.code, block.lang),
                                style = style.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = ReadingScale.sp(12.sp),
                                    lineHeight = ReadingScale.sp(18.sp),
                                    lineBreak = LineBreak.Simple,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }

                    is MdBlock.Quote -> Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Box(
                            Modifier
                                .width(2.5.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        )
                        Text(
                            text = renderInline(block.text, color, codeBg, codeColor, onMentionClick, mentionNames),
                            style = readingStyle(block.text).copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                        )
                    }

                    is MdBlock.Table -> MarkdownTable(block, bodyStyle, color, onMentionClick, mentionNames)

                    is MdBlock.Tasks -> Column(Modifier.fillMaxWidth()) {
                        block.items.forEach { (done, text) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    Modifier
                                        .padding(top = 3.dp)
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    text = renderInline(
                                        text, color, codeBg, codeColor, onMentionClick, mentionNames,
                                    ),
                                    style = readingStyle(text),
                                    color = if (done) InkFaint else color,
                                )
                            }
                        }
                    }

                    is MdBlock.Footnotes -> Column(Modifier.fillMaxWidth()) {
                        block.notes.forEach { (k, v) ->
                            Text(
                                text = "[$k] $v",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkFaint,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }

                    is MdBlock.Image -> CenteredBlock(block.centered) {
                        if (block.alt == MARKDOWN_STICKER_ALT) {
                        StickerInlineImage(block.alt, block.url) { onImageTap(block) }
                    } else {
                        MarkdownImagePlaceholder(
                            alt = block.alt,
                            url = block.url,
                            natural = naturalImages,
                            onClick = { onImageTap(block) },
                        )
                        }
                    }

                    is MdBlock.ImageRow -> CenteredBlock(block.centered) {
                        if (naturalImages) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            block.items.forEach { img ->
                                MarkdownImagePlaceholder(
                                    alt = img.alt,
                                    url = img.url,
                                    natural = true,
                                    // 行内徽章不抢整行宽：加载占位按内容收缩
                                    fillWidth = false,
                                    onClick = { onImageTap(img) },
                                )
                            }
                        }
                    } else {
                        // 非 profile 模式不会产生 ImageRow；兜底按单图平铺
                        block.items.forEach { img ->
                            MarkdownImagePlaceholder(alt = img.alt, url = img.url, onClick = { onImageTap(img) })
                        }
                    }
                    }

                    MdBlock.Divider -> Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }
            }
        }
    }
}

/** 居中容器里的块：整块水平居中（GitHub 上 <div align="center"> 的观感） */
@Composable
private fun CenteredBlock(centered: Boolean, content: @Composable () -> Unit) {
    if (!centered) {
        content()
        return
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun MarkdownTable(
    block: MdBlock.Table,
    style: TextStyle,
    color: Color,
    onMentionClick: ((String) -> Unit)? = null,
    mentionNames: Map<String, String>? = null,
) {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
    ) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            block.header.forEach { cell ->
                Text(
                    text = renderInline(cell, color, codeBg, codeColor, onMentionClick, mentionNames),
                    style = style.copy(fontWeight = FontWeight.SemiBold, fontSize = ReadingScale.sp(13.sp)),
                    color = color,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        block.rows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )
            }
            Row {
                row.forEach { cell ->
                    Text(
                        text = renderInline(cell, color, codeBg, codeColor, onMentionClick, mentionNames),
                        style = style.copy(fontSize = ReadingScale.sp(13.sp)),
                        color = color,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Markdown 内嵌图片。默认（帖子正文）：定高裁切占位，保证列表排版稳定；
 * natural（个人主页 README）：按 GitHub 规则展示——图片以固有像素值当 CSS px
 * 显示（SVG 的矢量单位正好落进同一把尺），超过容器宽才等比缩回，加载中/失败
 * 回退到小占位条。
 */
@Composable
fun MarkdownImagePlaceholder(
    alt: String,
    url: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 172.dp,
    onClick: (() -> Unit)? = null,
    natural: Boolean = false,
    fillWidth: Boolean = true,
) {
    if (natural) {
        NaturalImage(alt, url, modifier, fillWidth, onClick)
        return
    }
    val seed = url.hashCode()
    val palette = listOf(Vermilion, Indigo, Celadon, Plum, Gold, PlaceholderTeal)
    val a = palette[((seed % palette.size) + palette.size) % palette.size]
    val b = palette[(((seed / 7) % palette.size) + palette.size) % palette.size]

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(a.copy(alpha = 0.85f), b.copy(alpha = 0.85f)),
                    start = Offset(0f, 0f),
                    end = Offset(500f, 500f),
                )
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        coil3.compose.SubcomposeAsyncImage(
            model = url,
            contentDescription = alt.ifBlank { "图片" },
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("图", fontSize = 22.sp, color = OnAccent.copy(alpha = 0.92f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = alt.ifBlank { "图片" },
                        style = MaterialTheme.typography.labelMedium,
                        color = OnAccent.copy(alpha = 0.92f),
                    )
                }
            },
            error = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("图", fontSize = 22.sp, color = OnAccent.copy(alpha = 0.92f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = alt.ifBlank { "图片" },
                        style = MaterialTheme.typography.labelMedium,
                        color = OnAccent.copy(alpha = 0.92f),
                    )
                }
            },
        )
    }
}

/**
 * 行内表情：比正文图片小一圈、不铺渐变占位，Fit 展示不裁边——
 * 动图（gif/webp）由 Coil 的动图解码器逐帧播，透明底也留得住。
 */
@Composable
private fun StickerInlineImage(
    alt: String,
    url: String,
    onClick: (() -> Unit)?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 失败后 Coil 不会自己重来：换一次请求对象逼出新请求，点一下就重取，
    // 不用退出帖子再进来（用户反馈的「第一遍看是空白的」）
    var attempt by remember(url) { mutableStateOf(0) }
    val model = remember(url, attempt) {
        coil3.request.ImageRequest.Builder(context).data(url).build()
    }
    Box(
        Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        coil3.compose.SubcomposeAsyncImage(
            model = model,
            contentDescription = alt.ifBlank { "表情" },
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            error = {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Rounded.Refresh,
                    contentDescription = "重新加载",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { attempt++ },
                )
            },
        )
    }
}

/** 位图后缀：走约束请求让 Coil 降采样，防整图爆内存；其余按 SVG 原尺寸请求 */
private val rasterUrlRe = Regex("""\.(png|jpe?g|webp|gif|bmp|avif)([?#].*)?$""", RegexOption.IGNORE_CASE)

/**
 * 主页位图解码的长边上限（px）。取 2048：手机版心在 xhdpi 下约 1130px、
 * 平板上限也在 2400px 以内，超出的部分本来就会被版心裁掉，解码不必全解。
 * INEXACT 精度允许解码器给出至多两倍于此的结果，堆占用封顶在几十 MB 量级。
 */
private const val RASTER_DECODE_MAX = 2048

/** 居中容器标记：GitHub 主页 README 用 <div align="center"> 包 logo 与标题 */
private const val CENTER_OPEN = "\u0001center\u0001"
private const val CENTER_CLOSE = "\u0001center-end\u0001"

/** <div align="center">／<p align="center">／<center>：同标签闭合、不嵌套（README 的用法） */
private val centerBlockRe = Regex(
    """(?is)<(div|p|h[1-6])\b[^>]*\balign\s*=\s*["']?center["']?[^>]*>(.*?)</\1>|<center>(.*?)</center>""",
)

@Composable
private fun NaturalImage(
    alt: String,
    url: String,
    modifier: Modifier,
    fillWidth: Boolean,
    onClick: (() -> Unit)?,
) {
    // 解码结果尺寸（px）。SVG 经注入 viewBox 后按「自然尺寸×密度」解码，
    // 换算回 dp 恰是 GitHub 的 CSS px 观感；带约束的请求会把小徽章强行放大到容器宽
    var decoded by remember(url) { mutableStateOf<Pair<Int, Int>?>(null) }
    // SVG 先自取字节补 viewBox 再落本地文件交给 Coil：coil 的磁盘缓存命中时
    // 以 FileImageSource 进解码器，工厂层补丁在这种源上读不到内容（实测），
    // 无 viewBox 的 SVG 在 Compose 画布上又按 1 单位=1 物理像素渲染，徽章全缩没
    var model by remember(url) { mutableStateOf<Any?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(url, attempt) {
        failed = false
        if (rasterUrlRe.containsMatchIn(url)) {
            model = url
            return@LaunchedEffect
        }
        // 目录名带版本：改写规则或校验变了，旧的副本不能再命中
        val dir = java.io.File(context.cacheDir, "md_svg_v3")
        val file = java.io.File(dir, "${url.hashCode()}.svg")
        // 改写好（补 viewBox / 摊平 logo）的副本落在本地：命中就直接用，不再为每张徽章打一次网络
        // （大陆访问 shields.io 一类外站本来就不稳，重复请求只是徒增失败）
        if (file.exists() && file.length() > 0L) {
            model = file
            return@LaunchedEffect
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val request = okhttp3.Request.Builder().url(url).build()
                dev.arisu.shrine.core.net.imageClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    // 只认拿得下手的 SVG：图床在人机校验/限流时回的是 HTML（403 的
                    // 「Just a moment…」），照单收下就会把错误页当图缓存住，
                    // 之后每次命中这份垃圾、永远不再重取（实打实踩过）
                    if (!response.isSuccessful || !text.contains("<svg", ignoreCase = true)) {
                        throw IllegalStateException("不是 SVG（HTTP ${response.code}）")
                    }
                    val patched = dev.arisu.shrine.core.util.patchSvgText(text)
                    dir.mkdirs()
                    file.writeText(patched)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        model = file
                    }
                }
            }.onFailure {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    model = url // 自取失败回退原 URL，交给 Coil 兜底（多半仍是占位）
                }
            }
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 30.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val dim = decoded?.takeIf { (w, h) -> w > 0 && h > 0 }?.let { (w, h) ->
            var dw = with(density) { w.toFloat().toDp() }
            var dh = with(density) { h.toFloat().toDp() }
            if (dw > maxWidth) {
                val r = maxWidth.value / dw.value
                dw = maxWidth
                dh *= r
            }
            if (dh > 340.dp) {
                val r = 340f / dh.value
                dh = 340.dp
                dw *= r
            }
            dw to dh
        }
        // 加载中与失败同一小条占位（沿用「图」占位文案）；成功后由图接管
        if (dim == null) {
            Row(
                Modifier
                    .widthIn(min = 72.dp, max = if (failed) 190.dp else 160.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "图",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = alt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (failed) Modifier.weight(1f, fill = false) else Modifier,
                )
                Spacer(Modifier.width(8.dp))
                if (failed) {
                    // 失败了就地给一个重取入口：点它重走一次，不必退出页面再进来
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Refresh,
                        contentDescription = "重新加载",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { attempt++ },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        if (model != null) {
            val request = androidx.compose.runtime.remember(model, url) {
                coil3.request.ImageRequest.Builder(context)
                    .data(model)
                    .apply {
                        // 位图（URL 进 Coil）必须带上限：Size.ORIGINAL 是「按原像素解」，
                        // 一张 20000×20000 的纯色 PNG 才 400 多 KB（下载很快），全解要
                        // 1.6GB 堆，读到这种图（别人的主页自定义区域可以放任意外链）
                        // 就是当场 OOM 崩溃。2048 以上本来必然被版心裁到底宽，
                        // 降到这个上限观感不变；SVG 徽章仍走原尺寸——约束请求会把
                        // 小徽章强行放大到容器宽，这是 profile 模式的既定规则
                        if (rasterUrlRe.containsMatchIn(url)) size(RASTER_DECODE_MAX, RASTER_DECODE_MAX)
                        else size(coil3.size.Size.ORIGINAL)
                    }
                    .build()
            }
            coil3.compose.AsyncImage(
                model = request,
                contentDescription = alt.ifBlank { "图片" },
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                onSuccess = { st -> decoded = st.result.image.width to st.result.image.height },
                onError = { failed = true },
                modifier = Modifier
                    .then(dim?.let { (w, h) -> Modifier.width(w).height(h) } ?: Modifier)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            )
        }
    }
}
