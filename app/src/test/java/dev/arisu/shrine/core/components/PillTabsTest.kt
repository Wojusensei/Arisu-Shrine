package dev.arisu.shrine.core.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.arisu.shrine.core.design.ArisuShrineTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h891dp-xhdpi")
class PillTabsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val inboxLabels = listOf("全部", "未读", "收藏", "回复@", "系统", "举报")

    @Composable
    private fun slotTextRoomPx(itemCount: Int): Float {
        val density = LocalDensity.current
        val screenPx = with(density) { 360.dp.toPx() }
        val trackPx = screenPx - with(density) { 10.dp.toPx() } * 2
        val innerPx = trackPx - with(density) { 4.dp.toPx() } * 2
        val slotPx = (innerPx - with(density) { 2.dp.toPx() } * (itemCount - 1)) / itemCount
        return slotPx - with(density) { 7.dp.toPx() } * 2
    }

    @Test
    fun sixInboxLabelsFitEqualSlots() {
        val measured = mutableMapOf<String, Int>()
        rule.setContent {
            ArisuShrineTheme {

                val measurer = rememberTextMeasurer()
                val style: TextStyle = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
                )
                val room = slotTextRoomPx(inboxLabels.size)
                Box(Modifier.fillMaxWidth()) {
                    PillTabs(
                        items = inboxLabels,
                        selected = inboxLabels.first(),
                        onSelect = {},
                        label = { it },
                    )
                    inboxLabels.forEach { label ->
                        measured[label] = measurer.measure(label, style).size.width

                        measured["$label#room"] = room.toInt()
                    }
                }
            }
        }
        rule.waitForIdle()

        inboxLabels.forEach { label ->
            val width = measured.getValue(label)
            val room = measured.getValue("$label#room")
            assertTrue(
                "「$label」渲染宽 ${width}px 超出等分格文字区 ${room}px，会被截断",
                width <= room,
            )
        }
    }

    @Test
    fun componentRendersAllLabelsUnclipped() {

        rule.setContent {
            ArisuShrineTheme {
                var selected by remember { mutableStateOf(inboxLabels.first()) }
                PillTabs(
                    items = inboxLabels,
                    selected = selected,
                    onSelect = { selected = it },
                    label = { it },
                )
            }
        }
        rule.waitForIdle()
        inboxLabels.forEach { label ->
            rule.onNodeWithText(label).assertExists()
        }
    }
}
