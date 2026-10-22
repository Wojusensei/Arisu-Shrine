package dev.arisu.shrine.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector
import dev.arisu.shrine.core.components.ShrineIcons

enum class ArisuTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    CHAT("chat", "有栖茶话", Icons.Rounded.Forum),
    SHRINE("shrine", "神社祝部", ShrineIcons.Torii),
    PLAY("play", "神社游会", ShrineIcons.Koinobori),
    ME("me", "我的客房", Icons.Rounded.Person),
}

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"

    const val CHAT = "chat"
    const val POST_DETAIL = "post/{postId}"
    const val NEW_POST = "newPost"

    const val EDIT_POST = "editPost/{postId}"

    const val SHRINE = "shrine"
    const val EMA_DETAIL = "ema/{emaId}"
    const val NEW_EMA = "newEma"
    const val EDIT_EMA = "editEma/{emaId}"

    fun editEma(id: Int) = "editEma/$id"
    const val NOTICE_DETAIL = "noticeDetail?noticeId={noticeId}"

    const val REPORTS = "reports?focus={focus}"

    fun reports(focusId: String? = null) = if (focusId.isNullOrEmpty()) "reports?focus=" else "reports?focus=$focusId"
    const val CHANGELOG = "changelog"
    const val CHANGELOG_DETAIL = "changelog/{version}"

    const val PLAY = "play"
    const val ME = "me"
    const val EDIT_PROFILE = "editProfile"
    const val SETTINGS = "settings"
    const val ACCOUNT_MANAGE = "accountManage"
    const val SESSIONS = "sessions"
    const val BLOCKED = "blocked"
    const val COLLECTIONS = "collections"
    const val FOLLOW_LIST = "follow/{mode}"
    const val USER_PROFILE = "user/{userId}?tab={tab}"

    const val INBOX = "inbox"
    const val SEARCH = "search"

    fun postDetail(postId: String) = "post/$postId"
    fun editPost(postId: String) = "editPost/$postId"
    fun emaDetail(emaId: Int) = "ema/$emaId"
    fun changelogDetail(version: String) = "changelog/$version"
    fun followList(mode: String) = "follow/$mode"
    fun noticeDetail(noticeId: String) = "noticeDetail?noticeId=$noticeId"
    fun userProfile(userId: String, tab: Int = 0) =
        if (tab == 0) "user/$userId" else "user/$userId?tab=$tab"
}

const val FOLLOW_MODE_FOLLOWING = "following"
const val FOLLOW_MODE_FOLLOWERS = "followers"
