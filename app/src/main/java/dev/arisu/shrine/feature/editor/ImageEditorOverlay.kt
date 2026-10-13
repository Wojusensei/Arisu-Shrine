package dev.arisu.shrine.feature.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
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
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.design.NeuBase
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.media.CropTransform
import dev.arisu.shrine.core.media.coverFit
import dev.arisu.shrine.core.media.decodeForEdit
import dev.arisu.shrine.core.media.rotateBitmap
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EDIT_DECODE_MAX = 2048

private const val EDIT_OUT_MAX = 1600

private val EditorAspects = listOf(
    "原图" to null,
    "1:1" to 1f,
    "4:3" to 4f / 3f,
    "16:9" to 16f / 9f,
)

const val COVER_ASPECT = 16f / 9f

@Composable
fun ImageEditorOverlay(
    sourcePath: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
    lockedAspect: Float? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val aspects = if (lockedAspect != null) listOf("" to lockedAspect) else EditorAspects

    var bitmap by remember(sourcePath) { mutableStateOf<Bitmap?>(null) }
    var decodeFailed by remember(sourcePath) { mutableStateOf(false) }
    var aspectIndex by remember(sourcePath) { mutableStateOf(0) }
    var cropping by remember { mutableStateOf(false) }
    var viewportSize by remember(sourcePath) { mutableStateOf(0.dp to 0.dp) }
    val transform = remember(sourcePath) { CropTransform() }

    LaunchedEffect(sourcePath) {
        val decoded = withContext(Dispatchers.IO) {
            runCatching { decodeForEdit(File(sourcePath), EDIT_DECODE_MAX) }.getOrNull()
        }
        if (decoded == null) decodeFailed = true else bitmap = decoded
    }

    androidx.activity.compose.BackHandler(enabled = true) { onCancel() }

    Box(
        Modifier
            .fillMaxSize()
            .background(NeuBase),
    ) {
        Column(Modifier.fillMaxSize()) {
            ArisuTopBar(title = "编辑图片", onBack = onCancel)

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                when {
                    decodeFailed -> Text(
                        text = "识别不出这张图片的格式，请换一张试试",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )

                    bmp == null -> ShrineLoadingState()

                    else -> EditorViewport(
                        bitmap = bmp,
                        aspect = aspects[aspectIndex].second,
                        transform = transform,
                        onViewport = { w, h -> viewportSize = w to h },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text(
                text = "拖动调整位置，双指缩放",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = bitmap != null && !cropping) {
                            val source = bitmap ?: return@clickable
                            bitmap = rotateBitmap(source, 90)
                            transform.reset()
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RotateRight,
                        contentDescription = "旋转",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "旋转",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))

                if (lockedAspect == null) {
                    EditorAspects.forEachIndexed { index, (label, _) ->
                        SoftPill(
                            text = label,
                            color = if (index == aspectIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            background = NeuInset,
                            modifier = Modifier.clickable(enabled = bitmap != null) {
                                aspectIndex = index
                                transform.reset()
                            },
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
                ArisuPrimaryButton(
                    text = if (cropping) "处理中…" else "确定",
                    enabled = bitmap != null && !cropping,
                    busy = cropping,
                    onClick = {
                        val source = bitmap ?: return@ArisuPrimaryButton
                        val (vpW, vpH) = viewportSize
                        if (vpW.value <= 0f || vpH.value <= 0f) return@ArisuPrimaryButton
                        cropping = true
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                runCatching {
                                    cropEditorToFile(
                                        context = context,
                                        bitmap = source,
                                        offset = transform.offset,
                                        zoom = transform.zoom,
                                        viewportW = with(density) { vpW.toPx() },
                                        viewportH = with(density) { vpH.toPx() },
                                        png = File(sourcePath).extension.equals("png", ignoreCase = true),
                                    )
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
private fun EditorViewport(
    bitmap: Bitmap,
    aspect: Float?,
    transform: CropTransform,
    onViewport: (Dp, Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        val ratio = aspect ?: (bitmap.width.toFloat() / bitmap.height.toFloat())

        val byWidth = this.maxWidth
        val byHeight = this.maxHeight * ratio
        val width: Dp = if (byWidth <= byHeight) byWidth else byHeight
        val height: Dp = width / ratio
        val viewportPx = with(density) { width.toPx() }
        val viewportHPx = with(density) { height.toPx() }

        val fit = coverFit(bitmap.width, bitmap.height, viewportPx, viewportHPx)

        LaunchedEffect(width, height) { onViewport(width, height) }

        Box(
            Modifier
                .size(width, height)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(bitmap, viewportPx, viewportHPx) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        transform.apply(
                            pan, zoomChange,
                            bitmap.width, bitmap.height,
                            fit, viewportPx, viewportHPx,
                        )
                    }
                },
        ) {
            ComposeCanvas(Modifier.fillMaxSize()) {
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

            ComposeCanvas(Modifier.fillMaxSize()) {
                drawRect(
                    color = Color.White.copy(alpha = 0.75f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}

private fun cropEditorToFile(
    context: android.content.Context,
    bitmap: Bitmap,
    offset: Offset,
    zoom: Float,
    viewportW: Float,
    viewportH: Float,
    png: Boolean,
): File {
    val scale = coverFit(bitmap.width, bitmap.height, viewportW, viewportH) * zoom
    val srcW = viewportW / scale
    val srcH = viewportH / scale

    val left = (bitmap.width / 2f - (viewportW / 2f + offset.x) / scale)
        .coerceIn(0f, (bitmap.width - srcW).coerceAtLeast(0f))
    val top = (bitmap.height / 2f - (viewportH / 2f + offset.y) / scale)
        .coerceIn(0f, (bitmap.height - srcH).coerceAtLeast(0f))
    val l = left.roundToInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
    val t = top.roundToInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
    val w = srcW.roundToInt().coerceIn(1, bitmap.width - l)
    val h = srcH.roundToInt().coerceIn(1, bitmap.height - t)

    val long = maxOf(w, h).toFloat()
    val outScale = if (long > EDIT_OUT_MAX) EDIT_OUT_MAX / long else 1f
    val outW = (w * outScale).roundToInt().coerceAtLeast(1)
    val outH = (h * outScale).roundToInt().coerceAtLeast(1)

    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    Canvas(out).apply {

        if (!png) drawColor(android.graphics.Color.WHITE)
        drawBitmap(
            bitmap,
            Rect(l, t, l + w, t + h),
            RectF(0f, 0f, outW.toFloat(), outH.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
    }
    val dir = File(context.cacheDir, "upload").apply { mkdirs() }

    val ext = if (png) "png" else "jpg"
    val file = File(dir, "edit_${System.currentTimeMillis()}.$ext")
    file.outputStream().use {
        out.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, it)
    }
    out.recycle()
    return file
}
