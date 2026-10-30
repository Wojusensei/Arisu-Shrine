package dev.arisu.shrine.feature.chat

import dev.arisu.shrine.data.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentGroupingTest {

    private fun c(id: String, parentId: String? = null) =
        Comment(id = id, postId = "p", authorId = "1", content = "内容$id", createdAt = "今天", parentId = parentId)

    @Test
    fun replyFallingAfterAnotherCommentMovesBackUnderItsParent() {

        val flat = listOf(c("A"), c("B"), c("R", parentId = "A"))
        assertEquals(listOf("A", "R", "B"), flat.groupedByThread().map { it.id })
    }

    @Test
    fun replyChainGroupsUnderTopAncestor() {

        val flat = listOf(c("A"), c("B"), c("R1", parentId = "A"), c("R2", parentId = "R1"))
        assertEquals(listOf("A", "R1", "R2", "B"), flat.groupedByThread().map { it.id })
    }

    @Test
    fun deepChainBeyondThirteenLevelsDegradesToOrphan() {

        var flat = listOf(c("A"))
        var parent = "A"
        repeat(15) { i ->
            val id = "L$i"
            flat = flat + c(id, parentId = parent)
            parent = id
        }
        val grouped = flat.groupedByThread()
        assertEquals(flat.size, grouped.size)

        assertEquals("A", grouped.first().id)
        assertEquals("L14", grouped.last().id)
    }

    @Test
    fun cycleInParentChainDoesNotHang() {

        val flat = listOf(c("A", parentId = "B"), c("B", parentId = "A"), c("C"))
        val grouped = flat.groupedByThread()
        assertEquals(3, grouped.size)
    }

    @Test
    fun orphanReplyKeepsItsPlace() {

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

        val flat = listOf(c("A"), c("B"), c("R1", parentId = "A"), c("R2", parentId = "R1"), c("R3", parentId = "B"))
        assertEquals(setOf("A", "R1", "R2"), flat.subtreeIds("A"))
    }

    @Test
    fun subtreeIdsTerminateOnParentCycle() {

        val flat = listOf(c("A", parentId = "B"), c("B", parentId = "A"), c("C"))
        assertEquals(setOf("A", "B"), flat.subtreeIds("A"))
    }
}
