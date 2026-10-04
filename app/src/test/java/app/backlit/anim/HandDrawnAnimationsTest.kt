package app.backlit.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class HandDrawnAnimationsTest {

    @Test
    fun heartBigBitmapOn25IsExactAndSymmetric() {
        val g = Heartbeat.frame(25, 0)                 // phase 0 → big heart
        for (x in 7..10) assertEquals(255, g[x, 6])    // "..XXXX...XXXX.." at x0 = 5, y0 = 6
        for (x in 14..17) assertEquals(255, g[x, 6])
        assertEquals(0, g[11, 6])
        assertEquals(255, g[12, 18])                   // tip
        for (y in 0 until 25) for (x in 0 until 25) assertEquals("x=$x y=$y", g[x, y], g[24 - x, y])
    }

    @Test
    fun heartRestsSmallAndDimmer() {
        val g = Heartbeat.frame(25, 600)               // phase 0.5 → small heart at 153
        assertEquals(153, g[12, 16])                   // tip of 11×9 at x0 = 7, y0 = 8
        assertEquals(0, g[12, 18])
        val s = Heartbeat.frame(13, 600)
        assertEquals(153, s[6, 8])                     // tip of 7×6 at x0 = 3, y0 = 3
        for (y in 0 until 13) for (x in 0 until 13) assertEquals(s[x, y], s[12 - x, y])
    }

    @Test
    fun heart13BigBitmap() {
        val g = Heartbeat.frame(13, 0)                 // 9×8 at x0 = 2, y0 = 2
        assertEquals(255, g[3, 2]); assertEquals(255, g[4, 2]); assertEquals(0, g[5, 2])
        assertEquals(255, g[6, 9])                     // tip
    }

    @Test
    fun smileyOutlineIsAGapFreeRoundCircle() {
        // Midpoint circle: the cardinal points must be present (the old distance band left holes there).
        val big = SmileyWink.frame(25, 0)
        for ((x, y) in listOf(12 to 4, 12 to 20, 4 to 12, 20 to 12, 10 to 4, 14 to 4)) assertEquals("($x,$y)", 140, big[x, y])
        val small = SmileyWink.frame(13, 0)
        for ((x, y) in listOf(6 to 1, 6 to 11, 1 to 6, 11 to 6)) assertEquals("($x,$y)", 140, small[x, y])
        for ((size, g) in listOf(25 to big, 13 to small)) {
            for (y in 0 until size) for (x in 0 until size) {
                assertEquals("size=$size mirror x", g[x, y], g[size - 1 - x, y])
                assertEquals("size=$size mirror y (outline only)", if (g[x, y] == 140) 140 else -1, if (g[x, size - 1 - y] == 140 && g[x, y] == 140) 140 else -1)
            }
            // Every outline pixel has an outline neighbour on both sides → no gaps.
            for (y in 0 until size) for (x in 0 until size) if (g[x, y] == 140) {
                val n = (-1..1).sumOf { dy -> (-1..1).count { dx -> (dx != 0 || dy != 0) && g[x + dx, y + dy] == 140 } }
                assertTrue("size=$size ($x,$y) has $n outline neighbours", n >= 2)
            }
        }
    }

    @Test
    fun smileyFacePixelsAndWink() {
        val open = SmileyWink.frame(25, 0)
        assertEquals(255, open[9, 9]); assertEquals(255, open[15, 10])
        assertEquals(255, open[12, 15]); assertEquals(0, open[8, 13])
        val wink = SmileyWink.frame(25, 1500)          // phase 0.625 → wink
        assertEquals(255, wink[13, 10]); assertEquals(255, wink[16, 10])
        assertEquals(0, wink[14, 9])
        assertEquals(255, wink[8, 13]); assertEquals(255, wink[16, 13])
        val small = SmileyWink.frame(13, 0)
        assertEquals(255, small[4, 4]); assertEquals(255, small[8, 4]); assertEquals(255, small[6, 8])
    }

    @Test
    fun ringing13IsHandPlaced() {
        val mid = Ringing.frame(13, 600)               // phase 0.5: phone still, inner arcs bright
        for ((x, y) in listOf(5 to 4, 6 to 4, 7 to 4, 5 to 6, 7 to 6, 6 to 8)) assertEquals(255, mid[x, y])
        assertEquals(255, mid[3, 6]); assertEquals(255, mid[9, 6])
        assertEquals(0, mid[1, 6])
        val late = Ringing.frame(13, 1000)             // phase 0.83: inner dim, outer on
        assertEquals(115, late[3, 6]); assertEquals(255, late[1, 6]); assertEquals(255, late[10, 8])
    }

    @Test
    fun everyFrameIsNonEmptyAtBothSizes() {
        for (a in listOf(Heartbeat, SmileyWink, Ringing)) for (size in listOf(25, 13)) {
            var t = 0L
            while (t < a.loopMs) { assertTrue("${a.id} $size $t", a.frame(size, t).litCount() > 0); t += 37 }
        }
    }
}
