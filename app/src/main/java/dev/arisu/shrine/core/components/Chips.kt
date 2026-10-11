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

@Composable
fun StatePill(state: EmaState, modifier: Modifier = Modifier) {
    DotPill(text = state.label, dotColor = state.color, textColor = InkMuted, modifier = modifier)
}

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

@Composable
fun RedDot(modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}
