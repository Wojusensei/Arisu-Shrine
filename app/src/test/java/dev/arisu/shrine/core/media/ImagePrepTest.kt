package dev.arisu.shrine.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePrepTest {

    @Test
    fun `动图无论多大都不重编码`() {
        assertFalse(needsRecompress("image/gif", 4000, 12L * 1024 * 1024))
        assertFalse(needsRecompress("image/gif", 800, 60 * 1024))
    }

    @Test
    fun `手机原图要压：长边超 2048 或体积超阈值`() {
        assertTrue(needsRecompress("image/jpeg", 4000, 600 * 1024))
        assertTrue(needsRecompress("image/jpeg", 1600, 3L * 1024 * 1024))
    }

    @Test
    fun `已经够小的图原样上传，不做无谓重编码`() {
        assertFalse(needsRecompress("image/jpeg", 1600, 600 * 1024))
        assertFalse(needsRecompress("image/png", 1080, 200 * 1024))
        assertFalse(needsRecompress("image/webp", 2048, 700 * 1024))
    }

    @Test
    fun `读不出尺寸的图按需要重编码处理`() {
        assertTrue(needsRecompress("image/jpeg", 0, 100 * 1024))
    }

    @Test
    fun `压出来更大或为空就不值得用`() {
        assertTrue(recompressWorthIt(300 * 1024, 900 * 1024))
        assertFalse(recompressWorthIt(0, 900 * 1024))
        assertFalse(recompressWorthIt(900 * 1024, 900 * 1024))
        assertFalse(recompressWorthIt(1200 * 1024, 900 * 1024))
    }

    @Test
    fun `取样倍率：解码后长边落在目标与两倍之间`() {

        listOf(1080 to 2400, 2048 to 1536, 4096 to 3072, 8000 to 6000, 12000 to 9000).forEach { (w, h) ->
            val long = maxOf(w, h) / imageSampleSize(w, h, 2048)
            assertTrue("${w}×$h 取样后长边 $long 应 ≥ 2048", long >= 2048)
            assertTrue("${w}×$h 取样后长边 $long 应 < 4096", long < 4096)
        }
    }
}
