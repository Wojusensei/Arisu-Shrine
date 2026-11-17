package dev.arisu.shrine.core.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuBaseHigh
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.NeuShadow
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.nav.ArisuTab

/* ══════════════════════════════════════════════════════════════════
 * 标题栏
 * ══════════════════════════════════════════════════════════════════ */

/**
 * 页面标题栏。背景保持透明，直接透出页面底色，
 * 只用一条极淡的发丝线收边——比实心色块透气。
 */
@Composable
fun ArisuTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    large: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth()) {
        if (large) MistTopDecor(dense = true) else MistTopDecor(dense = false)
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (large) 74.dp else 58.dp)
                .padding(start = if (onBack == null) 24.dp else 2.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = if (large) MaterialTheme.typography.headlineSmall
                    else MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            Spacer(Modifier.width(4.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
        )
        }
    }
}

/**
 * 顶栏烟雾：柔雾团从屏幕两侧缭绕进来，画在标题之下（纯装饰）。
 * dense=大标题页两簇；二级页只右簇、更淡——层次靠浓度分级。
 */
@Composable
private fun BoxScope.MistTopDecor(dense: Boolean) {
    Box(
        Modifier
            .matchParentSize()
            .drawBehind {
                // 雾收敛：每侧一团主雾足矣，不再满屏铺
                fogBlob(Offset(size.width - 40.dp.toPx(), -30.dp.toPx()), 210.dp.toPx(), 0.035f)
                if (dense) {
                    fogBlob(Offset(30.dp.toPx(), -20.dp.toPx()), 170.dp.toPx(), 0.026f)
                } else {
                    fogBlob(Offset(size.width - 280.dp.toPx(), -10.dp.toPx()), 110.dp.toPx(), 0.016f)
                }
            }
    ) {
        // 一朵风送云浮在雾上（大页大些浓些，二级页小些淡些）
        Icon(
            imageVector = ShrineIcons.Kumo,
            contentDescription = null,
            tint = Vermilion.copy(alpha = if (dense) 0.038f else 0.03f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(width = if (dense) 130.dp else 100.dp, height = if (dense) 54.dp else 42.dp)
                .offset(x = 24.dp, y = 2.dp),
        )
    }
}

/** 标题栏上的图标按钮，可带右上角未读数字。 */
@Composable
fun TopBarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(modifier) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(21.dp),
            )
        }
        if (badgeCount > 0) {
            UnreadBadge(
                count = badgeCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 3.dp),
            )
        }
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 底部导航
 *
 * 通底的一条实栏：不留外边距、不做悬浮胶囊，顶边一条极细暗线收口，
 * 栏体本身一直延伸到屏幕最下沿（导航条安全区也算在内）。
 * 四个 slot 等宽，**图标与名称全程都显示**，选中项整体转为朱色。
 * ══════════════════════════════════════════════════════════════════ */

@Composable
fun ArisuBottomBar(
    current: ArisuTab?,
    onSelect: (ArisuTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(NeuBaseHigh)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NeuShadow.copy(alpha = 0.5f))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .drawBehind {
                    fogBlob(Offset(size.width - 20.dp.toPx(), 0.dp.toPx()), 150.dp.toPx(), 0.026f)
                }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArisuTab.entries.forEach { tab ->
                BottomBarItem(
                    tab = tab,
                    selected = tab == current,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: ArisuTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) Vermilion else InkMuted
    val scale by animateFloatAsState(if (selected) 1f else 0.96f, label = "tabScale")

    Column(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier
                .size(21.dp)
                .scale(scale),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/* ══════════════════════════════════════════════════════════════════
 * 底部面板的头部
 * ══════════════════════════════════════════════════════════════════ */

@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 34.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
        Box(Modifier.fillMaxWidth()) {
            // 樱花角饰：弹层标题右上角两瓣，压在标题层之下（纯装饰）
            Icon(
                imageVector = ShrineIcons.Sakura,
                contentDescription = null,
                tint = Vermilion.copy(alpha = 0.12f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .offset(x = 8.dp, y = (-6).dp)
                    .rotate(18f),
            )
            Icon(
                imageVector = ShrineIcons.Sakura,
                contentDescription = null,
                tint = Vermilion.copy(alpha = 0.08f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .offset(x = (-14).dp, y = 4.dp)
                    .rotate(-24f),
            )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        }
    }
}
