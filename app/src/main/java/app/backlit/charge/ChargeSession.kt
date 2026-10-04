package app.backlit.charge

data class Battery(val plugged: Boolean, val level: Int)

enum class Moment { STILL, PLUG_IN, CHARGING, DONE }

data class Show(val moment: Moment, val startedAt: Long)

/**
 * What the Charge toy shows, from battery events. Pure: callers pass the time.
 * A session runs from plug-in to unplug; DONE plays at most once per session, and only when the level
 * crosses the target while charging and the toy is active.
 */
class ChargeSession(private val target: () -> Int) {
    var show = Show(Moment.STILL, 0)
        private set
    var level = 0
        private set

    private var plugged = false
    private var donePlayed = false
    private var donePending = false

    fun onBind(b: Battery, now: Long) {
        level = b.level
        plugged = b.plugged
        donePending = false
        donePlayed = b.plugged && b.level >= target()
        show = Show(if (b.plugged) Moment.CHARGING else Moment.STILL, now)
    }

    fun onBattery(b: Battery, now: Long, active: Boolean) {
        val prev = level
        level = b.level
        if (!b.plugged) {
            if (plugged || show.moment != Moment.STILL) show = Show(Moment.STILL, now)
            plugged = false
            donePending = false
            return
        }
        if (!plugged) {                                  // a new session
            plugged = true
            donePending = false
            donePlayed = b.level >= target()             // already there: no crossing, no done
            show = Show(if (active) Moment.PLUG_IN else Moment.CHARGING, now)
            return
        }
        if (!donePlayed && prev < target() && level >= target()) {
            donePlayed = true
            if (!active) return
            if (show.moment == Moment.PLUG_IN) donePending = true else show = Show(Moment.DONE, now)
        }
    }

    fun tick(now: Long) {
        val s = show
        when (s.moment) {
            Moment.PLUG_IN -> if (now - s.startedAt >= PLUG_IN_MS) {
                show = if (donePending) Show(Moment.DONE, now) else Show(Moment.CHARGING, now)
                donePending = false
            }
            Moment.DONE -> if (now - s.startedAt >= DONE_MS) show = Show(Moment.CHARGING, now)
            else -> Unit
        }
    }

    fun nextWakeAt(): Long? = when (show.moment) {
        Moment.PLUG_IN -> show.startedAt + PLUG_IN_MS
        Moment.DONE -> show.startedAt + DONE_MS
        else -> null
    }

    companion object {
        const val PLUG_IN_MS = 5000L
        const val DONE_MS = 3500L
    }
}
