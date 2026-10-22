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

@Composable
fun HeatmapCard(
    tiles: List<TileDay>,
    header: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {

    val columns = remember(tiles) { tiles.weekColumns() }
    val scroll = rememberScrollState()

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

internal fun List<TileDay>.weekColumns(): List<List<TileDay?>> {
    if (isEmpty()) return emptyList()
    val first = runCatching { LocalDate.parse(first().date) }.getOrNull()

    val pad = first?.let { it.dayOfWeek.value - 1 } ?: 0
    val padded = List<TileDay?>(pad.coerceIn(0, 6)) { null } + this
    return padded.chunked(7)
}
