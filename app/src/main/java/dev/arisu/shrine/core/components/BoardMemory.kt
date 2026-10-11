package dev.arisu.shrine.core.components

class BoardMemory<K, V> {
    private val entries = mutableMapOf<K, Pair<String, V>>()

    fun get(key: K, userId: String?): V? =
        userId?.let { entries[key]?.takeIf { it.first == userId }?.second }

    fun put(key: K, userId: String, value: V) {
        entries[key] = userId to value
    }
}
