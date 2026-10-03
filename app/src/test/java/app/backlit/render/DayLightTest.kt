package app.backlit.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayLightTest {

    @Test
    fun fixedIsSixToEighteen() {
        assertFalse(DayLight.FIXED.isDay(359.0))
        assertTrue(DayLight.FIXED.isDay(360.0))
        assertTrue(DayLight.FIXED.isDay(1079.9))
        assertFalse(DayLight.FIXED.isDay(1080.0))
    }

    @Test
    fun polarExtremes() {
        assertTrue(DayLight.ALL_DAY.isDay(0.0))
        assertTrue(DayLight.ALL_DAY.isDay(1439.0))
        assertFalse(DayLight.ALL_NIGHT.isDay(720.0))
    }

    @Test
    fun wrapsPastMidnight() {
        // Sunrise 03:00, sunset 00:30 the next day (high latitude summer).
        val d = DayLight(180, 30)
        assertTrue(d.isDay(10.0))
        assertFalse(d.isDay(100.0))
        assertTrue(d.isDay(200.0))
        assertTrue(d.isDay(1400.0))
    }
}
