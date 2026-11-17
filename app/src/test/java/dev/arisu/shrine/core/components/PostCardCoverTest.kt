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

/**
 * 帖子卡片封面的位置契约（v0.0.0.9 起封面统一在左；v0.0.0.11 起封面为通高柱）：
 * 有封面 → 封面柱占左位、作者头像退场、编号压在封面下缘、职务徽标随昵称展示；
 * 无封面 → 回落作者头像。
 */
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
        // 头像 URL 非空才会渲染带昵称描述的图（剪影占位没有描述）
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

        // 封面在卡片上，作者头像（以昵称作 contentDescription）退场
        rule.onNodeWithContentDescription("帖子封面").assertExists()
        rule.onNodeWithContentDescription("测试作者").assertDoesNotExist()
        // 收藏计数与顶/评论并列展示
        rule.onNodeWithText("2").assertExists()
        // 帖子编号压在封面下缘，职务徽标回到昵称之后
        rule.onNodeWithText("arisu_000042").assertExists()
        rule.onNodeWithText("社员").assertExists()
    }

    @Test
    fun noCoverFallsBackToAuthorAvatar() {
        // 收藏数取 7 等独特值：顶/评论默认 0，避免文本断言撞车
        setContent(post(cover = null, saves = 7))

        rule.onNodeWithContentDescription("帖子封面").assertDoesNotExist()
        rule.onNodeWithContentDescription("测试作者").assertExists()
        rule.onNodeWithText("7").assertExists()
    }

    @Test
    fun neitherCoverNorAvatarShowsPlaceholder() {
        setContent(post(cover = null, saves = 3, avatar = null))

        // 既无封面也无头像：人像剪影占位，左位仍是 84dp 方形封面位
        rule.onNodeWithContentDescription("帖子封面").assertDoesNotExist()
        rule.onNodeWithContentDescription("测试作者").assertDoesNotExist()
        rule.onNodeWithText("封面与收藏数").assertExists()
    }
}
