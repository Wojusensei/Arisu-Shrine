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

/** 编辑时解码的上限：够看清细节，又不至于让旋转/裁剪卡手 */
private const val EDIT_DECODE_MAX = 2048

/** 裁出图的长边上限：比上传上限略小，二次压缩不吃掉画质 */
private const val EDIT_OUT_MAX = 1600

/** 可选比例；null = 跟着原图 */
private val EditorAspects = listOf(
    "原图" to null,
    "1:1" to 1f,
    "4:3" to 4f / 3f,
    "16:9" to 16f / 9f,
)

/** 封面的显示比例：帖详情里摊开的横幅就是这个形，取景就按它取 */
const val COVER_ASPECT = 16f / 9f

/**
 * 发图前的编辑：双指缩放、拖动取景、按比例裁切、旋转 90°。
 *
 * 所见即所得——框里是什么，确定后就是什么（圆形头像那一套另有一页）。
 * [lockedAspect] 非空时只按这一个比例取景、且不给换（封面用：列表条与详情横幅
 * 都会把它裁成这个形，让上传的人自己定显示范围，而不是被系统从中间截一刀）。
 * [onConfirm] 收到的是裁好的暂存文件路径，上传与否由调用方决定。
 */
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

    // 解码（含 EXIF 转正）在 IO 线程：相机原图动辄几千万像素
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

            // 取景区独占中间这块（weight 先把它框住），工具条与确定键才不会被顶出屏幕：
            // 取景框按可用高度算尺寸时，若不给它上界，竖图会把整列撑满
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

            // 工具条：旋转 + 比例。旋转换的是位图本身，取景随之复位
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
                // 锁死比例（封面）时不给换：显示形是定的，能换反而会白框一场
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

/** 取景框：框内是成图，框外压暗；手势在整块框上都有效 */
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
        // 先按可用宽度定，再按可用高度夹回来，横竖都框得下
        val byWidth = this.maxWidth
        val byHeight = this.maxHeight * ratio
        val width: Dp = if (byWidth <= byHeight) byWidth else byHeight
        val height: Dp = width / ratio
        val viewportPx = with(density) { width.toPx() }
        val viewportHPx = with(density) { height.toPx() }
        // 盖满框的基准倍率：横竖图都不会露出空边
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
            // 一圈细线：明白告诉用户边界在哪
            ComposeCanvas(Modifier.fillMaxSize()) {
                drawRect(
                    color = Color.White.copy(alpha = 0.75f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}

/**
 * 按取景框把可见区域裁下来写成上传件。
 * 长边封顶 [EDIT_OUT_MAX]；源图是 PNG 就继续用 PNG（保住透明底）。
 */
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
    // 取景框左上角对应的原图坐标；夹回图内，取整误差不吃到界外
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
        // 带透明通道的图压成 JPEG 会丢 alpha，先铺白底
        if (!png) drawColor(android.graphics.Color.WHITE)
        drawBitmap(
            bitmap,
            Rect(l, t, l + w, t + h),
            RectF(0f, 0f, outW.toFloat(), outH.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
    }
    val dir = File(context.cacheDir, "upload").apply { mkdirs() }
    // 扩展名先落变量：模板串里再嵌引号会被开源导出器的注释剥离器截断（见导出管线陷阱）
    val ext = if (png) "png" else "jpg"
    val file = File(dir, "edit_${System.currentTimeMillis()}.$ext")
    file.outputStream().use {
        out.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, it)
    }
    out.recycle()
    return file
}
