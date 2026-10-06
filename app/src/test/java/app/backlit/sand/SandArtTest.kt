package app.backlit.sand

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SandArtTest {
    private val m = TimerState.MIN

    private fun assertInMask(g: PixelGrid, label: String) {
        assertTrue("$label has lit pixels", g.litCount() > 0)
        for (y in 0 until g.size) for (x in 0 until g.size) if (g[x, y] > 0) assertTrue("$label $x,$y", g.hasLed(x, y))
    }

    @Test
    fun everyViewRendersInsideTheMask() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            val grains = SandLayout.layout(s, 0.5, false)
            val states = listOf(
                TimerState(),
                TimerState(phase = Phase.RUNNING, endAt = 2 * m),
                TimerState(phase = Phase.PAUSED, leftMs = m),
                TimerState(phase = Phase.DONE, doneAt = 0),
                TimerState(numberUntil = 100, numberValue = 25, refillUntil = 800),
            )
            for (st in states) for (t in listOf(0L, 50L, 400L, 1250L, 1400L, 5000L)) {
                assertInMask(SandArt.frame(s, grains, null, st, t), "frame n=$n ${st.phase} t=$t")
                assertInMask(SandArt.still(s, st, t), "still n=$n ${st.phase} t=$t")
            }
        }
    }

    @Test
    fun flipMeSpinsTheDonePictureHalfATurn() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            val src = SandArt.picture(s, SandLayout.layout(s, 0.0, false), null, 0.3)
            val start = SandArt.flipMe(s, 1, 0)
            val end = SandArt.flipMe(s, 1, 1250)
            for (y in 0 until n) for (x in 0 until n) {
                assertEquals(src[x, y], start[x, y])
                assertEquals("n=$n $x,$y", src[n - 1 - x, n - 1 - y], end[x, y])
            }
        }
    }

    @Test
    fun numberViewShowsTheDigits() {
        val s25 = HourglassShape.forSize(25)
        val g = SandArt.frame(s25, SandLayout.layout(s25, 0.0, false), null, TimerState(numberUntil = 1000, numberValue = 5), 0)
        for (x in 10..14) assertEquals(255, g[x, 9])        // 5×7 "5" top bar at x0=10, y0=9
        val s13 = HourglassShape.forSize(13)
        val h = SandArt.frame(s13, SandLayout.layout(s13, 0.0, false), null, TimerState(numberUntil = 1000, numberValue = 5), 0)
        for (x in 5..7) assertEquals(255, h[x, 4])          // 3×5 "5" top bar at x0=5, y0=4
    }

    @Test
    fun runningStillHasABrightStreamAndDoneHasABrightGlass() {
        val s = HourglassShape.forSize(25)
        val run = SandArt.still(s, TimerState(phase = Phase.RUNNING, durationMs = 100_000, endAt = 60_000), 0)
        assertEquals(255, run[s.center, s.center])
        val done = SandArt.still(s, TimerState(phase = Phase.DONE), 0)
        val wallCell = s.kind.indices.first { s.kind[it] == Cell.WALL }
        assertEquals(89, done[wallCell % 25, wallCell / 25])   // 0.35 × 255 = 89.25 → 89
    }

    @Test
    fun previewsParse() {
        assertNotNull(SandPreviewAnimation.parse(SandPreviewAnimation.RUNNING_ID))
        assertNotNull(SandPreviewAnimation.parse("sand:done"))
        assertEquals(null, SandPreviewAnimation.parse("pet:happy"))
        for (id in listOf("sand:ready", "sand:running", "sand:done")) for (n in listOf(25, 13)) {
            val a = SandPreviewAnimation.parse(id)!!
            for (t in 0L..a.loopMs step 250) assertInMask(a.frame(n, t), "$id n=$n t=$t")
        }
    }
    @Test
    fun runningShowsAFallingStreamUnderTheNeck() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            val c = s.center
            val grains = SandLayout.layout(s, 0.5, false)
            val st = TimerState(phase = Phase.RUNNING, durationMs = 10 * m, endAt = 5 * m)
            val pileTop = (c + 1 until n).first { grains[it * n + c] }
            fun column(g: PixelGrid) = (c + 1 until pileTop).map { g[c, it] }
            val a = SandArt.frame(s, grains, null, st, 0)
            val b = SandArt.frame(s, grains, null, st, 90)
            assertEquals(255, a[c, c])
            assertTrue("n=$n stream lit", column(a).any { it == 255 })
            assertTrue("n=$n stream moves", column(a) != column(b))
            val paused = SandArt.frame(s, grains, null, st.copy(phase = Phase.PAUSED, leftMs = 5 * m), 0)
            assertTrue("n=$n no stream when paused", column(paused).all { it == 0 })
            val empty = SandArt.frame(s, SandLayout.layout(s, 0.0, false), null, st, 0)
            assertEquals("n=$n no stream with an empty top", 0, empty[c, c])
        }
    }
}
