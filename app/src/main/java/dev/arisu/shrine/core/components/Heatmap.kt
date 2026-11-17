package dev.arisu.shrine.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ColumnScope
import dev.arisu.shrine.core.design.InkMuted
import dev.arisu.shrine.core.design.Vermilion
import dev.arisu.shrine.core.design.heatColor
import dev.arisu.shrine.data.model.TileDay
import java.time.LocalDate

/**
 * 参拜热度：近一年的方格图，53 列 × 7 行，最后一格是今天。
 *
 * 服务端从「今天往前 370 天」连续发出瓷砖，起点落在星期几不固定；
 * 这里按起点补齐到周一再切列——不补的话整张表会随起点整体错位
 * （用户实测：星期天那格落进了星期六一行）。
 */
@Composable
fun HeatmapCard(
    tiles: List<TileDay>,
    header: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    // 切周列是纯计算但每次重组都要重跑一遍（逐日 LocalDate 解析），按数据记住结果
    val columns = remember(tiles) { tiles.weekColumns() }
    val scroll = rememberScrollState()

    // 默认滚到最右端：最新的一周在最右边。
    LaunchedEffect(scroll, columns.size) {
        snapshotFlow { scroll.maxValue }.collect { max ->
            if (max > 0) scroll.scrollTo(max)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Column { header() } }
            Spacer(Modifier.width(6.dp))
            TileLegend()
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth()) {
            WeekdayLabels()
            Spacer(Modifier.width(5.dp))
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                columns.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day -> TileSquare(day?.level ?: 0) }
                        repeat(7 - week.size) { TileSquare(0) }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "今天",
                style = MaterialTheme.typography.labelSmall,
                color = Vermilion,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 周一 / 周三 / 周五 三行标签，行高与格子严格对齐。 */
@Composable
private fun WeekdayLabels() {
    val labels = listOf("周一", "", "周三", "", "周五", "", "")
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEach { label ->
            Box(
                Modifier
                    .width(22.dp)
                    .height(10.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                        ),
                        color = InkMuted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TileSquare(level: Int) {
    Box(
        Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(heatColor(level))
    )
}

@Composable
private fun TileLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "少",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
        )
        Spacer(Modifier.width(4.dp))
        for (level in 0..4) {
            TileSquare(level)
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = "多",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
        )
    }
}

/**
 * 按「周一起始」切周：首列前补 null 占位，让每一列的第 0 行都是周一。
 * 服务端保证最后一格是今天；date 是上海时区的 yyyy-MM-dd。
 */
internal fun List<TileDay>.weekColumns(): List<List<TileDay?>> {
    if (isEmpty()) return emptyList()
    val first = runCatching { LocalDate.parse(first().date) }.getOrNull()
    // 解析不了就按原样切：宁可错位也不崩
    val pad = first?.let { it.dayOfWeek.value - 1 } ?: 0
    val padded = List<TileDay?>(pad.coerceIn(0, 6)) { null } + this
    return padded.chunked(7)
}
