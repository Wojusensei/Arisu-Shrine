package dev.arisu.shrine.core.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.arisu.shrine.core.design.ArisuShrineTheme
import dev.arisu.shrine.data.model.Board
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.Role
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class PostCardCoverTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun post(cover: String?, saves: Int = 2, avatar: String? = "http://example.com/a.jpg") = Post(
        id = "arisu_000042",
        board = Board.MAIN,
        title = "封面与收藏数",
        body = "正文",
        authorId = "1",
        authorName = "测试作者",
        authorRole = Role.MEMBER,
        createdAt = "昨天",
        coverUrl = cover,
        saveCount = saves,

        authorAvatar = avatar,
    )

    private fun setContent(p: Post) {
        rule.setContent {
            ArisuShrineTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxWidth()) {
                        PostCard(post = p, onClick = {})
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun coverTakesAvatarSlotAndSaveCountShows() {
        setContent(post(cover = "http://example.com/cover.jpg"))

        rule.onNodeWithContentDescription("帖子封面").assertExists()
        rule.onNodeWithContentDescription("测试作者").assertDoesNotExist()

        rule.onNodeWithText("2").assertExists()

        rule.onNodeWithText("arisu_000042").assertExists()
        rule.onNodeWithText("社员").assertExists()
    }

    @Test
    fun noCoverFallsBackToAuthorAvatar() {

        setContent(post(cover = null, saves = 7))

        rule.onNodeWithContentDescription("帖子封面").assertDoesNotExist()
        rule.onNodeWithContentDescription("测试作者").assertExists()
        rule.onNodeWithText("7").assertExists()
    }

    @Test
    fun neitherCoverNorAvatarShowsPlaceholder() {
        setContent(post(cover = null, saves = 3, avatar = null))

        rule.onNodeWithContentDescription("帖子封面").assertDoesNotExist()
        rule.onNodeWithContentDescription("测试作者").assertDoesNotExist()
        rule.onNodeWithText("封面与收藏数").assertExists()
    }
}
