package dev.arisu.shrine.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.design.InkTitle
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.markdown.MarkdownText

/**
 * 自定义区域：仿 GitHub 个人主页 README。
 * Markdown + 安全 CSS（仅 border-radius / background-color / color / font-size），
 * 未编辑时展示「hello there！」。隐藏该板块时由服务端不下发内容，此处兜底同样显示。
 */
@Composable
fun CustomArea(
    markdown: String,
    safeCss: String,
    modifier: Modifier = Modifier,
) {
    val safe = remember(safeCss) { parseSafeCssValues(safeCss) }
    val radiusDp = safe.radiusPx?.dp
    val bg = safe.background
    val contentColor = safe.color
    val fontSize = safe.fontSizePx

    Column(
        modifier
            .fillMaxWidth()
            .softCard(shape = RoundedCornerShape(radiusDp ?: 14.dp), color = bg ?: NeuBaseHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (markdown.isBlank()) {
            Text(
                text = "hello there！",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = (fontSize ?: 13.5f).sp),
                color = contentColor ?: MaterialTheme.colorScheme.onSurface,
            )
        } else {
            MarkdownText(
                markdown = markdown,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = (fontSize ?: 13.5f).sp),
                color = contentColor ?: MaterialTheme.colorScheme.onSurface,
                naturalImages = true,
            )
        }
    }
}

/** 安全 CSS 解析结果：主页渲染与编辑页预览共用同一份解析器（同一把尺） */
internal data class SafeCssValues(
    val radiusPx: Float? = null,
    val background: Color? = null,
    val color: Color? = null,
    val fontSizePx: Float? = null,
)

/**
 * 白名单解析 border-radius / background(-color) / color / font-size。
 * 声明按 `;`、换行与花括号拆（编辑器里允许多行/带选择器写法），
 * 两处（主页实况与编辑页预览）必须共用本函数，不许各写一份。
 */
internal fun parseSafeCssValues(css: String): SafeCssValues {
    var radius: Float? = null
    var background: Color? = null
    var color: Color? = null
    var fontSize: Float? = null
    css.split(';', '\n', '{', '}').forEach { declaration ->
        val colon = declaration.indexOf(':')
        if (colon <= 0) return@forEach
        val key = declaration.substring(0, colon).trim().lowercase()
        val value = declaration.substring(colon + 1).trim()
        when (key) {
            "border-radius" -> cssPxValue(value)?.let { radius = it }
            "background", "background-color" -> safeHexColor(value)?.let { background = it }
            "color" -> safeHexColor(value)?.let { color = it }
            "font-size" -> cssPxValue(value)?.let { fontSize = it }
        }
    }
    return SafeCssValues(radius, background, color, fontSize)
}

/**
 * px 数值上限与编辑资料页的预览解析（0..400）同规：没有这层，主页端的
 * font-size: 99999px 会把任何访客看到的排版撑爆——两处解析器必须同一把尺子
 */
internal fun cssPxValue(raw: String): Float? =
    raw.filter { it.isDigit() || it == '.' }.toFloatOrNull()?.takeIf { it in 0f..400f }

/** 3/6/8 位 hex：3 位展开、8 位按 CSS 的 #RRGGBBAA 通道序（不走 android 的 AARRGGBB 误序） */
internal fun safeHexColor(raw: String): Color? {
    val hex = raw.trim().removePrefix("#")
    return when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("").toLongOrNull(16)?.let { Color(0xFF000000L or it) }
        6 -> hex.toLongOrNull(16)?.let { Color(0xFF000000L or it) }
        8 -> hex.toLongOrNull(16)?.let {
            Color(((it and 0xFF) shl 24) or (it ushr 8))
        }
        else -> null
    }
}
