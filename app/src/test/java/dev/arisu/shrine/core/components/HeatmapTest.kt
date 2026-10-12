package dev.arisu.shrine.core.components

import dev.arisu.shrine.data.model.TileDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HeatmapTest {

    private fun tiles(from: String, count: Int): List<TileDay> = (0 until count).map {
        TileDay(LocalDate.parse(from).plusDays(it.toLong()).toString(), 0)
    }

    @Test
    fun padsHeadToMonday() {

        val cols = tiles("2026-10-06", 8).weekColumns()
        assertEquals(2, cols.size)
        assertNull(cols[0][0])
        assertEquals("2026-10-06", cols[0][1]?.date)
        assertEquals("2026-10-11", cols[0][6]?.date)
        assertEquals("2026-10-12", cols[1][0]?.date)
    }

    @Test
    fun noPaddingWhenStartsMonday() {
        val cols = tiles("2026-10-12", 7).weekColumns()
        assertEquals(1, cols.size)
        assertEquals("2026-10-12", cols[0][0]?.date)
        assertEquals(7, cols[0].size)
    }

    @Test
    fun sundayLandsOnLastRow() {
        val cols = tiles("2025-10-07", 371).weekColumns()
        assertEquals("2026-10-12", cols.last()[0]?.date)
        assertEquals("2026-10-11", cols[cols.size - 2][6]?.date)
    }

    @Test
    fun emptyIsEmpty() {
        assertEquals(0, emptyList<TileDay>().weekColumns().size)
    }

    @Test
    fun unparsableDateFallsBack() {
        val cols = listOf(TileDay("x", 1), TileDay("y", 2)).weekColumns()
        assertEquals(1, cols.size)
        assertEquals("x", cols[0][0]?.date)
    }
}
