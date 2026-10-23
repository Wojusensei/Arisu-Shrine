package dev.arisu.shrine.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.InkTitle
import dev.arisu.shrine.core.design.Paper
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.markdown.markdownToPlain
import dev.arisu.shrine.core.design.NeuAvatarBg
import dev.arisu.shrine.core.design.NeuAvatarGrey
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuInset
import coil3.compose.AsyncImage
import dev.arisu.shrine.core.net.thumbUrl
import dev.arisu.shrine.data.model.Ema
import dev.arisu.shrine.data.model.EmaTag
import dev.arisu.shrine.data.model.Post

@Composable
private fun postCardUniformHeight(): Dp {

    val textPart = with(LocalDensity.current) { (18f + 28f * 2 + 19f * 2 + 18f + 18f).sp.toDp() }
    return textPart + 6.dp * 4 + 12.dp * 2
}

@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val excerpt = remember(post.id, post.intro, post.body) {
        post.intro.ifBlank { markdownToPlain(post.body) }
    }

    NeuSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        depth = 5.dp,
        blur = 10.dp,
        color = NeuBaseHigh,
    ) {

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onClick() },
        ) {
            Row(Modifier.fillMaxWidth().height(postCardUniformHeight())) {

                Spacer(Modifier.weight(36.6f))
                Column(
                    Modifier
                        .weight(63.4f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = 15.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val hasBadges = post.pinned || post.essence
                    if (hasBadges) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (post.pinned) {
                                MetaBadge(icon = Icons.Rounded.PushPin, text = "置顶", tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                            }
                            if (post.essence) {
                                MetaBadge(text = "精华", tint = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (excerpt.isNotBlank()) {
                        Text(
                            text = excerpt,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,

                            maxLines = if (hasBadges) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.weight(4f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        RoleTag(role = post.authorRole)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCount(
                            icon = Icons.Rounded.ArrowUpward,
                            count = post.upCount,
                            tint = if (post.upvoted) Vermilion else InkMuted,
                        )
                        Spacer(Modifier.width(14.dp))
                        IconCount(
                            icon = Icons.Rounded.ChatBubbleOutline,
                            count = post.commentCount,
                            tint = InkMuted,
                        )
                        Spacer(Modifier.width(14.dp))
                        IconCount(
                            icon = Icons.Rounded.Star,
                            count = post.saveCount,
                            tint = InkMuted,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = post.createdAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            Row(Modifier.matchParentSize()) {
                PostCover(post = post, modifier = Modifier.weight(36.6f).fillMaxHeight())
                Spacer(Modifier.weight(63.4f))
            }
        }
    }
}

@Composable
private fun PostCover(post: Post, modifier: Modifier = Modifier) {
    val cover = post.coverUrl

    val model = thumbUrl(cover, 800) ?: thumbUrl(post.authorAvatar, 400)
    Box(
        modifier.background(NeuAvatarBg),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {

            var loaded by androidx.compose.runtime.remember(model) { androidx.compose.runtime.mutableStateOf(false) }
            var instant by androidx.compose.runtime.remember(model) { androidx.compose.runtime.mutableStateOf(false) }
            val imgAlpha = if (instant) 1f
            else androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (loaded) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(220),
                label = "coverFade",
            ).value

            AsyncImage(
                model = model,
                contentDescription = cover?.let { "帖子封面" } ?: post.authorName,
                contentScale = ContentScale.Crop,
                onSuccess = {
                    if (it.result.dataSource == coil3.decode.DataSource.MEMORY_CACHE) instant = true
                    else loaded = true
                },
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = imgAlpha },
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = NeuAvatarGrey,
                modifier = Modifier.size(44.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()

                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    )
                )
                .padding(top = 16.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            CodeLabel(text = post.id, color = Color.White)
        }
    }
}

@Composable
private fun MetaBadge(text: String, tint: Color, icon: ImageVector? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .neuInset(shape = RoundedCornerShape(20.dp), color = NeuInset, highlight = false)
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = tint,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun IconCount(icon: ImageVector, count: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(text = count.toString(), style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
fun EmaCard(
    ema: Ema,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val excerpt = remember(ema.id, ema.body) { markdownToPlain(ema.body) }

    NeuSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        depth = 5.dp,
        blur = 10.dp,
        color = NeuBaseHigh,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 15.dp, vertical = 14.dp),
        ) {
            ArisuAvatar(
                seed = 0,
                name = ema.authorName,
                size = 40.dp,
                role = ema.authorRole,
                ring = true,
                url = ema.authorAvatar,
                modifier = Modifier.align(Alignment.Top),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${ema.id}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = ema.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,

                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatePill(ema.state)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ema.tags.isEmpty()) {
                        TagChip(
                            EmaTag.entries.first(),
                            modifier = Modifier.alpha(0f),
                        )
                    } else {
                        ema.tags.forEach { tag ->
                            TagChip(tag)
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                }

                Text(
                    text = excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ema.authorName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    RoleTag(role = ema.authorRole)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = ema.createdAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    IconCount(
                        icon = Icons.Rounded.ChatBubbleOutline,
                        count = ema.replyCount,
                        tint = InkMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun KeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun HSpacer(width: Dp) = Spacer(Modifier.width(width))
