package dev.arisu.shrine.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.arisu.shrine.core.design.ArisuShrineTheme
import org.junit.Rule
import java.io.File

/**
 * 所有截图测试的公共基类。
 *
 * 通过 Robolectric + Roborazzi 在 JVM 上把 Compose 界面真实渲染成 PNG，
 * 不需要模拟器、不需要真机。产物写到仓库根目录的 `screenshots/`。
 */
abstract class ScreenshotBase {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    protected fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            // 截图基线全部按亮色录制；darkTheme 默认 false，与基线一致
            ArisuShrineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
        val dir = File(System.getProperty("arisu.screenshot.dir") ?: "screenshots")
        dir.mkdirs()
        rule.onRoot().captureRoboImage(File(dir, "$name.png").absolutePath)
    }
}
