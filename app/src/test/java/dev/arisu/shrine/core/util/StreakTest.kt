package dev.arisu.shrine.core.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {

    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun countsBackFromToday() {
        val days = listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(3, streakDays(days, today))
    }

    @Test
    fun missingTodayIsZero() {
        val days = listOf(today.minusDays(1), today.minusDays(2))
        assertEquals(0, streakDays(days, today))
    }

    @Test
    fun duplicateDayDoesNotDoubleCount() {
        val days = listOf(today, today, today.minusDays(1))
        assertEquals(2, streakDays(days, today))
    }
}
