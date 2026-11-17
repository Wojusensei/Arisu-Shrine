package dev.arisu.shrine.core.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import kotlin.math.abs

/**
 * 正文字号。只作用于阅读内容（帖子、评论、绘马、日志），
 * 不碰标题、按钮、计数与导航；行高随字号等比缩放。
 *
 * 换行用 [LineBreak] 控制：短文本用 Heading 的均衡策略，
 * 避免最后一行只剩一个字；长段落用 Paragraph 的正文策略。
 */
object ReadingScale {

    val steps = listOf(0.9f, 1.0f, 1.15f, 1.3f)
    val labels = listOf("小", "标准", "大", "特大")

    var value by mutableFloatStateOf(1f)
        private set

    fun apply(scale: Float) {
        value = nearest(scale)
    }

    fun nearest(scale: Float): Float =
        steps.minByOrNull { abs(it - scale) } ?: 1f

    fun sp(size: TextUnit): TextUnit = size * value

    /**
     * 正文样式：字号与行高同倍率，换行策略按文本长短选。
     * balanced 只给短文本（评论、短段落、标题），长段落用 Paragraph。
     */
    fun of(base: TextStyle, balanced: Boolean = false): TextStyle = base.copy(
        fontSize = base.fontSize * value,
        lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * value else base.lineHeight,
        lineBreak = if (balanced) LineBreak.Heading else LineBreak.Paragraph,
    )
}
