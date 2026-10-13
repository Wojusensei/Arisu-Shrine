package dev.arisu.shrine.core.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

const val CROP_MAX_ZOOM = 4f

class CropTransform {
    var zoom by mutableStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

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

        offset = clampCropOffset(offset + pan, bmpW, bmpH, fit * zoom, viewportW, viewportH)
    }

    fun reset() {
        zoom = 1f
        offset = Offset.Zero
    }
}

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

fun coverFit(bmpW: Int, bmpH: Int, viewportW: Float, viewportH: Float): Float =
    maxOf(viewportW / bmpW.toFloat(), viewportH / bmpH.toFloat())
