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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.design.Celadon
import dev.arisu.shrine.core.design.Gold
import dev.arisu.shrine.core.design.Indigo
import dev.arisu.shrine.core.design.OnAccent
import dev.arisu.shrine.core.design.Plum
import dev.arisu.shrine.core.design.InkFaint
import dev.arisu.shrine.core.design.PlaceholderTeal
import dev.arisu.shrine.core.design.Vermilion

internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String, val centered: Boolean = false) : MdBlock
    data class Paragraph(val text: String, val centered: Boolean = false) : MdBlock
    data class Bullet(val items: List<String>) : MdBlock
    data class Ordered(val items: List<String>) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock

    data class Image(val alt: String, val url: String, val link: String? = null, val centered: Boolean = false) : MdBlock

    data class ImageRow(val items: List<Image>, val centered: Boolean = false) : MdBlock
    data class Tasks(val items: List<Pair<Boolean, String>>) : MdBlock
    data class Footnotes(val notes: Map<String, String>) : MdBlock
    data object Divider : MdBlock
}

const val MARKDOWN_STICKER_ALT = "表情"

private val imageTokenRe = Regex(
    """\[!\[([^\]]*)]\(([^)\s]+)\)]\(([^)\s]+)\)""" +
        """|!\[([^\]]*)]\(([^)\s]+)\)""",
)

private fun MutableList<MdBlock.Image>.toImageBlock(centered: Boolean = false): MdBlock =
    if (size == 1) this[0] else MdBlock.ImageRow(toList(), centered)

private fun MatchResult.toImage(centered: Boolean = false): MdBlock.Image =
    if (groups[3] != null) MdBlock.Image(groupValues[1], groupValues[2], link = groupValues[3], centered = centered)
    else MdBlock.Image(groupValues[4], groupValues[5], centered = centered)

private val headingRe = Regex("""^(#{1,6})\s+(.*)$""")
private val bulletRe = Regex("""^\s*[-*+]\s+(.*)$""")
private val orderedRe = Regex("""^\s*\d+[.)]\s+(.*)$""")
private val quoteRe = Regex("^\\s*>\\s?(.*)$")
private val taskRe = Regex("^\\s*[-*+]\\s+\\[([ xX])\\]\\s+(.*)$")
private val footnoteRe = Regex("^\\[\\^(\\w+)\\]:\\s*(.*)$")

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

    if (!source.contains('<')) return decodeEntities(source)
    val fences = mutableListOf<String>()
    var text = source.replace(Regex("""```[\s\S]*?(?:```|$)""")) { m ->
        fences += m.value
        "\u0000${fences.lastIndex}\u0000"
    }
    text = text.replace(htmlComment, "")
    text = text.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")

    text = text.replace(centerBlockRe) { m ->
        val inner = m.groupValues[2].ifBlank { m.groupValues[3] }
        "\n$CENTER_OPEN\n$inner\n$CENTER_CLOSE\n"
    }

    text = text.replace(htmlDetails) { m ->
        val body = m.groupValues[1]
        val sm = htmlSummary.find(body)
        val summary = sm?.groupValues?.get(1)?.trim().orEmpty().ifBlank { "详情" }
        val inner = sm?.let { body.removeRange(it.range) } ?: body
        val innerMd = normalizeHtml(inner).trim().lines().joinToString("\n") { if (it.isBlank()) ">" else "> $it" }
        "\n> **▸ $summary**\n$innerMd\n"
    }

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

    text = text.replace(htmlATag) { m ->
        val href = m.groupValues[1].trim()
        val label = normalizeHtml(m.groupValues[2]).trim()
        if (href.isBlank()) label else "[${label}](${href})"
    }

    text = text.replace(htmlTable) { m ->
        val rows = htmlTr.findAll(m.groupValues[1]).mapNotNull { tr ->
            htmlCell.findAll(tr.groupValues[1]).map { c ->
                normalizeHtml(c.groupValues[1]).trim().replace("\n", " ").replace("|", "\\|")
            }.toList().takeIf { it.isNotEmpty() }
        }.toList()

        val cells = rows.map { row -> row.joinToString(" | ") }
        if (cells.size < 2) ""
        else "\n" + cells.joinToString("\n") { "| " + it + " |" } +
            "\n| " + rows[0].joinToString(" | ") { "---" } + " |\n"
    }

    text = text.replace(htmlHeading) { m ->
        "\n" + "#".repeat(m.groupValues[1].toInt()) + " " + m.groupValues[2].trim() + "\n"
    }

    text = text.replace(htmlBlockquote) { m ->
        val inner = normalizeHtml(m.groupValues[1]).trim()
        "\n" + inner.lines().joinToString("\n") { if (it.isBlank()) ">" else "> $it" } + "\n"
    }

    text = text.replace(Regex("""</?(?:b|strong)\s*>""", RegexOption.IGNORE_CASE), "**")
        .replace(Regex("""</?(?:i|em)\s*>""", RegexOption.IGNORE_CASE), "*")
        .replace(Regex("""</?(?:s|del|strike)\s*>""", RegexOption.IGNORE_CASE), "~~")
        .replace(Regex("""<code\s*>""", RegexOption.IGNORE_CASE), "`")
        .replace(Regex("""</code\s*>""", RegexOption.IGNORE_CASE), "`")

    text = text.replace(Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE), "\n---\n")
    unwrapTags.forEach { tag ->
        text = text.replace(Regex("""</?$tag\b[^>]*>""", RegexOption.IGNORE_CASE), "")
    }
    text = text.replace(anyTag, "")
    text = decodeEntities(text)

    fences.forEachIndexed { idx, code -> text = text.replace("\u0000$idx\u0000", code) }
    return text.replace(Regex("""\n{3,}"""), "\n\n")
}

fun markdownToPlain(source: String): String {
    var t = normalizeHtml(source)
    t = t.replace(Regex("```[\\s\\S]*?(?:```|$)"), " ")
    t = t.replace(Regex("!\\[[^\\]]*]\\(([^)\\s]+)\\)"), " [图片] ")
    t = t.replace(Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)"), "$1")
    t = t.replace(Regex("^[#>*\\-+]+\\s*", RegexOption.MULTILINE), "")
    t = t.replace(Regex("^\\s*\\|.*\\|\\s*$", RegexOption.MULTILINE), "")
    t = t.replace(Regex("[*_~`]+"), "")
    t = t.replace(Regex("\\s+"), " ").trim()
    return t
}

internal fun parseMarkdown(source: String, profileMode: Boolean = false): List<MdBlock> {
    val lines = normalizeHtml(source).replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var i = 0

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
                i++
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

                    val joined = para.joinToString("\n")
                    if (profileMode) {

                        var last = 0
                        var row = mutableListOf<MdBlock.Image>()
                        for (m in imageTokenRe.findAll(joined)) {
                            val gap = joined.substring(last, m.range.first)
                            if (gap.isNotBlank()) {
                                if (row.isNotEmpty()) { blocks += row.toImageBlock(); row = mutableListOf() }
                                blocks += MdBlock.Paragraph(gap.trim(), centered = inCenter)
                            }
                            row += m.toImage()
                            last = m.range.last + 1
                        }
                        if (row.isNotEmpty()) blocks += row.toImageBlock()
                        val tail = joined.substring(last).trim()
                        if (tail.isNotEmpty()) blocks += MdBlock.Paragraph(tail, centered = inCenter)
                    } else {
                        var last = 0
                        for (m in imageTokenRe.findAll(joined)) {
                            if (m.range.first > last) {
                                val before = joined.substring(last, m.range.first).trim()
                                if (before.isNotEmpty()) blocks += MdBlock.Paragraph(before, centered = inCenter)
                            }
                            blocks += m.toImage()
                            last = m.range.last + 1
                        }
                        val tail = joined.substring(last).trim()
                        if (tail.isNotEmpty()) blocks += MdBlock.Paragraph(tail)
                    }
                }
            }
        }
    }
    if (footnotes.isNotEmpty()) blocks += MdBlock.Footnotes(footnotes)
    return blocks
}

private fun buildTable(lines: List<String>): MdBlock.Table {
    fun cells(l: String) = l.trim().trim('|').split('|').map { it.trim() }
    val header = if (lines.isNotEmpty()) cells(lines[0]) else emptyList()
    val body = lines.drop(1)
        .filterNot { it.replace("|", "").replace("-", "").replace(":", "").isBlank() }
        .map { cells(it) }
    return MdBlock.Table(header, body)
}

private val inlineRe = Regex(
    """(!\[[^\]]*]\([^)\s]+\))""" +
        """|(\*\*[^*]+\*\*)""" +
        """|(\*[^*\n]+\*)""" +
        """|(`[^`]+`)""" +
        """|(\[[^\]]+]\([^)\s]+\))""" +
        """|(\[\^\w+\])""" +
        """|(~~[^~]+~~)""" +
        """|(@[\w\u4e00-\u9fa5·]+)"""
)

internal val mentionIdRe = Regex("""@user_0*(\d{1,12})""", RegexOption.IGNORE_CASE)

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

@Composable
internal fun renderInline(
    text: String,
    baseColor: Color,
    codeBg: Color,
    codeColor: Color,
    onMention: ((String) -> Unit)? = null,
): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, baseColor, codeBg, codeColor, linkColor, onMention) {
        buildAnnotatedString {
            var last = 0
            for (m in inlineRe.findAll(text)) {
                if (m.range.first > last) append(text.substring(last, m.range.first))
                val token = m.value
                when {
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

                        val m = if (onMention != null) mentionIdRe.matchEntire(token) else null
                        if (m != null && onMention != null) {
                            val no = "User_" + "%06d".format(m.groupValues[1].toLong())
                            withLink(
                                LinkAnnotation.Clickable(
                                    no,
                                    TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)),
                                ) { onMention(no) },
                            ) {
                                append(token)
                            }
                        } else {
                            withStyle(
                                SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)
                            ) { append(token) }
                        }
                    }

                    token.startsWith("[") -> {
                        val label = token.substringAfter('[').substringBefore(']')
                        val url = token.substringAfter('(').substringBefore(')')

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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onImageClick: ((String) -> Unit)? = null,
    onMentionClick: ((String) -> Unit)? = null,

    naturalImages: Boolean = false,
) {
    val blocks = remember(markdown, naturalImages) { parseMarkdown(markdown, naturalImages) }
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
                        val (size, weight) = when (block.level) {
                            1 -> 22.sp to FontWeight.Bold
                            2 -> 18.sp to FontWeight.Bold
                            3 -> 16.sp to FontWeight.SemiBold
                            else -> 15.sp to FontWeight.SemiBold
                        }
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
                                        text = renderInline(block.text, color, codeBg, codeColor, onMentionClick),
                                        style = style.copy(fontSize = size, fontWeight = weight, lineHeight = size * 1.35f),
                                        color = color,
                                    )
                                }
                            } else {
                                Text(
                                    text = renderInline(block.text, color, codeBg, codeColor, onMentionClick),
                                    style = style.copy(fontSize = size, fontWeight = weight, lineHeight = size * 1.35f),
                                    color = color,
                                )
                            }
                        }
                        }
                    }

                    is MdBlock.Paragraph -> CenteredBlock(block.centered) {
                        Text(
                            text = renderInline(block.text, color, codeBg, codeColor, onMentionClick),
                            style = style,
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
                                        .padding(top = style.fontSize.value.dp * 0.62f)
                                        .size(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    text = renderInline(item, color, codeBg, codeColor, onMentionClick),
                                    style = style,
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
                                    style = style.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = renderInline(item, color, codeBg, codeColor, onMentionClick),
                                    style = style,
                                    color = color,
                                )
                            }
                        }
                    }

                    is MdBlock.Code -> Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    ) {
                        if (block.lang.isNotBlank()) {
                            Text(
                                text = block.lang,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                            )
                        }
                        Text(
                            text = block.code,
                            style = style.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
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
                            text = renderInline(block.text, color, codeBg, codeColor, onMentionClick),
                            style = style.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                        )
                    }

                    is MdBlock.Table -> MarkdownTable(block, style, color, onMentionClick)

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
                                        text, color, codeBg, codeColor, onMentionClick,
                                    ),
                                    style = style,
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

                                    fillWidth = false,
                                    onClick = { onImageTap(img) },
                                )
                            }
                        }
                    } else {

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
                    text = renderInline(cell, color, codeBg, codeColor, onMentionClick),
                    style = style.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
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
                        text = renderInline(cell, color, codeBg, codeColor, onMentionClick),
                        style = style.copy(fontSize = 13.sp),
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

@Composable
private fun StickerInlineImage(
    alt: String,
    url: String,
    onClick: (() -> Unit)?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

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

private val rasterUrlRe = Regex("""\.(png|jpe?g|webp|gif|bmp|avif)([?#].*)?$""", RegexOption.IGNORE_CASE)

private val viewBoxAttrRe = Regex("""viewBox\s*=\s*["']""", RegexOption.IGNORE_CASE)

private const val CENTER_OPEN = "\u0001center\u0001"
private const val CENTER_CLOSE = "\u0001center-end\u0001"

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

    var decoded by remember(url) { mutableStateOf<Pair<Int, Int>?>(null) }

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
        val dir = java.io.File(context.cacheDir, "md_svg")
        val file = java.io.File(dir, "${url.hashCode()}.svg")

        if (file.exists() && file.length() > 0L) {
            model = file
            return@LaunchedEffect
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val request = okhttp3.Request.Builder().url(url).build()
                dev.arisu.shrine.core.net.imageClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (text.isBlank()) throw IllegalStateException("空响应")
                    val patched = if (viewBoxAttrRe.containsMatchIn(text)) text else {
                        val w = Regex("""width\s*=\s*"([\d.]+)"""").find(text)?.groupValues?.get(1)
                        val h = Regex("""height\s*=\s*"([\d.]+)"""").find(text)?.groupValues?.get(1)
                        if (w != null && h != null) {
                            text.replaceFirst("<svg", "<svg viewBox=\"0 0 $w $h\"", ignoreCase = true)
                        } else {
                            text
                        }
                    }
                    dir.mkdirs()
                    file.writeText(patched)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        model = file
                    }
                }
            }.onFailure {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    model = url
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
            coil3.compose.AsyncImage(
                model = coil3.request.ImageRequest.Builder(context)
                    .data(model)
                    .size(coil3.size.Size.ORIGINAL)
                    .build(),
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
