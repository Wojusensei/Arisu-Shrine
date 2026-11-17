package dev.arisu.shrine.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上传前的整备规则。
 *
 * 用户报过「发图基本传不上去」：原图原样直传，几 MB 的图在弱网下必然失败，
 * 服务端与 Caddy 还有 10/12MB 两道闸。这里把「压不压、值不值得用」钉住；
 * 动图必须永远原样——重编码一次就等于把 gif 压成静帧。
 */
class ImagePrepTest {

    @Test
    fun `动图无论多大都不重编码`() {
        assertFalse(needsRecompress("image/gif", 4000, 12L * 1024 * 1024))
        assertFalse(needsRecompress("image/gif", 800, 60 * 1024))
    }

    @Test
    fun `手机原图要压：长边超 2048 或体积超阈值`() {
        assertTrue(needsRecompress("image/jpeg", 4000, 600 * 1024))   // 4000×3000 的原图
        assertTrue(needsRecompress("image/jpeg", 1600, 3L * 1024 * 1024)) // 长边合规但太大
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
    fun `按文件头认图，不信任相册的 MIME`() {
        assertEquals("jpeg", sniffImageKind(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())))
        assertEquals(
            "png",
            sniffImageKind(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)),
        )
        assertEquals("gif", sniffImageKind("GIF89a".toByteArray()))
        assertEquals("webp", sniffImageKind("RIFF____WEBPVP8 ".toByteArray()))
    }

    @Test
    fun `HEIC 与 AVIF 单独识别，不能被当成 jpg 原样发出去`() {
        assertEquals("heif", sniffImageKind("____ftypheic".toByteArray()))
        assertEquals("heif", sniffImageKind("____ftypavif".toByteArray()))
        assertNull(sniffImageKind("not-an-image".toByteArray()))
    }

    @Test
    fun `取样倍率：解码后长边落在目标与两倍之间`() {
        // 2 的幂只能粗调，解出来仍可能大于目标：真正的「缩到 2048」还要 scaleDownTo 补一刀
        listOf(1080 to 2400, 2048 to 1536, 4096 to 3072, 8000 to 6000, 12000 to 9000).forEach { (w, h) ->
            val long = maxOf(w, h) / imageSampleSize(w, h, 2048)
            assertTrue("${w}×$h 取样后长边 $long 应 ≥ 2048", long >= 2048)
            assertTrue("${w}×$h 取样后长边 $long 应 < 4096", long < 4096)
        }
    }
}
