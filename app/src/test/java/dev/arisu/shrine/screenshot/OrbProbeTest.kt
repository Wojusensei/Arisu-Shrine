package dev.arisu.shrine.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.arisu.shrine.core.components.ArisuPrimaryButton
import dev.arisu.shrine.core.components.PageBackground
import dev.arisu.shrine.core.components.YinYangOrb
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.design.ArisuTheme
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.Vermilion
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 阴阳玉探针：盯的是**朱底按钮上那颗载入玉**——此前「白 + 白32%」两瓣都是浅色，
 * 18dp 下糊成一坨，用户点名两次「不像太极球」。这里渲染真实按钮组件，
 * 亮暗两套主题各录一张，改配色前后都能拿来对着看。
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class OrbProbeTest : ScreenshotBase() {

    @Test
    fun probeYinYangLight() {
        ArisuTheme.apply(false)
        shot("00-阴阳玉探针") { ProbeBody() }
    }

    @Test
    fun probeYinYangDark() {
        ArisuTheme.apply(true)
        try {
            shot("00-阴阳玉探针-暗色") { ProbeBody() }
        } finally {
            ArisuTheme.apply(false)
        }
    }

    @Composable
    private fun ProbeBody() {
        PageBackground {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("登录按钮载入态（真实组件）", style = MaterialTheme.typography.labelMedium)
                ArisuPrimaryButton(text = "登入", onClick = {}, busy = true)
                ArisuPrimaryButton(text = "登入（禁用）", onClick = {}, busy = true, enabled = false)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .width(120.dp)
                            .height(40.dp)
                            .neuInset(shape = RoundedCornerShape(12.dp), color = NeuInset),
                        contentAlignment = Alignment.Center,
                    ) { YinYangOrb(size = 16.dp, rotating = false) }
                    Text("发送验证码·寄送中", style = MaterialTheme.typography.labelSmall)
                }

                Text("放大看：按钮态 48/96dp，纸底 48/96dp", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OnVermilion(48.dp)
                    OnVermilion(96.dp)
                    OnPaper(48.dp)
                    OnPaper(96.dp)
                }
            }
        }
    }

    @Composable
    private fun OnVermilion(size: Dp) {
        Box(
            Modifier.size(size + 16.dp).background(Vermilion, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            YinYangOrb(
                size = size,
                primary = Color.White,
                base = Color.Black.copy(alpha = 0.30f),
                ring = Color.Transparent,
                rotating = false,
            )
        }
    }

    @Composable
    private fun OnPaper(size: Dp) {
        Box(
            Modifier.size(size + 16.dp).background(NeuInset, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            YinYangOrb(size = size, rotating = false)
        }
    }
}
