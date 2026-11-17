package dev.arisu.shrine.core.components

/**
 * 主板块内容常驻内存：切走再切回直接显示上次内容、后台静默刷新，
 * 不重播「少女祈祷中」（参考 QQ 的板块切换）。
 * 键值都附带用户 id，换账号登录后旧内容自动失效，不会串号。
 */
class BoardMemory<K, V> {
    private val entries = mutableMapOf<K, Pair<String, V>>()

    fun get(key: K, userId: String?): V? =
        userId?.let { entries[key]?.takeIf { it.first == userId }?.second }

    fun put(key: K, userId: String, value: V) {
        entries[key] = userId to value
    }
}
