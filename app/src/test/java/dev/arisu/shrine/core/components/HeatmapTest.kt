package dev.arisu.shrine.core.components

import dev.arisu.shrine.data.model.TileDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 参拜热度周列对齐回归：服务端从「今天往前 370 天」连续发瓷砖，起点落在哪个星期几不固定
 * （371 = 53 周 + 6 天，永远是「今天 - 6 天」那个星期几）。不按星期补齐的话整张表会整体
 * 错位——用户实测：星期天那格落进了星期六一行。
 */
class HeatmapTest {

    private fun tiles(from: String, count: Int): List<TileDay> = (0 until count).map {
        TileDay(LocalDate.parse(from).plusDays(it.toLong()).toString(), 0)
    }

    /** 起点是星期二：首列前补一个空位，让第 0 行回到周一 */
    @Test
    fun padsHeadToMonday() {
        // 2026-10-06 是星期二（2026-10-12 是周一）
        val cols = tiles("2026-10-06", 8).weekColumns()
        assertEquals(2, cols.size)
        assertNull(cols[0][0])                       // 周一留空
        assertEquals("2026-10-06", cols[0][1]?.date) // 星期二落在第 1 行
        assertEquals("2026-10-11", cols[0][6]?.date) // 首列收在星期天
        assertEquals("2026-10-12", cols[1][0]?.date) // 下一列第 0 行是周一
    }

    /** 起点正好是星期一：不补位 */
    @Test
    fun noPaddingWhenStartsMonday() {
        val cols = tiles("2026-10-12", 7).weekColumns()
        assertEquals(1, cols.size)
        assertEquals("2026-10-12", cols[0][0]?.date)
        assertEquals(7, cols[0].size)
    }

    /** 371 天全量：今天是周一（最后一列仅一格），昨天（周日）落在上一列的最后一个格子 */
    @Test
    fun sundayLandsOnLastRow() {
        val cols = tiles("2025-10-07", 371).weekColumns()
        assertEquals("2026-10-12", cols.last()[0]?.date)
        assertEquals("2026-10-11", cols[cols.size - 2][6]?.date)
    }

    /** 空列表不炸 */
    @Test
    fun emptyIsEmpty() {
        assertEquals(0, emptyList<TileDay>().weekColumns().size)
    }

    /** 日期解析不了就按原样切，不抛异常 */
    @Test
    fun unparsableDateFallsBack() {
        val cols = listOf(TileDay("x", 1), TileDay("y", 2)).weekColumns()
        assertEquals(1, cols.size)
        assertEquals("x", cols[0][0]?.date)
    }
}
