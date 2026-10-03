package app.backlit.data

import app.backlit.render.DayLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class SunTimesTest {

    private fun assertNear(label: String, expectedHhMm: String, actualMinute: Int) {
        val (h, m) = expectedHhMm.split(":").map { it.toInt() }
        val expected = h * 60 + m
        assertTrue("$label expected $expectedHhMm got ${actualMinute / 60}:${actualMinute % 60}",
            abs(expected - actualMinute) <= 3)
    }

    @Test
    fun londonSummerSolstice() {
        val d = SunTimes.compute(LocalDate.of(2026, 6, 21), 51.5074, -0.1278, ZoneId.of("Europe/London"))
        assertNear("rise", "04:43", d.riseMinute)
        assertNear("set", "21:21", d.setMinute)
    }

    @Test
    fun newYorkWinterSolstice() {
        val d = SunTimes.compute(LocalDate.of(2026, 12, 21), 40.7128, -74.0060, ZoneId.of("America/New_York"))
        assertNear("rise", "07:16", d.riseMinute)
        assertNear("set", "16:32", d.setMinute)
    }

    @Test
    fun singaporeEquinoxFarFromUtc() {
        val d = SunTimes.compute(LocalDate.of(2026, 3, 20), 1.3521, 103.8198, ZoneId.of("Asia/Singapore"))
        assertNear("rise", "07:09", d.riseMinute)
        assertNear("set", "19:15", d.setMinute)
    }

    @Test
    fun tromsoPolarDayAndNight() {
        val zone = ZoneId.of("Europe/Oslo")
        assertEquals(DayLight.ALL_DAY, SunTimes.compute(LocalDate.of(2026, 6, 21), 69.6492, 18.9553, zone))
        assertEquals(DayLight.ALL_NIGHT, SunTimes.compute(LocalDate.of(2026, 12, 21), 69.6492, 18.9553, zone))
    }
}
