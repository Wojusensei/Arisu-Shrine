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

/**
 * 头像：有网络 URL 时用 Coil 加载，加载失败或没设置时
 * 回退到一枚暖灰的人像剪影（压在浅浅的凹槽圆底上）。
 * [ring] + [role] 画身份环：环不贴头像，留一圈空隙（与个人主页大头像一致）。
 */
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
            // 加载完成前露出底层占位底色，完成后 220ms 淡入——不再有生硬的灰块切换。
            // 内存命中（图已在缓存里）同帧直出，不再每次进屏都跑一遍淡入——
            // 切页时十几个头像同时起动画，全在给转场添乱
            var loaded by androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf(false) }
            var instant by androidx.compose.runtime.remember(url) { androidx.compose.runtime.mutableStateOf(false) }
            val imgAlpha = if (instant) 1f
            else androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (loaded) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(220),
                label = "avatarFade",
            ).value
            AsyncImage(
                // 头像显示尺寸最大也就 130dp 上下，挂 400px 缩略图够用：
                // 原图动辄一两 MB，一屏十几个头像全按原图拉是列表卡顿的大头
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

/**
 * 个人主页的大头像：一圈身份色细描边 + 一圈留白，双层套环。
 */
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

/** 列表里的小头像，带一圈极浅描边。 */
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

/** 留给旧调用点：新拟物下不再按种子取色。 */
fun avatarColorFor(seed: Int): Color = NeuAvatarBg

fun avatarInitial(name: String): String = name.trim().take(1).ifEmpty { "栖" }
