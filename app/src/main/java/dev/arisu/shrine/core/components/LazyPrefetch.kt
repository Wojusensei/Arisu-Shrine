package dev.arisu.shrine.core.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 距列表末尾还剩这么多条时就开始拉下一页。
 *
 * 卡片一屏约 2~3 张，8 条相当于两三屏的余量：滑到底之前下一页通常已经就位。
 * 此前四个列表都是「末尾哨兵项进入组合」才触发，等于每翻一页都要停在底部等一次往返
 * （用户反馈的「滑一段停一下、等加载」）。
 */
const val PREFETCH_DISTANCE = 8

/**
 * 最后可见条目是否已进入预取区间。itemCount 是内容条目数（不含页脚）。
 * 列表短于一屏时（可见条目已覆盖全部内容）直接为真，交给调用方的
 * 「已在加载 / 已到底」守卫去拦。
 */
internal fun shouldPrefetch(
    lastVisibleIndex: Int,
    itemCount: Int,
    distance: Int = PREFETCH_DISTANCE,
): Boolean = itemCount > 0 && lastVisibleIndex >= itemCount - 1 - distance

/**
 * 滑到列表尾部附近时提前加载下一页。与 LazyColumn 同级调用，不要放进 item——
 * 放进 item 就退化成「到末尾才触发」。
 *
 * 用 snapshotFlow 观察可见区间，避免每个条目进出屏幕都重组调用方；
 * onLoadMore 走 rememberUpdatedState，翻页完成（itemCount 变化）后会重新求值，
 * 用户持续下滑时可以连续预取。调用方的 loadMore 仍须自带
 * 「loadingMore / endReached」守卫。
 */
@Composable
fun PrefetchOnApproach(
    listState: LazyListState,
    itemCount: Int,
    onLoadMore: () -> Unit,
) {
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, itemCount) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (shouldPrefetch(lastVisible, itemCount)) loadMore()
            }
    }
}
