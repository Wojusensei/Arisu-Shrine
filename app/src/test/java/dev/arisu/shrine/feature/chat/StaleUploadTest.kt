package dev.arisu.shrine.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class StaleUploadTest {

    @Test
    fun removesPlaceholderOnItsOwnLine() {
        assertEquals("前后", stripStaleUploads("前\n![图片](上传中-1)\n后"))
    }

    @Test
    fun removesPlaceholderAtStart() {
        assertEquals("后", stripStaleUploads("![图片](上传中-2)\n后"))
    }

    @Test
    fun removesPlaceholderAtEnd() {
        assertEquals("前", stripStaleUploads("前\n![图片](上传中-3)\n"))
    }

    @Test
    fun removesSeveralPlaceholders() {
        assertEquals(
            "开头结尾",
            stripStaleUploads("开头\n![图片](上传中-4)\n\n![图片](上传中-5)\n结尾"),
        )
    }

    @Test
    fun keepsNormalBodyUntouched() {
        assertEquals("正文\n\n第二段", stripStaleUploads("正文\n\n第二段"))
    }
}
