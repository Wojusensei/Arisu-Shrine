package dev.arisu.shrine.core.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/** 放大上限：再放大只剩马赛克 */
const val CROP_MAX_ZOOM = 4f

/**
 * 取景框的手势状态：拖动与缩放逐次累积在这上面。头像裁剪与图片编辑共用一套。
 *
 * 关键在「公式读的是状态本身，不是某一帧的旧值」——把 zoom/offset 当参数传给手势回调，
 * pointerInput 不会因参数变化而重启，回调里读到的永远是最初那一帧：拖完一松手只剩最后一小段
 * 位移、缩放永远乘在 1 上，看起来就是「动一下就弹回原位」（用户实测反馈）。
 */
class CropTransform {
    var zoom by mutableStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** 一次手势回调：缩放与位移都在当前值上继续累积（位图尺寸与取景框都以像素传，逻辑可单测） */
    fun apply(
        pan: Offset,
        zoomChange: Float,
        bmpW: Int,
        bmpH: Int,
        fit: Float,
        viewportW: Float,
        viewportH: Float,
    ) {
        zoom = (zoom * zoomChange).coerceIn(1f, CROP_MAX_ZOOM)
        // 拖动与缩放都夹在「图始终盖住取景框」的范围内，不会拖出空白边
        offset = clampCropOffset(offset + pan, bmpW, bmpH, fit * zoom, viewportW, viewportH)
    }

    /** 换了图（旋转、换比例）就回到初始取景 */
    fun reset() {
        zoom = 1f
        offset = Offset.Zero
    }
}

/** 拖动范围：图比取景框大的那一部分才允许移动 */
fun clampCropOffset(
    pan: Offset,
    bmpW: Int,
    bmpH: Int,
    scale: Float,
    viewportW: Float,
    viewportH: Float,
): Offset {
    val maxX = ((bmpW * scale - viewportW) / 2f).coerceAtLeast(0f)
    val maxY = ((bmpH * scale - viewportH) / 2f).coerceAtLeast(0f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/** 「盖满取景框」的基准倍率：两个方向都要盖住，横竖图都不会露出空边 */
fun coverFit(bmpW: Int, bmpH: Int, viewportW: Float, viewportH: Float): Float =
    maxOf(viewportW / bmpW.toFloat(), viewportH / bmpH.toFloat())
