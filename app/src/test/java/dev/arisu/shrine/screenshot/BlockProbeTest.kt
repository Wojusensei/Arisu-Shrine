package dev.arisu.shrine.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.arisu.shrine.core.components.ArisuConfirmDialog
import dev.arisu.shrine.core.components.PageBackground
import dev.arisu.shrine.core.design.ArisuShrineTheme
import dev.arisu.shrine.data.model.Role
import dev.arisu.shrine.data.model.User
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.feature.profile.BlockedUsersScreen
import dev.arisu.shrine.feature.profile.SettingsScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class BlockProbeTest {

    @get:Rule
    val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val me = User(id = "User_000001", nickname = "窝居", role = Role.KANNUSHI, statusEmoji = "⛩️")

    private val blocked = listOf(
        User(id = "User_000002", nickname = "Ether♪", role = Role.HOUSHI),
        User(id = "User_000007", nickname = "凡魔王异闻录", role = Role.GUUJI),
        User(id = "User_000010", nickname = "chuchu0wo", role = Role.MEMBER),
    )

    @Before
    fun seed() {
        Shrine.seedForTest(me)
    }

    private fun dir(): File =
        File(System.getProperty("arisu.screenshot.dir") ?: "screenshots").apply { mkdirs() }

    private fun content(block: @Composable () -> Unit) {
        rule.setContent {
            ArisuShrineTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PageBackground { block() }
                }
            }
        }
    }

    @Test
    fun blockedList() {
        content { BlockedUsersScreen(onBack = {}, initial = blocked) }
        rule.onRoot().captureRoboImage(File(dir(), "拉黑-名单页.png").absolutePath)
    }

    @Test
    fun settingsEntry() {
        content { SettingsScreen(onBack = {}) }
        rule.onNodeWithText("拉黑名单").performScrollTo()
        rule.onRoot().captureRoboImage(File(dir(), "拉黑-设置入口.png").absolutePath)
    }

    @Test
    fun confirmDialog() {
        rule.setContent {
            ArisuShrineTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PageBackground { Box(Modifier.fillMaxSize()) }
                    ArisuConfirmDialog(
                        title = "拉黑",
                        body = "拉黑后您将看不到对方的内容与通知，对方也无法关注或打扰您。",
                        confirmText = "拉黑",
                        onConfirm = {},
                        onDismiss = {},
                    )
                }
            }
        }
        rule.onNode(isDialog()).captureRoboImage(File(dir(), "拉黑-确认框.png").absolutePath)
    }
}
