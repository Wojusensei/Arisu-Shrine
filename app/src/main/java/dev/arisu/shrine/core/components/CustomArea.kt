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

@Composable
fun CustomArea(
    markdown: String,
    safeCss: String,
    modifier: Modifier = Modifier,
) {
    val css = remember(safeCss) { parseSafeCss(safeCss) }
    val radiusDp = css["border-radius"]?.let { cssPxValue(it) }?.dp
    val bg = css["background-color"]?.let { parseHexColor(it) }
    val contentColor = css["color"]?.let { parseHexColor(it) }
    val fontSize = css["font-size"]?.let { cssPxValue(it) }

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

private fun parseSafeCss(css: String): Map<String, String> =
    css.split(';').mapNotNull { d ->
        val i = d.indexOf(':')
        if (i < 0) return@mapNotNull null
        val k = d.substring(0, i).trim().lowercase()
        val v = d.substring(i + 1).trim()
        if (k in setOf("border-radius", "background-color", "color", "font-size") && v.isNotEmpty()) k to v else null
    }.toMap()

internal fun cssPxValue(raw: String): Float? =
    raw.filter { it.isDigit() || it == '.' }.toFloatOrNull()?.takeIf { it in 0f..400f }

private fun parseHexColor(v: String): Color? {
    val hex = v.removePrefix("#").trim()
    return when (hex.length) {
        3 -> runCatching {
            Color(android.graphics.Color.parseColor("#" + hex.map { "$it$it" }.joinToString("")))
        }.getOrNull()
        6, 8 -> runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrNull()
        else -> null
    }
}
