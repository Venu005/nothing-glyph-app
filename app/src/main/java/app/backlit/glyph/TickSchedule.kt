package app.backlit.glyph

object TickSchedule {
    private const val LANDING_MS = 20L

    /** Milliseconds until just after the next wall-clock second (or minute). */
    fun delayToNextTick(nowMillis: Long, perSecond: Boolean): Long {
        val period = if (perSecond) 1_000L else 60_000L
        return period - (nowMillis % period) + LANDING_MS
    }
}
