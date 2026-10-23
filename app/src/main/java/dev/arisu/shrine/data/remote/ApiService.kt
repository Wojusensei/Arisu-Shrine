package dev.arisu.shrine.data.remote

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/code")
    suspend fun sendCode(@Body req: SendCodeReq): SendCodeRes

    @POST("api/auth/register")
    suspend fun register(@Body req: RegisterReq): AuthResultDto

    @POST("api/auth/login")
    suspend fun login(@Body req: LoginReq): AuthResultDto

    @POST("api/auth/reset")
    suspend fun resetPassword(@Body req: ResetReq): OkRes

    @POST("api/auth/refresh")
    suspend fun refresh(@Body req: RefreshReq): AuthResultDto

    @POST("api/auth/logout")
    suspend fun logout(@Body req: LogoutReq): OkRes

    @GET("api/me")
    suspend fun me(): MeDto

    @PATCH("api/me")
    suspend fun patchMe(@Body req: ProfilePatchReq): MeDto

    @PATCH("api/me/password")
    suspend fun changePassword(@Body req: PasswordPatchReq): AuthResultDto

    @POST("api/me/email/change-request")
    suspend fun emailChangeRequest(@Body req: EmailChangeReq): SendCodeRes

    @POST("api/me/email/change")
    suspend fun emailChange(@retrofit2.http.Header("X-Refresh-Token") refresh: String, @Body req: EmailChangeReq): MeDto

    @POST("api/me/deactivate")
    suspend fun deactivate(): OkRes

    @GET("api/me/achievements")
    suspend fun myAchievements(): AchievementsRes

    @POST("api/me/heartbeat")
    suspend fun heartbeat(@Body req: HeartbeatReq): HeartbeatRes

    @GET("api/health")
    suspend fun health(): okhttp3.ResponseBody

    @GET("api/me/sessions")
    suspend fun sessions(@retrofit2.http.Header("X-Refresh-Token") refresh: String): SessionListRes

    @DELETE("api/me/sessions/{id}")
    suspend fun kickSession(@retrofit2.http.Path("id") id: Long): OkRes

    @GET("api/me/room")
    suspend fun myRoom(): RoomDto

    @GET("api/me/posts")
    suspend fun myPosts(@Query("tab") tab: String, @Query("collection_id") collectionId: Long? = null, @Query("page_size") pageSize: Int = 50): PostListRes

    @DELETE("api/me/collections/{id}")
    suspend fun deleteCollection(@retrofit2.http.Path("id") id: Long): OkRes

    @GET("api/me/collections")
    suspend fun myCollections(): CollectionListRes

    @POST("api/me/collections")
    suspend fun newCollection(@Body req: CollectionReq): CollectionDto

    @GET("api/me/following")
    suspend fun myFollowing(): UserBriefListRes

    @GET("api/me/followers")
    suspend fun myFollowers(): UserBriefListRes

    @GET("api/users/{id}")
    suspend fun userDetail(@retrofit2.http.Path("id") id: Long): UserDetailDto

    @GET("api/users/{id}/tiles")
    suspend fun userTiles(@retrofit2.http.Path("id") id: Long): TilesRes

    @GET("api/users/{id}/posts")
    suspend fun userPosts(@retrofit2.http.Path("id") id: Long, @Query("tab") tab: String, @Query("page_size") pageSize: Int = 50): PostListRes

    @POST("api/users/{id}/follow")
    suspend fun follow(@retrofit2.http.Path("id") id: Long): FollowingRes

    @DELETE("api/users/{id}/follow")
    suspend fun unfollow(@retrofit2.http.Path("id") id: Long): FollowingRes

    @POST("api/users/{id}/block")
    suspend fun blockUser(@retrofit2.http.Path("id") id: Long): BlockRes

    @DELETE("api/users/{id}/block")
    suspend fun unblockUser(@retrofit2.http.Path("id") id: Long): BlockRes

    @GET("api/me/blocks")
    suspend fun myBlocks(): BlockedListRes

    @POST("api/users/{id}/report")
    suspend fun reportUser(@retrofit2.http.Path("id") id: Long, @Body req: ReportReq): OkRes

    @POST("api/users/{id}/warn")
    suspend fun warnUser(@retrofit2.http.Path("id") id: Long, @Body req: WarnReq): OkRes

    @POST("api/users/{id}/mute")
    suspend fun muteUser(@retrofit2.http.Path("id") id: Long, @Body req: MuteReq): OkRes

    @POST("api/users/{id}/ban")
    suspend fun banUser(@retrofit2.http.Path("id") id: Long, @Body req: BanReq): OkRes

    @POST("api/users/{id}/unmute")
    suspend fun unmuteUser(@retrofit2.http.Path("id") id: Long): OkRes

    @POST("api/users/{id}/unban")
    suspend fun unbanUser(@retrofit2.http.Path("id") id: Long): OkRes

    @POST("api/users/{id}/role")
    suspend fun setRole(@retrofit2.http.Path("id") id: Long, @Body req: RoleReq): OkRes

    @GET("api/posts")
    suspend fun listPosts(
        @Query("board") board: String,
        @Query("sort") sort: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): PostPageDto

    @POST("api/posts")
    suspend fun createPost(@Body req: PostReq): PostDto

    @GET("api/posts/{id}")
    suspend fun getPost(@retrofit2.http.Path("id") id: Long): PostDto

    @PATCH("api/posts/{id}")
    suspend fun updatePost(@retrofit2.http.Path("id") id: Long, @Body req: PostPatchReq): PostDto

    @retrofit2.http.HTTP(method = "DELETE", path = "api/posts/{id}", hasBody = true)
    suspend fun deletePost(@retrofit2.http.Path("id") id: Long, @Body req: DeleteReasonReq): OkRes

    @POST("api/posts/{id}/up")
    suspend fun toggleUp(@retrofit2.http.Path("id") id: Long): UpRes

    @POST("api/posts/{id}/track")
    suspend fun toggleTrack(@retrofit2.http.Path("id") id: Long): TrackRes

    @POST("api/posts/{id}/favorite")
    suspend fun toggleFavorite(@retrofit2.http.Path("id") id: Long, @Body req: FavoriteReq): FavoriteRes

    @POST("api/posts/{id}/essence")
    suspend fun toggleEssence(@retrofit2.http.Path("id") id: Long): EssenceRes

    @POST("api/posts/{id}/pin")
    suspend fun togglePin(@retrofit2.http.Path("id") id: Long): PinRes

    @POST("api/posts/{id}/report")
    suspend fun reportPost(@retrofit2.http.Path("id") id: Long, @Body req: ReportReq): OkRes

    @GET("api/posts/{id}/comments")
    suspend fun comments(@retrofit2.http.Path("id") id: Long): CommentListRes

    @POST("api/posts/{id}/comments")
    suspend fun addComment(@retrofit2.http.Path("id") id: Long, @Body req: CommentReq): CommentDto

    @POST("api/comments/{id}/like")
    suspend fun toggleCommentLike(@retrofit2.http.Path("id") id: Long): LikeRes

    @POST("api/comments/{id}/report")
    suspend fun reportComment(@retrofit2.http.Path("id") id: Long, @Body req: ReportReq): OkRes

    @retrofit2.http.HTTP(method = "DELETE", path = "api/comments/{id}", hasBody = true)
    suspend fun deleteComment(@retrofit2.http.Path("id") id: Long, @Body req: DeleteReasonReq): OkRes

    @GET("api/emas")
    suspend fun listEmas(
        @Query("tags") tags: String?,
        @Query("states") states: String?,
        @Query("sort") sort: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): EmaPageDto

    @POST("api/emas")
    suspend fun createEma(@Body req: EmaReq): EmaDto

    @GET("api/emas/{id}")
    suspend fun getEma(@retrofit2.http.Path("id") id: Long): EmaDto

    @PATCH("api/emas/{id}")
    suspend fun updateEma(@retrofit2.http.Path("id") id: Long, @Body req: EmaPatchReq): EmaDto

    @retrofit2.http.HTTP(method = "DELETE", path = "api/emas/{id}", hasBody = true)
    suspend fun deleteEma(@retrofit2.http.Path("id") id: Long, @Body req: DeleteReasonReq): OkRes

    @POST("api/emas/{id}/state")
    suspend fun setEmaState(@retrofit2.http.Path("id") id: Long, @Body req: EmaStateReq): StateRes

    @GET("api/emas/{id}/replies")
    suspend fun emaReplies(@retrofit2.http.Path("id") id: Long): EmaReplyListRes

    @POST("api/emas/{id}/replies")
    suspend fun addEmaReply(@retrofit2.http.Path("id") id: Long, @Body req: EmaReplyReq): EmaReplyDto

    @POST("api/emas/{id}/report")
    suspend fun reportEma(@retrofit2.http.Path("id") id: Long, @Body req: ReportReq): OkRes

    @retrofit2.http.HTTP(method = "DELETE", path = "api/emas/{emaId}/replies/{rid}", hasBody = true)
    suspend fun deleteEmaReply(@retrofit2.http.Path("emaId") emaId: Long, @retrofit2.http.Path("rid") rid: Long, @Body req: DeleteReasonReq): OkRes

    @GET("api/staff")
    suspend fun staff(): StaffRes

    @POST("api/staff/apply")
    suspend fun applyStaff(@Body req: StaffApplyReq): ApplyRes

    @GET("api/staff/my-application")
    suspend fun myStaffApplication(): MyApplicationRes

    @GET("api/staff/applications")
    suspend fun pendingApplications(): ApplicationListRes

    @POST("api/staff/applications/{id}/decide")
    suspend fun decideApplication(@retrofit2.http.Path("id") id: Long, @Body req: DecideReq): DecideRes

    @GET("api/notices")
    suspend fun notices(
        @Query("category") category: String,
        @Query("only") only: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): NoticePageDto

    @GET("api/notices/unread")
    suspend fun unread(): UnreadRes

    @GET("api/notices/{id}")
    suspend fun notice(@retrofit2.http.Path("id") id: Long): NoticeDto

    @POST("api/notices/{id}/read")
    suspend fun readNotice(@retrofit2.http.Path("id") id: Long): OkRes

    @POST("api/notices/{id}/unread")
    suspend fun markNoticeUnread(@retrofit2.http.Path("id") id: Long): OkRes

    @POST("api/notices/{id}/star")
    suspend fun starNotice(@retrofit2.http.Path("id") id: Long, @Body req: NoticeStarReq): OkRes

    @POST("api/notices/read-all")
    suspend fun readAllNotices(): OkRes

    @DELETE("api/notices/{id}")
    suspend fun deleteNotice(@retrofit2.http.Path("id") id: Long): OkRes

    @GET("api/reports")
    suspend fun reports(
        @Query("status") status: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): ReportListRes

    @POST("api/reports/{id}/resolve")
    suspend fun resolveReport(@retrofit2.http.Path("id") id: Long, @Body req: ResolveReq): OkRes

    @GET("api/search")
    suspend fun search(@Query("q") q: String, @Query("regex") regex: String): SearchResultDto

    @GET("api/play/online")
    suspend fun onlineStats(): OnlineStatsDto

    @POST("api/play/omikuji")
    suspend fun drawOmikuji(): OmikujiDto

    @GET("api/play/omikuji/today")
    suspend fun omikujiToday(): OmikujiTodayRes

    @GET("api/play/omikuji/history")
    suspend fun omikujiHistory(): OmikujiListRes

    @GET("api/play/omikuji/leaderboard")
    suspend fun omikujiLeaderboard(): OmikujiLeaderRes

    @GET("api/releases")
    suspend fun releases(): ReleaseListRes

    @GET("api/releases/check")
    suspend fun checkUpdate(@Query("version") version: String, @Query("channel") channel: String): UpdateCheckDto

    @POST("api/releases")
    suspend fun createRelease(@Body req: ReleaseReq): OkRes

    @Multipart
    @POST("api/upload")
    suspend fun upload(@Part file: MultipartBody.Part): UploadRes

    @GET("api/stickers")
    suspend fun myStickers(): StickerListRes

    @POST("api/stickers")
    suspend fun addSticker(@Body req: StickerAddReq): StickerDto

    @POST("api/stickers/reorder")
    suspend fun reorderStickers(@Body req: StickerReorderReq): OkRes

    @DELETE("api/stickers/{id}")
    suspend fun deleteSticker(@Path("id") id: Long): OkRes
}

@kotlinx.serialization.Serializable
data class EmailChangeReq(
    @kotlinx.serialization.SerialName("new_email") val newEmail: String,
    val code: String? = null,
)

@kotlinx.serialization.Serializable
data class SendCodeRes(val ok: Boolean = false, @kotlinx.serialization.SerialName("dev_code") val devCode: String? = null)

@kotlinx.serialization.Serializable
data class AchievementsRes(val items: List<AchievementDto> = emptyList())

@kotlinx.serialization.Serializable
data class HeartbeatRes(val ok: Boolean = false, @kotlinx.serialization.SerialName("online_now") val onlineNow: Long = 0, @kotlinx.serialization.SerialName("server_time") val serverTime: Long = 0, val unread: Int? = null)

@kotlinx.serialization.Serializable
data class PostListRes(val items: List<PostDto> = emptyList())

@kotlinx.serialization.Serializable
data class CollectionListRes(val items: List<CollectionDto> = emptyList())

@kotlinx.serialization.Serializable
data class UserBriefListRes(val items: List<UserBriefDto> = emptyList())

@kotlinx.serialization.Serializable
data class TilesRes(val tiles: List<TileDto> = emptyList())

@kotlinx.serialization.Serializable
data class FollowingRes(val following: Boolean = false)

@kotlinx.serialization.Serializable
data class UpRes(val upped: Boolean = false, @kotlinx.serialization.SerialName("up_count") val upCount: Long = 0)

@kotlinx.serialization.Serializable
data class TrackRes(val tracked: Boolean = false)

@kotlinx.serialization.Serializable
data class FavoriteRes(val favorited: Boolean = false)

@kotlinx.serialization.Serializable
data class EssenceRes(val essence: Boolean = false)

@kotlinx.serialization.Serializable
data class PinRes(val pinned: Boolean = false)

@kotlinx.serialization.Serializable
data class CommentListRes(val items: List<CommentDto> = emptyList())

@kotlinx.serialization.Serializable
data class LikeRes(val liked: Boolean = false, @kotlinx.serialization.SerialName("like_count") val likeCount: Long = 0)

@kotlinx.serialization.Serializable
data class EmaReplyListRes(val items: List<EmaReplyDto> = emptyList())

@kotlinx.serialization.Serializable
data class StateRes(val state: String = "")

@kotlinx.serialization.Serializable
data class StaffRes(val groups: List<StaffGroupDto> = emptyList())

@kotlinx.serialization.Serializable
data class ApplyRes(val status: String = "")

@kotlinx.serialization.Serializable
data class OmikujiTodayRes(
    val drawn: Boolean = false,
    val luck: Int = 0,
)

@kotlinx.serialization.Serializable
data class MyApplicationRes(
    val applied: Boolean = false,
    val status: String = "",
    @kotlinx.serialization.SerialName("created_at") val createdAt: Long = 0,
)

@kotlinx.serialization.Serializable
data class ApplicationDto(
    val id: Long = 0,
    val user: UserBriefDto = UserBriefDto(),
    val reason: String = "",
    @kotlinx.serialization.SerialName("weekly_hours") val weeklyHours: String = "",
    val status: String = "",
    @kotlinx.serialization.SerialName("created_at") val createdAt: Long = 0,
)

@kotlinx.serialization.Serializable
data class ApplicationListRes(val items: List<ApplicationDto> = emptyList())

@kotlinx.serialization.Serializable
data class DecideRes(val status: String = "")

@kotlinx.serialization.Serializable
data class ReportListRes(
    val items: List<ReportDto> = emptyList(),
    val total: Long = 0,
)

@kotlinx.serialization.Serializable
data class OmikujiListRes(val items: List<OmikujiDto> = emptyList())

@kotlinx.serialization.Serializable
data class OmikujiLeaderRes(val items: List<OmikujiLeaderDto> = emptyList())

@kotlinx.serialization.Serializable
data class OmikujiLeaderDto(
    val nickname: String,
    val role: String,
    val avatar: String = "",
    val luck: Int,
    val rank: Int,
)

@kotlinx.serialization.Serializable
data class ReleaseListRes(val items: List<ReleaseDto> = emptyList())
