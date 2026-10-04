package app.backlit.render.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberStyleTest {

    @Test
    fun stillIsJustTheNumber() {
        val g = NumberStyle.still(25, 62)
        assertEquals(255, g[9, 10])                     // '6' (5×7, x0 = 7, y0 = 10, row "00110")
        assertEquals(0, g[13, 5])                       // no bolt (the bolt lives at x 11..13, y 3..7)
        assertEquals(0, ChargeKit.rimRing(25).count { (x, y) -> g[x, y] > 0 })
    }

    @Test
    fun thirteenHasATwoColumnGapBetweenDigits() {
        val g = NumberStyle.still(13, 62)
        val litCols = (0 until 13).filter { x -> (4..8).any { y -> g[x, y] > 0 } }
        assertEquals(listOf(3, 4, 5, 8, 9, 10), litCols)
        val hundred = NumberStyle.still(13, 100)       // 3 digits fall back to a 1 px gap so they fit
        assertEquals(listOf(1, 2, 3, 5, 6, 7, 9, 10, 11), (0 until 13).filter { x -> (4..8).any { y -> hundred[x, y] > 0 } })
    }

    @Test
    fun chargingHasBoltAndOneTrailingRimDot() {
        val g = NumberStyle.charging(25, 62, 0)
        assertTrue(g[11, 5] > 0 || g[12, 5] > 0)       // bolt middle row "111" at y = 5
        val ring = ChargeKit.rimRing(25)
        assertEquals(255, g[ring[0].first, ring[0].second])          // head at index 0 when t = 0
        assertEquals(8, ring.count { (x, y) -> g[x, y] > 0 })        // head + 7 tail pixels
    }

    @Test
    fun plugInCountsUp() {
        val start = NumberStyle.plugIn(25, 62, 0)       // shows "0": w = 5 → x0 = 10, row 0 "01110" → x 11..13
        assertEquals(255, start[11, 10]); assertEquals(0, start[10, 10])
        val end = NumberStyle.plugIn(25, 62, 2000)      // count finished → "62"
        assertEquals(NumberStyle.still(25, 62)[9, 10], end[9, 10])
    }
}
