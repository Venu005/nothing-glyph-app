package app.backlit.sand

import app.backlit.render.px
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SandLayoutTest {
    private fun count(g: BooleanArray, cells: List<Int>) = cells.count { g[it] }

    @Test
    fun fractionSplitsTheLoad() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            for (f in listOf(0.0, 0.2, 0.5, 0.6, 1.0)) {
                val g = SandLayout.layout(s, f, stream = false)
                val top = (s.total * f).px()
                assertEquals("n=$n f=$f top", top, count(g, s.cellsOn(1)))
                assertEquals("n=$n f=$f bottom", s.total - top, count(g, s.cellsOn(-1)))
                assertEquals(s.total, g.count { it })
            }
        }
    }

    @Test
    fun upsideDownMirrorsTopToBottom() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            val a = SandLayout.layout(s, 0.6, false, upSide = 1)
            val b = SandLayout.layout(s, 0.6, false, upSide = -1)
            for (y in 0 until n) for (x in 0 until n) assertEquals(a[y * n + x], b[(n - 1 - y) * n + x])
        }
    }

    @Test
    fun streamAddsTheNeckAndOneFallingDot() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            val plain = SandLayout.layout(s, 0.5, false)
            val st = SandLayout.layout(s, 0.5, true)
            assertEquals(plain.count { it } + 2, st.count { it })
            for (i in SandLayout.streamCells(s, 1)) assertTrue(st[i])
            assertTrue(st[s.gate])
            // no stream when a bulb is empty
            assertEquals(s.total, SandLayout.layout(s, 0.0, true).count { it })
            assertEquals(s.total, SandLayout.layout(s, 1.0, true).count { it })
        }
    }

    @Test
    fun balancedLeftRightWithinOneGrain() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            for (f in listOf(0.0, 0.3, 0.6, 1.0)) {
                val g = SandLayout.layout(s, f, false)
                for (side in listOf(1, -1)) {
                    val cells = s.cellsOn(side)
                    val left = cells.count { g[it] && it % n < s.center }
                    val right = cells.count { g[it] && it % n > s.center }
                    assertTrue("n=$n f=$f side=$side $left vs $right", abs(left - right) <= 1)
                }
            }
        }
    }

    @Test
    fun refillScalesTheWholeLoad() {
        val s = HourglassShape.forSize(25)
        assertEquals(0, SandLayout.layout(s, 0.0, false, fill = 0.0).count { it })
        assertEquals((s.total * 0.5).px(), SandLayout.layout(s, 0.0, false, fill = 0.5).count { it })
        assertEquals(s.total, SandLayout.layout(s, 0.0, false, fill = 1.0).count { it })
    }
}
