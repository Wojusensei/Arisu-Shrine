package dev.arisu.shrine.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFmt {

    internal var now: () -> java.time.ZonedDateTime = { java.time.ZonedDateTime.now(ZoneId.systemDefault()) }

    fun epoch(value: Long): String {
        if (value <= 0) return ""
        val now = now()
        val zone = now.zone
        val time = Instant.ofEpochSecond(value).atZone(zone)
        val today = now.toLocalDate()
        val date = time.toLocalDate()
        val hm = time.format(DateTimeFormatter.ofPattern("HH:mm"))

        val minutes = java.time.Duration.between(time, now).toMinutes()
        return when {

            date.isAfter(today) -> "刚刚"
            minutes in 0..1 -> "刚刚"
            minutes in 2..59 -> "$minutes 分钟前"

            date == today && minutes in 60..119 -> "1 小时前"
            date == today && minutes in 120..(6 * 60) -> "${minutes / 60} 小时前"
            date == today -> "今天 $hm"
            date == today.minusDays(1) -> "昨天 $hm"
            date.isAfter(today.minusDays(7)) -> "${today.toEpochDay() - date.toEpochDay()} 天前"
            date.year == today.year -> time.format(DateTimeFormatter.ofPattern("M月d日"))
            else -> time.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        }
    }

    fun duration(totalMinutes: Long): String {
        val days = totalMinutes / 1440
        val hours = (totalMinutes % 1440) / 60
        val minutes = totalMinutes % 60
        return "${days}天${hours}小时${minutes}分钟"
    }

    fun hours(totalMinutes: Long): Int = (totalMinutes / 60).toInt()
}
