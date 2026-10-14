package dev.arisu.shrine.core.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.data.model.Sticker
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun StickerSettingsOverlay(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<Sticker>() }

    val savedOrder = remember { mutableStateListOf<String>() }
    var loading by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var saving by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    val dirty = items.map { it.id } != savedOrder.toList()

    LaunchedEffect(Unit) {
        Shrine.peekStickers()?.let { list ->
            items.addAll(list)
            savedOrder.addAll(list.map { it.id })
        }
        runCatching { Shrine.myStickers() }
            .onSuccess { list ->
                items.clear()
                items.addAll(list)
                savedOrder.clear()
                savedOrder.addAll(list.map { it.id })
            }
            .onFailure { if (items.isEmpty()) notice = it.toApiMessage() }
        loading = false
    }

    fun save(after: () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                Shrine.reorderStickers(items.toList())
                savedOrder.clear()
                savedOrder.addAll(items.map { it.id })
                notice = "顺序已保存"
                after()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                notice = e.toApiMessage()
            }
            saving = false
        }
    }

    fun requestClose() {

        if (saving) return
        if (dirty) confirmExit = true else onClose()
    }

    fun collect(url: String) {
        scope.launch {
            try {
                val sticker = Shrine.addSticker(url)
                items.add(0, sticker)

                savedOrder.add(0, sticker.id)
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

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("保存顺序？") },
            text = { Text("尚未保存的排序不会被保留。") },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) { Text("继续调整") }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { confirmExit = false; onClose() }) { Text("不保存") }
                    TextButton(onClick = { confirmExit = false; save { onClose() } }) { Text("保存") }
                }
            },
        )
    }

    Dialog(onDismissRequest = { requestClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(Modifier.fillMaxSize()) {
                ArisuTopBar(
                    title = "表情包设置",
                    subtitle = "长按拖动排序 · 点「添加」收新表情",
                    onBack = { requestClose() },
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

                    StickerOrderGrid(
                        items = items,
                        firstCell = {
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
                        },
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${items.size}/${Shrine.stickerLimit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(92.dp))
                }
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                ShrineNoticeBar(
                    text = notice,
                    onDismiss = { notice = null },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp),
                )

                NeuSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    depth = 6.dp,
                    blur = 12.dp,
                    color = NeuBaseHigh,
                ) {
                    ArisuPrimaryButton(
                        text = if (saving) "保存中…" else "保存",
                        modifier = Modifier.padding(6.dp),

                        enabled = !saving && dirty,
                        busy = saving,
                        onClick = { save {} },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCell(
    busy: Boolean,
    progress: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(STICKER_CELL)
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
