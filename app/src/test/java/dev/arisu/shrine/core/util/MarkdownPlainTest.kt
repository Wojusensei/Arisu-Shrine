package dev.arisu.shrine.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownPlainTest {

    @Test
    fun stripsHeadingsListsQuotesAndEmphasis() {
        val md = """
            ## 小标题
            **加粗**和*斜体*
            > 引用
            - 列表项
            1. 有序项
        """.trimIndent()

        assertEquals(
            "小标题\n加粗和斜体\n引用\n列表项\n有序项",
            markdownPlainText(md),
        )
    }

    @Test
    fun imagesAndLinksUseTheirText() {
        assertEquals("图 文字", markdownPlainText("![图](https://a) [文字](https://b)"))
    }

    @Test
    fun codeFenceKeepsBodyWithoutLanguage() {
        val md = "```kotlin\nval a = 1\n```"
        assertEquals("val a = 1", markdownPlainText(md))
    }

    @Test
    fun dropsHtmlTags() {
        assertEquals("正文", markdownPlainText("<b>正文</b>"))
    }

    @Test
    fun keepsUnderscoresInsideNames() {
        assertEquals("@User_000001", markdownPlainText("@User_000001"))
    }
}
