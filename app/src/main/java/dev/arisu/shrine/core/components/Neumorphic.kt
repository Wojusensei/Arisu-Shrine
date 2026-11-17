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

/* ══════════════════════════════════════════════════════════════════
 * 新拟物（Neumorphism / Soft UI）
 *
 * 凸起：和底色**同色**，光从上方来——上缘受光、下缘落影，左右对称。
 * 两层影子必须反向，而 Android 自带的 elevation 只能往一个方向投影，
 * 所以这里用一层**预模糊的投影位图**加一层渐变描边倒角叠出来。
 * （早期用 RenderEffect 真模糊，每帧对每张卡做一次高斯模糊，
 * 茶话列表实测掉帧 74%；影子是一团与内容无关的纯色模糊，
 * 生成一次 1/4 分辨率位图，之后每帧只是贴图，观感不变。）
 * ══════════════════════════════════════════════════════════════════ */

/** 凸起面。内容自己决定尺寸，影子与亮部用 matchParentSize 跟着走。 */
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
    // 影子色直接烘进位图，绘制阶段零混色成本
    val shadowArgb = NeuShadow.copy(alpha = 0.62f).toArgb().toLong() and 0xFFFFFFFFL
    val padReal = blurPx * 2f // 高斯可见外扩约两个半径

    Box(modifier) {
        // 暗影：向右下偏移的模糊影子，落在自身边界外的部分才是投影
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    // 圆角从 outline 里取（composition 阶段拿不到尺寸）
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
        // 亮部只画在自己边界内：1.2dp 渐变描边倒角。
        // 光从上方来：上缘受光、下缘入暗，左右两侧落在透明区——
        // 早期用对角渐变，宽卡片上会出现「左亮右暗」的歪斜感，已废弃。
        Box(
            Modifier
                .matchParentSize()
                .background(color, shape)
                .border(
                    width = 1.2.dp,
                    // 两侧是首尾色的中间过渡：圆环完整、左右对称
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

/**
 * 投影位图缓存：按「尺寸+圆角+模糊半径+颜色」生成一次 1/4 分辨率的影子，
 * LRU 复用。列表里成排的卡高度接近，命中率高；1/4 分辨率对一团模糊毫无损失。
 */
private object ShadowBitmapCache {
    private const val SCALE = 0.25f
    private const val MAX_ENTRIES = 48

    private val lru = object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) =
            size > MAX_ENTRIES
    }

    fun bitmap(w: Int, h: Int, cornerPx: Float, blurPx: Float, argb: Long): ImageBitmap? {
        if (w <= 0 || h <= 0 || w > 4096 || h > 4096) return null
        // 高度按 16px 分桶：列表卡片高度五花八门，逐高度各存一张很快把 LRU 挤穿；
        // 贴图时按实际尺寸拉伸回去，≤15px 的形变落在一团软糊上肉眼不可见
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

/** 凸起面（浅一档）。 */
@Composable
fun NeuSurfaceHigh(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    depth: Dp = 4.dp,
    blur: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit,
) = NeuSurface(modifier, shape, depth, blur, NeuBaseHigh, content)

/**
 * 柔雾团：径向渐变淡出到同色全透明，没有边缘——烟雾缭绕的基本单位。
 * 只画静态渐变，无模糊滤镜，列表性能无负担。
 * alpha 在绘制期乘上 FogAlphaScale：暗底吸光，夜里雾要加倍才看得见；
 * 绘制期读主题 state，切换主题即触发重绘。
 */
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

/**
 * 大面板右下角的淡朱鸟居水印：鸟居从缭绕的柔雾里探出来，
 * 出界部分被面板轮廓裁掉，必须写在面板内容的前面（先画的在底下）。
 * 纯装饰，不给无障碍负担。
 */
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
                // 鸟居锚在右下（170dp 盒、出血 52/46），雾团绕着它的肩与底座
                val cx = size.width - (52.dp + 85.dp).toPx()
                val cy = size.height - (46.dp + 85.dp).toPx()
                fogBlob(Offset(cx + 40.dp.toPx(), cy - 70.dp.toPx()), 115.dp.toPx(), 0.032f)
                fogBlob(Offset(cx + 10.dp.toPx(), cy + 40.dp.toPx()), 105.dp.toPx(), 0.024f)
            }
    ) {
        // 左上：日轮被横云拦腰而过，与右下鸟居对角呼应（2026-10-04 用户构图）
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

/**
 * 面板右上的柔雾水印：几团无边缘的雾从右缘缭绕进来，出界部分被面板轮廓裁掉。
 * 与鸟居水印（右下）互补，别用在同一个面板上。
 */
/**
 * 面板左上角：日轮被横云拦腰而过。与右下角的鸟居水印对角呼应；
 * 日轮只露一角（出血），云压在日轮下缘之前。
 */
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

/**
 * 凹陷面：输入框、凹槽、热度格子的空档。
 * 做法是「更暗的底 + 上下方向的描边」：光从上方来，
 * 上缘落进阴影、下缘接住亮光，左右两侧完全对称——这才是「压进去」。
 * 早期用左上→右下的对角渐变，宽输入框会左暗右亮、看着左边大右边小，已废弃。
 */
fun Modifier.neuInset(
    shape: Shape = RoundedCornerShape(14.dp),
    color: Color = NeuInset,
    highlight: Boolean = true,
): Modifier {
    val base = this.clip(shape).background(color)
    return if (!highlight) base else base.border(
        width = 1.2.dp,
        // 两侧是首尾色的中间过渡：圆环完整、左右对称
        //（中段全透明会让圆形凹槽的左右两侧整段隐形，看起来「缺了一块」）
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to NeuShadow.copy(alpha = 0.5f),
                1f to NeuLight.copy(alpha = 0.65f),
            ),
        ),
        shape = shape,
    )
}

/** 凹陷面（Composable 版）。 */
@Composable
fun NeuInset(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    color: Color = NeuInset,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.neuInset(shape = shape, color = color), content = content)
}

/**
 * 通用卡面 Modifier。
 *
 * 之前整套 UI 用的是 `.softCard()`，这里让它直接产出新拟物的凸起面，
 * 这样几十个页面不用逐个改就能整体换皮。
 * 走 elevation 投影 + 白色环境光，比 [NeuSurface] 的两层真实模糊轻，
 * 适合列表里大量重复的小卡片。
 */
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

/** 朱色强调面（主按钮、选中态），带同色投影让它浮起来。 */
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

/* ══════════════════════════════════════════════════════════════════
 * 页面底
 * ══════════════════════════════════════════════════════════════════ */

/**
 * 页面底：一整块纯粹的基底色。
 * 新拟物不需要渐变和光斑——那会破坏「整页是同一块材质」的感觉，
 * 只留极淡的一层顶部暖意，避免大面积纯色发闷。
 */
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
                    0.0f to Vermilion.copy(alpha = 0.035f),
                    0.32f to Color.Transparent,
                )
            )
    ) {
        content()
    }
}

/** 兼容旧调用点（旧签名里有个 showGlow 参数）。 */
@Composable
fun PageBackground(
    modifier: Modifier = Modifier,
    showGlow: Boolean,
    content: @Composable BoxScope.() -> Unit,
) = PageBackground(modifier, content)

/** 大标题区的一层朱色气息，新拟物下要更淡。 */
fun Modifier.headerAtmosphere(): Modifier = this.background(
    Brush.verticalGradient(
        0.0f to Vermilion.copy(alpha = 0.035f),
        1.0f to Color.Transparent,
    )
)

/**
 * 毛玻璃：只留给浮在内容之上的层（底部面板、悬浮输入条）。
 * 新拟物是「同一块材质」，毛玻璃是「另一种材质盖上来」，
 * 所以只在真正需要「盖住下面」的地方用。
 */
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

/** 极细分割线，可往左缩进。 */
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
