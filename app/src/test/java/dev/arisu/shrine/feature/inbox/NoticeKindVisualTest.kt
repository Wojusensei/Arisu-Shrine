package dev.arisu.shrine.feature.inbox

import dev.arisu.shrine.data.model.NoticeKind
import org.junit.Assert.assertTrue
import org.junit.Test

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
