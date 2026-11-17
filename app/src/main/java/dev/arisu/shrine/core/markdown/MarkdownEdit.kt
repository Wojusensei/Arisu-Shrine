package dev.arisu.shrine.core.markdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 工具栏动作。语义照 GitHub 的 markdown-toolbar：
 * 行内样式再点一次取消，标题/列表/引用按整行处理，代码按单行行内、多行围栏处理。
 */
enum class MdAction { Bold, Italic, Heading, List, Code, Quote, Link }

fun applyMarkdownAction(value: TextFieldValue, action: MdAction): TextFieldValue = when (action) {
    MdAction.Bold -> toggleInline(value, "**", "**", trimFirst = true)
    // 用 * 而不是 _：渲染器只认 *，而且 @User_000015 的下划线不能被当成斜体记号
    MdAction.Italic -> toggleInline(value, "*", "*", trimFirst = true)
    MdAction.Heading -> toggleHeading(value, 2)
    MdAction.List -> toggleUnorderedList(value)
    MdAction.Code -> toggleCode(value)
    MdAction.Quote -> toggleQuote(value)
    MdAction.Link -> toggleLink(value)
}

private val HeadingPrefixRe = Regex("""^#{1,6}[ \t]+""")
private val BulletPrefixRe = Regex("""^[-*+][ \t]+""")
private val OrderedPrefixRe = Regex("""^\d+\.[ \t]+""")
private val FenceRe = Regex("""^```.*$""")
private val UrlRe = Regex("""^https?://\S+$""")
private val LinkRe = Regex("""^\[([^\]]*)]\(([^)]*)\)$""")

/* ── 行内样式 ───────────────────────────────────────────── */

/**
 * 加粗/斜体/行内代码共用。
 *
 * 选中/光标落在已有记号里就剥掉记号（再点一次取消）；否则包起来。
 * 没有选区时插一对空记号，光标落中间——不塞「粗体」这类占位词，
 * 免得只点一下没输入就把占位词留进正文。
 */
fun toggleInline(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    trimFirst: Boolean,
): TextFieldValue {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)
    val collapsed = start == end

    // 已经是 ***粗斜体***：再点斜体去掉一层，别继续叠星号
    if (prefix == "*" && suffix == "*" && !collapsed) {
        val selected = text.substring(start, end)
        if (selected.length >= 6 && selected.startsWith("***") && selected.endsWith("***")) {
            val newText = text.removeRange(end - 1, end).removeRange(start, start + 1)
            return TextFieldValue(
                newText,
                TextRange(start + 2, start + selected.length - 4),
            )
        }
        val leftRun = starRunBefore(text, start)
        val rightRun = starRunAfter(text, end)
        if (leftRun >= 3 && rightRun >= 3) {
            val newText = text.removeRange(end, end + 1).removeRange(start - 1, start)
            return TextFieldValue(newText, TextRange(start - 1, end - 1))
        }
    }

    val wrap = findWrap(text, start, end, prefix, suffix)
    if (wrap != null) {
        val (open, close) = wrap
        val innerStart = open + prefix.length
        val innerEnd = close - suffix.length
        val inner = text.substring(innerStart, innerEnd)
        val newText = text.removeRange(innerEnd, close).removeRange(open, innerStart)
        val caret = if (collapsed) {
            (start - prefix.length).coerceIn(open, open + inner.length)
        } else {
            open
        }
        val selEnd = if (collapsed) caret else open + inner.length
        return TextFieldValue(newText, TextRange(caret, selEnd))
    }

    if (collapsed) {
        val newText = text.replaceRange(start, end, prefix + suffix)
        return TextFieldValue(newText, TextRange(start + prefix.length))
    }

    // 选中的内容整体包住；两端空白留在记号外（GitHub 的 trimFirst）
    var core = text.substring(start, end)
    var leading = ""
    var trailing = ""
    if (trimFirst) {
        val first = core.indexOfFirst { !it.isWhitespace() }
        val last = core.indexOfLast { !it.isWhitespace() }
        if (first >= 0 && last >= first) {
            leading = core.substring(0, first)
            trailing = core.substring(last + 1)
            core = core.substring(first, last + 1)
        }
    }
    val replacement = leading + prefix + core + suffix + trailing
    val newText = text.replaceRange(start, end, replacement)
    val selStart = start + leading.length + prefix.length
    return TextFieldValue(newText, TextRange(selStart, selStart + core.length))
}

/** 找出包住选区/光标的那对记号；没有就返回 null */
private fun findWrap(text: String, start: Int, end: Int, prefix: String, suffix: String): Pair<Int, Int>? {
    // 选区本身带记号（例：整段 **粗体** 被选中）
    if (end - start >= prefix.length + suffix.length &&
        text.regionMatches(start, prefix, 0, prefix.length) &&
        text.regionMatches(end - suffix.length, suffix, 0, suffix.length) &&
        markerBoundary(text, start, prefix, opening = true) &&
        markerBoundary(text, end - suffix.length, suffix, opening = false)
    ) {
        return start to end
    }
    // 记号贴在选区两侧（例：只选了粗体两个字）
    if (start >= prefix.length && end + suffix.length <= text.length &&
        text.regionMatches(start - prefix.length, prefix, 0, prefix.length) &&
        text.regionMatches(end, suffix, 0, suffix.length) &&
        markerBoundary(text, start - prefix.length, prefix, opening = true) &&
        markerBoundary(text, end, suffix, opening = false)
    ) {
        return (start - prefix.length) to (end + suffix.length)
    }
    // 光标落在记号中间（例：**粗|体**，再点一次取消）
    if (start == end) {
        val lineFrom = lineStart(text, start)
        val lineTo = lineEnd(text, end)
        var open = -1
        var i = lineFrom
        while (i < start) {
            if (text.regionMatches(i, prefix, 0, prefix.length) &&
                markerBoundary(text, i, prefix, opening = true)
            ) {
                open = i
            }
            i++
        }
        if (open < 0) return null
        var j = start
        while (j + suffix.length <= lineTo) {
            if (text.regionMatches(j, suffix, 0, suffix.length) &&
                markerBoundary(text, j, suffix, opening = false)
            ) {
                return open to (j + suffix.length)
            }
            j++
        }
    }
    return null
}

/** 下划线既可能是斜体记号，也可能是 @User_000015 的一部分；星号要避开 ** 加粗 */
private fun markerBoundary(text: String, index: Int, marker: String, opening: Boolean): Boolean {
    if (marker == "*") {
        // 单个 * 才是斜体；** 或 *** 里的 * 不抢
        return text.getOrNull(index - 1) != '*' && text.getOrNull(index + marker.length) != '*'
    }
    if (marker != "_") return true
    val before = text.getOrNull(index - 1)
    val after = text.getOrNull(index + marker.length)
    return if (opening) {
        (before == null || !before.isLetterOrDigit()) && (after == null || !after.isWhitespace())
    } else {
        (before == null || !before.isWhitespace()) && (after == null || !after.isLetterOrDigit())
    }
}

private fun starRunBefore(text: String, index: Int): Int {
    var i = index
    while (i > 0 && text[i - 1] == '*') i--
    return index - i
}

private fun starRunAfter(text: String, index: Int): Int {
    var i = index
    while (i < text.length && text[i] == '*') i++
    return i - index
}

/* ── 标题 / 列表 / 引用 ─────────────────────────────────── */

/** 标题按整行处理：选中多行就每行加；再点一次把已有的标题记号去掉 */
fun toggleHeading(value: TextFieldValue, level: Int): TextFieldValue {
    val text = value.text
    val (from, to) = lineRange(text, value.selection.min, value.selection.max)
    val lines = text.substring(from, to).split('\n')
    val prefix = "#".repeat(level.coerceIn(1, 6)) + " "
    val nonEmpty = lines.filter { it.isNotBlank() }
    val allTarget = nonEmpty.isNotEmpty() && nonEmpty.all { it.trimStart().startsWith(prefix) }
    val collapsed = value.selection.min == value.selection.max

    if (nonEmpty.isEmpty() && !collapsed) return value
    if (nonEmpty.isEmpty()) {
        val replacement = prefix
        val newText = text.replaceRange(from, to, replacement)
        return TextFieldValue(newText, TextRange(from + prefix.length))
    }

    val newLines = lines.map { line ->
        if (line.isBlank()) {
            line
        } else {
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val body = line.substring(indent.length).replace(HeadingPrefixRe, "")
            if (allTarget) indent + body else indent + prefix + body
        }
    }
    val block = newLines.joinToString("\n")
    val newText = text.replaceRange(from, to, block)
    return if (collapsed) {
        val lineIndex = text.substring(from, value.selection.min).count { it == '\n' }
        val caret = lineOffset(newLines, lineIndex) + (if (allTarget) 0 else prefix.length)
        TextFieldValue(newText, TextRange(caret))
    } else {
        TextFieldValue(newText, TextRange(from, from + block.length))
    }
}

/** 无序列表按整行开关；已有别的列表记号先清掉再套，不会叠成 - 1. */
fun toggleUnorderedList(value: TextFieldValue): TextFieldValue = toggleList(value, bullet = true)

private fun toggleList(value: TextFieldValue, bullet: Boolean): TextFieldValue {
    val text = value.text
    val (from, to) = lineRange(text, value.selection.min, value.selection.max)
    val lines = text.substring(from, to).split('\n')
    val nonEmpty = lines.filter { it.isNotBlank() }
    val collapsed = value.selection.min == value.selection.max
    val marker = if (bullet) "- " else "1. "
    val targetRe = if (bullet) BulletPrefixRe else OrderedPrefixRe
    val allTarget = nonEmpty.isNotEmpty() && nonEmpty.all { targetRe.containsMatchIn(it) }

    if (nonEmpty.isEmpty() && !collapsed) return value
    // 当前是空行：点列表就是起一个新列表项
    if (nonEmpty.isEmpty()) {
        val newText = text.replaceRange(from, to, marker)
        return TextFieldValue(newText, TextRange(from + marker.length))
    }

    val adding = !allTarget
    val newLines = lines.mapIndexed { index, line ->
        if (line.isBlank()) {
            line
        } else {
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val body = line.substring(indent.length)
                .replace(BulletPrefixRe, "")
                .replace(OrderedPrefixRe, "")
            if (adding) {
                indent + (if (bullet) "- " else "${index + 1}. ") + body
            } else {
                indent + body
            }
        }
    }

    val (prepend, append) = if (adding) newlinesAround(text, from, to) else "" to ""
    val block = newLines.joinToString("\n")
    val replacement = prepend + block + append
    val newText = text.replaceRange(from, to, replacement)
    return if (collapsed) {
        val lineIndex = text.substring(from, value.selection.min).count { it == '\n' }
        val caret = from + prepend.length + lineOffset(newLines, lineIndex) +
            (if (adding) marker.length else 0)
        TextFieldValue(newText, TextRange(caret))
    } else {
        val start = from + prepend.length
        TextFieldValue(newText, TextRange(start, start + block.length))
    }
}

/** 引用按整行开关；加引用时上下各留一个空行，避免和正文粘成一段 */
fun toggleQuote(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val (from, to) = lineRange(text, value.selection.min, value.selection.max)
    val lines = text.substring(from, to).split('\n')
    val nonEmpty = lines.filter { it.isNotBlank() }
    val collapsed = value.selection.min == value.selection.max
    val quoteRe = Regex("""^>[ \t]?""")
    val allQuoted = nonEmpty.isNotEmpty() && nonEmpty.all { quoteRe.containsMatchIn(it) }

    if (nonEmpty.isEmpty() && !collapsed) return value
    if (nonEmpty.isEmpty()) {
        val newText = text.replaceRange(from, to, "> ")
        return TextFieldValue(newText, TextRange(from + 2))
    }

    val adding = !allQuoted
    val newLines = lines.map { line ->
        if (line.isBlank()) {
            line
        } else {
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val body = line.substring(indent.length).replace(quoteRe, "")
            if (adding) indent + "> " + body else indent + body
        }
    }
    val (prepend, append) = if (adding) newlinesAround(text, from, to) else "" to ""
    val block = newLines.joinToString("\n")
    val replacement = prepend + block + append
    val newText = text.replaceRange(from, to, replacement)
    return if (collapsed) {
        val lineIndex = text.substring(from, value.selection.min).count { it == '\n' }
        val caret = from + prepend.length + lineOffset(newLines, lineIndex) + (if (adding) 2 else 0)
        TextFieldValue(newText, TextRange(caret))
    } else {
        val start = from + prepend.length
        TextFieldValue(newText, TextRange(start, start + block.length))
    }
}

/* ── 代码 / 链接 ─────────────────────────────────────────── */

/**
 * 代码按钮照 GitHub：单行是行内代码，跨行是围栏代码块。
 * 围栏块再点一次会连围栏一起撤掉。
 */
fun toggleCode(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)
    val selected = text.substring(start, end)

    if (!selected.trim().contains('\n')) {
        return toggleInline(value, "`", "`", trimFirst = false)
    }

    val (from, to) = lineRange(text, start, end)
    val block = text.substring(from, to)
    val blockLines = block.split('\n')

    // 选区自己带着围栏
    if (blockLines.size >= 2 &&
        blockLines.first().trimStart().startsWith("```") &&
        blockLines.last().trim() == "```"
    ) {
        val inner = blockLines.drop(1).dropLast(1).joinToString("\n")
        val newText = text.replaceRange(from, to, inner)
        val selStart = from
        val selEnd = from + inner.length
        return TextFieldValue(newText, TextRange(selStart, selEnd))
    }

    // 围栏在选区外：上一行是 ```，下一行是 ```
    val beforeLineStart = lineStart(text, from - 1)
    val beforeLine = if (from > 0) text.substring(beforeLineStart, from - 1) else null
    val afterLineEnd = if (to < text.length && text[to] == '\n') lineEnd(text, to + 1) else to
    val afterLine = if (to < text.length && text[to] == '\n') {
        text.substring(to + 1, afterLineEnd)
    } else {
        null
    }
    if (beforeLine != null && afterLine != null &&
        FenceRe.matches(beforeLine.trim()) && afterLine.trim() == "```"
    ) {
        val newText = text.removeRange(to, afterLineEnd).removeRange(beforeLineStart, from)
        val selStart = beforeLineStart
        val selEnd = selStart + (to - from)
        return TextFieldValue(newText, TextRange(selStart, selEnd))
    }

    // 加围栏：整行搬进代码块，内容保持选中
    val replacement = "```\n$block\n```"
    val newText = text.replaceRange(from, to, replacement)
    val selStart = from + 4 + (start - from)
    val selEnd = selStart + (end - start)
    return TextFieldValue(newText, TextRange(selStart, selEnd))
}

/**
 * 链接：选区是 URL → 生成 []（URL）并把光标放到方括号里；
 * 选区是已有链接 → 拆回链接文字；否则包成 [文字](url) 并选中 url。
 */
fun toggleLink(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)
    val selected = text.substring(start, end)

    LinkRe.matchEntire(selected)?.let { match ->
        val label = match.groupValues[1]
        val newText = text.replaceRange(start, end, label)
        return TextFieldValue(newText, TextRange(start, start + label.length))
    }

    if (start == end) {
        val newText = text.replaceRange(start, end, "[](url)")
        return TextFieldValue(newText, TextRange(start + 3, start + 6))
    }

    if (UrlRe.matches(selected)) {
        val newText = text.replaceRange(start, end, "[]($selected)")
        return TextFieldValue(newText, TextRange(start + 1))
    }

    val newText = text.replaceRange(start, end, "[$selected](url)")
    val urlStart = start + 1 + selected.length + 2
    return TextFieldValue(newText, TextRange(urlStart, urlStart + 3))
}

/* ── 行工具 ─────────────────────────────────────────────── */

private fun lineStart(text: String, index: Int): Int {
    if (index <= 0) return 0
    val newline = text.lastIndexOf('\n', index - 1)
    return if (newline < 0) 0 else newline + 1
}

private fun lineEnd(text: String, index: Int): Int {
    val newline = text.indexOf('\n', index)
    return if (newline < 0) text.length else newline
}

private fun lineRange(text: String, selectionStart: Int, selectionEnd: Int): Pair<Int, Int> {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(0, text.length)
    return lineStart(text, start) to lineEnd(text, end)
}

/** 第 index 行在新块里的起始偏移 */
private fun lineOffset(lines: List<String>, index: Int): Int {
    var offset = 0
    for (i in 0 until index.coerceIn(0, lines.size - 1)) offset += lines[i].length + 1
    return offset
}

/** 加块级样式时上下补空行（GitHub 的 surroundWithNewlines） */
private fun newlinesAround(text: String, start: Int, end: Int): Pair<String, String> {
    val before = text.substring(0, start)
    val after = text.substring(end)
    val beforeCount = before.takeLastWhile { it == '\n' }.length
    val afterCount = after.takeWhile { it == '\n' }.length
    val prepend = if (before.any { !it.isWhitespace() } && beforeCount < 2) {
        "\n".repeat(2 - beforeCount)
    } else ""
    val append = if (after.any { !it.isWhitespace() } && afterCount < 2) {
        "\n".repeat(2 - afterCount)
    } else ""
    return prepend to append
}
