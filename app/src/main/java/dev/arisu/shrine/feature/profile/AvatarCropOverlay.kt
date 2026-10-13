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

private const val CROP_OUT = 640

private const val CROP_DECODE_MAX = 1600

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

    LaunchedEffect(sourcePath) {
        val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { decodeCropSource(File(sourcePath)) }.getOrNull()
        }
        if (decoded == null) decodeFailed = true else bitmap = decoded
    }

    BackHandler(enabled = true) { onCancel() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(NeuBase),
    ) {

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

@Composable
private fun CropViewport(
    bitmap: android.graphics.Bitmap,
    viewport: Dp,
    viewportPx: Float,
    transform: CropTransform,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }

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

private fun decodeCropSource(file: File): android.graphics.Bitmap? =
    dev.arisu.shrine.core.media.decodeForEdit(file, CROP_DECODE_MAX)

private fun cropToFile(
    context: android.content.Context,
    bitmap: android.graphics.Bitmap,
    offset: Offset,
    scale: Float,
    viewportPx: Float,
): File {
    val side = viewportPx / scale

    val left = (bitmap.width / 2f - (viewportPx / 2f + offset.x) / scale)
        .coerceIn(0f, (bitmap.width - side).coerceAtLeast(0f))
    val top = (bitmap.height / 2f - (viewportPx / 2f + offset.y) / scale)
        .coerceIn(0f, (bitmap.height - side).coerceAtLeast(0f))

    val out = android.graphics.Bitmap.createBitmap(CROP_OUT, CROP_OUT, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)

    canvas.drawColor(android.graphics.Color.WHITE)

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
