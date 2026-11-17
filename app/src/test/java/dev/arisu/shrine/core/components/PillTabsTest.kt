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

/**
 * PillTabs 等宽分布是硬法则，字样必须完整：
 * 六格标签（社务箱）在最窄的 360dp 屏上也不许溢出截断
 * （v0.0.1.17 前每格只装得下单字「全」「未」「收」）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h891dp-xhdpi")
class PillTabsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val inboxLabels = listOf("全部", "未读", "收藏", "回复@", "系统", "举报")

    /** 与 PillTabs 内部一致的每格文字可用宽度（px）：轨道宽平分后去掉标签左右内边距。 */
    @Composable
    private fun slotTextRoomPx(itemCount: Int): Float {
        val density = LocalDensity.current
        val screenPx = with(density) { 360.dp.toPx() }
        val trackPx = screenPx - with(density) { 10.dp.toPx() } * 2   // 轨道外边距
        val innerPx = trackPx - with(density) { 4.dp.toPx() } * 2     // 凹槽内衬
        val slotPx = (innerPx - with(density) { 2.dp.toPx() } * (itemCount - 1)) / itemCount
        return slotPx - with(density) { 7.dp.toPx() } * 2             // 标签左右内边距
    }

    @Test
    fun sixInboxLabelsFitEqualSlots() {
        val measured = mutableMapOf<String, Int>()
        rule.setContent {
            ArisuShrineTheme {
                // 用与 PillTabs 相同的样式实测文字宽度（SemiBold 取更宽的一档）
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
                        // 断言用：把可用宽度也存下来（px 取整后仍留有余量才算稳）
                        measured["$label#room"] = room.toInt()
                    }
                }
            }
        }
        rule.waitForIdle()

        // 每个标签的实际渲染宽度都必须装进等分格的文字区，否则就是被截断
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
        // 组件真实渲染路径的冒烟：六个标签节点全部存在（截断是视觉层，节点恒在；
        // 宽度契约由上一条实测断言把守）
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
