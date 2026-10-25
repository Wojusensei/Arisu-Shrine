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

internal const val MIN_LOADING_MILLIS = 420L

internal const val EXIT_LOADING_MILLIS = 180

internal fun loadingRemainder(elapsedMillis: Long, minMillis: Long = MIN_LOADING_MILLIS): Long =
    (minMillis - elapsedMillis.coerceAtLeast(0L)).coerceAtLeast(0L)

internal fun contentRevealDelay(elapsedMillis: Long): Long =
    loadingRemainder(elapsedMillis) + EXIT_LOADING_MILLIS

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

@Composable
fun ShrineEmptyState(
    note: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    FadeIn(modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 76.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {

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
                text = "少女祈祷中",
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

@Composable
fun ShrineCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

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

                kotlinx.coroutines.withTimeout(12_000L) { onRefresh() }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                isRefreshing = false
            }
        }
    }

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

internal fun pullAlpha(offsetPx: Float, triggerPx: Float, isRefreshing: Boolean): Float =
    if (isRefreshing) 1f else (offsetPx / triggerPx).coerceIn(0f, 1f)
