package dev.arisu.shrine.core.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuShadow
import kotlin.math.roundToInt

@Composable
fun ScrollTopOrb(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .shadow(6.dp, CircleShape, ambientColor = NeuShadow.copy(alpha = 0.3f), spotColor = NeuShadow.copy(alpha = 0.55f))
                .clip(CircleShape)
                .background(NeuBaseHigh)
                .border(1.dp, NeuShadow.copy(alpha = 0.3f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            YinYangOrb(size = 26.dp)
        }
    }
}

@Composable
fun ScrollTopOrb(
    listState: LazyListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300
        }
    }
    ScrollTopOrb(visible = visible, onClick = onClick, modifier = modifier)
}

@Composable
fun ScrollTopOrb(
    scrollState: ScrollState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible by remember(scrollState) { derivedStateOf { scrollState.value > 500 } }
    ScrollTopOrb(visible = visible, onClick = onClick, modifier = modifier)
}

private const val GLIDE_SECONDS = 0.7f
private const val MIN_SPEED = 4000f
private const val MAX_SPEED = 42000f

suspend fun LazyListState.smoothScrollToTop() {
    if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) return

    val info = layoutInfo
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat().coerceAtLeast(1f)
    val visible = info.visibleItemsInfo
    val avg = if (visible.isEmpty()) viewport else visible.sumOf { it.size }.toFloat() / visible.size
    val est = firstVisibleItemScrollOffset + avg * firstVisibleItemIndex
    val speed = (est / GLIDE_SECONDS).coerceIn(MIN_SPEED, MAX_SPEED)

    scroll {
        var last = withFrameNanos { it }
        var stalled = 0
        while (firstVisibleItemIndex > 0) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            last = now
            val moved = -scrollBy(-speed * dt)

            if (moved < 0.5f) {
                if (++stalled > 3) break
            } else {
                stalled = 0
            }
        }
    }
    val off = firstVisibleItemScrollOffset
    if (off > 0) {
        val tail = (off / speed * 1000f).roundToInt().coerceIn(120, 420)
        animateScrollBy(-off.toFloat(), tween(durationMillis = tail, easing = LinearOutSlowInEasing))
    }
}

suspend fun ScrollState.smoothScrollToTop() {
    val dist = value
    if (dist <= 0) return
    val speed = (dist / GLIDE_SECONDS).coerceIn(MIN_SPEED, MAX_SPEED)
    val duration = (dist / speed * 1000f).roundToInt().coerceIn(140, 700)
    animateScrollBy(-dist.toFloat(), tween(durationMillis = duration, easing = LinearOutSlowInEasing))
}
