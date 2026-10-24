package dev.arisu.shrine.data.model

import kotlinx.serialization.Serializable

import androidx.compose.ui.graphics.Color
import dev.arisu.shrine.core.design.Gold
import dev.arisu.shrine.core.design.GoldSoft
import dev.arisu.shrine.core.design.Indigo
import dev.arisu.shrine.core.design.IndigoSoft
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.design.VermilionSoft
import dev.arisu.shrine.core.design.Celadon
import dev.arisu.shrine.core.design.CeladonSoft
import dev.arisu.shrine.core.design.StateClosed
import dev.arisu.shrine.core.design.StateClosedBg
import dev.arisu.shrine.core.design.StateOpen
import dev.arisu.shrine.core.design.StateOpenBg
import dev.arisu.shrine.core.design.StateProgress
import dev.arisu.shrine.core.design.StateProgressBg
import dev.arisu.shrine.core.design.StateResolved
import dev.arisu.shrine.core.design.StateResolvedBg
import dev.arisu.shrine.core.design.InkFaint

@Serializable
enum class Role(val label: String, val rank: Int) {
    KANNUSHI("神主", 5),
    GUUJI("宫司", 4),
    HOUSHI("奉仕", 3),
    TEMP_HOUSHI("临时奉仕", 2),
    MEMBER("社员", 1);

    val canModerate: Boolean get() = rank >= 3

    val canCurate: Boolean get() = rank >= 4

    val canAssignRoles: Boolean get() = rank >= 5
}

val Role.accent: Color
    get() = when (this) {
        Role.KANNUSHI -> Gold
        Role.GUUJI -> Vermilion
        Role.HOUSHI -> Indigo
        Role.TEMP_HOUSHI -> Indigo
        Role.MEMBER -> InkFaint
    }

fun roleFromWire(wire: String): Role = when (wire) {
    "kannushi" -> Role.KANNUSHI
    "guuji" -> Role.GUUJI
    "houshi" -> Role.HOUSHI
    "temp_houshi" -> Role.TEMP_HOUSHI
    else -> Role.MEMBER
}

val Role.accentSoft: Color
    get() = when (this) {
        Role.KANNUSHI -> GoldSoft
        Role.GUUJI -> VermilionSoft
        Role.HOUSHI -> IndigoSoft
        Role.TEMP_HOUSHI -> IndigoSoft
        Role.MEMBER -> StateClosedBg
    }

@Serializable
data class User(

    val id: String,
    val nickname: String,
    val role: Role,
    val signature: String = "",
    val bio: String = "",
    val statusEmoji: String? = null,
    val statusText: String? = null,

    val fixedAvatarSeed: Int = 0,

    val activeAvatarSeed: Int? = null,

    val activeAvatarEnabled: Boolean = true,
    val fixedAvatarUrl: String? = null,
    val activeAvatarUrl: String? = null,
    val joinedAt: String = "",
    val email: String = "",
    val blogUrl: String = "",
    val customMarkdown: String = "",
    val customCss: String = "",
    val followingCount: Int = 0,
    val followerCount: Int = 0,
    val onlineHours: Int = 0,
    val postCount: Int = 0,

    val balanceTenths: Long = 0,

    val showFields: Map<String, Boolean> = emptyMap(),

    val notifyScope: Map<String, Boolean> = emptyMap(),
    val following: Boolean = false,
    val followsMe: Boolean = false,
    val achievements: List<Achievement> = emptyList(),
)

fun countCn(n: Long): String = if (n < 10000) n.toString() else {
    val w = n / 10000.0
    val s = if (w >= 100) "%.0f".format(w) else "%.1f".format(w).removeSuffix(".0")
    s + "万"
}

fun Long.yenText(): String {
    val whole = this / 10
    val frac = this % 10
    return if (frac == 0L) whole.toString() else "$whole.$frac"
}

val User.displayAvatarSeed: Int
    get() = if (activeAvatarEnabled && activeAvatarSeed != null) activeAvatarSeed else fixedAvatarSeed

val User.avatarUrl: String?
    get() = if (activeAvatarEnabled && activeAvatarUrl != null) activeAvatarUrl else fixedAvatarUrl

@Serializable
data class Achievement(
    val name: String,
    val description: String,
    val unlocked: Boolean = true,
)

@Serializable
enum class Board(val label: String) {
    MAIN("正殿"),
    GENSOKYO("幻想乡闻"),
    DEV("/dev."),
}

@Serializable
enum class PostSort(val label: String) {
    LATEST("最新"),
    HOTTEST("最热"),
    ESSENCE("精华"),
}

@Serializable
data class Post(

    val id: String,
    val board: Board,
    val title: String,
    val body: String,

    val intro: String = "",

    val coverUrl: String? = null,
    val authorId: String,
    val authorName: String = "",
    val authorRole: Role = Role.MEMBER,
    val authorAvatar: String? = null,
    val createdAt: String,
    val commentCount: Int = 0,
    val upCount: Int = 0,

    val saveCount: Int = 0,

    val offerCount: Int = 0,
    val upvoted: Boolean = false,
    val tracked: Boolean = false,
    val favorited: Boolean = false,
    val essence: Boolean = false,
    val pinned: Boolean = false,

    val mentionNames: Map<String, String> = emptyMap(),
)

@Serializable
data class Comment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String = "",
    val authorRole: Role = Role.MEMBER,
    val authorAvatar: String? = null,
    val content: String,

    val imageUrl: String? = null,
    val createdAt: String,
    val likeCount: Int = 0,
    val liked: Boolean = false,

    val parentId: String? = null,

    val replyToNickname: String? = null,
    val mine: Boolean = false,

    val mentionNames: Map<String, String> = emptyMap(),
)

@Serializable
enum class EmaTag(val label: String, val color: Color, val background: Color) {
    ENHANCE("enhance", dev.arisu.shrine.core.design.TagEnhance, dev.arisu.shrine.core.design.TagEnhanceBg),
    BUG("bug", dev.arisu.shrine.core.design.TagBug, dev.arisu.shrine.core.design.TagBugBg),
    QUESTIONS("questions", dev.arisu.shrine.core.design.TagQuestions, dev.arisu.shrine.core.design.TagQuestionsBg),
    TIPS("tips", dev.arisu.shrine.core.design.TagTips, dev.arisu.shrine.core.design.TagTipsBg),
}

@Serializable
enum class EmaState(val label: String, val color: Color, val background: Color) {
    OPEN("open", StateOpen, StateOpenBg),
    IN_PROGRESS("in progress", StateProgress, StateProgressBg),
    RESOLVED("resolved", StateResolved, StateResolvedBg),
    CLOSED("closed", StateClosed, StateClosedBg),
}

@Serializable
data class Ema(

    val id: Int,
    val title: String,
    val body: String,
    val authorId: String,
    val authorName: String = "",
    val authorRole: Role = Role.MEMBER,
    val authorAvatar: String? = null,
    val tags: List<EmaTag>,
    val state: EmaState = EmaState.OPEN,
    val createdAt: String,
    val replyCount: Int = 0,
)

@Serializable
data class EmaReply(
    val id: String,
    val emaId: Int,
    val authorId: String,
    val authorName: String = "",
    val authorRole: Role = Role.MEMBER,
    val authorAvatar: String? = null,
    val content: String,
    val createdAt: String,
)

@Serializable
enum class ReleaseChannel(val label: String) {
    STABLE("稳定版"),
    BETA("测试版"),
}

@Serializable
enum class ChangeKind(val label: String, val color: Color, val background: Color) {
    FEATURE("新功能", Celadon, CeladonSoft),
    FIX("修复", Indigo, IndigoSoft),
    IMPROVE("优化", Gold, GoldSoft),
    BREAKING("破坏性变更", dev.arisu.shrine.core.design.TagBug, dev.arisu.shrine.core.design.TagBugBg),
}

@Serializable
data class ReleaseNote(
    val version: String,
    val channel: ReleaseChannel,
    val publishedAt: String,
    val kinds: List<ChangeKind>,
    val body: String,
    val latest: Boolean = false,
)

@Serializable
enum class NoticeKind(val label: String) {
    REPLY("回复/@"),
    UP("顶帖"),
    OFFER("奉纳"),
    SYSTEM("系统通知"),
    REPORT("举报动态"),
    APPLICATION("奉纳者申请"),
    TRACKING("追踪动态"),
    FOLLOW("关注动态"),
    WARN("警告"),
    MUTE("禁言"),
}

@Serializable
enum class NoticeStatus(val label: String) {
    PENDING("待处理"),
    DONE("已处理"),
    REJECTED("已驳回"),
}

@Serializable
data class Notice(
    val id: String,
    val kind: NoticeKind,
    val title: String,
    val summary: String,
    val createdAt: String,
    val read: Boolean = false,

    val starred: Boolean = false,
    val status: NoticeStatus? = null,

    val targetRoute: String? = null,
)

@Serializable
data class Sticker(
    val id: String,
    val url: String,
    val animated: Boolean = false,
)

@Serializable
data class Collection(
    val id: String,
    val name: String,
    val count: Int,
    val coverSeed: Int,
    val isDefault: Boolean = false,

    val coverUrl: String? = null,
    val contains: Boolean = false,
)

@Serializable
enum class ProfileTab(val label: String) {
    POSTED("发布的帖子"),
    TRACKED("追踪的帖子"),
    LIKED("喜欢的帖子"),
    FAVORITED("收藏的帖子"),
}

@Serializable
data class TileDay(
    val date: String,
    val level: Int,
)

@Serializable
enum class OmikujiFortune(val label: String) {
    DAIKICHI("大吉"),
    KICHI("吉"),
    CHUUKICHI("中吉"),
    SHOUKICHI("小吉"),
    SUEKICHI("末吉"),
    KYOU("凶"),
}

@Serializable
data class OmikujiDraw(
    val fortune: OmikujiFortune,
    val character: String,
    val series: String,
    val comment: String,
    val luckyItem: String,
    val drawnAt: String,
)

@Serializable
data class OmikujiLeaderRow(
    val nickname: String,
    val role: Role,
    val avatar: String? = null,
    val luck: Int,
    val rank: Int,
)
