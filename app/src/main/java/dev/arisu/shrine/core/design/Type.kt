package dev.arisu.shrine.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.arisu.shrine.R

val SerifFamily = FontFamily(

    Font(R.font.noto_serif_400, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.noto_serif_500, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.noto_serif_600, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.noto_serif_700, FontWeight.Bold, FontStyle.Normal),

    Font(R.font.tinos_italic, FontWeight.Normal, FontStyle.Italic),
)

val MonoFamily = FontFamily.Monospace

val ArisuTypography = Typography(

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

val CodeLabelStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.1.sp,
)
