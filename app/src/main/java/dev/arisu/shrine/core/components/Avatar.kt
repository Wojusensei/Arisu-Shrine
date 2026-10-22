package dev.arisu.shrine.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import coil3.compose.AsyncImage
import dev.arisu.shrine.core.design.NeuAvatarBg
import dev.arisu.shrine.core.design.NeuAvatarGrey
import dev.arisu.shrine.core.design.NeuBase
import dev.arisu.shrine.core.design.NeuLight
import dev.arisu.shrine.core.design.NeuShadow
import dev.arisu.shrine.data.model.Role
import dev.arisu.shrine.data.model.accent

@Composable
fun ArisuAvatar(
    seed: Int,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    role: Role? = null,
    ring: Boolean = false,
    url: String? = null,
) {
    val ringed = ring && role != null
    val ringPad = if (ringed) 4.dp else 0.dp
    Box(modifier.size(size + ringPad * 2), contentAlignment = Alignment.Center) {
        if (ringed) {
            Box(
                Modifier
                    .size(size + ringPad * 2)
                    .clip(CircleShape)
                    .border(1.5.dp, role.accent, CircleShape)
            )
        }
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(NeuAvatarBg)
                .border(
                    width = 1.dp,
                    color = NeuShadow.copy(alpha = 0.35f),
                    shape = CircleShape,
                )
        )
        if (url != null) {

            var loaded by androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf(false) }
            var instant by androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf(false) }
            val imgAlpha = if (instant) 1f
            else androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (loaded) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(220),
                label = "avatarFade",
            ).value
            AsyncImage(

                model = dev.arisu.shrine.core.net.thumbUrl(url, 400),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onSuccess = {
                    if (it.result.dataSource == coil3.decode.DataSource.MEMORY_CACHE) instant = true
                    else loaded = true
                },
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .graphicsLayer { alpha = imgAlpha },
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = NeuAvatarGrey,
                modifier = Modifier.size(size * 0.58f),
            )
        }
    }
}

@Composable
fun ArisuAvatarRinged(
    seed: Int,
    name: String,
    role: Role,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    url: String? = null,
) {
    Box(modifier.size(size + 14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(1.5.dp, role.accent.copy(alpha = 0.55f), CircleShape)
        )
        Box(
            Modifier
                .size(size + 7.dp)
                .clip(CircleShape)
                .background(NeuBase)
        )
        ArisuAvatar(seed = seed, name = name, size = size, url = url)
    }
}

@Composable
fun ArisuAvatarOutlined(seed: Int, name: String, size: Dp = 40.dp, modifier: Modifier = Modifier, url: String? = null) {
    Box(
        modifier
            .size(size + 4.dp)
            .clip(CircleShape)
            .border(1.dp, NeuLight, CircleShape)
            .padding(2.dp)
    ) {
        ArisuAvatar(seed = seed, name = name, size = size, url = url)
    }
}

fun avatarColorFor(seed: Int): Color = NeuAvatarBg

fun avatarInitial(name: String): String = name.trim().take(1).ifEmpty { "栖" }
