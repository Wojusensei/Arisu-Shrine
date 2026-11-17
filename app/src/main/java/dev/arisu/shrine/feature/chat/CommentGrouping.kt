package dev.arisu.shrine.feature.chat

import dev.arisu.shrine.data.model.Comment

/**
 * 评论区按主消息归组：服务端下发扁平时间序，回复不保证挨着父消息，
 * 会落到别人消息的后面。这里把每条回复挪到其顶级祖先之后（组内保持时间序，
 * 回复@回复 沿父链上溯）；父消息已被删的回复解析不到祖先，原位保留。
 * 总条数不变，只是次序重排。
 */
internal fun List<Comment>.groupedByThread(): List<Comment> {
    if (isEmpty()) return this
    val byId = associateBy { it.id }
    val rootOf = HashMap<String, String?>()
    fun rootOf(c: Comment): String? {
        var cur = c
        // 防御环：父链正常只向上，13 层封顶
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
            // 其余回复已归到祖先名下，由祖先带走
        }
    }
    return out
}

/**
 * 删除一条评论时，服务端会连同整棵子树一起软删；本地列表和落盘缓存必须按同一范围移除，
 * 否则子评论会留在界面上，重进帖子还会先用旧缓存把它摆出来。
 */
internal fun List<Comment>.subtreeIds(rootId: String): Set<String> {
    val byParent = groupBy { it.parentId }
    val out = LinkedHashSet<String>()
    val stack = ArrayDeque<String>()
    stack.addLast(rootId)
    while (stack.isNotEmpty()) {
        val id = stack.removeLast()
        if (!out.add(id)) continue
        byParent[id]?.forEach { stack.addLast(it.id) }
    }
    return out
}
