package app.backlit.render.faces

import app.backlit.render.DayLight
import app.backlit.render.FaceContext
import app.backlit.render.FaceOptions
import app.backlit.render.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DayRingFaceTest {

    private fun ctx(h: Int, m: Int, size: Int = 25, use24h: Boolean = true, day: DayLight = DayLight.FIXED) =
        FaceContext(h, m, 0, size, if (size == 13) Mode.AOD else Mode.ACTIVE, FaceOptions(use24h = use24h), day)

    @Test
    fun rimIsBrightByDayAndDimByNight25() {
        val g = DayRingFace.render(ctx(15, 0))
        assertEquals(110, g[12, 0])   // noon (top) is daylight
        assertEquals(22, g[12, 24])   // midnight (bottom) is night
        assertEquals(110, g[3, 4])    // about 08:46 on the rim — daylight
    }

    @Test
    fun timeDigits24h() {
        val g = DayRingFace.render(ctx(15, 7))
        assertEquals(230, g[5, 10])   // '1'
        assertEquals(0, g[4, 10])
        assertEquals(230, g[12, 11])  // colon
        assertEquals(230, g[12, 13])
        assertEquals(0, g[12, 12])
    }

    @Test
    fun timeDigits12h() {
        val g = DayRingFace.render(ctx(15, 7, use24h = false))
        assertEquals(230, g[4, 10])   // '0' of "03"
    }

    @Test
    fun twelveHourMidnightShowsTwelve() {
        assertEquals("12", DayRingFace.hourText(0, use24h = false))
        assertEquals("12", DayRingFace.hourText(12, use24h = false))
        assertEquals("01", DayRingFace.hourText(13, use24h = false))
        assertEquals("00", DayRingFace.hourText(0, use24h = true))
        assertEquals("23", DayRingFace.hourText(23, use24h = true))
    }

    @Test
    fun sunMarkerIsBrightDisc() {
        // 15:00 → marker up-right of centre at about (19.4, 4.6) on radius 10.5.
        val g = DayRingFace.render(ctx(15, 0))
        assertEquals(255, g[19, 5])
    }

    @Test
    fun allNightRingIsDim() {
        val g = DayRingFace.render(ctx(3, 0, day = DayLight.ALL_NIGHT))
        assertEquals(22, g[12, 0])
        assertEquals(22, g[3, 4])
    }

    @Test
    fun small13HasTopAndBottomGapsAndStackedDigits() {
        val g = DayRingFace.render(ctx(15, 7, size = 13))
        assertEquals(0, g[6, 0])      // top gap
        assertEquals(0, g[6, 12])     // bottom gap
        assertEquals(90, g[1, 3])     // left-upper ring, about 08:04 — daylight
        assertEquals(255, g[4, 1])    // '1' of HH, top row
        assertEquals(255, g[7, 1])    // '5' of HH
        assertEquals(170, g[3, 7])    // '0' of MM, dimmer
        assertEquals(170, g[7, 7])    // '7' of MM
        assertEquals(255, g[10, 2])   // marker at 15:00
    }

    @Test
    fun neverNeedsSecondTicks() {
        assertFalse(DayRingFace.needsSecondTicks(ctx(15, 0)))
    }

    @Test
    fun registryIncludesBothFaces() {
        assertEquals(listOf("analog", "dayring"), Faces.all.map { it.id })
        assertEquals(AnalogFace, Faces.next("dayring"))
    }
}
