package dev.arisu.shrine.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.arisu.shrine.core.design.ArisuShrineTheme
import dev.arisu.shrine.core.design.SerifFamily
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FontProbeTest {

    companion object {
        init {

            System.setProperty("roborazzi.test.record", "true")
        }
    }

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun dir(): File {
        val d = File(System.getProperty("arisu.screenshot.dir") ?: "screenshots")
        d.mkdirs()
        return d
    }

    private fun shoot(fileName: String, content: @Composable () -> Unit) {
        rule.setContent {
            ArisuShrineTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
        rule.onRoot().captureRoboImage(File(dir(), fileName).absolutePath)
    }

    @Test
    fun step1RenderSerif() {
        shoot("font-cjk-serif.png") {
            Text(
                "少女祈祷中",
                style = TextStyle(fontFamily = SerifFamily, fontSize = 40.sp),
                modifier = Modifier.padding(24.dp),
            )
        }
    }

    @Test
    fun step2RenderSans() {
        shoot("font-cjk-sans.png") {
            Text(
                "少女祈祷中",
                style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 40.sp),
                modifier = Modifier.padding(24.dp),
            )
        }
    }

    @Test
    fun step3SerifMustDifferFromSans() {
        val a = File(dir(), "font-cjk-serif.png").readBytes()
        val b = File(dir(), "font-cjk-sans.png").readBytes()
        org.junit.Assert.assertTrue(
            "SerifFamily 与系统无衬线渲染出了同一张图——汉字没走到思源宋体",
            !a.contentEquals(b),
        )
    }

    @Test
    fun step4RenderRegular() {
        shoot("font-weight-400.png") {
            Text(
                "神社参拜 Regular 123",
                style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp),
                modifier = Modifier.padding(24.dp),
            )
        }
    }

    @Test
    fun step5RenderBold() {
        shoot("font-weight-700.png") {
            Text(
                "神社参拜 Bold 123",
                style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
                modifier = Modifier.padding(24.dp),
            )
        }
    }

    @Test
    fun step6WeightsMustDiffer() {
        val a = File(dir(), "font-weight-400.png").readBytes()
        val b = File(dir(), "font-weight-700.png").readBytes()
        org.junit.Assert.assertTrue(
            "Regular 与 Bold 渲染一致——四个字重文件是同一份轮廓的假字重",
            !a.contentEquals(b),
        )
    }

    @Test
    fun zzProbeFonts() {
        rule.setContent {
            ArisuShrineTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(24.dp)) {
                        Text("有栖神社 有栖茶话 绘马", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Arisu Shrine Times New Roman 1234567890",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "arisu_000001  User_000001  2026-09-25",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "常规字重 Regular 思源宋体 ABCdef 123",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "中等字重 Medium 思源宋体 ABCdef 123",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "加粗字重 Bold 思源宋体 ABCdef 123",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "斜体 Italic ABCdef 测试",
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        )
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage(File(dir(), "00-字体探针.png").absolutePath)
    }
}
