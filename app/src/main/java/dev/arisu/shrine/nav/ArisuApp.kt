package dev.arisu.shrine.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.arisu.shrine.core.components.ArisuBottomBar
import dev.arisu.shrine.core.components.PageBackground
import dev.arisu.shrine.core.components.ScrollTopBus
import dev.arisu.shrine.core.components.YinYangOrb
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.InkTitle
import dev.arisu.shrine.data.repo.SessionState
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.feature.auth.ForgotPasswordScreen
import dev.arisu.shrine.feature.auth.LoginScreen
import dev.arisu.shrine.feature.auth.RegisterScreen
import dev.arisu.shrine.feature.chat.ChatBoardScreen
import dev.arisu.shrine.feature.chat.NewPostScreen
import dev.arisu.shrine.feature.chat.PostDetailScreen
import dev.arisu.shrine.feature.inbox.InboxScreen
import dev.arisu.shrine.feature.inbox.NoticeDetailHolder
import dev.arisu.shrine.feature.inbox.NoticeDetailScreen
import dev.arisu.shrine.feature.inbox.ReportsScreen
import dev.arisu.shrine.feature.play.PlayScreen
import dev.arisu.shrine.feature.profile.AccountManageScreen
import dev.arisu.shrine.feature.profile.BlockedUsersScreen
import dev.arisu.shrine.feature.profile.CollectionsScreen
import dev.arisu.shrine.feature.profile.EditProfileScreen
import dev.arisu.shrine.feature.profile.FollowListScreen
import dev.arisu.shrine.data.model.Board
import dev.arisu.shrine.feature.profile.DraftsScreen
import dev.arisu.shrine.feature.profile.HistoryScreen
import dev.arisu.shrine.feature.profile.MyRoomScreen
import dev.arisu.shrine.feature.profile.SessionsScreen
import dev.arisu.shrine.feature.profile.SettingsScreen
import dev.arisu.shrine.feature.profile.UserProfileScreen
import dev.arisu.shrine.feature.search.SearchScreen
import dev.arisu.shrine.feature.shrine.ChangelogDetailScreen
import dev.arisu.shrine.feature.shrine.EmaDetailScreen
import dev.arisu.shrine.feature.shrine.NewEmaScreen
import dev.arisu.shrine.feature.shrine.ShrineBoardScreen

@Composable
fun ArisuApp() {
    val session by Shrine.sessionState.collectAsStateWithLifecycle()

    when (session) {
        SessionState.Booting -> SplashScreen()
        else -> key(session is SessionState.LoggedIn) {
            ShrineApp(loggedIn = session is SessionState.LoggedIn)
        }
    }
}

/** 启动页：阴阳玉 + 站名，会话校验完成后自动进入。 */
@Composable
private fun SplashScreen() {
    PageBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                YinYangOrb(size = 72.dp)
                Spacer(Modifier.height(26.dp))
                Text(
                    text = "有栖神社",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = InkTitle,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "建造于人间之里外的飘渺存在",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ShrineApp(loggedIn: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 选中态与可见性同样做路由归一化：祝部路由带 query（shrine?tab=xxx），
    // 精确匹配永远失败 → 图标不变红。剥掉 query 后再匹配主 tab。
    val normalizedRoute = currentRoute?.substringBefore('?')
    val currentTab = ArisuTab.entries.firstOrNull { it.route == normalizedRoute }

    // 前后台直接驱动心跳：回前台立即补跳一拍（未读数随心跳回来，不再单独发请求），
    // 进后台暂停心跳——此前后台也 60 秒常跳，费电且把在线时长虚撑起来
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> Shrine.onAppForeground()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> Shrine.onAppBackground()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 页面底（暖白渐变 + 朱色氛围光）铺在最外层，
    // 悬浮的毛玻璃底栏才能「透」出下面的颜色。
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val backgroundClickSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    PageBackground(
        modifier = androidx.compose.ui.Modifier.clickable(
            interactionSource = backgroundClickSource,
            indication = null,
        ) { focusManager.clearFocus() },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (loggedIn && normalizedRoute != null && ArisuTab.entries.any { it.route == normalizedRoute }) {
                    ArisuBottomBar(
                        current = currentTab,
                        onSelect = { tab ->
                            // 再点当前板块：不重新导航（否则 pop+push 会重播转场、重载页面），只滚回顶部
                            if (tab == currentTab) ScrollTopBus.request(tab.route)
                            else navController.switchTab(tab)
                        },
                    )
                }
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    // 把底栏高度声明为「已消费」，之后任何 .imePadding()
                    // 都按（键盘高度 - 底栏高度）精确补垫——输入条正好落在键盘上沿
                    .consumeWindowInsets(androidx.compose.foundation.layout.PaddingValues(bottom = innerPadding.calculateBottomPadding()))
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                ArisuNavHost(navController = navController, currentRoute = currentRoute, loggedIn = loggedIn)
            }
        }
    }
}

@Composable
private fun ArisuNavHost(navController: NavHostController, currentRoute: String?, loggedIn: Boolean) {
    // 主 tab 路由模式（含祝部带参路由），用于判定转场方向
    val tabPatterns = remember {
        ArisuTab.entries.map { it.route } + (Routes.SHRINE + "?tab={tab}")
    }
    fun tabIndex(route: String?) = ArisuTab.entries.indexOfFirst { it.route == route }
        .let { if (route == Routes.SHRINE + "?tab={tab}") 1 else it }

    NavHost(
        navController = navController,
        startDestination = if (loggedIn) Routes.CHAT else Routes.LOGIN,
        modifier = Modifier.fillMaxSize(),
        // 主 tab 之间：按板块顺序做方向性滑入（与 WebUI 一致）；
        // 二级页面：从右侧推进来，返回时反向退出
        enterTransition = {
            val from = initialState.destination.route
            val to = targetState.destination.route
            if (from in tabPatterns && to in tabPatterns) {
                val forward = tabIndex(to) >= tabIndex(from)
                slideInHorizontally(tween(260)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(260))
            } else {
                slideInHorizontally(tween(280)) { it / 5 } + fadeIn(tween(280))
            }
        },
        exitTransition = {
            val from = initialState.destination.route
            val to = targetState.destination.route
            if (from in tabPatterns && to in tabPatterns) {
                val forward = tabIndex(to) >= tabIndex(from)
                slideOutHorizontally(tween(240)) { if (forward) -it / 6 else it / 6 } + fadeOut(tween(200))
            } else {
                slideOutHorizontally(tween(280)) { -it / 8 } + fadeOut(tween(200))
            }
        },
        popEnterTransition = {
            slideInHorizontally(tween(280)) { -it / 8 } + fadeIn(tween(280))
        },
        popExitTransition = {
            slideOutHorizontally(tween(280)) { it / 5 } + fadeOut(tween(200))
        },
    ) {
        /* ── 注册 / 登录 / 找回密码 ──────────────────────── */
        composable(Routes.LOGIN) {
            LoginScreen(
                onLogin = { /* 会话状态翻转，导航图整体重建到 CHAT */ },
                onGoRegister = { navController.navigate(Routes.REGISTER) { launchSingleTop = true } },
                onForgot = { navController.navigate(Routes.FORGOT) { launchSingleTop = true } },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegister = { /* 同上 */ },
                onGoLogin = { navController.popBackStack() },
            )
        }
        composable(Routes.FORGOT) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }

        /* ── 有栖茶话 ───────────────────────────────────── */
        composable(
            route = Routes.CHAT,
        ) {
            val unread by Shrine.unread.collectAsStateWithLifecycle()
            ChatBoardScreen(
                unreadCount = unread,
                onOpenPost = { navController.navigate(Routes.postDetail(it)) { launchSingleTop = true } },
                onOpenSearch = { navController.navigate(Routes.SEARCH) { launchSingleTop = true } },
                onOpenInbox = { navController.navigate(Routes.INBOX) { launchSingleTop = true } },
                onNewPost = { board -> navController.navigate("${Routes.NEW_POST}?board=${board.name}") { launchSingleTop = true } },
            )
        }
        composable(
            route = "${Routes.NEW_POST}?board={board}",
            arguments = listOf(
                navArgument("board") {
                    type = NavType.StringType
                    defaultValue = dev.arisu.shrine.data.model.Board.MAIN.name
                }
            ),
        ) { entry ->
            val boardName = entry.arguments?.getString("board")
            val board = dev.arisu.shrine.data.model.Board.entries.firstOrNull { it.name == boardName }
                ?: dev.arisu.shrine.data.model.Board.MAIN
            NewPostScreen(
                initialBoard = board,
                onBack = { navController.popBackStack() },
                onPublished = { postId ->
                    navController.popBackStack()
                    navController.navigate(Routes.postDetail(postId)) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = Routes.POST_DETAIL,
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
        ) { entry ->
            PostDetailScreen(
                postId = entry.arguments?.getString("postId").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenUser = { navController.navigate(Routes.userProfile(it)) { launchSingleTop = true } },
                onEditPost = { navController.navigate(Routes.editPost(it)) { launchSingleTop = true } },
            )
        }
        /* 改帖：与撰写同一套整页面板（弹窗里写不下长文，封面也没处取景）。
         * 资料的取法与绘马那条一致：有本地缓存就先摆上，没有就联网取，失败可重试。 */
        composable(
            route = Routes.EDIT_POST,
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
        ) { entry ->
            val postId = entry.arguments?.getString("postId").orEmpty()
            var editPost by remember { mutableStateOf(Shrine.peekPost(postId)) }
            var editLoadFailed by remember { mutableStateOf(false) }
            var editReload by remember { mutableStateOf(0) }
            LaunchedEffect(postId, editReload) {
                // 有缓存就直接用：中途换成联网取回的新实例会把已落笔的稿子冲掉
                if (editPost != null) return@LaunchedEffect
                editLoadFailed = false
                editPost = try {
                    Shrine.post(postId)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    editLoadFailed = true
                    null
                }
            }
            val loaded = editPost
            if (loaded != null) {
                NewPostScreen(
                    initialBoard = loaded.board,
                    editPost = loaded,
                    onBack = { navController.popBackStack() },
                    // 改完退回详情：那边一露面就重新取一遍，拿到的就是新稿
                    onPublished = { navController.popBackStack() },
                )
            } else if (editLoadFailed) {
                dev.arisu.shrine.core.components.ShrineEmptyState(
                    note = "加载失败，点击重试",
                    onClick = { editReload++ },
                )
            } else {
                dev.arisu.shrine.core.components.ShrineLoadingState()
            }
        }

        /* ── 神社祝部 ───────────────────────────────────── */
        composable(
            route = Routes.SHRINE + "?tab={tab}",
            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
        ) { entry ->
            val unread by Shrine.unread.collectAsStateWithLifecycle()
            ShrineBoardScreen(
                initialTabIndex = entry.arguments?.getInt("tab") ?: 0,
                onOpenUser = { navController.navigate(Routes.userProfile(it)) { launchSingleTop = true } },
                unreadCount = unread,
                onOpenEma = { navController.navigate(Routes.emaDetail(it)) { launchSingleTop = true } },
                onNewEma = { navController.navigate(Routes.NEW_EMA) { launchSingleTop = true } },
                onOpenInbox = { navController.navigate(Routes.INBOX) { launchSingleTop = true } },
                onOpenChangelog = { navController.navigate(Routes.changelogDetail(it)) { launchSingleTop = true } },
            )
        }
        composable(
            route = Routes.EMA_DETAIL,
            arguments = listOf(navArgument("emaId") { type = NavType.IntType }),
        ) { entry ->
            EmaDetailScreen(
                emaId = entry.arguments?.getInt("emaId") ?: 1,
                onBack = { navController.popBackStack() },
                onOpenUser = { navController.navigate(Routes.userProfile(it)) { launchSingleTop = true } },
                onEdit = { navController.navigate(Routes.editEma(it)) { launchSingleTop = true } },
            )
        }
        composable(Routes.NEW_EMA) {
            NewEmaScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.emaDetail(id)) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = Routes.EDIT_EMA,
            arguments = listOf(navArgument("emaId") { type = NavType.IntType }),
        ) { entry ->
            val emaId = entry.arguments?.getInt("emaId") ?: 0
            var editEma by remember { mutableStateOf<dev.arisu.shrine.data.model.Ema?>(null) }
            // 加载失败不再静默退化成新建模式（此前保存会把「编辑」变成重发一块新绘马）
            var editLoadFailed by remember { mutableStateOf(false) }
            var editReload by remember { mutableStateOf(0) }
            LaunchedEffect(emaId, editReload) {
                editLoadFailed = false
                editEma = try {
                    Shrine.ema(emaId)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    editLoadFailed = true
                    null
                }
            }
            if (editLoadFailed && editEma == null) {
                dev.arisu.shrine.core.components.ShrineEmptyState(
                    note = "加载失败，点击重试",
                    onClick = { editReload++ },
                )
            } else if (editEma == null) {
                // 资料未到的窗口不渲染「新建」形态：标题栏会闪现发绘马页，输入的还会被覆盖
                dev.arisu.shrine.core.components.ShrineLoadingState()
            } else {
                NewEmaScreen(
                    editEma = editEma,
                    onBack = { navController.popBackStack() },
                    onCreated = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = Routes.CHANGELOG_DETAIL,
            arguments = listOf(navArgument("version") { type = NavType.StringType }),
        ) { entry ->
            ChangelogDetailScreen(
                version = entry.arguments?.getString("version").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        /* ── 神社游会 ───────────────────────────────────── */
        composable(
            route = Routes.PLAY,
        ) {
            val unread by Shrine.unread.collectAsStateWithLifecycle()
            PlayScreen(
                unreadCount = unread,
                onOpenInbox = { navController.navigate(Routes.INBOX) { launchSingleTop = true } },
            )
        }

        /* ── 我的客房 ───────────────────────────────────── */
        composable(
            route = Routes.ME,
        ) {
            val unread by Shrine.unread.collectAsStateWithLifecycle()
            MyRoomScreen(
                unreadCount = unread,
                onOpenInbox = { navController.navigate(Routes.INBOX) { launchSingleTop = true } },
                onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) { launchSingleTop = true } },
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenPost = { navController.navigate(Routes.postDetail(it)) { launchSingleTop = true } },
                onOpenFollowList = { navController.navigate(Routes.followList(it)) { launchSingleTop = true } },
                onOpenCollections = { navController.navigate(Routes.COLLECTIONS) { launchSingleTop = true } },
                onOpenDrafts = { navController.navigate(Routes.DRAFTS) { launchSingleTop = true } },
                onOpenHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
                onOpenUser = { navController.navigate(Routes.userProfile(it)) { launchSingleTop = true } },
                onOpenMyProfileTab = { tab ->
                    Shrine.me.value?.id?.let { navController.navigate(Routes.userProfile(it, tab)) { launchSingleTop = true } }
                },
            )
        }
        composable(Routes.DRAFTS) {
            DraftsScreen(
                onBack = { navController.popBackStack() },
                onOpenPost = { navController.navigate(Routes.postDetail(it)) { launchSingleTop = true } },
                onOpenEma = { navController.navigate(Routes.emaDetail(it)) { launchSingleTop = true } },
                onNewPost = {
                    navController.navigate("${Routes.NEW_POST}?board=${Board.MAIN.name}") {
                        launchSingleTop = true
                    }
                },
                onNewEma = { navController.navigate(Routes.NEW_EMA) { launchSingleTop = true } },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenPost = { navController.navigate(Routes.postDetail(it)) { launchSingleTop = true } },
                onOpenEma = { navController.navigate(Routes.emaDetail(it)) { launchSingleTop = true } },
            )
        }
        composable(Routes.EDIT_PROFILE) {
            EditProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenSessions = { navController.navigate(Routes.SESSIONS) { launchSingleTop = true } },
                onOpenAccountManage = { navController.navigate(Routes.ACCOUNT_MANAGE) { launchSingleTop = true } },
                onOpenBlocked = { navController.navigate(Routes.BLOCKED) { launchSingleTop = true } },
            )
        }
        composable(Routes.BLOCKED) {
            BlockedUsersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ACCOUNT_MANAGE) {
            AccountManageScreen(
                onBack = { navController.popBackStack() },
                onSwitched = { navController.popBackStack(Routes.CHAT, inclusive = false) },
            )
        }
        composable(Routes.SESSIONS) {
            SessionsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.FOLLOW_LIST,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { entry ->
            FollowListScreen(
                mode = entry.arguments?.getString("mode") ?: FOLLOW_MODE_FOLLOWING,
                onBack = { navController.popBackStack() },
                onOpenUser = { navController.navigate(Routes.userProfile(it)) { launchSingleTop = true } },
            )
        }
        composable(Routes.COLLECTIONS) {
            CollectionsScreen(
                onBack = { navController.popBackStack() },
                onOpenPost = { navController.navigate(Routes.postDetail(it)) { launchSingleTop = true } },
            )
        }
        composable(
            route = Routes.USER_PROFILE,
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            UserProfileScreen(
                userId = entry.arguments?.getString("userId").orEmpty(),
                initialPostTab = entry.arguments?.getInt("tab") ?: 0,
                onBack = { navController.popBackStack() },
                onOpenPost = { navController.navigate(Routes.postDetail(it)) { launchSingleTop = true } },
                onOpenFollowList = { navController.navigate(Routes.followList(it)) { launchSingleTop = true } },
            )
        }

        /* ── 社务箱 / 搜索 ──────────────────────────────── */
        composable(
            route = Routes.NOTICE_DETAIL,
            arguments = listOf(navArgument("noticeId") { type = NavType.StringType; defaultValue = "" }),
        ) {
            NoticeDetailScreen(
                noticeId = it.arguments?.getString("noticeId").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenRoute = { route ->
                    // 服务端下发的 targetRoute 原样进 NavController：空 id/非数字等
                    // 畸形值会抛 IllegalArgumentException 直接崩——宁可不跳也不崩
                    runCatching {
                        when {
                            route.startsWith("post/") -> navController.navigate(route) { launchSingleTop = true }
                            route.startsWith("ema/") -> navController.navigate(route) { launchSingleTop = true }
                            route.startsWith("user/") -> navController.navigate(route) { launchSingleTop = true }
                            route == "staff" -> navController.navigate(Routes.SHRINE + "?tab=1") { launchSingleTop = true }
                            route == "changelog" -> navController.navigate(Routes.SHRINE + "?tab=2") { launchSingleTop = true }
                            route == "reports" -> navController.navigate(Routes.reports()) { launchSingleTop = true }
                            // report/N：举报类通知（管理员收到的新举报 / 举报结果），
                            // 之前没有分支——点「前往查看」毫无反应（用户实测反馈）。
                            // 带上 N：队列里找不到它，说明已被别的管理人员处理
                            route.startsWith("report/") -> navController.navigate(
                                Routes.reports(route.removePrefix("report/"))
                            ) { launchSingleTop = true }
                        }
                    }
                },
                // 收藏/标为未读/删除都要让下层的社务箱回来后刷新
                onChanged = {
                    navController.previousBackStackEntry?.savedStateHandle?.let { sh ->
                        sh.set("inbox_dirty", (sh.get<Int>("inbox_dirty") ?: 0) + 1)
                    }
                },
            )
        }
        composable(
            route = Routes.REPORTS,
            arguments = listOf(navArgument("focus") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            ReportsScreen(
                focusReportId = entry.arguments?.getString("focus").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenPost = { navController.navigate(Routes.postDetail(it)) { launchSingleTop = true } },
                onOpenEma = { navController.navigate(Routes.emaDetail(it)) { launchSingleTop = true } },
                onOpenUser = { navController.navigate(Routes.userProfile(it)) { launchSingleTop = true } },
            )
        }
        composable(Routes.INBOX) { entry ->
            val inboxDirty = entry.savedStateHandle
                .getStateFlow("inbox_dirty", 0)
                .collectAsStateWithLifecycle()
            InboxScreen(
                onBack = { navController.popBackStack() },
                onOpenDetail = { id -> navController.navigate(Routes.noticeDetail(id)) { launchSingleTop = true } },
                refreshKey = inboxDirty.value,
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenPost = { navController.navigate(Routes.postDetail(it)) { launchSingleTop = true } },
                onOpenUser = { navController.navigate(Routes.userProfile(it)) { launchSingleTop = true } },
            )
        }
    }
}

private fun NavHostController.switchTab(tab: ArisuTab) {
    // 不保存/恢复状态：每次切到主板块都是默认视图（选中项复位 + 回到顶部）。
    // 此前的 restoreState 会把滚动位置一并还原，列表停在上次的位置，看起来像「没复位」。
    //
    // 回「有栖茶话」还要连条目一起重建：它是起始页，launchSingleTop 会复用旧条目，
    // rememberSaveable 的页签态（幻想乡闻/最热）照旧还原——用户要的是回正殿·最新。
    val isChat = tab.route == Routes.CHAT
    navigate(tab.route) {
        if (isChat) {
            popUpTo(graph.findStartDestination().id) {
                inclusive = true
                saveState = false
            }
        } else {
            popUpTo(graph.findStartDestination().id)
        }
        launchSingleTop = true
    }
}

