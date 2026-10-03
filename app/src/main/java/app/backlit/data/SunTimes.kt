package app.backlit.data

import app.backlit.render.DayLight
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

object SunTimes {

    /** Official zenith for sunrise/sunset, including refraction and the sun's radius. */
    private const val ZENITH = 90.833

    private sealed interface Event {
        data class At(val utcHours: Double) : Event
        data object NeverRises : Event
        data object NeverSets : Event
    }

    fun compute(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): DayLight {
        val rise = event(date, lat, lon, rising = true)
        val set = event(date, lat, lon, rising = false)
        if (rise is Event.NeverRises || set is Event.NeverRises) return DayLight.ALL_NIGHT
        if (rise is Event.NeverSets || set is Event.NeverSets) return DayLight.ALL_DAY
        return DayLight(
            localMinute(date, (rise as Event.At).utcHours, zone),
            localMinute(date, (set as Event.At).utcHours, zone),
        )
    }

    private fun event(date: LocalDate, lat: Double, lon: Double, rising: Boolean): Event {
        val n = date.dayOfYear
        val lngHour = lon / 15.0
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        val l = norm360(m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634)
        var ra = norm360(atanD(0.91764 * tanD(l)))
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sinD(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosD(ZENITH) - sinDec * sinD(lat)) / (cosDec * cosD(lat))
        if (cosH > 1) return Event.NeverRises
        if (cosH < -1) return Event.NeverSets
        val h = (if (rising) 360.0 - acosD(cosH) else acosD(cosH)) / 15.0
        val localMeanTime = h + ra - 0.06571 * t - 6.622
        return Event.At(norm24(localMeanTime - lngHour))
    }

    private fun localMinute(date: LocalDate, utcHours: Double, zone: ZoneId): Int {
        val instant = date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds((utcHours * 3600).roundToLong())
        val t = instant.atZone(zone).toLocalTime()
        return t.hour * 60 + t.minute
    }

    private fun sinD(d: Double) = sin(Math.toRadians(d))
    private fun cosD(d: Double) = cos(Math.toRadians(d))
    private fun tanD(d: Double) = tan(Math.toRadians(d))
    private fun atanD(x: Double) = Math.toDegrees(atan(x))
    private fun acosD(x: Double) = Math.toDegrees(acos(x))
    private fun norm360(d: Double) = ((d % 360.0) + 360.0) % 360.0
    private fun norm24(h: Double) = ((h % 24.0) + 24.0) % 24.0
}
