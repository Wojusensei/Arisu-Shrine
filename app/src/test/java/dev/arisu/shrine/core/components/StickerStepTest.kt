package dev.arisu.shrine.core.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表情格栅的跨格判定（纯函数）：左右不越行、上下不出格、不占「添加」格。
 * 6 张表情 = 槽位 1..6：第一行 添加+1/2/3，第二行 4/5/6。
 */
class StickerStepTest {

    @Test
    fun rightStopsAtRowEnd() {
        assertTrue(canStep(DragDir.Right, 0, 6))
        assertFalse(canStep(DragDir.Right, 2, 6)) // 槽 3，行尾
        assertTrue(canStep(DragDir.Right, 3, 6)) // 槽 4，换行后继续向右
    }

    @Test
    fun rightStopsAtLastItem() {
        assertFalse(canStep(DragDir.Right, 5, 6))
    }

    @Test
    fun leftNeverEntersAddCell() {
        assertFalse(canStep(DragDir.Left, 0, 6)) // 槽 1 再往左就是「添加」格
        assertFalse(canStep(DragDir.Left, 3, 6)) // 槽 4 在行首：不绕到上一行行尾
        assertTrue(canStep(DragDir.Left, 4, 6)) // 槽 5 行内左移
    }

    @Test
    fun downOnlyWhenTargetExists() {
        assertTrue(canStep(DragDir.Down, 0, 6)) // 槽 1 → 槽 5
        assertFalse(canStep(DragDir.Down, 3, 6)) // 槽 4 下面没有格
    }

    @Test
    fun upOnlyFromSecondRow() {
        assertFalse(canStep(DragDir.Up, 2, 6))
        assertTrue(canStep(DragDir.Up, 4, 6))
    }

    @Test
    fun slotMapsToGridPosition() {
        assertEquals(Offset(0f, 0f), slotOffsetPx(0, 100f)) // 「添加」格
        assertEquals(Offset(300f, 0f), slotOffsetPx(3, 100f)) // 第一行第 4 格
        assertEquals(Offset(100f, 100f), slotOffsetPx(5, 100f)) // 第二行第 2 格
    }
}
