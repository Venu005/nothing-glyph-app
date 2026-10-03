package app.backlit.data

import app.backlit.render.DayLight
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayLightResolverTest {

    private var calls = 0
    private val fake = { _: LocalDate, _: Double, _: Double, _: ZoneId -> calls++; DayLight(300 + calls, 1100) }
    private val located = Settings(locationMode = LocationMode.CITY, lat = 51.5, lon = -0.1)
    private val day1 = LocalDate.of(2026, 10, 3)
    private val london = ZoneId.of("Europe/London")

    @Test
    fun fixedModeIgnoresCoordinates() {
        val r = DayLightResolver(fake)
        assertEquals(DayLight.FIXED, r.resolve(located.copy(locationMode = LocationMode.FIXED), day1, london))
        assertEquals(0, calls)
    }

    @Test
    fun missingCoordinatesFallBackToFixed() {
        val r = DayLightResolver(fake)
        assertEquals(DayLight.FIXED, r.resolve(Settings(locationMode = LocationMode.APPROXIMATE), day1, london))
    }

    @Test
    fun cachesWithinTheSameDay() {
        val r = DayLightResolver(fake)
        r.resolve(located, day1, london)
        r.resolve(located, day1, london)
        assertEquals(1, calls)
    }

    @Test
    fun recomputesWhenDateChanges() {
        val r = DayLightResolver(fake)
        val a = r.resolve(located, day1, london)
        val b = r.resolve(located, day1.plusDays(1), london)
        assertEquals(2, calls)
        assertEquals(301, a.riseMinute)
        assertEquals(302, b.riseMinute)
    }

    @Test
    fun recomputesWhenZoneChanges() {
        val r = DayLightResolver(fake)
        r.resolve(located, day1, london)
        r.resolve(located, day1, ZoneId.of("Asia/Kolkata"))
        assertEquals(2, calls)
    }
}
