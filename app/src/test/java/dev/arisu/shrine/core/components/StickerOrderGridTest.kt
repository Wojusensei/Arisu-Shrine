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

    private fun pitchPx(): Float =
        with(rule.density) { (STICKER_CELL + STICKER_GAP).toPx() }

    private fun drag(delta: Offset) {
        val anchor = rule.onNodeWithContentDescription("首格")
            .fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            down(Offset(anchor.x + pitchPx(), anchor.y))
            advanceEventTime(700)
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
