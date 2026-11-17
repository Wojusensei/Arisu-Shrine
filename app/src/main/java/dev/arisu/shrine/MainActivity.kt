package dev.arisu.shrine

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.arisu.shrine.core.design.ArisuShrineTheme
import dev.arisu.shrine.core.design.ArisuTheme
import dev.arisu.shrine.core.design.ReadingScale
import dev.arisu.shrine.data.ReadingPrefs
import dev.arisu.shrine.data.ThemePrefs
import dev.arisu.shrine.nav.ArisuApp
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private var nightOverride: Boolean? = null
    private var readingScale: Float = 1f

    override fun attachBaseContext(newBase: Context) {
        // 主题规则的源头：默认亮色，本机手动覆盖永久生效（DataStore）。
        // 在 attachBaseContext 注入 uiMode，启动窗口背景（values-night）与
        // windowBackground 都按偏好解析——冷启动首帧即正确，不闪色。
        // 系统深浅不再参与：默认亮色，拨过开关以手动为准。
        nightOverride = runBlocking { ThemePrefs.currentNightOverride(newBase) }
        readingScale = runBlocking { ReadingPrefs.currentScale(newBase) }
        val dark = nightOverride ?: false
        val config = Configuration(newBase.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 开关拨动后的即时切换走 ArisuTheme.setNightPref（全树重组，不重建 Activity）；
        // 这里只在冷启动时把偏好灌进来。
        ArisuTheme.apply(nightOverride)
        ReadingScale.apply(readingScale)
        setContent {
            ArisuShrineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ArisuApp()
                }
            }
        }
    }
}
