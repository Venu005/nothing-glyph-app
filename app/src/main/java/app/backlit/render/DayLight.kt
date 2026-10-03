package app.backlit.render

/** Daylight window in local minutes of the day [0, 1440]. Handles sunset after midnight. */
data class DayLight(val riseMinute: Int, val setMinute: Int) {

    fun isDay(minuteOfDay: Double): Boolean = when {
        riseMinute == setMinute -> false
        riseMinute < setMinute -> minuteOfDay >= riseMinute && minuteOfDay < setMinute
        else -> minuteOfDay >= riseMinute || minuteOfDay < setMinute
    }

    companion object {
        val FIXED = DayLight(360, 1080)
        val ALL_DAY = DayLight(0, 1440)
        val ALL_NIGHT = DayLight(0, 0)
    }
}
