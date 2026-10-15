package dev.arisu.shrine.core.markdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

data class MdMark(
    val text: String,
    val caret: Int? = null,
    val select: String? = null,
    val block: Boolean = false,
)

fun insertMark(value: TextFieldValue, mark: MdMark): TextFieldValue {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)
    val picked = text.substring(start, end)

    val select = mark.select?.takeIf { it.isNotEmpty() && mark.text.contains(it) }
    val split = select?.let { mark.text.indexOf(it) }
        ?: (mark.caret ?: mark.text.length).coerceIn(0, mark.text.length)

    val suffixAt = split + (select?.length ?: 0)

    val core = if (picked.isEmpty()) mark.text
    else mark.text.substring(0, split) + picked + mark.text.substring(suffixAt)

    val lead = if (mark.block && start > 0 && text[start - 1] != '\n') "\n" else ""
    val base = start + lead.length

    val range = when {
        picked.isEmpty() && select != null -> TextRange(base + split, base + split + select.length)
        picked.isNotEmpty() -> TextRange(base + split, base + split + picked.length)
        else -> TextRange(base + split)
    }
    return TextFieldValue(text.replaceRange(start, end, lead + core), range)
}

fun TextFieldValue.withText(newText: String): TextFieldValue =
    if (newText == text) this
    else TextFieldValue(
        newText,
        TextRange(
            selection.min.coerceAtMost(newText.length),
            selection.max.coerceAtMost(newText.length),
        ),
    )
