package app.backlit.pet

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** One-line, human hints for the PET tab: what the mood is doing and when it will change. */
object PetInsight {
    private const val BORED_BELOW = 40.0
    private const val SAD_BELOW = 15.0

    fun hint(mood: Double, now: Long, sleep: SleepWindow, zone: ZoneId, charging: Boolean, asleep: Boolean): String = when {
        asleep -> "Sleeping until %02d:00 · mood won't drop".format(sleep.endHour)
        charging -> "Snacking · +1 mood per minute"
        mood >= BORED_BELOW -> "Gets bored in " + duration(wallMsFor(mood - BORED_BELOW, now, sleep, zone))
        mood >= SAD_BELOW -> "Gets sad in " + duration(wallMsFor(mood - SAD_BELOW, now, sleep, zone))
        else -> "Pet him to cheer him up"
    }

    /** Wall-clock time until [points] of mood have decayed, skipping sleep hours (no decay while asleep). */
    private fun wallMsFor(points: Double, now: Long, sleep: SleepWindow, zone: ZoneId): Long {
        var need = (points * PetMood.DECAY_MS).roundToLong()
        var t = now
        var guard = 0
        while (need > 0 && guard++ < 24 * 30) {
            val nextHour = Instant.ofEpochMilli(t).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val span = nextHour - t
            if (!sleep.contains(t, zone)) {
                if (span >= need) return t + need - now
                need -= span
            }
            t = nextHour
        }
        return t - now
    }

    private fun duration(ms: Long): String {
        val min = (ms / 60_000.0).roundToLong()
        return if (min < 60) "~$min min" else "~${(min / 60.0).roundToLong()} h"
    }
}
