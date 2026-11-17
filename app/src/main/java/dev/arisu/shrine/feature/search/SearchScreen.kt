package dev.arisu.shrine.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import dev.arisu.shrine.core.components.ArisuAvatar
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.CodeLabel
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.PostCard
import dev.arisu.shrine.core.components.RoleTag
import dev.arisu.shrine.core.components.SectionCard
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.ScrollTopOrb
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineListEnd
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.components.headerAtmosphere
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.components.smoothScrollToTop
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuInsetDeep
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.User
import dev.arisu.shrine.data.model.displayAvatarSeed
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/* ══════════════════════════════════════════════════════════════════
 * 搜索
 *
 * 支持两种模式：
 * - 普通模式：标题 / 昵称 / 编号做包含匹配
 * - 正则模式：把输入当正则解析，只匹配帖子标题（非法正则给出红色提示）
 *
 * 无论哪种模式，如果输入正好等于某个编号（arisu_000001 / User_000001，
 * 大小写不敏感），命中的对象会被提到结果最前面并打上「编号精确匹配」标签。
 * ══════════════════════════════════════════════════════════════════ */

/** 一次搜索的命中结果。 */
private data class SearchOutcome(
    val posts: List<Post> = emptyList(),
    val users: List<User> = emptyList(),
    /** 命中的帖子编号，用于「编号精确匹配」标签 */
    val exactPostId: String? = null,
    /** 命中的社员编号，用于「编号精确匹配」标签 */
    val exactUserId: String? = null,
    /** 正则模式下解析失败时的提示 */
    val regexError: Boolean = false,
    /** 其他失败（网络/服务器）的提示 */
    val error: String? = null,
    /** 是否已经执行过一次搜索（用于决定是否显示空状态） */
    val done: Boolean = false,
)

/** 判断输入是否为正则；正则模式下非法表达式返回 null。 */
private fun compileQuery(keyword: String, regex: Boolean): Pattern? {
    if (!regex) return null
    return try {
        Pattern.compile(keyword, Pattern.CASE_INSENSITIVE)
    } catch (_: PatternSyntaxException) {
        null
    }
}

/** 普通模式下的包含匹配，带大小写回退。 */
private fun containsIgnoreCase(source: String, keyword: String): Boolean =
    source.contains(keyword, ignoreCase = true)

/**
 * 执行一次搜索（服务器端）。编号精确命中的对象已由服务器排到最前。
 *
 * @param keyword 用户输入，调用前请先 trim
 * @param regex   是否启用正则
 */
private suspend fun runSearch(keyword: String, regex: Boolean): SearchOutcome {
    if (keyword.isEmpty()) return SearchOutcome()

    val pattern = compileQuery(keyword, regex)
    if (regex && pattern == null) {
        // 正则不合法：停下来并让 UI 报错，不要再退化成包含匹配
        return SearchOutcome(regexError = true, done = true)
    }

    val result = Shrine.search(keyword, regex)
    val exactPostId = keyword.lowercase().takeIf { it.startsWith("arisu_") }
    val exactUserId = keyword.uppercase().takeIf { it.startsWith("USER_") }

    return SearchOutcome(
        posts = result.posts,
        users = result.users,
        exactPostId = result.posts.firstOrNull { it.id.equals(keyword, ignoreCase = true) }?.id ?: exactPostId,
        exactUserId = result.users.firstOrNull { it.id.equals(keyword, ignoreCase = true) }?.id ?: exactUserId,
        done = true,
    )
}

/**
 * 搜索页：顶部返回 + 圆角搜索框 + 朱色「搜索」，下面依次是历史记录与结果。
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var regexMode by rememberSaveable { mutableStateOf(false) }
    // 历史记录存本地，从空白开始
    var history by remember { mutableStateOf(SearchHistoryStore.items) }
    var result by remember { mutableStateOf(SearchOutcome()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /** 点「搜索」/ 回车时调用：把关键词记进历史并跑一次搜索。 */
    fun submit() {
        val keyword = query.trim()
        if (keyword.isEmpty() || searching) return
        history = (listOf(keyword) + history.filterNot { it == keyword }).take(12)
        SearchHistoryStore.items = history
        searching = true
        scope.launch {
            result = try {
                runSearch(keyword, regexMode)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // 只有正则编译失败才是“表达式不合法”，网络/服务器问题如实提示
                if (e is dev.arisu.shrine.data.remote.ApiError &&
                    e.message?.contains("正则") == true) {
                    SearchOutcome(regexError = true, done = true)
                } else {
                    SearchOutcome(error = e.toApiMessage(), done = true)
                }
            }
            searching = false
        }
    }

    Box(Modifier.fillMaxSize()) {
    val searchListState = rememberLazyListState()
    Column(modifier.fillMaxSize().imePadding()) {
        SearchHeader(
            query = query,
            // 服务端关键词上限 100 字
            onQueryChange = { if (it.length <= 100) query = it },
            regexMode = regexMode,
            onToggleRegex = { regexMode = !regexMode },
            onBack = onBack,
            onSubmit = { submit() },
        )

        if (result.regexError || result.error != null) {
            // 网络错误那行文案承诺「点击重试」，行本身就得可点
            Row(
                Modifier
                    .fillMaxWidth()
                    .let { if (result.error != null) it.clickable { submit() } else it }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Clear,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (result.regexError) "正则表达式不合法。请社员注意，正则不是必须，如若掌握不熟悉可直接搜索关键词"
                    else result.error ?: "加载失败，点击重试",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (searching) {
            dev.arisu.shrine.core.components.ShrineLoadingState(note = "少女祈祷中")
        } else if (!result.done) {
            // 还没搜过：历史记录 + 一句能力说明
            HistorySection(
                history = history,
                onPick = { picked ->
                    // 复用 submit()：同一条搜索通道（写历史、防抖、错误分类），不再分叉
                    query = picked
                    submit()
                },
                onClear = { history = emptyList(); SearchHistoryStore.items = emptyList() }
            )
            HintLine()
        } else {
            LazyColumn(
                state = searchListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (result.posts.isEmpty() && result.users.isEmpty() && !result.regexError) {
                    item { ShrineEmptyState(note = "没有搜索结果") }
                }

                if (result.posts.isNotEmpty()) {
                    item {
                        SectionLabel(text = "帖子结果", trailing = "${result.posts.size} 条")
                    }
                    items(result.posts, key = { "post_${it.id}" }) { post ->
                        val open = remember(post.id, onOpenPost) { { onOpenPost(post.id) } }
                        Column {
                            if (post.id == result.exactPostId) {
                                ExactMatchBadge(Modifier.padding(start = 4.dp, bottom = 5.dp))
                            }
                            PostCard(post = post, onClick = open)
                        }
                    }
                }

                if (result.users.isNotEmpty()) {
                    item {
                        SectionLabel(text = "社员结果", trailing = "${result.users.size} 条")
                    }
                    item {
                        SectionCard {
                            result.users.forEachIndexed { index, user ->
                                UserResultRow(
                                    user = user,
                                    // 兜底分支存的是全大写 USER_，实际 id 是 User_——按忽略大小写比
                                    exact = result.exactUserId?.let { user.id.equals(it, ignoreCase = true) } == true,
                                    onClick = { onOpenUser(user.id) },
                                )
                                if (index != result.users.lastIndex) {
                                    HairlineDivider(startIndent = 66.dp)
                                }
                            }
                        }
                    }
                }

                if (result.posts.isNotEmpty()) {
                    item { ShrineListEnd(text = "已经到底了") }
                }
            }
        }
    }
    ScrollTopOrb(
        listState = searchListState,
        onClick = { scope.launch { searchListState.smoothScrollToTop() } },
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 28.dp),
    )
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 顶部搜索条
 * ══════════════════════════════════════════════════════════════════ */

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    regexMode: Boolean,
    onToggleRegex: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .headerAtmosphere()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        ArisuTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索帖子标题 / 编号 / 社员",
            leadingIcon = Icons.Rounded.Search,
            modifier = Modifier.weight(1f),
            trailing = {
                RegexToggle(on = regexMode, onToggle = onToggleRegex)
            },
        )
        Spacer(Modifier.width(2.dp))
        TextButton(onClick = onSubmit) {
            Text(
                text = "搜索",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 「正则」开关：开启后按正则解析输入；两种状态都是小胶囊，未开启时为中性凹槽。 */
@Composable
private fun RegexToggle(on: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (on) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.neuInset(shape = RoundedCornerShape(20.dp), color = NeuInsetDeep, highlight = false)
                }
            )
            .clickable { onToggle() }
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = "正则",
            style = MaterialTheme.typography.labelSmall,
            color = if (on) MaterialTheme.colorScheme.onPrimary else InkMuted,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 历史记录与提示
 * ══════════════════════════════════════════════════════════════════ */

@Composable
private fun HistorySection(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(
                text = "历史搜索",
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "清空",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onClear() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }

        if (history.isEmpty()) {
            Text(
                text = "还没有搜索记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        } else {
            SectionCard {
                history.forEachIndexed { index, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item) }
                            .padding(start = 16.dp, end = 14.dp, top = 13.dp, bottom = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (index != history.lastIndex) {
                        HairlineDivider(startIndent = 48.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HintLine() {
    Text(
        text = "支持正则表达式搜索标题，也可以直接输入编号",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

/* ══════════════════════════════════════════════════════════════════
 * 结果行
 * ══════════════════════════════════════════════════════════════════ */

/** 「编号精确匹配」标签。 */
@Composable
private fun ExactMatchBadge(modifier: Modifier = Modifier) {
    SoftPill(
        text = "编号精确匹配",
        color = MaterialTheme.colorScheme.primary,
        background = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    )
}

/** 用户结果行：头像 / 昵称 / 身份 / 社员编号。 */
@Composable
private fun UserResultRow(
    user: User,
    exact: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArisuAvatar(
            seed = user.displayAvatarSeed,
            name = user.nickname,
            size = 38.dp,
            role = user.role,
            ring = true,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.nickname,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(7.dp))
                RoleTag(user.role)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                CodeLabel(text = user.id)
            }
        }
        if (exact) {
            Spacer(Modifier.width(8.dp))
            ExactMatchBadge()
        }
    }
}

/** 搜索历史：进程级保留，退出搜索页不丢（与服务端无关的客户端偏好）。 */
object SearchHistoryStore {
    var items: List<String> = emptyList()
}
