package dev.arisu.shrine.core.components

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollTopBusTest {

    @Test
    fun requestRecordsLatestRoute() {
        ScrollTopBus.request("me")
        assertEquals("me", ScrollTopBus.latest)
        ScrollTopBus.request("chat")
        assertEquals("chat", ScrollTopBus.latest)
    }

    @Test
    fun collectorReceivesLastEventThenFreshOnes() = runBlocking {
        // 无订阅者时的一击保留为最新状态，订阅后立即收到
        ScrollTopBus.request("chat")
        val seen = mutableListOf<String>()
        val job = launch { ScrollTopBus.events.take(2).collect { seen.add(it) } }
        repeat(3) { yield() } // 让订阅者先收到当前值并挂起等待
        ScrollTopBus.request("me")
        job.join()
        assertEquals(listOf("chat", "me"), seen)
    }
}
