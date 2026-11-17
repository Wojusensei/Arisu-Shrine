package dev.arisu.shrine.feature.play

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.arisu.shrine.feature.share.FortuneCardOptions
import dev.arisu.shrine.feature.share.FortuneCardStyle
import dev.arisu.shrine.feature.share.FortuneOptionsSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.arisu.shrine.core.components.BoardMemory
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.ScrollTopOrb
import dev.arisu.shrine.core.components.MistWatermark
import dev.arisu.shrine.core.components.ToriiWatermark
import dev.arisu.shrine.core.components.ArisuPrimaryButton
import dev.arisu.shrine.core.components.ArisuTopBar
import dev.arisu.shrine.core.components.ArisuCard
import dev.arisu.shrine.core.components.ArisuAvatar
import dev.arisu.shrine.core.components.HairlineDivider
import dev.arisu.shrine.core.components.NeuSurface
import dev.arisu.shrine.core.components.ScrollTopOrb
import dev.arisu.shrine.core.components.SectionLabel
import dev.arisu.shrine.core.components.ShrineIcons
import dev.arisu.shrine.core.components.ShrineNoticeBar
import dev.arisu.shrine.core.components.ShrinePullRefreshBox
import dev.arisu.shrine.core.components.ShrineStaleBanner
import dev.arisu.shrine.core.components.SoftPill
import dev.arisu.shrine.core.components.TopBarIcon
import dev.arisu.shrine.core.components.YinYangOrb
import dev.arisu.shrine.core.components.smoothScrollToTop
import dev.arisu.shrine.core.design.SerifFamily
import dev.arisu.shrine.core.components.neuInset
import dev.arisu.shrine.core.design.Gold
import dev.arisu.shrine.core.design.GoldSoft
import dev.arisu.shrine.core.design.InkBody
import dev.arisu.shrine.core.design.InkFaint
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.InkTitle
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.NeuShadow
import dev.arisu.shrine.core.design.PaperSunken
import dev.arisu.shrine.core.design.OnAccent
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.design.VermilionButton
import dev.arisu.shrine.core.components.ScrollTopBus
import dev.arisu.shrine.core.media.needsLegacyGalleryPermission
import dev.arisu.shrine.feature.share.FortuneCard
import dev.arisu.shrine.feature.share.FortuneCardData
import dev.arisu.shrine.feature.share.fortuneCardData
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.nav.Routes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.OmikujiBoard
import dev.arisu.shrine.data.model.OmikujiDraw
import dev.arisu.shrine.data.model.OmikujiFortune
import dev.arisu.shrine.data.model.OmikujiLeaderRow
import dev.arisu.shrine.data.model.accent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 求签三态：「还没查到」与「今日未抽」是两回事——前者不能渲染成可点的求签按钮 */
private sealed interface LuckState {
    data object Unknown : LuckState
    data object NotDrawn : LuckState
    data class Drawn(val luck: Int) : LuckState
}

private enum class FortuneExportAction { Share, Save }

/** 游会板块的常驻记忆：切走再切回直接摆上次的内容，与其它三个板块同构 */
private class PlaySnapshot(
    val luck: LuckState,
    val leader: OmikujiBoard?,
    val stats: Shrine.OnlineStats?,
)

private val playMemory = BoardMemory<String, PlaySnapshot>()
private const val PLAY_KEY = "play"

/** 御签榜固定展示前十名；自己的小榜只在前十之外出现 */
private const val LEADER_TOP_RANKS = 10

/**
 * 神社游会。娱乐板块：
 * ① 今日运势（幸运值 1-100）；② 社员统计；③ 全端总在线时长（意象文案）。
 */
@Composable
fun PlayScreen(
    unreadCount: Int,
    onOpenInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 账号切换时整块签到状态跟着换成新社员，不能把上一个人的签带过来
    val me by Shrine.me.collectAsStateWithLifecycle()
    // 进页先摆已知的内容：内存记忆（同进程切板块）→ 落盘缓存（冷启动）→ 都没有才算未知。
    // 此前一律从 null 起步，切进来先渲染一枚可点的「今日求签」，等网络回来才变成已求签
    val memo = remember(me?.id) { playMemory.get(PLAY_KEY, me?.id) }
    var luckState by remember(me?.id) {
        mutableStateOf(
            memo?.luck ?: when (val cached = Shrine.peekOmikujiToday()) {
                null -> LuckState.Unknown
                0 -> LuckState.NotDrawn
                else -> LuckState.Drawn(cached)
            }
        )
    }
    val luck = (luckState as? LuckState.Drawn)?.luck
    var stats by remember { mutableStateOf(memo?.stats ?: Shrine.peekOnlineStats()) }
    var drawing by remember { mutableStateOf(false) }
    // 求签失败与统计失败分家：此前共用一个 failed，点「重试」只重启统计轮询，签永远补不上
    var drawFailed by remember { mutableStateOf(false) }
    var leader by remember(me?.id) { mutableStateOf(memo?.leader ?: Shrine.peekOmikujiLeaderboard()) }
    var reloadKey by remember { mutableStateOf(0) }

    // 记忆写回：板块切走时留着这份内容，切回来直接摆出来（账号换了自动失效）
    LaunchedEffect(luckState, leader, stats, me?.id) {
        val id = me?.id ?: return@LaunchedEffect
        playMemory.put(PLAY_KEY, id, PlaySnapshot(luckState, leader, stats))
    }
    // 榜单刷新：进页 / 抽签成功 / 下拉刷新共用；失败静默（空榜本就不渲染）
    val refreshLeader: suspend () -> Unit = {
        try {
            leader = Shrine.omikujiLeaderboard()
        } catch (_: Exception) {
        }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var pendingSaveLuck by remember { mutableStateOf<Int?>(null) }
    var pendingSaveOptions by remember { mutableStateOf(FortuneCardOptions()) }
    var exportAction by remember { mutableStateOf<FortuneExportAction?>(null) }
    var streak by remember(me?.id) { mutableStateOf(0) }
    var showVerse by rememberSaveable(me?.id) { mutableStateOf(true) }
    var showYiJi by rememberSaveable(me?.id) { mutableStateOf(true) }
    var showStreak by rememberSaveable(me?.id) { mutableStateOf(true) }
    var showQr by rememberSaveable(me?.id) { mutableStateOf(false) }
    var cardStyleName by rememberSaveable(me?.id) { mutableStateOf(FortuneCardStyle.LACQUER.name) }
    val cardStyle = FortuneCardStyle.entries.firstOrNull { it.name == cardStyleName }
        ?: FortuneCardStyle.LACQUER
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    val previewHistory = remember { mutableListOf<Bitmap>() }
    val cardOptions = FortuneCardOptions(
        showVerse = showVerse,
        showYiJi = showYiJi,
        showStreak = showStreak,
        showQr = showQr,
    )

    LaunchedEffect(luck, me?.id) {
        if (luck == null) {
            streak = 0
            return@LaunchedEffect
        }
        streak = try {
            Shrine.omikujiStreak()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            0
        }
    }

    /** 御签卡只取今日已抽到的数字与吉凶档位；身份取当前社员这一份 */
    fun fortuneData(
        value: Int,
        options: FortuneCardOptions,
        style: FortuneCardStyle = cardStyle,
    ): FortuneCardData = fortuneCardData(
        user = me,
        luck = value,
        band = luckBand(value).first,
        date = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
        streak = streak.coerceAtLeast(1),
        options = options,
        style = style,
    )

    LaunchedEffect(exportAction, cardOptions, cardStyle, streak, luck, me?.id) {
        if (exportAction == null) {
            preview = null
            return@LaunchedEffect
        }
        val value = luck ?: return@LaunchedEffect
        val data = fortuneData(value, cardOptions, cardStyle)
        val next = try {
            withContext(Dispatchers.Default) { FortuneCard.render(context, data, width = 432) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        } ?: return@LaunchedEffect
        previewHistory += next
        while (previewHistory.size > 4) {
            previewHistory.removeAt(0).recycle()
        }
        preview = next
    }
    DisposableEffect(Unit) {
        onDispose {
            previewHistory.forEach { if (!it.isRecycled) it.recycle() }
            previewHistory.clear()
            preview = null
        }
    }

    fun shareFortune(value: Int, options: FortuneCardOptions) {
        if (exporting) return
        // 点按瞬间就把身份固定下来，渲染期间切号也不会换头
        val payload = fortuneData(value, options)
        exporting = true
        scope.launch {
            try {
                FortuneCard.share(context, payload)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                notice = e.toApiMessage()
            }
            exporting = false
        }
    }

    fun saveFortune(value: Int, options: FortuneCardOptions) {
        if (exporting) return
        val payload = fortuneData(value, options)
        exporting = true
        scope.launch {
            try {
                FortuneCard.save(context, payload)
                notice = "已保存到相册"
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                notice = e.toApiMessage()
            }
            exporting = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val value = pendingSaveLuck
        val options = pendingSaveOptions
        pendingSaveLuck = null
        if (granted && value != null) {
            saveFortune(value, options)
        } else if (!granted) {
            notice = "没有存储权限，存不进相册"
        }
    }

    fun requestSave(value: Int, options: FortuneCardOptions) {
        if (needsLegacyGalleryPermission() &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSaveLuck = value
            pendingSaveOptions = options
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveFortune(value, options)
        }
    }
    val animatedLuck by animateIntAsState(
        targetValue = luck ?: 0,
        animationSpec = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
        label = "luck",
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(reloadKey, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    // 值没变就不赋值：在线人数每 30 秒回来一次，等值时保持旧实例，
                    // 整页不因为一次轮询白重组一遍（结构相等，data class）
                    val fresh = Shrine.onlineStats()
                    if (fresh != stats) stats = fresh
                } catch (_: Exception) {
                    // 拉不到就继续摆着上一次的数字（弱网横幅会说明数据是旧的），
                    // 不把已知的值抹成占位符
                }
                delay(30_000L)
            }
        }
    }

    // 进页只查不抽：此前误用求签接口“试探”，把用户当天的签直接消耗掉了。
    // 查不到就停在三态里的「未知」——不把「没查到」渲染成「没求过」
    LaunchedEffect(me?.id) {
        if (me == null) return@LaunchedEffect
        try {
            val today = Shrine.omikujiToday()
            luckState = if (today > 0) LuckState.Drawn(today) else LuckState.NotDrawn
        } catch (_: Exception) {
        }
    }

    // 御签榜进页即拉；抽完签/下拉刷新各再刷一次
    LaunchedEffect(me?.id) {
        if (me != null) refreshLeader()
    }

    val playScroll = rememberScrollState()
    // 底栏再点一次当前板块：不重新导航，直接滚回顶部
    LaunchedEffect(Unit) {
        ScrollTopBus.events.collect { route ->
            if (route == Routes.PLAY) playScroll.smoothScrollToTop()
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(modifier.fillMaxSize()) {
        ArisuTopBar(
            title = "神社游会",
            large = true,
            subtitle = "求签 · 看板 · 全端统计",
            actions = {
                // 结果出来后顶栏常驻分享入口，滚动到哪儿都不用回头找
                luck?.let {
                    TopBarIcon(
                        icon = Icons.Rounded.Share,
                        contentDescription = "分享御签",
                        onClick = { exportAction = FortuneExportAction.Share },
                    )
                }
                TopBarIcon(
                    icon = Icons.Rounded.Mail,
                    contentDescription = "社务箱",
                    onClick = onOpenInbox,
                    badgeCount = unreadCount,
                )
            },
        )

        ShrineStaleBanner()
        ShrineNoticeBar(text = notice, onDismiss = { notice = null })

        ShrinePullRefreshBox(
            onRefresh = {
                try {
                    stats = Shrine.onlineStats()
                    refreshLeader()
                } catch (_: Exception) {
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(playScroll)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            SectionLabel(text = "今日运势", trailing = "每日一次 · 明日再来")
            OmikujiHero()
            Spacer(Modifier.height(12.dp))
            ArisuPrimaryButton(
                text = when {
                    drawing -> "摇签中…"
                    luckState is LuckState.Drawn -> "今日已求签，明日再来"
                    else -> "今日求签"
                },
                icon = ShrineIcons.Torii,
                // 未知期间不给人点：查清楚之前宁可先转着
                enabled = !drawing && luckState is LuckState.NotDrawn,
                busy = drawing || luckState is LuckState.Unknown,
                onClick = {
                    if (!drawing) {
                        drawing = true
                        scope.launch {
                            try {
                                luckState = LuckState.Drawn(Shrine.drawOmikuji())
                                drawFailed = false
                                refreshLeader()
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                drawFailed = true
                            }
                            drawing = false
                        }
                    }
                },
            )
            if (drawFailed && luck == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "加载失败，点击重试",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (!drawing) {
                                drawing = true
                                scope.launch {
                                    try {
                                        // 先查今日是否已中：第一次请求可能已被服务器处理，直接把签领回来
                                        val today = Shrine.omikujiToday()
                                        luckState = if (today > 0) LuckState.Drawn(today) else LuckState.Drawn(Shrine.drawOmikuji())
                                        drawFailed = false
                                        refreshLeader()
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        drawFailed = true
                                    }
                                    drawing = false
                                }
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            val currentLuck = luck
            if (drawing) {
                Spacer(Modifier.height(12.dp))
                ArisuCard(padding = 24.dp) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        YinYangOrb(size = 44.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "摇 签 中",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkMuted,
                            letterSpacing = 3.sp,
                        )
                    }
                }
            } else if (currentLuck != null) {
                Spacer(Modifier.height(12.dp))
                val band = luckBand(currentLuck)
                ArisuCard(padding = 22.dp, watermark = true) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "今日幸运值",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                            color = InkFaint,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$animatedLuck",
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = " / 100",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkFaint,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        SoftPill(
                            text = band.first,
                            color = band.second,
                            background = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "仅代表抽签结果，幸运与否不能由小小签子决定。",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkMuted,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))
                        HairlineDivider()
                        Spacer(Modifier.height(2.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            FortuneAction(
                                icon = Icons.Rounded.Share,
                                text = "分享御签",
                                enabled = !exporting,
                                modifier = Modifier.weight(1f),
                            ) { exportAction = FortuneExportAction.Share }
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(PaperSunken)
                            )
                            FortuneAction(
                                icon = Icons.Rounded.Download,
                                text = "保存到相册",
                                enabled = !exporting,
                                modifier = Modifier.weight(1f),
                            ) { exportAction = FortuneExportAction.Save }
                        }
                    }
                }
            }

            // 御签榜：今日全社幸运值前 10 + 自己前后各 2 人；空榜不渲染
            val board = leader
            val leaderBoard = board?.items.orEmpty()
            if (leaderBoard.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ArisuCard(padding = 20.dp, watermark = true) {
                    Column(Modifier.fillMaxWidth()) {
                        // 装饰头：细线 ─ 鸟居纹 ─ 御签榜 ─ 鸟居纹 ─ 细线
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(NeuShadow.copy(alpha = 0.38f))
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                ShrineIcons.Torii,
                                contentDescription = null,
                                tint = Vermilion.copy(alpha = 0.75f),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "御签榜",
                                style = MaterialTheme.typography.titleSmall,
                                color = InkTitle,
                                letterSpacing = 4.sp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                ShrineIcons.Torii,
                                contentDescription = null,
                                tint = Vermilion.copy(alpha = 0.75f),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(NeuShadow.copy(alpha = 0.38f))
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        // ── 领奖台：按名次序取前三条目，2 左 1 中 3 右，中位更高 ──
                        val podium = leaderBoard.take(3)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            PodiumSlot(podium.getOrNull(1), 16.dp, Modifier.weight(1f))
                            PodiumSlot(podium.getOrNull(0), 26.dp, Modifier.weight(1f))
                            PodiumSlot(podium.getOrNull(2), 10.dp, Modifier.weight(1f))
                        }
                        // ── 第 4 位起：全宽榜单行 ──
                        val rest = leaderBoard.drop(3)
                        if (rest.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            HairlineDivider()
                            Spacer(Modifier.height(14.dp))
                            rest.forEachIndexed { ri, row ->
                                if (ri > 0) Spacer(Modifier.height(12.dp))
                                LeaderRow(row)
                            }
                        }
                        // ── 我的位置：自己在前十之外时，单独摆出前后各 2 人 ──
                        // 自己在前十时，前十本身已经包含本人和身边的人；服务端也会把
                        // Nearby 收空，这里再挡一道，避免旧缓存摆出一截没有「我」的名单
                        val myRank = board?.me?.rank
                        val nearby = board?.nearby.orEmpty()
                        if (myRank != null && myRank > LEADER_TOP_RANKS && nearby.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            HairlineDivider()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "我的位置",
                                style = MaterialTheme.typography.titleSmall,
                                color = InkTitle,
                                letterSpacing = 3.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            Spacer(Modifier.height(12.dp))
                            nearby.forEachIndexed { ni, row ->
                                if (ni > 0) Spacer(Modifier.height(12.dp))
                                LeaderRow(row)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "同分按求签先后排位",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkFaint,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel(text = "社员统计")
            val st = stats
            // 只有真的没有值才留「--」。此前拉取失败也一并留空，于是每次切回本板块
            // 都会先闪一下「--」再跳回数字，看着像 bug（用户点名）。现在失败保留上一次
            // 的值，网络层失败走 ShrineStaleBanner 的「网络不佳，展示的是…」提示条
            fun num(v: Int?) = if (v == null) "--" else v.toString()
            NeuSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                ToriiWatermark(RoundedCornerShape(24.dp))
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        StatColumnNum(
                            value = num(st?.onlineNow),
                            unit = "人正在神社内",
                            highlight = true,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(26.dp)
                                .background(NeuShadow.copy(alpha = 0.38f))
                        )
                        StatColumnNum(
                            value = num(st?.registered),
                            unit = "注册社员",
                            highlight = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SoftPill(
                        text = "今日峰值 ${num(st?.todayPeak)} 人",
                        color = Gold,
                        background = GoldSoft,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel(text = "全端总在线时长", trailing = "开站至今累计")
            NeuSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                MistWatermark(RoundedCornerShape(24.dp))
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp)) {
                    val total = st?.totalMinutes ?: 0
                    val d = total / 1440
                    val h = (total % 1440) / 60
                    val m = total % 60
                    val num = SpanStyle(
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 22.sp,
                    )
                    val unit = SpanStyle(fontSize = 11.sp, color = InkMuted)
                    Text(
                        text = buildAnnotatedString {
                            append("神社的灯火，已亮了 ")
                            if (d > 0) { withStyle(num) { append("$d") }; withStyle(unit) { append(" 天 ") } }
                            withStyle(num) { append("$h") }
                            withStyle(unit) { append(" 小时 ") }
                            withStyle(num) { append("$m") }
                            withStyle(unit) { append(" 分钟") }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = InkTitle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "您的每一秒驻足都会为神社增添香火",
                        style = MaterialTheme.typography.labelSmall,
                        color = InkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        }
        }
        ScrollTopOrb(
            scrollState = playScroll,
            onClick = { scope.launch { playScroll.smoothScrollToTop() } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 118.dp),
        )
        exportAction?.let { action ->
            FortuneOptionsSheet(
                preview = preview,
                style = cardStyle,
                onStyleChange = { cardStyleName = it.name },
                options = cardOptions,
                onChange = { updated ->
                    showVerse = updated.showVerse
                    showYiJi = updated.showYiJi
                    showStreak = updated.showStreak
                    showQr = updated.showQr
                },
                confirmText = if (action == FortuneExportAction.Share) "分享御签" else "保存到相册",
                busy = exporting,
                onConfirm = {
                    val value = luck
                    if (value != null) {
                        exportAction = null
                        if (action == FortuneExportAction.Share) {
                            shareFortune(value, cardOptions)
                        } else {
                            requestSave(value, cardOptions)
                        }
                    }
                },
                onDismiss = { exportAction = null },
            )
        }
    }
}

/**
 * 求签大面板：整块压进页面的一枚凹槽，中间顶出一枚圆角槽放着鸟居。
 */
@Composable
private fun OmikujiHero() {
    NeuSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(24.dp),
        depth = 5.dp,
        blur = 10.dp,
    ) {
        ToriiWatermark(RoundedCornerShape(24.dp))
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .neuInset(shape = RoundedCornerShape(26.dp), color = NeuInset),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ShrineIcons.Torii,
                    contentDescription = null,
                    tint = Vermilion,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "御神签",
                style = MaterialTheme.typography.headlineMedium,
                color = InkTitle,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "摇出今天属于您的幸运值",
                style = MaterialTheme.typography.labelMedium,
                color = InkMuted,
            )
        }
    }
}

/** 幸运值档位：<=10 凶 / <=30 末吉 / <=50 小吉 / <=70 中吉 / <=90 吉 / 大吉 */
/** 名次印章：第 1 实心朱白字，2-3 朱晕圆盘（呼应图标母题），其余素色 */
@Composable
private fun RankSeal(rank: Int, size: Dp, filled: Boolean, textStyle: TextStyle) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    filled -> VermilionButton
                    rank <= 3 -> Vermilion.copy(alpha = 0.14f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$rank",
            style = textStyle,
            color = when {
                filled -> OnAccent
                rank <= 3 -> Vermilion
                else -> InkMuted
            },
        )
    }
}

/** 领奖台一位：印章名次 + 头像（身份环）+ 昵称 + 签档 + 幸运值 + 台阶底座；null 占位留空 */
@Composable
private fun PodiumSlot(row: OmikujiLeaderRow?, stepHeight: Dp, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (row == null) return@Column
        val first = row.rank == 1
        RankSeal(
            rank = row.rank,
            size = if (first) 36.dp else 30.dp,
            filled = first,
            textStyle = if (first) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(7.dp))
        Box {
            ArisuAvatar(
                seed = 0,
                name = row.nickname,
                size = if (first) 52.dp else 42.dp,
                role = row.role,
                ring = true,
                url = row.avatar,
            )
            // 自己站上领奖台时给个角标，不额外占一行高度
            if (row.isMe) {
                Text(
                    text = "我",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnAccent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Vermilion)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = row.nickname,
            style = if (first) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = row.role.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        val band = luckBand(row.luck)
        SoftPill(
            text = band.first,
            color = band.second,
            background = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${row.luck}",
            style = if (first) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        // 台阶底座：第 1 位站得最高
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .height(stepHeight)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(if (first) Vermilion.copy(alpha = 0.16f) else NeuShadow.copy(alpha = 0.10f))
        )
    }
}

/** 榜单行：名次印章 + 头像 + 昵称/幸运值细条 + 档位与分数；自己那行淡朱底 + 「我」 */
@Composable
private fun LeaderRow(row: OmikujiLeaderRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (row.isMe) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Vermilion.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 5.dp)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankSeal(
            rank = row.rank,
            size = 24.dp,
            filled = row.rank == 1,
            textStyle = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.width(8.dp))
        ArisuAvatar(seed = 0, name = row.nickname, size = 30.dp, role = row.role, ring = true, url = row.avatar)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.nickname,
                    style = MaterialTheme.typography.bodyMedium,
                    color = row.role.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isMe) {
                    Spacer(Modifier.width(6.dp))
                    SoftPill(
                        text = "我",
                        color = Vermilion,
                        background = Vermilion.copy(alpha = 0.10f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // 幸运值细条：与个人卡「/100」同语义的图形化
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(row.luck / 100f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${row.luck}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = luckBand(row.luck).first,
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint,
            )
        }
    }
}

private fun luckBand(v: Int): Pair<String, Color> = when {
    v <= 10 -> "凶" to Vermilion
    v <= 30 -> "末吉" to InkMuted
    v <= 50 -> "小吉" to InkTitle
    v <= 70 -> "中吉" to InkTitle
    v <= 90 -> "吉" to InkTitle
    else -> "大吉" to Gold
}

/** 御签卡的两个出口：分享 / 保存到相册；与卡片底部的发丝线同一套轻量样式 */
@Composable
private fun FortuneAction(
    icon: ImageVector,
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else InkFaint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) InkTitle else InkFaint,
        )
    }
}

/** 数字在上、单位在下的统计列（社员统计用）。 */
@Composable
private fun StatColumnNum(
    value: String,
    unit: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // 等分格内不折行：放不下就缩字号
        androidx.compose.foundation.text.BasicText(
            text = value,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                color = if (highlight) MaterialTheme.colorScheme.primary else InkTitle,
                lineHeight = 38.sp,
            ),
            maxLines = 1,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                minFontSize = 18.sp,
                maxFontSize = 34.sp,
            ),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            maxLines = 1,
            softWrap = false,
        )
    }
}
