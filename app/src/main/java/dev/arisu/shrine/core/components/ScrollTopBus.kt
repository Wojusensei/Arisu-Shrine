package dev.arisu.shrine.core.components

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull

object ScrollTopBus {
    private val _events = MutableStateFlow<Pair<String, Int>?>(null)

    val events: Flow<String> = _events.mapNotNull { it?.first }

    val latest: String? get() = _events.value?.first

    fun request(route: String) {
        _events.value = route to ((_events.value?.second ?: 0) + 1)
    }
}
