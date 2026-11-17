package dev.arisu.shrine.core.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/* ══════════════════════════════════════════════════════════════════
 * 有栖神社 · 色板
 *
 * 定调：朱与纸。以神社的朱红为主，和纸的暖白为底，墨色书写。
 * 所有颜色都压低了饱和度，避免廉价的高亮感；
 * 层级靠明度与留白表达，而不是靠花哨的色相。
 *
 * ── 深浅双主题机制 ──
 * 每个色名是顶层 getter，内部按 ArisuTheme.isDark 分流：
 * 组合期或绘制期读取都会订阅这个 state，切换主题即整树刷新，
 * 因此全部 UI 代码照常写 `Vermilion`、`NeuBase`，不感知主题。
 * 两条硬约束：
 *   1. 禁止在无 key 的 remember / LaunchedEffect / 静态初始化里取色，
 *      那些地方求值一次就固化，不会跟随主题；
 *   2. `val X = Y` 形式的别名会固化取值，必须写成 `get() = Y`。
 * ══════════════════════════════════════════════════════════════════ */

/**
 * 主题状态。写入点只有 MainActivity（attachBaseContext 读本地偏好后注入）
 * 和设置页的「黑夜模式」开关（即时生效，无需重建 Activity）。
 *
 * 规则：默认亮色；用户在设置里拨过开关后以手动值为准，本机永久保存
 * （DataStore），换号/登录登出都不影响。
 */
object ArisuTheme {
    var isDark by mutableStateOf(false)
        private set

    /** 启动合并：本地覆盖，未设置默认亮色 */
    fun apply(override: Boolean?) {
        isDark = override ?: false
    }

    /** 开关拨动：立即生效；pref 传 null 恢复默认亮色 */
    fun setNightPref(pref: Boolean?) {
        isDark = pref ?: false
    }
}

/* ── 朱 · 主色 ───────────────────────────────────────────────── */

/** 朱 —— 主色，比常见的大红更沉，接近鸟居上剥落的漆；暗色下提亮为珊瑚 */
val Vermilion: Color get() = if (ArisuTheme.isDark) Color(0xFFFF7B72) else Color(0xFFB02A21)

/** 深朱 —— 用于渐变暗端、按下态 */
val VermilionDeep: Color get() = if (ArisuTheme.isDark) Color(0xFFB23A31) else Color(0xFF7C1712)

/** 亮朱 —— 用于渐变亮端、强调 */
val VermilionBright: Color get() = if (ArisuTheme.isDark) Color(0xFFFF8D80) else Color(0xFFCB4A38)

/** 浅朱 —— 用于选中底色、浅色标签；暗色下改为深色容器 */
val VermilionSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF3C272B) else Color(0xFFF7E4E0)

/** 极浅朱 —— 用于整块淡色区域 */
val VermilionTint: Color get() = if (ArisuTheme.isDark) Color(0xFF1A1417) else Color(0xFFFDF4F2)

/**
 * 朱 · 色块底（实底按钮、悬浮球、印章这类「白字压朱底」的场合）。
 * 亮色与 [Vermilion] 同值；暗色下朱要提亮当文字用，但色块底必须
 * 保持压暗的红，白字才够对比——两种角色夜里分裂，用这个 token 分流。
 */
val VermilionButton: Color get() = if (ArisuTheme.isDark) Color(0xFFC73A37) else Color(0xFFB02A21)

/* ── 墨 · 文字 ───────────────────────────────────────────────── */

/** 标题墨 */
val InkTitle: Color get() = if (ArisuTheme.isDark) Color(0xFFE6EDF3) else Color(0xFF1A1512)

/** 正文墨 */
val InkBody: Color get() = if (ArisuTheme.isDark) Color(0xFFCED5DE) else Color(0xFF2E2723)

/** 次要文字 */
val InkMuted: Color get() = if (ArisuTheme.isDark) Color(0xFF8B949E) else Color(0xFF6E655D)

/** 最弱文字、占位符 */
val InkFaint: Color get() = if (ArisuTheme.isDark) Color(0xFF6E7681) else Color(0xFFA09790)

/* ── 纸 · 背景与表面 ─────────────────────────────────────────── */

/** 页面底色（亮色暖白；暗色深蓝黑） */
val Paper: Color get() = if (ArisuTheme.isDark) Color(0xFF0A0E14) else Color(0xFFF9F7F4)

/** 下沉区域 / 空状态圆底 */
val PaperSunken: Color get() = if (ArisuTheme.isDark) Color(0xFF080B10) else Color(0xFFF2EEEA)

/** 卡片表面 */
val Surface: Color get() = if (ArisuTheme.isDark) Color(0xFF131820) else Color(0xFFFFFFFF)

/** 略带暖意的表面 */
val SurfaceWarm: Color get() = if (ArisuTheme.isDark) Color(0xFF151A22) else Color(0xFFFEFCFA)

/** 分割线 */
val Hairline: Color get() = if (ArisuTheme.isDark) Color(0xFF2B323B) else Color(0xFFECE7E2)

/** 更弱的分割线 */
val HairlineSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF222933) else Color(0xFFF5F2EF)

/* ── 金 ──────────────────────────────────────────────────────── */

/** 金箔 —— 神主身份、精华 */
val Gold: Color get() = if (ArisuTheme.isDark) Color(0xFFD5AA4D) else Color(0xFF9E7729)
val GoldSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF342C1C) else Color(0xFFF6EDD8)

/* ── 参拜热度（由浅到深五档） ────────────────────────────────── */

val Heat0: Color get() = if (ArisuTheme.isDark) Color(0xFF242A34) else Color(0xFFE8E3DD)
val Heat1: Color get() = if (ArisuTheme.isDark) Color(0xFF4A3A3C) else Color(0xFFF2CEC7)
val Heat2: Color get() = if (ArisuTheme.isDark) Color(0xFF7A4A4A) else Color(0xFFDE9A8D)
val Heat3: Color get() = if (ArisuTheme.isDark) Color(0xFFB25A55) else Color(0xFFC25F4E)
val Heat4: Color get() = if (ArisuTheme.isDark) Color(0xFFFF7B72) else Color(0xFF9A231A)

/** 取第 level 档热度色，level ∈ 0..4 */
fun heatColor(level: Int): Color = when (level.coerceIn(0, 4)) {
    0 -> Heat0
    1 -> Heat1
    2 -> Heat2
    3 -> Heat3
    else -> Heat4
}

/* ── 身份色（沉、雅；暗色下整体提亮为柔色） ─────────────────── */

val Plum: Color get() = if (ArisuTheme.isDark) Color(0xFFC398DD) else Color(0xFF6E4C6B)
val PlumSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF31293F) else Color(0xFFF1E7F0)
val Indigo: Color get() = if (ArisuTheme.isDark) Color(0xFF8AB4F8) else Color(0xFF3C5A78)
val IndigoSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF1F2D45) else Color(0xFFE5EBF2)
val Celadon: Color get() = if (ArisuTheme.isDark) Color(0xFF9CCFAE) else Color(0xFF46685E)
val CeladonSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF24352B) else Color(0xFFE4EEEA)
val Slate: Color get() = if (ArisuTheme.isDark) Color(0xFF99A1AC) else Color(0xFF8C847C)
val SlateSoft: Color get() = if (ArisuTheme.isDark) Color(0xFF2C323B) else Color(0xFFF1EDE9)

/* ── 绘马状态（get() 代理：直接赋值会把亮色固化） ───────────── */

val StateOpen: Color get() = Indigo
val StateOpenBg: Color get() = IndigoSoft
val StateProgress: Color get() = Gold
val StateProgressBg: Color get() = GoldSoft
val StateResolved: Color get() = Celadon
val StateResolvedBg: Color get() = CeladonSoft
val StateClosed: Color get() = Slate
val StateClosedBg: Color get() = SlateSoft

/* ── 绘马标签 ────────────────────────────────────────────────── */

val TagEnhance: Color get() = Plum
val TagEnhanceBg: Color get() = PlumSoft
val TagBug: Color get() = Vermilion
val TagBugBg: Color get() = VermilionSoft
val TagQuestions: Color get() = Indigo
val TagQuestionsBg: Color get() = IndigoSoft
val TagTips: Color get() = Celadon
val TagTipsBg: Color get() = CeladonSoft

/* ── 主题配套的语义色 ────────────────────────────────────────── */

/**
 * 彩底上的字与点：亮色主题的身份彩是深色系，配白字；
 * 暗色主题的身份彩是亮色系，白字会沉进底色，改配深墨。
 * （标签胶囊选中态、Markdown 图片占位上的字）
 */
val OnAccent: Color get() = if (ArisuTheme.isDark) Color(0xFF131820) else Color.White

/** Markdown 图片占位渐变的第六色（与身份色同一套节奏） */
val PlaceholderTeal: Color get() = if (ArisuTheme.isDark) Color(0xFF4E8F8A) else Color(0xFF2F6E6B)

/**
 * 柔雾的浓度倍率：暗底吸光，同样的 alpha 在夜里几乎不可见，
 * 统一在 [fogBlob] 出口处乘上。
 */
val FogAlphaScale: Float get() = if (ArisuTheme.isDark) 2.4f else 1f

/* ══════════════════════════════════════════════════════════════════
 * 旧命名兼容别名
 *
 * 第一版 UI 用的是这套名字，为了让所有页面在重构期间保持可编译，
 * 这里把它们全部指向新色板。新代码请直接用上面的名字。
 * ══════════════════════════════════════════════════════════════════ */

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

/* ══════════════════════════════════════════════════════════════════
 * 新拟物（Neumorphism / Soft UI）
 *
 * 核心规则：**凸起面和页面底是同一个颜色**，靠左上的一层高光与
 * 右下的一层暗影把形状「顶」出来；凹陷面（输入框、凹槽）则反过来，
 * 用更暗的底色加一圈由暗到亮的描边。整页只有一个基底色，
 * 所以颜色不能多——朱红只在真正需要强调的地方点一下。
 *
 * 暗色下立体感的来源不同：影子在黑底上不可见，
 * 改用「微亮的描边色」（NeuLight 从纯白变为灰蓝）承担受光面，
 * 各处 copy(alpha=…) 的字面量无需感知主题。
 * ══════════════════════════════════════════════════════════════════ */

/** 页面底 / 凸起面（同一个颜色，这是新拟物的前提） */
val NeuBase: Color get() = if (ArisuTheme.isDark) Color(0xFF0A0E14) else Color(0xFFF2EFEB)

/** 稍微亮一点的面，用于需要一点点区分的凸起件 */
val NeuBaseHigh: Color get() = if (ArisuTheme.isDark) Color(0xFF10151D) else Color(0xFFF4F1ED)

/** 凹陷面（输入框、热度格子的空档、头像底） */
val NeuInset: Color get() = if (ArisuTheme.isDark) Color(0xFF080B10) else Color(0xFFE8E4DE)

/** 更深的凹陷，用于进度、禁用态 */
val NeuInsetDeep: Color get() = if (ArisuTheme.isDark) Color(0xFF05070C) else Color(0xFFDFDBD5)

/** 左上高光：亮色为纯白，暗色为微亮的灰蓝描边色 */
val NeuLight: Color get() = if (ArisuTheme.isDark) Color(0xFF3A4250) else Color(0xFFFFFFFF)

/** 右下暗影：暗色下影子用纯黑 */
val NeuShadow: Color get() = if (ArisuTheme.isDark) Color(0xFF000000) else Color(0xFFCDC7BE)

/** 默认头像的灰 */
val NeuAvatarGrey: Color get() = if (ArisuTheme.isDark) Color(0xFF7D8590) else Color(0xFF9C948A)
val NeuAvatarBg: Color get() = if (ArisuTheme.isDark) Color(0xFF232B36) else Color(0xFFE4E0DA)
