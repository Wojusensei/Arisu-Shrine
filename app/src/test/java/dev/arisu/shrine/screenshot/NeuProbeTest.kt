package dev.arisu.shrine.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.NeuInset
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.PageBackground
import dev.arisu.shrine.core.components.accentRaised
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.components.softCard
import dev.arisu.shrine.core.design.ArisuShrineTheme
import dev.arisu.shrine.core.design.NeuBase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class NeuProbeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun probeNeumorphism() {
        rule.setContent {

            ArisuShrineTheme {
                PageBackground {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        Text("新拟物探针", style = MaterialTheme.typography.headlineSmall)

                        NeuSurface(
                            shape = RoundedCornerShape(22.dp),
                            depth = 6.dp,
                            blur = 12.dp,
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text("NeuSurface（双层真实模糊）", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(6.dp))
                                Text("上缘受光 + 下缘落影，左右对称，和底面同色", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .softCard(shape = RoundedCornerShape(20.dp))
                                .padding(20.dp),
                        ) {
                            Text("softCard（elevation 近似）", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Text("用在列表里的大量小卡片上", style = MaterialTheme.typography.bodySmall)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            NeuInset(
                                Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("凹陷输入槽", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Box(
                                Modifier
                                    .width(52.dp)
                                    .height(52.dp)
                                    .neuInset(shape = RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("凹", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .accentRaised(shape = RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "朱色强调按钮",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            ArisuAvatar(seed = 0, name = "有栖", size = 52.dp)
                            ArisuAvatar(seed = 7, name = "灵梦", size = 40.dp)
                            ArisuAvatar(seed = 12, name = "魔理沙", size = 30.dp)
                            Text("默认头像：暖灰人像剪影", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .padding(horizontal = 8.dp)
                                .background(NeuBase)
                        )
                    }
                }
            }
        }
        val dir = File(System.getProperty("arisu.screenshot.dir") ?: "screenshots")
        dir.mkdirs()
        rule.onRoot().captureRoboImage(File(dir, "00-新拟物探针.png").absolutePath)
    }
}
