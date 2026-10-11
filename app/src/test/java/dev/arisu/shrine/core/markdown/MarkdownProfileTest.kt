package dev.arisu.shrine.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownProfileTest {

    @Test
    fun `badge link parses with target url`() {
        val blocks = parseMarkdown("[![徽章](https://img/x.svg)](https://example.com)", profileMode = true)
        assertEquals(1, blocks.size)
        val img = blocks[0] as MdBlock.Image
        assertEquals("徽章", img.alt)
        assertEquals("https://img/x.svg", img.url)
        assertEquals("https://example.com", img.link)
    }

    @Test
    fun `badge link works in default mode too`() {
        val blocks = parseMarkdown("[![b](https://img/x.svg)](https://example.com)")
        assertEquals("https://example.com", (blocks[0] as MdBlock.Image).link)
    }

    @Test
    fun `plain image has no link`() {
        val blocks = parseMarkdown("![截图](https://img/a.png)", profileMode = true)
        val img = blocks[0] as MdBlock.Image
        assertEquals("https://img/a.png", img.url)
        assertNull(img.link)
    }

    @Test
    fun `adjacent image lines form one row in profile mode`() {
        val md = "![a](https://img/a.svg)\n![b](https://img/b.svg)\n![c](https://img/c.svg)"
        val blocks = parseMarkdown(md, profileMode = true)
        assertEquals(1, blocks.size)
        assertEquals(3, (blocks[0] as MdBlock.ImageRow).items.size)
    }

    @Test
    fun `adjacent image lines stay separate in default mode`() {
        val md = "![a](https://img/a.svg)\n![b](https://img/b.svg)"
        val blocks = parseMarkdown(md)
        assertEquals(2, blocks.count { it is MdBlock.Image })
    }

    @Test
    fun `same line badges with nbsp separators form a row`() {
        val md = "![a](https://img/a.svg)&nbsp;![b](https://img/b.svg)"
        val blocks = parseMarkdown(md, profileMode = true)
        assertEquals(1, blocks.size)
        assertEquals(2, (blocks[0] as MdBlock.ImageRow).items.size)
    }

    @Test
    fun `blank line between images keeps them separate`() {
        val md = "![a](https://img/a.svg)\n\n![b](https://img/b.svg)"
        val blocks = parseMarkdown(md, profileMode = true)
        assertEquals(2, blocks.count { it is MdBlock.Image })
    }

    @Test
    fun `text around badges stays in paragraphs`() {
        val md = "开场白\n![a](https://img/a.svg) ![b](https://img/b.svg)\n收尾"
        val blocks = parseMarkdown(md, profileMode = true)
        assertTrue(blocks[0] is MdBlock.Paragraph)
        assertEquals(2, (blocks[1] as MdBlock.ImageRow).items.size)
        assertTrue(blocks[2] is MdBlock.Paragraph)
    }

    @Test
    fun `html anchor with img becomes clickable badge`() {
        val blocks = parseMarkdown(
            "<a href=\"https://example.com\"><img src=\"https://img/x.svg\" alt=\"b\"></a>",
            profileMode = true,
        )
        assertEquals(1, blocks.size)
        val img = blocks[0] as MdBlock.Image
        assertEquals("https://example.com", img.link)
        assertEquals("https://img/x.svg", img.url)
    }

    @Test
    fun `two anchors with imgs sit in one row`() {
        val md = "<a href=\"https://a.example\"><img src=\"https://img/a.svg\"></a>" +
            "&nbsp;<a href=\"https://b.example\"><img src=\"https://img/b.svg\"></a>"
        val blocks = parseMarkdown(md, profileMode = true)
        assertEquals(1, blocks.size)
        assertEquals(2, (blocks[0] as MdBlock.ImageRow).items.size)
    }
}
