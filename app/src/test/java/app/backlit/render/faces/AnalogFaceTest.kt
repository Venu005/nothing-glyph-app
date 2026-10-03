package app.backlit.render.faces

import app.backlit.render.FaceContext
import app.backlit.render.FaceOptions
import app.backlit.render.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalogFaceTest {

    private fun ctx(h: Int, m: Int, s: Int = 0, size: Int = 25, mode: Mode = Mode.ACTIVE, sec: Boolean = false) =
        FaceContext(h, m, s, size, mode, FaceOptions(secondHand = sec))

    @Test
    fun noonOn25StacksHourOverMinute() {
        val g = AnalogFace.render(ctx(12, 0))
        for (y in 6..12) assertEquals("hour row $y", 255, g[12, y])
        for (y in 3..5) assertEquals("minute row $y", 170, g[12, y])
        assertEquals(200, g[12, 0])      // 12 o'clock major tick
        assertEquals(0, g[12, 2])
    }

    @Test
    fun threeOClockHourPointsRight() {
        val g = AnalogFace.render(ctx(3, 0))
        for (x in 12..18) assertEquals("hour col $x", 255, g[x, 12])
        for (y in 3..11) assertEquals("minute row $y", 170, g[12, y])
        assertEquals(200, g[24, 12])     // 3 o'clock tick
    }

    @Test
    fun minorTicksOnlyOn25() {
        val big = AnalogFace.render(ctx(6, 30))
        assertEquals(70, big[18, 2])     // 1 o'clock tick on 25×25
        val small = AnalogFace.render(ctx(6, 30, size = 13, mode = Mode.AOD))
        assertEquals(0, small[9, 1])     // no 1 o'clock tick on 13×13
        assertEquals(200, small[6, 0])
        assertEquals(200, small[12, 6])
    }

    @Test
    fun noonOn13() {
        val g = AnalogFace.render(ctx(12, 0, size = 13, mode = Mode.AOD))
        assertEquals(170, g[6, 2])
        for (y in 3..6) assertEquals(255, g[6, y])
    }

    @Test
    fun minuteHandIsStillWithinAMinute() {
        val a = AnalogFace.render(ctx(10, 41, 5))
        val b = AnalogFace.render(ctx(10, 41, 55))
        assertEquals(a, b)
    }

    @Test
    fun secondDotOnlyWhenActiveOn25AndEnabled() {
        val on = AnalogFace.render(ctx(12, 0, 0, sec = true))
        assertEquals(255, on[12, 0])
        val at15 = AnalogFace.render(ctx(12, 0, 15, sec = true))
        assertEquals(255, at15[24, 12])
        val aod = AnalogFace.render(ctx(12, 0, 15, mode = Mode.AOD, sec = true))
        assertEquals(AnalogFace.render(ctx(12, 0, 15, mode = Mode.AOD, sec = false)), aod)
        val off = AnalogFace.render(ctx(12, 0, 15, sec = false))
        assertNotEquals(at15, off)
    }

    @Test
    fun needsSecondTicks() {
        assertTrue(AnalogFace.needsSecondTicks(ctx(1, 1, sec = true)))
        assertFalse(AnalogFace.needsSecondTicks(ctx(1, 1, sec = false)))
        assertFalse(AnalogFace.needsSecondTicks(ctx(1, 1, mode = Mode.AOD, sec = true)))
        assertFalse(AnalogFace.needsSecondTicks(ctx(1, 1, size = 13, sec = true)))
    }

    @Test
    fun registryCyclesAndFallsBack() {
        assertEquals(AnalogFace, Faces.byId("analog"))
        assertEquals(AnalogFace, Faces.byId("does-not-exist"))
        assertEquals(Faces.all[(Faces.all.indexOf(AnalogFace) + 1) % Faces.all.size], Faces.next("analog"))
        assertEquals(Faces.all.first(), Faces.next(Faces.all.last().id))
    }
}
