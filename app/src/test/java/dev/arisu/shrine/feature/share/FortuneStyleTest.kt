package dev.arisu.shrine.feature.share

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FortuneStyleTest {

    @Test
    fun eachStyleHasItsOwnPaperAndInk() {
        val palettes = FortuneCardStyle.entries.map { paletteFor(it) }
        assertEquals(FortuneCardStyle.entries.size, palettes.map { it.paper }.toSet().size)
        palettes.forEach {
            assertNotEquals(it.paper, it.ink)
            assertNotEquals(it.paper, it.qrInk)
        }
    }

    @Test
    fun everyQrPaletteKeepsScannerContrast() {
        FortuneCardStyle.entries.forEach { style ->
            val palette = paletteFor(style)
            assertTrue(
                "$style 二维码对比度不足",
                contrast(palette.paper, palette.qrInk) >= 4.5,
            )
        }
    }

    private fun contrast(a: Int, b: Int): Double {
        val first = luminance(a)
        val second = luminance(b)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val s = value / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel((color shr 16) and 0xFF) +
            0.7152 * channel((color shr 8) and 0xFF) +
            0.0722 * channel(color and 0xFF)
    }
}
