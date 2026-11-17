package dev.arisu.shrine.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.R

/**
 * 字体方案
 * ─────────────────────────────────────────────────────────────
 * · 中西文全部：思源宋体（Source Han Serif / Noto Serif SC）。
 *   它自带完整且高质量的拉丁字形，英文与数字同样由它渲染。
 * · Tinos（Times New Roman 度量兼容替身）仅保留斜体一款，
 *   供真正的斜体请求使用（正体永不命中它）。
 *
 * 为什么宋体必须排在家族第一位——这是三次真机复现后的定论：
 * Android 应用内字体族**不做逐字回退**（模拟器 API 35 实证：
 * Tinos 在前时，汉字全部落到系统黑体，打包在 APK 里的宋体从未被使用）。
 * 字体族里声明多个字体只是给字重/样式匹配提供候选，
 * 缺字时一律回落系统字体链，而系统的中文兜底是黑体。
 * 所以把思源宋体放第一位、让它直接命中所有中西文字形，
 * 不依赖任何回退机制，任何机型、任何 API 级别行为一致。
 *
 * 四个字重是 fontTools 从官方可变字体（wght 200–900）实例化的
 * 静态字体（Regular/Medium/SemiBold/Bold，轮廓各不相同）。
 * 此前误把默认实例 ExtraLight 改了个字重元数据当四个字重用——
 * 四份全是极细体，视觉上「不像宋体」，此为上一版仍在犯的错。
 */
val SerifFamily = FontFamily(
    // 中文 + 西文正体：思源宋体，静态实例化四个字重
    Font(R.font.noto_serif_400, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.noto_serif_500, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.noto_serif_600, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.noto_serif_700, FontWeight.Bold, FontStyle.Normal),
    // 斜体：Tinos Italic（仅当某处真的请求斜体时命中）
    Font(R.font.tinos_italic, FontWeight.Normal, FontStyle.Italic),
)

/** 数字、编号这类需要等宽对齐的地方。 */
val MonoFamily = FontFamily.Monospace

val ArisuTypography = Typography(
    // displayLarge/Medium、headlineLarge 此前未定义，会回落到 M3 默认的
    // 无衬线字体——幸运值大数字、个别大标题因此变成系统黑体。补齐挂宋体。
    displayLarge = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Bold,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Bold,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 44.sp, letterSpacing = 0.5.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 36.sp, letterSpacing = 0.4.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 32.sp, letterSpacing = 0.3.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.3.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp, lineHeight = 21.sp, letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 27.sp, letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 19.sp, letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SerifFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.1.sp,
    ),
)

/** 编号 arisu_000001 / User_000001 / #1，用等宽字体保持对齐。 */
val CodeLabelStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.1.sp,
)
