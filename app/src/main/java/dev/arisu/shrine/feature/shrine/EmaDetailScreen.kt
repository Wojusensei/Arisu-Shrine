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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.DraftRestoredBanner
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
import dev.arisu.shrine.core.markdown.MARKDOWN_STICKER_ALT
import dev.arisu.shrine.core.markdown.MarkdownText
import dev.arisu.shrine.core.markdown.MdMark
import dev.arisu.shrine.core.markdown.insertMark
import dev.arisu.shrine.data.local.Draft
import dev.arisu.shrine.data.local.DraftStore
import dev.arisu.shrine.data.local.HistoryStore
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.feature.share.ShareCard
import dev.arisu.shrine.feature.share.ShareCardData
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.EmaReply
import dev.arisu.shrine.data.model.EmaState
import dev.arisu.shrine.data.model.Role

/**
 * 绘马详情。
 *
 * 标题栏：返回 + 更多菜单（编辑 / 删除 / 改变状态，按权限显示）
 * 正文：编号 + 标题 + 状态 + 标签 + 发起人 + Markdown 正文
 * 回复：列表 + 底部输入框
 */
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
    // 全屏看图：绘马与回复里的图也能放大、存相册、收进表情夹
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var collectedStickers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reportOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<dev.arisu.shrine.data.model.Ema?>(null) }
    // 回复草稿驻留：串门看发起人主页再回来，写到一半的话还在。
    // 带选区：表情/表情包插在光标处，插完光标跟着内容走，不会把上一张图的 Markdown 拆开
    var reply by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var replyLoaded by rememberSaveable(emaId) { mutableStateOf(false) }
    var replyRestored by rememberSaveable(emaId) { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    // 首帧：先把上次读到的绘马与回帖摆上来（列表点进来几乎无感），网络回来再覆盖
    var ema by remember { mutableStateOf(Shrine.peekEma(emaId)) }
    var replies by remember { mutableStateOf(Shrine.peekEmaReplies(emaId) ?: emptyList()) }
    var loadState by remember { mutableStateOf(if (ema == null) "loading" else "ok") } // loading | ok | missing | fail
    val me by Shrine.me.collectAsStateWithLifecycle()
    var pendingReplyDelete by remember { mutableStateOf<EmaReply?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    fun refresh() {
        scope.launch {
            try {
                ema = Shrine.ema(emaId)
                replies = Shrine.emaReplies(emaId)
                loadState = "ok"
            } catch (e: dev.arisu.shrine.data.remote.ApiError) {
                // 内容已在屏上（缓存直出）时刷新失败不夺走阅读：只有手上空空才进失败/不存在态
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

    LaunchedEffect(emaId) {
        refresh()
        if (!replyLoaded) {
            val uid = Shrine.me.value?.id
            if (uid != null && reply.text.isBlank()) {
                val saved = DraftStore.get(uid, DraftStore.COMMENT_EMA, emaId.toString())
                // 读盘这几毫秒里用户可能已经落笔：那就别再用旧稿盖掉
                if (saved != null && reply.text.isBlank() && saved.body.isNotBlank()) {
                    reply = TextFieldValue(saved.body, TextRange(saved.body.length))
                    replyRestored = true
                }
            }
            // 恢复完再放行自动保存，否则空表单会先把本地那份删掉
            replyLoaded = true
        }
    }

    // 足迹
    LaunchedEffect(ema?.id) {
        val e = ema ?: return@LaunchedEffect
        val uid = Shrine.me.value?.id ?: return@LaunchedEffect
        HistoryStore.visit(
            uid, HistoryStore.EMA, emaId.toString(), e.title,
            e.tags.joinToString(" · ") { it.label },
        )
    }

    // 输入停顿后落盘；清空即删掉本地那份
    LaunchedEffect(reply.text, replyLoaded, ema?.title) {
        if (!replyLoaded) return@LaunchedEffect
        val uid = Shrine.me.value?.id ?: return@LaunchedEffect
        if (reply.text.isBlank()) {
            DraftStore.deleteAsync(uid, DraftStore.COMMENT_EMA, emaId.toString())
            return@LaunchedEffect
        }
        delay(600)
        DraftStore.saveAsync(
            Draft(
                userId = uid,
                kind = DraftStore.COMMENT_EMA,
                targetId = emaId.toString(),
                targetTitle = ema?.title.orEmpty(),
                body = reply.text,
            ),
        )
    }

    val canModerate = me?.role?.canModerate ?: false
    val isAuthor = ema?.authorId == me?.id

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "绘马详情",
            subtitle = ema?.let { "#${it.id}" },
            onBack = onBack,
            actions = {
                // 非作者也要有菜单入口（举报）；作者/管理层看到编辑删除等。
                // 菜单必须挂在触发按钮的 Box 里，否则 DropdownMenu 会按父容器定位弹到别处
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
                                onShare = {
                                    menuOpen = false
                                    val e = menuEma
                                    scope.launch {
                                        try {
                                            ShareCard.share(
                                                context,
                                                ShareCardData(
                                                    kind = "绘马",
                                                    meta = e.tags.joinToString(" · ") { it.label },
                                                    id = "#${e.id}",
                                                    title = e.title,
                                                    body = ShareCard.plainText(e.body),
                                                    author = e.authorName,
                                                    time = e.createdAt,
                                                    images = ShareCard.images(e.body),
                                                ),
                                            )
                                        } catch (ex: Exception) {
                                            if (ex is kotlinx.coroutines.CancellationException) throw ex
                                            notice = ex.toApiMessage()
                                        }
                                    }
                                },
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
                                    // 作者与管理删除都先确认：此前作者路径直接删，
                                    // 手一抖整块绘马就没了（帖子路径一直有确认）
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
                // 管理删除需填理由（与服务端一致）；作者自删不需要
                reasonHint = if (isAuthor) null else "删除理由（必填）",
                onConfirm = { reason ->
                    Shrine.deleteEma(target.id, if (isAuthor) null else reason)
                    deleteTarget = null
                    onBack()
                },
                onDismiss = { deleteTarget = null },
            )
        }

        // 与帖子详情同一套表情面板
        if (showEmoji) {
            EmojiSheet(
                // 插在光标处，并把光标落到插入内容之后；此前是字符串追加，
                // 光标留在旧末尾，接着打字会把上一张表情包的 Markdown 拆坏
                onSelect = { reply = insertMark(reply, MdMark(it)) },
                onSticker = { sticker ->
                    val snippet = "![$MARKDOWN_STICKER_ALT](${sticker.url})\n"
                    reply = insertMark(reply, MdMark(snippet, block = true))
                },
                onDismiss = { showEmoji = false },
            )
        }

        // 全屏看图（绘马正文与回复共用）
        val previewing = previewUrl
        if (previewing != null) {
            dev.arisu.shrine.core.components.ImageViewerDialog(
                url = previewing,
                addedToStickers = previewing in collectedStickers,
                onAddSticker = { imageUrl ->
                    // 成败由看图器自己在底部提示，这里只负责落库与状态
                    Shrine.addSticker(imageUrl)
                    collectedStickers = collectedStickers + imageUrl
                },
                onDismiss = { previewUrl = null },
            )
        }

        // 举报绘马（文案与帖子举报一致）
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

        // 删除绘马回复（作者本人或维护人员，一律先确认）；
        // 维护人员删他人回复需填理由，否则服务端 400 直接卡死在弹窗里
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
                        // 列表页懒加载：此前整页 verticalScroll + 全量组合，回复一多
                        // 进页就要把所有回复（连同每条正文的 Markdown 解析）一次做掉，
                        // 热门绘马开页明显卡。正文/标题仍是一次性组合的头部
                        val emaList = rememberLazyListState()
                        LazyColumn(
                            state = emaList,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            item(key = "header") {
                            Column {
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
                            }
                            }

                            if (replies.isEmpty()) {
                                item(key = "empty") { ShrineEmptyState(note = "没有回复") }
                            } else {
                                itemsIndexed(replies, key = { _, item -> item.id }) { index, item ->
                                    Column {
                                        if (index > 0) {
                                            HairlineDivider()
                                        }
                                        EmaReplyRow(
                                            reply = item,
                                            // 层级门控与服务端 requireOver 同规：
                                            // 维护人员对平级及以上回复不显示删除，省得点了必 403
                                            canDelete = item.authorId == me?.id ||
                                                (me?.role?.canModerate == true && item.authorRole.rank < (me?.role?.rank ?: 0)),
                                            onOpenUser = onOpenUser,
                                            onDelete = { pendingReplyDelete = item },
                                            onOpenImage = { previewUrl = it },
                                        )
                                    }
                                }
                                item(key = "end") { ShrineListEnd(text = "已经到底了") }
                            }

                            // 给悬浮的回复条留出空间，保证最后一条不会被盖住
                            item(key = "tail") { Spacer(Modifier.height(120.dp)) }
                        }

                        ScrollTopOrb(
                            listState = emaList,
                            onClick = { scope.launch { emaList.smoothScrollToTop() } },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 96.dp),
                        )

                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            if (replyRestored) {
                                DraftRestoredBanner(onDismiss = { replyRestored = false })
                            }
                            ReplyComposer(
                                value = reply,
                                // 服务端回复上限 1000 字，超出会被整条打回
                                onValueChange = { if (it.text.length <= 1000) reply = it },
                                onEmoji = { showEmoji = true },
                                onSend = {
                                    val text = reply.text.trim()
                                    if (text.isNotEmpty() && !sending) {
                                        sending = true
                                        scope.launch {
                                            try {
                                                val created = Shrine.addEmaReply(emaId, text)
                                                // 发送成功即乐观落列表并清稿：若等「重取列表」都成功才清，
                                                // 重取失败会诱导用户对着已发出的回复再发一遍
                                                replies = replies + created
                                                ema = ema0.copy(replyCount = replies.size)
                                                reply = TextFieldValue("")
                                                Shrine.me.value?.id?.let {
                                                    DraftStore.deleteAsync(it, DraftStore.COMMENT_EMA, emaId.toString())
                                                }
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
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

}

/** 更多菜单：举报（人人可见，作者除外）/ 编辑 / 删除。状态改变走正文里的独立菜单。 */
@Composable
private fun EmaMoreMenu(
    canReport: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("生成分享卡片") },
            leadingIcon = {
                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
            },
            onClick = onShare,
        )
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

/** 状态菜单：维护人员的「改变状态」独立下拉，挂在正文触发点上。 */
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

/** 发起人身份标签。 */
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
                // 与评论区同一套对齐：昵称+徽标占弹性槽位，日期钉在最右，
                // 名字长短不改变日期的横向位置
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

/** 底部回复输入条：表情 · 输入框 · 朱色圆形发送（与帖子详情同一套结构）。 */
@Composable
private fun ReplyComposer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onEmoji: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = value.text.isNotBlank()
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
            // 回车即换行（与评论区同一套），不再收起键盘
            singleLine = false,
            maxLines = 5,
        )
        // 间隔不可省：紧贴时发送键的凸起阴影会压到输入框
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
