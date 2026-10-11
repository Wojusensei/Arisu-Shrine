package dev.arisu.shrine.core.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadingTimingTest {

    @Test
    fun zeroWhenShownLongEnough() {

        assertEquals(0L, loadingRemainder(elapsedMillis = 500L))
        assertEquals(0L, loadingRemainder(elapsedMillis = 100_000L))
    }

    @Test
    fun padsShortDisplay() {

        assertEquals(120L, loadingRemainder(elapsedMillis = 300L))
    }

    @Test
    fun neverWaitsBackwards() {

        val r = loadingRemainder(elapsedMillis = -5L)
        assert(r in 0..MIN_LOADING_MILLIS)
        assertEquals(MIN_LOADING_MILLIS, r)
    }

    @Test
    fun customMinimumRespected() {
        assertEquals(400L, loadingRemainder(elapsedMillis = 100L, minMillis = 500L))
    }
}

class GateTimingTest {

    @Test
    fun revealWaitsForMinimumPlusExit() {

        assertEquals(MIN_LOADING_MILLIS + EXIT_LOADING_MILLIS, contentRevealDelay(0L))
    }

    @Test
    fun revealOnlyWaitsForExitWhenPlayedLongEnough() {

        assertEquals(EXIT_LOADING_MILLIS.toLong(), contentRevealDelay(500L))
    }

    @Test
    fun revealNeverNegative() {

        assertEquals(MIN_LOADING_MILLIS + EXIT_LOADING_MILLIS, contentRevealDelay(-5L))
    }

    @Test
    fun revealIsShorterThanBefore() {

        assert(MIN_LOADING_MILLIS + EXIT_LOADING_MILLIS < 560L + 340L)
    }
}
