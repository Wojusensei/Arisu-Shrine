package dev.arisu.shrine.core.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardMemoryTest {

    @Test
    fun putThenGetReturnsSameValue() {
        val memory = BoardMemory<String, Int>()
        memory.put("chat", "User_000001", 7)
        assertEquals(7, memory.get("chat", "User_000001"))
    }

    @Test
    fun otherUserSeesNothing() {
        val memory = BoardMemory<String, Int>()
        memory.put("chat", "User_000001", 7)
        assertNull(memory.get("chat", "User_000002"))
    }

    @Test
    fun nullUserSeesNothing() {
        val memory = BoardMemory<String, Int>()
        memory.put("chat", "User_000001", 7)
        assertNull(memory.get("chat", null))
    }

    @Test
    fun keysAreIsolated() {
        val memory = BoardMemory<Pair<String, String>, Int>()
        memory.put("MAIN" to "LATEST", "u", 1)
        memory.put("MAIN" to "HOT", "u", 2)
        assertEquals(1, memory.get("MAIN" to "LATEST", "u"))
        assertEquals(2, memory.get("MAIN" to "HOT", "u"))
    }

    @Test
    fun putOverwritesSameKey() {
        val memory = BoardMemory<String, Int>()
        memory.put("chat", "u", 1)
        memory.put("chat", "u", 2)
        assertEquals(2, memory.get("chat", "u"))
    }
}
