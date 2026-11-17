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

/**
 * 帖子卡统一高度：徽标 + 两行标题 + 两行介绍语 + 署名行 + 计数行的最坏情况预算。
 * 行高部分用 sp 换算（随系统字体缩放），间隙与内边距固定——
 * 卡高不再随内容起伏（v0.0.1.15 用户点名「高矮要统一」）。
 */
@Composable
private fun postCardUniformHeight(): Dp {
    // 徽标行 18 + 标题两行(titleLarge 28×2) + 介绍两行(bodySmall 19×2)
    // + 署名行 18 + 计数行 18，随系统字体缩放
    val textPart = with(LocalDensity.current) { (18f + 28f * 2 + 19f * 2 + 18f + 18f).sp.toDp() }
    return textPart + 6.dp * 4 + 12.dp * 2
}

/**
 * 茶话帖子卡片，论坛通行的「左图右文」解剖：
 * 封面柱占卡宽 36.6%、上下通高，图裁切不求完整——有封面用封面，
 * 没有就用作者头像充当，两者皆无落到人像剪影；帖子编号压在封面下缘。
 * 右列：徽标 / 标题（中偏上、titleLarge 醒目） / 介绍语 / 底部压齐的
 * 昵称+职务与顶·评·藏计数+时间。
 */
@Composable
fun PostCard(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 封面摘要：作者填写的介绍语优先，未填则取正文纯文本
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
        // 内容行只排右列、卡高统一固定（postCardUniformHeight）；封面柱作为覆盖层铺满整卡。
        // （此前用 IntrinsicSize.Min 让封面参与内在测量，每张卡多一整轮测量，
        //   列表里成排的卡明显拖慢滚动。）
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onClick() },
        ) {
            Row(Modifier.fillMaxWidth().height(postCardUniformHeight())) {
                // 封面柱 36.6%（2026-10-04 用户给定，浮点权重精确到小数）
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
                    // 标题停在中偏上：徽标之下垫一段弹性空白（上方 1 : 下方 4，
                    // 短内容卡标题中心约四分之一卡高处），正文之后的大弹簧
                    // 把署名/计数/时间压到卡底（2026-10-04 用户定版）
                    Spacer(Modifier.weight(1f))
                    // 下方弹簧见介绍语之后的 weight(4f)
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
                            // 徽标占了整行预算，介绍语让位为单行，保证固定高内放得下
                            maxLines = if (hasBadges) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.weight(4f))
                    // 署名行（贴底）：职务徽标回到昵称之后
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
                    // 底行：计数靠左，时间贴底靠右
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
            // 封面柱：与内容行同宽同高（36.6% 分栏、上下通高）
            Row(Modifier.matchParentSize()) {
                PostCover(post = post, modifier = Modifier.weight(36.6f).fillMaxHeight())
                Spacer(Modifier.weight(63.4f))
            }
        }
    }
}

/**
 * 封面柱：占卡宽三成、上下通高，图裁切不求完整。
 * 封面图优先，没有就用作者头像充当，都没有则人像剪影占位；
 * 帖子编号压在下缘的暗渐变上，等宽小字。
 */
@Composable
private fun PostCover(post: Post, modifier: Modifier = Modifier) {
    val cover = post.coverUrl
    // 封面柱只占卡宽三成：800px 缩略图足够，原图（常见 2048px、一两 MB）
    // 在列表里既费流量也费解码；头像兜底同理走 400
    val model = thumbUrl(cover, 800) ?: thumbUrl(post.authorAvatar, 400)
    Box(
        modifier.background(NeuAvatarBg),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            // 与头像同一套淡入：网络加载完成后 220ms 浮现，内存缓存命中同帧直出
            // （滚动回看时不重播动画，十张卡同屏不齐跳）
            var loaded by androidx.compose.runtime.remember(model) { androidx.compose.runtime.mutableStateOf(false) }
            var instant by androidx.compose.runtime.remember(model) { androidx.compose.runtime.mutableStateOf(false) }
            val imgAlpha = if (instant) 1f
            else androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (loaded) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(220),
                label = "coverFade",
            ).value
            // matchParentSize 不参与内在尺寸测量：卡高由右列内容决定，图只负责填满
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
                // 封面压字是「图上元素」，恒用黑渐变+白字，不随主题走：
                // 夜里 InkTitle 会变成近白，用它做渐变会让白字沉进灰白里（2026-10-06 用户指出）
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

/**
 * 绘马卡片：头像在左，编号+标题+状态 → 标签 → 摘要 → 元信息（回复计数靠右）。
 */
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
                        // 单行省略：标题过长不再换行，整列等高（2026-10-07 用户拍板）
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatePill(ema.state)
                }

                // 标签行恒渲染：无标签时放一枚隐形占位胶囊撑住行高，卡才等高
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

                // 摘要行恒渲染：空摘要的绘马也占一行，底部元信息对齐
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

/** 「键 —— 值」一行，右对齐。 */
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
