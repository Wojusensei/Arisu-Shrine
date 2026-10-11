package dev.arisu.shrine.core.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun YinYangOrb(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    primary: Color = MaterialTheme.colorScheme.primary,
    base: Color = MaterialTheme.colorScheme.surfaceVariant,
    ring: Color = MaterialTheme.colorScheme.outlineVariant,
    rotating: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "yin-yang")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "yin-yang-angle",
    )

    Canvas(modifier = modifier.size(size)) {
        val side = size.toPx()
        val r = side / 2f
        val center = Offset(r, r)
        val stroke = side * 0.035f
        val rr = r - stroke

        drawCircle(color = ring, radius = r - stroke / 2, center = center, style = Stroke(stroke))

        rotate(degrees = if (rotating) angle else 0f, pivot = center) {

            drawCircle(color = base, radius = rr, center = center)

            drawArc(
                color = primary,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(r - rr, r - rr),
                size = Size(rr * 2, rr * 2),
            )

            drawCircle(color = primary, radius = rr / 2, center = Offset(r - rr / 2, r))
            drawCircle(color = base, radius = rr / 2, center = Offset(r + rr / 2, r))

            drawCircle(color = base, radius = rr / 6, center = Offset(r - rr / 2, r))
            drawCircle(color = primary, radius = rr / 6, center = Offset(r + rr / 2, r))
        }
    }
}
