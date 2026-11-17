package dev.arisu.shrine.core.util

import java.time.LocalDate

/** 从今天往前数连续求签天数；今天没签则为 0。 */
internal fun streakDays(days: Iterable<LocalDate>, today: LocalDate): Int {
    val set = days.toSet()
    var day = today
    var count = 0
    while (set.contains(day)) {
        count++
        day = day.minusDays(1)
    }
    return count
}
