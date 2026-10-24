package dev.arisu.shrine.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.arisu.shrine.core.components.PageBackground
import dev.arisu.shrine.core.design.ArisuShrineTheme
import dev.arisu.shrine.core.markdown.MarkdownText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class CodeHighlightProbeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun probeCodeHighlight() {
        rule.setContent {
            ArisuShrineTheme {
                PageBackground {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text("代码高亮探针", style = MaterialTheme.typography.headlineSmall)
                        MarkdownText(
                            markdown = "```kotlin\n" +
                                "@Serializable\ndata class Ema(\n" +
                                "    val id: Long = 0,\n" +
                                "    val tags: List<String> = emptyList(), // 标签\n" +
                                ")\n```"
                        )
                        MarkdownText(
                            markdown = "```go\n" +
                                "// 心跳：只在前台跳\n" +
                                "func (w *Web) heartbeat(c *gin.Context) {\n" +
                                "    unread, _ := w.St.UnreadNoticeCount(ctx, v.ID)\n" +
                                "    c.JSON(200, gin.H{\"unread\": unread})\n" +
                                "}\n```"
                        )
                        MarkdownText(
                            markdown = "```sql\n" +
                                "SELECT count(*) FROM notices\n" +
                                "WHERE user_id = 1 AND read_at IS NULL;\n```"
                        )
                        MarkdownText(
                            markdown = "```bash\n" +
                                "# 分片上传大包\n" +
                                "split -b 10m app.apk part- && scp part-* host:/tmp/\n```"
                        )
                        MarkdownText(
                            markdown = "```json\n" +
                                "{\"ok\": true, \"online_now\": 3, \"unread\": 0}\n```"
                        )
                        MarkdownText(
                            markdown = "```python\n" +
                                "def draw(luck: int) -> str:\n" +
                                "    \"\"\"每日运势\"\"\"\n" +
                                "    return '大吉' if luck >= 90 else '吉'\n```"
                        )
                    }
                }
            }
        }
        val dir = File(System.getProperty("arisu.screenshot.dir") ?: "screenshots")
        dir.mkdirs()
        rule.onRoot().captureRoboImage(File(dir, "00-代码高亮探针.png").absolutePath)
    }
}
