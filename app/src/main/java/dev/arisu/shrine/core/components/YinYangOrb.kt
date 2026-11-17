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

/**
 * 阴阳玉：匀速旋转，无回弹（设计语言 §七）。
 * 朱玉 + 底玉双色，各带一枚异色小点；外侧一圈细描边收住轮廓。
 *
 * @param size 直径；加载条 14dp、空态 32dp、启动页 72dp
 */
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

    // 画布被父级约束压扁时（如外层给过 fillMaxWidth），按参数画会在边上裁掉一角、
    // 看上去就不是正圆了：直径一律取画布短边，圆心取画布正中
    val fallbackPx = size.value * androidx.compose.ui.platform.LocalDensity.current.density
    Canvas(modifier = modifier.size(size)) {
        val side = minOf(this.size.width, this.size.height).takeIf { it > 0f } ?: fallbackPx
        val r = side / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val stroke = side * 0.035f
        val rr = r - stroke // 内圆半径

        drawCircle(color = ring, radius = r - stroke / 2, center = center, style = Stroke(stroke))

        rotate(degrees = if (rotating) angle else 0f, pivot = center) {
            // 底色全圆
            drawCircle(color = base, radius = rr, center = center)
            // 上半：朱
            drawArc(
                color = primary,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(r - rr, r - rr),
                size = Size(rr * 2, rr * 2),
            )
            // S 曲线：左瓣朱色凸向下，右瓣底色凸向上
            drawCircle(color = primary, radius = rr / 2, center = Offset(r - rr / 2, r))
            drawCircle(color = base, radius = rr / 2, center = Offset(r + rr / 2, r))
            // 两枚异色鱼眼
            drawCircle(color = base, radius = rr / 6, center = Offset(r - rr / 2, r))
            drawCircle(color = primary, radius = rr / 6, center = Offset(r + rr / 2, r))
        }
    }
}
