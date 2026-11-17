package dev.arisu.shrine.feature.inbox

import dev.arisu.shrine.data.model.NoticeKind
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知类型 → 图标/主色 的映射表完整性：新增类型忘了登记时，
 * 列表里会渲染成空白图标。这里守住「每种类型都有视觉」这条底线。
 */
class NoticeKindVisualTest {

    @Test
    fun everyKindHasVisual() {
        NoticeKind.entries.forEach { kind ->
            val (icon, color) = noticeKindVisual(kind)
            assertTrue("缺图标: $kind", icon.name.isNotEmpty())
            assertTrue("缺主色: $kind", color.alpha > 0f)
        }
    }
}
