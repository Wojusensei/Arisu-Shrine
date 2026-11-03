package dev.arisu.shrine.core.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingScaleTest {

    @After
    fun resetScale() {

        ReadingScale.apply(1f)
    }

    @Test
    fun nearestSnapsToFourSteps() {
        assertEquals(0.9f, ReadingScale.nearest(0.88f))
        assertEquals(1.0f, ReadingScale.nearest(1.03f))
        assertEquals(1.15f, ReadingScale.nearest(1.2f))
        assertEquals(1.3f, ReadingScale.nearest(1.6f))
    }

    @Test
    fun styleScalesFontAndLineHeightTogether() {
        ReadingScale.apply(1.15f)
        val base = TextStyle(fontSize = 14.sp, lineHeight = 24.sp)
        val scaled = ReadingScale.of(base)

        assertEquals(16.1f, scaled.fontSize.value, 0.01f)
        assertEquals(27.6f, scaled.lineHeight.value, 0.01f)
    }

    @Test
    fun spHelperUsesCurrentScale() {
        ReadingScale.apply(1.3f)
        assertEquals(15.6f, ReadingScale.sp(12.sp).value, 0.01f)
    }
}
