package dev.arisu.shrine.feature.profile

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.components.YinYangOrb
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.data.model.Sticker
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun StickerPackDialog(
    userId: String,
    nickname: String,
    onNotice: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var items by remember(userId) { mutableStateOf<List<Sticker>?>(null) }
    var failed by remember(userId) { mutableStateOf<String?>(null) }
    var mine by remember(userId) { mutableStateOf(Shrine.peekStickers()?.map { it.url }?.toSet() ?: emptySet()) }
    var busyUrl by remember(userId) { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        try {
            items = Shrine.userStickers(userId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            failed = e.toApiMessage()
            items = emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text(
                text = "$nickname 的表情包",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "点一张即收进自己的表情夹",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            val list = items
            when {
                list == null -> YinYangOrb(size = 22.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
                list.isEmpty() -> Text(
                    text = failed ?: "$nickname 还没有收藏表情。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    list.chunked(4).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            row.forEach { sticker ->
                                val owned = sticker.url in mine
                                Box(
                                    Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .neuInset(shape = RoundedCornerShape(12.dp), color = NeuInset)
                                        .clickable(enabled = !owned && busyUrl == null) {
                                            busyUrl = sticker.url
                                            scope.launch {
                                                try {
                                                    Shrine.addSticker(sticker.url)
                                                    mine = mine + sticker.url
                                                    onNotice("已收进表情包")
                                                } catch (e: Exception) {
                                                    if (e is CancellationException) throw e
                                                    onNotice(e.toApiMessage())
                                                }
                                                busyUrl = null
                                            }
                                        }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    coil3.compose.AsyncImage(
                                        model = sticker.url,
                                        contentDescription = "表情",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (owned) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = "已有",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.size(64.dp)) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("收起") }
            }
        }
    }
}
