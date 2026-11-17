package dev.arisu.shrine.feature.profile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.performScrollTo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.arisu.shrine.core.design.ArisuShrineTheme
import dev.arisu.shrine.data.model.Board
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.Role
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 主页帖子折叠分页的行为断言：默认三条、展开后每页五条、翻页与收起。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class CollapsiblePostListTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun posts(n: Int) = (1..n).map { i ->
        Post(
            id = "arisu_%06d".format(i),
            board = Board.MAIN,
            title = "标题$i",
            body = "正文",
            authorId = "1",
            authorName = "作者",
            authorRole = Role.MEMBER,
            createdAt = "昨天",
        )
    }

    private fun setContent(n: Int) {
        rule.setContent {
            ArisuShrineTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // 帖子卡统一高度后，展开的列表高过测试窗口——
                    // 与真实页面一致套 verticalScroll，点击前先滚动到控件
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    ) {
                        CollapsiblePostList(posts = posts(n), onOpenPost = {})
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun showsThreeByDefaultThenPagedFiveWhenExpanded() {
        setContent(13)

        assertEquals(3, rule.onAllNodesWithText("标题", substring = true).fetchSemanticsNodes().size)
        rule.onNodeWithText("展开查看更多（共 13 篇）").assertExists().performClick()
        rule.waitForIdle()
        // 展开后第一页 5 条
        assertEquals(5, rule.onAllNodesWithText("标题", substring = true).fetchSemanticsNodes().size)
        rule.onNodeWithText("第 1 / 3 页").assertExists()

        // 卡片加高后页脚滑出窗口，点击会落空：先滚到位再点（T63 同款处理）
        rule.onNodeWithText("下一页").performScrollTo()
        rule.waitForIdle()
        rule.onNodeWithText("下一页").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("第 2 / 3 页").performScrollTo()
        rule.waitForIdle()
        rule.onNodeWithText("第 2 / 3 页").assertExists()

        rule.onNodeWithText("收起").performScrollTo()
        rule.waitForIdle()
        rule.onNodeWithText("收起").performClick()
        rule.waitForIdle()
        assertEquals(3, rule.onAllNodesWithText("标题", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun shortListShowsNoExpand() {
        setContent(3)
        rule.onNodeWithText("展开查看更多（共 3 篇）").assertDoesNotExist()
    }
}
