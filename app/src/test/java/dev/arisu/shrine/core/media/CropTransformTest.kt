package dev.arisu.shrine.core.media

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 取景框手势的状态累积。
 *
 * 用户实测报告过「拖完一松手就弹回原位、双指缩放不生效」：根因是手势回调读的是
 * 组件参数（某一帧的旧值），每次都从 zoom=1 / offset=0 重算。这里把行为钉住：
 * 连续两次手势必须在当前值上继续累积，而不是各自从零开始。
 * 取景框 840px（320dp@2.625），测试图 600×900（竖图）与 1200×600（横图）。
 */
class CropTransformTest {

    private val viewport = 840f

    private fun vertical(): CropTransform = CropTransform().apply {
        // 竖图：短边 600 对齐取景框，纵向才有可移动的余量
        apply(Offset.Zero, 1f, 600, 900, viewport / 600f, viewport, viewport)
    }

    @Test
    fun `连续两次拖动要累加而不是各自归零`() {
        val t = vertical()
        t.apply(Offset(0f, 100f), 1f, 600, 900, viewport / 600f, viewport, viewport)
        assertEquals(100f, t.offset.y, 0.01f)
        t.apply(Offset(0f, 100f), 1f, 600, 900, viewport / 600f, viewport, viewport)
        assertEquals(200f, t.offset.y, 0.01f)
    }

    @Test
    fun `连续两次捏合要累加`() {
        val t = vertical()
        t.apply(Offset.Zero, 1.5f, 600, 900, viewport / 600f, viewport, viewport)
        assertEquals(1.5f, t.zoom, 0.001f)
        t.apply(Offset.Zero, 1.5f, 600, 900, viewport / 600f, viewport, viewport)
        assertEquals(2.25f, t.zoom, 0.001f)
    }

    @Test
    fun `缩放夹在 1 到 4 倍之间`() {
        val t = vertical()
        t.apply(Offset.Zero, 0.2f, 600, 900, viewport / 600f, viewport, viewport)
        assertEquals(1f, t.zoom, 0.001f)
        t.apply(Offset.Zero, 99f, 600, 900, viewport / 600f, viewport, viewport)
        assertEquals(4f, t.zoom, 0.001f)
    }

    @Test
    fun `拖动夹在图片盖住取景框的范围内`() {
        val t = vertical()
        // 竖图纵向余量 = (900×1.4 − 840) / 2 = 210px
        t.apply(Offset(0f, 9999f), 1f, 600, 900, viewport / 600f, viewport, viewport)
        assertEquals(210f, t.offset.y, 0.5f)
        t.apply(Offset(0f, -9999f), 1f, 600, 900, viewport / 600f, viewport, viewport)
        assertEquals(-210f, t.offset.y, 0.5f)
    }

    @Test
    fun `短边对齐的方向上没有可移动余量`() {
        val t = vertical()
        // 竖图的宽度恰好等于取景框：横向拖动不该动（这是设计，不是失灵）
        t.apply(Offset(200f, 0f), 1f, 600, 900, viewport / 600f, viewport, viewport)
        assertEquals(0f, t.offset.x, 0.01f)
    }

    @Test
    fun `矩形取景框两个方向各自算余量（图片编辑器用）`() {
        // 取景框 840×472（16:9）；图 1600×1200：盖满倍率 max(840/1600, 472/1200) = 0.525
        // 显示尺寸 840×630 —— 横向刚好贴合（余量 0），纵向余量 (630−472)/2 = 79
        val fit = coverFit(1600, 1200, 840f, 472f)
        val t = CropTransform().apply { apply(Offset.Zero, 1f, 1600, 1200, fit, 840f, 472f) }
        t.apply(Offset(500f, 0f), 1f, 1600, 1200, fit, 840f, 472f)
        assertEquals(0f, t.offset.x, 0.01f)
        t.apply(Offset(0f, 500f), 1f, 1600, 1200, fit, 840f, 472f)
        assertEquals(79f, t.offset.y, 1f)
    }

    @Test
    fun `换比例或旋转后取景复位`() {
        val t = vertical()
        t.apply(Offset(0f, 200f), 2f, 600, 900, viewport / 600f, viewport, viewport)
        t.reset()
        assertEquals(1f, t.zoom, 0.001f)
        assertEquals(0f, t.offset.y, 0.01f)
    }

    @Test
    fun `放大之后原本不能拖的方向就能拖了`() {
        val t = vertical()
        t.apply(Offset.Zero, 2f, 600, 900, viewport / 600f, viewport, viewport)
        // 2 倍后宽度 840×2 = 1680，横向余量 (1680 − 840) / 2 = 420
        t.apply(Offset(200f, 0f), 1f, 600, 900, viewport / 600f, viewport, viewport)
        assertEquals(200f, t.offset.x, 0.5f)
    }
}
