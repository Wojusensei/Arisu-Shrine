package dev.arisu.shrine.core.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.arisu.shrine.core.design.ArisuShrineTheme
import dev.arisu.shrine.data.model.Role
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class AvatarRingGapTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ringedAvatarLeavesGapAroundAvatar() {
        rule.setContent {
            ArisuShrineTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.testTag("host")) {
                        ArisuAvatar(seed = 0, name = "两心", size = 40.dp, role = Role.GUUJI, ring = true,
                            modifier = Modifier.testTag("ringed"))
                    }
                }
            }
        }
        rule.waitForIdle()

        val density = rule.activity.resources.displayMetrics.density
        val ringed = rule.onNodeWithTag("ringed").fetchSemanticsNode().boundsInRoot.width

        assertEquals((48f * density), ringed, 1.5f)
    }

    @Test
    fun plainAvatarKeepsExactSize() {
        rule.setContent {
            ArisuShrineTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ArisuAvatar(seed = 0, name = "两心", size = 40.dp, modifier = Modifier.testTag("plain"))
                }
            }
        }
        rule.waitForIdle()

        val density = rule.activity.resources.displayMetrics.density
        val plain = rule.onNodeWithTag("plain").fetchSemanticsNode().boundsInRoot.width
        assertEquals((40f * density), plain, 1.5f)
    }
}
