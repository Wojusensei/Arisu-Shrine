package dev.arisu.shrine.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.PostCard
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.data.model.Post

/**
 * 折叠分页的帖子列表：默认三条，展开后每页五条。
 * 帖子多的社员要是全量平铺，主页会被拉得老长。两个主页共用。
 */
@Composable
internal fun CollapsiblePostList(posts: List<Post>, onOpenPost: (String) -> Unit) {
    // 展开与翻页驻留：切内容标签、进帖子再返回，不折叠回默认三条
    var expanded by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pageSize = 5
    val totalPages = (posts.size + pageSize - 1) / pageSize
    // 内容变短（切标签/删帖）时页码收敛：此前 drop 越界渲染出空白页、翻页器却显示「第 3 / 2 页」
    val safePage = page.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val visible = if (expanded) {
                posts.drop(safePage * pageSize).take(pageSize)
            } else {
                posts.take(3)
            }
            visible.forEach { post ->
                key(post.id) {
                    val open = remember(post.id, onOpenPost) { { onOpenPost(post.id) } }
                    PostCard(post = post, onClick = open)
                }
            }
        }

        // 控件行与帖子之间必须留出一口气：卡片的柔影会向下渗约 10dp，
        // 贴太近看起来就像粘在最后一张帖子上
        if (!expanded && posts.size > 3) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                SoftPill(
                    text = "展开查看更多（共 ${posts.size} 篇）",
                    color = MaterialTheme.colorScheme.primary,
                    background = NeuInset,
                    modifier = Modifier.clickable {
                        expanded = true
                        page = 0
                    },
                )
            }
        }

        if (expanded && totalPages > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PagerPill("上一页", enabled = safePage > 0) { page = safePage - 1 }
                Text(
                    text = "第 ${safePage + 1} / $totalPages 页",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                PagerPill("下一页", enabled = safePage < totalPages - 1) { page = safePage + 1 }
            }
        }

        if (expanded) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (totalPages > 1) 8.dp else 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "收起",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkMuted,
                    modifier = Modifier
                        .clickable {
                            expanded = false
                            page = 0
                        }
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun PagerPill(text: String, enabled: Boolean, onClick: () -> Unit) {
    SoftPill(
        text = text,
        color = if (enabled) MaterialTheme.colorScheme.primary else InkMuted.copy(alpha = 0.5f),
        background = NeuInset,
        modifier = Modifier.clickable(enabled = enabled) { onClick() },
    )
}
