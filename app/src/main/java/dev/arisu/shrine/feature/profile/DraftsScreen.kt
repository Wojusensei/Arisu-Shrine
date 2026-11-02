package dev.arisu.shrine.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.util.TimeFmt
import dev.arisu.shrine.data.local.Draft
import dev.arisu.shrine.data.local.DraftStore
import dev.arisu.shrine.data.repo.Shrine

@Composable
fun DraftsScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenEma: (Int) -> Unit,
    onNewPost: () -> Unit,
    onNewEma: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val me by Shrine.me.collectAsStateWithLifecycle()
    var drafts by remember { mutableStateOf<List<Draft>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Draft?>(null) }

    LaunchedEffect(me?.id) {
        val uid = me?.id ?: return@LaunchedEffect
        drafts = DraftStore.list(uid)
        loaded = true
    }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "草稿箱",
            subtitle = if (loaded) "共 ${drafts.size} 条" else null,
            onBack = onBack,
        )
        if (loaded && drafts.isEmpty()) {
            ShrineEmptyState(
                title = "暂无草稿",
                note = "回复、帖子与绘马的草稿会留在这里。",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(drafts, key = { it.id }) { draft ->
                    DraftRow(
                        draft = draft,
                        onClick = {
                            when (draft.kind) {
                                DraftStore.COMMENT_POST -> onOpenPost(draft.targetId)
                                DraftStore.COMMENT_EMA -> draft.targetId.toIntOrNull()?.let(onOpenEma)
                                DraftStore.POST -> onNewPost()
                                DraftStore.EMA -> onNewEma()
                            }
                        },
                        onDelete = { pendingDelete = draft },
                    )
                    HairlineDivider(startIndent = 52.dp)
                }
            }
        }
    }

    pendingDelete?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条草稿？") },
            text = { Text("删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val uid = me?.id
                    if (uid != null) {
                        drafts = drafts.filterNot { it.id == draft.id }
                        DraftStore.deleteAsync(uid, draft.kind, draft.targetId)
                    }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DraftRow(
    draft: Draft,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val isReply = draft.kind == DraftStore.COMMENT_POST || draft.kind == DraftStore.COMMENT_EMA
    val kindLabel = when (draft.kind) {
        DraftStore.COMMENT_POST, DraftStore.COMMENT_EMA -> "回复草稿"
        DraftStore.POST -> "发帖草稿"
        else -> "绘马草稿"
    }
    val title = when (draft.kind) {
        DraftStore.COMMENT_POST, DraftStore.COMMENT_EMA ->
            "回复 · " + draft.targetTitle.ifBlank { draft.targetId }
        DraftStore.POST -> draft.title.ifBlank { "未命名帖子" }
        else -> draft.title.ifBlank { "未命名绘马" }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (isReply) Icons.Rounded.Reply else Icons.Rounded.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = kindLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (draft.body.isNotBlank()) {
                Text(
                    text = draft.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = TimeFmt.epoch(draft.updatedAt / 1000),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "删除草稿",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
