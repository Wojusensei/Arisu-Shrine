package dev.arisu.shrine.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 服务器时间（epoch 秒）→ 界面展示文案。
 * 今天显示「今天 HH:mm」，昨天「昨天 HH:mm」，一周内「N 天前」，更早「yyyy年M月d日」。
 */
object TimeFmt {

    /** 时钟可注入（单测用）；默认真实系统时间 */
    internal var now: () -> java.time.ZonedDateTime = { java.time.ZonedDateTime.now(ZoneId.systemDefault()) }

    fun epoch(value: Long): String {
        if (value <= 0) return ""
        val now = now()
        val zone = now.zone
        val time = Instant.ofEpochSecond(value).atZone(zone)
        val today = now.toLocalDate()
        val date = time.toLocalDate()
        val hm = time.format(DateTimeFormatter.ofPattern("HH:mm"))
        // 同类型相减：ZonedDateTime 与 LocalDateTime 混用会让 Duration.between
        // 抛「Unable to obtain ZonedDateTime」——之前这一行炸掉了全 App 的时间渲染
        val minutes = java.time.Duration.between(time, now).toMinutes()
        return when {
            // 设备时钟偏慢时时间戳可能落在未来：负分钟数会一路漏到「N 天前」算出「-1 天前」
            date.isAfter(today) -> "刚刚"
            minutes in 0..1 -> "刚刚"
            minutes in 2..59 -> "$minutes 分钟前"
            // 「N 小时前」只允许落在今天：昨天深夜的内容在这里被吞掉，
            // 会显示成「1 小时前」而与相邻条目的「昨天 HH:mm」自相矛盾
            date == today && minutes in 60..119 -> "1 小时前"
            date == today && minutes in 120..(6 * 60) -> "${minutes / 60} 小时前"
            date == today -> "今天 $hm"
            date == today.minusDays(1) -> "昨天 $hm"
            date.isAfter(today.minusDays(7)) -> "${today.toEpochDay() - date.toEpochDay()} 天前"
            date.year == today.year -> time.format(DateTimeFormatter.ofPattern("M月d日"))
            else -> time.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        }
    }

    /** 「x天xx小时xx分钟」用于总在线时长 */
    fun duration(totalMinutes: Long): String {
        val days = totalMinutes / 1440
        val hours = (totalMinutes % 1440) / 60
        val minutes = totalMinutes % 60
        return "${days}天${hours}小时${minutes}分钟"
    }

    /** 在线小时数（个人资料页展示整数小时） */
    fun hours(totalMinutes: Long): Int = (totalMinutes / 60).toInt()
}
