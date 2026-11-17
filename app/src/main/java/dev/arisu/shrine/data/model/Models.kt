package dev.arisu.shrine.data.model

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable

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

/* ══════════════════════════════════════════════════════════════════
 * 身份体系
 * ══════════════════════════════════════════════════════════════════ */

@Serializable
enum class Role(val label: String, val rank: Int) {
    KANNUSHI("神主", 5),
    GUUJI("宫司", 4),
    HOUSHI("奉仕", 3),
    TEMP_HOUSHI("临时奉仕", 2),
    MEMBER("社员", 1);

    /** 奉仕及以上：可删他人帖、处理举报、警告（禁言/封禁需宫司+） */
    val canModerate: Boolean get() = rank >= 3

    /** 宫司及以上：可置顶/精华、封禁用户 */
    val canCurate: Boolean get() = rank >= 4

    /** 神主：可改用户角色 */
    val canAssignRoles: Boolean get() = rank >= 5
}

// 身份色全站统一一套：神主金、宫司朱红、奉仕与临时奉仕同蓝、社员灰（头像环、身份徽标共用）
val Role.accent: Color
    get() = when (this) {
        Role.KANNUSHI -> Gold
        Role.GUUJI -> Vermilion
        Role.HOUSHI -> Indigo
        Role.TEMP_HOUSHI -> Indigo
        Role.MEMBER -> InkFaint
    }

/** 服务端 wire 值（member / temp_houshi / …）→ Role；未知值一律按社员处理。 */
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

/* ══════════════════════════════════════════════════════════════════
 * 用户
 * ══════════════════════════════════════════════════════════════════ */

@Serializable
@Immutable
data class User(
    /** 社员编号，如 User_000001 */
    val id: String,
    val nickname: String,
    val role: Role,
    val signature: String = "",
    val bio: String = "",
    val statusEmoji: String? = null,
    val statusText: String? = null,
    /** 用于生成固定头像的色相种子 */
    val fixedAvatarSeed: Int = 0,
    /** 活跃头像：用哪个种子绘制；null 表示没有活跃头像 */
    val activeAvatarSeed: Int? = null,
    /** 是否启用活跃头像 */
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
    /** 账面（单位 0.1 円）：零头不丢地累积被奉纳收入 */
    val balanceTenths: Long = 0,
    /** 主页展示开关（键缺省视为展示） */
    val showFields: Map<String, Boolean> = emptyMap(),
    /** 通知范围 */
    val notifyScope: Map<String, Boolean> = emptyMap(),
    val following: Boolean = false,
    val followsMe: Boolean = false,
    val achievements: List<Achievement> = emptyList(),
)

/** 大数计数：过万压成「x.x万」，一格宽里放得下（234569 → "23.5万"、10000 → "1万"） */
fun countCn(n: Long): String = if (n < 10000) n.toString() else {
    val w = n / 10000.0
    val s = if (w >= 100) "%.0f".format(w) else "%.1f".format(w).removeSuffix(".0")
    s + "万"
}

/** 账面（0.1 円单位）转展示文：整十不带小数，否则保留一位（78 → "78"、783 → "78.3"） */
fun Long.yenText(): String {
    val whole = this / 10
    val frac = this % 10
    return if (frac == 0L) whole.toString() else "$whole.$frac"
}

/** 展示用头像种子：启用活跃头像且有活跃头像时用活跃头像，否则回退固定头像 */
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

/* ══════════════════════════════════════════════════════════════════
 * 有栖茶话
 * ══════════════════════════════════════════════════════════════════ */

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
@Immutable
data class Post(
    /** 帖子编号，如 arisu_000001 */
    val id: String,
    val board: Board,
    val title: String,
    val body: String,
    /** 介绍语：列表封面展示的一句话（选填） */
    val intro: String = "",
    /** 封面图；空则展示时回落作者头像 */
    val coverUrl: String? = null,
    val authorId: String,
    val authorName: String = "",
    val authorRole: Role = Role.MEMBER,
    val authorAvatar: String? = null,
    val createdAt: String,
    val commentCount: Int = 0,
    val upCount: Int = 0,
    /** 被收藏次数，列表与详情一并展示 */
    val saveCount: Int = 0,
    /** 被奉纳的赛钱总额（円） */
    val offerCount: Int = 0,
    /** 当前用户是否已给该帖奉纳过 */
    val offered: Boolean = false,
    val upvoted: Boolean = false,
    val tracked: Boolean = false,
    val favorited: Boolean = false,
    val essence: Boolean = false,
    val pinned: Boolean = false,
    /** 正文里被 @ 的社员：编号到昵称，渲染时用昵称顶替编号展示 */
    val mentionNames: Map<String, String> = emptyMap(),
)

/** 评论 / 回复。parentId 非空即为嵌套回复。 */
@Serializable
@Immutable
data class Comment(
    val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String = "",
    val authorRole: Role = Role.MEMBER,
    val authorAvatar: String? = null,
    val content: String,
    /** 评论附图（本站图床，空 = 纯文字） */
    val imageUrl: String? = null,
    val createdAt: String,
    val likeCount: Int = 0,
    val liked: Boolean = false,
    /** 被回复的评论 id */
    val parentId: String? = null,
    /** 被回复者昵称，用于「回复 @某某」 */
    val replyToNickname: String? = null,
    val mine: Boolean = false,
    /** 内容里被 @ 的社员：编号到昵称，渲染时用昵称顶替编号展示 */
    val mentionNames: Map<String, String> = emptyMap(),
)

/* ══════════════════════════════════════════════════════════════════
 * 神社祝部 · 绘马
 * ══════════════════════════════════════════════════════════════════ */

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
@Immutable
data class Ema(
    /** #1、#2 … */
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
@Immutable
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

/* ══════════════════════════════════════════════════════════════════
 * 神社日志
 * ══════════════════════════════════════════════════════════════════ */

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

/* ══════════════════════════════════════════════════════════════════
 * 社务箱
 * ══════════════════════════════════════════════════════════════════ */

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
@Immutable
data class Notice(
    val id: String,
    val kind: NoticeKind,
    val title: String,
    val summary: String,
    val createdAt: String,
    val read: Boolean = false,
    /** 已收藏（社务箱的收藏标记，区别于帖子收藏夹） */
    val starred: Boolean = false,
    val status: NoticeStatus? = null,
    /** 点击后跳转的目标路由 */
    val targetRoute: String? = null,
)

/* ══════════════════════════════════════════════════════════════════
 * 表情包
 * ══════════════════════════════════════════════════════════════════ */

/** 一张自留表情：存的是本站图床地址；[animated] 用来标注它会动 */
@Serializable
@Immutable
data class Sticker(
    val id: String,
    val url: String,
    val animated: Boolean = false,
)

/* ══════════════════════════════════════════════════════════════════
 * 收藏 / 关注
 * ══════════════════════════════════════════════════════════════════ */

@Serializable
@Immutable
data class Collection(
    val id: String,
    val name: String,
    val count: Int,
    val coverSeed: Int,
    val isDefault: Boolean = false,
    /** 收藏夹里最新一条帖子里的图片（网络加载，空则回退色块） */
    val coverUrl: String? = null,
    val contains: Boolean = false,
)

/* ══════════════════════════════════════════════════════════════════
 * 我的客房
 * ══════════════════════════════════════════════════════════════════ */

@Serializable
enum class ProfileTab(val label: String) {
    POSTED("发布的帖子"),
    TRACKED("追踪的帖子"),
    LIKED("喜欢的帖子"),
    FAVORITED("收藏的帖子"),
}

/** 参拜热度的一格 */
@Serializable
data class TileDay(
    val date: String,
    val level: Int,
)

/* ══════════════════════════════════════════════════════════════════
 * 神社游会 · 御神签
 * ══════════════════════════════════════════════════════════════════ */

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

/** 御签榜一行：rank 服务端算好（同分按求签先后严格排位），isMe 标记自己 */
@Serializable
@Immutable
data class OmikujiLeaderRow(
    val nickname: String,
    val role: Role,
    val avatar: String? = null,
    val luck: Int,
    val rank: Int,
    val isMe: Boolean = false,
)

/** 自己的今日名次；未求签时为 null */
@Serializable
@Immutable
data class OmikujiStanding(
    val rank: Int,
    val luck: Int,
)

/** 御签榜：前十名次 + 自己附近的名次；两端都为空时整块不渲染 */
@Serializable
@Immutable
data class OmikujiBoard(
    val items: List<OmikujiLeaderRow> = emptyList(),
    val nearby: List<OmikujiLeaderRow> = emptyList(),
    val me: OmikujiStanding? = null,
)
