package dev.arisu.shrine.feature.profile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.arisu.shrine.core.design.ArisuShrineTheme
import dev.arisu.shrine.data.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ProfileIdentityLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nicknameCenteredAndRoleFollowsStatus() {
        rule.setContent {
            ArisuShrineTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.testTag(BoxTag).width(320.dp), contentAlignment = Alignment.Center) {
                        ProfileIdentity("两心", Role.GUUJI, "祈祷中")
                    }
                }
            }
        }
        rule.waitForIdle()

        val box = rule.onNodeWithTag(BoxTag).fetchSemanticsNode().boundsInRoot
        val name = rule.onNodeWithText("两心").fetchSemanticsNode().boundsInRoot
        val status = rule.onNodeWithText("祈祷中").fetchSemanticsNode().boundsInRoot
        val tag = rule.onNodeWithText("宫司").fetchSemanticsNode().boundsInRoot

        assertEquals("昵称应水平居中", box.center.x, name.center.x, box.width * 0.02f)
        assertTrue(
            "职务标签应排在个人状态之后（tag.left=${tag.left} status.right=${status.right}）",
            tag.left >= status.right - 2f,
        )
        assertEquals("职务标签与个人状态应同一行", status.center.y, tag.center.y, 3f)
    }

    @Test
    fun roleStillShowsWhenStatusBlank() {
        rule.setContent {
            ArisuShrineTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.testTag(BoxTag).width(320.dp), contentAlignment = Alignment.Center) {
                        ProfileIdentity("两心", Role.MEMBER, "")
                    }
                }
            }
        }
        rule.waitForIdle()

        val box = rule.onNodeWithTag(BoxTag).fetchSemanticsNode().boundsInRoot
        val tag = rule.onNodeWithText("社员").fetchSemanticsNode().boundsInRoot
        assertEquals("无状态时职务标签应水平居中", box.center.x, tag.center.x, box.width * 0.02f)
    }

    private companion object {
        const val BoxTag = "identityBox"
    }
}
