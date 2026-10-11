package dev.arisu.shrine.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeFmtTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun pinNow(at: LocalDateTime) {
        TimeFmt.now = { at.atZone(zone) }
    }

    @After
    fun restoreClock() {
        TimeFmt.now = { ZonedDateTime.now(zone) }
    }

    private fun at(now: LocalDateTime, h: Int, m: Int): Long =
        now.toLocalDate().atTime(h, m).atZone(zone).toEpochSecond()

    private fun yesterdayAt(now: LocalDateTime, h: Int, m: Int): Long =
        now.toLocalDate().minusDays(1).atTime(h, m).atZone(zone).toEpochSecond()

    private fun secondsAgo(now: LocalDateTime, sec: Long): Long =
        now.atZone(zone).toEpochSecond() - sec

    @Test
    fun nowIsJustNow() {
        val now = LocalDateTime.of(2026, 10, 1, 14, 0)
        pinNow(now)
        assertEquals("刚刚", TimeFmt.epoch(secondsAgo(now, 0)))
        assertEquals("刚刚", TimeFmt.epoch(secondsAgo(now, 60)))
    }

    @Test
    fun minutesAgo() {
        val now = LocalDateTime.of(2026, 10, 1, 14, 0)
        pinNow(now)
        assertEquals("3 分钟前", TimeFmt.epoch(secondsAgo(now, 3 * 60)))
        assertEquals("59 分钟前", TimeFmt.epoch(secondsAgo(now, 59 * 60)))
    }

    @Test
    fun hoursAgoToday() {
        val now = LocalDateTime.of(2026, 10, 1, 14, 0)
        pinNow(now)
        assertEquals("1 小时前", TimeFmt.epoch(secondsAgo(now, 61 * 60)))
        assertEquals("5 小时前", TimeFmt.epoch(secondsAgo(now, 5 * 3600)))
    }

    @Test
    fun hoursAgoCrossingMidnightIsYesterday() {

        val now = LocalDateTime.of(2026, 10, 1, 0, 30)
        pinNow(now)
        assertEquals("昨天 23:10", TimeFmt.epoch(yesterdayAt(now, 23, 10)))
    }

    @Test
    fun lateEveningSameDayUsesHourBucket() {

        val now = LocalDateTime.of(2026, 10, 1, 23, 0)
        pinNow(now)
        assertEquals("5 小时前", TimeFmt.epoch(at(now, 18, 0)))
    }

    @Test
    fun todayBeyondSixHoursShowsClockTime() {
        val now = LocalDateTime.of(2026, 10, 1, 23, 30)
        pinNow(now)
        assertEquals("今天 01:00", TimeFmt.epoch(at(now, 1, 0)))
    }

    @Test
    fun yesterdayShowsClockTime() {
        val now = LocalDateTime.of(2026, 10, 1, 14, 0)
        pinNow(now)
        assertEquals("昨天 09:30", TimeFmt.epoch(yesterdayAt(now, 9, 30)))
    }

    @Test
    fun twoDaysAgo() {
        val now = LocalDateTime.of(2026, 10, 1, 14, 0)
        pinNow(now)
        assertTrue(TimeFmt.epoch(yesterdayAt(now, 9, 0) - 86400).endsWith("天前"))
    }

    @Test
    fun invalidIsBlank() {
        assertEquals("", TimeFmt.epoch(0))
    }
}
