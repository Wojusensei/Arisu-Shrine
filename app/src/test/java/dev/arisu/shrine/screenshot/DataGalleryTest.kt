package dev.arisu.shrine.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import dev.arisu.shrine.core.components.PageBackground
import dev.arisu.shrine.core.components.PostCard
import dev.arisu.shrine.data.model.Board
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.Role
import dev.arisu.shrine.data.model.User
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.feature.profile.MyRoomScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class DataGalleryTest : ScreenshotBase() {

    private val sampleUser = User(
        id = "User_000001",
        nickname = "窝居",
        role = Role.KANNUSHI,
        signature = "这是一条五十个字的个性签名：思源宋体之下，应是完整可读的多行展示，而不是一行省略号就草草收场。",
        statusEmoji = "🌸",
        statusText = "在线",
        joinedAt = "2026-01-01",
    )

    private val samplePost = Post(
        id = "arisu_000004",
        board = Board.MAIN,
        title = "这是帖子的标题。",
        body = "这是文本的内容。",
        intro = "这是介绍语，列表封面应优先展示它。",
        authorId = "User_000001",
        authorName = "窝居",
        authorRole = Role.KANNUSHI,
        createdAt = "今天 18:00",
        commentCount = 2,
        upCount = 1,
    )

    @Test
    fun postCards() {
        shot("组件-帖子卡片") {
            PageBackground {
                Column(Modifier.fillMaxSize()) {
                    PostCard(post = samplePost, onClick = {})
                    PostCard(post = samplePost.copy(intro = ""), onClick = {})
                }
            }
        }
    }

    @Test
    fun myRoomCover() {

        shot("组件-客房封面") {
            PageBackground {
                dev.arisu.shrine.feature.profile.CoverSection(me = sampleUser, onEditProfile = {})
            }
        }
    }
}
