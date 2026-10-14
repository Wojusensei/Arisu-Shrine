package dev.arisu.shrine.core.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerStepTest {

    @Test
    fun rightStopsAtRowEnd() {
        assertTrue(canStep(DragDir.Right, 0, 6))
        assertFalse(canStep(DragDir.Right, 2, 6))
        assertTrue(canStep(DragDir.Right, 3, 6))
    }

    @Test
    fun rightStopsAtLastItem() {
        assertFalse(canStep(DragDir.Right, 5, 6))
    }

    @Test
    fun leftNeverEntersAddCell() {
        assertFalse(canStep(DragDir.Left, 0, 6))
        assertFalse(canStep(DragDir.Left, 3, 6))
        assertTrue(canStep(DragDir.Left, 4, 6))
    }

    @Test
    fun downOnlyWhenTargetExists() {
        assertTrue(canStep(DragDir.Down, 0, 6))
        assertFalse(canStep(DragDir.Down, 3, 6))
    }

    @Test
    fun upOnlyFromSecondRow() {
        assertFalse(canStep(DragDir.Up, 2, 6))
        assertTrue(canStep(DragDir.Up, 4, 6))
    }

    @Test
    fun slotMapsToGridPosition() {
        assertEquals(Offset(0f, 0f), slotOffsetPx(0, 100f))
        assertEquals(Offset(300f, 0f), slotOffsetPx(3, 100f))
        assertEquals(Offset(100f, 100f), slotOffsetPx(5, 100f))
    }
}
