package dev.arisu.shrine.feature.chat

import dev.arisu.shrine.data.model.Comment

internal fun List<Comment>.groupedByThread(): List<Comment> {
    if (isEmpty()) return this
    val byId = associateBy { it.id }
    val rootOf = HashMap<String, String?>()
    fun rootOf(c: Comment): String? {
        var cur = c

        repeat(13) {
            val pid = cur.parentId ?: return cur.id
            rootOf[cur.id]?.let { return it }
            cur = byId[pid] ?: return null
        }
        return null
    }
    val repliesByRoot = HashMap<String, MutableList<Comment>>()
    val orphanIds = HashSet<String>()
    for (c in this) {
        if (c.parentId == null) continue
        val root = rootOf(c)
        if (root == null) orphanIds += c.id
        else repliesByRoot.getOrPut(root) { mutableListOf() }.add(c)
    }
    val out = ArrayList<Comment>(size)
    for (c in this) {
        when {
            c.parentId == null -> { out += c; repliesByRoot[c.id]?.let { out += it } }
            c.id in orphanIds -> out += c

        }
    }
    return out
}
