package dev.arisu.shrine.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * TimeFmt 回归：粒度修复曾因类型混用抛异常，炸掉全 App 的时间渲染。
 *
 * 时钟改为注入后可以锁定「现在」，跨零点的边界不再随测试运行时刻漂移
 * （此前 hoursAgo 在午夜前后运行会随机失败/漏测）。
 * 所有时间戳都从钉住的「现在」推导，禁止混用真实时钟。
 */
class TimeFmtTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 把「现在」钉在指定时刻，返回恢复原状的闭包 */
    private fun pinNow(at: LocalDateTime) {
        TimeFmt.now = { at.atZone(zone) }
    }

    @After
    fun restoreClock() {
        TimeFmt.now = { ZonedDateTime.now(zone) }
    }

    /** 钉住的那一天的 h:m 的时间戳 */
    private fun at(now: LocalDateTime, h: Int, m: Int): Long =
        now.toLocalDate().atTime(h, m).atZone(zone).toEpochSecond()

    /** 钉住那一天的前一天 h:m 的时间戳 */
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
        // 现在 00:30，看昨晚 23:10（80 分钟前）：必须报「昨天 HH:mm」，
        // 不能吞成「1 小时前」而与相邻条目的「昨天」自相矛盾
        val now = LocalDateTime.of(2026, 10, 1, 0, 30)
        pinNow(now)
        assertEquals("昨天 23:10", TimeFmt.epoch(yesterdayAt(now, 23, 10)))
    }

    @Test
    fun lateEveningSameDayUsesHourBucket() {
        // 晚上 23:00 看 18:00（5 小时前，仍在今天）：小时档照常
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
