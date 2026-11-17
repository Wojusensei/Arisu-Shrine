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

/**
 * 字体探针 + 字体**断言**。
 *
 * v0.0.0.5 之前这里只有 [probeFonts] 一张截图供人目测——静态检查和目测
 * 连续三轮都没拦住「中文实际渲染成系统黑体」。现在补两条像素断言：
 *
 * · step1-3 [step3SerifMustDifferFromSans]：同一段汉字，SerifFamily 渲染结果
 *   必须与系统无衬线不同——家族里思源宋体必须真的被命中，
 *   拦住「Tinos 在前、汉字回落系统黑体」一类的回退失效；
 * · step4-6 [step6WeightsMustDiffer]：Regular 与 Bold 渲染必须不同——
 *   拦住「四份文件其实是同一份轮廓、只改了字重元数据」一类的假字重
 *   （v0.0.0.5 的四个 noto_serif_*.ttf 全是 ExtraLight）。
 *
 * Robolectric 的 setContent 每个用例只能调一次，所以拆成
 * 渲染→渲染→比较 三步，按方法名字典序执行（@FixMethodOrder）。
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FontProbeTest {

    companion object {
        init {
            // 普通 test 任务下 roborazzi 默认不落盘（taskType=None），
            // 这里的断言必须真的拿到像素——强制开录制。
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
