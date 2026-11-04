package dev.arisu.shrine.core.markdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditTest {

    private fun value(text: String, start: Int, end: Int = start) =
        TextFieldValue(text, TextRange(start, end))

    @Test
    fun boldWrapsSelection() {
        val after = toggleInline(value("hello world", 0, 5), "**", "**", trimFirst = true)
        assertEquals("**hello** world", after.text)
        assertEquals(TextRange(2, 7), after.selection)
    }

    @Test
    fun boldAgainUnwrapsSelection() {

        val after = toggleInline(value("**hello** world", 2, 7), "**", "**", trimFirst = true)
        assertEquals("hello world", after.text)
        assertEquals(TextRange(0, 5), after.selection)
    }

    @Test
    fun boldUnwrapsWhenSelectionIncludesMarkers() {
        val after = toggleInline(value("**hello**", 0, 9), "**", "**", trimFirst = true)
        assertEquals("hello", after.text)
        assertEquals(TextRange(0, 5), after.selection)
    }

    @Test
    fun boldUnwrapsFromCaretInsideMarkers() {
        val after = toggleInline(value("**hello**", 4), "**", "**", trimFirst = true)
        assertEquals("hello", after.text)
        assertEquals(TextRange(2), after.selection)
    }

    @Test
    fun noSelectionInsertsEmptyPairWithCaretInside() {
        val after = toggleInline(value("ab", 1), "**", "**", trimFirst = true)
        assertEquals("a****b", after.text)
        assertEquals(TextRange(3), after.selection)
    }

    @Test
    fun trimFirstLeavesWhitespaceOutsideMarkers() {
        val after = toggleInline(value(" hello ", 0, 7), "**", "**", trimFirst = true)
        assertEquals(" **hello** ", after.text)
        assertEquals(TextRange(3, 8), after.selection)
    }

    @Test
    fun italicUsesStar() {
        val after = toggleInline(value("hello", 0, 5), "*", "*", trimFirst = true)
        assertEquals("*hello*", after.text)
        assertEquals(TextRange(1, 6), after.selection)
    }

    @Test
    fun italicOnBoldTextMakesItBoldItalic() {

        val after = toggleInline(value("**hello**", 2, 7), "*", "*", trimFirst = true)
        assertEquals("***hello***", after.text)
        assertEquals(TextRange(3, 8), after.selection)
    }

    @Test
    fun italicAgainOnBoldItalicRemovesItalic() {
        val after = toggleInline(value("***hello***", 3, 8), "*", "*", trimFirst = true)
        assertEquals("**hello**", after.text)
        assertEquals(TextRange(2, 7), after.selection)
    }

    @Test
    fun italicOffWholeBoldItalicSelection() {
        val after = toggleInline(value("***hello***", 0, 11), "*", "*", trimFirst = true)
        assertEquals("**hello**", after.text)
        assertEquals(TextRange(2, 7), after.selection)
    }

    @Test
    fun italicUnwrapsSelectionInsideStars() {
        val after = toggleInline(value("*hello* world", 1, 6), "*", "*", trimFirst = true)
        assertEquals("hello world", after.text)
        assertEquals(TextRange(0, 5), after.selection)
    }

    @Test
    fun headingPrefixesWholeLine() {
        val after = toggleHeading(value("hello", 5), 2)
        assertEquals("## hello", after.text)
        assertEquals(TextRange(3), after.selection)
    }

    @Test
    fun headingTogglesOff() {
        val after = toggleHeading(value("## hello", 5), 2)
        assertEquals("hello", after.text)
        assertEquals(TextRange(0), after.selection)
    }

    @Test
    fun headingReplacesExistingLevel() {
        val after = toggleHeading(value("### hello", 5), 2)
        assertEquals("## hello", after.text)
    }

    @Test
    fun headingPrefixesEverySelectedLine() {
        val after = toggleHeading(value("a\nb", 0, 3), 2)
        assertEquals("## a\n## b", after.text)
        assertEquals(TextRange(0, 9), after.selection)
    }

    @Test
    fun headingOnBlankLineStartsTypingAfterPrefix() {
        val after = toggleHeading(value("", 0), 2)
        assertEquals("## ", after.text)
        assertEquals(TextRange(3), after.selection)
    }

    @Test
    fun listPrefixesCurrentLine() {
        val after = toggleUnorderedList(value("hello", 5))
        assertEquals("- hello", after.text)
        assertEquals(TextRange(2), after.selection)
    }

    @Test
    fun listTogglesOff() {
        val after = toggleUnorderedList(value("- hello", 5))
        assertEquals("hello", after.text)
        assertEquals(TextRange(0), after.selection)
    }

    @Test
    fun listPrefixesEverySelectedLine() {
        val after = toggleUnorderedList(value("a\nb", 0, 3))
        assertEquals("- a\n- b", after.text)
        assertEquals(TextRange(0, 7), after.selection)
    }

    @Test
    fun listConvertsOrderedToList() {
        val after = toggleUnorderedList(value("1. a\n2. b", 0, 8))
        assertEquals("- a\n- b", after.text)
    }

    @Test
    fun listSurroundsParagraphWithBlankLine() {
        val after = toggleUnorderedList(value("para\nlist", 7))
        assertEquals("para\n\n- list", after.text)
        assertEquals(TextRange(8), after.selection)
    }

    @Test
    fun listSeparatesFollowingParagraph() {
        val after = toggleUnorderedList(value("list\npara", 0, 4))
        assertEquals("- list\n\npara", after.text)
    }

    @Test
    fun listOnBlankLineStartsItem() {
        val after = toggleUnorderedList(value("a\n\nb", 2))
        assertEquals("a\n- \nb", after.text)
        assertEquals(TextRange(4), after.selection)
    }

    @Test
    fun quotePrefixesEverySelectedLine() {
        val after = toggleQuote(value("a\nb", 0, 3))
        assertEquals("> a\n> b", after.text)
    }

    @Test
    fun quoteTogglesOff() {
        val after = toggleQuote(value("> a\n> b", 0, 7))
        assertEquals("a\nb", after.text)
    }

    @Test
    fun quoteSurroundsParagraph() {
        val after = toggleQuote(value("para\nquote", 7))
        assertEquals("para\n\n> quote", after.text)
        assertEquals(TextRange(8), after.selection)
    }

    @Test
    fun quoteSeparatesFollowingParagraph() {
        val after = toggleQuote(value("quote\npara", 0, 5))
        assertEquals("> quote\n\npara", after.text)
    }

    @Test
    fun codeWrapsSingleLineInline() {
        val after = toggleCode(value("hello", 0, 5))
        assertEquals("`hello`", after.text)
        assertEquals(TextRange(1, 6), after.selection)
    }

    @Test
    fun codeUnwrapsInline() {
        val after = toggleCode(value("`hello`", 1, 6))
        assertEquals("hello", after.text)
        assertEquals(TextRange(0, 5), after.selection)
    }

    @Test
    fun codeWrapsMultipleLinesInFence() {
        val after = toggleCode(value("a\nb", 0, 3))
        assertEquals("```\na\nb\n```", after.text)
        assertEquals(TextRange(4, 7), after.selection)
    }

    @Test
    fun codeUnwrapsContentBetweenFences() {
        val after = toggleCode(value("```\na\nb\n```", 4, 7))
        assertEquals("a\nb", after.text)
        assertEquals(TextRange(0, 3), after.selection)
    }

    @Test
    fun codeUnwrapsSelectedFenceBlock() {
        val after = toggleCode(value("```\na\nb\n```", 0, 11))
        assertEquals("a\nb", after.text)
        assertEquals(TextRange(0, 3), after.selection)
    }

    @Test
    fun linkWrapsTextAndSelectsUrl() {
        val after = toggleLink(value("hello", 0, 5))
        assertEquals("[hello](url)", after.text)
        assertEquals(TextRange(8, 11), after.selection)
    }

    @Test
    fun linkWithNoSelectionSelectsUrl() {
        val after = toggleLink(value("hello", 5))
        assertEquals("hello[](url)", after.text)
        assertEquals(TextRange(8, 11), after.selection)
    }

    @Test
    fun linkFromUrlSelectionPutsCaretInBrackets() {
        val after = toggleLink(value("https://x.dev", 0, 13))
        assertEquals("[](https://x.dev)", after.text)
        assertEquals(TextRange(1), after.selection)
    }

    @Test
    fun linkUnwrapsExistingLink() {
        val after = toggleLink(value("[hello](https://x.dev)", 0, 22))
        assertEquals("hello", after.text)
        assertEquals(TextRange(0, 5), after.selection)
    }

    @Test
    fun linkUnwrapsExistingPlaceholderLink() {
        val after = toggleLink(value("[hello](url)", 0, 12))
        assertEquals("hello", after.text)
    }
}
