package dev.arisu.shrine.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.components.FadeIn
import dev.arisu.shrine.core.components.ArisuAvatarRinged
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.BoardMemory
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.ToriiWatermark
import dev.arisu.shrine.core.components.PillTabs
import dev.arisu.shrine.core.components.ScrollTopOrb
import dev.arisu.shrine.core.components.PostCard
import dev.arisu.shrine.core.components.SectionCard
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.SettingNavRow
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineStaleBanner
import dev.arisu.shrine.core.components.ShrinePullRefreshBox
import dev.arisu.shrine.core.components.ShrineIcons
import dev.arisu.shrine.core.components.YinYangOrb
import androidx.compose.ui.text.style.TextAlign
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.components.ScrollTopBus
import dev.arisu.shrine.core.components.StatColumn
import dev.arisu.shrine.core.components.TopBarIcon
import dev.arisu.shrine.core.components.softCard
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.design.heatColor
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.Achievement
import dev.arisu.shrine.data.model.ProfileTab
import dev.arisu.shrine.data.model.User
import dev.arisu.shrine.data.model.avatarUrl
import dev.arisu.shrine.nav.FOLLOW_MODE_FOLLOWERS
import dev.arisu.shrine.nav.FOLLOW_MODE_FOLLOWING
import dev.arisu.shrine.nav.Routes

@Composable
fun MyRoomScreen(
    unreadCount: Int,
    onOpenInbox: () -> Unit,
    onEditProfile: () -> Unit,
    onSettings: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenFollowList: (String) -> Unit,
    onOpenCollections: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenMyProfileTab: (Int) -> Unit = {},
    modifier: Modifier = Modifier,

    initialTabIndex: Int = 0,
) {

    val cached = remember { roomMemory.get("room", Shrine.me.value?.id) }
    var me by remember { mutableStateOf(cached?.me) }
    var tiles by remember { mutableStateOf(cached?.tiles ?: emptyList()) }
    var today by remember { mutableStateOf(cached?.today ?: Triple(0, 0, 0)) }
    var achievements by remember {
        mutableStateOf(cached?.achievements ?: Shrine.achievementCatalog.map { it.second.copy(unlocked = false) })
    }
    var counts by remember { mutableStateOf(cached?.counts ?: Quad(0, 0, 0, 0)) }

    var loading by remember { mutableStateOf(cached == null) }
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable {
        mutableStateOf(ProfileTab.entries[initialTabIndex.coerceIn(0, ProfileTab.entries.size - 1)])
    }
    var showAchievements by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            val room = Shrine.myRoom()
            me = room.me
            tiles = room.tiles
            today = Triple(room.todayMinutes, room.todayPosts, room.todayComments)
            achievements = room.achievements

            var trackedN = 0
            var likedN = 0
            var favoritedN = 0
            try { trackedN = Shrine.postsByAuthor(room.me.id, "tracked").size } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
            try { likedN = Shrine.postsByAuthor(room.me.id, "liked").size } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
            try { favoritedN = Shrine.postsByAuthor(room.me.id, "favorited").size } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
            counts = Quad(room.me.postCount, trackedN, likedN, favoritedN)
            failed = false
            roomMemory.put("room", room.me.id, RoomSnapshot(room.me, tiles, today, achievements, counts))
        } catch (e: Exception) {

            if (me == null) failed = true
        }
        loading = false
    }

    androidx.compose.runtime.LaunchedEffect(reloadKey) { load() }

    if (loading) {
        dev.arisu.shrine.core.components.ShrineLoadingState()
        return
    }
    if (failed || me == null) {
        dev.arisu.shrine.core.components.ShrineEmptyState(note = "加载失败，点击重试", onClick = { reloadKey++ })
        return
    }
    val meState = me ?: return

    FadeIn(modifier = modifier) {
    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "我的客房",
            large = true,
            subtitle = meState.id,
            actions = {
                TopBarIcon(
                    icon = Icons.Rounded.Mail,
                    contentDescription = "社务箱",
                    onClick = onOpenInbox,
                    badgeCount = unreadCount,
                )
                TopBarIcon(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "设置",
                    onClick = onSettings,
                )
            },
        )

        ShrineStaleBanner()

        ShrinePullRefreshBox(
            onRefresh = { load() },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
        Box(Modifier.fillMaxSize()) {
        val meScroll = rememberScrollState()

        LaunchedEffect(Unit) {
            ScrollTopBus.events.collect { route ->
                if (route == Routes.ME) scope.launch { meScroll.animateScrollTo(0) }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(meScroll),
        ) {
            CoverSection(me = meState, onEditProfile = onEditProfile)

            StatsCard(me = meState, onOpenFollowList = onOpenFollowList)

            if (meState.customMarkdown.isNotBlank() || meState.customCss.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                SectionLabel(text = "自定义区域", modifier = Modifier.padding(horizontal = 16.dp))
                dev.arisu.shrine.core.components.CustomArea(
                    markdown = meState.customMarkdown,
                    safeCss = meState.customCss,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionLabel(text = "参拜热度", modifier = Modifier.padding(horizontal = 16.dp))
            HeatmapCard(tiles = tiles, todayMinutes = today.first, todayPosts = today.second, todayComments = today.third)

            Spacer(Modifier.height(12.dp))

            PillTabs(
                items = ProfileTab.entries,
                selected = tab,
                onSelect = { tab = it },
                label = { it.label },
            )
            PostsSection(meId = meState.id, tab = tab, onOpenPost = onOpenPost, reloadKey = reloadKey)

            Spacer(Modifier.height(12.dp))

            EntryGroups(
                me = meState,
                counts = counts,
                onOpenCollections = onOpenCollections,
                onOpenFollowList = onOpenFollowList,
                onOpenPostsTab = onOpenMyProfileTab,
                onShowAchievements = { showAchievements = true },
                onSettings = onSettings,
                onNote = { note = it },
                achievements = achievements,
                onToggleActiveAvatar = { on ->
                    scope.launch {
                        try {
                            Shrine.updateMe(dev.arisu.shrine.data.remote.ProfilePatchReq(activeAvatarEnabled = on))
                            load()
                        } catch (e: Exception) {
                            note = e.toApiMessage()
                        }
                    }
                },
                onEditProfile = onEditProfile,
            )

            Spacer(Modifier.height(32.dp))
        }
        ScrollTopOrb(
            scrollState = meScroll,
            onClick = { scope.launch { meScroll.animateScrollTo(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 110.dp),
        )
        }
    }

    if (showAchievements) {
        AchievementsDialog(
            achievements = achievements,
            onDismiss = { showAchievements = false },
        )
    }

    note?.let { text ->
        AlertDialog(
            onDismissRequest = { note = null },
            title = { Text("说明") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { note = null }) { Text("知道了") }
            },
        )
        }
    }
    }
}

private val CoverHeight = 284.dp

private val CoverMargin = 18.dp

private val CoverShape = RoundedCornerShape(28.dp)

private val CoverContentBottom = 38.dp

@Composable
internal fun CoverSection(me: User, onEditProfile: () -> Unit) {
    NeuSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CoverMargin, top = CoverMargin, end = CoverMargin)
            .height(CoverHeight),
        shape = CoverShape,
        depth = 5.dp,
        blur = 10.dp,
    ) {
        ToriiWatermark(CoverShape)
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(bottom = CoverContentBottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.padding(horizontal = 46.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ArisuAvatarRinged(
                    seed = 0,
                    name = me.nickname,
                    role = me.role,
                    size = 78.dp,
                    url = me.avatarUrl,
                )
                Spacer(Modifier.height(8.dp))
                ProfileIdentity(
                    nickname = me.nickname,
                    role = me.role,
                    status = listOfNotNull(me.statusEmoji, me.statusText).joinToString(" "),
                )
            }

            if (me.signature.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = me.signature,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }

        if (true) {
            SoftPill(
                text = "编辑资料",
                color = Vermilion,
                background = NeuInset,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 30.dp)
                    .clickable { onEditProfile() },
            )
        }
    }
}

@Composable
private fun StatsCard(me: User, onOpenFollowList: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            StatColumn(
                value = me.followingCount.toString(),
                label = "关注数",
                modifier = Modifier.weight(1f),
                onClick = { onOpenFollowList(FOLLOW_MODE_FOLLOWING) },
            )
            StatColumn(
                value = me.followerCount.toString(),
                label = "被关注数",
                modifier = Modifier.weight(1f),
                onClick = { onOpenFollowList(FOLLOW_MODE_FOLLOWERS) },
            )
            StatColumn(
                value = if (me.showFields["online_hours"] == false) "已隐藏" else me.onlineHours.toString(),
                label = "总在线小时数",
                modifier = Modifier.weight(1f),
            )
    }
}

@Composable
private fun HeatmapCard(
    tiles: List<dev.arisu.shrine.data.model.TileDay>,
    todayMinutes: Int,
    todayPosts: Int,
    todayComments: Int,
) {
    val columns = tiles.chunked(7)
    val scroll = rememberScrollState()

    LaunchedEffect(scroll, columns.size) {
        snapshotFlow { scroll.maxValue }.collect { max ->
            if (max > 0) scroll.scrollTo(max)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "今天在神社停留 $todayMinutes 分钟 · 发帖 $todayPosts · 评论 $todayComments",
                style = MaterialTheme.typography.labelSmall,
                color = InkMuted,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            TileLegend()
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth()) {
            WeekdayLabels()
            Spacer(Modifier.width(5.dp))
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                columns.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day -> TileSquare(day.level) }
                        repeat(7 - week.size) { TileSquare(0) }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "今天",
                style = MaterialTheme.typography.labelSmall,
                color = Vermilion,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun WeekdayLabels() {
    val labels = listOf("周一", "", "周三", "", "周五", "", "")
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEach { label ->
            Box(
                Modifier
                    .width(22.dp)
                    .height(10.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                        ),
                        color = InkMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TileSquare(level: Int) {
    Box(
        Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(tileColor(level))
    )
}

private fun tileColor(level: Int): Color = heatColor(level)

@Composable
private fun TileLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "少",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
        )
        Spacer(Modifier.width(4.dp))
        for (level in 0..4) {
            TileSquare(level)
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = "多",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
        )
    }
}

@Composable
private fun PostsSection(meId: String, tab: ProfileTab, onOpenPost: (String) -> Unit, reloadKey: Int) {

    val cachedPosts = remember(tab) { postsMemory.get("${meId}:${tab.name}", meId) }
    var posts by remember(tab) { mutableStateOf(cachedPosts ?: emptyList()) }

    var loading by remember(tab) { mutableStateOf(cachedPosts == null) }
    var failed by remember(tab) { mutableStateOf(false) }
    var retryKey by remember(tab) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val wireTab = when (tab) {
        ProfileTab.POSTED -> "posts"
        ProfileTab.TRACKED -> "tracked"
        ProfileTab.LIKED -> "liked"
        ProfileTab.FAVORITED -> "favorited"
    }
    LaunchedEffect(wireTab, reloadKey, retryKey) {
        if (posts.isEmpty()) loading = true
        posts = try {
            Shrine.postsByAuthor(meId, wireTab).also {
                postsMemory.put("${meId}:${tab.name}", meId, it)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e

            if (cachedPosts == null) failed = true
            posts
        }
        loading = false
    }

    if (loading) {
        dev.arisu.shrine.core.components.ShrineLoadingState(note = "少女祈祷中")
        return
    }

    if (posts.isEmpty() && failed) {
        ShrineEmptyState(note = "加载失败，点击重试", onClick = { retryKey++ })
        return
    }

    if (posts.isEmpty()) {
        val hint = when (tab) {
            ProfileTab.POSTED -> "还没有发过帖子"
            ProfileTab.TRACKED -> "还没有追踪的帖子"
            ProfileTab.LIKED -> "还没有喜欢的帖子"
            ProfileTab.FAVORITED -> "收藏夹还是空的"
        }
        ShrineEmptyState(note = hint)
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {

        CollapsiblePostList(posts = posts, onOpenPost = onOpenPost)
    }
}

private data class RoomSnapshot(
    val me: User,
    val tiles: List<dev.arisu.shrine.data.model.TileDay>,
    val today: Triple<Int, Int, Int>,
    val achievements: List<Achievement>,
    val counts: Quad,
)
private val roomMemory = BoardMemory<String, RoomSnapshot>()
private val postsMemory = BoardMemory<String, List<Post>>()

private data class Quad(val a: Int, val b: Int, val c: Int, val d: Int)

@Composable
private fun EntryGroups(
    me: User,
    counts: Quad,
    onOpenCollections: () -> Unit,
    onOpenFollowList: (String) -> Unit,
    onOpenPostsTab: (Int) -> Unit,
    onShowAchievements: () -> Unit,
    onSettings: () -> Unit,
    onNote: (String) -> Unit,
    achievements: List<Achievement>,
    onToggleActiveAvatar: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
) {
    val unlocked = achievements.count { it.unlocked }
    val total = achievements.size
    val customEdited = me.customMarkdown.isNotBlank() || me.customCss.isNotBlank()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {

        SectionLabel(text = "资料")
        SectionCard {
            SettingNavRow(
                title = "固定头像以及活跃头像",
                subtitle = "关闭后所有地方都显示固定头像",
                value = if (me.activeAvatarEnabled) "已启用" else "已关闭",
                trailing = {
                    Switch(
                        checked = me.activeAvatarEnabled,
                        onCheckedChange = { on -> onToggleActiveAvatar(on) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { onToggleActiveAvatar(!me.activeAvatarEnabled) },
            )
            SettingNavRow(
                title = "个人昵称",
                value = me.nickname,
                onClick = { onNote("昵称可在「编辑资料」中修改。") },
            )
            SettingNavRow(
                title = "个性签名",
                value = truncate(me.signature, 14),
                onClick = { onNote("个性签名可在「编辑资料」中修改。") },
            )
            SettingNavRow(
                title = "个人状态",
                value = listOfNotNull(me.statusEmoji, me.statusText).joinToString(" "),
                onClick = { onNote("个人状态可在「编辑资料」中修改。") },
            )
            SettingNavRow(
                title = "个人邮箱",
                subtitle = "仅本人可见，不公开展示",
                value = me.email,
                onClick = { onNote("邮箱仅本人可见，不会公开展示。") },
            )
            SettingNavRow(
                title = "个人博客网址",
                value = if (me.blogUrl.isBlank()) "未填写" else me.blogUrl,
                onClick = { onNote("博客网址可在「编辑资料」中修改。") },
                showDivider = false,
            )
        }

        Spacer(Modifier.height(4.dp))

        SectionLabel(text = "我的内容")
        SectionCard {
            SettingNavRow(
                title = "个人发布的帖子",
                value = "${counts.a} 篇",
                onClick = { onOpenPostsTab(0) },
            )
            SettingNavRow(
                title = "个人追踪的帖子",
                value = "${counts.b} 篇",
                onClick = { onOpenPostsTab(1) },
            )
            SettingNavRow(
                title = "个人喜欢的帖子",
                value = "${counts.c} 篇",
                onClick = { onOpenPostsTab(2) },
            )
            SettingNavRow(
                title = "个人收藏的帖子",
                value = "${counts.d} 篇",
                onClick = { onOpenPostsTab(3) },
            )
            SettingNavRow(
                title = "收藏夹管理",
                onClick = onOpenCollections,
                showDivider = false,
            )
        }

        Spacer(Modifier.height(4.dp))

        SectionLabel(text = "展示")
        SectionCard {
            SettingNavRow(
                title = "自定义区域",
                value = if (customEdited) "已编辑" else "未编辑",

                onClick = onEditProfile,
            )
            SettingNavRow(
                title = "个人御守",
                subtitle = "点击查看全部御守",
                value = "已解锁 $unlocked / $total",
                onClick = onShowAchievements,
            )
            SettingNavRow(
                title = "参拜热度",
                value = "查看",
                onClick = { onNote("参拜热度记录近一年的活跃情况，最后一格是今天。") },
            )
            SettingNavRow(
                title = "个人关注的社员数",
                value = me.followingCount.toString(),
                onClick = { onOpenFollowList(FOLLOW_MODE_FOLLOWING) },
            )
            SettingNavRow(
                title = "个人被关注数",
                value = me.followerCount.toString(),
                onClick = { onNote("被关注数由其他社员的关注行为累积。") },
                showDivider = false,
            )
        }

        Spacer(Modifier.height(4.dp))

        SectionLabel(text = "其他")
        SectionCard {
            SettingNavRow(
                title = "设置",
                onClick = onSettings,
                showDivider = false,
            )
        }
    }
}

@Composable
private fun AchievementsDialog(
    achievements: List<Achievement>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,

        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text("个人御守（${achievements.count { it.unlocked }} / ${achievements.size}）")
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                achievements.chunked(2).forEach { pair ->

                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pair.forEach { item -> AchievementCell(item, Modifier.weight(1f).fillMaxHeight()) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun AchievementIcon(name: String, unlocked: Boolean) {
    val tint = if (unlocked) dev.arisu.shrine.core.design.Vermilion
    else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = when (name) {
        "神社出仕" -> ShrineIcons.Torii
        "第一帖" -> Icons.Rounded.Description
        "捧帖" -> Icons.Rounded.Celebration
        "挂绘马" -> ShrineIcons.Ema
        "上上签" -> ShrineIcons.Omikuji
        "参拜者" -> ShrineIcons.Incense
        "夜行性" -> Icons.Rounded.NightsStay
        else -> null
    }
    if (name == "社务常客") {

        YinYangOrb(
            size = 22.dp,
            rotating = false,
            primary = if (unlocked) dev.arisu.shrine.core.design.Vermilion
            else MaterialTheme.colorScheme.onSurfaceVariant,
            base = NeuInset,
        )
    } else if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun AchievementCell(achievement: Achievement, modifier: Modifier = Modifier) {

    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NeuInset)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        AchievementIcon(achievement.name, achievement.unlocked)
        Spacer(Modifier.height(14.dp))
        Text(
            text = achievement.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (achievement.unlocked) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = achievement.description,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                lineHeight = 12.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun truncate(text: String, max: Int): String =
    if (text.length <= max) text else text.take(max) + "…"
