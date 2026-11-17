package dev.arisu.shrine.core.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** 加载态最短显示时长（T20/T27）：数据秒回时动画不被硬切。 */
class LoadingTimingTest {

    @Test
    fun zeroWhenShownLongEnough() {
        // 已显示超过最短时长：立即可退
        assertEquals(0L, loadingRemainder(elapsedMillis = 500L))
        assertEquals(0L, loadingRemainder(elapsedMillis = 100_000L))
    }

    @Test
    fun padsShortDisplay() {
        // 出现 300ms 就收到停止信号：还需再停 120ms 才淡出（最短 420ms）
        assertEquals(120L, loadingRemainder(elapsedMillis = 300L))
    }

    @Test
    fun neverWaitsBackwards() {
        // 时钟回拨等异常场景：按「刚显示」处理，补足整段最短时长且绝不产生负延时
        val r = loadingRemainder(elapsedMillis = -5L)
        assert(r in 0..MIN_LOADING_MILLIS)
        assertEquals(MIN_LOADING_MILLIS, r)
    }

    @Test
    fun customMinimumRespected() {
        assertEquals(400L, loadingRemainder(elapsedMillis = 100L, minMillis = 500L))
    }
}

/** 加载衔接时序（T27）：内容画面必须等「少女祈祷中」退场播完才允许出现。 */
class GateTimingTest {

    @Test
    fun revealWaitsForMinimumPlusExit() {
        // 刚出现就完成加载：等最短显示 + 整段退场
        assertEquals(MIN_LOADING_MILLIS + EXIT_LOADING_MILLIS, contentRevealDelay(0L))
    }

    @Test
    fun revealOnlyWaitsForExitWhenPlayedLongEnough() {
        // 已显示 500ms（超过最短 420ms）：只等退场 180ms
        assertEquals(EXIT_LOADING_MILLIS.toLong(), contentRevealDelay(500L))
    }

    @Test
    fun revealNeverNegative() {
        // 时钟异常：按刚显示处理，绝不提前放行
        assertEquals(MIN_LOADING_MILLIS + EXIT_LOADING_MILLIS, contentRevealDelay(-5L))
    }

    @Test
    fun revealIsShorterThanBefore() {
        // 用户反馈衔接拖沓：整体节奏（最短显示 + 退场）必须比 v0.0.0.5 的 560+340 快
        assert(MIN_LOADING_MILLIS + EXIT_LOADING_MILLIS < 560L + 340L)
    }
}
