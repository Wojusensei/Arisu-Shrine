package dev.arisu.shrine.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import dev.arisu.shrine.core.design.FogAlphaScale
import dev.arisu.shrine.core.design.NeuBase
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.NeuLight
import dev.arisu.shrine.core.design.NeuShadow
import dev.arisu.shrine.core.design.Vermilion
import kotlin.math.roundToInt

@Composable
fun NeuSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    depth: Dp = 4.dp,
    blur: Dp = 8.dp,
    color: Color = NeuBase,
    content: @Composable BoxScope.() -> Unit,
) {
    val depthPx = with(LocalDensity.current) { depth.toPx() }
    val blurPx = with(LocalDensity.current) { blur.toPx() }

    val shadowArgb = NeuShadow.copy(alpha = 0.62f).toArgb().toLong() and 0xFFFFFFFFL
    val padReal = blurPx * 2f

    Box(modifier) {

        Box(
            Modifier
                .matchParentSize()
                .drawBehind {

                    val corner = when (val outline = shape.createOutline(size, layoutDirection, this)) {
                        is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                        else -> 0f
                    }
                    val bmp = ShadowBitmapCache.bitmap(
                        size.width.roundToInt(), size.height.roundToInt(), corner, blurPx, shadowArgb,
                    ) ?: return@drawBehind
                    drawImage(
                        image = bmp,
                        dstOffset = IntOffset(
                            (depthPx - padReal).roundToInt(),
                            (depthPx - padReal).roundToInt(),
                        ),
                        dstSize = IntSize(
                            (size.width + padReal * 2).roundToInt(),
                            (size.height + padReal * 2).roundToInt(),
                        ),
                        filterQuality = FilterQuality.Low,
                    )
                }
        )

        Box(
            Modifier
                .matchParentSize()
                .background(color, shape)
                .border(
                    width = 1.2.dp,

                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to NeuLight.copy(alpha = 0.9f),
                            1f to NeuShadow.copy(alpha = 0.3f),
                        ),
                    ),
                    shape = shape,
                )
        )
        content()
    }
}

private object ShadowBitmapCache {
    private const val SCALE = 0.25f
    private const val MAX_ENTRIES = 48

    private val lru = object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) =
            size > MAX_ENTRIES
    }

    fun bitmap(w: Int, h: Int, cornerPx: Float, blurPx: Float, argb: Long): ImageBitmap? {
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return null

        val hb = (h + 15) / 16 * 16
        val key = "$w|$hb|${cornerPx.roundToInt()}|${blurPx.roundToInt()}|$argb"
        synchronized(lru) { lru[key]?.let { return it } }

        val pad = (blurPx * 2f * SCALE).toInt().coerceAtLeast(2)
        val bw = (w * SCALE).toInt() + pad * 2
        val bh = (hb * SCALE).toInt() + pad * 2
        val bmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = argb.toInt()
            maskFilter = android.graphics.BlurMaskFilter(
                blurPx * SCALE, android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
        }
        android.graphics.Canvas(bmp).drawRoundRect(
            pad.toFloat(), pad.toFloat(), (bw - pad).toFloat(), (bh - pad).toFloat(),
            (cornerPx * SCALE).coerceAtLeast(0.5f), (cornerPx * SCALE).coerceAtLeast(0.5f),
            paint,
        )
        val image = bmp.asImageBitmap()
        synchronized(lru) { lru[key] = image }
        return image
    }
}

@Composable
fun NeuSurfaceHigh(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    depth: Dp = 4.dp,
    blur: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit,
) = NeuSurface(modifier, shape, depth, blur, NeuBaseHigh, content)

fun DrawScope.fogBlob(center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Vermilion.copy(alpha = alpha * FogAlphaScale),
                Vermilion.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

@Composable
fun BoxScope.ToriiWatermark(
    shape: Shape,
    modifier: Modifier = Modifier,
    tint: Color = Vermilion.copy(alpha = 0.06f * FogAlphaScale),
) {
    Box(
        Modifier
            .matchParentSize()
            .clip(shape)
            .drawBehind {

                val cx = size.width - (52.dp + 85.dp).toPx()
                val cy = size.height - (46.dp + 85.dp).toPx()
                fogBlob(Offset(cx + 40.dp.toPx(), cy - 70.dp.toPx()), 115.dp.toPx(), 0.032f)
                fogBlob(Offset(cx + 10.dp.toPx(), cy + 40.dp.toPx()), 105.dp.toPx(), 0.024f)
            }
    ) {

        SunCloudCorner(shape)
        Icon(
            imageVector = ShrineIcons.Torii,
            contentDescription = null,
            tint = tint,
            modifier = modifier
                .align(Alignment.BottomEnd)
                .size(170.dp)
                .offset(x = 52.dp, y = 46.dp),
        )
    }
}

@Composable
fun BoxScope.SunCloudCorner(shape: Shape) {
    Box(Modifier.matchParentSize().clip(shape)) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(110.dp)
                .offset(x = (-42).dp, y = (-42).dp)
                .background(Vermilion.copy(alpha = 0.035f * FogAlphaScale), CircleShape)
        )
        Icon(
            imageVector = ShrineIcons.Kumo,
            contentDescription = null,
            tint = Vermilion.copy(alpha = 0.055f * FogAlphaScale),
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(width = 120.dp, height = 50.dp)
                .offset(x = (-30).dp, y = 26.dp),
        )
    }
}

@Composable
fun BoxScope.MistWatermark(
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        Modifier
            .matchParentSize()
            .clip(shape)
    ) {
        SunCloudCorner(shape)
    }
}

fun Modifier.neuInset(
    shape: Shape = RoundedCornerShape(14.dp),
    color: Color = NeuInset,
    highlight: Boolean = true,
): Modifier {
    val base = this.clip(shape).background(color)
    return if (!highlight) base else base.border(
        width = 1.2.dp,

        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to NeuShadow.copy(alpha = 0.5f),
                1f to NeuLight.copy(alpha = 0.65f),
            ),
        ),
        shape = shape,
    )
}

@Composable
fun NeuInset(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    color: Color = NeuInset,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.neuInset(shape = shape, color = color), content = content)
}

@Composable
fun Modifier.softCard(
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 6.dp,
    color: Color = NeuBase,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = NeuLight.copy(alpha = 0.75f),
        spotColor = NeuShadow.copy(alpha = 0.7f),
    )
    .clip(shape)
    .background(color)

@Composable
fun Modifier.accentRaised(
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Vermilion.copy(alpha = 0.28f),
        spotColor = Vermilion.copy(alpha = 0.42f),
    )
    .clip(shape)
    .background(color)

@Composable
fun PageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(NeuBase)
            .background(
                Brush.verticalGradient(
                    0.0f to Vermilion.copy(alpha = 0.05f),
                    0.32f to Color.Transparent,
                )
            )
    ) {
        content()
    }
}

@Composable
fun PageBackground(
    modifier: Modifier = Modifier,
    showGlow: Boolean,
    content: @Composable BoxScope.() -> Unit,
) = PageBackground(modifier, content)

fun Modifier.headerAtmosphere(): Modifier = this.background(
    Brush.verticalGradient(
        0.0f to Vermilion.copy(alpha = 0.05f),
        1.0f to Color.Transparent,
    )
)

fun Modifier.frostedGlass(
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = NeuBaseHigh,
    alpha: Float = 0.9f,
    borderAlpha: Float = 0.55f,
): Modifier = this
    .clip(shape)
    .background(tint.copy(alpha = alpha))
    .border(1.dp, NeuLight.copy(alpha = borderAlpha), shape)

@Composable
fun Modifier.glassPanel(
    shape: Shape = RoundedCornerShape(22.dp),
    elevation: Dp = 16.dp,
    tint: Color = NeuBaseHigh,
    alpha: Float = 0.9f,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = NeuLight.copy(alpha = 0.9f),
        spotColor = NeuShadow.copy(alpha = 0.85f),
    )
    .frostedGlass(shape = shape, tint = tint, alpha = alpha)

@Composable
fun HairlineDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 0.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(NeuShadow.copy(alpha = 0.38f))
    )
}
