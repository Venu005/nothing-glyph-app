package app.backlit.render.charge

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class MoonStyleTest {

    private fun inDisc(size: Int, x: Int, y: Int): Boolean {
        val c = (size - 1) / 2.0; val r = if (size >= 25) 8.6 else 4.6
        return hypot(x - c, y - c) <= r
    }
    /** Lit moon pixels (stars sit outside the disc and can reach 127, so they're excluded). */
    private fun bright(g: PixelGrid) = (0 until g.size).sumOf { y -> (0 until g.size).count { x -> inDisc(g.size, x, y) && g[x, y] >= 120 } }
    private fun discArea(size: Int) = (0 until size).sumOf { y -> (0 until size).count { x -> inDisc(size, x, y) } }

    @Test
    fun emptyAtZeroFullAtHundred() {
        for (size in listOf(25, 13)) {
            assertEquals(0, bright(MoonStyle.still(size, 0)))
            assertEquals(discArea(size), bright(MoonStyle.still(size, 100)))
        }
    }

    @Test
    fun litAreaMatchesLevel() {
        val area = discArea(25)
        for (level in listOf(25, 50, 62, 80)) {
            val lit = bright(MoonStyle.still(25, level))
            assertTrue("level $level lit $lit", kotlin.math.abs(lit - area * level / 100.0) <= area * 0.10)
        }
    }

    @Test
    fun crescentGrowsFromTheRight() {
        val g = MoonStyle.still(25, 20)
        assertTrue(g[19, 12] >= 120)               // right edge lit
        assertTrue(g[5, 12] in 1..40)              // left edge dark (0.07 → 18)
    }

    @Test
    fun plugInEndsWithRevealAndDoneHasHalo() {
        val g = MoonStyle.plugIn(25, 62, 4500)
        assertEquals(255, g[9, 10])                // '6' of the reveal at x0 = 9, y0 = 10
        val d = MoonStyle.done(25, 1000)
        assertTrue((0 until 25).any { x -> d[x, 2] > 0 })   // halo ring at r ≈ 9.9 near the top
    }
}
