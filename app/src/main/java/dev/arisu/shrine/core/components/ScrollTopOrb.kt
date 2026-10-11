package dev.arisu.shrine.core.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuShadow

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
