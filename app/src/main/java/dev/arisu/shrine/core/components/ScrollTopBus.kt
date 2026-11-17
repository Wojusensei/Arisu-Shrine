package dev.arisu.shrine.core.components

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull

/**
 * 底栏「再点一次当前板块」的回顶信号：当前板块不重新导航（不重播转场），
 * 只广播一次，各主板块自行监听并滚回自己的列表顶端。
 * 用 StateFlow 保留最后一击——晚到的订阅者（刚切进来的板块）收到后
 * 滚回顶部也无害；stamp 让同一路由的连击也不会被去重。
 */
object ScrollTopBus {
    private val _events = MutableStateFlow<Pair<String, Int>?>(null)

    /** 最近一击的板块路由；null 表示还没人点过。 */
    val events: Flow<String> = _events.mapNotNull { it?.first }

    /** 最近一击的路由，供同步读取（测试/调试）。 */
    val latest: String? get() = _events.value?.first

    fun request(route: String) {
        _events.value = route to ((_events.value?.second ?: 0) + 1)
    }
}
