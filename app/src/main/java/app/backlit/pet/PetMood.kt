package app.backlit.pet

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class MoodState(val mood: Double, val at: Long)

/** Whole-hour sleep window; may wrap midnight; start == end means no sleep. */
data class SleepWindow(val startHour: Int, val endHour: Int) {
    val enabled: Boolean get() = startHour != endHour

    fun contains(epochMs: Long, zone: ZoneId): Boolean {
        if (!enabled) return false
        val h = Instant.ofEpochMilli(epochMs).atZone(zone).hour
        return if (startHour < endHour) h in startHour until endHour else h >= startHour || h < endHour
    }
}

/** Mood math from timestamps; nothing ticks in the background. */
object PetMood {
    const val START = 70.0
    const val DECAY_MS = 360_000L
    private const val MAX_SPAN_MS = 60L * 24 * 3_600_000

    fun clamp(v: Double): Double = v.coerceIn(0.0, 100.0)

    /** Milliseconds between [from] and [to] that fall outside the sleep window (walks whole hours). */
    fun awakeMs(from: Long, to: Long, sleep: SleepWindow, zone: ZoneId): Long {
        if (to <= from) return 0
        var t = maxOf(from, to - MAX_SPAN_MS)
        var awake = 0L
        while (t < to) {
            val nextHour = Instant.ofEpochMilli(t).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val end = minOf(to, nextHour)
            if (!sleep.contains(t, zone)) awake += end - t
            t = end
        }
        return awake
    }

    fun decay(state: MoodState, now: Long, sleep: SleepWindow, zone: ZoneId): Double {
        if (state.at <= 0 || now <= state.at) return clamp(state.mood)
        return clamp(state.mood - awakeMs(state.at, now, sleep, zone).toDouble() / DECAY_MS)
    }
}
