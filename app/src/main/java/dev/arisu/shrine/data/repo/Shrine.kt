package dev.arisu.shrine.data.repo

import android.content.Context
import dev.arisu.shrine.core.util.TimeFmt
import dev.arisu.shrine.data.AccountVault
import dev.arisu.shrine.data.SessionStore
import dev.arisu.shrine.data.VaultAccount
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
import dev.arisu.shrine.data.model.OmikujiDraw
import dev.arisu.shrine.data.model.OmikujiLeaderRow
import dev.arisu.shrine.data.model.OmikujiFortune
import dev.arisu.shrine.data.model.Post
import dev.arisu.shrine.data.model.PostSort
import dev.arisu.shrine.data.model.ReleaseChannel
import dev.arisu.shrine.data.model.ReleaseNote
import dev.arisu.shrine.data.model.Role
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
import dev.arisu.shrine.data.remote.NoticeStarReq
import dev.arisu.shrine.data.remote.PasswordPatchReq
import dev.arisu.shrine.data.remote.PostDto
import dev.arisu.shrine.data.remote.PostPatchReq
import dev.arisu.shrine.data.remote.PostReq
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
import dev.arisu.shrine.data.remote.UserBriefDto
import dev.arisu.shrine.data.remote.UserDto
import dev.arisu.shrine.data.remote.apiCall
import dev.arisu.shrine.data.remote.toApiMessage
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

sealed interface SessionState {
    data object Booting : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val me: User) : SessionState
}

object Shrine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var session: SessionStore
    private lateinit var vault: AccountVault

    val vaultAccounts: StateFlow<List<VaultAccount>> by lazy {
        vault.accounts.stateIn(scope, SharingStarted.Eagerly, emptyList())
    }

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Booting)
    val sessionState: StateFlow<SessionState> = _sessionState

    private val _me = MutableStateFlow<User?>(null)
    val me: StateFlow<User?> = _me

    fun seedForTest(user: User?) {
        _me.value = user
        if (user != null) _sessionState.value = SessionState.LoggedIn(user)
    }

    private val _unreadCount = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unreadCount

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl

    fun init(context: Context) {
        session = SessionStore(context)
        vault = AccountVault(context)
        CacheStore.init(context)

        Api.onSessionRefreshed = { access, refresh ->
            session.saveTokens(access, refresh)
        }

        Api.onSessionDead = { scope.launch { toLoggedOut() } }
        scope.launch { bootstrap() }
        scope.launch { heartbeatLoop() }
    }

    private val _staleNotice = MutableStateFlow<String?>(null)
    val staleNotice: StateFlow<String?> = _staleNotice

    private fun cacheKey(vararg parts: Any?): String = parts.joinToString("|")

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
        val fullKey = cacheKey(_me.value?.id ?: "anon", key)
        return try {
            val fresh = fetch()

            if (cacheIf(fresh)) withContext(Dispatchers.IO) { CacheStore.put(fullKey, serializer, fresh) }
            _staleNotice.value = null
            fresh
        } catch (e: Exception) {
            if (e is ApiError || e !is IOException) throw e
            val snap = withContext(Dispatchers.IO) { CacheStore.get(fullKey, serializer) } ?: throw e
            _staleNotice.value = "网络不佳，展示的是 ${cacheAgo(snap.cachedAt)}的数据，刷新后更新"
            snap.value
        }
    }

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

            if (access.isNotEmpty()) {
                val meDto = apiCall { Api.service.me() }
                applyMe(meDto)
            } else {
                throw ApiError("no access")
            }
        } catch (_: Exception) {
            val refreshed = refreshTokens()
            if (refreshed) {
                try {
                    applyMe(apiCall { Api.service.me() })
                } catch (_: Exception) {
                    toLoggedOut()
                }
            } else {
                toLoggedOut()
            }
        }
    }

    private suspend fun applyMe(meDto: MeDto) {
        val user = meDto.toUser()
        _me.value = user
        _sessionState.value = SessionState.LoggedIn(user)
        refreshUnread()
    }

    private suspend fun toLoggedOut() {
        _me.value = null
        _unreadCount.value = 0
        _staleNotice.value = null
        _sessionState.value = SessionState.LoggedOut
    }

    private suspend fun installSession(base: String, access: String, refresh: String) {
        session.saveTokens(access, refresh)
        Api.updateSession(base, access, refresh)
    }

    private suspend fun refreshTokens(): Boolean {

        val result = Api.refreshAccess() ?: return false
        result.me?.let { applyMe(it) }
        return true
    }

    private suspend fun heartbeatLoop() {
        while (true) {
            if (_sessionState.value is SessionState.LoggedIn) {
                try {
                    apiCall { Api.service.heartbeat(HeartbeatReq(activeSeconds = 60)) }
                } catch (_: Exception) {

                }
            }
            delay(60 * 1000L)
        }
    }

    suspend fun saveBaseUrl(url: String) {
        val trimmed = url.trim().trimEnd('/')
        session.saveBaseUrl(trimmed)
        _baseUrl.value = trimmed

        session.clearSession()
        Api.updateSession(trimmed, "", "")

        CacheStore.clear()
    }

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

        val result = apiCall { Api.service.changePassword(PasswordPatchReq(old, new)) }
        if (result.accessToken.isNotEmpty()) {
            installSession(_baseUrl.value, result.accessToken, result.refreshToken)

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

    private fun clearLocalCache() {
        CacheStore.clear()
    }

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

    suspend fun switchAccount(target: VaultAccount): Boolean {
        val meNow = _me.value
        if (meNow != null && userKey(meNow.id) == target.userId) return true

        val oldBase = Api.currentBaseUrl.ifEmpty { _baseUrl.value }
        if (meNow != null) saveCurrentToVault()

        session.saveBaseUrl(target.baseUrl)
        installSession(target.baseUrl, target.access, target.refresh)
        clearLocalCache()

        val user = try {
            val result = Api.refreshAccess() ?: throw ApiError("登录已过期，请重新登录")
            result.me?.toUser()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
        if (user == null) {

            session.clearSession()
            session.saveBaseUrl(oldBase)
            Api.updateSession(oldBase, "", "")
            toLoggedOut()
            return false
        }
        _me.value = user
        _baseUrl.value = target.baseUrl

        runCatching { saveCurrentToVault(target.email) }
        _sessionState.value = SessionState.LoggedIn(user)

        runCatching { refreshUnread() }
        return true
    }

    suspend fun parkCurrentSession(): Boolean {
        if (_me.value == null) return false
        saveCurrentToVault()
        session.clearSession()
        Api.updateSession(_baseUrl.value, "", "")
        clearLocalCache()
        toLoggedOut()
        return true
    }

    suspend fun removeVaultAccount(account: VaultAccount) {
        runCatching { apiCall { Api.service.logout(LogoutReq(refreshToken = account.refresh)) } }
        vault.remove(account.userId)
    }

    suspend fun sessions(): List<SessionDto> =
        apiCall { Api.service.sessions(session.currentRefresh()) }.items

    suspend fun kickSession(id: Long) {
        apiCall { Api.service.kickSession(id) }
    }

    private fun postKey(no: String): Long = no.removePrefix("arisu_").toLongOrNull() ?: 0L
    private fun userKey(no: String): Long = no.removePrefix("User_").toLongOrNull() ?: 0L

    @Serializable
    data class PostPage(val items: List<Post>, val total: Long, val page: Int)

    suspend fun postsOf(board: Board, sort: PostSort, page: Int, pageSize: Int = 20): PostPage =
        cached(
            cacheKey("posts", board, sort, "p$page", "n$pageSize"),
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
        apiCall { Api.service.createPost(PostReq(board.wire, title, body, intro, cover)) }.toPost()

    suspend fun updatePost(id: String, title: String?, intro: String?, body: String?, board: Board? = null, cover: String? = null): Post =
        apiCall {
            Api.service.updatePost(
                postKey(id),

                PostPatchReq(title, body, intro, board?.wire, cover),
            )
        }.toPost()

    suspend fun deletePost(id: String, reason: String? = null) {
        apiCall { Api.service.deletePost(postKey(id), DeleteReasonReq(reason ?: "")) }
    }

    suspend fun toggleUp(id: String): Pair<Boolean, Int> {
        val r = apiCall { Api.service.toggleUp(postKey(id)) }
        return r.upped to r.upCount.toInt()
    }

    suspend fun toggleTrack(id: String): Boolean =
        apiCall { Api.service.toggleTrack(postKey(id)) }.tracked

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

    suspend fun addComment(postNo: String, content: String, parentId: String? = null, replyToUserId: String? = null): Comment =
        apiCall {
            Api.service.addComment(
                postKey(postNo),
                CommentReq(content, parentId?.toLongOrNull(), replyToUserId?.let { userKey(it) }),
            )
        }.toComment()

    suspend fun likeComment(commentId: String): Pair<Boolean, Int> {
        val r = apiCall { Api.service.toggleCommentLike(commentId.toLongOrNull() ?: 0L) }
        return r.liked to r.likeCount.toInt()
    }

    suspend fun deleteComment(commentId: String, reason: String? = null) {
        apiCall { Api.service.deleteComment(commentId.toLongOrNull() ?: 0L, DeleteReasonReq(reason ?: "")) }
    }

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
            cacheKey("emas", wireTags, wireStates, sort, "p$page", "n$pageSize"),
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

    @Serializable
    data class NoticePage(val items: List<Notice>, val unread: Int, val total: Long)

    suspend fun notices(category: String = "all", only: String = "", page: Int = 1, pageSize: Int = 30): NoticePage =
        cached(
            cacheKey("notices", category, only, "p$page", "n$pageSize"),
            NoticePage.serializer(),
            fetch = {
                val dto = apiCall { Api.service.notices(category, only, page, pageSize) }
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
    )

    suspend fun user(id: String): UserDetail =
        cached(cacheKey("user", id), UserDetail.serializer()) {
            val dto = apiCall { Api.service.userDetail(userKey(id)) }
            val user = dto.user.toUser().copy(
                following = dto.following,
                followsMe = dto.followsMe,
            )
            UserDetail(user, dto.following, dto.followsMe, dto.muted)
        }

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

    suspend fun updateMe(patch: ProfilePatchReq): User {
        val meDto = apiCall { Api.service.patchMe(patch) }
        val user = meDto.toUser()
        _me.value = user
        return user
    }

    data class SearchResult(val posts: List<Post>, val users: List<User>)

    suspend fun search(q: String, regex: Boolean): SearchResult {
        val dto = apiCall { Api.service.search(q, if (regex) "1" else "0") }
        return SearchResult(
            posts = dto.posts.map { it.toPost() },
            users = dto.users.map { it.toUserBrief() },
        )
    }

    @Serializable
    data class OnlineStats(val onlineNow: Int, val registered: Int, val todayPeak: Int, val todayMinutes: Int, val totalMinutes: Int)

    suspend fun drawOmikuji(): Int = apiCall { Api.service.drawOmikuji() }.luck

    suspend fun omikujiToday(): Pair<Boolean, Int> =
        apiCall { Api.service.omikujiToday() }.let { it.drawn to it.luck }

    suspend fun omikujiLeaderboard(): List<OmikujiLeaderRow> =
        apiCall { Api.service.omikujiLeaderboard() }.items.map {
            OmikujiLeaderRow(
                nickname = it.nickname,
                role = it.role.toRole() ?: Role.MEMBER,
                avatar = it.avatar.ifEmpty { null },
                luck = it.luck,
                rank = it.rank,
            )
        }

    suspend fun onlineStats(): OnlineStats =
        cached(cacheKey("stats"), OnlineStats.serializer()) {
            apiCall { Api.service.onlineStats() }.let {
                OnlineStats(it.onlineNow.toInt(), it.registered.toInt(), it.todayPeak.toInt(), it.todayMinutes.toInt(), it.totalMinutes.toInt())
            }
        }

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

    suspend fun requestEmailChange(newEmail: String) {
        apiCall { Api.service.emailChangeRequest(dev.arisu.shrine.data.remote.EmailChangeReq(newEmail)) }
    }

    suspend fun changeEmail(newEmail: String, code: String): User {

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

    suspend fun uploadImageFromUri(context: Context, uri: android.net.Uri): String =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "upload").apply { mkdirs() }

            val ext = when (context.contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val file = File(dir, "img_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("读取图片失败")
            try {
                uploadImage(file)
            } finally {
                file.delete()
            }
        }

    suspend fun uploadImage(file: File): String = withContext(Dispatchers.IO) {
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }.toMediaType()
        val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(mime))
        apiCall { Api.service.upload(part) }.url
    }

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
    upvoted = upped,
    tracked = tracked,
    favorited = favorited,
    essence = essence,
    pinned = pinned,
)

private fun CommentDto.toComment(): Comment = Comment(
    id = id.toString(),
    postId = "arisu_" + "%06d".format(postId),
    authorId = author.no,
    authorName = author.nickname,
    authorRole = author.role.toRole() ?: Role.MEMBER,
    authorAvatar = author.avatar.ifEmpty { null },
    content = content,
    createdAt = TimeFmt.epoch(createdAt),
    likeCount = likeCount.toInt(),
    liked = liked,
    parentId = parentId?.toString(),
    replyToNickname = replyToUser?.nickname,
    mine = mine,
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

private fun MeDto.toUser(): User = User(
    id = no,
    nickname = nickname,
    role = role.toRole() ?: Role.MEMBER,
    signature = signature,
    statusEmoji = statusEmoji.ifEmpty { null },
    statusText = statusText.ifEmpty { null },
    fixedAvatarSeed = stableSeed(no),

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
)
