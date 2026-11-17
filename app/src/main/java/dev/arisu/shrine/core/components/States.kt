package dev.arisu.shrine.core.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 入场淡入：状态切换时内容不再「啪」地一下出现。
 * 包一层即可；离场的突变由内容的 FadeIn 承接一半，感知上已经顺滑。
 */
@Composable
fun FadeIn(
    modifier: Modifier = Modifier,
    durationMillis: Int = 220,
    content: @Composable () -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(durationMillis)),
        exit = fadeOut(tween(durationMillis)),
        modifier = modifier,
    ) {
        content()
    }
}

/** 最短显示时长：出现后至少停留多久，避免数据秒回时动画被硬切。 */
internal const val MIN_LOADING_MILLIS = 420L

/** 覆盖层（少女祈祷中）退场淡出的时长；内容画面等它播完才入场，衔接不重叠。 */
internal const val EXIT_LOADING_MILLIS = 180

/** 计算 [visible] 变 false 后还需等待多久（出现不足最短时长时补足差额；elapsed 异常时按 0 处理）。 */
internal fun loadingRemainder(elapsedMillis: Long, minMillis: Long = MIN_LOADING_MILLIS): Long =
    (minMillis - elapsedMillis.coerceAtLeast(0L)).coerceAtLeast(0L)

/**
 * 内容放行延迟：从「少女祈祷中」出现到此刻经过 [elapsedMillis]，
 * 还要多久之后内容画面才允许出现 = 补足最短显示 + 退场动画播完。
 * 纯函数，单测覆盖（测试清单 T28）。
 */
internal fun contentRevealDelay(elapsedMillis: Long): Long =
    loadingRemainder(elapsedMillis) + EXIT_LOADING_MILLIS

/**
 * 双向淡入淡出：[visible] 为 true 淡入、为 false 淡出，**离场动画一定播放完**。
 * 另设最短显示时长：数据秒回时「少女祈祷中」也至少停留 [MIN_LOADING_MILLIS] 毫秒，
 * 不会刚浮现就被掐灭。
 * 之前的 [FadeIn] 是只进不出的一次性动画，用于 if/else 直接切换时会瞬间消失——加载态请用本组件。
 */
@Composable
fun FadeInOut(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var effectiveVisible by remember { mutableStateOf(false) }
    var shownAt by remember { mutableStateOf(0L) }
    LaunchedEffect(visible) {
        if (visible) {
            shownAt = System.currentTimeMillis()
            effectiveVisible = true
        } else if (effectiveVisible) {
            delay(loadingRemainder(System.currentTimeMillis() - shownAt))
            effectiveVisible = false
        }
    }
    AnimatedVisibility(
        visible = effectiveVisible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(EXIT_LOADING_MILLIS)),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * 加载衔接协调器：页面级「少女祈祷中」与新画面的交接由它统一编排——
 *
 * · 加载开始 → 覆盖层淡入，内容画面隐去（alpha 0，但保持组合，滚动位置不丢）；
 * · 加载结束 → 覆盖层至少停留 [MIN_LOADING_MILLIS] 再退场，
 *   **退场动画播完之后**内容画面才淡入——两者不在同一帧叠影，
 *   消除「动画还没消失画面就出来了」的断裂感（用户实测反馈）。
 *
 * 下拉刷新不受影响：刷新期间页面状态仍是 Ok，[loading] 不会变 true，
 * 旧内容始终可见。整体节奏也按要求提速（420ms 最短显示 + 180ms 退场）。
 *
 * [modifier] 必须给出确定尺寸（fillMaxSize / weight 等）：
 * 外层 Box 的两块子层都是 matchParentSize，谁也不撑开谁——
 * 不给尺寸会整个塌成 0×0（v0.0.0.6 自测抓到的第一版 bug）。
 *
 * 时序数字全部出自纯函数 [contentRevealDelay]，有单测。
 */
@Composable
fun ShrineLoadGate(
    loading: Boolean,
    modifier: Modifier = Modifier,
    overlay: @Composable () -> Unit = { ShrineLoadingState() },
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    var overlayShown by remember { mutableStateOf(false) }
    var contentShown by remember { mutableStateOf(false) }
    var shownAt by remember { mutableStateOf(0L) }
    LaunchedEffect(loading) {
        if (loading) {
            shownAt = System.currentTimeMillis()
            contentShown = false
            overlayShown = true
        } else if (overlayShown) {
            delay(contentRevealDelay(System.currentTimeMillis() - shownAt))
            overlayShown = false
            contentShown = true
        } else {
            contentShown = true
        }
    }
    val contentAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (contentShown) 1f else 0f,
        animationSpec = tween(140),
        label = "loadGateContentAlpha",
    )
    androidx.compose.foundation.layout.Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = contentAlpha }
        ) { content() }
        FadeInOut(visible = overlayShown, modifier = Modifier.matchParentSize()) { overlay() }
    }
}

/**
 * 空状态 / 失败状态：图标压在一枚**凹陷的圆槽**里。
 * 与加载态共用「少女祈祷中」大标题。
 */
@Composable
fun ShrineEmptyState(
    note: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    title: String = "少女祈祷中",
) {
    FadeIn(modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 76.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                // 云 + 樱：圆徽左上一朵和风云，右肩与左下各飘一瓣樱（纯装饰）
                Icon(
                    imageVector = ShrineIcons.Kumo,
                    contentDescription = null,
                    tint = dev.arisu.shrine.core.design.Vermilion.copy(alpha = 0.05f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(width = 44.dp, height = 18.dp)
                        .offset(x = (-34).dp, y = (-30).dp)
                        .scale(scaleX = -1f, scaleY = 1f),
                )
                Icon(
                    imageVector = ShrineIcons.Sakura,
                    contentDescription = null,
                    tint = dev.arisu.shrine.core.design.Vermilion.copy(alpha = 0.12f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .offset(x = 24.dp, y = (-8).dp)
                        .rotate(24f),
                )
                Icon(
                    imageVector = ShrineIcons.Sakura,
                    contentDescription = null,
                    tint = dev.arisu.shrine.core.design.Vermilion.copy(alpha = 0.08f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(13.dp)
                        .offset(x = (-28).dp, y = 6.dp)
                        .rotate(-28f),
                )
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .neuInset(shape = CircleShape, color = dev.arisu.shrine.core.design.NeuInset),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ShrineIcons.Torii,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = if (onClick != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier,
            )
        }
    }
}

/** 列表底部的收尾。 */
@Composable
fun ShrineListEnd(modifier: Modifier = Modifier, text: String = "到底了") {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(1.dp)
                .width(22.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            text = "  $text  ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .height(1.dp)
                .width(22.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/** 「少女祈祷中……」的加载提示条：阴阳玉匀速旋转。 */
@Composable
fun ShrineRefreshingHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YinYangOrb(
            size = 14.dp,
            primary = MaterialTheme.colorScheme.primary,
            base = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "少女祈祷中……",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 页面级加载态：凹槽圆底里一枚旋转的阴阳玉。
 * 与空状态共用「少女祈祷中」大标题，但只有加载态会转。
 */
@Composable
fun ShrineLoadingState(
    modifier: Modifier = Modifier,
    note: String = "少女祈祷中",
) {
    FadeIn(modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 76.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .neuInset(shape = CircleShape, color = dev.arisu.shrine.core.design.NeuInset),
                contentAlignment = Alignment.Center,
            ) {
                YinYangOrb(
                    size = 34.dp,
                    primary = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    base = MaterialTheme.colorScheme.surface,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 页面级说明小字。 */
@Composable
fun ShrineCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * 弱网缓存提示条：当前展示的是上一次缓存的数据。
 * 收集 Shrine.staleNotice，无缓存回退时不显示。
 */
@Composable
fun ShrineStaleBanner(modifier: Modifier = Modifier) {
    val notice by dev.arisu.shrine.data.repo.Shrine.staleNotice.collectAsStateWithLifecycle()
    FadeIn(modifier = modifier) {
        if (notice != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = notice.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Snackbar 风格的操作结果提示条：淡入浮现，3 秒后自动淡出消失。
 * [text] 为 null 即隐藏； disappearing 期间保持最后一条文案，避免退出动画空白。
 */
@Composable
fun ShrineNoticeBar(
    text: String?,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long = 3000,
    onDismiss: () -> Unit = {},
) {
    var shown by remember { mutableStateOf("") }
    if (text != null) shown = text
    val visible = text != null
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + androidx.compose.animation.expandVertically(tween(180)),
        exit = fadeOut(tween(260)),
        modifier = modifier,
    ) {
        if (visible) {
            LaunchedEffect(text) {
                delay(autoDismissMillis)
                onDismiss()
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.inverseSurface)
                .padding(horizontal = 18.dp, vertical = 11.dp)
        ) {
            Text(
                text = shown,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 下拉刷新（自研）：整页跟手下拉，顶部浮现下层的「少女祈祷中」+ 旋转阴阳玉——
 * 无底板无描边，只是浮在内容之下的一个图层，随拉动距离淡入；松手超过阈值触发刷新并停驻，
 * 刷新完成后整页回弹、指示器淡出。不依赖 M3 组件，行为完全可控。
 *
 * [onRefresh] 是 suspend：组件内部持有刷新状态并 try/finally 强制复位，
 * 调用方不可能忘记把指示器收回（此前多页「少女祈祷中」不消失的根因）。
 *
 * 纯函数部分（[pullAlpha]、[loadingRemainder]）有单测：刷新状态机见测试清单 T19/T21。
 */
@Composable
fun ShrinePullRefreshBox(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val trigger = with(density) { 64.dp.toPx() }
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun startRefresh() {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            try {
                // 兜底超时：半开连接（飞行模式/VPN/NAT 切换后连接池里的死连接）
                // 会让请求一直挂到网络层超时，最坏叠加 apiCall 重试可达一分钟，
                // 指示器全程停驻且第二次下拉被静默忽略——用户观感就是
                // 「少女祈祷中不消失」。12s 强制收场，超时视为本次失败，立即可重拉。
                kotlinx.coroutines.withTimeout(12_000L) { onRefresh() }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // 超时失败：内容仍是旧数据，静默收起指示器
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                isRefreshing = false
            }
        }
    }

    // 刷新状态与位移联动：开始刷新→停驻在阈值；刷新完成→回弹归零（慢慢消失）
    androidx.compose.runtime.LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            offset.animateTo(trigger, tween(180))
        } else if (offset.value > 0f) {
            offset.animateTo(0f, tween(340))
        }
    }

    val nested = remember(offset) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                // 内容滚到顶后剩下的下拉量 → 全页跟手（带阻尼）
                if (available.y > 0f) {
                    val d = if (offset.value > 0f) available.y else available.y * 0.55f
                    scope.launch {
                        offset.snapTo((offset.value + d).coerceAtMost(trigger * 1.35f))
                    }
                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                if (!isRefreshing && offset.value >= trigger * 0.92f) {
                    startRefresh()
                } else if (!isRefreshing && offset.value > 0f) {
                    offset.animateTo(0f, tween(260))
                }
                return available
            }
        }
    }

    Box(modifier.nestedScroll(nested)) {
        Box(
            Modifier
                .graphicsLayer { translationY = offset.value }
        ) {
            content()
        }
        // 指示器此前在组合期读 offset.value：下拉全程逐帧重组整个刷新容器，
        // 观感是「下拉不跟手」。改成两段——可见性用 derivedStateOf（只在真假翻转时
        // 重组一次），透明度与位移放进 graphicsLayer 的 lambda（绘制阶段读动画值，
        // 逐帧读也不触发重组）。视觉与手感与原来完全一致
        val indicatorVisible by remember {
            androidx.compose.runtime.derivedStateOf {
                isRefreshing || pullAlpha(offset.value, trigger, isRefreshing) > 0.01f
            }
        }
        if (indicatorVisible) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        alpha = pullAlpha(offset.value, trigger, isRefreshing)
                        translationY = offset.value * 0.45f
                    }
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YinYangOrb(size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "少女祈祷中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 指示器透明度：拉动距离占比（0..1），刷新中恒为 1。 */
internal fun pullAlpha(offsetPx: Float, triggerPx: Float, isRefreshing: Boolean): Float =
    if (isRefreshing) 1f else (offsetPx / triggerPx).coerceIn(0f, 1f)
