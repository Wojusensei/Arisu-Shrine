package dev.arisu.shrine.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightTest {

    private fun kinds(code: String, lang: String): List<Pair<String, TokenKind>> =
        highlightTokens(code, lang).map { code.substring(it.start, it.end) to it.kind }

    @Test
    fun kotlinKeywordsStringsAndComments() {
        val code = "val name = \"arisu\" // 神社"
        val tokens = kinds(code, "kotlin")
        assertTrue(tokens.size >= 3)
        assertTrue(("val" to TokenKind.KEYWORD) in tokens)
        assertEquals("\"arisu\"" to TokenKind.STRING, tokens.first { it.first.startsWith("\"") })
        assertEquals("// 神社" to TokenKind.COMMENT, tokens.last())
    }

    @Test
    fun commentMarkerInsideStringIsNotComment() {
        val tokens = kinds("""val url = "https://x//y" # nope""", "kotlin")
        assertTrue(tokens.none { it.second == TokenKind.COMMENT && it.first.startsWith("https") })
        assertEquals("\"https://x//y\"" to TokenKind.STRING, tokens.first { it.first == "\"https://x//y\"" })
    }

    @Test
    fun blockCommentSpansLines() {
        val code = "/* 起\n伏 */ val"
        val tokens = kinds(code, "go")
        assertEquals("/* 起\n伏 */" to TokenKind.COMMENT, tokens.first())
        assertTrue(tokens.none { it.second == TokenKind.KEYWORD && it.first == "起" })
    }

    @Test
    fun pythonTripleQuoteDocstring() {
        val tokens = kinds("\"\"\"文档\n多行\"\"\" x = 1", "python")
        assertEquals(TokenKind.STRING, tokens.first { it.first.startsWith("\"\"\"") }.second)
    }

    @Test
    fun sqlIsCaseInsensitive() {
        val tokens = kinds("SELECT * FROM users", "sql")
        assertEquals(TokenKind.KEYWORD, tokens.first { it.first == "SELECT" }.second)
        assertEquals(TokenKind.KEYWORD, tokens.first { it.first == "FROM" }.second)
        assertTrue(tokens.none { it.first == "users" })
    }

    @Test
    fun jsonOnlyStringsAndNumbers() {
        val tokens = kinds("""{"n": 42, "ok": true}""", "json")
        assertTrue(tokens.filter { it.second == TokenKind.STRING }.size >= 2)
        assertTrue(("42" to TokenKind.NUMBER) in tokens)
        assertTrue(("true" to TokenKind.KEYWORD) in tokens)
    }

    @Test
    fun numbersWithHexAndFloat() {
        val tokens = kinds("val mask = 0xFF and 3.14", "kotlin")
        assertTrue(tokens.any { it.first == "0xFF" && it.second == TokenKind.NUMBER })
        assertTrue(tokens.any { it.first == "3.14" && it.second == TokenKind.NUMBER })
    }

    @Test
    fun annotationPrefix() {
        val tokens = kinds("@Serializable data class P", "kotlin")
        assertTrue(("@Serializable" to TokenKind.ANNOTATION) in tokens)
        assertTrue(("data" to TokenKind.KEYWORD) in tokens)
    }

    @Test
    fun aliasNormalization() {
        assertEquals("javascript", normalizeLang("TS"))
        assertEquals("bash", normalizeLang("sh"))
        assertEquals("python", normalizeLang("py"))
        assertEquals("xml", normalizeLang("html"))
        assertEquals("go", normalizeLang("Golang"))
    }

    @Test
    fun unknownLanguageYieldsNoSpans() {
        assertTrue(highlightTokens("any thing", "cobol").isEmpty())
        assertTrue(highlightTokens("any thing", "").isEmpty())
    }

    @Test
    fun escapedQuoteDoesNotEndString() {
        val tokens = kinds("val s = \"a\\\"b\"", "kotlin")
        assertEquals(1, tokens.count { it.second == TokenKind.STRING })
    }
}
