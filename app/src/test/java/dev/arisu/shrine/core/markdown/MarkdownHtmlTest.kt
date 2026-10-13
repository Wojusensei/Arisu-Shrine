package dev.arisu.shrine.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownHtmlTest {

    @Test
    fun `html comments are stripped`() {
        assertEquals("前文后文", normalizeHtml("前文<!-- 隐藏的注释 -->后文"))
    }

    @Test
    fun `br tags become newlines`() {
        assertEquals("一\n二", normalizeHtml("一<br>二"))
    }

    @Test
    fun `picture prefers img then source`() {
        val out = normalizeHtml(
            "<picture>" +
                "<source srcset=\"https://a/dark.png\" media=\"(prefers-color-scheme: dark)\">" +
                "<img src=\"https://a/main.png\" alt=\"图\">" +
                "</picture>",
        )

        assertEquals("![图](https://a/main.png)", out)
    }

    @Test
    fun `picture falls back to first source srcset`() {
        val out = normalizeHtml(
            "<picture><source srcset=\"https://a/graph.png 1x\" media=\"(prefers-color-scheme: dark)\"></picture>",
        )
        assertEquals("![](https://a/graph.png)", out)
    }

    @Test
    fun `img inside anchor nests into badge link`() {
        val out = normalizeHtml(
            "<a href=\"https://example.com\"><img src=\"https://img/x.svg\" alt=\"b\"></a>",
        )
        assertEquals("[![b](https://img/x.svg)](https://example.com)", out)
    }

    @Test
    fun `tables convert to markdown`() {
        val out = normalizeHtml(
            "<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>",
        )
        assertTrue(out.contains("| A | B |"))
        assertTrue(out.contains("| --- | --- |"))
        assertTrue(out.contains("| 1 | 2 |"))
    }

    @Test
    fun `details converts to quote with bold summary`() {
        val out = normalizeHtml("<details><summary>点开</summary>内容</details>")
        assertTrue(out.contains("> **▸ 点开**"))
        assertTrue(out.contains("> 内容"))
    }

    @Test
    fun `formatting tags map to markdown markers`() {
        assertEquals(
            "加粗**b**斜体*i*删除~~s~~",
            normalizeHtml("加粗<b>b</b>斜体<i>i</i>删除<s>s</s>"),
        )
    }

    @Test
    fun `links convert with href`() {
        assertEquals(
            "看[这个](https://example.com)就行",
            normalizeHtml("看<a href=\"https://example.com\">这个</a>就行"),
        )
    }

    @Test
    fun `unknown tags unwrap and entities decode`() {
        assertEquals("文本 & 更多", normalizeHtml("<font color=\"red\">文本</font> &amp; 更多"))
    }

    @Test
    fun `entities decode in pure markdown without html`() {

        assertEquals("a b", normalizeHtml("a&nbsp;b"))
    }

    @Test
    fun `code fences survive html conversion`() {
        val src = "看代码：\n```html\n<b>不是标签</b>\n```\n完"
        assertEquals(src, normalizeHtml(src))
    }

    @Test
    fun `center container keeps markers`() {
        val out = normalizeHtml("<div align=\"center\"><img src=\"https://a/logo.png\" alt=\"logo\"></div>")
        assertTrue(out.contains("\u0001center\u0001"))
        assertTrue(out.contains("\u0001center-end\u0001"))
        assertTrue(out.contains("![logo](https://a/logo.png)"))
    }

    @Test
    fun `blocks inside center container are marked centered`() {
        val blocks = parseMarkdown(
            "<div align=\"center\">\n<img src=\"https://a/logo.png\" alt=\"logo\">\n# 站名\n</div>",
            profileMode = true,
        )
        val image = blocks.filterIsInstance<MdBlock.Image>().firstOrNull()
        val heading = blocks.filterIsInstance<MdBlock.Heading>().firstOrNull()
        assertTrue("图片应在居中容器内", image?.centered == true)
        assertTrue("标题应在居中容器内", heading?.centered == true)
    }

    @Test
    fun `blocks outside center container stay left aligned`() {
        val blocks = parseMarkdown("# 站名\n\n正文", profileMode = true)
        assertTrue(blocks.filterIsInstance<MdBlock.Heading>().all { !it.centered })
        assertTrue(blocks.filterIsInstance<MdBlock.Paragraph>().all { !it.centered })
    }
}
