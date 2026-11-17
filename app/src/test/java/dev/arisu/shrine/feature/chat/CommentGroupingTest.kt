package dev.arisu.shrine.feature.chat

import dev.arisu.shrine.data.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 评论区归组规则（T54）：回复必须紧跟其顶级主消息，主消息之间才画分割线；
 * 父消息已删的回复原位保留；总条数不变。
 */
class CommentGroupingTest {

    private fun c(id: String, parentId: String? = null) =
        Comment(id = id, postId = "p", authorId = "1", content = "内容$id", createdAt = "今天", parentId = parentId)

    @Test
    fun replyFallingAfterAnotherCommentMovesBackUnderItsParent() {
        // 时间序 A、B、R(回复A)：扁平下发时 R 落在 B 后面，归组后应紧跟 A
        val flat = listOf(c("A"), c("B"), c("R", parentId = "A"))
        assertEquals(listOf("A", "R", "B"), flat.groupedByThread().map { it.id })
    }

    @Test
    fun replyChainGroupsUnderTopAncestor() {
        // 回复@回复：R2 的父是 R1，应与 R1 一起归到 A 名下
        val flat = listOf(c("A"), c("B"), c("R1", parentId = "A"), c("R2", parentId = "R1"))
        assertEquals(listOf("A", "R1", "R2", "B"), flat.groupedByThread().map { it.id })
    }

    @Test
    fun deepChainBeyondThirteenLevelsDegradesToOrphan() {
        // 防环上限是 13 层：14 层以上的深链解析不到顶级祖先，按孤儿原位保留——
        // 宁可少归组也不能死循环
        var flat = listOf(c("A"))
        var parent = "A"
        repeat(15) { i ->
            val id = "L$i"
            flat = flat + c(id, parentId = parent)
            parent = id
        }
        val grouped = flat.groupedByThread()
        assertEquals(flat.size, grouped.size)
        // 前 13 层归到 A 名下，超限的变成孤儿留在时间原位（尾部）
        assertEquals("A", grouped.first().id)
        assertEquals("L14", grouped.last().id)
    }

    @Test
    fun cycleInParentChainDoesNotHang() {
        // 数据异常出现环（A↔B 互为父）：必须终止，条数不变
        val flat = listOf(c("A", parentId = "B"), c("B", parentId = "A"), c("C"))
        val grouped = flat.groupedByThread()
        assertEquals(3, grouped.size)
    }

    @Test
    fun orphanReplyKeepsItsPlace() {
        // 父消息已被删（不在列表里）：回复原位保留，不挪不丢
        val flat = listOf(c("A"), c("B"), c("R", parentId = "GONE"))
        assertEquals(listOf("A", "B", "R"), flat.groupedByThread().map { it.id })
    }

    @Test
    fun wellFormedThreadIsUntouchedAndCountNeverChanges() {
        val flat = listOf(c("A"), c("R1", parentId = "A"), c("R2", parentId = "A"))
        assertEquals(flat, flat.groupedByThread())
        assertEquals(flat.size, flat.groupedByThread().size)
    }

    @Test
    fun emptyListPassesThrough() {
        assertEquals(emptyList<Comment>(), emptyList<Comment>().groupedByThread())
    }

    @Test
    fun subtreeIdsCoverNestedRepliesOnly() {
        // 删 A 时服务端会连 R1、R2 一起软删；B 及其回复必须留下
        val flat = listOf(c("A"), c("B"), c("R1", parentId = "A"), c("R2", parentId = "R1"), c("R3", parentId = "B"))
        assertEquals(setOf("A", "R1", "R2"), flat.subtreeIds("A"))
    }

    @Test
    fun subtreeIdsTerminateOnParentCycle() {
        // 异常数据（A↔B 互为父）不能把删除流程挂死
        val flat = listOf(c("A", parentId = "B"), c("B", parentId = "A"), c("C"))
        assertEquals(setOf("A", "B"), flat.subtreeIds("A"))
    }
}
