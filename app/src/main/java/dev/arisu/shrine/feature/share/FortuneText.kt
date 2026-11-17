package dev.arisu.shrine.feature.share

/** 卡面签文与今日宜忌；不引用任何官方签文。 */
internal data class FortuneText(
    val verse: List<String>,
    val yi: String,
    val ji: String,
)

internal fun fortuneText(band: String): FortuneText = when (band) {
    "大吉" -> FortuneText(listOf("云开月满，", "前路自明。"), "赴约", "迟疑")
    "吉" -> FortuneText(listOf("风送轻帆，", "一路向晴。"), "远行", "拖延")
    "中吉" -> FortuneText(listOf("静水行舟，", "渐入佳境。"), "清扫", "熬夜")
    "小吉" -> FortuneText(listOf("檐下听雨，", "自有清风。"), "读书", "多思")
    "末吉" -> FortuneText(listOf("守灯待晓，", "后路可期。"), "蓄力", "冒进")
    "凶" -> FortuneText(listOf("苍山如海，", "残阳如血。"), "静养", "争执")
    else -> FortuneText(emptyList(), "", "")
}
