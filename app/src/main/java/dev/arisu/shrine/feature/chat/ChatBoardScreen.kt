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
import kotlinx.coroutines.launch
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
import dev.arisu.shrine.data.model.Board
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.PostSort
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.nav.Routes

sealed interface ListState<out T> {
    data object Loading : ListState<Nothing>
    data class Ok<T>(val items: List<T>, val total: Long) : ListState<T>
    data class Fail(val message: String) : ListState<Nothing>
}

private val feedMemory = BoardMemory<Pair<Board, PostSort>, ListState<Post>>()

private const val FEED_PAGE_SIZE = 20

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

    var board by rememberSaveable { mutableStateOf(initialBoard) }
    var sort by rememberSaveable { mutableStateOf(initialSort) }

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

    var refreshGen by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val loadFirst: suspend () -> Unit = {
        refreshGen++
        state = firstPaint(board, sort) ?: ListState.Loading
        val restored = state as? ListState.Ok
        page = if (restored == null || restored.items.isEmpty()) 1 else (restored.items.size + FEED_PAGE_SIZE - 1) / FEED_PAGE_SIZE
        endReached = restored != null && restored.items.size >= restored.total
        state = try {
            val result = Shrine.postsOf(board, sort, page = 1)
            page = 1
            endReached = result.items.size >= result.total
            loadMoreFailed = false
            val fresh = ListState.Ok(result.items, result.total)

            if ((state as? ListState.Ok) == fresh) state else fresh
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (state is ListState.Loading) ListState.Fail(e.toApiMessage()) else state
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

                val merged = ListState.Ok((current.items + result.items).distinctBy { it.id }, result.total)
                state = merged
                Shrine.me.value?.id?.let { feedMemory.put(snapBoard to snapSort, it, merged) }
                loadMoreFailed = false
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                loadMoreFailed = true
            } finally {

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

        LaunchedEffect(Unit) {
            if (Shrine.consumeJustPublished()) {
                reloadKey++
                chatListState.scrollToItem(0)
            }
        }

        LaunchedEffect(Unit) {
            ScrollTopBus.events.collect { route ->
                if (route == Routes.CHAT) scope.launch { chatListState.smoothScrollToTop() }
            }
        }

        var lastFilter by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(board, sort) {
            val key = "${board.name}|${sort.name}"
            if (lastFilter.isNotEmpty() && lastFilter != key) chatListState.scrollToItem(0)
            lastFilter = key
        }

        Crossfade(
            targetState = board to sort,
            animationSpec = tween(220),
            label = "chatContent",
        ) { target ->

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

                            PrefetchOnApproach(chatListState, s.items.size) { loadMore() }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = chatListState,
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(s.items, key = { it.id }, contentType = { "post" }) { post ->
                                    PostCard(post = post, onClick = { onOpenPost(post.id) })
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
