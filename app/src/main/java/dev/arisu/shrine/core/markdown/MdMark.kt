package dev.arisu.shrine.core.markdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 一段待插进正文的 Markdown 记号。
 *
 * [caret] 是插完光标落在记号里的哪个偏移（「**粗体**」这类带占位词的一般不用它，
 * 空记号才用）；[select] 非空时改为选中这段占位词——接着打字就是替换它。
 * [block] 表示记号要独占行首（标题、列表、代码块），光标不在行首就先补一个换行。
 */
data class MdMark(
    val text: String,
    val caret: Int? = null,
    val select: String? = null,
    val block: Boolean = false,
)

/**
 * 把记号插到光标处；有选中内容就把它包进记号里。
 *
 * 此前一律追加到正文末尾：行文到一半点「加粗」，记号落进最后一行，
 * 想加粗的那句话还得手动搬一趟。占位词同时是切点：它前面的当前缀、
 * 后面的当后缀，选中的内容正好落在中间。
 */
fun insertMark(value: TextFieldValue, mark: MdMark): TextFieldValue {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)
    val picked = text.substring(start, end)

    val select = mark.select?.takeIf { it.isNotEmpty() && mark.text.contains(it) }
    val split = select?.let { mark.text.indexOf(it) }
        ?: (mark.caret ?: mark.text.length).coerceIn(0, mark.text.length)
    // 占位词自身要被选中内容顶掉：后缀从它之后算起
    val suffixAt = split + (select?.length ?: 0)

    val core = if (picked.isEmpty()) mark.text
    else mark.text.substring(0, split) + picked + mark.text.substring(suffixAt)

    // 块级记号粘在句子中间就废了：光标前还有字就先换个行
    val lead = if (mark.block && start > 0 && text[start - 1] != '\n') "\n" else ""
    val base = start + lead.length

    val range = when {
        picked.isEmpty() && select != null -> TextRange(base + split, base + split + select.length)
        picked.isNotEmpty() -> TextRange(base + split, base + split + picked.length)
        else -> TextRange(base + split)
    }
    return TextFieldValue(text.replaceRange(start, end, lead + core), range)
}

/**
 * 程序性改写正文（图片上传完成、移除配图、提及补全）时的替换。
 * 选区越界就收敛回正文长度内，光标尽量留在原处。
 */
fun TextFieldValue.withText(newText: String): TextFieldValue =
    if (newText == text) this
    else TextFieldValue(
        newText,
        TextRange(
            selection.min.coerceAtMost(newText.length),
            selection.max.coerceAtMost(newText.length),
        ),
    )

/**
 * 用 [new] 替换正文里的 [start, end)，光标落到替换结果之后。
 * 提及补全这类「把一个触发字符换成一段完整编号」的改写走这里：
 * 只改文本不搬光标，接着打字就会插进刚生成的编号中间。
 */
fun TextFieldValue.replaceRangeWith(start: Int, end: Int, new: String): TextFieldValue {
    val from = start.coerceIn(0, text.length)
    val to = end.coerceIn(from, text.length)
    return TextFieldValue(
        text.replaceRange(from, to, new),
        TextRange(from + new.length),
    )
}

/**
 * 原位换掉正文里的一段，并让光标跟着新旧长度差走。
 *
 * 上传占位符（`![图片](上传中-123)`）比真地址短得多；只按旧偏移截断，
 * 光标会落在刚换上的 Markdown 中间，接着打字或插下一张图就把这张拆坏。
 * [old] 必须唯一（占位符带自增编号，天然唯一）。
 */
fun TextFieldValue.replaceSpan(old: String, new: String): TextFieldValue {
    val at = text.indexOf(old)
    if (at < 0) return this
    val delta = new.length - old.length
    val end = at + old.length
    fun map(offset: Int): Int = when {
        offset <= at -> offset
        offset >= end -> offset + delta
        // 光标原来落在这段里（例如占位符被选中）：跟到替换结果末尾
        else -> at + new.length
    }
    val replaced = text.replaceRange(at, end, new)
    return TextFieldValue(
        replaced,
        TextRange(
            map(selection.min).coerceIn(0, replaced.length),
            map(selection.max).coerceIn(0, replaced.length),
        ),
    )
}
