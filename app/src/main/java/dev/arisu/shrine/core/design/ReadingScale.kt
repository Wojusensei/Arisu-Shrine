package dev.arisu.shrine.core.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import kotlin.math.abs

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

    fun of(base: TextStyle, balanced: Boolean = false): TextStyle = base.copy(
        fontSize = base.fontSize * value,
        lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * value else base.lineHeight,
        lineBreak = if (balanced) LineBreak.Heading else LineBreak.Paragraph,
    )
}
