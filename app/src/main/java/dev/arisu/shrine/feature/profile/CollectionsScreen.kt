package dev.arisu.shrine.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.FadeInOut
import dev.arisu.shrine.core.components.ArisuCard
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.CodeLabel
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineLoadGate
import dev.arisu.shrine.core.components.ShrinePullRefreshBox
import dev.arisu.shrine.core.components.ShrineListEnd
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.components.avatarColorFor
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.Collection

@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var collections by remember { mutableStateOf<List<Collection>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    var pendingDelete by remember { mutableStateOf<Collection?>(null) }
    var createError by remember { mutableStateOf<String?>(null) }

    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    var expandedFor by remember { mutableStateOf<String?>(null) }
    var expandedPosts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var expandedFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var failed by remember { mutableStateOf(false) }

    val load: suspend () -> Unit = {
        try {
            collections = Shrine.collections()
            failed = false
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (collections.isEmpty()) failed = true
        }
        loading = false
    }

    LaunchedEffect(reloadKey) { load() }

    var expandedLoading by remember { mutableStateOf(false) }

    fun loadExpandedPosts(collectionId: String) {
        expandedFor = collectionId
        expandedPosts = emptyList()
        expandedFailed = false
        expandedLoading = true
        scope.launch {
            expandedPosts = try {
                Shrine.postsByAuthor(Shrine.me.value?.id ?: "", "favorited", collectionId.toLongOrNull())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                expandedFailed = true
                emptyList()
            }
            expandedLoading = false
        }
    }

    LaunchedEffect(expandedId) {
        val restored = expandedId
        if (restored != null && expandedFor != restored) loadExpandedPosts(restored)
    }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "收藏夹管理",
            onBack = onBack,
            actions = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { showCreate = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "新建",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(6.dp))
            },
        )

        ShrinePullRefreshBox(
            onRefresh = { load() },
            modifier = Modifier.fillMaxSize(),
        ) {
        Box(Modifier.fillMaxSize()) {
        ShrineLoadGate(loading = loading, modifier = Modifier.fillMaxSize()) {
        if (!loading) {
            if (collections.isEmpty()) {
                ShrineEmptyState(note = "还没有收藏夹")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),

                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(collections, key = { it.id }) { collection ->
                        CollectionCard(
                            collection = collection,
                            onDelete = if (collection.isDefault) null else { { pendingDelete = collection } },
                            expanded = expandedId == collection.id,
                            posts = if (expandedFor == collection.id) expandedPosts else emptyList(),
                            loadFailed = expandedId == collection.id && expandedFor == collection.id && expandedFailed,
                            loading = expandedFor == collection.id && expandedLoading,
                            onRetry = { loadExpandedPosts(collection.id) },
                            onToggle = {
                                val next = if (expandedId == collection.id) null else collection.id
                                expandedId = next
                                if (next != null) loadExpandedPosts(next)
                            },
                            onOpenPost = onOpenPost,
                        )
                    }
                    item { ShrineListEnd(text = "共 ${collections.size} 个收藏夹") }
                }
            }
        }
        }
        }
    }

    pendingDelete?.let { target ->
        dev.arisu.shrine.core.components.ArisuConfirmDialog(
            title = "删除这个收藏夹？",
            body = "「${target.name}」里的帖子会移回默认收藏夹。",
            confirmText = "删除",
            onConfirm = {
                Shrine.deleteCollection(target.id)
                pendingDelete = null
                reloadKey++
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建收藏夹") },
            text = {
                Column {
                    ArisuTextField(
                        value = newName,
                        onValueChange = { if (it.length <= 20) newName = it },
                        placeholder = "收藏夹名称（最多 20 字）",
                    )
                    if (createError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = createError.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = dev.arisu.shrine.core.design.Vermilion,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank() && !creating,
                    onClick = {
                        creating = true
                        scope.launch {
                            try {
                                Shrine.createCollection(newName.trim())
                                collections = Shrine.collections()
                                newName = ""
                                showCreate = false
                                createError = null
                            } catch (e: Exception) {
                                createError = e.toApiMessage()
                            }
                            creating = false
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            },
        )
    }
    }
}

@Composable
private fun CollectionCard(
    collection: Collection,
    onDelete: (() -> Unit)?,
    expanded: Boolean,
    posts: List<Post>,
    loadFailed: Boolean,
    loading: Boolean,
    onRetry: () -> Unit,
    onToggle: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    ArisuCard(watermark = true) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CollectionCover(seed = collection.coverSeed, url = collection.coverUrl)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (collection.isDefault) {
                        Spacer(Modifier.width(6.dp))
                        SoftPill(
                            text = "默认",
                            color = MaterialTheme.colorScheme.primary,
                            background = MaterialTheme.colorScheme.primaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${collection.count} 个内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "删除收藏夹",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }

        if (expanded) {
            Spacer(Modifier.height(14.dp))
            HairlineDivider()
            Spacer(Modifier.height(2.dp))

            posts.take(4).forEach { post ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenPost(post.id) }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CodeLabel(text = post.id)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (posts.isEmpty() && loading) {
                Text(
                    text = "少女祈祷中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (posts.isEmpty() && loadFailed) {
                Text(
                    text = "加载失败，点击重试",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(onClick = onRetry),
                )
            } else if (posts.isEmpty()) {
                Text(
                    text = "这个收藏夹还没有内容",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    text = if (posts.size > 4) "仅展示最近 4 条" else "共 ${posts.size} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CollectionCover(seed: Int, size: Dp = 52.dp, url: String? = null) {
    val tones = listOf(
        avatarColorFor(seed),
        avatarColorFor(seed + 5),
        avatarColorFor(seed + 9),
        avatarColorFor(seed + 13),
    )
    Box(
        Modifier
            .size(size)
            .neuInset(shape = RoundedCornerShape(10.dp), color = NeuInset),
        contentAlignment = Alignment.Center,
    ) {

        if (url != null) {
            coil3.compose.AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(7.dp)),
            )
            return
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(RoundedCornerShape(7.dp)),
        ) {
            Row(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(tones[0])
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(tones[1])
                )
            }
            Row(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(tones[2])
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(tones[3])
                )
            }
        }
    }
}
