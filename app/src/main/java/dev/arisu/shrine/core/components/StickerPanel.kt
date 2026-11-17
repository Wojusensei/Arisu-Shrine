package dev.arisu.shrine.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.data.model.Sticker
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 表情包面板：自留表情的网格，第一格是「添加」，长按一张可以删。
 *
 * 上限由服务端下发（[Shrine.stickerLimit]），不在界面里硬编码；
 * 别人发过的表情也能收——帖子与评论里的图长按即可「添加到表情包」。
 */
@Composable
fun StickerPanel(
    onPick: (Sticker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(Shrine.peekStickers() ?: emptyList()) }
    var loading by remember { mutableStateOf(Shrine.peekStickers() == null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<Sticker?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            items = Shrine.myStickers()
            loading = false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            loading = false
            // 有缓存就继续用，只把失败说出来
            if (items.isEmpty()) notice = e.toApiMessage()
        }
    }

    val atLimit = items.size >= Shrine.stickerLimit

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "自留表情",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${items.size}/${Shrine.stickerLimit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = notice ?: if (atLimit) "表情夹满了，长按一张可以删" else "点选即插入，长按一张可以删",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (loading) {
            Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.Center) {
                YinYangOrb(size = 22.dp)
            }
            return@Column
        }

        if (items.isEmpty()) {
            Text(
                text = "还没有收藏的表情。点第一个格子进设置，从相册选一张；长按别人发过的图也能收进来。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 268.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val cells: List<Sticker?> = listOf(null) + items
            cells.chunked(4).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    // 居中排布：一行四格但面板比四格宽，左对齐会在右边留出一截空白
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { sticker ->
                        if (sticker == null) {
                            SettingsCell { settingsOpen = true }
                        } else {
                            StickerCell(
                                sticker = sticker,
                                onClick = { onPick(sticker) },
                                onLongClick = { pendingDelete = sticker },
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.size(64.dp)) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这张表情？") },
            text = { Text("只从您的表情夹里拿走，已经发出去的内容不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        try {
                            Shrine.deleteSticker(target.id)
                            items = items.filterNot { it.id == target.id }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            notice = e.toApiMessage()
                        }
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("留着") }
            },
        )
    }
    if (settingsOpen) {
        StickerSettingsOverlay(onClose = {
            settingsOpen = false
            // 关掉设置时按服务器重取：顺序与增删都可能变过
            scope.launch { runCatching { items = Shrine.myStickers() } }
        })
    }
}

/** 一格表情：凹槽里 Fit 展示，透明底/动图都不会被裁 */
@Composable
private fun StickerCell(
    sticker: Sticker,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .neuInset(shape = RoundedCornerShape(12.dp), color = NeuInset)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        coil3.compose.AsyncImage(
            model = sticker.url,
            contentDescription = if (sticker.animated) "动图表情" else "表情",
            contentScale = ContentScale.Fit,
            // 圆角要落在图片自己身上：只裁外框时，内缩 4dp 的图四角正好落在直边段，
            // 看起来仍是方的（管理界面同一处理）
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

/** 「设置」格：占着原先「添加」的位置，进去才是添加与拖拽排序 */
@Composable
private fun SettingsCell(onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Handyman,
                contentDescription = "表情包设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "设置",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
