package app.backlit.render.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SproutStyleTest {

    @Test
    fun potIsAlwaysThereAndStemHeightFollowsLevel() {
        val empty = SproutStyle.still(25, 0)
        assertTrue(empty[12, 19] > 0)                   // pot rim
        assertEquals(0, empty[12, 18])                  // no stem at 0 %
        val half = SproutStyle.still(25, 50)            // len = round(0.5 × 12) = 6 → stem y 18..13, bud at y 12
        assertTrue(half[12, 13] > 0); assertEquals(0, half[12, 11])
        val full = SproutStyle.still(25, 100)           // len = 12 → stem y 18..7, bud at y 6
        assertTrue(full[12, 7] > 0); assertTrue(full[12, 6] > 0)
    }

    @Test
    fun litCountRisesWithLevel() {
        for (size in listOf(25, 13)) {
            val counts = listOf(0, 30, 60, 100).map { SproutStyle.still(size, it).litCount() }
            assertTrue("$size $counts", counts.zipWithNext().all { (a, b) -> b >= a } && counts.last() > counts.first())
        }
    }

    @Test
    fun stillIsStaticButChargingSways() {
        assertEquals(SproutStyle.still(25, 80), SproutStyle.still(25, 80))
        val frames = (0L until 4000L step 100).map { SproutStyle.charging(25, 100, it) }.toSet()
        assertTrue(frames.size > 1)
    }

    @Test
    fun doneBloomsAFlower() {
        val g = SproutStyle.done(25, 2400)              // full bloom; sway is 0 here (sin(2.67)·0.55 < 0.5) → centre (12, 5)
        assertEquals(255, g[12, 5])
        assertTrue(g.litCount() > SproutStyle.still(25, 100).litCount())
    }
}
