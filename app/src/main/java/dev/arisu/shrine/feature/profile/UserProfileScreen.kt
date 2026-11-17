package dev.arisu.shrine.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.components.ArisuAvatarRinged
import dev.arisu.shrine.core.components.ToriiWatermark
import dev.arisu.shrine.core.components.ArisuPrimaryButton
import dev.arisu.shrine.core.components.ArisuTextField
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.HeatmapCard
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.PillTabs
import dev.arisu.shrine.core.components.PostCard
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.SheetHeader
import dev.arisu.shrine.core.components.ShrineEmptyState
import dev.arisu.shrine.core.components.ShrineStaleBanner
import dev.arisu.shrine.core.components.ShrineNoticeBar
import dev.arisu.shrine.core.components.ShrinePullRefreshBox
import dev.arisu.shrine.core.components.StatColumn
import dev.arisu.shrine.core.components.TopBarIcon
import dev.arisu.shrine.core.components.accentRaised
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.components.softCard
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.design.heatColor
import dev.arisu.shrine.data.model.ProfileTab
import dev.arisu.shrine.data.model.Role
import dev.arisu.shrine.data.model.TileDay
import dev.arisu.shrine.data.remote.toApiMessage
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.TextButton
import dev.arisu.shrine.data.repo.Shrine
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.User
import dev.arisu.shrine.data.model.avatarUrl
import dev.arisu.shrine.nav.FOLLOW_MODE_FOLLOWERS
import dev.arisu.shrine.nav.FOLLOW_MODE_FOLLOWING
import kotlinx.coroutines.delay

private val MuteDurations = listOf("1 小时", "1 天", "7 天", "30 天", "永久")

private val CoverHeight = 262.dp

/** 四周留白：封面是页面上「挖」出来的一块凹面板，所以不通铺到屏幕边。 */
private val CoverMargin = 18.dp

private val CoverShape = RoundedCornerShape(28.dp)

/** 内容整体上移一点，给右下角的按钮让位，同时保持视觉居中。 */
private val CoverContentBottom = 38.dp

/**
 * 他人主页。
 *
 * 结构与「我的客房」保持同一套：封面、向上重叠的数据栏、参拜热度、TA 的帖子。
 * 差别只在封面右上角的关注按钮与顶部的更多菜单。
 *
 * 更多菜单里的禁言/警告面板：确认后调服务端接口。
 */
@Composable
fun UserProfileScreen(
    userId: String,
    initialPostTab: Int = 0,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenFollowList: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 首帧：先把上次看过的资料摆上来（从关注列表点进来几乎无感），网络回来再覆盖
    var user by remember { mutableStateOf(Shrine.peekUser(userId)?.user) }
    var tiles by remember { mutableStateOf(Shrine.peekTiles(userId).orEmpty()) }
    var loading by remember { mutableStateOf(user == null) }
    var failed by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val me by Shrine.me.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var moreMenuOpen by remember { mutableStateOf(false) }
    var followMenuOpen by remember { mutableStateOf(false) }
    var mutePanelOpen by remember { mutableStateOf(false) }
    var banOpen by remember { mutableStateOf(false) }
    var unbanOpen by remember { mutableStateOf(false) }
    var warnPanelOpen by remember { mutableStateOf(false) }
    var rolePanelOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reportOpen by remember { mutableStateOf(false) }
    // 我有没有拉黑 TA（静默：TA 看不到这个状态）
    var blocked by remember { mutableStateOf(false) }
    // TA 是否被封禁：只有管理角色拿得到，用来决定菜单里显示「封禁」还是「解除封禁」
    var banned by remember { mutableStateOf(false) }
    var blockConfirm by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    var muteDuration by remember { mutableStateOf(MuteDurations.first()) }
    var muteReason by rememberSaveable { mutableStateOf("") }
    var warnText by rememberSaveable { mutableStateOf("") }

    // 首载与下拉刷新共用；刷新失败保留已有资料，只有首载进入失败态
    suspend fun load() {
        try {
            val detail = Shrine.user(userId)
            user = detail.user
            blocked = detail.blocked
            banned = detail.banned
            tiles = Shrine.tiles(userId)
            failed = false
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // 此前刷新一次接口抖动就把整页替换成失败空态，注释与代码自相矛盾
            if (user == null) failed = true
        }
        loading = false
    }

    LaunchedEffect(reloadKey) { load() }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2200)
            notice = null
        }
    }

    if (loading) {
        dev.arisu.shrine.core.components.ShrineLoadingState()
        return
    }
    if (failed || user == null) {
        dev.arisu.shrine.core.components.ShrineEmptyState(note = "加载失败，点击重试", onClick = { reloadKey++ })
        return
    }
    val userState = user ?: return

    Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = userState.nickname,
            subtitle = "${userState.role.label} · ${userState.id}",
            onBack = onBack,
            actions = {
                Box {
                    TopBarIcon(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "更多",
                        onClick = { moreMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = moreMenuOpen,
                        onDismissRequest = { moreMenuOpen = false },
                    ) {
                        // 禁言/封禁宫司起步；警告奉仕即可用（与服务端 requireAdmin 对齐）
                        if (me?.role?.canModerate == true && userId != me?.id) {
                            if (me?.role?.canCurate == true) {
                                DropdownMenuItem(
                                    text = { Text("禁言") },
                                    onClick = {
                                        moreMenuOpen = false
                                        mutePanelOpen = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("警告") },
                                onClick = {
                                    moreMenuOpen = false
                                    warnPanelOpen = true
                                },
                            )
                            if (me?.role?.canCurate == true) {
                                if (banned) {
                                    DropdownMenuItem(
                                        text = { Text("解除封禁") },
                                        onClick = {
                                            moreMenuOpen = false
                                            unbanOpen = true
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("封禁") },
                                        onClick = {
                                            moreMenuOpen = false
                                            banOpen = true
                                        },
                                    )
                                }
                            }
                        }
                        if (me?.role?.canAssignRoles == true && userId != me?.id) {
                            DropdownMenuItem(
                                text = { Text("调整身份") },
                                onClick = {
                                    moreMenuOpen = false
                                    rolePanelOpen = true
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (blocked) "解除拉黑" else "拉黑") },
                            onClick = {
                                moreMenuOpen = false
                                if (blocked) {
                                    scope.launch {
                                        try {
                                            Shrine.unblockUser(userId)
                                            blocked = false
                                            load()
                                            notice = "已解除拉黑"
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            notice = e.toApiMessage()
                                        }
                                    }
                                } else {
                                    blockConfirm = true
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("复制主页链接") },
                            onClick = {
                                moreMenuOpen = false
                                val base = Shrine.baseUrl.value
                                if (base.isEmpty()) {
                                    notice = "尚未配置服务器地址，无法生成链接"
                                } else {
                                    clipboard.setText(AnnotatedString("$base/user/$userId"))
                                    notice = "链接已复制"
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("举报") },
                            onClick = {
                                moreMenuOpen = false
                                reportOpen = true
                            },
                        )
                    }
                }
            },
        )

        ShrineStaleBanner()

        ShrinePullRefreshBox(
            onRefresh = { load() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            CoverSection(
                user = userState,
                isSelf = userId == me?.id,
                blocked = blocked,
                onReload = { scope.launch { load() } },
                followMenuOpen = followMenuOpen,
                onFollowMenuChange = { followMenuOpen = it },
                onNotice = { notice = it },
            )

            StatsCard(user = userState, isSelf = userId == me?.id, onOpenFollowList = onOpenFollowList)

            Spacer(Modifier.height(12.dp))

            SectionLabel(text = "参拜热度", modifier = Modifier.padding(horizontal = 16.dp))
            HeatmapCard(
                tiles = tiles,
                header = {
                    Text(
                        text = "活跃度按当日在线、发帖与评论加权累计",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                    )
                },
            )

            Spacer(Modifier.height(12.dp))

            // 以 blocked 为键重建：拉黑/解除后帖子区立刻按新的可见范围重取
            androidx.compose.runtime.key(blocked) {
                PostsSection(userId = userId, total = userState.postCount, initialTab = initialPostTab, isSelf = userId == me?.id, onOpenPost = onOpenPost)
            }

            // GitHub 规则：没有 README 就不出这一节。服务端在用户关掉可见性时
            // 直接不下发内容（ToPublic 清空），内容为空即等价于「隐藏/未编辑」，
            // 客户端不再看 show_fields——公开资料的 DTO 本来就不带它
            if (userState.customMarkdown.isNotBlank() || userState.customCss.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                dev.arisu.shrine.core.components.SectionLabel(
                    text = "自定义区域",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                dev.arisu.shrine.core.components.CustomArea(
                    markdown = userState.customMarkdown,
                    safeCss = userState.customCss,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
        }

        ShrineNoticeBar(
            text = notice,
            onDismiss = { notice = null },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 24.dp),
        )
    }

    if (blockConfirm) {
        dev.arisu.shrine.core.components.ArisuConfirmDialog(
            title = "拉黑",
            body = "拉黑后您将看不到对方的内容与通知，对方也无法关注或打扰您。",
            confirmText = "拉黑",
            onConfirm = {
                Shrine.blockUser(userId)
                blocked = true
                blockConfirm = false
                load()
                notice = "已拉黑"
            },
            onDismiss = { blockConfirm = false },
        )
    }

    if (reportOpen) {
        dev.arisu.shrine.core.components.ArisuConfirmDialog(
            title = "举报社员",
            body = "处理结果会通过社务箱通知您。",
            confirmText = "提交举报",
            reasonHint = "举报理由（必填）",
            onConfirm = { reason ->
                Shrine.reportUser(userId, reason)
                reportOpen = false
                notice = "已提交举报，处理结果会通过社务箱通知您"
            },
            onDismiss = { reportOpen = false },
        )
    }

    if (banOpen) {
        dev.arisu.shrine.core.components.ArisuConfirmDialog(
            title = "封禁社员",
            body = "封禁后对方将无法登录，处理结果会通过社务箱通知。",
            confirmText = "确认封禁",
            reasonHint = "封禁理由（必填）",
            onConfirm = { reason ->
                Shrine.banUser(userId, reason)
                banned = true
                banOpen = false
                notice = "已发送至对方社务箱"
            },
            onDismiss = { banOpen = false },
        )
    }
    if (unbanOpen) {
        dev.arisu.shrine.core.components.ArisuConfirmDialog(
            title = "解除封禁",
            body = "解除后对方可以正常登录使用。",
            confirmText = "确认解除",
            onConfirm = {
                Shrine.unbanUser(userId)
                banned = false
                unbanOpen = false
                notice = "已解除封禁"
            },
            onDismiss = { unbanOpen = false },
        )
    }

    // 禁言/警告/调身份三张面板共用一把在途锁：确认连点不再发两笔管理操作
    var sheetBusy by remember { mutableStateOf(false) }
    if (mutePanelOpen) {
        MuteSheet(
            targetName = userState.nickname,
            duration = muteDuration,
            reason = muteReason,
            busy = sheetBusy,
            onDurationChange = { muteDuration = it },
            onReasonChange = { muteReason = it },
            onDismiss = { mutePanelOpen = false },
            onConfirm = {
                if (!sheetBusy) {
                    sheetBusy = true
                    scope.launch {
                        try {
                            val hours = when (muteDuration) {
                                "1 小时" -> 1
                                "1 天" -> 24
                                "7 天" -> 168
                                "30 天" -> 720
                                else -> 0
                            }
                            Shrine.muteUser(userId, hours, muteReason.trim())
                            mutePanelOpen = false
                            muteReason = ""
                            notice = "已发送至对方社务箱"
                        } catch (e: Exception) {
                            notice = e.toApiMessage()
                        }
                        sheetBusy = false
                    }
                }
            },
            onUnmute = {
                if (!sheetBusy) {
                    sheetBusy = true
                    scope.launch {
                        try {
                            Shrine.unmuteUser(userId)
                            mutePanelOpen = false
                            notice = "已解除禁言"
                        } catch (e: Exception) {
                            notice = e.toApiMessage()
                        }
                        sheetBusy = false
                    }
                }
            },
        )
    }

    if (warnPanelOpen) {
        WarnSheet(
            targetName = userState.nickname,
            text = warnText,
            busy = sheetBusy,
            onTextChange = { warnText = it },
            onDismiss = { warnPanelOpen = false },
            onConfirm = {
                if (!sheetBusy) {
                    sheetBusy = true
                    scope.launch {
                        try {
                            Shrine.warnUser(userId, warnText.trim())
                            warnPanelOpen = false
                            warnText = ""
                            notice = "已发送至对方社务箱"
                        } catch (e: Exception) {
                            notice = e.toApiMessage()
                        }
                        sheetBusy = false
                    }
                }
            },
        )
    }

    if (rolePanelOpen) {
        RoleSheet(
            targetName = userState.nickname,
            current = userState.role,
            busy = sheetBusy,
            onDismiss = { rolePanelOpen = false },
            onConfirm = { picked ->
                if (!sheetBusy) {
                    sheetBusy = true
                    scope.launch {
                        try {
                            Shrine.setRole(userId, picked)
                            rolePanelOpen = false
                            notice = "身份已调整为「" + picked.label + "」"
                            load()
                        } catch (e: Exception) {
                            notice = e.toApiMessage()
                        }
                        sheetBusy = false
                    }
                }
            },
        )
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 封面区
 * ══════════════════════════════════════════════════════════════════ */

@Composable
private fun CoverSection(
    user: User,
    isSelf: Boolean,
    blocked: Boolean,
    onReload: () -> Unit,
    followMenuOpen: Boolean,
    onFollowMenuChange: (Boolean) -> Unit,
    onNotice: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val status = listOfNotNull(user.statusEmoji, user.statusText).joinToString(" ")

    NeuSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CoverMargin, top = CoverMargin, end = CoverMargin)
            // 他人主页多一行「关注」按钮（签名正下方），三行签名的极端情况也不挤爆
            .height(if (isSelf) CoverHeight else CoverHeight + 28.dp),
        shape = CoverShape,
        depth = 5.dp,
        blur = 10.dp,
    ) {
        ToriiWatermark(CoverShape)
        Column(
            Modifier
                .align(Alignment.Center)
                // 自我视角保留原版式的下偏留白；他人主页加按钮行后整体真居中（原偏移会把内容顶得过低）
                .padding(bottom = if (isSelf) CoverContentBottom else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.padding(horizontal = 76.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ArisuAvatarRinged(
                    seed = 0,
                    name = user.nickname,
                    role = user.role,
                    size = 78.dp,
                    url = user.avatarUrl,
                )
                Spacer(Modifier.height(8.dp))
                ProfileIdentity(
                    nickname = user.nickname,
                    role = user.role,
                    status = status,
                )
            }
            // 签名独立层：不受 76dp 内边距挤压，50 字可完整读（最多三行）
            if (user.signature.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(
                    text = user.signature,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            // 关注按钮：签名正下方（自己主页不显示；拉黑后也不显示——点了不会有任何反应）
            if (!isSelf && !blocked) {
                Spacer(Modifier.height(12.dp))
                Box {
                    // 关注/取关/特别关注共用一把在途锁：连点不会「关注上又被取关」
                    var followBusy by remember { mutableStateOf(false) }
                    fun runFollow(block: suspend () -> Unit) {
                        if (followBusy) return
                        followBusy = true
                        scope.launch {
                            try {
                                block()
                                onReload()
                            } catch (e: Exception) {
                                // 弱网下点关注毫无反应会让人反复点，失败要有回声
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                onNotice(e.toApiMessage())
                            }
                            followBusy = false
                        }
                    }
                    FollowPill(
                        following = user.following,
                        onClick = {
                            if (user.following) {
                                onFollowMenuChange(true)
                            } else {
                                runFollow { Shrine.toggleFollow(user.id) }
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = followMenuOpen,
                        onDismissRequest = { onFollowMenuChange(false) },
                    ) {
                        DropdownMenuItem(
                            text = { Text("取消关注") },
                            onClick = {
                                onFollowMenuChange(false)
                                runFollow { Shrine.unfollow(user.id) }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 签名正下方的关注按钮：
 * 未关注 = 主题色实底凸起胶囊、白字，一眼可辨；
 * 已关注 = 凹槽灰字，点击弹出管理菜单。
 * 之前经由 SoftPill 渲染，而 SoftPill 丢弃 background 参数——朱色底从未画出来过，
 * 白字压在浅色凹槽上等于隐形。
 */
@Composable
private fun FollowPill(following: Boolean, onClick: () -> Unit) {
    if (following) {
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .neuInset(shape = RoundedCornerShape(999.dp), color = NeuInset)
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "已关注",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
            )
        }
    } else {
        Box(
            Modifier
                .accentRaised(shape = RoundedCornerShape(999.dp), elevation = 7.dp)
                .clickable { onClick() }
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) {
            Text(
                text = "关注",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 数据栏
 * ══════════════════════════════════════════════════════════════════ */

/** 封面正下方的扁平数据行。关注列表接口只有「我的」，他人主页这两格不可点（点了会开自己的列表）。 */
@Composable
private fun StatsCard(user: User, isSelf: Boolean, onOpenFollowList: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            StatColumn(
                value = user.followingCount.toString(),
                label = "关注数",
                modifier = Modifier.weight(1f),
                onClick = if (isSelf) {
                    { onOpenFollowList(FOLLOW_MODE_FOLLOWING) }
                } else null,
            )
            StatColumn(
                value = user.followerCount.toString(),
                label = "被关注数",
                modifier = Modifier.weight(1f),
                onClick = if (isSelf) {
                    { onOpenFollowList(FOLLOW_MODE_FOLLOWERS) }
                } else null,
            )
            StatColumn(
                value = if (user.showFields["online_hours"] == false) "已隐藏" else user.onlineHours.toString(),
                label = "总在线小时数",
                modifier = Modifier.weight(1f),
            )
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 参拜热度
 * ══════════════════════════════════════════════════════════════════ */

/* HeatmapCard / 星期标签 / 图例见 core/components/Heatmap.kt（与「我的客房」共用一份） */

/* ══════════════════════════════════════════════════════════════════
 * TA 的帖子
 * ══════════════════════════════════════════════════════════════════ */

@Composable
private fun PostsSection(userId: String, total: Int, initialTab: Int, isSelf: Boolean, onOpenPost: (String) -> Unit) {
    // 追踪/喜欢/收藏仅本人可见（服务端 403 保护），他人主页只展示发布的
    // rememberSaveable：进帖子详情再返回，仍停在原来的标签
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, if (isSelf) 3 else 0)) }
    val wireTab = when (tab) {
        1 -> "tracked"
        2 -> "liked"
        3 -> "favorited"
        else -> "posts"
    }
    // 首帧先摆上次那份（进他人主页不再先空一下），网络回来再覆盖
    val cachedPosts = remember(userId, wireTab) { Shrine.peekAuthorPosts(userId, wireTab) }
    var posts by remember { mutableStateOf(cachedPosts.orEmpty()) }
    var loading by remember { mutableStateOf(cachedPosts == null) }
    var postsFailed by remember { mutableStateOf(false) }
    var postsReload by remember { mutableStateOf(0) }

    LaunchedEffect(userId, wireTab, postsReload) {
        // 已经有内容（缓存直出 / 上次拉到的）就静默刷新，不整块换成「少女祈祷中」
        loading = posts.isEmpty()
        postsFailed = false
        posts = try {
            Shrine.postsByAuthor(userId, wireTab)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            postsFailed = true
            posts // 刷新失败保留已有内容，只有手上空空才落进失败态
        }
        loading = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        SectionLabel(
            text = if (isSelf) "我的帖子" else "TA 的帖子",
            trailing = if (tab == 0 || !isSelf) "$total 篇" else null,
        )
        if (isSelf) {
            PillTabs(
                items = ProfileTab.entries,
                selected = ProfileTab.entries[tab],
                onSelect = { tab = it.ordinal },
                label = { it.label },
            )
        }
        if (loading) {
            dev.arisu.shrine.core.components.ShrineLoadingState(note = "少女祈祷中")
        } else if (posts.isEmpty() && postsFailed) {
            ShrineEmptyState(note = "加载失败，点击重试", onClick = { postsReload++ })
        } else if (posts.isEmpty()) {
            ShrineEmptyState(
                note = when {
                    isSelf && tab == 1 -> "还没有追踪的帖子"
                    isSelf && tab == 2 -> "还没有喜欢的帖子"
                    isSelf && tab == 3 -> "收藏夹还是空的"
                    isSelf -> "还没有发过帖子"
                    else -> "这位社员还没有发过帖子"
                }
            )
        } else {
            // 帖子多时全量平铺会把主页拉得老长：默认三条，展开后分页
            CollapsiblePostList(posts = posts, onOpenPost = onOpenPost)
        }
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 禁言 / 警告面板
 * ══════════════════════════════════════════════════════════════════ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MuteSheet(
    targetName: String,
    duration: String,
    reason: String,
    busy: Boolean,
    onDurationChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onUnmute: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(
            title = "禁言 $targetName",
            onClose = onDismiss,
        )
        Text(
            text = "选择禁言时长",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp),
        )
        PillTabs(
            items = MuteDurations,
            selected = duration,
            onSelect = onDurationChange,
            label = { it },
        )
        Text(
            text = "理由（必填）",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp),
        )
        Column(Modifier.padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(8.dp))
            ArisuTextField(
                value = reason,
                onValueChange = { if (it.length <= 500) onReasonChange(it) },
                placeholder = "请说明禁言理由，会记入社务箱",
                singleLine = false,
                minLines = 2,
            )
            Spacer(Modifier.height(16.dp))
            ArisuPrimaryButton(
                text = "确认禁言",
                enabled = reason.isNotBlank() && !busy,
                onClick = onConfirm,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                enabled = !busy,
                onClick = onUnmute,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("解除禁言") }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarnSheet(
    targetName: String,
    text: String,
    busy: Boolean,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(
            title = "警告 $targetName",
            subtitle = "警告内容会发送到对方的社务箱",
            onClose = onDismiss,
        )
        Column(Modifier.padding(horizontal = 22.dp)) {
            ArisuTextField(
                value = text,
                onValueChange = { if (it.length <= 500) onTextChange(it) },
                placeholder = "填写警告内容",
                singleLine = false,
                minLines = 3,
            )
            Spacer(Modifier.height(16.dp))
            ArisuPrimaryButton(
                text = "发送警告",
                enabled = text.isNotBlank() && !busy,
                onClick = onConfirm,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * 身份调整面板（仅神主可见）：单选新身份，确认后立即生效并经社务箱通知对方。
 * 与服务端 POST /users/{id}/role 对应（仅神主、不可自改）。
 */
@Composable
private fun RoleSheet(
    targetName: String,
    current: Role,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Role) -> Unit,
) {
    var picked by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整身份") },
        text = {
            Column {
                Text(
                    text = "为 " + targetName + " 选择新的身份，确认后立即生效，并通过社务箱通知对方。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Role.entries.forEach { role ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { picked = role }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .then(
                                    if (picked == role) Modifier.background(dev.arisu.shrine.core.design.VermilionButton)
                                    else Modifier.neuInset(shape = CircleShape, color = dev.arisu.shrine.core.design.NeuInset)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (picked == role) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = role.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (picked == role) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Spacer(Modifier.weight(1f))
                        if (role == current) {
                            Text(
                                text = "现任",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkMuted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked != current && !busy, onClick = { onConfirm(picked) }) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
