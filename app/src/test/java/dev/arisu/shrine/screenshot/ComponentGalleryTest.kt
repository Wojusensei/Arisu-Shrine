package dev.arisu.shrine.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.EmaCard
import dev.arisu.shrine.core.components.PostCard
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineLoadingState
import dev.arisu.shrine.core.components.YinYangOrb
import dev.arisu.shrine.data.model.Board
import dev.arisu.shrine.data.model.Ema
import dev.arisu.shrine.data.model.EmaState
import dev.arisu.shrine.data.model.EmaTag
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.PostSort
import dev.arisu.shrine.data.model.Role
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ComponentGalleryTest : ScreenshotBase() {

    private fun samplePost(pinned: Boolean = false, essence: Boolean = false) = Post(
        id = "arisu_000001",
        board = Board.MAIN,
        title = "这里是一张帖子卡片的标题，最多展示两行的宽度",
        body = "",
        authorId = "User_000001",
        authorName = "示例作者",
        authorRole = Role.MEMBER,
        createdAt = "今天 09:12",
        commentCount = 12,
        upCount = 34,
        pinned = pinned,
        essence = essence,
    )

    private fun sampleEma() = Ema(
        id = 1,
        title = "绘马卡片：标题最多两行，右侧带状态胶囊",
        body = "",
        authorId = "User_000001",
        authorName = "示例作者",
        authorRole = Role.GUUJI,
        tags = listOf(EmaTag.ENHANCE, EmaTag.BUG),
        state = EmaState.IN_PROGRESS,
        createdAt = "今天 10:00",
        replyCount = 3,
    )

    @Test
    fun cards() = shot("组件-卡片") {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            PostCard(post = samplePost(pinned = true), onClick = {})
            Spacer(Modifier.height(10.dp))
            PostCard(post = samplePost(essence = true), onClick = {})
            Spacer(Modifier.height(10.dp))
            EmaCard(ema = sampleEma(), onClick = {})
        }
    }

    @Test
    fun states() = shot("组件-状态") {
        Column(Modifier.fillMaxWidth()) {
            ShrineLoadingState(note = "少女祈祷中")
            ShrineEmptyState(note = "没有帖子")
        }
    }

    @Test
    fun orb() = shot("组件-阴阳玉") {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 40.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            YinYangOrb(size = 72.dp)
            Spacer(Modifier.height(18.dp))
            YinYangOrb(size = 34.dp)
            Spacer(Modifier.height(18.dp))
            YinYangOrb(size = 14.dp)
            Spacer(Modifier.height(18.dp))
            MaterialTheme.typography.labelSmall.let {
                androidx.compose.material3.Text("匀速旋转 · 无回弹", style = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
