package dev.arisu.shrine.core.markdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

enum class MdAction { Bold, Italic, Heading, List, Code, Quote, Link }

fun applyMarkdownAction(value: TextFieldValue, action: MdAction): TextFieldValue = when (action) {
    MdAction.Bold -> toggleInline(value, "**", "**", trimFirst = true)

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

private fun findWrap(text: String, start: Int, end: Int, prefix: String, suffix: String): Pair<Int, Int>? {

    if (end - start >= prefix.length + suffix.length &&
        text.regionMatches(start, prefix, 0, prefix.length) &&
        text.regionMatches(end - suffix.length, suffix, 0, suffix.length) &&
        markerBoundary(text, start, prefix, opening = true) &&
        markerBoundary(text, end - suffix.length, suffix, opening = false)
    ) {
        return start to end
    }

    if (start >= prefix.length && end + suffix.length <= text.length &&
        text.regionMatches(start - prefix.length, prefix, 0, prefix.length) &&
        text.regionMatches(end, suffix, 0, suffix.length) &&
        markerBoundary(text, start - prefix.length, prefix, opening = true) &&
        markerBoundary(text, end, suffix, opening = false)
    ) {
        return (start - prefix.length) to (end + suffix.length)
    }

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

private fun markerBoundary(text: String, index: Int, marker: String, opening: Boolean): Boolean {
    if (marker == "*") {

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

    val replacement = "```\n$block\n```"
    val newText = text.replaceRange(from, to, replacement)
    val selStart = from + 4 + (start - from)
    val selEnd = selStart + (end - start)
    return TextFieldValue(newText, TextRange(selStart, selEnd))
}

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

private fun lineOffset(lines: List<String>, index: Int): Int {
    var offset = 0
    for (i in 0 until index.coerceIn(0, lines.size - 1)) offset += lines[i].length + 1
    return offset
}

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
