package dev.arisu.shrine.core.components

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 主页实况与编辑页预览共用同一份安全 CSS 解析器：这里钉死白名单与边界。 */
class SafeCssTest {

    @Test
    fun `whitelist keys are parsed`() {
        val css = parseSafeCssValues(
            "border-radius: 12px; background-color: #ff0000; color: #00ff00; font-size: 18px",
        )
        assertEquals(12f, css.radiusPx)
        assertEquals(0xFFFF0000.toInt(), css.background?.toArgb())
        assertEquals(0xFF00FF00.toInt(), css.color?.toArgb())
        assertEquals(18f, css.fontSizePx)
    }

    @Test
    fun `background alias works like background-color`() {
        val css = parseSafeCssValues("background: #336699")
        assertEquals(0xFF336699.toInt(), css.background?.toArgb())
    }

    @Test
    fun `multiline and braces parse like the editor preview`() {
        val css = parseSafeCssValues("color: red\nfont-size: 20px\n{ border-radius: 4px }")
        // 非法颜色值忽略，其余声明照常生效
        assertNull(css.color)
        assertEquals(20f, css.fontSizePx)
        assertEquals(4f, css.radiusPx)
    }

    @Test
    fun `three digit hex expands`() {
        val css = parseSafeCssValues("color: #abc")
        assertEquals(0xFFAABBCC.toInt(), css.color?.toArgb())
    }

    @Test
    fun `eight digit hex follows css rrggbbaa order`() {
        val css = parseSafeCssValues("color: #10203040")
        // CSS 语义：RR=10 GG=20 BB=30 AA=40 → ARGB = 0x40102030
        assertEquals(0x40102030.toInt(), css.color?.toArgb())
    }

    @Test
    fun `px values out of range are dropped`() {
        assertNull(cssPxValue("99999"))
        assertNull(cssPxValue("500"))
        assertEquals(400f, cssPxValue("400"))
        assertEquals(13.5f, cssPxValue("13.5px"))
    }
}
