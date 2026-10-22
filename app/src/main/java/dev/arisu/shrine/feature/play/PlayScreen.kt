package dev.arisu.shrine.feature.play

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
import androidx.compose.material.icons.rounded.Mail
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import dev.arisu.shrine.data.remote.toApiMessage
import dev.arisu.shrine.data.repo.Shrine
import dev.arisu.shrine.nav.Routes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.arisu.shrine.data.model.OmikujiDraw
import dev.arisu.shrine.data.model.OmikujiFortune
import dev.arisu.shrine.data.model.OmikujiLeaderRow
import dev.arisu.shrine.data.model.accent

private sealed interface LuckState {
    data object Unknown : LuckState
    data object NotDrawn : LuckState
    data class Drawn(val luck: Int) : LuckState
}

private class PlaySnapshot(
    val luck: LuckState,
    val leader: List<OmikujiLeaderRow>,
    val stats: Shrine.OnlineStats?,
)

private val playMemory = BoardMemory<String, PlaySnapshot>()
private const val PLAY_KEY = "play"

@Composable
fun PlayScreen(
    unreadCount: Int,
    onOpenInbox: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val memo = remember { playMemory.get(PLAY_KEY, Shrine.me.value?.id) }
    var luckState by remember {
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

    var drawFailed by remember { mutableStateOf(false) }
    var leader by remember { mutableStateOf(memo?.leader ?: Shrine.peekOmikujiLeaderboard()) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(luckState, leader, stats) {
        val id = Shrine.me.value?.id ?: return@LaunchedEffect
        playMemory.put(PLAY_KEY, id, PlaySnapshot(luckState, leader ?: emptyList(), stats))
    }

    val refreshLeader: suspend () -> Unit = {
        try {
            leader = Shrine.omikujiLeaderboard()
        } catch (_: Exception) {
        }
    }
    val scope = rememberCoroutineScope()
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

                    val fresh = Shrine.onlineStats()
                    if (fresh != stats) stats = fresh
                } catch (_: Exception) {

                }
                delay(30_000L)
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val today = Shrine.omikujiToday()
            luckState = if (today > 0) LuckState.Drawn(today) else LuckState.NotDrawn
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(Unit) { refreshLeader() }

    val playScroll = rememberScrollState()

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
                TopBarIcon(
                    icon = Icons.Rounded.Mail,
                    contentDescription = "社务箱",
                    onClick = onOpenInbox,
                    badgeCount = unreadCount,
                )
            },
        )

        ShrineStaleBanner()

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
                    }
                }
            }

            val leaderBoard = leader
            if (!leaderBoard.isNullOrEmpty()) {
                Spacer(Modifier.height(12.dp))
                ArisuCard(padding = 20.dp, watermark = true) {
                    Column(Modifier.fillMaxWidth()) {

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

                        val podium = leaderBoard.take(3)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            PodiumSlot(podium.getOrNull(1), 16.dp, Modifier.weight(1f))
                            PodiumSlot(podium.getOrNull(0), 26.dp, Modifier.weight(1f))
                            PodiumSlot(podium.getOrNull(2), 10.dp, Modifier.weight(1f))
                        }

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
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel(text = "社员统计")
            val st = stats

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
    }
}

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
        ArisuAvatar(
            seed = 0,
            name = row.nickname,
            size = if (first) 52.dp else 42.dp,
            role = row.role,
            ring = true,
            url = row.avatar,
        )
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

        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .height(stepHeight)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(if (first) Vermilion.copy(alpha = 0.16f) else NeuShadow.copy(alpha = 0.10f))
        )
    }
}

@Composable
private fun LeaderRow(row: OmikujiLeaderRow) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            Text(
                text = row.nickname,
                style = MaterialTheme.typography.bodyMedium,
                color = row.role.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))

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

@Composable
private fun StatColumnNum(
    value: String,
    unit: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            color = if (highlight) MaterialTheme.colorScheme.primary else InkTitle,
            lineHeight = 38.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = unit, style = MaterialTheme.typography.labelSmall, color = InkMuted)
    }
}
