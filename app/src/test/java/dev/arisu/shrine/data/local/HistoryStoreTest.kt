package dev.arisu.shrine.data.local

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class HistoryStoreTest {

    private lateinit var dir: java.io.File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("arisu-history").toFile()
        HistoryStore.initAt(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun visitKeepsSavedPosition() = runBlocking {
        HistoryStore.visit("U1", HistoryStore.POST, "p1", "标题", "正殿")
        HistoryStore.savePosition("U1", HistoryStore.POST, "p1", "c42", 42, 120)
        HistoryStore.visit("U1", HistoryStore.POST, "p1", "新标题", "正殿")

        val entry = HistoryStore.get("U1", HistoryStore.POST, "p1")
        assertNotNull(entry)
        assertEquals("新标题", entry?.title)
        assertEquals("c42", entry?.lastCommentId)
        assertEquals(42, entry?.lastIndex)
        assertEquals(120, entry?.lastOffset)
    }

    @Test
    fun sameTargetDeduplicates() = runBlocking {
        HistoryStore.visit("U1", HistoryStore.POST, "p1", "甲", "正殿")
        HistoryStore.visit("U1", HistoryStore.POST, "p1", "乙", "正殿")

        assertEquals(1, HistoryStore.list("U1").size)
        assertEquals("乙", HistoryStore.list("U1").first().title)
    }

    @Test
    fun clearOnlyClearsCurrentUser() = runBlocking {
        HistoryStore.visit("U1", HistoryStore.POST, "p1", "甲", "正殿")
        HistoryStore.visit("U2", HistoryStore.POST, "p1", "乙", "正殿")

        HistoryStore.clear("U1")

        assertEquals(0, HistoryStore.list("U1").size)
        assertEquals(1, HistoryStore.list("U2").size)
    }
}
