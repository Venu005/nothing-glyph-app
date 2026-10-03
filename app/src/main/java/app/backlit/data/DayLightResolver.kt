package app.backlit.data

import app.backlit.render.DayLight
import java.time.LocalDate
import java.time.ZoneId

/** Settings + date + zone → today's daylight window, cached until any input changes. */
class DayLightResolver(
    private val compute: (LocalDate, Double, Double, ZoneId) -> DayLight = SunTimes::compute,
) {
    private data class Key(val date: LocalDate, val lat: Double, val lon: Double, val zone: ZoneId)

    private var key: Key? = null
    private var cached: DayLight = DayLight.FIXED

    fun resolve(settings: Settings, date: LocalDate, zone: ZoneId): DayLight {
        val lat = settings.lat
        val lon = settings.lon
        if (settings.locationMode == LocationMode.FIXED || lat == null || lon == null) return DayLight.FIXED
        val k = Key(date, lat, lon, zone)
        if (k != key) {
            cached = compute(date, lat, lon, zone)
            key = k
        }
        return cached
    }
}
