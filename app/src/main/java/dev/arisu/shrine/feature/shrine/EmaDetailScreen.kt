package dev.arisu.shrine.feature.shrine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Send
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.RoleTag
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.EmojiSheet
import dev.arisu.shrine.core.components.ScrollTopOrb
import dev.arisu.shrine.core.components.ShrineEmptyState
import androidx.compose.foundation.layout.imePadding
import dev.arisu.shrine.core.components.ShrineNoticeBar
import dev.arisu.shrine.core.components.ShrineListEnd
import dev.arisu.shrine.core.components.StatePill
import dev.arisu.shrine.core.components.TagChip
import dev.arisu.shrine.core.components.TopBarIcon
import dev.arisu.shrine.core.components.accentRaised
import dev.arisu.shrine.core.components.glassPanel
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.components.smoothScrollToTop
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.markdown.MarkdownText
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.EmaReply
import dev.arisu.shrine.data.model.EmaState
import dev.arisu.shrine.data.model.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmaDetailScreen(
    emaId: Int,
    onBack: () -> Unit,
    onOpenUser: (String) -> Unit,
    onEdit: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var stateMenuOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showEmoji by remember { mutableStateOf(false) }

    var previewUrl by remember { mutableStateOf<String?>(null) }
    var collectedStickers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reportOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<dev.arisu.shrine.data.model.Ema?>(null) }

    var reply by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    var ema by remember { mutableStateOf(Shrine.peekEma(emaId)) }
    var replies by remember { mutableStateOf(Shrine.peekEmaReplies(emaId) ?: emptyList()) }
    var loadState by remember { mutableStateOf(if (ema == null) "loading" else "ok") }
    val me by Shrine.me.collectAsState()
    var pendingReplyDelete by remember { mutableStateOf<EmaReply?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            try {
                ema = Shrine.ema(emaId)
                replies = Shrine.emaReplies(emaId)
                loadState = "ok"
            } catch (e: dev.arisu.shrine.data.remote.ApiError) {

                loadState = when {
                    e.message?.contains("不存在") == true -> "missing"
                    ema != null -> "ok"
                    else -> "fail"
                }
            } catch (e: Exception) {
                if (ema == null) loadState = "fail"
            }
        }
    }

    LaunchedEffect(emaId) { refresh() }

    val canModerate = me?.role?.canModerate ?: false
    val isAuthor = ema?.authorId == me?.id

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "绘马详情",
            subtitle = ema?.let { "#${it.id}" },
            onBack = onBack,
            actions = {

                val menuEma = ema
                if (menuEma != null) {
                    Box {
                        TopBarIcon(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = "更多",
                            onClick = { menuOpen = true },
                        )
                        if (menuOpen) {
                            EmaMoreMenu(
                                canReport = !isAuthor,
                                canEdit = isAuthor,
                                canDelete = isAuthor || canModerate,
                                onDismiss = { menuOpen = false },
                                onReport = {
                                    menuOpen = false
                                    reportOpen = true
                                },
                                onEdit = {
                                    menuOpen = false
                                    onEdit(menuEma.id)
                                },
                                onDelete = {
                                    menuOpen = false

                                    deleteTarget = menuEma
                                },
                            )
                        }
                    }
                }
            },
        )

        ShrineNoticeBar(
            text = notice,
            onDismiss = { notice = null },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 6.dp),
        )

        deleteTarget?.let { target ->
            dev.arisu.shrine.core.components.ArisuConfirmDialog(
                title = "删除绘马",
                body = if (isAuthor) "删除后不可恢复。" else "删除后不可恢复，删除理由将通知给发起人。",
                confirmText = "确认删除",

                reasonHint = if (isAuthor) null else "删除理由（必填）",
                onConfirm = { reason ->
                    Shrine.deleteEma(target.id, if (isAuthor) null else reason)
                    deleteTarget = null
                    onBack()
                },
                onDismiss = { deleteTarget = null },
            )
        }

        if (showEmoji) {
            EmojiSheet(
                onSelect = { reply += it },
                onSticker = { sticker ->
                    val snippet = "![${dev.arisu.shrine.core.markdown.MARKDOWN_STICKER_ALT}](${sticker.url})"
                    reply = if (reply.isBlank()) snippet else reply.trimEnd('\n') + "\n" + snippet
                },
                onDismiss = { showEmoji = false },
            )
        }

        val previewing = previewUrl
        if (previewing != null) {
            dev.arisu.shrine.core.components.ImageViewerDialog(
                url = previewing,
                addedToStickers = previewing in collectedStickers,
                onAddSticker = { imageUrl ->

                    Shrine.addSticker(imageUrl)
                    collectedStickers = collectedStickers + imageUrl
                },
                onDismiss = { previewUrl = null },
            )
        }

        if (reportOpen) {
            dev.arisu.shrine.core.components.ArisuConfirmDialog(
                title = "举报内容",
                body = "处理结果会通过社务箱通知您。",
                confirmText = "提交举报",
                reasonHint = "举报理由（必填）",
                onConfirm = { reason ->
                    Shrine.reportEma(emaId, reason)
                    reportOpen = false
                    notice = "已提交举报，处理结果会通过社务箱通知您"
                },
                onDismiss = { reportOpen = false },
            )
        }

        pendingReplyDelete?.let { target ->
            val modDeleting = canModerate && target.authorId != me?.id
            dev.arisu.shrine.core.components.ArisuConfirmDialog(
                title = "删除这条回复？",
                body = "删除后无法恢复。",
                confirmText = "删除",
                reasonHint = if (modDeleting) "删除理由（必填）" else null,
                onConfirm = { reason ->
                    Shrine.deleteEmaReply(emaId, target.id, if (modDeleting) reason else null)
                    replies = replies.filterNot { it.id == target.id }
                    ema = ema?.copy(replyCount = replies.size)
                    pendingReplyDelete = null
                    notice = "回复已删除"
                },
                onDismiss = { pendingReplyDelete = null },
            )
        }

        when (loadState) {
            "loading" -> dev.arisu.shrine.core.components.ShrineLoadingState()
            "missing" -> ShrineEmptyState(note = "这块绘马不存在", onClick = onBack)
            "fail" -> ShrineEmptyState(note = "加载失败，点击重试", onClick = { loadState = "loading"; refresh() })
            else -> {
                val ema0 = ema
                if (ema0 != null) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val emaScroll = rememberScrollState()
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(emaScroll)
                                .padding(horizontal = 16.dp)
                        ) {
                            Spacer(Modifier.height(14.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${ema0.id}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    StatePill(ema0.state)
                                }
                                Spacer(Modifier.height(9.dp))
                                Text(
                                    text = ema0.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (ema0.tags.isNotEmpty()) {
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        ema0.tags.forEach { tag ->
                                            TagChip(tag)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(13.dp))
                                HairlineDivider()
                                Spacer(Modifier.height(13.dp))
                                EmaAuthorRow(
                                    ema = ema0,
                                    onOpenUser = onOpenUser,
                                )
                            }

                            Spacer(Modifier.height(13.dp))
                            MarkdownText(ema0.body, onImageClick = { previewUrl = it })

                            Spacer(Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    SectionLabel(text = "回复 ${replies.size}")
                                }
                                if (canModerate) {
                                    Box {
                                        Text(
                                            text = "改变状态",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { stateMenuOpen = true }
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                        )
                                        if (stateMenuOpen) {
                                            EmaStateMenu(
                                                onDismiss = { stateMenuOpen = false },
                                                onChangeState = { state ->
                                                    stateMenuOpen = false
                                                    scope.launch {
                                                        try {
                                                            Shrine.updateEmaState(emaId, state)
                                                            ema = Shrine.ema(emaId)
                                                        } catch (e: Exception) {
                                                            notice = e.toApiMessage()
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            if (replies.isEmpty()) {
                                ShrineEmptyState(note = "没有回复")
                            } else {
                                Column {
                                    replies.forEachIndexed { index, item ->
                                        if (index > 0) {
                                            HairlineDivider()
                                        }
                                        EmaReplyRow(
                                            reply = item,

                                            canDelete = item.authorId == me?.id ||
                                                (me?.role?.canModerate == true && item.authorRole.rank < (me?.role?.rank ?: 0)),
                                            onOpenUser = onOpenUser,
                                            onDelete = { pendingReplyDelete = item },
                                            onOpenImage = { previewUrl = it },
                                        )
                                    }
                                }
                                ShrineListEnd(text = "已经到底了")
                            }

                            Spacer(Modifier.height(120.dp))
                        }

                        ScrollTopOrb(
                            scrollState = emaScroll,
                            onClick = { scope.launch { emaScroll.smoothScrollToTop() } },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 96.dp),
                        )

                        ReplyComposer(
                            value = reply,

                            onValueChange = { if (it.length <= 1000) reply = it },
                            onEmoji = { showEmoji = true },
                            onSend = {
                                val text = reply.trim()
                                if (text.isNotEmpty() && !sending) {
                                    sending = true
                                    scope.launch {
                                        try {
                                            val created = Shrine.addEmaReply(emaId, text)

                                            replies = replies + created
                                            ema = ema0.copy(replyCount = replies.size)
                                            reply = ""
                                            try {
                                                replies = Shrine.emaReplies(emaId)
                                                ema = ema0.copy(replyCount = replies.size)
                                            } catch (e: Exception) {
                                                if (e is kotlinx.coroutines.CancellationException) throw e
                                            }
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            notice = e.toApiMessage()
                                        }
                                        sending = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun EmaMoreMenu(
    canReport: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onReport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        if (canReport) {
            DropdownMenuItem(
                text = { Text("举报") },
                leadingIcon = {
                    Icon(Icons.Rounded.Flag, null, modifier = Modifier.size(18.dp))
                },
                onClick = onReport,
            )
        }
        if (canEdit) {
            DropdownMenuItem(
                text = { Text("编辑") },
                leadingIcon = {
                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
                },
                onClick = onEdit,
            )
        }
        if (canDelete) {
            DropdownMenuItem(
                text = { Text("删除") },
                leadingIcon = {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                },
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun EmaStateMenu(
    onDismiss: () -> Unit,
    onChangeState: (EmaState) -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        EmaState.entries.forEach { state ->
            DropdownMenuItem(
                text = { Text("改变状态：${state.label}") },
                onClick = { onChangeState(state) },
            )
        }
    }
}

@Composable
private fun EmaAuthorRow(
    ema: dev.arisu.shrine.data.model.Ema,
    onOpenUser: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ArisuAvatar(
            seed = 0,
            name = ema.authorName,
            size = 30.dp,
            role = ema.authorRole,
            ring = true,
            url = ema.authorAvatar,
            modifier = Modifier.clickable { onOpenUser(ema.authorId) },
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ema.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { onOpenUser(ema.authorId) },
                )
                Spacer(Modifier.width(5.dp))
                RoleTag(role = ema.authorRole)
            }
            Text(
                text = ema.createdAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmaRoleBadge(role: Role) {
    RoleTag(role)
}

@Composable
private fun EmaReplyRow(
    reply: EmaReply,
    canDelete: Boolean,
    onOpenUser: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenImage: (String) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ArisuAvatar(
            seed = 0,
            name = reply.authorName,
            size = 30.dp,
            role = reply.authorRole,
            ring = true,
            url = reply.authorAvatar,
            modifier = Modifier.clickable { onOpenUser(reply.authorId) },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = reply.authorName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { onOpenUser(reply.authorId) },
                    )
                    Spacer(Modifier.width(5.dp))
                    RoleTag(role = reply.authorRole)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = reply.createdAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            MarkdownText(
                reply.content,
                style = MaterialTheme.typography.bodyMedium,
                onImageClick = onOpenImage,
            )
        }
        if (canDelete) {
            Box {
                IconButton(
                    onClick = { menu = true },
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onEmoji: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = value.isNotBlank()
    Row(
        modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 14.dp)
            .padding(bottom = 12.dp)
            .glassPanel(
                shape = RoundedCornerShape(26.dp),
                elevation = 14.dp,
                tint = NeuBaseHigh,
                alpha = 0.92f,
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onEmoji) {
            Icon(
                imageVector = Icons.Rounded.EmojiEmotions,
                contentDescription = "表情",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        ArisuTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "写下您的回复…",
            containerColor = NeuInset,
            modifier = Modifier.weight(1f),

            singleLine = false,
            maxLines = 5,
        )

        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .then(
                    if (canSend) Modifier.accentRaised(shape = CircleShape, color = dev.arisu.shrine.core.design.VermilionButton)
                    else Modifier.neuInset(shape = CircleShape, color = NeuInset)
                )
                .clickable(enabled = canSend) { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Send,
                contentDescription = "发送",
                tint = if (canSend) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
    }
}
