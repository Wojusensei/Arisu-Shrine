package dev.arisu.shrine.screenshot

import androidx.compose.runtime.Composable
import dev.arisu.shrine.core.components.PageBackground
import dev.arisu.shrine.feature.auth.LoginScreen
import dev.arisu.shrine.feature.auth.RegisterScreen
import dev.arisu.shrine.feature.auth.ForgotPasswordScreen
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenGalleryTest : ScreenshotBase() {

    @Test
    fun login() {
        shot("页面-登录") { PageBackground { LoginScreen(onLogin = {}, onGoRegister = {}, onForgot = {}) } }
    }

    @Test
    fun register() {
        shot("页面-注册") { PageBackground { RegisterScreen(onRegister = {}, onGoLogin = {}) } }
    }

    @Test
    fun forgotPassword() {
        shot("页面-忘记密码") { PageBackground { ForgotPasswordScreen(onBack = {}) } }
    }
}
