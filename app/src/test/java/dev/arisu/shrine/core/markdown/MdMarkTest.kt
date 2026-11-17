package dev.arisu.shrine.core.markdown

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Markdown 记号插入（纯函数）：落在光标处、包住选中、块级记号不粘句子中间。
 */
class MdMarkTest {

    private val bold = MdMark("**粗体**", select = "粗体")
    private val heading = MdMark("## 小标题", select = "小标题", block = true)

    private fun value(text: String, start: Int, end: Int = start) = TextFieldValue(text, TextRange(start, end))

    @Test
    fun insertsAtCaretInMiddle() {
        val after = insertMark(value("前后", 1), bold)
        assertEquals("前**粗体**后", after.text)
        // 插完选中占位词：接着打字就是替换它
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
        // 选中内容原地留下，取消选中就还在原处
        assertEquals(TextRange(4, 6), after.selection)
    }

    @Test
    fun wrapsSelectionAroundInsertPointWhenMarkHasNoPlaceholder() {
        // 不依赖占位词：切点由 caret 给（绘马那种空记号）
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
        // 补充平面上的字符占两个 char：光标该落在整个字之后
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
        // 行内记号就落在光标处，不额外换行
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
        // 老稿子 restore 出来的选区可能越界：不能因此炸掉
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

    // 正文配图：选图时在光标处放占位符（独占一行），上传完成后原位替换成真地址。
    // 此前上传完成一律追加到末尾，图片永远落在帖子最后。
    private val imageMark = MdMark("![图片](https://x/a.png)\n", block = true)

    @Test
    fun imageBlockInsertsAtCaretOnOwnLine() {
        val after = insertMark(value("前半后半", 2), imageMark)
        assertEquals("前半\n![图片](https://x/a.png)\n后半", after.text)
        // 光标落在插入内容之后：接着打字不会顶到图前面
        assertEquals(TextRange("前半\n".length + imageMark.text.length), after.selection)
    }

    @Test
    fun imageBlockAtEmptyBody() {
        val after = insertMark(value("", 0), imageMark)
        assertEquals("![图片](https://x/a.png)\n", after.text)
    }

    @Test
    fun imageBlockAtLineStartKeepsOneLine() {
        val after = insertMark(value("下一段", 0), imageMark)
        assertEquals("![图片](https://x/a.png)\n下一段", after.text)
    }

    // 多图上传：占位符换成真地址后长度会变，光标必须跟着挪，
    // 否则下一张的占位符会被打字拆坏，渲染成一段坏 Markdown。
    private val placeholder = "![图片](上传中-1)"
    private val snippet = "![图片](https://x/a.png)"

    @Test
    fun replaceSpanKeepsCaretAfterReplacement() {
        val text = "$placeholder\n"
        val after = value(text, text.length).replaceSpan(placeholder, snippet)
        assertEquals("$snippet\n", after.text)
        assertEquals(TextRange(snippet.length + 1), after.selection)
    }

    @Test
    fun replaceSpanShiftsTextTypedAfterPlaceholder() {
        val text = "$placeholder\n后半"
        val after = value(text, text.length).replaceSpan(placeholder, snippet)
        assertEquals("$snippet\n后半", after.text)
        assertEquals(TextRange(after.text.length), after.selection)
    }

    @Test
    fun replaceSpanCollapsesCaretInsideReplacement() {
        val after = value(placeholder, 3).replaceSpan(placeholder, snippet)
        assertEquals(TextRange(snippet.length), after.selection)
    }

    @Test
    fun replaceSpanLeavesEarlierCaretAlone() {
        val after = value("前$placeholder", 1).replaceSpan(placeholder, snippet)
        assertEquals("前$snippet", after.text)
        assertEquals(TextRange(1), after.selection)
    }

    @Test
    fun replaceSpanRemovesAndPullsCaretBack() {
        val text = "前${placeholder}后"
        val after = value(text, text.length).replaceSpan(placeholder, "")
        assertEquals("前后", after.text)
        assertEquals(TextRange(2), after.selection)
    }

    @Test
    fun replaceSpanMissingTextIsNoOp() {
        val before = value("正文", 1)
        assertEquals(before, before.replaceSpan("不存在", "x"))
    }

    // 提及补全：把触发字符 @ 换成 @User_000015，光标必须落在整段编号之后
    @Test
    fun replaceRangeWithPutsCaretAfterInsert() {
        val after = value("正文 @", 4).replaceRangeWith(3, 4, "@User_000015 ")
        assertEquals("正文 @User_000015 ", after.text)
        assertEquals(TextRange(after.text.length), after.selection)
    }

    @Test
    fun replaceRangeWithClampsOutOfBounds() {
        val after = value("甲", 1).replaceRangeWith(9, 12, "乙")
        assertEquals("甲乙", after.text)
        assertEquals(TextRange(2), after.selection)
    }

    /** 两张图先后上传：第一张完成时，光标不能被留在第二张的占位符中间 */
    @Test
    fun firstUploadDoesNotSplitSecondPlaceholder() {
        val second = "![图片](上传中-2)"
        var body = insertMark(value("", 0), MdMark("$placeholder\n", block = true))
        body = insertMark(body, MdMark("$second\n", block = true))
        body = body.replaceSpan(placeholder, snippet)
        assertEquals("$snippet\n$second\n", body.text)
        assertEquals(TextRange(body.text.length), body.selection)
        // 接着敲字落在最末，第二张的占位符原样还在
        body = insertMark(body, MdMark("字"))
        assertEquals("$snippet\n$second\n字", body.text)
    }
}
