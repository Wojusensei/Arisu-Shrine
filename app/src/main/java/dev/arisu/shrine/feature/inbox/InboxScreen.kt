package dev.arisu.shrine.feature.inbox

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Markunread
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.CurrencyYen
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuConfirmDialog
import dev.arisu.shrine.core.components.FadeInOut
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.PillTabs
import dev.arisu.shrine.core.components.RedDot
import dev.arisu.shrine.core.components.SectionCard
import dev.arisu.shrine.core.components.ScrollTopOrb
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineLoadGate
import dev.arisu.shrine.core.components.ShrinePullRefreshBox
import dev.arisu.shrine.core.components.ShrineStaleBanner
import dev.arisu.shrine.core.components.ShrineIcons
import dev.arisu.shrine.core.components.ShrineListEnd
import dev.arisu.shrine.core.components.ShrineNoticeBar
import dev.arisu.shrine.core.components.PrefetchOnApproach
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.components.smoothScrollToTop
import dev.arisu.shrine.core.components.softCard
import dev.arisu.shrine.core.design.Gold
import dev.arisu.shrine.core.design.GoldSoft
import dev.arisu.shrine.core.design.Indigo
import dev.arisu.shrine.core.design.IndigoSoft
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.Plum
import dev.arisu.shrine.core.design.Slate
import dev.arisu.shrine.core.design.SlateSoft
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.data.model.Notice
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.NoticeKind
import dev.arisu.shrine.data.model.NoticeStatus

private enum class InboxTab(val label: String, val category: String, val only: String) {
    ALL("全部", "all", ""),
    UNREAD("未读", "all", "unread"),
    STARRED("收藏", "all", "starred"),
    REPLY("回复@", "reply", ""),
    SYSTEM("系统", "system", ""),
    REPORT("举报", "report", ""),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    refreshKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val tabs = remember { InboxTab.entries }

    var tab by rememberSaveable { mutableStateOf(InboxTab.ALL) }
    val scope = rememberCoroutineScope()

    val firstPaint = remember { Shrine.peekNotices() }
    var notices by remember { mutableStateOf(firstPaint?.items ?: emptyList()) }
    var unread by remember { mutableStateOf(firstPaint?.unread ?: 0) }
    var total by remember { mutableStateOf(firstPaint?.total ?: 0L) }
    var loading by remember { mutableStateOf(firstPaint == null) }
    var reloadKey by remember { mutableStateOf(0) }

    var page by remember { mutableStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    var loadMoreFailed by remember { mutableStateOf(false) }

    var failed by remember { mutableStateOf(false) }

    var loadSeq by remember { mutableStateOf(0) }

    var pendingDelete by remember { mutableStateOf<Notice?>(null) }

    var bar by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        val seq = ++loadSeq
        try {
            val pageData = Shrine.notices(tab.category, tab.only)
            if (seq != loadSeq) return
            notices = pageData.items
            unread = pageData.unread
            total = pageData.total
            page = 1
            endReached = notices.size >= total
            failed = false
            loadMoreFailed = false
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (seq != loadSeq) return

            if (notices.isEmpty()) failed = true
        }
        loading = false
    }

    fun loadMore() {
        if (loadingMore || endReached || loadMoreFailed || notices.isEmpty()) return
        loadingMore = true
        val seq = loadSeq
        val snapTab = tab
        val snapPage = page
        scope.launch {
            try {
                val pageData = Shrine.notices(snapTab.category, snapTab.only, page = snapPage + 1)

                if (seq != loadSeq || tab != snapTab || page != snapPage) return@launch
                page += 1
                notices = (notices + pageData.items).distinctBy { it.id }
                total = pageData.total
                endReached = notices.size >= pageData.total
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                loadMoreFailed = true
            } finally {

                loadingMore = false
            }
        }
    }

    LaunchedEffect(tab) {

        notices = emptyList()
        loadMoreFailed = false

        loading = true
    }
    LaunchedEffect(tab, reloadKey, refreshKey) { load() }

    val unreadCount = unread

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "社务箱",
            subtitle = "未读 $unreadCount 条 · 共 $total 条",
            onBack = onBack,
            actions = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            Shrine.markAllRead()
                            reloadKey++
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e

                            bar = e.toApiMessage()
                        }
                    }
                }) {
                    Text(
                        text = "全部已读",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )

        ShrineStaleBanner()

        ShrineNoticeBar(
            text = bar,
            onDismiss = { bar = null },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        PillTabs(
            items = tabs,
            selected = tab,
            onSelect = { tab = it },
            label = { it.label },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            ShrineLoadGate(loading = loading, modifier = Modifier.fillMaxSize()) {
            if (!loading) {
                if (notices.isEmpty() && failed) {
                    ShrineEmptyState(
                        note = "加载失败，点击重试",
                        onClick = { loading = true; reloadKey++ },
                    )
                } else if (notices.isEmpty()) {
                    ShrineEmptyState(
                        note = when (tab) {
                            InboxTab.UNREAD -> "没有未读通知"
                            InboxTab.STARRED -> "还没有收藏的通知"
                            else -> "没有通知"
                        }
                    )
                } else {
                    ShrinePullRefreshBox(
                        onRefresh = { load() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                val inboxListState = rememberLazyListState()

                PrefetchOnApproach(inboxListState, notices.size) { loadMore() }
                SectionCard(Modifier.fillMaxSize()) {
                LazyColumn(modifier = Modifier.fillMaxSize(), state = inboxListState) {
                    itemsIndexed(
                        notices,
                        key = { _, notice -> notice.id },
                        contentType = { _, _ -> "notice" },
                    ) { index, notice ->
                        SwipeableNoticeRow(
                            notice = notice,
                            onClick = {
                                scope.launch {

                                    if (!notice.read) {
                                        val ok = try {
                                            Shrine.markRead(notice.id); true
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            bar = e.toApiMessage(); false
                                        }
                                        if (ok) {
                                            notices = notices.map {
                                                if (it.id == notice.id) it.copy(read = true) else it
                                            }
                                            unread = (unread - 1).coerceAtLeast(0)
                                        }
                                    }
                                }

                                NoticeDetailHolder.current = notice.copy(read = true)
                                onOpenDetail(notice.id)
                            },
                            onSwipeToggleRead = {
                                scope.launch {

                                    if (notice.read) {
                                        val ok = try {
                                            Shrine.markUnread(notice.id); true
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            bar = e.toApiMessage(); false
                                        }
                                        if (ok) {
                                            notices = notices.map {
                                                if (it.id == notice.id) it.copy(read = false) else it
                                            }
                                            unread += 1
                                        }
                                    } else {
                                        val ok = try {
                                            Shrine.markRead(notice.id); true
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            bar = e.toApiMessage(); false
                                        }
                                        if (ok) {
                                            notices = notices.map {
                                                if (it.id == notice.id) it.copy(read = true) else it
                                            }
                                            unread = (unread - 1).coerceAtLeast(0)
                                        }
                                    }
                                }
                            },
                            onSwipeDelete = { pendingDelete = notice },
                        )
                        if (index != notices.lastIndex) {
                            HairlineDivider(startIndent = 68.dp)
                        }
                    }

                    if (endReached || notices.isEmpty()) {
                        item { ShrineListEnd(text = "已经到底了 · 共 $total 条") }
                    } else if (loadMoreFailed) {

                        item(key = "load-more-retry") {
                            Text(
                                text = "加载更多失败，点击重试",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { loadMoreFailed = false; loadMore() }
                                    .padding(vertical = 28.dp)
                                    .fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                }
                ScrollTopOrb(
                    listState = inboxListState,
                    onClick = { scope.launch { inboxListState.smoothScrollToTop() } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 18.dp),
                )
                    }
                }
            }
            }
        }
    }

    pendingDelete?.let { target ->
        ArisuConfirmDialog(
            title = "删除这条通知？",
            body = "删除后无法恢复。",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                Shrine.deleteNotice(target.id)

                notices = notices.filterNot { it.id == target.id }
                total = (total - 1).coerceAtLeast(0)
                if (!target.read) unread = (unread - 1).coerceAtLeast(0)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNoticeRow(
    notice: Notice,
    onClick: () -> Unit,
    onSwipeToggleRead: () -> Unit,
    onSwipeDelete: () -> Unit,
) {

    val currentToggleRead by rememberUpdatedState(onSwipeToggleRead)
    val currentDelete by rememberUpdatedState(onSwipeDelete)

    @Suppress("DEPRECATION")
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {

                    currentToggleRead()
                }

                SwipeToDismissBoxValue.EndToStart -> {

                    currentDelete()
                }

                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            SwipeActionBackground(dismissState.targetValue, notice.read)
        },
    ) {
        NoticeRow(notice = notice, onClick = onClick)
    }
}

@Composable
private fun SwipeActionBackground(direction: SwipeToDismissBoxValue, read: Boolean) {
    val showToggle = direction == SwipeToDismissBoxValue.StartToEnd
    val showDelete = direction == SwipeToDismissBoxValue.EndToStart
    if (!showToggle && !showDelete) return

    if (showDelete) {
        Row(
            Modifier
                .fillMaxSize()
                .background(SlateSoft)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "删除",
                tint = Slate,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "删除",
                style = MaterialTheme.typography.labelLarge,
                color = Slate,
                fontWeight = FontWeight.SemiBold,
            )
        }
    } else {
        val label = if (read) "标为未读" else "标为已读"
        val icon = if (read) Icons.Rounded.Markunread else Icons.Rounded.Check
        Row(
            Modifier
                .fillMaxSize()
                .background(IndigoSoft)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Indigo,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Indigo,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun NoticeRow(
    notice: Notice,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        NoticeKindIcon(notice.kind)

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notice.title.ifEmpty { notice.summary },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (notice.starred) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = "已收藏",
                        tint = Gold,
                        modifier = Modifier.size(13.dp),
                    )
                }
                if (!notice.read) {
                    Spacer(Modifier.width(8.dp))
                    RedDot(size = 8.dp)
                }
            }

            Spacer(Modifier.height(4.dp))

            if (notice.title.isNotEmpty() && notice.summary != notice.title) {
                Text(
                    text = notice.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notice.createdAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (notice.status != null) {
                    Spacer(Modifier.width(8.dp))
                    NoticeStatusPill(notice.status)
                }
            }
        }
    }
}

@Composable
private fun NoticeKindIcon(kind: NoticeKind) {
    val (icon, tint) = noticeKindVisual(kind)

    Box(
        Modifier
            .size(40.dp)
            .softCard(shape = RoundedCornerShape(12.dp), elevation = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = kind.label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

internal fun noticeKindVisual(kind: NoticeKind): Pair<ImageVector, Color> = when (kind) {
    NoticeKind.REPLY -> Icons.Rounded.ChatBubbleOutline to Indigo
    NoticeKind.UP -> Icons.Rounded.ThumbUp to Vermilion
    NoticeKind.SYSTEM -> Icons.Rounded.Campaign to Gold
    NoticeKind.REPORT -> Icons.Rounded.Flag to Slate
    NoticeKind.APPLICATION -> ShrineIcons.Ema to Plum
    NoticeKind.TRACKING -> Icons.Rounded.Notifications to Indigo
    NoticeKind.FOLLOW -> Icons.Rounded.Person to Indigo
    NoticeKind.WARN -> Icons.Rounded.Warning to Gold
    NoticeKind.MUTE -> Icons.Rounded.VolumeOff to Slate
    NoticeKind.OFFER -> Icons.Rounded.CurrencyYen to Gold
}

@Composable
private fun NoticeStatusPill(status: NoticeStatus) {
    SoftPill(
        text = status.label,
        color = status.accentColor(),
        background = status.accentBackground(),
    )
}

private fun NoticeStatus.accentColor(): Color = when (this) {
    NoticeStatus.PENDING -> Gold
    NoticeStatus.DONE -> Indigo
    NoticeStatus.REJECTED -> Slate
}

private fun NoticeStatus.accentBackground(): Color = when (this) {
    NoticeStatus.PENDING -> GoldSoft
    NoticeStatus.DONE -> IndigoSoft
    NoticeStatus.REJECTED -> SlateSoft
}
