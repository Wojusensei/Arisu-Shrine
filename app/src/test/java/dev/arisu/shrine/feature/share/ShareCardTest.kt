package dev.arisu.shrine.feature.share

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareCardTest {

    @Test
    fun imagesAreTakenInOrder() {
        val md = "开头 ![一](https://a/1.png) 中间 ![二](https://a/2.jpg) 结尾"
        assertEquals(listOf("https://a/1.png", "https://a/2.jpg"), ShareCard.images(md))
    }

    @Test
    fun imagesInsideCodeAreIgnored() {
        val md = "```\n![示例](https://code/1.png)\n```\n`![行内](https://code/2.png)`\n![真图](https://a/real.png)"
        assertEquals(listOf("https://a/real.png"), ShareCard.images(md))
    }

    @Test
    fun htmlImagesAreTaken() {
        val md = "<img src=\"https://a/html.png\" alt=\"图\">"
        assertEquals(listOf("https://a/html.png"), ShareCard.images(md))
    }

    @Test
    fun duplicateImagesAreCollapsed() {
        val md = "![a](https://a/1.png) ![b](https://a/1.png)"
        assertEquals(listOf("https://a/1.png"), ShareCard.images(md))
    }

    @Test
    fun plainTextDropsImagePlaceholders() {
        val md = "正文 ![图片](https://a/1.png) [链接](https://b)"
        assertEquals("正文  链接", ShareCard.plainText(md))
    }
}
