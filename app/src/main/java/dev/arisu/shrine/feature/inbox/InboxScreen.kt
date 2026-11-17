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

/* ══════════════════════════════════════════════════════════════════
 * 社务箱
 *
 * 通知来自 Shrine.notices。点击进详情前先标已读；
 * 左滑删除（弹确认框，防误划），右滑在已读/未读之间切换，
 * 两个动作都直接改 Shrine 的内存状态，因此列表会立即重组。
 * 列表本身是一张白卡 + 内缩发丝线，不再逐条套卡片。
 * ══════════════════════════════════════════════════════════════════ */

/**
 * 社务箱的分类标签。
 *
 * category 是服务端的分类过滤，only 是额外的状态过滤（""=不过滤）：
 * - 全部：不过滤
 * - 未读：还没看的（快速清掉红点）
 * - 收藏：详情页里收藏过的通知
 * - 回复/@：REPLY + TRACKING + FOLLOW
 *   追踪与关注本质上也是「别人对你的动态」，和回复放在一起看更顺手
 * - 系统：SYSTEM + APPLICATION + WARN + MUTE
 *   奉纳者申请、警告与禁言都是站务流程通知，归到系统一侧
 * - 举报：REPORT（举报结果会发给举报人本人，全员可见）
 */
private enum class InboxTab(val label: String, val category: String, val only: String) {
    ALL("全部", "all", ""),
    UNREAD("未读", "all", "unread"),
    STARRED("收藏", "all", "starred"),
    REPLY("回复@", "reply", ""),
    SYSTEM("系统", "system", ""),
    REPORT("举报", "report", ""),
}

/**
 * 社务箱页面。
 *
 * 标题栏右侧「全部已读」→ Shrine.markAllRead()；
 * 分类用 PillTabs，列表用 SwipeToDismissBox 支持左滑删除（带确认）/ 右滑已读切换。
 * [refreshKey] 由详情页在收藏/未读/删除后递增，返回时社务箱据此重新拉取。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    refreshKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val tabs = remember { InboxTab.entries }
    // 进通知详情再返回，分类停留在原处
    var tab by rememberSaveable { mutableStateOf(InboxTab.ALL) }
    val scope = rememberCoroutineScope()

    // 首帧先摆上次那份（进社务箱不再先空一下），网络回来再覆盖
    val firstPaint = remember { Shrine.peekNotices() }
    var notices by remember { mutableStateOf(firstPaint?.items ?: emptyList()) }
    var unread by remember { mutableStateOf(firstPaint?.unread ?: 0) }
    var total by remember { mutableStateOf(firstPaint?.total ?: 0L) }
    var loading by remember { mutableStateOf(firstPaint == null) }
    var reloadKey by remember { mutableStateOf(0) }
    // 通知不止一页：触底续页（此前只拉前 30 条，页脚却宣称「共 N 条」）
    var page by remember { mutableStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    // 翻页失败给「点击重试」页脚：哨兵是 LaunchedEffect(size)，失败后 size 不变不会自动再触发
    var loadMoreFailed by remember { mutableStateOf(false) }
    // 首载失败与「没有通知」分开：失败给重试入口，不再把断网伪装成空箱
    var failed by remember { mutableStateOf(false) }
    // 请求序号：下拉刷新与切 tab 竞态时，晚到的旧响应不许覆盖新列表
    var loadSeq by remember { mutableStateOf(0) }

    // 左滑弹出的删除确认；null = 没有待确认的删除
    var pendingDelete by remember { mutableStateOf<Notice?>(null) }
    // 页内提示条（如「全部已读」失败时的服务端/网络消息）
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
            // 已有内容时保留旧列表，只有首载无数据才进入失败态
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
                // 刷新（seq 变了）、切 tab 或 page 被重置后，旧页不得落地
                if (seq != loadSeq || tab != snapTab || page != snapPage) return@launch
                page += 1
                notices = (notices + pageData.items).distinctBy { it.id }
                total = pageData.total
                endReached = notices.size >= pageData.total
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                loadMoreFailed = true
            } finally {
                // 旧响应作废的 return@launch 也要复位，否则翻页永久卡死
                loadingMore = false
            }
        }
    }

    LaunchedEffect(tab) {
        // 切 tab 先清残留：旧 tab 的列表不该在新 tab 下闪现。
        // 只挂在 tab 上——下拉刷新/reload 时保留旧内容，load 失败才有得显示
        notices = emptyList()
        loadMoreFailed = false
        // 请求在途显示加载态：否则慢网下新 tab 会先闪一帧「没有通知」
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
                            // 静默失败会让「点了没反应」：至少让用户看到原因
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
                // 距末尾还剩几屏就开始拉下一页（与茶话页同一套）
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
                                    // 已读条目不再重复扣减未读数；失败不改本地，
                                    // 下次刷新以服务器为准（与右滑路径同一套约定）；
                                    // 失败要出声：静默失败会让红点「点了也不消失」
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
                                // 列表点击已标已读，holder 里同步置位：详情页的 LaunchedEffect
                                // 看到 read=true 就不会再发一次 markRead
                                NoticeDetailHolder.current = notice.copy(read = true)
                                onOpenDetail(notice.id)
                            },
                            onSwipeToggleRead = {
                                scope.launch {
                                    // 右滑：已读 ⇄ 未读切换；失败保持原状并提示（弱网下滑了没反应会像失灵）
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
                        // 翻页失败给可点的重试页脚（预取不会自己再触发）
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
                // 成功才落列表：失败时错误显示在弹窗里，弹窗保持打开可重试
                notices = notices.filterNot { it.id == target.id }
                total = (total - 1).coerceAtLeast(0)
                if (!target.read) unread = (unread - 1).coerceAtLeast(0)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * 单条通知的外层：套一层 SwipeToDismissBox，支持左滑删除（带确认）/ 右滑已读切换。
 *
 * 用的是 Material3 稳定 API（`SwipeToDismissBox` + `rememberSwipeToDismissBoxState`），
 * 依赖 material3 ≥ 1.2。`confirmValueChange` 一律返回 false：
 * 动作不停留在「划开」状态，由状态列表重组来体现结果（删除后该行直接消失）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNoticeRow(
    notice: Notice,
    onClick: () -> Unit,
    onSwipeToggleRead: () -> Unit,
    onSwipeDelete: () -> Unit,
) {
    // confirmValueChange 在状态创建时定格，直接捕获调用方 lambda 会拿到旧闭包：
    // 对已读行再右滑仍走 markRead 分支，本地未读数与服务器漂移——经
    // rememberUpdatedState 转一手，每次重组取最新回调，notice.read 才是当下值
    val currentToggleRead by rememberUpdatedState(onSwipeToggleRead)
    val currentDelete by rememberUpdatedState(onSwipeDelete)

    // positionalThreshold 在部分 material3 版本里是 deprecated，只用命名参数以免踩版本差异
    @Suppress("DEPRECATION")
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // 右滑：已读 ⇄ 未读
                    currentToggleRead()
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    // 左滑：删除（弹确认框，防误划一手抖就没了）
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

/* ══════════════════════════════════════════════════════════════════
 * 滑动背景
 * ══════════════════════════════════════════════════════════════════ */

/**
 * 滑动时露出的背景：
 * - StartToEnd（向右滑）显示「标为已读 / 标为未读」，落在左侧，按当前状态切换
 * - EndToStart（向左滑）显示「删除」，落在右侧
 * - Settled 时两边都淡出，正常状态下不可见
 */
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

/* ══════════════════════════════════════════════════════════════════
 * 单条通知
 * ══════════════════════════════════════════════════════════════════ */

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

/** 按 NoticeKind 给出图标与主色：中性凹槽 + 彩色图标，不再铺色块底。 */
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

/** NoticeKind → (图标, 主色)。 */
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

/** 待处理 / 已处理 / 已驳回。 */
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
