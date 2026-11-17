package dev.arisu.shrine.core.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 神社自绘图标集：与 Material 图标同一 24dp 网格。
 * 风格：实心剪影——与启动图标同一套语言，小尺寸下轮廓依然清晰。
 */
object ShrineIcons {

    /** 鸟居实心剪影（与启动图标同一图形）。用于「神社祝部」tab。 */
    val Torii: ImageVector by lazy {
        ImageVector.Builder(
            name = "Torii",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(0.8f, 3.7f)
                quadTo(12f, 1.3f, 23.2f, 3.7f)
                lineTo(23.2f, 5.9f)
                quadTo(12f, 3.5f, 0.8f, 5.9f)
                close()
                moveTo(2.7f, 6.1f)
                lineTo(21.3f, 6.1f)
                lineTo(21.3f, 7.9f)
                lineTo(2.7f, 7.9f)
                close()
                moveTo(10.7f, 7.9f)
                lineTo(13.3f, 7.9f)
                lineTo(13.3f, 10.7f)
                lineTo(10.7f, 10.7f)
                close()
                moveTo(3.5f, 10.7f)
                lineTo(20.5f, 10.7f)
                lineTo(20.5f, 12.5f)
                lineTo(3.5f, 12.5f)
                close()
                moveTo(5.7f, 7.9f)
                lineTo(8f, 7.9f)
                lineTo(8f, 22.2f)
                lineTo(5.7f, 22.2f)
                close()
                moveTo(16f, 7.9f)
                lineTo(18.3f, 7.9f)
                lineTo(18.3f, 22.2f)
                lineTo(16f, 22.2f)
                close()
            }
        }.build()
    }

    /** 御神签筒实心剪影：三签头 + 沿口 + 圆底筒身。 */
    val Omikuji: ImageVector by lazy {
        ImageVector.Builder(
            name = "Omikuji",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 5.6f)
                lineTo(9.7f, 5.6f)
                lineTo(9.7f, 10.1f)
                lineTo(8f, 10.1f)
                close()
                moveTo(11.15f, 4.4f)
                lineTo(12.85f, 4.4f)
                lineTo(12.85f, 10.1f)
                lineTo(11.15f, 10.1f)
                close()
                moveTo(14.3f, 5.6f)
                lineTo(16f, 5.6f)
                lineTo(16f, 10.1f)
                lineTo(14.3f, 10.1f)
                close()
                moveTo(5.9f, 10.1f)
                lineTo(18.1f, 10.1f)
                lineTo(18.1f, 12f)
                lineTo(5.9f, 12f)
                close()
                moveTo(6.9f, 12f)
                lineTo(17.1f, 12f)
                lineTo(17.1f, 16.8f)
                quadTo(17.1f, 20.8f, 12f, 20.8f)
                quadTo(6.9f, 20.8f, 6.9f, 16.8f)
                close()
            }
        }.build()
    }

    /** 鲤鱼旗实心剪影：旗杆 + 鱼形风幡（眼用 even-odd 镂空）。用于「神社游会」tab。 */
    val Koinobori: ImageVector by lazy {
        ImageVector.Builder(
            name = "Koinobori",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4.6f, 1.8f)
                lineTo(6.3f, 1.8f)
                lineTo(6.3f, 22.2f)
                lineTo(4.6f, 22.2f)
                close()
            }
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(7.2f, 6.1f)
                curveTo(10.7f, 4f, 14.5f, 4.4f, 17f, 7f)
                lineTo(20.5f, 4f)
                lineTo(19.8f, 9.6f)
                lineTo(20.5f, 15.2f)
                lineTo(17f, 12.2f)
                curveTo(14.5f, 14.8f, 10.7f, 15.2f, 7.2f, 13.1f)
                close()
                moveTo(10.6f, 7.6f)
                lineTo(12f, 7.6f)
                lineTo(12f, 9f)
                lineTo(10.6f, 9f)
                close()
            }
        }.build()
    }

    /** 标题 T 字。Markdown 工具「标题」。 */
    val Title: ImageVector by lazy {
        ImageVector.Builder(
            name = "Title",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 4f)
                lineTo(19f, 4f)
                lineTo(19f, 7.2f)
                lineTo(13.6f, 7.2f)
                lineTo(13.6f, 20f)
                lineTo(10.4f, 20f)
                lineTo(10.4f, 7.2f)
                close()
            }
        }.build()
    }

    /** 燃烧中的香（参拜者御守）：火苗 + 香杆 + 香座，实心剪影。 */
    val Incense: ImageVector by lazy {
        ImageVector.Builder(
            name = "Incense",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // 火苗
                moveTo(12f, 2f)
                curveTo(13.6f, 3.8f, 14.8f, 5.4f, 14.8f, 7.1f)
                curveToRelative(0f, 1.55f, -1.25f, 2.8f, -2.8f, 2.8f)
                reflectiveCurveToRelative(-2.8f, -1.25f, -2.8f, -2.8f)
                curveToRelative(0f, -1.7f, 1.2f, -3.3f, 2.8f, -5.1f)
                close()
                // 香杆
                moveTo(11.25f, 10.6f)
                lineTo(12.75f, 10.6f)
                lineTo(12.75f, 19.2f)
                lineTo(11.25f, 19.2f)
                close()
                // 香座
                moveTo(7.2f, 20.2f)
                lineTo(16.8f, 20.2f)
                lineTo(16.8f, 22f)
                lineTo(7.2f, 22f)
                close()
            }
        }.build()
    }

    /** 绘马牌（even-odd 镂空边框）。通知「奉纳申请」。 */
    val Ema: ImageVector by lazy {
        ImageVector.Builder(
            name = "Ema",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(12f, 1.6f)
                lineTo(21.6f, 6.9f)
                lineTo(21.6f, 22.4f)
                lineTo(2.4f, 22.4f)
                lineTo(2.4f, 6.9f)
                close()
                moveTo(12f, 4.4f)
                lineTo(4.8f, 8.3f)
                lineTo(4.8f, 20f)
                lineTo(19.2f, 20f)
                lineTo(19.2f, 8.3f)
                close()
                moveTo(7.2f, 10.4f)
                lineTo(16.8f, 10.4f)
                lineTo(16.8f, 11.6f)
                lineTo(7.2f, 11.6f)
                close()
                moveTo(7.2f, 13.6f)
                lineTo(16.8f, 13.6f)
                lineTo(16.8f, 14.8f)
                lineTo(7.2f, 14.8f)
                close()
            }
        }.build()
    }

    /** 樱花瓣：带缺口的单瓣剪影。空状态与弹层的点缀。 */
    val Sakura: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sakura",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 22.5f)
                curveTo(6.8f, 20.5f, 4.2f, 16.4f, 4.2f, 11.8f)
                curveTo(4.2f, 7.2f, 7.6f, 3.6f, 10.6f, 2.4f)
                lineTo(12f, 5f)
                lineTo(13.4f, 2.4f)
                curveTo(16.4f, 3.6f, 19.8f, 7.2f, 19.8f, 11.8f)
                curveTo(19.8f, 16.4f, 17.2f, 20.5f, 12f, 22.5f)
                close()
            }
        }.build()
    }





    /** 涡环云：云头一道由外向内卷的厚螺线（白描），云尾从螺线外缘顺出、平滑收细。 */
    val Kumo: ImageVector by lazy {
        ImageVector.Builder(
            name = "Kumo",
            defaultWidth = 72.dp,
            defaultHeight = 30.dp,
            viewportWidth = 72f,
            viewportHeight = 30f,
        ).apply {
            // 螺线云头：半径每四分之一圈收缩，一圈半卷入圆心；粗描边=涡带
            path(
                stroke = SolidColor(Color.Black),
                strokeAlpha = 1f,
                strokeLineWidth = 4.5f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(28f, 13f)
                curveTo(28f, 19.07f, 21.14f, 20.5f, 17f, 20.5f)
                curveTo(12.86f, 20.5f, 11f, 16.31f, 11f, 13f)
                curveTo(11f, 9.69f, 14.35f, 8.2f, 17f, 8.2f)
                curveTo(19.65f, 8.2f, 20.8f, 10.9f, 20.8f, 13f)
                curveTo(20.8f, 15.1f, 18.82f, 16.3f, 17f, 16.3f)
            }
            // 云尾：从螺线右缘顺出，向右收细成尖
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.55f,
            ) {
                moveTo(26f, 10.5f)
                curveTo(34f, 9.5f, 46f, 11f, 56f, 13.5f)
                curveTo(60f, 14.5f, 64f, 15.8f, 66f, 17f)
                curveTo(64f, 18f, 60f, 18.6f, 54f, 18.8f)
                curveTo(44f, 19.2f, 34f, 17.8f, 26f, 15.5f)
                close()
            }
        }.build()
    }
}
