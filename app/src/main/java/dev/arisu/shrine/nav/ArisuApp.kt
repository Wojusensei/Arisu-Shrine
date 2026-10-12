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
import androidx.compose.runtime.collectAsState
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
import dev.arisu.shrine.feature.profile.CollectionsScreen
import dev.arisu.shrine.feature.profile.EditProfileScreen
import dev.arisu.shrine.feature.profile.FollowListScreen
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
    val session by Shrine.sessionState.collectAsState()

    when (session) {
        SessionState.Booting -> SplashScreen()
        else -> key(session is SessionState.LoggedIn) {
            ShrineApp(loggedIn = session is SessionState.LoggedIn)
        }
    }
}

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

    val normalizedRoute = currentRoute?.substringBefore('?')
    val currentTab = ArisuTab.entries.firstOrNull { it.route == normalizedRoute }
    val unread by Shrine.unread.collectAsState()
    val appScope = androidx.compose.runtime.rememberCoroutineScope()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {

                appScope.launch { Shrine.refreshUnread() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

                    .consumeWindowInsets(androidx.compose.foundation.layout.PaddingValues(bottom = innerPadding.calculateBottomPadding()))
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                ArisuNavHost(navController = navController, currentRoute = currentRoute, loggedIn = loggedIn, unread = unread)
            }
        }
    }
}

@Composable
private fun ArisuNavHost(navController: NavHostController, currentRoute: String?, loggedIn: Boolean, unread: Int) {

    val tabPatterns = remember {
        ArisuTab.entries.map { it.route } + (Routes.SHRINE + "?tab={tab}")
    }
    fun tabIndex(route: String?) = ArisuTab.entries.indexOfFirst { it.route == route }
        .let { if (route == Routes.SHRINE + "?tab={tab}") 1 else it }

    NavHost(
        navController = navController,
        startDestination = if (loggedIn) Routes.CHAT else Routes.LOGIN,
        modifier = Modifier.fillMaxSize(),

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

        composable(Routes.LOGIN) {
            LoginScreen(
                onLogin = {  },
                onGoRegister = { navController.navigate(Routes.REGISTER) { launchSingleTop = true } },
                onForgot = { navController.navigate(Routes.FORGOT) { launchSingleTop = true } },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegister = {  },
                onGoLogin = { navController.popBackStack() },
            )
        }
        composable(Routes.FORGOT) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.CHAT,
        ) {
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
            )
        }

        composable(
            route = Routes.SHRINE + "?tab={tab}",
            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
        ) { entry ->
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

        composable(
            route = Routes.PLAY,
        ) {
            PlayScreen(
                unreadCount = unread,
                onOpenInbox = { navController.navigate(Routes.INBOX) { launchSingleTop = true } },
            )
        }

        composable(
            route = Routes.ME,
        ) {
            MyRoomScreen(
                unreadCount = unread,
                onOpenInbox = { navController.navigate(Routes.INBOX) { launchSingleTop = true } },
                onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) { launchSingleTop = true } },
                onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenPost = { navController.navigate(Routes.postDetail(it)) { launchSingleTop = true } },
                onOpenFollowList = { navController.navigate(Routes.followList(it)) { launchSingleTop = true } },
                onOpenCollections = { navController.navigate(Routes.COLLECTIONS) { launchSingleTop = true } },
                onOpenUser = { navController.navigate(Routes.userProfile(it)) { launchSingleTop = true } },
                onOpenMyProfileTab = { tab ->
                    Shrine.me.value?.id?.let { navController.navigate(Routes.userProfile(it, tab)) { launchSingleTop = true } }
                },
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
            )
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

        composable(
            route = Routes.NOTICE_DETAIL,
            arguments = listOf(navArgument("noticeId") { type = NavType.StringType; defaultValue = "" }),
        ) {
            NoticeDetailScreen(
                noticeId = it.arguments?.getString("noticeId").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenRoute = { route ->

                    runCatching {
                        when {
                            route.startsWith("post/") -> navController.navigate(route) { launchSingleTop = true }
                            route.startsWith("ema/") -> navController.navigate(route) { launchSingleTop = true }
                            route.startsWith("user/") -> navController.navigate(route) { launchSingleTop = true }
                            route == "staff" -> navController.navigate(Routes.SHRINE + "?tab=1") { launchSingleTop = true }
                            route == "changelog" -> navController.navigate(Routes.SHRINE + "?tab=2") { launchSingleTop = true }
                            route == "reports" -> navController.navigate(Routes.reports()) { launchSingleTop = true }

                            route.startsWith("report/") -> navController.navigate(
                                Routes.reports(route.removePrefix("report/"))
                            ) { launchSingleTop = true }
                        }
                    }
                },

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
                .collectAsState()
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

    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}
