package dev.arisu.shrine.feature.chat

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.BoardMemory
import dev.arisu.shrine.core.components.FadeInOut
import dev.arisu.shrine.core.components.PostCard
import dev.arisu.shrine.core.components.PrefetchOnApproach
import dev.arisu.shrine.core.components.ScrollTopBus
import dev.arisu.shrine.core.components.ScrollTopOrb
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineLoadGate
import dev.arisu.shrine.core.components.ShrinePullRefreshBox
import dev.arisu.shrine.core.components.ShrineStaleBanner
import dev.arisu.shrine.core.components.ShrineListEnd
import dev.arisu.shrine.core.components.ShrineLoadingState
import dev.arisu.shrine.core.components.ShrineRefreshingHint
import dev.arisu.shrine.core.components.SortBar
import dev.arisu.shrine.core.components.TopBarIcon
import dev.arisu.shrine.core.components.UnderlineTabs
import dev.arisu.shrine.core.components.smoothScrollToTop
import dev.arisu.shrine.core.markdown.markdownToPlain
import dev.arisu.shrine.data.model.Board
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.PostSort
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.nav.Routes

/** 列表三态：加载中 / 成功 / 失败。 */
sealed interface ListState<out T> {
    data object Loading : ListState<Nothing>
    data class Ok<T>(val items: List<T>, val total: Long) : ListState<T>
    data class Fail(val message: String) : ListState<Nothing>
}

// 茶话内容常驻：切走再切回不再重播加载动画（QQ 式板块切换），按板块+排序各存一份
private val feedMemory = BoardMemory<Pair<Board, PostSort>, ListState<Post>>()

/** 与 Shrine.postsOf 的默认 pageSize 对齐；翻页进度反推与触底判定都依赖它 */
private const val FEED_PAGE_SIZE = 20

/** 首屏只等这几张；余下的预热放到后台，不拖慢翻页和首帧。 */
private const val VISIBLE_PREWARM = 4

/**
 * 摘要解析是正则流水线，留在主线程会卡首帧。
 * 单条解析失败只丢摘要，不能把整页加载拖进错误态。
 */
private suspend fun prewarmExcerpts(posts: List<Post>) = withContext(Dispatchers.Default) {
    posts.forEach { if (it.intro.isBlank()) runCatching { markdownToPlain(it.body) } }
}

/**
 * 有栖茶话。
 *
 * 标题栏：有栖茶话 + 搜索 / 发帖 / 社务箱
 * 子板块：正殿 / 幻想乡闻 / /dev.
 * 排序：  最新 / 最热 / 精华
 */
@Composable
fun ChatBoardScreen(
    unreadCount: Int,
    onOpenPost: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenInbox: () -> Unit,
    onNewPost: (Board) -> Unit,
    modifier: Modifier = Modifier,
    initialBoard: Board = Board.MAIN,
    initialSort: PostSort = PostSort.LATEST,
) {
    // 板块与排序可跨页面驻留：进帖子详情再返回，不重置回「正殿 · 最新」
    var board by rememberSaveable { mutableStateOf(initialBoard) }
    var sort by rememberSaveable { mutableStateOf(initialSort) }
    // 板块首屏内容：内存记忆（同进程切回来）→ 落盘缓存（冷启动）→ 都没有才转圈
    fun firstPaint(b: Board, s: PostSort): ListState<Post>? =
        feedMemory.get(b to s, Shrine.me.value?.id)
            ?: Shrine.peekPosts(b, s)?.let { ListState.Ok(it.items, it.total) }
    var state by remember {
        mutableStateOf(firstPaint(board, sort) ?: ListState.Loading)
    }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreFailed by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    // 刷新代数：loadFirst 每次执行递增。page 快照比对在「刷新把 page 重置为 1」时失效
    //（snapPage 恰为 1 则恒真），在途的旧 loadMore 响应会拼回刷新前的数据，以代数兜底
    var refreshGen by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // 首载与下拉刷新共用：换板块 / 换排序 / 主动刷新时拉第一页。
    // 换板块先把记忆里的旧内容摆上来（静默刷新），不再盯着上一个板块的列表；
    // 恢复的列表带着翻页进度（页码由条数反推），翻页才不会从第一页重取而撞重复 key。
    val loadFirst: suspend () -> Unit = {
        refreshGen++
        val restoredPaint = firstPaint(board, sort)
        val cachedItems = (restoredPaint as? ListState.Ok<Post>)?.items.orEmpty()
        // 冷启动/切板块先摆缓存：只等可见的几张，其余后台续上
        prewarmExcerpts(cachedItems.take(VISIBLE_PREWARM))
        state = restoredPaint ?: ListState.Loading
        if (cachedItems.size > VISIBLE_PREWARM) {
            scope.launch(Dispatchers.Default) { prewarmExcerpts(cachedItems.drop(VISIBLE_PREWARM)) }
        }
        val restored = state as? ListState.Ok
        page = if (restored == null || restored.items.isEmpty()) 1 else (restored.items.size + FEED_PAGE_SIZE - 1) / FEED_PAGE_SIZE
        endReached = restored != null && restored.items.size >= restored.total
        var restPrewarm: List<Post> = emptyList()
        state = try {
            val result = Shrine.postsOf(board, sort, page = 1)
            page = 1
            endReached = result.items.size >= result.total
            loadMoreFailed = false
            prewarmExcerpts(result.items.take(VISIBLE_PREWARM))
            restPrewarm = result.items.drop(VISIBLE_PREWARM)
            val fresh = ListState.Ok(result.items, result.total)
            // 内容没变就不换对象：每次切回本板块都会重拉一页，等值时保持旧实例，
            // 整列组合原地安静——转场动画不再被一次全列表重组打断（data class 结构相等）
            if ((state as? ListState.Ok) == fresh) state else fresh
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (state is ListState.Loading) ListState.Fail(e.toApiMessage()) else state
        }
        if (restPrewarm.isNotEmpty()) {
            scope.launch(Dispatchers.Default) { prewarmExcerpts(restPrewarm) }
        }
        (state as? ListState.Ok)?.let { ok ->
            Shrine.me.value?.id?.let { feedMemory.put(board to sort, it, ok) }
        }
    }
    LaunchedEffect(board, sort, reloadKey) { loadFirst() }

    fun loadMore() {
        val current = state as? ListState.Ok ?: return
        if (loadingMore || endReached) return
        loadingMore = true
        // 发起时快照：返回时若已换板块/排序、刷新过或页码被重置，旧响应作废不落地
        //（此前晚到的旧响应会把别的板块第二页拼进当前列表、或整体覆盖刚刷新的数据）
        val snapGen = refreshGen
        val snapBoard = board
        val snapSort = sort
        val snapPage = page
        scope.launch {
            try {
                val result = Shrine.postsOf(snapBoard, snapSort, page = snapPage + 1)
                if (refreshGen != snapGen || board != snapBoard || sort != snapSort || page != snapPage) return@launch
                page += 1
                endReached = (page - 1) * FEED_PAGE_SIZE + result.items.size >= result.total
                prewarmExcerpts(result.items.take(VISIBLE_PREWARM))
                // 去重结果同时喂给 state 和记忆缓存——
                // 此前记忆存的是去重前的列表，恢复时重复 key 直接崩掉茶话页
                val merged = ListState.Ok((current.items + result.items).distinctBy { it.id }, result.total)
                state = merged
                if (result.items.size > VISIBLE_PREWARM) {
                    scope.launch(Dispatchers.Default) { prewarmExcerpts(result.items.drop(VISIBLE_PREWARM)) }
                }
                Shrine.me.value?.id?.let { feedMemory.put(snapBoard to snapSort, it, merged) }
                loadMoreFailed = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // 失败不再无限静默：底部提供「点击重试」
                loadMoreFailed = true
            } finally {
                // 旧响应作废的 return@launch 也要复位，否则翻页永久卡死
                loadingMore = false
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "有栖茶话",
            large = true,
            subtitle = "有栖神社 · 社员闲聊之处",
            actions = {
                TopBarIcon(Icons.Rounded.Search, "搜索", onOpenSearch)
                TopBarIcon(Icons.Rounded.Edit, "发帖", { onNewPost(board) })
                TopBarIcon(Icons.Rounded.Mail, "社务箱", onOpenInbox, badgeCount = unreadCount)
            },
        )

        UnderlineTabs(
            items = Board.entries,
            selected = board,
            onSelect = { board = it },
            label = { it.label },
        )

        SortBar(
            items = PostSort.entries,
            selected = sort,
            onSelect = { sort = it },
            label = { it.label },
            trailing = {
                val count = (state as? ListState.Ok<Post>)?.total ?: 0
                Text(
                    text = "共 $count 篇",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        ShrineStaleBanner()

        val chatListState = rememberLazyListState()
        // 刚从撰写页发完帖回来：回到顶部并重拉第一页，让自己刚发的帖立刻在眼前
        LaunchedEffect(Unit) {
            if (Shrine.consumeJustPublished()) {
                reloadKey++
                chatListState.scrollToItem(0)
            }
        }
        // 底栏再点一次当前板块：不重新导航，直接滚回顶部
        LaunchedEffect(Unit) {
            ScrollTopBus.events.collect { route ->
                if (route == Routes.CHAT) scope.launch { chatListState.smoothScrollToTop() }
            }
        }
        // 换板块 / 换排序：列表滚回顶部。只认真切换——进帖子详情再返回时这个
        // effect 会重跑一次，那会儿再滚顶就把读者刚翻到的位置吃掉了（用户反馈）
        var lastFilter by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(board, sort) {
            val key = "${board.name}|${sort.name}"
            if (lastFilter.isNotEmpty() && lastFilter != key) chatListState.scrollToItem(0)
            lastFilter = key
        }
        // 换板块 / 换排序时做一次淡入淡出，避免列表「啪」地跳一下
        Crossfade(
            targetState = board to sort,
            animationSpec = tween(220),
            label = "chatContent",
        ) { target ->
            // 内容读的是 state（数据层状态），不直接消费 targetState；
            // 用 key 按 target 强制按板块+排序重建，交叉淡入淡出才以它为键
            key(target) {
            ShrinePullRefreshBox(
                onRefresh = { loadFirst() },
                modifier = Modifier.fillMaxSize(),
            ) {
            Box(Modifier.fillMaxSize()) {
                ShrineLoadGate(loading = state is ListState.Loading, modifier = Modifier.fillMaxSize()) {
                when (val s = state) {
                    is ListState.Loading -> {}

                    is ListState.Fail -> ShrineEmptyState(
                        note = "加载失败，点击重试",
                        onClick = { reloadKey++ },
                    )

                    is ListState.Ok -> {
                        if (s.items.isEmpty()) {
                            ShrineEmptyState(
                                note = "没有帖子",
                                onClick = { onNewPost(board) },
                            )
                        } else {
                            // 距末尾还剩几屏就开始拉下一页：滑到底时内容通常已就位
                            PrefetchOnApproach(chatListState, s.items.size) { loadMore() }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = chatListState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(s.items, key = { it.id }, contentType = { "post" }) { post ->
                                    // 每行只建一次点击闭包：未读心跳带着整页重组时，卡不必换实例
                                    val open = remember(post.id, onOpenPost) { { onOpenPost(post.id) } }
                                    PostCard(post = post, onClick = open)
                                }
                                item(key = "footer") {
                                    when {
                                        loadingMore -> ShrineRefreshingHint()
                                        endReached -> ShrineListEnd(text = "已经到底了")
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
                }
                // 回顶阴阳玉压在列表上层：Box 内后写的后画。此前写在列表之前，
                // 帖子把它盖在下面，滚动后点不到
                ScrollTopOrb(
                    listState = chatListState,
                    onClick = { scope.launch { chatListState.smoothScrollToTop() } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 118.dp),
                )
            }
            }
        }
    }
}
