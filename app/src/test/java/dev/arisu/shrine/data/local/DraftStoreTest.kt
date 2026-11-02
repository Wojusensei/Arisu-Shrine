package dev.arisu.shrine.data.local

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class DraftStoreTest {

    private lateinit var dir: java.io.File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("arisu-drafts").toFile()
        DraftStore.initAt(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun draft(user: String, target: String, body: String, updatedAt: Long = 0) = Draft(
        userId = user,
        kind = DraftStore.COMMENT_POST,
        targetId = target,
        body = body,
        updatedAt = updatedAt,
    )

    @Test
    fun savesAndIsolatesByUser() = runBlocking {
        DraftStore.save(draft("U1", "p1", "甲"))
        DraftStore.save(draft("U1", "p2", "乙"))
        DraftStore.save(draft("U2", "p1", "丙"))

        assertEquals(listOf("p1", "p2"), DraftStore.list("U1").map { it.targetId }.sorted())
        assertEquals(listOf("p1"), DraftStore.list("U2").map { it.targetId })
        assertNull(DraftStore.get("U2", DraftStore.COMMENT_POST, "p2"))
    }

    @Test
    fun sameTargetKeepsOnlyLatestBody() = runBlocking {
        DraftStore.save(draft("U1", "p1", "旧"))
        DraftStore.save(draft("U1", "p1", "新"))

        assertEquals(1, DraftStore.list("U1").size)
        assertEquals("新", DraftStore.get("U1", DraftStore.COMMENT_POST, "p1")?.body)
    }

    @Test
    fun deleteRemovesOnlyThatTarget() = runBlocking {
        DraftStore.save(draft("U1", "p1", "甲"))
        DraftStore.save(draft("U1", "p2", "乙"))

        DraftStore.delete("U1", DraftStore.COMMENT_POST, "p1")

        assertEquals(listOf("p2"), DraftStore.list("U1").map { it.targetId })
    }

    @Test
    fun corruptFileFallsBackToEmpty() = runBlocking {
        java.io.File(dir, "drafts.json").writeText("{not json")
        DraftStore.initAt(dir)

        assertEquals(emptyList<Draft>(), DraftStore.list("U1"))
    }
}
