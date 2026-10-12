package dev.arisu.shrine.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserBriefDto(
    val id: Long = 0,
    val no: String = "",
    val nickname: String = "",
    val role: String = "",
    val avatar: String = "",
    val following: Boolean = false,
)

@Serializable
data class UserDto(
    val id: Long = 0,
    val no: String = "",
    val nickname: String = "",
    val role: String = "",
    val avatar: String = "",
    val signature: String = "",
    @SerialName("status_emoji") val statusEmoji: String = "",
    @SerialName("status_text") val statusText: String = "",
    @SerialName("blog_url") val blogUrl: String = "",
    @SerialName("custom_markdown") val customMarkdown: String = "",
    @SerialName("custom_css") val customCss: String = "",
    @SerialName("joined_at") val joinedAt: Long = 0,
    @SerialName("online_minutes") val onlineMinutes: Long = 0,
    @SerialName("following_count") val followingCount: Long = 0,
    @SerialName("follower_count") val followerCount: Long = 0,
    @SerialName("post_count") val postCount: Long = 0,
)

@Serializable
data class MeDto(
    val id: Long = 0,
    val no: String = "",
    val nickname: String = "",
    val role: String = "",
    val avatar: String = "",
    val signature: String = "",
    @SerialName("status_emoji") val statusEmoji: String = "",
    @SerialName("status_text") val statusText: String = "",
    @SerialName("blog_url") val blogUrl: String = "",
    @SerialName("custom_markdown") val customMarkdown: String = "",
    @SerialName("custom_css") val customCss: String = "",
    @SerialName("joined_at") val joinedAt: Long = 0,
    @SerialName("online_minutes") val onlineMinutes: Long = 0,
    @SerialName("following_count") val followingCount: Long = 0,
    @SerialName("follower_count") val followerCount: Long = 0,
    @SerialName("post_count") val postCount: Long = 0,
    val email: String = "",
    @SerialName("fixed_avatar_url") val fixedAvatarUrl: String = "",
    @SerialName("active_avatar_url") val activeAvatarUrl: String = "",
    @SerialName("active_avatar_enabled") val activeAvatarEnabled: Boolean = true,
    @SerialName("show_fields") val showFields: Map<String, Boolean> = emptyMap(),
    @SerialName("notify_scope") val notifyScope: Map<String, Boolean> = emptyMap(),
    @SerialName("muted_until") val mutedUntil: Long? = null,
    @SerialName("deactivated_at") val deactivatedAt: Long? = null,
)

@Serializable
data class PostDto(
    val id: Long = 0,
    val no: String = "",
    val board: String = "main",
    val title: String = "",
    val body: String = "",
    val intro: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    val pinned: Boolean = false,
    val essence: Boolean = false,
    val author: UserBriefDto = UserBriefDto(),
    @SerialName("up_count") val upCount: Long = 0,
    @SerialName("comment_count") val commentCount: Long = 0,
    @SerialName("save_count") val saveCount: Long = 0,
    val upped: Boolean = false,
    val tracked: Boolean = false,
    val favorited: Boolean = false,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    val edited: Boolean = false,
)

@Serializable
data class PostPageDto(
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
    val total: Long = 0,
    val items: List<PostDto> = emptyList(),
)

@Serializable
data class CommentDto(
    val id: Long = 0,
    @SerialName("post_id") val postId: Long = 0,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("reply_to_user") val replyToUser: UserBriefDto? = null,
    val author: UserBriefDto = UserBriefDto(),
    val content: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("like_count") val likeCount: Long = 0,
    val liked: Boolean = false,
    val mine: Boolean = false,
    @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class EmaDto(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val state: String = "open",
    val author: UserBriefDto = UserBriefDto(),
    @SerialName("reply_count") val replyCount: Long = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class EmaPageDto(
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
    val total: Long = 0,
    val items: List<EmaDto> = emptyList(),
)

@Serializable
data class EmaReplyDto(
    val id: Long = 0,
    @SerialName("ema_id") val emaId: Long = 0,
    val author: UserBriefDto = UserBriefDto(),
    val content: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class CollectionDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("is_default") val isDefault: Boolean = false,
    val count: Long = 0,
    @SerialName("cover_url") val coverUrl: String = "",
)

@Serializable
data class NoticeDto(
    val id: Long = 0,
    val kind: String = "",
    val category: String = "",
    val content: String = "",
    @SerialName("target_route") val targetRoute: String = "",
    val read: Boolean = false,
    val starred: Boolean = false,
    @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class NoticePageDto(
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 30,
    val total: Long = 0,
    val unread: Long = 0,
    val items: List<NoticeDto> = emptyList(),
)

@Serializable
data class NoticeStarReq(val starred: Boolean)

@Serializable
data class ReleaseDto(
    val id: Long = 0,
    val version: String = "",
    val channel: String = "stable",
    @SerialName("change_type") val changeType: String = "feature",
    val body: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class OmikujiDto(
    val luck: Int = 0,
    val result: String = "",
    @SerialName("drawn_at") val drawnAt: Long = 0,
)

@Serializable
data class OnlineStatsDto(
    @SerialName("online_now") val onlineNow: Long = 0,
    val registered: Long = 0,
    @SerialName("today_peak") val todayPeak: Long = 0,
    @SerialName("today_minutes") val todayMinutes: Long = 0,
    @SerialName("total_minutes") val totalMinutes: Long = 0,
)

@Serializable
data class TileDto(
    val date: String = "",
    val score: Int = 0,
    val posts: Int = 0,
    val comments: Int = 0,
    val minutes: Int = 0,
)

@Serializable
data class StaffMemberDto(
    val id: Long = 0,
    val no: String = "",
    val nickname: String = "",
    val role: String = "",
    val avatar: String = "",
    val signature: String = "",
    @SerialName("joined_at") val joinedAt: Long = 0,
)

@Serializable
data class StaffGroupDto(
    val role: String = "",
    @SerialName("role_name") val roleName: String = "",
    val members: List<StaffMemberDto> = emptyList(),
)

@Serializable
data class AchievementDto(
    val code: String = "",
    @SerialName("unlocked_at") val unlockedAt: Long = 0,
)

@Serializable
data class AuthResultDto(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    val me: MeDto? = null,
)

@Serializable
data class RoomDto(
    val me: MeDto = MeDto(),
    val tiles: List<TileDto> = emptyList(),
    val today: TodayDto = TodayDto(),
    val online: OnlineStatsDto = OnlineStatsDto(),
    val achievements: List<AchievementDto> = emptyList(),
)

@Serializable
data class TodayDto(
    val minutes: Int = 0,
    val posts: Int = 0,
    val comments: Int = 0,
)

@Serializable
data class UserDetailDto(
    val user: UserDto = UserDto(),
    val following: Boolean = false,
    @SerialName("follows_me") val followsMe: Boolean = false,
    val muted: Boolean = false,
)

@Serializable
data class SearchResultDto(
    val posts: List<PostDto> = emptyList(),
    val users: List<UserBriefDto> = emptyList(),
)

@Serializable
data class UpdateCheckDto(
    @SerialName("update_available") val updateAvailable: Boolean = false,
    @SerialName("latest_version") val latestVersion: String? = null,
    val release: ReleaseDto? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
)

@Serializable
data class ReportDto(
    val id: Long = 0,
    val reporter: UserBriefDto = UserBriefDto(),
    @SerialName("post_id") val postId: Long? = null,
    @SerialName("comment_id") val commentId: Long? = null,
    @SerialName("ema_id") val emaId: Long? = null,
    val reason: String = "",
    val status: String = "pending",
    @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class SendCodeReq(val email: String, val purpose: String)

@Serializable
data class RegisterReq(val email: String, val code: String, val password: String, val nickname: String)

@Serializable
data class LoginReq(val email: String, val password: String)

@Serializable
data class ResetReq(val email: String, val code: String, val password: String)

@Serializable
data class RefreshReq(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class LogoutReq(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class PostReq(val board: String, val title: String, val body: String, val intro: String = "", val cover: String = "")

@Serializable
data class PostPatchReq(val title: String? = null, val body: String? = null, val intro: String? = null, val board: String? = null, val cover: String? = null)

@Serializable
data class DeleteReasonReq(val reason: String)

@Serializable
data class FavoriteReq(@SerialName("collection_id") val collectionId: Long? = null)

@Serializable
data class CommentReq(
    val content: String,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("reply_to_user_id") val replyToUserId: Long? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class EmaReq(val title: String, val body: String, val tags: List<String>)

@Serializable
data class EmaPatchReq(val title: String? = null, val body: String? = null, val tags: List<String>? = null)

@Serializable
data class EmaStateReq(val state: String)

@Serializable
data class EmaReplyReq(val content: String)

@Serializable
data class CollectionReq(val name: String)

@Serializable
data class ProfilePatchReq(
    val nickname: String? = null,
    val signature: String? = null,
    @SerialName("status_emoji") val statusEmoji: String? = null,
    @SerialName("status_text") val statusText: String? = null,
    @SerialName("blog_url") val blogUrl: String? = null,
    @SerialName("custom_markdown") val customMarkdown: String? = null,
    @SerialName("custom_css") val customCss: String? = null,
    @SerialName("fixed_avatar_url") val fixedAvatarUrl: String? = null,
    @SerialName("active_avatar_url") val activeAvatarUrl: String? = null,
    @SerialName("active_avatar_enabled") val activeAvatarEnabled: Boolean? = null,
    @SerialName("show_fields") val showFields: Map<String, Boolean>? = null,
    @SerialName("notify_scope") val notifyScope: Map<String, Boolean>? = null,
)

@Serializable
data class PasswordPatchReq(@SerialName("old_password") val oldPassword: String, @SerialName("new_password") val newPassword: String)

@Serializable
data class HeartbeatReq(@SerialName("active_seconds") val activeSeconds: Int)

@Serializable
data class SessionDto(
    val id: Long,
    val device: String = "",
    @SerialName("last_used") val lastUsed: Long = 0,
    val current: Boolean = false,
)

@Serializable
data class SessionListRes(val items: List<SessionDto> = emptyList())

@Serializable
data class MuteReq(val hours: Int, val reason: String)

@Serializable
data class WarnReq(val content: String)

@Serializable
data class BanReq(val reason: String)

@Serializable
data class RoleReq(val role: String)

@Serializable
data class ReportReq(val reason: String)

@Serializable
data class ResolveReq(val approve: Boolean, @SerialName("delete_target") val deleteTarget: Boolean, val reason: String)

@Serializable
data class StaffApplyReq(val reason: String, @SerialName("weekly_hours") val weeklyHours: String)

@Serializable
data class DecideReq(val approve: Boolean, val reason: String)

@Serializable
data class ReleaseReq(val version: String, val channel: String, @SerialName("change_type") val changeType: String, val body: String)

@Serializable
data class UploadRes(val url: String)

@Serializable
data class OkRes(val ok: Boolean = true)

@Serializable
data class UnreadRes(val unread: Long = 0)

@Serializable
data class ErrorRes(val error: String = "")
