package dev.arisu.shrine.core.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import dev.arisu.shrine.core.media.needsLegacyGalleryPermission
import dev.arisu.shrine.core.media.saveImageToGallery
import dev.arisu.shrine.data.remote.toApiMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 放大上限：再往里推只剩马赛克 */
private const val VIEWER_MAX_ZOOM = 6f

/** 双击落到的倍率 */
private const val VIEWER_DOUBLE_ZOOM = 2.5f

/**
 * 全屏看图：双指缩放、单指拖动、双击放大/还原，长按弹「保存原图 / 添加到表情包」。
 *
 * 存相册存的是服务端上的原文件，不是屏幕上这张缩放图：29+ 走 MediaStore
 * 挂起写，不要任何权限；26–28 先申请写外部存储权限再写公共图片目录。
 */
@Composable
fun ImageViewerDialog(
    url: String,
    onDismiss: () -> Unit,
    /** 传了才显示「添加到表情包」；挂起，成功与否由本弹窗自己提示 */
    onAddSticker: (suspend (String) -> Unit)? = null,
    addedToStickers: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember(url) { mutableStateOf(false) }
    var notice by remember(url) { mutableStateOf<String?>(null) }
    var saving by remember(url) { mutableStateOf(false) }

    var zoom by remember(url) { mutableStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }

    val painter = rememberAsyncImagePainter(model = url)
    val painterState by painter.state.collectAsStateWithLifecycle()
    val loaded = painterState is AsyncImagePainter.State.Success

    fun save() {
        if (saving) return
        saving = true
        menu = false
        scope.launch {
            try {
                saveImageToGallery(context, url)
                notice = "已保存到相册"
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                notice = e.toApiMessage()
            }
            saving = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) save() else notice = "没有存储权限，存不进相册" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.96f))
                // 点空白处关闭；点在图上的抬手被图的指针处理吃掉，不会误关
                .clickable { if (!menu) onDismiss() },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                val viewportW = constraints.maxWidth.toFloat()
                val viewportH = constraints.maxHeight.toFloat()
                val intrinsic = painter.intrinsicSize
                val hasSize = loaded && intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f
                // 铺满展示（Fit）下的显示尺寸，用来算拖动边界
                val fit = if (hasSize) minOf(viewportW / intrinsic.width, viewportH / intrinsic.height) else 1f

                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(url, hasSize, viewportW, viewportH) {
                            if (!hasSize) return@pointerInput
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                val next = (zoom * zoomChange).coerceIn(1f, VIEWER_MAX_ZOOM)
                                val displayW = intrinsic.width * fit * next
                                val displayH = intrinsic.height * fit * next
                                val maxX = ((displayW - viewportW) / 2f).coerceAtLeast(0f)
                                val maxY = ((displayH - viewportH) / 2f).coerceAtLeast(0f)
                                zoom = next
                                // 图比屏幕小就回正，不给拖出黑边的机会
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                            }
                        }
                        .pointerInput(url, hasSize) {
                            detectTapGestures(
                                // 单击：没放大就关（沿用旧预览弹窗的习惯），放大中先回正
                                onTap = {
                                    if (zoom > 1f) {
                                        zoom = 1f
                                        offset = Offset.Zero
                                    } else {
                                        onDismiss()
                                    }
                                },
                                onDoubleTap = {
                                    zoom = if (zoom > 1f) 1f else VIEWER_DOUBLE_ZOOM
                                    if (zoom == 1f) offset = Offset.Zero
                                },
                                onLongPress = { menu = true },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    coil3.compose.AsyncImage(
                        model = url,
                        contentDescription = "查看图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (hasSize) {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                            },
                    )
                    if (!loaded) {
                        Text(
                            text = "正在取回原图…",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }

                // 底部提示与操作：不做常驻按钮，全屏看图时尽量把画面留给图
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 结果提示（存相册／收表情）从下方弹出：看图时视线就在底部，
                    // 提示浮到顶部或落回底层页面都会被错过
                    NoticePill(notice) { notice = null }
                    val hint = when {
                        saving -> "正在存进相册…"
                        menu -> ""
                        zoom > 1f -> "单击回正 · 双指缩放 · 长按保存"
                        else -> "双指缩放 · 双击放大 · 长按保存 · 单击关闭"
                    }
                    if (hint.isNotEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelSmall,
                            // 浮在暗色 scrim 上，提示字恒白，不随主题走
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                    if (menu) {
                        Spacer(Modifier.height(10.dp))
                        Column(
                            Modifier
                                .width(240.dp)
                                .softCard(shape = RoundedCornerShape(16.dp), elevation = 10.dp)
                                .padding(vertical = 4.dp),
                        ) {
                            ViewerMenuRow(Icons.Rounded.Download, "保存原图到相册") {
                                if (needsLegacyGalleryPermission() &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    menu = false
                                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else {
                                    save()
                                }
                            }
                            if (onAddSticker != null) {
                                ViewerMenuRow(
                                    Icons.Rounded.EmojiEmotions,
                                    if (addedToStickers) "已添加到表情包" else "添加到表情包",
                                    enabled = !addedToStickers,
                                ) {
                                    menu = false
                                    scope.launch {
                                        try {
                                            onAddSticker(url)
                                            notice = "已添加到表情包"
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            notice = e.toApiMessage()
                                        }
                                    }
                                }
                            }
                            ViewerMenuRow(Icons.Rounded.Close, "收起") { menu = false }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 看图器专用的结果提示片：浮在暗色 scrim 上，配色不跟主题走——
 * 浅色主题的 inverseSurface 在这层黑底上等于隐形。
 */
@Composable
private fun NoticePill(text: String?, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf("") }
    if (text != null) shown = text
    LaunchedEffect(text) {
        if (text != null) {
            delay(2000)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(260)),
    ) {
        Box(
            Modifier
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.93f))
                .padding(horizontal = 18.dp, vertical = 11.dp)
        ) {
            Text(
                text = shown,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2B2823),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ViewerMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
