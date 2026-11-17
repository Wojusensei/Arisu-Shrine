package dev.arisu.shrine.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角规范。
 * 走的是「克制的现代」路线：卡片 18dp、控件 12dp、胶囊 999dp，
 * 不用过大的圆角，避免廉价感。
 */
val ArisuShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)
