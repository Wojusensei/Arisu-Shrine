package dev.arisu.shrine.core.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.arisu.shrine.core.design.NeuInset
import dev.arisu.shrine.core.design.NeuLight

/**
 * 子板块切换标签，选中项下方是一条短下划线。
 */
@Composable
fun <T> UnderlineTabs(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    evenWeight: Boolean = true,
    /** 一格里字长的场景（绘马的 enhance/questions）：缩一档字号并压紧左右留白，保证一行放得下 */
    compact: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (!evenWeight) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val isSelected = item == selected
                val color by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabColor",
                )
                Column(
                    Modifier
                        .then(if (evenWeight) Modifier.weight(1f) else Modifier)
                        .clickable { onSelect(item) }
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label(item),
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                        color = color,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = if (compact) 4.dp else 14.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .width(if (isSelected) 20.dp else 0.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

/** 排序栏：最新 / 最热 / 精华，等宽分布，选中项短下划线（与板块 tab 同语言）。 */
@Composable
fun <T> SortBar(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val color by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "sortColor",
            )
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(item) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .width(if (isSelected) 22.dp else 0.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * 分段控件式的分类切换。
 *
 * 新拟物里这是最经典的一个件：一条**压进去**的轨道 + 一枚**凸出来**的滑块。
 */
@Composable
fun <T> PillTabs(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    evenWeight: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .neuInset(shape = RoundedCornerShape(16.dp), color = NeuInset, highlight = true)
            .padding(4.dp)
            .then(if (!evenWeight && scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
        // 边距收窄：等宽分布是硬法则，六格标签在 360dp 屏上也要放得下完整字样
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            Box(
                Modifier
                    .then(if (evenWeight) Modifier.weight(1f) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isSelected) Modifier
                            .background(NeuLight.copy(alpha = 0.9f))
                            .softCard(shape = RoundedCornerShape(12.dp), elevation = 4.dp)
                        else Modifier
                    )
                    .clickable { onSelect(item) }
                    .padding(horizontal = 7.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** 保留给旧调用点。 */
