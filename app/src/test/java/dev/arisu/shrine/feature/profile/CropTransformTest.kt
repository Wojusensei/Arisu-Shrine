package dev.arisu.shrine.feature.profile

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CropTransformTest {

    private val viewport = 840f

    private fun vertical(): CropTransform = CropTransform().apply {

        apply(Offset.Zero, 1f, 600, 900, viewport / 600f, viewport)
    }

    @Test
    fun `连续两次拖动要累加而不是各自归零`() {
        val t = vertical()
        t.apply(Offset(0f, 100f), 1f, 600, 900, viewport / 600f, viewport)
        assertEquals(100f, t.offset.y, 0.01f)
        t.apply(Offset(0f, 100f), 1f, 600, 900, viewport / 600f, viewport)
        assertEquals(200f, t.offset.y, 0.01f)
    }

    @Test
    fun `连续两次捏合要累加`() {
        val t = vertical()
        t.apply(Offset.Zero, 1.5f, 600, 900, viewport / 600f, viewport)
        assertEquals(1.5f, t.zoom, 0.001f)
        t.apply(Offset.Zero, 1.5f, 600, 900, viewport / 600f, viewport)
        assertEquals(2.25f, t.zoom, 0.001f)
    }

    @Test
    fun `缩放夹在 1 到 4 倍之间`() {
        val t = vertical()
        t.apply(Offset.Zero, 0.2f, 600, 900, viewport / 600f, viewport)
        assertEquals(1f, t.zoom, 0.001f)
        t.apply(Offset.Zero, 99f, 600, 900, viewport / 600f, viewport)
        assertEquals(4f, t.zoom, 0.001f)
    }

    @Test
    fun `拖动夹在图片盖住取景框的范围内`() {
        val t = vertical()

        t.apply(Offset(0f, 9999f), 1f, 600, 900, viewport / 600f, viewport)
        assertEquals(210f, t.offset.y, 0.5f)
        t.apply(Offset(0f, -9999f), 1f, 600, 900, viewport / 600f, viewport)
        assertEquals(-210f, t.offset.y, 0.5f)
    }

    @Test
    fun `短边对齐的方向上没有可移动余量`() {
        val t = vertical()

        t.apply(Offset(200f, 0f), 1f, 600, 900, viewport / 600f, viewport)
        assertEquals(0f, t.offset.x, 0.01f)
    }

    @Test
    fun `放大之后原本不能拖的方向就能拖了`() {
        val t = vertical()
        t.apply(Offset.Zero, 2f, 600, 900, viewport / 600f, viewport)

        t.apply(Offset(200f, 0f), 1f, 600, 900, viewport / 600f, viewport)
        assertEquals(200f, t.offset.x, 0.5f)
    }
}
