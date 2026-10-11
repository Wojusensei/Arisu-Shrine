package dev.arisu.shrine.core.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object ArisuTheme {
    var isDark by mutableStateOf(false)
        private set

    fun apply(override: Boolean?) {
        isDark = override ?: false
    }

    fun setNightPref(pref: Boolean?) {
        isDark = pref ?: false
    }
}

val Vermilion: Color get() = if (ArisuTheme.isDark) Color(0xFFFF7B72) else Color(0xFFB02A21)

val VermilionDeep: Color get() = if (ArisuTheme.isDark) Color(0xFFB23A31) else Color(0xFF7C1712)

val VermilionBright: Color get() = if (ArisuTheme.isDark) Color(0xFFFF8D80) else Color(0xFFCB4A38)

val VermilionSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF3C272B) else Color(0xFFF7E4E0)

val VermilionTint: Color get() = if (ArisuTheme.isDark) Color(0xFF1A1417) else Color(0xFFFDF4F2)

val VermilionButton: Color get() = if (ArisuTheme.isDark) Color(0xFFC73A37) else Color(0xFFB02A21)

val InkTitle: Color get() = if (ArisuTheme.isDark) Color(0xFFE6EDF3) else Color(0xFF1A1512)

val InkBody: Color get() = if (ArisuTheme.isDark) Color(0xFFCED5DE) else Color(0xFF2E2723)

val InkMuted: Color get() = if (ArisuTheme.isDark) Color(0xFF8B949E) else Color(0xFF6E655D)

val InkFaint: Color get() = if (ArisuTheme.isDark) Color(0xFF6E7681) else Color(0xFFA09790)

val Paper: Color get() = if (ArisuTheme.isDark) Color(0xFF0A0E14) else Color(0xFFF7F3EE)

val PaperSunken: Color get() = if (ArisuTheme.isDark) Color(0xFF080B10) else Color(0xFFEFE8E1)

val Surface: Color get() = if (ArisuTheme.isDark) Color(0xFF131820) else Color(0xFFFFFFFF)

val SurfaceWarm: Color get() = if (ArisuTheme.isDark) Color(0xFF151A22) else Color(0xFFFDFBF8)

val Hairline: Color get() = if (ArisuTheme.isDark) Color(0xFF2B323B) else Color(0xFFEAE2D9)

val HairlineSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF222933) else Color(0xFFF3ECE5)

val Gold: Color get() = if (ArisuTheme.isDark) Color(0xFFD5AA4D) else Color(0xFF9E7729)
val GoldSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF342C1C) else Color(0xFFF6EDD8)

val Heat0: Color get() = if (ArisuTheme.isDark) Color(0xFF242A34) else Color(0xFFE3DCD2)
val Heat1: Color get() = if (ArisuTheme.isDark) Color(0xFF4A3A3C) else Color(0xFFF2CEC7)
val Heat2: Color get() = if (ArisuTheme.isDark) Color(0xFF7A4A4A) else Color(0xFFDE9A8D)
val Heat3: Color get() = if (ArisuTheme.isDark) Color(0xFFB25A55) else Color(0xFFC25F4E)
val Heat4: Color get() = if (ArisuTheme.isDark) Color(0xFFFF7B72) else Color(0xFF9A231A)

fun heatColor(level: Int): Color = when (level.coerceIn(0, 4)) {
    0 -> Heat0
    1 -> Heat1
    2 -> Heat2
    3 -> Heat3
    else -> Heat4
}

val Plum: Color get() = if (ArisuTheme.isDark) Color(0xFFC398DD) else Color(0xFF6E4C6B)
val PlumSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF31293F) else Color(0xFFF1E7F0)
val Indigo: Color get() = if (ArisuTheme.isDark) Color(0xFF8AB4F8) else Color(0xFF3C5A78)
val IndigoSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF1F2D45) else Color(0xFFE5EBF2)
val Celadon: Color get() = if (ArisuTheme.isDark) Color(0xFF9CCFAE) else Color(0xFF46685E)
val CeladonSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF24352B) else Color(0xFFE4EEEA)
val Slate: Color get() = if (ArisuTheme.isDark) Color(0xFF99A1AC) else Color(0xFF8C847C)
val SlateSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF2C323B) else Color(0xFFF0EBE6)

val StateOpen: Color get() = Indigo
val StateOpenBg: Color get() = IndigoSoft
val StateProgress: Color get() = Gold
val StateProgressBg: Color get() = GoldSoft
val StateResolved: Color get() = Celadon
val StateResolvedBg: Color get() = CeladonSoft
val StateClosed: Color get() = Slate
val StateClosedBg: Color get() = SlateSoft

val TagEnhance: Color get() = Plum
val TagEnhanceBg: Color get() = PlumSoft
val TagBug: Color get() = Vermilion
val TagBugBg: Color get() = VermilionSoft
val TagQuestions: Color get() = Indigo
val TagQuestionsBg: Color get() = IndigoSoft
val TagTips: Color get() = Celadon
val TagTipsBg: Color get() = CeladonSoft

val OnAccent: Color get() = if (ArisuTheme.isDark) Color(0xFF131820) else Color.White

val PlaceholderTeal: Color get() = if (ArisuTheme.isDark) Color(0xFF4E8F8A) else Color(0xFF2F6E6B)

val FogAlphaScale: Float get() = if (ArisuTheme.isDark) 2.4f else 1f

val ToriiVermilion: Color get() = Vermilion
val ToriiVermilionDeep: Color get() = VermilionDeep
val ToriiVermilionSoft: Color get() = VermilionSoft
val ToriiVermilionTint: Color get() = VermilionTint

val SumiInk: Color get() = InkTitle
val SumiInkSoft: Color get() = InkMuted
val SumiInkFaint: Color get() = InkFaint

val WashiPaper: Color get() = Paper
val WashiCard: Color get() = Surface
val WashiSunken: Color get() = PaperSunken
val WashiLine: Color get() = Hairline

val KeyakiWood: Color get() = InkMuted
val KeyakiWoodSoft: Color get() = PaperSunken
val GoldLeaf: Color get() = Gold
val GoldLeafSoft: Color get() = GoldSoft

val MossGreen: Color get() = Celadon
val MossGreenSoft: Color get() = CeladonSoft
val IndigoBlue: Color get() = Indigo
val IndigoBlueSoft: Color get() = IndigoSoft
val PlumPurple: Color get() = Plum
val PlumPurpleSoft: Color get() = PlumSoft
val AmberOchre: Color get() = Gold
val AmberOchreSoft: Color get() = GoldSoft
val SakuraPink: Color get() = VermilionBright

val NeuBase: Color get() = if (ArisuTheme.isDark) Color(0xFF0A0E14) else Color(0xFFEDE8E1)

val NeuBaseHigh: Color get() = if (ArisuTheme.isDark) Color(0xFF10151D) else Color(0xFFF1ECE6)

val NeuInset: Color get() = if (ArisuTheme.isDark) Color(0xFF080B10) else Color(0xFFE4DED5)

val NeuInsetDeep: Color get() = if (ArisuTheme.isDark) Color(0xFF05070C) else Color(0xFFDBD4CA)

val NeuLight: Color get() = if (ArisuTheme.isDark) Color(0xFF3A4250) else Color(0xFFFFFFFF)

val NeuShadow: Color get() = if (ArisuTheme.isDark) Color(0xFF000000) else Color(0xFFC6BCAD)

val NeuAvatarGrey: Color get() = if (ArisuTheme.isDark) Color(0xFF7D8590) else Color(0xFF9C948A)
val NeuAvatarBg: Color get() = if (ArisuTheme.isDark) Color(0xFF232B36) else Color(0xFFE0DAD1)
