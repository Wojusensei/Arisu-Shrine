package dev.arisu.shrine.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuPrimaryButton
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.ShrineLoadingState
import dev.arisu.shrine.core.design.InkTitle
import dev.arisu.shrine.core.design.NeuBase
import dev.arisu.shrine.core.media.CropTransform
import dev.arisu.shrine.core.media.coverFit
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/* ══════════════════════════════════════════════════════════════════
 * 头像裁剪
 * ══════════════════════════════════════════════════════════════════ */

/** 裁剪输出边长：大头像最大显示 84dp，640 足够 4x 屏用，JPEG 约 100KB */
private const val CROP_OUT = 640

/** 预览解码的最长边：1600 放到 4 倍仍然清楚，位图约 10MB */
private const val CROP_DECODE_MAX = 1600

/**
 * 头像裁剪：圆形取景框，拖动移动、双指缩放，确定即按框里所见裁成方形图。
 *
 * 头像处处以圆显示（外层再裁一次圆），所以圆里的内容与最终头像逐像素一致——
 * 此前是服务端原图居中裁剪，取不到想要的圆形只能靠运气。
 * [onConfirm] 收到的是裁好的暂存文件路径，是否上传由调用方决定。
 */
@Composable
fun AvatarCropOverlay(
    sourcePath: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var bitmap by remember(sourcePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var decodeFailed by remember(sourcePath) { mutableStateOf(false) }
    var cropping by remember { mutableStateOf(false) }
    val transform = remember(sourcePath) { CropTransform() }

    // 解码（含 EXIF 转正）在 IO 线程：相机原图动辄几千万像素
    LaunchedEffect(sourcePath) {
        val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { decodeCropSource(File(sourcePath)) }.getOrNull()
        }
        if (decoded == null) decodeFailed = true else bitmap = decoded
    }

    // 系统返回键 = 放弃本次选择（与左上角返回一致）
    BackHandler(enabled = true) { onCancel() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(NeuBase),
    ) {
        // 取景框取屏幕宽的方阵，留出边距后不超过 320dp：太小的屏幕也不会顶到边
        val viewport: Dp = minOf(this.maxWidth - 64.dp, 320.dp)
        val viewportPx = with(density) { viewport.toPx() }

        Column(Modifier.fillMaxSize()) {
            ArisuTopBar(title = "调整头像", onBack = onCancel)

            Spacer(Modifier.weight(1f))

            val bmp = bitmap
            when {
                decodeFailed -> Text(
                    text = "识别不出这张图片的格式，请换一张试试",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 32.dp),
                )

                bmp == null -> ShrineLoadingState()

                else -> CropViewport(
                    bitmap = bmp,
                    viewport = viewport,
                    viewportPx = viewportPx,
                    transform = transform,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "拖动调整位置，双指缩放",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.weight(1f))

            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                ArisuPrimaryButton(
                    text = if (cropping) "处理中…" else "确定",
                    enabled = bmp != null && !cropping,
                    busy = cropping,
                    onClick = {
                        val source = bitmap ?: return@ArisuPrimaryButton
                        cropping = true
                        scope.launch {
                            val fit = coverFit(source.width, source.height, viewportPx, viewportPx)
                            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    cropToFile(context, source, transform.offset, fit * transform.zoom, viewportPx)
                                }.getOrNull()
                            }
                            cropping = false
                            file?.let { onConfirm(it.absolutePath) }
                        }
                    },
                )
            }
        }
    }
}

/** 取景框：圆内是图，圆外压暗；手势在整块取景框上都有效 */
@Composable
private fun CropViewport(
    bitmap: android.graphics.Bitmap,
    viewport: Dp,
    viewportPx: Float,
    transform: CropTransform,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    // 「盖满圆」的基准比例：短边对齐取景框，横竖图都不会露出空边
    val fit = coverFit(bitmap.width, bitmap.height, viewportPx, viewportPx)

    Box(
        modifier
            .size(viewport)
            .pointerInput(bitmap, viewportPx) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    transform.apply(pan, zoomChange, bitmap.width, bitmap.height, fit, viewportPx, viewportPx)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
            val scale = fit * transform.zoom
            val w = bitmap.width * scale
            val h = bitmap.height * scale
            val left = (size.width - w) / 2f + transform.offset.x
            val top = (size.height - h) / 2f + transform.offset.y
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
        // 圆外压暗 + 一圈细线：明白告诉用户哪一块会被裁掉
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val outside = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addOval(Rect(center = center, radius = radius))
            }
            drawPath(outside, InkTitle.copy(alpha = 0.55f))
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

/**
 * 解码暂存图：两段式降采样（先只读尺寸）后再按 EXIF 转正。
 * 相机竖拍的照片带 Orientation 标记、像素本身是躺着的，不转正就会裁出一张歪头像。
 */
private fun decodeCropSource(file: File): android.graphics.Bitmap? =
    dev.arisu.shrine.core.media.decodeForEdit(file, CROP_DECODE_MAX)

/**
 * 按取景框把可见区域裁成 [CROP_OUT] 见方的 JPEG。
 * 取景框是圆的外接正方形：圆外的四个角显示时同样被裁掉，所以裁方形即所见即所得。
 */
private fun cropToFile(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    offset: Offset,
    scale: Float,
    viewportPx: Float,
): File {
    val side = viewportPx / scale
    // 取景框左上角对应的原图坐标；再夹回图内，避免取整误差吃到界外
    val left = (bitmap.width / 2f - (viewportPx / 2f + offset.x) / scale)
        .coerceIn(0f, (bitmap.width - side).coerceAtLeast(0f))
    val top = (bitmap.height / 2f - (viewportPx / 2f + offset.y) / scale)
        .coerceIn(0f, (bitmap.height - side).coerceAtLeast(0f))

    val out = android.graphics.Bitmap.createBitmap(CROP_OUT, CROP_OUT, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    // 带透明通道的图（PNG）压成 JPEG 会丢 alpha，先铺白底
    canvas.drawColor(android.graphics.Color.WHITE)
    // src 取整到像素：误差不到一个原图像素，缩到 640 后看不见；正方形的边长 x/y 用同一个值，不会拉扁
    val s = side.roundToInt().coerceAtLeast(1)
    val l = left.roundToInt().coerceIn(0, (bitmap.width - s).coerceAtLeast(0))
    val t = top.roundToInt().coerceIn(0, (bitmap.height - s).coerceAtLeast(0))
    canvas.drawBitmap(
        bitmap,
        android.graphics.Rect(l, t, l + s, t + s),
        android.graphics.RectF(0f, 0f, CROP_OUT.toFloat(), CROP_OUT.toFloat()),
        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG),
    )
    val dir = File(context.cacheDir, "upload").apply { mkdirs() }
    val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
    out.recycle()
    return file
}
