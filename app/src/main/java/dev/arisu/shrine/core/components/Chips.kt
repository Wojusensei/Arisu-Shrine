package dev.arisu.shrine.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.core.design.CodeLabelStyle
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.OnAccent
import dev.arisu.shrine.data.model.EmaState
import dev.arisu.shrine.data.model.EmaTag
import dev.arisu.shrine.data.model.Role
import dev.arisu.shrine.data.model.accent

/* ──────────────────────────────────────────────────────────────────
 * 标签一律做成「中性凹槽 + 一枚小色点」。
 *
 * 之前每种标签都配一块粉色的圆角底，一屏下来五颜六色，
 * 这正是那种廉价的「AI 生成感」。颜色收敛到一枚 5dp 的点上，
 * 信息还在，页面却安静得多。
 * ────────────────────────────────────────────────────────────────── */

@Composable
private fun DotPill(
    text: String,
    dotColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    dot: Boolean = true,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .neuInset(shape = RoundedCornerShape(20.dp), color = NeuInset, highlight = false)
            // 全站徽标统一描边：文字色 30%（2026-10-04 用户拍板）
            .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 身份标签：神主 / 宫司 / 奉仕 / 临时奉仕 / 社员。 */
@Composable
fun RoleTag(role: Role, modifier: Modifier = Modifier) {
    DotPill(
        text = role.label,
        dotColor = role.accent,
        textColor = role.accent,
        modifier = modifier,
        bold = true,
    )
}

/** 绘马状态：open / in progress / resolved / closed。 */
@Composable
fun StatePill(state: EmaState, modifier: Modifier = Modifier) {
    DotPill(text = state.label, dotColor = state.color, textColor = InkMuted, modifier = modifier)
}

/** 绘马标签：enhance / bug / questions / tips。 */
@Composable
fun TagChip(tag: EmaTag, modifier: Modifier = Modifier, selected: Boolean = false) {
    DotPill(
        text = tag.label,
        dotColor = tag.color,
        textColor = if (selected) tag.color else InkMuted,
        modifier = modifier,
        bold = selected,
    )
}

/** 可选中的标签（筛选面板、发绘马）。选中时整颗染上标签色。 */
@Composable
fun TagChipSelectable(
    tag: EmaTag,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (selected) Modifier.background(tag.color)
                else Modifier.neuInset(shape = RoundedCornerShape(20.dp), color = NeuInset, highlight = false)
            )
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (selected) OnAccent else tag.color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = tag.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) OnAccent else InkMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * 实底小胶囊：[background] 必须真正画出来。
 * 之前经由 DotPill 转调时把 [background] 丢了——「通过」按钮的朱色底从未画出来过，
 * 白字压在浅色凹槽上等于隐形（社务箱审批按钮不可读的根因）。
 * 传 [NeuInset] 时保持凹陷槽质感，其余颜色按实底圆角绘制。
 */
@Composable
fun SoftPill(
    text: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (background == NeuInset) {
                    Modifier.neuInset(shape = RoundedCornerShape(20.dp), color = NeuInset, highlight = false)
                } else {
                    Modifier.background(background)
                }
            )
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 帖子编号 arisu_000001，等宽小字。 */
@Composable
fun CodeLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text,
        style = CodeLabelStyle,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** 未读数角标，超过 99 显示 99+。 */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val label = if (count > 99) "99+" else count.toString()
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 未读小红点。 */
@Composable
fun RedDot(modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}
