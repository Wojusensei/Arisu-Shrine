package dev.arisu.shrine.core.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyPrefetchTest {

    @Test
    fun `顶部不触发`() {
        assertFalse(shouldPrefetch(lastVisibleIndex = 0, itemCount = 20))
        assertFalse(shouldPrefetch(lastVisibleIndex = 5, itemCount = 20))
        assertFalse(shouldPrefetch(lastVisibleIndex = 10, itemCount = 20))
    }

    @Test
    fun `进入尾部区间触发`() {

        assertTrue(shouldPrefetch(lastVisibleIndex = 11, itemCount = 20))
        assertTrue(shouldPrefetch(lastVisibleIndex = 15, itemCount = 20))
        assertTrue(shouldPrefetch(lastVisibleIndex = 19, itemCount = 20))
    }

    @Test
    fun `追加一页后重新远离尾部则不重复触发`() {
        assertFalse(shouldPrefetch(lastVisibleIndex = 11, itemCount = 40))
        assertTrue(shouldPrefetch(lastVisibleIndex = 31, itemCount = 40))
    }

    @Test
    fun `内容不足一屏立即预取，空列表不触发`() {
        assertTrue(shouldPrefetch(lastVisibleIndex = 0, itemCount = 1))
        assertTrue(shouldPrefetch(lastVisibleIndex = 2, itemCount = 3))
        assertFalse(shouldPrefetch(lastVisibleIndex = -1, itemCount = 0))
    }

    @Test
    fun `还没完成首次布局时不触发`() {
        assertFalse(shouldPrefetch(lastVisibleIndex = -1, itemCount = 20))
    }
}
