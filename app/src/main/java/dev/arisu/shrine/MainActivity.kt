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
import dev.arisu.shrine.data.ThemePrefs
import dev.arisu.shrine.nav.ArisuApp
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private var nightOverride: Boolean? = null

    override fun attachBaseContext(newBase: Context) {

        nightOverride = runBlocking { ThemePrefs.currentNightOverride(newBase) }
        val dark = nightOverride ?: false
        val config = Configuration(newBase.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ArisuTheme.apply(nightOverride)
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
