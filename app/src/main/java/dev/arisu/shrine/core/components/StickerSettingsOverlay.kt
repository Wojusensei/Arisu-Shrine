package dev.arisu.shrine.core.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.data.model.Sticker
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val GRID_COLS = 4
private val CELL = 64.dp
private val GAP = 10.dp

@Composable
fun StickerSettingsOverlay(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<Sticker>() }
    var loading by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    var dragId by remember { mutableStateOf<String?>(null) }
    var dragIndex by remember { mutableStateOf(-1) }
    var residual by remember { mutableStateOf(Offset.Zero) }

    val step = with(LocalDensity.current) { (CELL + GAP).toPx() }

    LaunchedEffect(Unit) {
        Shrine.peekStickers()?.let { items.addAll(it) }
        runCatching { Shrine.myStickers() }
            .onSuccess { list -> items.clear(); items.addAll(list) }
            .onFailure { if (items.isEmpty()) notice = it.toApiMessage() }
        loading = false
    }

    fun swap(a: Int, b: Int) {
        if (a !in items.indices || b !in items.indices) return
        val tmp = items[a]
        items[a] = items[b]
        items[b] = tmp
    }

    fun persistOrder() {
        scope.launch {
            try {
                Shrine.reorderStickers(items.toList())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notice = e.toApiMessage()
            }
        }
    }

    fun collect(url: String) {
        scope.launch {
            try {
                val sticker = Shrine.addSticker(url)
                items.add(0, sticker)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notice = e.toApiMessage()
            }
            uploading = false
            progress = 0f
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        progress = 0f
        scope.launch {
            try {

                collect(Shrine.uploadImageFromUri(context, uri) { progress = it })
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notice = e.toApiMessage()
                uploading = false
            }
        }
    }

    val atLimit = items.size >= Shrine.stickerLimit

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ArisuTopBar(
                title = "表情包设置",
                subtitle = "长按拖动排序 · 点「添加」收新表情",
                onBack = onClose,
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (loading && items.isEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalArrangement = Arrangement.Center) {
                        YinYangOrb(size = 22.dp)
                    }
                }

                val cells: List<Sticker?> = listOf(null) + items
                cells.chunked(GRID_COLS).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = GAP / 2),
                        horizontalArrangement = Arrangement.spacedBy(GAP, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEach { sticker ->
                            if (sticker == null) {
                                AddCell(
                                    busy = uploading,
                                    progress = progress,
                                    enabled = !atLimit,
                                    onClick = {
                                        if (uploading) return@AddCell
                                        if (atLimit) {
                                            notice = "表情夹最多 ${Shrine.stickerLimit} 张，先删几张再加"
                                        } else {
                                            pickLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    },
                                )
                            } else {

                                androidx.compose.runtime.key(sticker.id) {
                                val dragging = sticker.id == dragId
                                Box(
                                    Modifier
                                        .size(CELL)
                                        .zIndex(if (dragging) 1f else 0f)
                                        .graphicsLayer {
                                            if (dragging) {
                                                translationX = residual.x
                                                translationY = residual.y
                                                scaleX = 1.08f
                                                scaleY = 1.08f
                                            }
                                        }
                                        .clip(RoundedCornerShape(12.dp))
                                        .neuInset(shape = RoundedCornerShape(12.dp), color = NeuInset)
                                        .pointerInput(sticker.id, items.size) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    dragId = sticker.id
                                                    dragIndex = items.indexOfFirst { it.id == sticker.id }
                                                    residual = Offset.Zero
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    if (dragIndex !in items.indices) return@detectDragGesturesAfterLongPress
                                                    residual += amount

                                                    while (residual.x > step / 2 &&
                                                        visualSlot(dragIndex) % GRID_COLS < GRID_COLS - 1 &&
                                                        dragIndex + 1 <= items.lastIndex
                                                    ) {
                                                        swap(dragIndex, dragIndex + 1)
                                                        dragIndex++
                                                        residual -= Offset(step, 0f)
                                                    }
                                                    while (residual.x < -step / 2 &&
                                                        visualSlot(dragIndex) % GRID_COLS > 0 &&
                                                        dragIndex - 1 >= 0
                                                    ) {
                                                        swap(dragIndex, dragIndex - 1)
                                                        dragIndex--
                                                        residual += Offset(step, 0f)
                                                    }
                                                    while (residual.y > step / 2 && dragIndex + GRID_COLS <= items.lastIndex) {
                                                        swap(dragIndex, dragIndex + GRID_COLS)
                                                        dragIndex += GRID_COLS
                                                        residual -= Offset(0f, step)
                                                    }
                                                    while (residual.y < -step / 2 && dragIndex - GRID_COLS >= 0) {
                                                        swap(dragIndex, dragIndex - GRID_COLS)
                                                        dragIndex -= GRID_COLS
                                                        residual += Offset(0f, step)
                                                    }
                                                },
                                                onDragEnd = {
                                                    dragId = null
                                                    dragIndex = -1
                                                    residual = Offset.Zero
                                                    persistOrder()
                                                },
                                                onDragCancel = {
                                                    dragId = null
                                                    dragIndex = -1
                                                    residual = Offset.Zero
                                                },
                                            )
                                        }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    coil3.compose.AsyncImage(
                                        model = sticker.url,
                                        contentDescription = if (sticker.animated) "动图表情" else "表情",
                                        contentScale = ContentScale.Fit,

                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                                }
                            }
                        }
                        repeat(GRID_COLS - row.size) { Spacer(Modifier.size(CELL)) }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${items.size}/${Shrine.stickerLimit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ShrineNoticeBar(
                text = notice,
                onDismiss = { notice = null },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 20.dp),
            )
        }
    }
}

private fun visualSlot(index: Int): Int = index + 1

@Composable
private fun AddCell(
    busy: Boolean,
    progress: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(CELL)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(enabled = enabled || busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                progress = { progress },
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(38.dp),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "添加表情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "添加",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
