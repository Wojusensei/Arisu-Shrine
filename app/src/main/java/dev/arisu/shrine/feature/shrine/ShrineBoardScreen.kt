package dev.arisu.shrine.feature.shrine

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.ArisuGhostButton
import dev.arisu.shrine.core.components.ArisuPrimaryButton
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.EmaCard
import dev.arisu.shrine.core.components.FadeInOut
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.MistWatermark
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.RoleTag
import dev.arisu.shrine.core.components.SectionCard
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.SheetHeader
import dev.arisu.shrine.core.components.ScrollTopOrb
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineLoadGate
import dev.arisu.shrine.core.components.ShrineRefreshingHint
import dev.arisu.shrine.core.components.BoardMemory
import dev.arisu.shrine.core.components.ScrollTopBus
import dev.arisu.shrine.core.components.ShrinePullRefreshBox
import dev.arisu.shrine.core.components.ShrineStaleBanner
import dev.arisu.shrine.core.components.PrefetchOnApproach
import dev.arisu.shrine.core.components.ShrineLoadingState
import dev.arisu.shrine.core.components.ShrineListEnd
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.components.TopBarIcon
import dev.arisu.shrine.core.components.UnderlineTabs
import dev.arisu.shrine.core.components.YinYangOrb
import dev.arisu.shrine.core.components.accentRaised
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.components.smoothScrollToTop
import dev.arisu.shrine.core.components.softCard
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuBase
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.markdown.MarkdownText
import dev.arisu.shrine.data.model.Ema
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.nav.Routes
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.EmaState
import dev.arisu.shrine.data.model.EmaTag
import dev.arisu.shrine.data.model.ReleaseNote
import dev.arisu.shrine.data.model.Role
import dev.arisu.shrine.data.model.User
import kotlinx.coroutines.delay

/* ══════════════════════════════════════════════════════════════════
 * 页面内部使用的小类型
 * ══════════════════════════════════════════════════════════════════ */

/** 列表三态：加载中 / 成功 / 失败。 */
private sealed interface ListState<out T> {
    data object Loading : ListState<Nothing>
    data class Ok<T>(val items: List<T>, val total: Long) : ListState<T>
    data class Fail(val message: String) : ListState<Nothing>
}

// 祝部各子板块内容常驻：切走再切回直接显示上次内容、后台静默刷新（QQ 式板块切换）
private data class EmaQuery(val tags: List<EmaTag>, val states: List<EmaState>, val sort: EmaSort)

private const val EMA_PAGE_SIZE = 20
private val emaMemory = BoardMemory<EmaQuery, ListState<Ema>>()
private val logMemory = BoardMemory<String, List<ReleaseNote>>()

private data class StaffSnapshot(
    val groups: List<Shrine.StaffGroup>,
    val myApplication: dev.arisu.shrine.data.remote.MyApplicationRes,
)
private val staffMemory = BoardMemory<String, StaffSnapshot>()

/** 三个子板块。 */
private enum class ShrineTab(val label: String) {
    EMA("绘马"),
    STAFF("奉仕会"),
    LOG("神社日志"),
}

/** 绘马列表的时间排序。 */
internal enum class EmaSort(val label: String) {
    NEWEST("最新"),
    OLDEST("最旧"),
}

private val EmaTag.wire: String
    get() = when (this) {
        EmaTag.ENHANCE -> "enhance"
        EmaTag.BUG -> "bug"
        EmaTag.QUESTIONS -> "questions"
        EmaTag.TIPS -> "tips"
    }

private val EmaState.wire: String
    get() = when (this) {
        EmaState.OPEN -> "open"
        EmaState.IN_PROGRESS -> "in_progress"
        EmaState.RESOLVED -> "resolved"
        EmaState.CLOSED -> "closed"
    }

/** 临时奉仕申请可投入的时间档位。 */
private val EffortOptions = listOf(
    "1-3 小时",
    "3-5 小时",
    "5-10 小时",
    "10 小时以上",
)

/** 检查更新的结果：有新版本（带发布信息）/ 已是最新 / 失败。 */
private sealed interface CheckState {
    data class UpdateAvailable(
        val release: dev.arisu.shrine.data.model.ReleaseNote,
        val downloadUrl: String? = null,
    ) : CheckState
    data object UpToDate : CheckState
    data object Failed : CheckState
}

/** 一行小提示，代替 android.widget.Toast。 */
private data class ToastMessage(val text: String, val key: Long)


/* ══════════════════════════════════════════════════════════════════
 * 神社祝部
 * ══════════════════════════════════════════════════════════════════ */

/**
 * 神社祝部。
 *
 * 标题栏：神社祝部 + 筛选 / 社务箱
 * 子板块：绘马 / 奉仕会 / 神社日志
 */
@Composable
fun ShrineBoardScreen(
    onOpenUser: (String) -> Unit = {},
    unreadCount: Int,
    onOpenEma: (Int) -> Unit,
    onNewEma: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenChangelog: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 截图工具用：直接打开第 N 个子板块 */
    initialTabIndex: Int = 0,
) {
    // 子板块可跨页面驻留：进他人主页再返回，仍停在离开时的板块（此前用 remember，
    // 返回即重置回绘马，从奉仕会成员串门回来像被弹回了大厅）
    var tab by rememberSaveable {
        mutableStateOf(ShrineTab.entries[initialTabIndex.coerceIn(0, ShrineTab.entries.size - 1)])
    }
    var filterOpen by remember { mutableStateOf(false) }

    val tagFilter by rememberSaveable(
        stateSaver = listSaver<SnapshotStateList<EmaTag>, String>(
            save = { it.map(EmaTag::name) },
            restore = { saved -> saved.mapNotNull { n -> runCatching { EmaTag.valueOf(n) }.getOrNull() }.toMutableStateList() },
        ),
    ) { mutableStateOf(mutableStateListOf()) }
    val stateFilter by rememberSaveable(
        stateSaver = listSaver<SnapshotStateList<EmaState>, String>(
            save = { it.map(EmaState::name) },
            restore = { saved -> saved.mapNotNull { n -> runCatching { EmaState.valueOf(n) }.getOrNull() }.toMutableStateList() },
        ),
    ) { mutableStateOf(mutableStateListOf()) }
    var sort by rememberSaveable { mutableStateOf(EmaSort.NEWEST) }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "神社祝部",
            large = true,
            subtitle = "绘马 · 奉仕会 · 神社日志",
            actions = {
                TopBarIcon(Icons.Rounded.FilterAlt, "筛选", { filterOpen = true })
                TopBarIcon(Icons.Rounded.Mail, "社务箱", onOpenInbox, badgeCount = unreadCount)
            },
        )

        UnderlineTabs(
            items = ShrineTab.entries,
            selected = tab,
            onSelect = { tab = it },
            label = { it.label },
        )

        ShrineStaleBanner()

        // 三个子板块之间淡入淡出
        Crossfade(
            targetState = tab,
            animationSpec = tween(220),
            label = "shrineTab",
        ) { current ->
        when (current) {
            ShrineTab.EMA -> EmaSection(
                tags = tagFilter,
                states = stateFilter,
                sort = sort,
                onSortChange = { sort = it },
                onOpenEma = onOpenEma,
                onNewEma = onNewEma,
            )

            ShrineTab.STAFF -> StaffSection(onOpenUser)

            ShrineTab.LOG -> ChangelogSection(onOpenChangelog = onOpenChangelog)
        }
        }
    }

    if (filterOpen) {
        EmaFilterSheet(
            states = stateFilter,
            sort = sort,
            onSortChange = { sort = it },
            onDismiss = { filterOpen = false },
        )
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 绘马
 * ══════════════════════════════════════════════════════════════════ */

@Composable
private fun EmaSection(
    tags: MutableList<EmaTag>,
    states: MutableList<EmaState>,
    sort: EmaSort,
    onSortChange: (EmaSort) -> Unit,
    onOpenEma: (Int) -> Unit,
    onNewEma: () -> Unit,
) {
    // 首帧：内存记忆 → 落盘缓存 → 都没有才转圈（冷启动进板块也不再先空一下）
    var state by remember {
        mutableStateOf(
            emaMemory.get(EmaQuery(tags.toList(), states.toList(), sort), Shrine.me.value?.id)
                ?: Shrine.peekEmas(
                    tags = tags.map { it.wire },
                    states = states.map { it.wire },
                    sort = if (sort == EmaSort.OLDEST) "old" else "new",
                    page = 1,
                    pageSize = EMA_PAGE_SIZE,
                )?.let { ListState.Ok(it.items, it.total) }
                ?: ListState.Loading
        )
    }
    val scope = rememberCoroutineScope()

    // 首载与下拉刷新共用同一份数据流。刷新失败时保留旧列表，只有首载无数据才进入失败态——
    // 之前下拉一次就整页替换成加载态，接口一慢「少女祈祷中」就永远停在屏上。
    // 请求序号：筛选连点/下拉刷新与晚到的旧响应竞态时，只有最新一次请求允许落地
    var loadSeq by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreFailed by remember { mutableStateOf(false) }
    // 刷新在途标志：load 会先把 page 置成按条数反推的页码，若此刻触底加载，
    // seq/page 快照守卫恒通过，会把「旧筛选列表 + 新筛选第二页」拼在一起
    var refreshing by remember { mutableStateOf(false) }

    // 多选筛选原样传服务端查全库：此前是「拉前 50 条回本地滤」，第 51 条起的
    // 绘马永远不可见，页脚的「共 N 条」也在撒谎
    val load: suspend () -> Unit = {
        val seq = ++loadSeq
        refreshing = true
        try {
            val restored = state as? ListState.Ok
            page = if (restored == null || restored.items.isEmpty()) 1
            else (restored.items.size + EMA_PAGE_SIZE - 1) / EMA_PAGE_SIZE
            val result = Shrine.emas(
                tags = tags.map { it.wire },
                states = states.map { it.wire },
                sort = if (sort == EmaSort.OLDEST) "old" else "new",
                page = 1,
                pageSize = EMA_PAGE_SIZE,
            )
            if (seq == loadSeq) {
                page = 1
                loadMoreFailed = false
                endReached = result.items.size >= result.total
                val fresh = ListState.Ok(result.items, result.total)
                // 内容没变就不换对象：每次进屏都会重拉一页，等值时保持旧实例，
                // 转场期间不再被一次全列表重组打断（data class 结构相等）
                if ((state as? ListState.Ok) != fresh) state = fresh
                Shrine.me.value?.id?.let {
                    emaMemory.put(EmaQuery(tags.toList(), states.toList(), sort), it, state)
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (seq == loadSeq && state is ListState.Loading) state = ListState.Fail(e.toApiMessage())
        } finally {
            if (seq == loadSeq) refreshing = false
        }
    }

    fun loadMore() {
        if (refreshing || loadingMore || endReached) return
        val current = state as? ListState.Ok ?: return
        if (current.items.isEmpty()) return
        loadingMore = true
        val seq = loadSeq
        val snapPage = page
        scope.launch {
            try {
                val result = Shrine.emas(
                    tags = tags.map { it.wire },
                    states = states.map { it.wire },
                    sort = if (sort == EmaSort.OLDEST) "old" else "new",
                    page = snapPage + 1,
                    pageSize = EMA_PAGE_SIZE,
                )
                if (seq != loadSeq || page != snapPage) return@launch
                page += 1
                endReached = (page - 1) * EMA_PAGE_SIZE + result.items.size >= result.total
                val merged = ListState.Ok((current.items + result.items).distinctBy { it.id }, result.total)
                state = merged
                loadMoreFailed = false
                Shrine.me.value?.id?.let {
                    emaMemory.put(EmaQuery(tags.toList(), states.toList(), sort), it, merged)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                loadMoreFailed = true
            } finally {
                loadingMore = false
            }
        }
    }

    // 换标签 / 状态 / 排序时：亮一次加载态并滚回顶部；只认真正的切换——
    // 进绘马详情再返回时 effect 会重跑一次，那会儿清空/滚顶会打断读者（与茶话页同规矩）
    var emaFilterKey by rememberSaveable { mutableStateOf("") }
    val emaListState = rememberLazyListState()
    LaunchedEffect(tags.toList(), states.toList(), sort) {
        val key = tags.joinToString(",") { it.name } + "|" + states.joinToString(",") { it.name } + "|" + sort.name
        if (emaFilterKey.isNotEmpty() && emaFilterKey != key) {
            state = ListState.Loading
            emaListState.scrollToItem(0)
        }
        emaFilterKey = key
        load()
    }

    ShrinePullRefreshBox(onRefresh = { load() }, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            EmaFilterBar(
                states = states,
                sort = sort,
                onClearSort = { onSortChange(EmaSort.NEWEST) },
            )

            // 分类页签：四类绘马分开看，单选（空 = 全部不筛）。
            // 原先的分类藏在筛选弹层的多选标签里，入口太深，等于没有分类
            UnderlineTabs(
                items = EmaTagTabs,
                selected = tags.firstOrNull(),
                onSelect = { picked ->
                    tags.clear()
                    if (picked != null) tags.add(picked)
                },
                label = { it?.label ?: "全部" },
                compact = true,
            )

            Box(Modifier.fillMaxSize()) {
                LaunchedEffect(Unit) {
                    ScrollTopBus.events.collect { route ->
                        if (route == Routes.SHRINE) scope.launch { emaListState.smoothScrollToTop() }
                    }
                }
                ShrineLoadGate(loading = state is ListState.Loading, modifier = Modifier.fillMaxSize()) {
                when (val s = state) {
                    is ListState.Loading -> {}
                    is ListState.Fail -> ShrineEmptyState(note = "加载失败，点击重试", onClick = { scope.launch { load() } })
                    is ListState.Ok -> {
                        if (s.items.isEmpty()) {
                            ShrineEmptyState(note = "没有绘马")
                        } else {
                            // 距末尾还剩几屏就开始拉下一页（与茶话页同一套）
                            PrefetchOnApproach(emaListState, s.items.size) { loadMore() }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = emaListState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(s.items, key = { it.id }, contentType = { "ema" }) { ema ->
                                    EmaCard(ema = ema, onClick = { onOpenEma(ema.id) })
                                }
                                item(key = "footer") {
                                    when {
                                        loadingMore -> ShrineRefreshingHint()
                                        endReached -> ShrineListEnd(text = "已经到底了 · 共 ${s.total} 条")
                                        loadMoreFailed -> Text(
                                            text = "加载更多失败，点击重试",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable { loadMoreFailed = false; loadMore() }
                                                .padding(vertical = 28.dp),
                                        )
                                        else -> ShrineListEnd(text = "上滑加载更多")
                                    }
                                }
                            }
                        }
                    }
                }
                }
                ScrollTopOrb(
                    listState = emaListState,
                    onClick = { scope.launch { emaListState.smoothScrollToTop() } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 118.dp),
                )
            }
        }
        FloatingActionButton(
            onClick = onNewEma,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            shape = RoundedCornerShape(22.dp),
            containerColor = dev.arisu.shrine.core.design.VermilionButton,
            contentColor = Color.White,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "发绘马")
        }
    }
}

/** 绘马分类页签：null = 全部（不筛），其余对应四个标签 */
private val EmaTagTabs: List<EmaTag?> = listOf(null) + EmaTag.entries

/** 已生效的筛选条件，点小叉即可移除。分类走页签，不在这里重复列出。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmaFilterBar(
    states: MutableList<EmaState>,
    sort: EmaSort,
    onClearSort: () -> Unit,
) {
    val active = states.isNotEmpty() || sort != EmaSort.NEWEST
    if (!active) return

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "筛选：",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val pillBg = MaterialTheme.colorScheme.surfaceContainerHigh
            val pillFg = MaterialTheme.colorScheme.onSurfaceVariant
            states.toList().forEach { state ->
                SoftPill(
                    text = "${state.label} ×",
                    color = pillFg,
                    background = pillBg,
                    modifier = Modifier.clickable { states.remove(state) },
                )
            }
            if (sort != EmaSort.NEWEST) {
                SoftPill(
                    text = "最旧 ×",
                    color = pillFg,
                    background = pillBg,
                    modifier = Modifier.clickable { onClearSort() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmaFilterSheet(
    states: MutableList<EmaState>,
    sort: EmaSort,
    onSortChange: (EmaSort) -> Unit,
    onDismiss: () -> Unit,
) {
    val draftStates = remember { mutableStateListOf<EmaState>().also { it.addAll(states) } }
    var draftSort by remember { mutableStateOf(sort) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            SheetHeader(
                title = "筛选绘马",
                subtitle = "按状态筛选，或切换时间排序。",
                onClose = onDismiss,
            )

            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionLabel(text = "状态")
                SectionCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EmaState.entries.forEach { state ->
                            FilterPill(
                                text = state.label,
                                color = state.color,
                                background = state.background,
                                selected = state in draftStates,
                                onClick = {
                                    if (state in draftStates) draftStates.remove(state) else draftStates.add(state)
                                },
                            )
                        }
                    }
                }

                SectionLabel(text = "时间排序")
                SectionCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EmaSort.entries.forEach { option ->
                            FilterPill(
                                text = option.label,
                                color = MaterialTheme.colorScheme.primary,
                                background = MaterialTheme.colorScheme.primaryContainer,
                                selected = option == draftSort,
                                onClick = { draftSort = option },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArisuGhostButton(
                        text = "重置",
                        onClick = {
                            draftStates.clear()
                            draftSort = EmaSort.NEWEST
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    ArisuPrimaryButton(
                        text = "应用",
                        onClick = {
                            states.clear()
                            states.addAll(draftStates)
                            onSortChange(draftSort)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 筛选面板里的可选中标签。 */
@Composable
private fun FilterPill(
    text: String,
    color: Color,
    background: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) background else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 奉仕会
 * ══════════════════════════════════════════════════════════════════ */

private val StaffRoles = listOf(Role.KANNUSHI, Role.GUUJI, Role.HOUSHI, Role.TEMP_HOUSHI)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffSection(onOpenUser: (String) -> Unit) {
    var formOpen by remember { mutableStateOf(false) }
    val cachedStaff = remember { staffMemory.get("staff", Shrine.me.value?.id) }
    // 落盘缓存兜底：冷启动直接进奉仕会也有名单，不再先转一圈
    val firstGroups = remember { cachedStaff?.groups ?: Shrine.peekStaff().orEmpty() }
    var groups by remember { mutableStateOf(firstGroups) }
    var loadState by remember {
        mutableStateOf(
            if (firstGroups.isNotEmpty()) ListState.Ok(firstGroups, firstGroups.sumOf { it.members.size }.toLong())
            else ListState.Loading
        )
    }
    var myApplication by remember { mutableStateOf(cachedStaff?.myApplication ?: dev.arisu.shrine.data.remote.MyApplicationRes()) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val me by dev.arisu.shrine.data.repo.Shrine.me.collectAsStateWithLifecycle()
    val staffListState = rememberLazyListState()

    LaunchedEffect(reloadKey) {
        try {
            groups = Shrine.staffGroups()
            loadState = ListState.Ok(groups, groups.sumOf { it.members.size }.toLong())
            // 我的申请状态
            myApplication = Shrine.myStaffApplication()
            Shrine.me.value?.id?.let { staffMemory.put("staff", it, StaffSnapshot(groups, myApplication)) }
        } catch (e: Exception) {
            // 已有内容时静默刷新失败保留旧列表，只有首载无数据才进入失败态
            if (loadState is ListState.Loading) loadState = ListState.Fail(e.toApiMessage())
        }
    }

    LaunchedEffect(Unit) {
        ScrollTopBus.events.collect { route ->
            if (route == Routes.SHRINE) scope.launch { staffListState.smoothScrollToTop() }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = staffListState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { StaffDutyCard() }

        if (me?.role?.canCurate == true) {
            item(key = "pending_apps") {
                PendingApplicationsCard(reloadKey = reloadKey)
            }
        }

        when (val st = loadState) {
            is ListState.Fail -> item { ShrineEmptyState(note = "加载失败，点击重试", onClick = { reloadKey++ }) }
            is ListState.Loading -> item { ShrineLoadingState() }
            is ListState.Ok -> {
                if (groups.none { it.members.isNotEmpty() }) {
                    item { ShrineEmptyState(note = "暂无神社维护人员信息") }
                }
                StaffRoles.forEach { role ->
                    val members = groups.firstOrNull { it.role == role }?.members.orEmpty()
                    if (members.isNotEmpty()) {
                        item(key = "section_${role.name}") {
                            SectionLabel(text = "${role.label} · ${members.size} 人")
                        }
                        item(key = "card_${role.name}") {
                            StaffCard(members, onOpenUser)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            // 申请状态：pending 等待审核；approved 已是奉仕会成员；
            // rejected 一周后可再次申请（与服务端冷却一致）
            val app = myApplication
            val cooldownLeft = if (app.applied && app.status == "rejected" && app.createdAt > 0) {
                val elapsed = (System.currentTimeMillis() / 1000) - app.createdAt
                (7 * 24 * 3600 - elapsed).coerceAtLeast(0)
            } else 0L
            val blocked = app.applied && (app.status == "pending" || app.status == "approved" || cooldownLeft > 0)
            ArisuPrimaryButton(
                text = when {
                    app.applied && app.status == "pending" -> "已申请，等待审核"
                    app.applied && app.status == "approved" -> "您已是奉仕会成员"
                    blocked -> "冷却中，${(cooldownLeft + 86399) / 86400} 天后可再次申请"
                    else -> "申请担任临时奉仕"
                },
                onClick = { formOpen = true },
                enabled = !blocked,
            )
            if (app.applied && app.status == "pending") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "宫司审核后会通过社务箱通知您",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
    }

    if (formOpen) {
        VolunteerApplySheet(
            onDismiss = { formOpen = false },
            onSubmit = {
                myApplication = dev.arisu.shrine.data.remote.MyApplicationRes(
                    applied = true, status = "pending",
                    createdAt = System.currentTimeMillis() / 1000,
                )
                formOpen = false
            },
        )
    }
}

/** 临时奉仕的职责说明：左侧一条朱线，条目依次列出。 */
@Composable
private fun StaffDutyCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 17.dp),
    ) {
        Text(
            text = "临时奉仕的职责",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(13.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Vermilion.copy(alpha = 0.85f))
            )
            Spacer(Modifier.width(13.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                DutyLine("每日巡阅各板块的帖子，维持殿内秩序。")
                DutyLine("甄别引战、营利广告、色情与政治等违禁之言。")
                DutyLine("记录帖子编号，附上 tips 标签，呈至社务箱。")
                DutyLine("静候奉仕、宫司与神主裁决，自身不作定夺。")
            }
        }
        Spacer(Modifier.height(13.dp))
        Text(
            text = "临时奉仕无删除之权，唯司上报。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DutyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 同一层身份的人员卡片，多人时纵向排列并用分割线隔开。 */
@Composable
private fun StaffCard(members: List<User>, onOpenUser: (String) -> Unit) {
    SectionCard {
        members.forEachIndexed { index, user ->
            if (index > 0) {
                HairlineDivider(startIndent = 56.dp)
            }
            StaffRow(user, onOpenUser)
        }
    }
}

@Composable
private fun StaffRow(user: User, onOpenUser: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenUser(user.id) }
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArisuAvatar(seed = 0, name = user.nickname, size = 38.dp, role = user.role, ring = true, url = user.fixedAvatarUrl)
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = user.nickname,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val intro = user.bio.ifBlank { user.signature }
            if (intro.isNotBlank()) {
                Text(
                    text = intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "任职时间 ${user.joinedAt}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        RoleTag(user.role)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolunteerApplySheet(
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    var effort by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = reason.isNotBlank() && effort != null && !submitting

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // 提交中禁止下滑/返回关闭：sheet 用自身 scope 发请求，提前离场会
        // 掐死在途请求，用户不知道到底交没交上
        confirmValueChange = { !submitting },
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            SheetHeader(
                title = "申请担任临时奉仕",
                subtitle = "请简述您的情况，宫司及以上将在一周内审核。",
                onClose = onDismiss,
            )

            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionCard {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 17.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = "申请理由",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        ArisuTextArea(
                            value = reason,
                            onValueChange = { if (it.length <= 500) reason = it },
                            placeholder = "例如：每天晚上在线 2 小时，可以帮忙看正殿与幻想乡闻的新帖。",
                        )

                        Spacer(Modifier.height(18.dp))

                        Text(
                            text = "每周可投入时间",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { menuOpen = true }
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = effort ?: "请选择",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (effort != null) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "▾",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                EffortOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            effort = option
                                            menuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(16.dp))
                ArisuPrimaryButton(
                    text = if (submitting) "提交中…" else "提交",
                    onClick = {
                        submitting = true
                        error = null
                        scope.launch {
                            try {
                                Shrine.applyStaff(reason.trim(), effort.orEmpty())
                                onSubmit()
                            } catch (e: Exception) {
                                error = e.toApiMessage()
                            }
                            submitting = false
                        }
                    },
                    enabled = canSubmit,
                )
            }
        }
    }
}

/** 多行文本框外壳，样式与 ArisuTextField 的多行形态一致。 */
@Composable
private fun ArisuTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
                inner()
            }
        },
    )
}

/* ══════════════════════════════════════════════════════════════════
 * 神社日志
 * ══════════════════════════════════════════════════════════════════ */

@Composable
private fun ChangelogSection(onOpenChangelog: (String) -> Unit) {
    var checkResult by remember { mutableStateOf<CheckState?>(null) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    val cachedLog = remember { logMemory.get("log", Shrine.me.value?.id) ?: Shrine.peekReleases() }
    var releases by remember { mutableStateOf(cachedLog ?: emptyList()) }
    var loadState by remember {
        mutableStateOf<ListState<ReleaseNote>>(
            if (cachedLog != null) ListState.Ok(cachedLog, cachedLog.size.toLong()) else ListState.Loading
        )
    }
    var checking by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val myVersion = remember { "v" + dev.arisu.shrine.core.util.AppInfo.version(context) }
    val logListState = rememberLazyListState()

    LaunchedEffect(reloadKey) {
        try {
            releases = Shrine.releases()
            loadState = ListState.Ok(releases, releases.size.toLong())
            Shrine.me.value?.id?.let { logMemory.put("log", it, releases) }
        } catch (e: Exception) {
            // 已有内容时静默刷新失败保留旧列表，只有首载无数据才进入失败态
            if (loadState is ListState.Loading) loadState = ListState.Fail(e.toApiMessage())
        }
    }

    LaunchedEffect(Unit) {
        ScrollTopBus.events.collect { route ->
            if (route == Routes.SHRINE) scope.launch { logListState.smoothScrollToTop() }
        }
    }

    fun checkUpdate() {
        if (checking) return
        checking = true
        scope.launch {
            try {
                val result = Shrine.checkUpdate(myVersion.removePrefix("v"))
                if (result.updateAvailable && result.release != null) {
                    checkResult = CheckState.UpdateAvailable(
                        dev.arisu.shrine.data.model.ReleaseNote(
                            version = result.release.version,
                            channel = if (result.release.channel == "beta") dev.arisu.shrine.data.model.ReleaseChannel.BETA
                            else dev.arisu.shrine.data.model.ReleaseChannel.STABLE,
                            publishedAt = "",
                            kinds = emptyList(),
                            body = result.release.body,
                        ),
                        downloadUrl = result.downloadUrl,
                    )
                } else {
                    toast = ToastMessage("已是最新版本", System.currentTimeMillis())
                }
            } catch (e: Exception) {
                toast = ToastMessage("检查更新失败，请稍后重试", System.currentTimeMillis())
            }
            checking = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val st = loadState) {
            is ListState.Loading -> ShrineLoadingState()
            is ListState.Fail -> ShrineEmptyState(note = "加载失败，点击重试", onClick = { reloadKey++ })
            is ListState.Ok -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = logListState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "current") {
                        CurrentVersionCard(
                            version = myVersion,
                            checking = checking,
                            onCheck = { checkUpdate() },
                        )
                    }

                    item(key = "listLabel") {
                        SectionLabel(text = "更新日志 · ${st.items.size} 条")
                    }

                    if (st.items.isEmpty()) {
                        item { ShrineEmptyState(note = "没有日志") }
                    } else {
                        // version+channel 组合做 key：同版本号双渠道常见，单 version 重复即崩
                        items(st.items, key = { it.version + "-" + it.channel }, contentType = { "release" }) { release ->
                            ReleaseNoteCard(
                                release = release,
                                onClick = { onOpenChangelog(release.version) },
                            )
                        }
                        item { ShrineListEnd(text = "已经到底了") }
                    }
                }
            }
        }

        toast?.let { message ->
            ToastHint(
                message = message,
                onExpire = { toast = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            )
        }
    }

    when (val result = checkResult) {
        is CheckState.UpdateAvailable -> AlertDialog(
            onDismissRequest = { checkResult = null },
            title = { Text("发现新版本") },
            text = {
                Column {
                    Text(
                        "有栖神社 ${result.release.version}（${result.release.channel.label}）",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    dev.arisu.shrine.core.markdown.MarkdownText(markdown = result.release.body)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        checkResult = null
                        result.downloadUrl?.let { url ->
                            // 只放行 http(s)：ACTION_VIEW 对任意 scheme 都可能被外层应用
                            // 当成输入（javascript:/intent: 等），下载地址必须锁死网页协议
                            val uri = android.net.Uri.parse(url)
                            val scheme = uri.scheme?.lowercase()
                            if (scheme == "http" || scheme == "https") {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                                    .onFailure { toast = ToastMessage("更新链接无效，请联系神主", System.currentTimeMillis()) }
                            } else {
                                toast = ToastMessage("更新链接无效，请联系神主", System.currentTimeMillis())
                            }
                        } ?: run { toast = ToastMessage("服务器尚未提供安装包，请联系神主", System.currentTimeMillis()) }
                    },
                ) { Text("立即更新") }
            },
            dismissButton = {
                TextButton(onClick = { checkResult = null }) { Text("稍后") }
            },
        )
        else -> {}
    }
}

/** 当前版本：凸起展示卡，右侧是朱色的检查更新胶囊。 */
@Composable
private fun CurrentVersionCard(version: String, checking: Boolean, onCheck: () -> Unit) {
    NeuSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        MistWatermark(RoundedCornerShape(20.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 19.dp),
        ) {
            Text(
                text = "当前版本",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = version,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .accentRaised(shape = RoundedCornerShape(20.dp), elevation = 8.dp)
                        .clickable(enabled = !checking) { onCheck() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (checking) {
                        YinYangOrb(size = 14.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = "检查更新",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** 轻提示：一枚悬浮的新拟物凸起小条，2.4 秒后自动消失。 */
@Composable
private fun ToastHint(
    message: ToastMessage,
    onExpire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(message.key) {
        delay(2400L)
        onExpire()
    }
    NeuSurface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        depth = 4.dp,
        blur = 10.dp,
        color = NeuBaseHigh,
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 11.dp)) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ReleaseNoteCard(release: ReleaseNote, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .softCard(shape = RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = release.version,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(10.dp))
            SoftPill(
                text = release.channel.label,
                color = MaterialTheme.colorScheme.secondary,
                background = MaterialTheme.colorScheme.secondaryContainer,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = release.publishedAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (release.kinds.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row {
                release.kinds.forEach { kind ->
                    SoftPill(text = kind.label, color = kind.color, background = kind.background)
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val maxBodyHeightPx = with(LocalDensity.current) { 220.dp.roundToPx() }
        var bodyHeightPx by remember(release.version) { mutableStateOf(0) }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clipToBounds()
        ) {
            MarkdownText(
                release.body,
                modifier = Modifier.onSizeChanged { bodyHeightPx = it.height },
            )
            // 只有正文真的超过 220dp 才盖渐隐；否则两条短说明的最后一行
            // 也会被压暗，看起来像禁用文字
            if (bodyHeightPx >= maxBodyHeightPx) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, NeuBase)
                            )
                        )
                )
            }
        }
    }
}



/** 宫司及以上的申请批阅卡：待审列表 + 通过/婉拒（附言选填），与 WebUI 的批阅流一致。 */
@Composable
private fun PendingApplicationsCard(reloadKey: Int) {
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<dev.arisu.shrine.data.remote.ApplicationDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var target by remember { mutableStateOf<dev.arisu.shrine.data.remote.ApplicationDto?>(null) }
    var approving by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastMessage?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            apps = Shrine.pendingApplications()
            loadFailed = false
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 刷新失败保留旧列表：不再把待审清单整块替换成空态（宫司会误以为没人申请）。
            // 首载失败单独标出来——空列表 + 失败时「暂时没有」是在撒谎
            loadFailed = apps.isEmpty()
        }
        loading = false
    }
    LaunchedEffect(reloadKey) { load() }

    NeuSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        MistWatermark(RoundedCornerShape(20.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = "申请批阅",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "临时奉仕的入会申请，一周内完成批阅",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            when {
                loading -> ShrineRefreshingHint()
                apps.isEmpty() && loadFailed -> Text(
                    text = "加载失败，点击重试",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            loading = true
                            scope.launch { load() }
                        },
                )
                apps.isEmpty() -> Text(
                    text = "暂时没有待批阅的申请",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> apps.forEach { app ->
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = app.user.nickname + " · " + app.user.no,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = app.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "每周可奉仕 " + app.weeklyHours,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 通过 = 朱色实底凸起胶囊（白字，一眼可辨）；
                            // 婉拒 = 凹槽灰字。之前走 SoftPill，其 background
                            // 参数被丢弃——白字压在白底上，谁都看不见
                            Box(
                                Modifier
                                    .accentRaised(shape = RoundedCornerShape(999.dp), elevation = 6.dp)
                                    .clickable(enabled = !busy) {
                                        target = app; approving = true; comment = ""
                                    }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "通过",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Box(
                                Modifier
                                    .neuInset(shape = RoundedCornerShape(999.dp), color = NeuInset)
                                    .clickable(enabled = !busy) {
                                        target = app; approving = false; comment = ""
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "婉拒",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = InkMuted,
                                )
                            }
                        }
                        dev.arisu.shrine.core.components.HairlineDivider(
                            modifier = Modifier.padding(top = 10.dp), startIndent = 0.dp,
                        )
                    }
                }
            }
        }
    }

    target?.let { app ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { target = null },
            title = { Text(if (approving) "通过申请" else "婉拒申请") },
            text = {
                Column {
                    Text(
                        text = if (approving) "批阅结果会通过社务箱通知对方。附言选填。"
                        else "婉拒时建议附上理由，会随结果通知对方。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    dev.arisu.shrine.core.components.ArisuTextField(
                        value = comment,
                        onValueChange = { if (it.length <= 100) comment = it },
                        placeholder = "附言（选填）",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                Shrine.decideApplication(app.id, approving, comment.trim())
                                toast = ToastMessage(
                                    if (approving) "已通过 " + app.user.nickname + " 的申请"
                                    else "已婉拒 " + app.user.nickname + " 的申请",
                                    System.currentTimeMillis(),
                                )
                                target = null
                                load()
                            } catch (e: Exception) {
                                toast = ToastMessage(e.toApiMessage(), System.currentTimeMillis())
                            }
                            busy = false
                        }
                    },
                ) { Text(if (approving) "确认通过" else "确认婉拒") }
            },
            dismissButton = {
                TextButton(onClick = { target = null }) { Text("取消") }
            },
        )
    }

    toast?.let { t ->
        dev.arisu.shrine.core.components.ShrineNoticeBar(
            text = t.text,
            onDismiss = { toast = null },
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}
