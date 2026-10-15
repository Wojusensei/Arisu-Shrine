package dev.arisu.shrine.core.markdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MdMarkTest {

    private val bold = MdMark("**粗体**", select = "粗体")
    private val heading = MdMark("## 小标题", select = "小标题", block = true)

    private fun value(text: String, start: Int, end: Int = start) = TextFieldValue(text, TextRange(start, end))

    @Test
    fun insertsAtCaretInMiddle() {
        val after = insertMark(value("前后", 1), bold)
        assertEquals("前**粗体**后", after.text)

        assertEquals(TextRange(3, 5), after.selection)
    }

    @Test
    fun insertsAtEndCaret() {
        val after = insertMark(value("写了一半", 4), bold)
        assertEquals("写了一半**粗体**", after.text)
        assertEquals(TextRange(6, 8), after.selection)
    }

    @Test
    fun wrapsSelectionWithMark() {
        val after = insertMark(value("你好世界", 2, 4), bold)
        assertEquals("你好**世界**", after.text)

        assertEquals(TextRange(4, 6), after.selection)
    }

    @Test
    fun wrapsSelectionAroundInsertPointWhenMarkHasNoPlaceholder() {

        val link = MdMark("[](https://)", caret = 1)
        val after = insertMark(value("看这里", 1, 3), link)
        assertEquals("看[这里](https://)", after.text)
        assertEquals(TextRange(2, 4), after.selection)
    }

    @Test
    fun emptyMarkPutsCaretBetweenMarkers() {
        val after = insertMark(value("", 0), MdMark("****", caret = 2, block = false))
        assertEquals("****", after.text)
        assertEquals(TextRange(2), after.selection)
    }

    @Test
    fun plainInsertLandsAfterMark() {
        val after = insertMark(value("ab", 1), MdMark("XY"))
        assertEquals("aXYb", after.text)
        assertEquals(TextRange(3), after.selection)
    }

    @Test
    fun emojiInsertLeavesCaretAfterEmoji() {

        val emoji = "🌸"
        val after = insertMark(value("ab", 1), MdMark(emoji))
        assertEquals("a${emoji}b", after.text)
        assertEquals(TextRange(1 + emoji.length), after.selection)
    }

    @Test
    fun blockMarkStartsNewLineMidLine() {
        val after = insertMark(value("句子末尾", 4), heading)
        assertEquals("句子末尾\n## 小标题", after.text)
        assertEquals(TextRange(8, 11), after.selection)
    }

    @Test
    fun blockMarkKeepsLineStart() {
        val after = insertMark(value("首行\n", 3), heading)
        assertEquals("首行\n## 小标题", after.text)
    }

    @Test
    fun blockMarkInEmptyBodyHasNoLeadingNewline() {
        val after = insertMark(value("", 0), heading)
        assertEquals("## 小标题", after.text)
    }

    @Test
    fun inlineMarkNeverBreaksLine() {

        val after = insertMark(value("这句话要加粗", 3), bold)
        assertEquals("这句话**粗体**要加粗", after.text)
    }

    @Test
    fun missingPlaceholderFallsBackToPlainInsert() {
        val mark = MdMark("**粗体**", select = "不存在的词")
        val after = insertMark(value("甲", 1), mark)
        assertEquals("甲**粗体**", after.text)
        assertEquals(TextRange(7), after.selection)
    }

    @Test
    fun selectionBeyondTextIsClamped() {

        val after = insertMark(TextFieldValue("abc", TextRange(9, 9)), bold)
        assertEquals("abc**粗体**", after.text)
    }

    @Test
    fun withTextKeepsCaretWhenUntouched() {
        val original = value("写了三行", 2)
        assertEquals(original, original.withText("写了三行"))
    }

    @Test
    fun withTextPullsCaretBackInside() {
        val after = value("写了一半", 4).withText("短")
        assertEquals("短", after.text)
        assertEquals(TextRange(1), after.selection)
    }
}
