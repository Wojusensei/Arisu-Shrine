package dev.arisu.shrine.core.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PullAlphaTest {

    @Test
    fun pullsFadesInWithDistance() {
        assertEquals(0f, pullAlpha(0f, 400f, isRefreshing = false), 0.001f)
        assertEquals(0.25f, pullAlpha(100f, 400f, isRefreshing = false), 0.001f)
        assertEquals(1f, pullAlpha(400f, 400f, isRefreshing = false), 0.001f)
    }

    @Test
    fun overPullClamps() {
        assertEquals(1f, pullAlpha(800f, 400f, isRefreshing = false), 0.001f)
    }

    @Test
    fun refreshingIsAlwaysVisible() {
        assertEquals(1f, pullAlpha(0f, 400f, isRefreshing = true), 0.001f)
        assertEquals(1f, pullAlpha(400f, 400f, isRefreshing = true), 0.001f)
    }
}
