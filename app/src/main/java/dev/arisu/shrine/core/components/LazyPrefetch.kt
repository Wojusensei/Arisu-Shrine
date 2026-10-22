package dev.arisu.shrine.core.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

const val PREFETCH_DISTANCE = 8

internal fun shouldPrefetch(
    lastVisibleIndex: Int,
    itemCount: Int,
    distance: Int = PREFETCH_DISTANCE,
): Boolean = itemCount > 0 && lastVisibleIndex >= itemCount - 1 - distance

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
