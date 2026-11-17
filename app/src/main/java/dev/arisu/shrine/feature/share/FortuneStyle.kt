package dev.arisu.shrine.feature.share

/** 卡面风格；颜色只在绘卡时取，不跟应用主题走。 */
enum class FortuneCardStyle(val label: String) {
    LACQUER("朱漆"),
    PAPER("素纸"),
    INK("墨夜"),
}

internal data class FortunePalette(
    val paper: Int,
    val ink: Int,
    val inkSoft: Int,
    val accent: Int,
    val line: Int,
    val pattern: Int,
    val patternFaint: Int,
    val petal: Int,
    val qrInk: Int,
)

internal fun paletteFor(style: FortuneCardStyle): FortunePalette = when (style) {
    FortuneCardStyle.LACQUER -> FortunePalette(
        paper = 0xFF9E1F18.toInt(),
        ink = 0xFFF7EFE2.toInt(),
        inkSoft = 0xCCF7EFE2.toInt(),
        accent = 0xFFD8B36A.toInt(),
        line = 0x3AF7EFE2,
        pattern = 0x3AF7EFE2,
        patternFaint = 0x24F7EFE2,
        petal = 0x40F7EFE2,
        qrInk = 0xFFF7EFE2.toInt(),
    )
    FortuneCardStyle.PAPER -> FortunePalette(
        paper = 0xFFF3EBDD.toInt(),
        ink = 0xFF2A241E.toInt(),
        inkSoft = 0xCC2A241E.toInt(),
        accent = 0xFFB02A21.toInt(),
        line = 0x3A2A241E,
        pattern = 0x2E2A241E,
        patternFaint = 0x1E2A241E,
        petal = 0x30B02A21,
        qrInk = 0xFF2A241E.toInt(),
    )
    FortuneCardStyle.INK -> FortunePalette(
        paper = 0xFF1C1A18.toInt(),
        ink = 0xFFF2E9DA.toInt(),
        inkSoft = 0xCCF2E9DA.toInt(),
        accent = 0xFFD8B36A.toInt(),
        line = 0x3AF2E9DA,
        pattern = 0x30F2E9DA,
        patternFaint = 0x1EF2E9DA,
        petal = 0x40D8B36A,
        qrInk = 0xFFD8B36A.toInt(),
    )
}
