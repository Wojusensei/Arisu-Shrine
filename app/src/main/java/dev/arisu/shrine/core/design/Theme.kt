package dev.arisu.shrine.core.design

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun lightColors() = lightColorScheme(
    primary = Vermilion,
    onPrimary = Color.White,
    primaryContainer = VermilionSoft,
    onPrimaryContainer = VermilionDeep,
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Color(0xFF4A370F),
    tertiary = Indigo,
    onTertiary = Color.White,
    tertiaryContainer = IndigoSoft,
    onTertiaryContainer = Color(0xFF1E3145),
    background = Paper,
    onBackground = InkTitle,
    surface = Surface,
    onSurface = InkTitle,
    surfaceVariant = PaperSunken,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = SurfaceWarm,
    surfaceContainer = Color(0xFFFBF7F3),
    surfaceContainerHigh = Color(0xFFF6F1EB),
    surfaceContainerHighest = Color(0xFFF1EBE4),
    outline = Hairline,
    outlineVariant = HairlineSoft,
    error = Vermilion,
    onError = Color.White,
    errorContainer = VermilionSoft,
    onErrorContainer = VermilionDeep,
    inverseSurface = InkTitle,
    inverseOnSurface = Paper,
    scrim = Color(0x66000000),
)

private fun darkColors() = darkColorScheme(
    primary = Color(0xFFC73A37),
    onPrimary = Color(0xFFFFF5F2),
    primaryContainer = VermilionSoft,
    onPrimaryContainer = Color(0xFFFFB0A8),
    secondary = Gold,
    onSecondary = Color(0xFF241B08),
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Color(0xFFEAD196),
    tertiary = Indigo,
    onTertiary = Color(0xFF0F1B2C),
    tertiaryContainer = IndigoSoft,
    onTertiaryContainer = Color(0xFFC9DDF8),
    background = Paper,
    onBackground = InkTitle,
    surface = Surface,
    onSurface = InkTitle,
    surfaceVariant = PaperSunken,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color(0xFF070A0F),
    surfaceContainerLow = Color(0xFF0E131B),
    surfaceContainer = Color(0xFF12171F),
    surfaceContainerHigh = Color(0xFF161C25),
    surfaceContainerHighest = Color(0xFF1B222C),
    outline = Hairline,
    outlineVariant = HairlineSoft,
    error = Color(0xFFFF7B72),
    onError = Color(0xFF3A0B08),
    errorContainer = Color(0xFF3C1210),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = InkTitle,
    inverseOnSurface = Paper,
    scrim = Color(0x66000000),
)

@Composable
fun ArisuShrineTheme(
    content: @Composable () -> Unit,
) {
    val darkTheme = ArisuTheme.isDark
    val colorScheme = if (darkTheme) darkColors() else lightColors()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ArisuTypography,
        shapes = ArisuShapes,
        content = content,
    )
}
