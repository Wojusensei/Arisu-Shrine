package dev.arisu.shrine.core.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.arisu.shrine.data.model.Sticker
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 长按拖拽排序的手势回归：拖起后越过半格与邻格换位，
 * 「添加」格不参与、行内不越界（那些边界由 StickerStepTest 的纯函数兜底）。
 * 只发手势不落库——落库是「保存」的事，这里断言的是草稿顺序。
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class StickerOrderGridTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun sample() = listOf("1", "2", "3", "4", "5", "6").map {
        Sticker(id = it, url = "https://example.com/$it.png")
    }

    private fun setGrid(items: SnapshotStateList<Sticker>) {
        rule.setContent {
            StickerOrderGrid(
                items = remember { items },
                firstCell = {
                    Box(
                        Modifier
                            .size(STICKER_CELL)
                            .semantics { contentDescription = "首格" },
                    )
                },
            )
        }
        rule.waitForIdle()
    }

    /** 一格位（格 + 间距）的像素宽 */
    private fun pitchPx(): Float =
        with(rule.density) { (STICKER_CELL + STICKER_GAP).toPx() }

    /**
     * 按住第一格（槽 1）拖出 [delta]。
     *
     * 在根坐标上注入：测试环境加载不出图片，表情格的语义框是空的（0 高）没法当锚点，
     * 用首格（真尺寸）落点：它的中心右移一格位就是第一格（槽 1）的格心。
     */
    private fun drag(delta: Offset) {
        val anchor = rule.onNodeWithContentDescription("首格")
            .fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            down(Offset(anchor.x + pitchPx(), anchor.y))
            advanceEventTime(700) // 长按判定
            moveBy(delta)
            advanceEventTime(50)
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun dragRightSwapsWithNeighbour() {
        val items = mutableStateListOf<Sticker>().apply { addAll(sample()) }
        setGrid(items)

        drag(Offset(pitchPx() * 1.2f, 0f))

        assertEquals(listOf("2", "1", "3", "4", "5", "6"), items.map { it.id })
    }

    @Test
    fun dragDownSwapsAcrossRows() {
        val items = mutableStateListOf<Sticker>().apply { addAll(sample()) }
        setGrid(items)

        drag(Offset(0f, pitchPx() * 1.2f))

        // 槽 1 与槽 5 换位（第二行第一格）
        assertEquals(listOf("5", "2", "3", "4", "1", "6"), items.map { it.id })
    }

    @Test
    fun dragLeftDoesNotEnterAddCell() {
        val items = mutableStateListOf<Sticker>().apply { addAll(sample()) }
        setGrid(items)

        drag(Offset(-pitchPx() * 1.2f, 0f))

        assertEquals(listOf("1", "2", "3", "4", "5", "6"), items.map { it.id })
    }
}
