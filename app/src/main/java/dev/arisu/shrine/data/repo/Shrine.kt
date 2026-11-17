package dev.arisu.shrine.data.repo

import android.content.Context
import dev.arisu.shrine.core.media.stagePickedImage
import dev.arisu.shrine.core.util.TimeFmt
import dev.arisu.shrine.core.util.streakDays
import dev.arisu.shrine.data.AccountVault
import dev.arisu.shrine.data.SessionStore
import dev.arisu.shrine.data.VaultAccount
import dev.arisu.shrine.data.local.DraftStore
import dev.arisu.shrine.data.local.HistoryStore
import dev.arisu.shrine.data.model.Achievement
import dev.arisu.shrine.data.model.Board
import dev.arisu.shrine.data.model.ChangeKind
import dev.arisu.shrine.data.model.Collection
import dev.arisu.shrine.data.model.Comment
import dev.arisu.shrine.data.model.Ema
import dev.arisu.shrine.data.model.EmaReply
import dev.arisu.shrine.data.model.EmaState
import dev.arisu.shrine.data.model.EmaTag
import dev.arisu.shrine.data.model.Notice
import dev.arisu.shrine.data.model.NoticeKind
import dev.arisu.shrine.data.model.OmikujiBoard
import dev.arisu.shrine.data.model.OmikujiDraw
import dev.arisu.shrine.data.model.OmikujiLeaderRow
import dev.arisu.shrine.data.model.OmikujiFortune
import dev.arisu.shrine.data.model.OmikujiStanding
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.PostSort
import dev.arisu.shrine.data.model.ReleaseChannel
import dev.arisu.shrine.data.model.ReleaseNote
import dev.arisu.shrine.data.model.Role
import dev.arisu.shrine.data.model.Sticker
import dev.arisu.shrine.data.model.TileDay
import dev.arisu.shrine.data.model.User
import dev.arisu.shrine.data.model.avatarUrl
import dev.arisu.shrine.data.remote.Api
import dev.arisu.shrine.data.remote.ApiError
import dev.arisu.shrine.data.remote.ApplicationDto
import dev.arisu.shrine.data.remote.CommentDto
import dev.arisu.shrine.data.remote.CommentReq
import dev.arisu.shrine.data.remote.CollectionReq
import dev.arisu.shrine.data.remote.DecideReq
import dev.arisu.shrine.data.remote.DeleteReasonReq
import dev.arisu.shrine.data.remote.EmaPatchReq
import dev.arisu.shrine.data.remote.EmaReplyReq
import dev.arisu.shrine.data.remote.EmaReq
import dev.arisu.shrine.data.remote.EmaStateReq
import dev.arisu.shrine.data.remote.FavoriteReq
import dev.arisu.shrine.data.remote.HeartbeatReq
import dev.arisu.shrine.data.remote.LoginReq
import dev.arisu.shrine.data.remote.LogoutReq
import dev.arisu.shrine.data.remote.MeDto
import dev.arisu.shrine.data.remote.MyApplicationRes
import dev.arisu.shrine.data.remote.MuteReq
import dev.arisu.shrine.data.remote.OmikujiLeaderDto
import dev.arisu.shrine.data.remote.NoticeStarReq
import dev.arisu.shrine.data.remote.PasswordPatchReq
import dev.arisu.shrine.data.remote.PostDto
import dev.arisu.shrine.data.remote.PostPatchReq
import dev.arisu.shrine.data.remote.PostReq
import dev.arisu.shrine.data.remote.ProgressRequestBody
import dev.arisu.shrine.data.remote.ProfilePatchReq
import dev.arisu.shrine.data.remote.RefreshReq
import dev.arisu.shrine.data.remote.RegisterReq
import dev.arisu.shrine.data.remote.ReleaseReq
import dev.arisu.shrine.data.remote.ReportReq
import dev.arisu.shrine.data.remote.ResetReq
import dev.arisu.shrine.data.remote.ResolveReq
import dev.arisu.shrine.data.remote.SendCodeReq
import dev.arisu.shrine.data.remote.SessionDto
import dev.arisu.shrine.data.remote.StaffApplyReq
import dev.arisu.shrine.data.remote.StickerAddReq
import dev.arisu.shrine.data.remote.StickerReorderReq
import dev.arisu.shrine.data.remote.UserBriefDto
import dev.arisu.shrine.data.remote.UserDto
import dev.arisu.shrine.data.remote.apiCall
import dev.arisu.shrine.data.remote.toApiMessage
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import java.io.File

/** 会话状态：启动检查 → 已登录 / 未登录 */
sealed interface SessionState {
    data object Booting : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val me: User) : SessionState
}

/**
 * 全局仓库：网络数据 → 界面模型。
 * 名字沿用「Shrine」，但数据全部来自服务器，不再有任何假数据。
 */
object Shrine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var session: SessionStore
    private lateinit var vault: AccountVault

    /** 本地账号档案（多账号快速切换）；lazy 起订，init() 装好 vault 后才可访问 */
    val vaultAccounts: StateFlow<List<VaultAccount>> by lazy {
        vault.accounts.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    // ── 会话 ────────────────────────────────────────────────
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Booting)
    val sessionState: StateFlow<SessionState> = _sessionState

    private val _me = MutableStateFlow<User?>(null)
    val me: StateFlow<User?> = _me

    /** 截图测试种子：直接注入会话数据（不进生产逻辑，仅测试基建调用） */
    fun seedForTest(user: User?) {
        _me.value = user
        if (user != null) _sessionState.value = SessionState.LoggedIn(user)
    }

    private val _unreadCount = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unreadCount

    /** 服务器地址（登录页 / 设置页可改） */
    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl

    fun init(context: Context) {
        session = SessionStore(context)
        vault = AccountVault(context)
        CacheStore.init(context)
        DraftStore.init(context)
        HistoryStore.init(context)
        // 后台把上次落盘的数据读进内存镜像，界面首帧就能同步取到（不再先空态再刷新）
        CacheStore.preload()
        // OkHttp Authenticator 静默续签成功后，旋转出的新 refresh token 也必须落盘，
        // 否则本地留着的旧 token 已被服务器作废，下次冷启动必被踢回登录页
        Api.onSessionRefreshed = { oldRefresh, access, refresh ->
            session.saveTokens(access, refresh)
            // 账号档案里的副本也必须跟着轮换走：档案只在登录/切换时写过一次，
            // 服务端却每天至少换一次令牌——登录满一天后档案里剩的就是死令牌，
            // 点它免密切换必然 401，还会被 switchAccount 的失败分支顺手登出。
            // 2026-10-17 用户报告「帖子写到一半突然退出登录」的根源之一
            runCatching { vault.refreshTokens(oldRefresh, access, refresh) }
        }
        Api.onRotationAbandoned = { oldRefresh, access, refresh ->
            runCatching { vault.refreshTokens(oldRefresh, access, refresh) }
        }
        // 服务端踢设备/吊销会话后必须收敛回登录页——否则停在「看似已登录」的
        // 401 黑洞态（安全审计实锤的客户端缺口：每个请求都失败、界面毫无感知）。
        // 同时记一笔「续签是被服务端明确拒绝的」，冷启动兜底据此区分
        // 「会话真没了」与「只是网络/服务端抖动」（见 bootstrap）
        Api.onSessionDead = {
            sessionDeadFired = true
            scope.launch { toLoggedOut() }
        }
        scope.launch { bootstrap() }
        scope.launch { heartbeatLoop() }
        // 登录成功立刻戳一下心跳：循环固定 60 秒节拍，不戳要等满一拍
        // （StateFlow 天然去重，Booting/LoggedOut 反复发射也不会多戳）
        scope.launch {
            sessionState.collect {
                if (it is SessionState.LoggedIn) heartbeatPoke.trySend(Unit)
            }
        }
        // 冷启动预连接：DNS+TCP+TLS+HTTP/2 的握手提前到启动瞬间打好，
        // 首屏真正发请求时直接复用连接，省一个往返
        scope.launch { warmConnection() }
    }

    /** 续签是否被服务端明确拒绝过（被踢/吊销/过期）。装新会话时清掉 */
    @Volatile private var sessionDeadFired = false

    // ── 板块预热 ────────────────────────────────────────────

    private var warmJob: Job? = null
    private var warmedUserId: String? = null

    /** 预热期间为 true：cached() 的回退不发「网络不佳」横幅——横幅该由用户正在看的页面立 */
    @Volatile private var warming = false

    /**
     * 后台预热各板块/各 tab 的首选数据。
     *
     * 切板块、切底栏此前要先等一次跨境往返（加载态转圈就是它），用户反馈
     * 「每次切板块都要好久」。这里把各页面进页本来就要拉的同一批只读数据
     * 提前拉好：第一帧直接有内容可摆，随后照常静默刷新。顺带把对象解析
     * 结果记进缓存记忆，进页面时连 JSON 都不用解。
     *
     * 全是只读 GET、没有副作用；失败一律咽掉——预热只是锦上添花，
     * 绝不能变成新的错误来源。错开 120ms 逐个来，不和当前页面自己的请求抢带宽。
     */
    private fun warmCaches() {
        val uid = _me.value?.id ?: return
        if (_sessionState.value !is SessionState.LoggedIn) return
        if (warmedUserId == uid && warmJob?.isActive == true) return
        warmedUserId = uid
        warmJob?.cancel()
        warmJob = scope.launch {
            warming = true
            try {
                suspend fun step(block: suspend () -> Unit) {
                    runCatching { block() }
                    delay(120)
                }
                step { postsOf(Board.GENSOKYO, PostSort.LATEST, page = 1) }
                step { postsOf(Board.DEV, PostSort.LATEST, page = 1) }
                step { emas(emptyList(), emptyList(), "new", page = 1) }
                step { staffGroups() }
                step { releases() }
                step { omikujiToday() }
                step { omikujiLeaderboard() }
                step { onlineStats() }
                step { myRoom() }
                step { postsByAuthor(uid, "tracked") }
                step { postsByAuthor(uid, "liked") }
                step { postsByAuthor(uid, "favorited") }
            } finally {
                warming = false
            }
        }
    }

    // ── 弱网缓存 ────────────────────────────────────────────
    //
    // 只读数据统一走 cached()：网络成功即落盘；连接层面的失败
    // （断网、超时、DNS）回读上一次的数据，界面不至于空手而归。
    // 服务器的业务错误（401/403/404 等）不属于网络问题，照常上抛。

    /** 非 null 表示最近一次数据来自缓存（附缓存时间文案），界面据此显示提示条 */
    private val _staleNotice = MutableStateFlow<String?>(null)
    val staleNotice: StateFlow<String?> = _staleNotice

    private fun cacheKey(vararg parts: Any?): String = parts.joinToString("|")

    /** 缓存键统一带用户前缀：换账号登录后旧缓存取不到，不会串号 */
    private fun scopedKey(key: String): String = cacheKey(_me.value?.id ?: "anon", key)

    /** 缓存距今的口语化时长 */
    private fun cacheAgo(cachedAt: Long): String {
        val mins = ((System.currentTimeMillis() / 1000) - cachedAt) / 60
        return when {
            mins < 1 -> "刚刚"
            mins < 60 -> "$mins 分钟前"
            mins < 60 * 24 -> "${mins / 60} 小时前"
            else -> "${mins / 60 / 24} 天前"
        }
    }

    private suspend fun <T> cached(
        key: String,
        serializer: KSerializer<T>,
        cacheIf: (T) -> Boolean = { true },
        fetch: suspend () -> T,
    ): T {
        val fullKey = scopedKey(key)
        return try {
            val fresh = fetch()
            // 空列表不落盘：网络抖动时不会拿陈旧空列表冒充“没有帖子”
            // 落盘是 JSON 序列化+写文件+目录扫描，必须离开主线程
            if (cacheIf(fresh)) withContext(Dispatchers.IO) { CacheStore.put(fullKey, serializer, fresh) }
            _staleNotice.value = null
            fresh
        } catch (e: Exception) {
            if (e is ApiError || e !is IOException) throw e
            val snap = withContext(Dispatchers.IO) { CacheStore.get(fullKey, serializer) } ?: throw e
            // 板块预热也会走到这里：它失败不该在用户当前页面上立横幅，
            // 横幅只反映「用户正在看的这份数据是不是旧的」
            if (!warming) _staleNotice.value = "网络不佳，展示的是 ${cacheAgo(snap.cachedAt)}的数据，刷新后更新"
            snap.value
        }
    }

    // ── 首帧预置（peek） ────────────────────────────────────
    //
    // 页面挂载时先同步取出上次的数据摆上，再照常联网刷新：板块切换、冷启动
    // 都不会先渲染一个「没有/未做」的空态再跳一下。数据键与 cached() 共用
    // 同一批私有构造器，两边不会各写一份而慢慢对不上。

    private fun kPosts(board: Board, sort: PostSort, page: Int, pageSize: Int) =
        cacheKey("posts", board, sort, "p$page", "n$pageSize")

    /** 茶话某板块某排序某页：上次的列表（含总数，可原样还原翻页进度） */
    fun peekPosts(board: Board, sort: PostSort, page: Int = 1, pageSize: Int = 20): PostPage? =
        peekCache(kPosts(board, sort, page, pageSize), PostPage.serializer())

    /** 单帖：列表点进详情时先摆上列表里那份内容，避免整页转圈 */
    fun peekPost(id: String): Post? = peekCache(cacheKey("post", id), Post.serializer())

    fun peekComments(postNo: String): List<Comment>? =
        peekCache(cacheKey("comments", postNo), ListSerializer(Comment.serializer()))

    private fun kEmas(tags: List<String>, states: List<String>, sort: String, page: Int, pageSize: Int) =
        cacheKey("emas", tags.joinToString(","), states.joinToString(","), sort, "p$page", "n$pageSize")

    fun peekEmas(
        tags: List<String> = emptyList(),
        states: List<String> = emptyList(),
        sort: String = "new",
        page: Int = 1,
        pageSize: Int = 20,
    ): EmaPage? = peekCache(kEmas(tags, states, sort, page, pageSize), EmaPage.serializer())

    fun peekEma(id: Int): Ema? = peekCache(cacheKey("ema", id), Ema.serializer())

    fun peekEmaReplies(id: Int): List<EmaReply>? =
        peekCache(cacheKey("emaReplies", id), ListSerializer(EmaReply.serializer()))

    fun peekStaff(): List<StaffGroup>? =
        peekCache(cacheKey("staff"), ListSerializer(StaffGroup.serializer()))

    fun peekReleases(): List<ReleaseNote>? =
        peekCache(cacheKey("releases"), ListSerializer(ReleaseNote.serializer()))

    fun peekTiles(userId: String? = null): List<TileDay>? =
        peekCache(cacheKey("tiles", userId ?: "me"), ListSerializer(TileDay.serializer()))

    fun peekMyRoom(): Room? = peekCache(cacheKey("myRoom"), Room.serializer())

    fun peekAuthorPosts(userId: String, tab: String = "posts", collectionId: Long? = null): List<Post>? =
        // 自己的收藏页带 collectionId、他人的不带，与 postsByAuthor 的分支保持一致
        if (userId == _me.value?.id) {
            peekCache(cacheKey("authorPosts", userId, tab, collectionId), ListSerializer(Post.serializer()))
        } else {
            peekCache(cacheKey("authorPosts", userId, tab), ListSerializer(Post.serializer()))
        }

    fun peekUser(id: String): UserDetail? = peekCache(cacheKey("user", id), UserDetail.serializer())

    fun peekCollections(): List<Collection>? =
        peekCache(cacheKey("collections"), ListSerializer(Collection.serializer()))

    fun peekFollowing(): List<User>? =
        peekCache(cacheKey("following"), ListSerializer(User.serializer()))

    fun peekFollowers(): List<User>? =
        peekCache(cacheKey("followers"), ListSerializer(User.serializer()))

    private fun kNotices(category: String, only: String, page: Int, pageSize: Int) =
        cacheKey("notices", category, only, "p$page", "n$pageSize")

    fun peekNotices(category: String = "all", only: String = "", page: Int = 1, pageSize: Int = 30): NoticePage? =
        peekCache(kNotices(category, only, page, pageSize), NoticePage.serializer())

    fun peekOnlineStats(): OnlineStats? = peekCache(cacheKey("stats"), OnlineStats.serializer())

    /**
     * 今日求签结果：null = 还没有今日记录（与「今日未抽」是两回事）。
     * 按服务器日切（东八区）落盘，跨天自动换键，不会把昨天的签拿来说今天。
     */
    fun peekOmikujiToday(): Int? =
        peekCache(cacheKey("omikuji", omikujiDayKey()), Int.serializer())

    private fun omikujiDayKey(): String =
        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString()

    /** 首帧同步预置：取上次落盘（或内存镜像）里的数据，不联网、不挂起 */
    fun <T> peekCache(key: String, serializer: KSerializer<T>): T? =
        CacheStore.peek(scopedKey(key), serializer)

    suspend fun bootstrap() {
        val base = session.currentBaseUrl()
        val access = session.currentAccess()
        val refresh = session.currentRefresh()
        Api.updateSession(base, access, refresh)
        _baseUrl.value = base
        if (base.isEmpty()) {
            _sessionState.value = SessionState.LoggedOut
            return
        }
        if (access.isEmpty() && refresh.isEmpty()) {
            _sessionState.value = SessionState.LoggedOut
            return
        }
        try {
            // access 过期则用 refresh 续期
            if (access.isNotEmpty()) {
                val meDto = apiCall { Api.service.me() }
                applyMe(meDto)
            } else {
                throw ApiError("no access")
            }
        } catch (e: Exception) {
            // 本次尝试之前先清账：这个标记只描述「刚刚这次续签」的结果
            sessionDeadFired = false
            val refreshed = refreshTokens()
            if (refreshed) {
                // refreshTokens 内部已经拿续签响应里的 me 把会话撑起来了，
                // 这次 me() 只是补一次最新资料：失败不该把人踢下线
                // （续签都成功了，会话是活的，抖一下最多是资料旧一点）
                runCatching { applyMe(apiCall { Api.service.me() }) }
                if (_sessionState.value !is SessionState.LoggedIn) {
                    val cached = withContext(Dispatchers.IO) {
                        val uid = session.currentUserId()
                        if (uid.isEmpty()) null else CacheStore.get("me|$uid", User.serializer())?.value
                    }
                    if (cached != null) {
                        _me.value = cached
                        _sessionState.value = SessionState.LoggedIn(cached)
                    } else {
                        toLoggedOut()
                    }
                }
            } else {
                // 网络层失败（断网/超时）或服务端抖动（503）都不是「会话没了」：
                // 拿上次落盘的本人民资料把会话撑住，否则一次弱网冷启动就被踢回
                // 登录页，缓存里的一切也一起看不见了。只有续签被服务端明确拒绝
                // （401，即 sessionDeadFired 已被置位）才认账登出
                val rejected = sessionDeadFired
                val cached = if (e is ApiError && rejected) null else withContext(Dispatchers.IO) {
                    val uid = session.currentUserId()
                    if (uid.isEmpty()) null else CacheStore.get("me|$uid", User.serializer())?.value
                }
                if (cached != null) {
                    _me.value = cached
                    _sessionState.value = SessionState.LoggedIn(cached)
                    refreshUnread()
                } else {
                    toLoggedOut()
                }
            }
        }
    }

    private suspend fun applyMe(meDto: MeDto) {
        val user = meDto.toUser()
        _me.value = user
        _sessionState.value = SessionState.LoggedIn(user)
        // 会话是活的：清掉「被判死」的标记，并开始温度各板块首屏数据
        sessionDeadFired = false
        warmCaches()
        // 落盘一份：弱网冷启动 me() 打不通时，用这份资料把会话撑起来（键带用户 id）
        withContext(Dispatchers.IO) {
            session.saveUserId(user.id)
            CacheStore.put("me|${user.id}", User.serializer(), user)
        }
        refreshUnread()
    }

    private suspend fun toLoggedOut() {
        _me.value = null
        _unreadCount.value = 0
        _staleNotice.value = null
        // 预热属于这个会话：会话结束就停，免得无人接的请求继续占带宽
        warmJob?.cancel()
        warmedUserId = null
        _sessionState.value = SessionState.LoggedOut
    }

    /**
     * 会话令牌变更的唯一写入口：内存（Api 拦截器读的 volatile）与磁盘（冷启动恢复）
     * 一次写齐。此前七个调用点各自双写两行，漏一处就是「内存有 token、磁盘没有」。
     */
    private suspend fun installSession(base: String, access: String, refresh: String) {
        session.saveTokens(access, refresh)
        Api.updateSession(base, access, refresh)
        // 会话换人了：上一个账号的板块预热作废（数据键带用户前缀不会串号，
        // 但没必要让它继续跑）
        warmJob?.cancel()
        warmedUserId = null
    }

    private suspend fun refreshTokens(): Boolean {
        // 全应用只有 Api.refreshAccess 一条续签路径、一把锁；返回值带 me 顺路喂给会话
        val result = Api.refreshAccess() ?: return false
        result.me?.let { applyMe(it) }
        return true
    }

    /** 登录后立即上报一次，之后每 60 秒一次心跳。只在前台跳：后台常跳
     * 既费电也把在线时长虚撑起来；回前台或登录成功会戳一下立即补跳 */
    @Volatile private var appForeground = true
    private val heartbeatPoke = Channel<Unit>(capacity = 1)

    fun onAppForeground() {
        appForeground = true
        heartbeatPoke.trySend(Unit)
    }

    fun onAppBackground() {
        appForeground = false
    }

    private suspend fun heartbeatLoop() {
        while (true) {
            if (appForeground && _sessionState.value is SessionState.LoggedIn) {
                try {
                    val res = apiCall { Api.service.heartbeat(HeartbeatReq(activeSeconds = 60)) }
                    // 未读数搭心跳的车：角标随每跳刷新，省掉回前台那次独立请求
                    res.unread?.let { _unreadCount.value = it }
                    // 账面同车：每日 +50 与被奉纳收入都能在一分钟内反映到界面
                    res.balanceTenths?.let { b -> _me.value = _me.value?.copy(balanceTenths = b) }
                } catch (_: Exception) {
                    // 心跳失败不打扰用户
                }
            }
            // 等 60 秒或被戳（回前台/登录）：被戳就提前结束等待立即补跳
            withTimeoutOrNull(60 * 1000L) { heartbeatPoke.receive() }
        }
    }

    /** 启动预连接：一次探活把握手全做好。失败无所谓，正常请求路径不受影响 */
    private suspend fun warmConnection() {
        try {
            Api.service.health().close()
        } catch (_: Exception) {
        }
    }

    // ── 服务器地址 ──────────────────────────────────────────
    suspend fun saveBaseUrl(url: String) {
        val trimmed = url.trim().trimEnd('/')
        session.saveBaseUrl(trimmed)
        _baseUrl.value = trimmed
        // 换服必须清令牌：活体凭据不能跟着地址移植到另一台服务器
        // （当前只从登录页调用属加固；防未来登录态调用变成跨服令牌泄漏）
        session.clearSession()
        Api.updateSession(trimmed, "", "")
        // 换服即清缓存：弱网回读不能再翻出上一台服务器的帖子/用户数据
        CacheStore.clear()
    }

    // ── 认证 ────────────────────────────────────────────────
    /** 返回非 null 表示服务器处于开发模式（SMTP 未配置），验证码直接带回给界面显示 */
    suspend fun sendCode(email: String, purpose: String): String? {
        val res = apiCall { Api.service.sendCode(SendCodeReq(email, purpose)) }
        return res.devCode
    }

    suspend fun register(email: String, code: String, password: String, nickname: String) {
        val result = apiCall { Api.service.register(RegisterReq(email, code, password, nickname)) }
        onAuthed(result)
    }

    suspend fun login(email: String, password: String) {
        val result = apiCall { Api.service.login(LoginReq(email, password)) }
        onAuthed(result)
    }

    private suspend fun onAuthed(result: dev.arisu.shrine.data.remote.AuthResultDto) {
        installSession(_baseUrl.value, result.accessToken, result.refreshToken)
        val meDto = result.me ?: return
        // 顺序不能换：sessionState 一翻转，导航图整体重建、登录页的协程作用域随即取消，
        // 挂起在其后的档案写入永远跑不到——表现为「刚登录的账号不在快速切换列表里」。
        // 先置 me、落档案，最后才翻转会话；refreshUnread 被取消无妨，主界面 ON_RESUME 会补
        val user = meDto.toUser()
        _me.value = user
        saveCurrentToVault(meDto.email)
        _sessionState.value = SessionState.LoggedIn(user)
        refreshUnread()
    }

    suspend fun resetPassword(email: String, code: String, password: String) {
        apiCall { Api.service.resetPassword(ResetReq(email, code, password)) }
    }

    suspend fun logout() {
        try {
            apiCall { Api.service.logout(LogoutReq(refreshToken = Api.currentRefresh)) }
        } catch (_: Exception) {
        }
        // 档案保留（QQ 逻辑）：退出后头像仍在登录页「最近登录」里，点它预填邮箱
        // 输密码即可快速重登；要彻底从本机删掉走账号管理里的「移除」。
        // 令牌已随登出吊销，档案里留的旧令牌下次切换会自动失败转入密码登录
        session.clearSession()
        Api.updateSession(_baseUrl.value, "", "")
        clearLocalCache()
        toLoggedOut()
    }

    suspend fun refreshMe() {
        try {
            applyMe(apiCall { Api.service.me() })
        } catch (_: Exception) {
        }
    }

    suspend fun changePassword(old: String, new: String) {
        // 服务端会吊销全部旧会话并为当前设备签发新 token，保存以免自己被登出
        val result = apiCall { Api.service.changePassword(PasswordPatchReq(old, new)) }
        if (result.accessToken.isNotEmpty()) {
            installSession(_baseUrl.value, result.accessToken, result.refreshToken)
            // 旧 refresh 已随改密作废，档案里的副本必须同步换新，否则下次切换自己必失败
            saveCurrentToVault()
        }
    }

    suspend fun deactivate() {
        apiCall { Api.service.deactivate() }
        val meNow = _me.value
        if (meNow != null) vault.remove(userKey(meNow.id))
        session.clearSession()
        Api.updateSession(_baseUrl.value, "", "")
        clearLocalCache()
        toLoggedOut()
    }

    /** 登出/注销即清磁盘缓存：cacheDir 里不留任何能回读的旧会话数据 */
    private fun clearLocalCache() {
        CacheStore.clear()
    }

    // ── 本地账号库（多账号快速切换） ────────────────────────

    /** 把当前登录会话写进本地账号档案（切走/落位时调用，令牌始终是此刻最新值） */
    private suspend fun saveCurrentToVault(email: String? = null) {
        val me = _me.value ?: return
        val base = _baseUrl.value
        if (base.isEmpty()) return
        vault.upsert(
            VaultAccount(
                userId = userKey(me.id),
                no = me.id,
                email = email ?: me.email,
                nickname = me.nickname,
                avatarUrl = me.avatarUrl ?: "",
                baseUrl = base,
                access = session.currentAccess(),
                refresh = session.currentRefresh(),
                savedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * 免密切换到档案里的账号：当前会话写回档案 → 装载目标令牌 → 主动续签验证活性。
     * 成功后直接进主界面；失败（令牌被踢/30 天没用/改过密码）退回登录页并保留档案，
     * 重新登录成功会覆盖。全程不登出服务器——切走的账号保持在线，随时可切回
     */
    suspend fun switchAccount(target: VaultAccount): Boolean {
        val meNow = _me.value
        if (meNow != null && userKey(meNow.id) == target.userId) return true
        // 失败要能原路还原：先记下切换前实际指向的服务器
        val oldBase = Api.currentBaseUrl.ifEmpty { _baseUrl.value }
        if (meNow != null) saveCurrentToVault()
        // 失败要能原路回来：记下切换前的令牌（档案写回之后再读，读到的是最新一份）
        val oldAccess = session.currentAccess()
        val oldRefreshSelf = session.currentRefresh()
        // 复用换服语义：saveBaseUrl 自带清缓存；同服切换幂等
        session.saveBaseUrl(target.baseUrl)
        installSession(target.baseUrl, target.access, target.refresh)
        clearLocalCache()
        // 与 onAuthed 同理：先落档案（用轮换出的最新令牌覆盖）再翻转会话——
        // LoggedIn→LoggedIn 不重建导航图，调用方（账号管理屏）的协程存活，写入跑得到
        val user = try {
            val result = Api.refreshAccess() ?: throw ApiError("登录已过期，请重新登录")
            result.me?.toUser()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
        if (user == null) {
            // 目标账号的令牌失效，不该顺手把人也登出：原样装回切换前的账号，
            // 会话、缓存里的东西、正在做的事都留在原地（2026-10-17 事故：
            // 点一下失效档案就把当前会话一起清掉，人被打回登录页）
            if (meNow != null && oldRefreshSelf.isNotEmpty()) {
                session.saveBaseUrl(oldBase)
                installSession(oldBase, oldAccess, oldRefreshSelf)
                _baseUrl.value = oldBase
                _me.value = meNow
                _sessionState.value = SessionState.LoggedIn(meNow)
                runCatching { refreshUnread() }
                return false
            }
            // 本来就没登录（登录页点档案）：清干净回登录页。服务器指向必须还原——
            // 否则登录页显示的还是原地址，请求却发往目标服，后续登录全部打错服务器。
            // installSession 已把目标账号的死令牌写进 DataStore，必须一并清掉
            session.clearSession()
            session.saveBaseUrl(oldBase)
            Api.updateSession(oldBase, "", "")
            toLoggedOut()
            return false
        }
        _me.value = user
        _baseUrl.value = target.baseUrl
        // 档案写入失败只降级（下次切换需密码），不该推翻已成功的切换
        runCatching { saveCurrentToVault(target.email) }
        _sessionState.value = SessionState.LoggedIn(user)
        // 单例未读数是上一个账号的残留，不刷新角标会「切完号数字对不上」
        runCatching { refreshUnread() }
        return true
    }

    /** 「添加账号」：当前会话只落本地档案（保持服务器在线），随后清会话进登录页 */
    suspend fun parkCurrentSession(): Boolean {
        if (_me.value == null) return false
        saveCurrentToVault()
        session.clearSession()
        Api.updateSession(_baseUrl.value, "", "")
        clearLocalCache()
        toLoggedOut()
        return true
    }

    /** 从档案移除账号；顺带尽力吊销服务器会话（logout 以 refresh 原文定位，无需登录态） */
    suspend fun removeVaultAccount(account: VaultAccount) {
        runCatching { apiCall { Api.service.logout(LogoutReq(refreshToken = account.refresh)) } }
        vault.remove(account.userId)
    }

    // ── 登录设备管理 ────────────────────────────────────────

    suspend fun sessions(): List<SessionDto> =
        apiCall { Api.service.sessions(session.currentRefresh()) }.items

    suspend fun kickSession(id: Long) {
        apiCall { Api.service.kickSession(id) }
    }

    // ── 编号换算 ────────────────────────────────────────────
    private fun postKey(no: String): Long = no.removePrefix("arisu_").toLongOrNull() ?: 0L
    private fun userKey(no: String): Long = no.removePrefix("User_").toLongOrNull() ?: 0L

    // ── 茶话 ────────────────────────────────────────────────

    @Serializable
    data class PostPage(val items: List<Post>, val total: Long, val page: Int)

    suspend fun postsOf(board: Board, sort: PostSort, page: Int, pageSize: Int = 20): PostPage =
        cached(
            kPosts(board, sort, page, pageSize),
            PostPage.serializer(),
            fetch = {
                val dto = apiCall {
                    Api.service.listPosts(board.wire, sort.wire, page, pageSize)
                }
                PostPage(dto.items.map { it.toPost() }, dto.total, dto.page)
            },
            cacheIf = { it.items.isNotEmpty() },
        )

    suspend fun post(id: String): Post =
        cached(cacheKey("post", id), Post.serializer()) {
            apiCall { Api.service.getPost(postKey(id)) }.toPost()
        }

    suspend fun createPost(board: Board, title: String, body: String, intro: String = "", cover: String = ""): Post =
        apiCall { Api.service.createPost(PostReq(board.wire, title, body, intro, cover)) }
            .toPost()
            // 立起「刚发过帖」旗标：茶话列表下次露面时回顶并重拉第一页，
            // 新帖就在第一行，不用用户自己往下找
            .also { _justPublished.value = true }

    suspend fun updatePost(id: String, title: String?, intro: String?, body: String?, board: Board? = null, cover: String? = null): Post {
        val post = apiCall {
            Api.service.updatePost(
                postKey(id),
                // intro 传空串即清空介绍语（服务端按指针判「有修改」）；
                // 此前把空串换成 null，explicitNulls=false 下字段整个消失，
                // 服务端视为未修改——介绍语一旦设置过就永远删不掉
                PostPatchReq(title, body, intro, board?.wire, cover),
            )
        }.toPost()
        // 改完的稿先落进本地缓存：详情页退回前台会立刻重取一次，
        // 这一取万一撞上网络不佳，兜底读出的得是新稿而不是改前的旧稿
        withContext(Dispatchers.IO) {
            CacheStore.put(scopedKey(cacheKey("post", id)), Post.serializer(), post)
        }
        return post
    }

    /** 宫司及以上：切换精华/置顶（服务端做权限校验） */
    suspend fun deletePost(id: String, reason: String? = null) {
        apiCall { Api.service.deletePost(postKey(id), DeleteReasonReq(reason ?: "")) }
    }

    suspend fun toggleUp(id: String): Pair<Boolean, Int> {
        val r = apiCall { Api.service.toggleUp(postKey(id)) }
        return r.upped to r.upCount.toInt()
    }

    /** 奉纳赛钱：回（新账面 tenths, 帖子新总额），本地账面同步刷掉 */
    suspend fun offerPost(id: String, amount: Long): Pair<Long, Int> {
        val r = apiCall { Api.service.offerPost(postKey(id), dev.arisu.shrine.data.remote.OfferReq(amount)) }
        _me.value = _me.value?.copy(balanceTenths = r.balanceTenths)
        return r.balanceTenths to r.offerTotal.toInt()
    }

    suspend fun toggleTrack(id: String): Boolean =
        apiCall { Api.service.toggleTrack(postKey(id)) }.tracked

    /** 收藏/换夹/移出三合一：collectionId=null 收进默认夹，=0 移出收藏（不管躺在哪个夹），其余=搬进指定夹 */
    suspend fun setFavorite(id: String, collectionId: Long? = null): Boolean =
        apiCall { Api.service.toggleFavorite(postKey(id), FavoriteReq(collectionId)) }.favorited

    suspend fun toggleEssence(id: String): Boolean =
        apiCall { Api.service.toggleEssence(postKey(id)) }.essence

    suspend fun togglePin(id: String): Boolean =
        apiCall { Api.service.togglePin(postKey(id)) }.pinned

    suspend fun reportPost(id: String, reason: String) {
        apiCall { Api.service.reportPost(postKey(id), ReportReq(reason)) }
    }

    suspend fun reportComment(commentId: String, reason: String) {
        apiCall { Api.service.reportComment(commentId.toLongOrNull() ?: 0L, ReportReq(reason)) }
    }

    // 管理删除他人回复必须带理由（服务端强校验），作者自删 reason 传 null
    suspend fun deleteEmaReply(emaId: Int, replyId: String, reason: String? = null) {
        apiCall { Api.service.deleteEmaReply(emaId.toLong(), replyId.toLongOrNull() ?: 0L, dev.arisu.shrine.data.remote.DeleteReasonReq(reason ?: "")) }
    }

    suspend fun reportEma(emaId: Int, reason: String) {
        apiCall { Api.service.reportEma(emaId.toLong(), ReportReq(reason)) }
    }

    suspend fun commentsOf(postNo: String): List<Comment> =
        cached(cacheKey("comments", postNo), ListSerializer(Comment.serializer())) {
            apiCall { Api.service.comments(postKey(postNo)) }.items.map { it.toComment() }
        }

    suspend fun addComment(
        postNo: String,
        content: String,
        parentId: String? = null,
        replyToUserId: String? = null,
        imageUrl: String? = null,
    ): Comment =
        apiCall {
            Api.service.addComment(
                postKey(postNo),
                CommentReq(content, parentId?.toLongOrNull(), replyToUserId?.let { userKey(it) }, imageUrl),
            )
        }.toComment()

    suspend fun likeComment(commentId: String): Pair<Boolean, Int> {
        val r = apiCall { Api.service.toggleCommentLike(commentId.toLongOrNull() ?: 0L) }
        return r.liked to r.likeCount.toInt()
    }

    suspend fun deleteComment(commentId: String, reason: String? = null) {
        apiCall { Api.service.deleteComment(commentId.toLongOrNull() ?: 0L, DeleteReasonReq(reason ?: "")) }
    }

    /** 删除评论后同步缓存：服务端已连同子树软删，缓存里不能再留着旧回复 */
    suspend fun cacheComments(postNo: String, items: List<Comment>) {
        withContext(Dispatchers.IO) {
            CacheStore.put(scopedKey(cacheKey("comments", postNo)), ListSerializer(Comment.serializer()), items)
        }
    }

    /** 评论数变更后同步帖子缓存，避免重进详情时旧计数先闪一下 */
    suspend fun cachePost(postNo: String, post: Post) {
        withContext(Dispatchers.IO) {
            CacheStore.put(scopedKey(cacheKey("post", postNo)), Post.serializer(), post)
        }
    }

    // ── 神社祝部 ────────────────────────────────────────────

    @Serializable
    data class EmaPage(val items: List<Ema>, val total: Long, val page: Int)

    suspend fun emas(
        tags: List<String> = emptyList(),
        states: List<String> = emptyList(),
        sort: String = "new",
        page: Int = 1,
        pageSize: Int = 20,
    ): EmaPage {
        val wireTags = tags.joinToString(",")
        val wireStates = states.joinToString(",")
        return cached(
            kEmas(tags, states, sort, page, pageSize),
            EmaPage.serializer(),
            fetch = {
                val dto = apiCall {
                    Api.service.listEmas(wireTags.ifEmpty { null }, wireStates.ifEmpty { null }, sort, page, pageSize)
                }
                EmaPage(dto.items.map { it.toEma() }, dto.total, dto.page)
            },
            cacheIf = { it.items.isNotEmpty() },
        )
    }

    suspend fun ema(id: Int): Ema = cached(cacheKey("ema", id), Ema.serializer()) {
        apiCall { Api.service.getEma(id.toLong()) }.toEma()
    }

    suspend fun createEma(title: String, body: String, tags: List<EmaTag>): Ema =
        apiCall { Api.service.createEma(EmaReq(title, body, tags.map { it.wire })) }.toEma()

    suspend fun updateEma(id: Int, title: String?, body: String?, tags: List<EmaTag>?): Ema =
        apiCall {
            Api.service.updateEma(id.toLong(), EmaPatchReq(title, body, tags?.map { it.wire }))
        }.toEma()

    suspend fun deleteEma(id: Int, reason: String? = null) {
        apiCall { Api.service.deleteEma(id.toLong(), DeleteReasonReq(reason ?: "")) }
    }

    suspend fun updateEmaState(id: Int, state: EmaState) {
        apiCall { Api.service.setEmaState(id.toLong(), EmaStateReq(state.wire)) }
    }

    suspend fun emaReplies(id: Int): List<EmaReply> =
        cached(cacheKey("emaReplies", id), ListSerializer(EmaReply.serializer())) {
            apiCall { Api.service.emaReplies(id.toLong()) }.items.map { it.toEmaReply() }
        }

    suspend fun addEmaReply(id: Int, content: String): EmaReply =
        apiCall { Api.service.addEmaReply(id.toLong(), EmaReplyReq(content)) }.toEmaReply()

    // ── 奉仕会 ──────────────────────────────────────────────

    @Serializable
    data class StaffGroup(val role: Role, val roleName: String, val members: List<User>)

    suspend fun staffGroups(): List<StaffGroup> =
        cached(cacheKey("staff"), ListSerializer(StaffGroup.serializer())) {
            apiCall { Api.service.staff() }.groups.map { g ->
                StaffGroup(
                    role = g.role.toRole() ?: Role.MEMBER,
                    roleName = g.roleName,
                    members = g.members.map { m ->
                        User(
                            id = m.no, nickname = m.nickname, role = m.role.toRole() ?: Role.MEMBER,
                            signature = m.signature, joinedAt = TimeFmt.epoch(m.joinedAt),
                            fixedAvatarSeed = stableSeed(m.no), fixedAvatarUrl = m.avatar.ifEmpty { null },
                        )
                    },
                )
            }
        }

    data class MyApplication(val id: Long, val status: String, val createdAt: Long)

    suspend fun applyStaff(reason: String, weeklyHours: String) {
        apiCall { Api.service.applyStaff(StaffApplyReq(reason, weeklyHours)) }
    }

    /** 我最近一次临时奉仕申请（含状态与时间，用于冷却判断） */
    suspend fun myStaffApplication(): MyApplicationRes = try {
        apiCall { Api.service.myStaffApplication() }
    } catch (e: Exception) {
        MyApplicationRes()
    }

    suspend fun pendingApplications(): List<ApplicationDto> =
        apiCall { Api.service.pendingApplications() }.items

    suspend fun decideApplication(id: Long, approve: Boolean, reason: String) {
        apiCall { Api.service.decideApplication(id, DecideReq(approve, reason)) }
    }

    // ── 社务箱 ──────────────────────────────────────────────

    @Serializable
    data class NoticePage(val items: List<Notice>, val unread: Int, val total: Long)

    /** only："" 全部 / "unread" 未读 / "starred" 收藏 */
    suspend fun notices(category: String = "all", only: String = "", page: Int = 1, pageSize: Int = 30): NoticePage =
        cached(
            kNotices(category, only, page, pageSize),
            NoticePage.serializer(),
            fetch = {
                val dto = apiCall { Api.service.notices(category, only, page, pageSize) }
                // 列表自带未读数：顺手把角标同步过来。少一次单独请求，也就少一次
                // 「网络抖了一下、红点没消」的观感问题（用户反馈要看几遍才消）
                _unreadCount.value = dto.unread.toInt()
                NoticePage(dto.items.map { it.toNotice() }, dto.unread.toInt(), dto.total)
            },
            cacheIf = { it.items.isNotEmpty() },
        )

    suspend fun refreshUnread(): Int = try {
        apiCall { Api.service.unread() }.unread.toInt().also { _unreadCount.value = it }
    } catch (_: Exception) {
        _unreadCount.value
    }

    suspend fun markRead(id: String) {
        apiCall { Api.service.readNotice(id.toLongOrNull() ?: 0L) }
        refreshUnread()
    }

    suspend fun markUnread(id: String) {
        apiCall { Api.service.markNoticeUnread(id.toLongOrNull() ?: 0L) }
        refreshUnread()
    }

    suspend fun starNotice(id: String, starred: Boolean) {
        apiCall { Api.service.starNotice(id.toLongOrNull() ?: 0L, NoticeStarReq(starred)) }
    }

    /** 单条通知：详情页进程回收后按 id 重取；失败由调用方兜底 */
    suspend fun notice(id: String): Notice? {
        val dto = id.toLongOrNull() ?: return null
        return apiCall { Api.service.notice(dto) }.toNotice()
    }

    suspend fun markAllRead() {
        apiCall { Api.service.readAllNotices() }
        _unreadCount.value = 0
    }

    suspend fun deleteNotice(id: String) {
        apiCall { Api.service.deleteNotice(id.toLongOrNull() ?: 0L) }
        refreshUnread()
    }

    // ── 收藏 ────────────────────────────────────────────────

    suspend fun deleteCollection(id: String) {
        apiCall { Api.service.deleteCollection(id.toLongOrNull() ?: 0L) }
    }

    suspend fun collections(): List<Collection> =
        cached(cacheKey("collections"), ListSerializer(Collection.serializer())) {
            apiCall { Api.service.myCollections() }.items.map {
            Collection(
                id = it.id.toString(), name = it.name, count = it.count.toInt(),
                coverSeed = stableSeed(it.id.toString() + it.name),
                isDefault = it.isDefault, coverUrl = it.coverUrl.ifEmpty { null },
            )
            }
        }

    suspend fun createCollection(name: String): Collection =
        apiCall { Api.service.newCollection(CollectionReq(name)) }.let {
            Collection(
                id = it.id.toString(), name = it.name, count = 0,
                coverSeed = stableSeed(it.id.toString() + it.name),
                isDefault = it.isDefault, coverUrl = null,
            )
        }

    // ── 关注 ────────────────────────────────────────────────

    suspend fun followingUsers(): List<User> =
        cached(cacheKey("following"), ListSerializer(User.serializer())) {
            apiCall { Api.service.myFollowing() }.items.map { it.toUserBrief() }
        }

    suspend fun followerUsers(): List<User> =
        cached(cacheKey("followers"), ListSerializer(User.serializer())) {
            apiCall { Api.service.myFollowers() }.items.map { it.toUserBrief() }
        }

    @Serializable
    data class UserDetail(
        val user: User,
        val following: Boolean,
        val followsMe: Boolean,
        val muted: Boolean,
        val blocked: Boolean = false,
        val banned: Boolean = false,
    )

    suspend fun user(id: String): UserDetail =
        cached(cacheKey("user", id), UserDetail.serializer()) {
            val dto = apiCall { Api.service.userDetail(userKey(id)) }
            val user = dto.user.toUser().copy(
                following = dto.following,
                followsMe = dto.followsMe,
            )
            UserDetail(user, dto.following, dto.followsMe, dto.muted, dto.blocked, dto.banned)
        }

    /** 拉黑：服务端从此过滤对方的内容与通知。本地缓存一并清掉，列表重新取就是过滤后的版本 */
    suspend fun blockUser(id: String) {
        apiCall { Api.service.blockUser(userKey(id)) }
        clearLocalCache()
    }

    suspend fun unblockUser(id: String) {
        apiCall { Api.service.unblockUser(userKey(id)) }
        clearLocalCache()
    }

    /** 我拉黑的人（只给本人看） */
    suspend fun blockedUsers(): List<User> =
        apiCall { Api.service.myBlocks() }.items.map { it.toUserBrief() }

    suspend fun toggleFollow(id: String): Boolean {
        val r = apiCall { Api.service.follow(userKey(id)) }
        return r.following
    }

    suspend fun unfollow(id: String): Boolean {
        val r = apiCall { Api.service.unfollow(userKey(id)) }
        return r.following
    }

    suspend fun warnUser(id: String, content: String) {
        apiCall { Api.service.warnUser(userKey(id), dev.arisu.shrine.data.remote.WarnReq(content)) }
    }

    suspend fun muteUser(id: String, hours: Int, reason: String) {
        apiCall { Api.service.muteUser(userKey(id), MuteReq(hours, reason)) }
    }

    suspend fun banUser(id: String, reason: String) {
        apiCall { Api.service.banUser(userKey(id), dev.arisu.shrine.data.remote.BanReq(reason)) }
    }

    // 解禁/解封幂等：对没被禁言/封禁的人调用同样成功
    suspend fun unmuteUser(id: String) {
        apiCall { Api.service.unmuteUser(userKey(id)) }
    }

    suspend fun unbanUser(id: String) {
        apiCall { Api.service.unbanUser(userKey(id)) }
    }

    suspend fun setRole(id: String, role: Role) {
        apiCall { Api.service.setRole(userKey(id), dev.arisu.shrine.data.remote.RoleReq(role.wire)) }
    }

    suspend fun reportUser(id: String, reason: String) {
        apiCall { Api.service.reportUser(userKey(id), ReportReq(reason)) }
    }

    // ── 个人页 ──────────────────────────────────────────────

    suspend fun tiles(userId: String? = null): List<TileDay> =
        cached(cacheKey("tiles", userId ?: "me"), ListSerializer(TileDay.serializer())) {
            val res = if (userId == null) {
                apiCall { Api.service.myRoom() }.tiles
            } else {
                apiCall { Api.service.userTiles(userKey(userId)) }.tiles
            }
            res.map { TileDay(date = it.date, level = it.score) }
        }

    @Serializable
    data class Room(
        val me: User,
        val tiles: List<TileDay>,
        val todayMinutes: Int,
        val todayPosts: Int,
        val todayComments: Int,
        val achievements: List<Achievement>,
    )

    suspend fun myRoom(): Room = cached(cacheKey("myRoom"), Room.serializer()) {
        val dto = apiCall { Api.service.myRoom() }
        _me.value = dto.me.toUser()
        val unlocked = dto.achievements.map { it.code }.toSet()
        Room(
            me = dto.me.toUser(),
            tiles = dto.tiles.map { TileDay(date = it.date, level = it.score) },
            todayMinutes = dto.today.minutes,
            todayPosts = dto.today.posts,
            todayComments = dto.today.comments,
            achievements = achievementCatalog.map { (code, a) ->
                a.copy(unlocked = code in unlocked)
            },
        )
    }

    suspend fun postsByAuthor(userId: String, tab: String = "posts", collectionId: Long? = null): List<Post> =
        if (userId == _me.value?.id) {
            cached(cacheKey("authorPosts", userId, tab, collectionId), ListSerializer(Post.serializer())) {
                apiCall { Api.service.myPosts(tab, collectionId) }.items.map { it.toPost() }
            }
        } else {
            cached(cacheKey("authorPosts", userId, tab), ListSerializer(Post.serializer())) {
                apiCall { Api.service.userPosts(userKey(userId), tab) }.items.map { it.toPost() }
            }
        }

    // ── 编辑资料 ────────────────────────────────────────────

    suspend fun updateMe(patch: ProfilePatchReq): User {
        val meDto = apiCall { Api.service.patchMe(patch) }
        val user = meDto.toUser()
        _me.value = user
        return user
    }

    // ── 搜索 ────────────────────────────────────────────────

    data class SearchResult(val posts: List<Post>, val users: List<User>)

    suspend fun search(q: String, regex: Boolean): SearchResult {
        val dto = apiCall { Api.service.search(q, if (regex) "1" else "0") }
        return SearchResult(
            posts = dto.posts.map { it.toPost() },
            users = dto.users.map { it.toUserBrief() },
        )
    }

    /** 提及补全：只查社员，不走带 2 秒限频的全站搜索 */
    suspend fun searchMentionUsers(q: String): List<User> =
        apiCall { Api.service.searchUsers(q) }.users.map { it.toUserBrief() }

    // ── 游会 ────────────────────────────────────────────────

    @Serializable
    data class OnlineStats(val onlineNow: Int, val registered: Int, val todayPeak: Int, val todayMinutes: Int, val totalMinutes: Int)

    // 每日运势：幸运值 1-100（档位由客户端映射展示）
    suspend fun drawOmikuji(): Int =
        apiCall { Api.service.drawOmikuji() }.luck.also { rememberOmikujiToday(it) }

    /**
     * 今日是否已求签（只查不抽，进页判断用）；返回 0 表示今日未抽
     * （幸运值从 1 起，0 不会与真值撞）。
     *
     * 结果按东八区日切落盘：进页先摆上次的结果，弱网/断网也不会把
     * 「已求签」显示成可点的「今日求签」。跨天自动换键，昨天的签不会冒充今天。
     */
    suspend fun omikujiToday(): Int =
        cached(cacheKey("omikuji", omikujiDayKey()), Int.serializer()) {
            apiCall { Api.service.omikujiToday() }.let { if (it.drawn) it.luck else 0 }
        }

    /** 抽到签后写回与 omikujiToday 同一个键，当天再进游会直接摆出结果 */
    private suspend fun rememberOmikujiToday(luck: Int) = withContext(Dispatchers.IO) {
        // 键必须与 omikujiToday 的 cached() 一致（scopedKey 带用户前缀），
        // 少一层前缀就会写成另一份，peek 与断网回读都看不见
        CacheStore.put(scopedKey(cacheKey("omikuji", omikujiDayKey())), Int.serializer(), luck)
    }

    /** 连续求签天数：历史接口最多 30 条，超过 30 天按 30 天算 */
    suspend fun omikujiStreak(): Int {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val days = apiCall { Api.service.omikujiHistory() }.items.mapNotNull { item ->
            if (item.drawnAt <= 0) {
                null
            } else {
                java.time.Instant.ofEpochSecond(item.drawnAt).atZone(zone).toLocalDate()
            }
        }
        return streakDays(days, java.time.LocalDate.now(zone))
    }

    /** 今日御签榜：前 10 名次 + 自己前后各 2 人；按日键落盘，进页先摆上次的榜 */
    suspend fun omikujiLeaderboard(): OmikujiBoard =
        cached(
            // 结构从「纯列表」扩成榜 + 小榜，窗口又从 11 人收到 5 人并允许与前十重复；
            // 换新键避免旧缓存按新结构解析失败或短暂摆出旧宽度的小榜
            cacheKey("omikujiBoard4", omikujiDayKey()),
            OmikujiBoard.serializer(),
            cacheIf = { it.items.isNotEmpty() || it.nearby.isNotEmpty() },
        ) {
            val res = apiCall { Api.service.omikujiLeaderboard() }
            OmikujiBoard(
                items = res.items.map { it.toLeaderRow() },
                nearby = res.nearby.map { it.toLeaderRow() },
                me = res.me?.let { OmikujiStanding(it.rank, it.luck) },
            )
        }

    fun peekOmikujiLeaderboard(): OmikujiBoard? =
        peekCache(cacheKey("omikujiBoard4", omikujiDayKey()), OmikujiBoard.serializer())

    suspend fun onlineStats(): OnlineStats =
        cached(cacheKey("stats"), OnlineStats.serializer()) {
            apiCall { Api.service.onlineStats() }.let {
                OnlineStats(it.onlineNow.toInt(), it.registered.toInt(), it.todayPeak.toInt(), it.todayMinutes.toInt(), it.totalMinutes.toInt())
            }
        }

    // ── 神社日志 ────────────────────────────────────────────

    suspend fun releases(): List<ReleaseNote> =
        cached(cacheKey("releases"), ListSerializer(ReleaseNote.serializer())) {
            apiCall { Api.service.releases() }.items.mapIndexed { i, dto ->
                val kind = when (dto.changeType) {
                    "fix" -> ChangeKind.FIX
                    "improvement" -> ChangeKind.IMPROVE
                    "breaking" -> ChangeKind.BREAKING
                    else -> ChangeKind.FEATURE
                }
                ReleaseNote(
                    version = dto.version,
                    channel = if (dto.channel == "beta") ReleaseChannel.BETA else ReleaseChannel.STABLE,
                    publishedAt = TimeFmt.epoch(dto.createdAt),
                    kinds = listOf(kind),
                    body = dto.body,
                    latest = i == 0,
                )
            }
        }

    suspend fun checkUpdate(version: String, channel: String = "stable"): dev.arisu.shrine.data.remote.UpdateCheckDto =
        apiCall { Api.service.checkUpdate(version, channel) }

    suspend fun createRelease(version: String, channel: String, changeType: String, body: String) {
        apiCall { Api.service.createRelease(ReleaseReq(version, channel, changeType, body)) }
    }

    // ── 邮箱换绑 ────────────────────────────────────────────

    suspend fun requestEmailChange(newEmail: String) {
        apiCall { Api.service.emailChangeRequest(dev.arisu.shrine.data.remote.EmailChangeReq(newEmail)) }
    }

    suspend fun changeEmail(newEmail: String, code: String): User {
        // 自报当前会话：服务端换绑成功后吊销其余设备的会话、保留本机在线
        val meDto = apiCall {
            Api.service.emailChange(
                session.currentRefresh(),
                dev.arisu.shrine.data.remote.EmailChangeReq(newEmail, code),
            )
        }
        val user = meDto.toUser()
        _me.value = user
        return user
    }

    // ── 导出 ────────────────────────────────────────────────

    suspend fun exportPostsMarkdown(): String = withContext(Dispatchers.IO) {
        val client = Api.client
        val request = okhttp3.Request.Builder()
            .url(_baseUrl.value + "/api/me/export")
            .header("Authorization", "Bearer " + Api.currentAccess)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiError("导出失败（HTTP ${resp.code}）")
            resp.body!!.string()
        }
    }

    // ── 上传 ────────────────────────────────────────────────

    /**
     * 相册/相机选图 → 整备（压缩/转正）→ 上传，返回图片 URL。
     * 拷贝、压缩、上传全是阻塞 IO，整体压在 IO 线程——此前拷贝散在各屏的主线程
     * launch 里，选大图会卡住界面；临时文件用完即删，不堆积缓存目录。
     */
    suspend fun uploadImageFromUri(
        context: Context,
        uri: android.net.Uri,
        onProgress: (Float) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "upload").apply { mkdirs() }
        val file = stagePickedImage(context, uri, dir)
        try {
            uploadImage(file, onProgress)
        } finally {
            file.delete()
        }
    }

    /**
     * 上传一张本地图。[onProgress] 传 0f–1f 的完成度，在 OkHttp 的 IO 线程回调
     * （调用方直接写界面状态即可）。
     */
    suspend fun uploadImage(file: File, onProgress: (Float) -> Unit = {}): String =
        withContext(Dispatchers.IO) {
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }.toMediaType()
            val body = ProgressRequestBody(file, mime) { sent, total ->
                onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
            }
            val part = MultipartBody.Part.createFormData("file", file.name, body)
            // 上传走放宽超时的通道：普通调用的 25s 总闸传不完手机拍的原图
            apiCall { Api.uploadService.upload(part) }.url
        }

    /** 「刚发过帖」旗标：发帖成功后立起，茶话列表据此回顶并重拉第一页 */
    private val _justPublished = MutableStateFlow(false)

    /** 取走旗标（一次性）：谁先看到谁负责回顶 */
    fun consumeJustPublished(): Boolean = _justPublished.value.also { if (it) _justPublished.value = false }

    // ── 表情包 ──────────────────────────────────────────────

    /** 表情夹上限，服务端下发（离线时用默认值，两个数一致） */
    @Volatile
    private var stickerLimitValue: Int = 64
    val stickerLimit: Int get() = stickerLimitValue

    /** 我的表情夹。与别的列表同一套落盘缓存：面板打开即时出图，断网也翻得到 */
    suspend fun myStickers(): List<Sticker> = cached(
        cacheKey("stickers"),
        ListSerializer(Sticker.serializer()),
        cacheIf = { it.isNotEmpty() },
    ) {
        val res = apiCall { Api.service.myStickers() }
        if (res.limit > 0) stickerLimitValue = res.limit
        res.items.map { it.toSticker() }
    }

    fun peekStickers(): List<Sticker>? =
        peekCache(cacheKey("stickers"), ListSerializer(Sticker.serializer()))

    /** 收一张表情。新收的并进本地缓存，面板与发送页不用再等一次网络往返 */
    suspend fun addSticker(url: String): Sticker {
        val sticker = apiCall { Api.service.addSticker(StickerAddReq(url)) }.toSticker()
        writeStickerCache(listOf(sticker) + (peekStickers() ?: emptyList()).filterNot { it.url == sticker.url })
        return sticker
    }

    suspend fun deleteSticker(id: String) {
        val numeric = id.toLongOrNull() ?: return
        apiCall { Api.service.deleteSticker(numeric) }
        writeStickerCache((peekStickers() ?: emptyList()).filterNot { it.id == id })
    }

    /** 拖拽排序落库：按传入顺序重写序号，成功后同步本地缓存 */
    suspend fun reorderStickers(ordered: List<Sticker>) {
        val ids = ordered.mapNotNull { it.id.toLongOrNull() }
        if (ids.isEmpty()) return
        apiCall { Api.service.reorderStickers(StickerReorderReq(ids)) }
        writeStickerCache(ordered)
    }

    private suspend fun writeStickerCache(list: List<Sticker>) = withContext(Dispatchers.IO) {
        // 删空也要落盘：留着旧列表会让面板把已删的表情再画出来
        CacheStore.put(scopedKey(cacheKey("stickers")), ListSerializer(Sticker.serializer()), list)
    }

    // ── 成就目录（产品外壳文案，解锁状态来自服务器） ─────────

    val achievementCatalog: List<Pair<String, Achievement>> = listOf(
        "founder" to Achievement("神社出仕", "前 100 位入社的社员"),
        "first_post" to Achievement("第一帖", "发出了第一篇帖子"),
        "hot_author" to Achievement("捧帖", "单帖顶数达到 20"),
        "first_ema" to Achievement("挂绘马", "提交第一个绘马"),
        "omikuji" to Achievement("上上签", "第一次求签求到 100 分"),
        "chatter" to Achievement("社务常客", "评论数达到 50"),
        "veteran" to Achievement("参拜者", "入社满 100 天"),
        "night_owl" to Achievement("夜行性", "凌晨三点还在神社"),
    )

    fun unlockedAchievementCount(list: List<Achievement>): Int = list.count { it.unlocked }
}

// ── DTO → 界面模型映射 ──────────────────────────────────────

private val PostSort.wire: String
    get() = when (this) {
        PostSort.LATEST -> "new"
        PostSort.HOTTEST -> "hot"
        PostSort.ESSENCE -> "essence"
    }

private val Board.wire: String
    get() = when (this) {
        Board.MAIN -> "main"
        Board.GENSOKYO -> "gensokyo"
        Board.DEV -> "dev"
    }

private val EmaTag.wire: String
    get() = when (this) {
        EmaTag.ENHANCE -> "enhance"
        EmaTag.BUG -> "bug"
        EmaTag.QUESTIONS -> "questions"
        EmaTag.TIPS -> "tips"
    }

private val EmaState.wire: String
    get() = when (this) {
        EmaState.OPEN -> "open"
        EmaState.IN_PROGRESS -> "in_progress"
        EmaState.RESOLVED -> "resolved"
        EmaState.CLOSED -> "closed"
    }

private val Role.wire: String
    get() = when (this) {
        Role.KANNUSHI -> "kannushi"
        Role.GUUJI -> "guuji"
        Role.HOUSHI -> "houshi"
        Role.TEMP_HOUSHI -> "temp_houshi"
        Role.MEMBER -> "member"
    }

private fun String.toRole(): Role? = when (this) {
    "kannushi" -> Role.KANNUSHI
    "guuji" -> Role.GUUJI
    "houshi" -> Role.HOUSHI
    "temp_houshi" -> Role.TEMP_HOUSHI
    "member" -> Role.MEMBER
    else -> null
}

private fun String.toBoard(): Board = when (this) {
    "gensokyo" -> Board.GENSOKYO
    "dev" -> Board.DEV
    else -> Board.MAIN
}

private fun String.toEmaTag(): EmaTag? = when (this) {
    "enhance" -> EmaTag.ENHANCE
    "bug" -> EmaTag.BUG
    "questions" -> EmaTag.QUESTIONS
    "tips" -> EmaTag.TIPS
    else -> null
}

private fun String.toEmaState(): EmaState = when (this) {
    "in_progress" -> EmaState.IN_PROGRESS
    "resolved" -> EmaState.RESOLVED
    "closed" -> EmaState.CLOSED
    else -> EmaState.OPEN
}

private fun String.toOmikuji(): OmikujiFortune = when (this) {
    "kichi" -> OmikujiFortune.KICHI
    "chuukichi" -> OmikujiFortune.CHUUKICHI
    "shoukichi" -> OmikujiFortune.SHOUKICHI
    "suekichi" -> OmikujiFortune.SUEKICHI
    "kyou" -> OmikujiFortune.KYOU
    else -> OmikujiFortune.DAIKICHI
}

/** 稳定种子：让没有头像 URL 的用户也有一个稳定的凹槽底色 */
private fun stableSeed(key: String): Int = key.hashCode()

private fun PostDto.toPost(): Post = Post(
    id = no,
    board = board.toBoard(),
    title = title,
    body = body,
    intro = intro,
    coverUrl = coverUrl.ifEmpty { null },
    authorId = author.no,
    authorName = author.nickname,
    authorRole = author.role.toRole() ?: Role.MEMBER,
    authorAvatar = author.avatar.ifEmpty { null },
    createdAt = TimeFmt.epoch(createdAt),
    commentCount = commentCount.toInt(),
    upCount = upCount.toInt(),
    saveCount = saveCount.toInt(),
    offerCount = offerTotal.toInt(),
    offered = offered,
    upvoted = upped,
    tracked = tracked,
    favorited = favorited,
    essence = essence,
    pinned = pinned,
    mentionNames = mentions?.associate { it.no to it.nickname } ?: emptyMap(),
)

private fun CommentDto.toComment(): Comment = Comment(
    id = id.toString(),
    postId = "arisu_" + "%06d".format(postId),
    authorId = author.no,
    authorName = author.nickname,
    authorRole = author.role.toRole() ?: Role.MEMBER,
    authorAvatar = author.avatar.ifEmpty { null },
    content = content,
    imageUrl = imageUrl.ifEmpty { null },
    createdAt = TimeFmt.epoch(createdAt),
    likeCount = likeCount.toInt(),
    liked = liked,
    parentId = parentId?.toString(),
    replyToNickname = replyToUser?.nickname,
    mine = mine,
    mentionNames = mentions?.associate { it.no to it.nickname } ?: emptyMap(),
)

private fun dev.arisu.shrine.data.remote.EmaDto.toEma(): Ema = Ema(
    id = id.toInt(),
    title = title,
    body = body,
    authorId = author.no,
    authorName = author.nickname,
    authorRole = author.role.toRole() ?: Role.MEMBER,
    authorAvatar = author.avatar.ifEmpty { null },
    tags = tags.mapNotNull { it.toEmaTag() },
    state = state.toEmaState(),
    createdAt = TimeFmt.epoch(createdAt),
    replyCount = replyCount.toInt(),
)

private fun dev.arisu.shrine.data.remote.EmaReplyDto.toEmaReply(): EmaReply = EmaReply(
    id = id.toString(),
    emaId = emaId.toInt(),
    authorId = author.no,
    authorName = author.nickname,
    authorRole = author.role.toRole() ?: Role.MEMBER,
    authorAvatar = author.avatar.ifEmpty { null },
    content = content,
    createdAt = TimeFmt.epoch(createdAt),
)

private fun NoticeKindOf(kind: String): NoticeKind = when (kind) {
    "reply" -> NoticeKind.REPLY
    "report" -> NoticeKind.REPORT
    "application" -> NoticeKind.APPLICATION
    "tracking" -> NoticeKind.TRACKING
    "follow" -> NoticeKind.FOLLOW
    "warn" -> NoticeKind.WARN
    "mute" -> NoticeKind.MUTE
    "up" -> NoticeKind.UP
    "offer" -> NoticeKind.OFFER
    else -> NoticeKind.SYSTEM
}

private fun dev.arisu.shrine.data.remote.NoticeDto.toNotice(): Notice = Notice(
    id = id.toString(),
    kind = NoticeKindOf(kind),
    title = "",
    summary = content,
    createdAt = TimeFmt.epoch(createdAt),
    read = read,
    starred = starred,
    status = null,
    targetRoute = targetRoute.ifEmpty { null },
)

private fun dev.arisu.shrine.data.remote.StickerDto.toSticker(): Sticker =
    Sticker(id = id.toString(), url = url, animated = animated)

private fun MeDto.toUser(): User = User(
    id = no,
    nickname = nickname,
    role = role.toRole() ?: Role.MEMBER,
    signature = signature,
    statusEmoji = statusEmoji.ifEmpty { null },
    statusText = statusText.ifEmpty { null },
    fixedAvatarSeed = stableSeed(no),
    // 服务器在 me 里下发的是通用 avatar 字段，漏映射会让登录态头像掉回剪影
    fixedAvatarUrl = fixedAvatarUrl.ifEmpty { avatar }.ifEmpty { null },
    activeAvatarUrl = activeAvatarUrl.ifEmpty { null },
    activeAvatarEnabled = activeAvatarEnabled,
    joinedAt = TimeFmt.epoch(joinedAt),
    email = email,
    blogUrl = blogUrl,
    customMarkdown = customMarkdown,
    customCss = customCss,
    followingCount = followingCount.toInt(),
    followerCount = followerCount.toInt(),
    onlineHours = TimeFmt.hours(onlineMinutes),
    postCount = postCount.toInt(),
    balanceTenths = balanceTenths,
    showFields = showFields,
    notifyScope = notifyScope,
    achievements = emptyList(),
)

private fun UserBriefDto.toUserBrief(): User = User(
    id = no,
    nickname = nickname,
    role = role.toRole() ?: Role.MEMBER,
    fixedAvatarSeed = stableSeed(no),
    fixedAvatarUrl = avatar.ifEmpty { null },
    following = following,
)

private fun OmikujiLeaderDto.toLeaderRow(): OmikujiLeaderRow = OmikujiLeaderRow(
    nickname = nickname,
    role = role.toRole() ?: Role.MEMBER,
    avatar = avatar.ifEmpty { null },
    luck = luck,
    rank = rank,
    isMe = isMe,
)

private fun UserDto.toUser(): User = User(
    id = no,
    nickname = nickname,
    role = role.toRole() ?: Role.MEMBER,
    signature = signature,
    statusEmoji = statusEmoji.ifEmpty { null },
    statusText = statusText.ifEmpty { null },
    fixedAvatarSeed = stableSeed(no),
    fixedAvatarUrl = avatar.ifEmpty { null },
    joinedAt = TimeFmt.epoch(joinedAt),
    blogUrl = blogUrl,
    customMarkdown = customMarkdown,
    customCss = customCss,
    followingCount = followingCount.toInt(),
    followerCount = followerCount.toInt(),
    onlineHours = TimeFmt.hours(onlineMinutes),
    postCount = postCount.toInt(),
    balanceTenths = balanceTenths,
)

