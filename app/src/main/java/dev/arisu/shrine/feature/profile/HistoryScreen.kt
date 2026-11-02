package dev.arisu.shrine.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EmojiEvents
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.TopBarIcon
import dev.arisu.shrine.core.util.TimeFmt
import dev.arisu.shrine.data.local.HistoryEntry
import dev.arisu.shrine.data.local.HistoryStore
import dev.arisu.shrine.data.repo.Shrine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenEma: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val me by Shrine.me.collectAsStateWithLifecycle()
    var entries by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(me?.id) {
        val uid = me?.id ?: return@LaunchedEffect
        entries = HistoryStore.list(uid)
        loaded = true
    }

    val today = LocalDate.now()
    val groups = listOf("今天", "昨天", "更早").mapNotNull { label ->
        val items = entries.filter { entry ->
            val d = Instant.ofEpochMilli(entry.visitedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            when (label) {
                "今天" -> d == today
                "昨天" -> d == today.minusDays(1)
                else -> d.isBefore(today.minusDays(1))
            }
        }
        if (items.isEmpty()) null else label to items
    }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "浏览足迹",
            subtitle = if (loaded) "共 ${entries.size} 条" else null,
            onBack = onBack,
            actions = {
                if (entries.isNotEmpty()) {
                    TopBarIcon(
                        icon = Icons.Rounded.DeleteSweep,
                        contentDescription = "清除足迹",
                        onClick = { confirmClear = true },
                    )
                }
            },
        )
        if (loaded && entries.isEmpty()) {
            ShrineEmptyState(
                title = "暂无足迹",
                note = "看过的帖子与绘马会留在这里。",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                groups.forEach { (label, items) ->
                    item(key = "label_$label") {
                        SectionLabel(
                            text = label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items.forEach { entry ->
                        item(key = entry.kind + entry.targetId) {
                            HistoryRow(
                                entry = entry,
                                onClick = {
                                    when (entry.kind) {
                                        HistoryStore.POST -> onOpenPost(entry.targetId)
                                        HistoryStore.EMA -> entry.targetId.toIntOrNull()?.let(onOpenEma)
                                    }
                                },
                            )
                            HairlineDivider(startIndent = 52.dp)
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除全部浏览足迹？") },
            text = { Text("清除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    me?.id?.let {
                        entries = emptyList()
                        HistoryStore.clearAsync(it)
                    }
                    confirmClear = false
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (entry.kind == HistoryStore.EMA) Icons.Rounded.EmojiEvents
            else Icons.Rounded.Article,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title.ifBlank { entry.targetId },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 3.dp)) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = TimeFmt.epoch(entry.visitedAt / 1000),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
