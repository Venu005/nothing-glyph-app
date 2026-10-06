package app.backlit.sand

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.hypot

enum class Phase { READY, RUNNING, PAUSED, DONE }

enum class Orientation {
    UP, DOWN, SIDE, FLAT;

    companion object {
        /** (gx, gy): gravity in matrix coordinates, m/s² (+y down the matrix, so UP means the top bulb is up). */
        fun of(gx: Float, gy: Float): Orientation {
            val m = hypot(gx, gy)
            if (m < 3f) return FLAT
            val ny = gy / m
            return when {
                ny > 0.2f -> UP
                ny < -0.2f -> DOWN
                else -> SIDE
            }
        }
    }
}

/**
 * The hourglass clock, kept apart from the pixels. The bulb that is up ([upSide]) holds the time left, so a flip
 * swaps time left and time run. Wall-clock ms throughout.
 */
@Serializable
data class TimerState(
    val phase: Phase = Phase.READY,
    val durationMs: Long = 5 * MIN,
    val presetIndex: Int = 2,
    val upSide: Int = 1,
    val endAt: Long = 0L,
    val leftMs: Long = 0L,
    val doneAt: Long = 0L,
    val sideSince: Long = 0L,
    val numberUntil: Long = 0L,
    val numberValue: Int = 0,
    val refillUntil: Long = 0L,
    val lastPressAt: Long = 0L,
) {
    fun timeLeft(now: Long): Long = when (phase) {
        Phase.READY -> durationMs
        Phase.RUNNING -> (endAt - now).coerceIn(0L, durationMs)
        Phase.PAUSED -> leftMs
        Phase.DONE -> 0L
    }

    /** Share of the sand in the up bulb (READY and DONE rest in the down bulb). */
    fun fractionUp(now: Long): Double = when (phase) {
        Phase.RUNNING, Phase.PAUSED -> timeLeft(now).toDouble() / durationMs
        else -> 0.0
    }

    fun tick(now: Long): TimerState =
        if (phase == Phase.RUNNING && now >= endAt) copy(phase = Phase.DONE, doneAt = endAt) else this

    fun onOrientation(o: Orientation, now: Long): TimerState {
        val t = tick(now)
        return when (o) {
            Orientation.FLAT -> t.flat()
            Orientation.SIDE -> t.onSide(now)
            Orientation.UP -> t.onUp(1, now)
            Orientation.DOWN -> t.onUp(-1, now)
        }
    }

    /** First reading after the toy (re)appears: READY/DONE only learn the orientation; a running timer still flips. */
    fun baseline(o: Orientation, now: Long): TimerState {
        val up = when (o) { Orientation.UP -> 1; Orientation.DOWN -> -1; else -> return tick(now) }
        return if (phase == Phase.READY || phase == Phase.DONE) copy(upSide = up, sideSince = 0L) else onOrientation(o, now)
    }

    /**
     * The reading that settles the baseline when the toy (re)appears. READY/DONE wait for a definite UP/DOWN
     * (FLAT or SIDE teach nothing, so the baseline stays pending: second = false); RUNNING/PAUSED take any reading.
     */
    fun firstReading(o: Orientation, now: Long): Pair<TimerState, Boolean> = when {
        phase == Phase.RUNNING || phase == Phase.PAUSED -> onOrientation(o, now) to true
        o == Orientation.UP || o == Orientation.DOWN -> baseline(o, now) to true
        else -> onOrientation(o, now) to false
    }

    /**
     * Face-down on a desk (the usual way to look at the matrix) gravity points through it, so the matrix is read
     * upright: the top bulb holds the time left and sand falls to the bottom. A change of view, not a flip.
     */
    private fun flat(): TimerState = if (upSide == 1 && sideSince == 0L) this else copy(upSide = 1, sideSince = 0L)

    private fun onSide(now: Long): TimerState {
        if (sideSince == 0L) return copy(sideSince = now)
        if (phase != Phase.RUNNING || now - sideSince < SIDE_MS) return this
        return copy(phase = Phase.PAUSED, leftMs = (endAt - sideSince).coerceIn(0L, durationMs))
    }

    private fun onUp(up: Int, now: Long): TimerState {
        val s = copy(sideSince = 0L)
        return when (phase) {
            Phase.READY, Phase.DONE -> if (up != upSide) s.run(durationMs, up, now) else s
            Phase.RUNNING -> if (up != upSide) s.run(durationMs - timeLeft(now), up, now) else s
            Phase.PAUSED -> if (up == upSide) s.run(leftMs, up, now) else s.run(durationMs - leftMs, up, now)
        }
    }

    private fun run(left: Long, up: Int, now: Long): TimerState =
        if (left <= 0L) copy(phase = Phase.DONE, upSide = up, doneAt = now, leftMs = 0L, numberUntil = 0L, refillUntil = 0L)
        else copy(phase = Phase.RUNNING, upSide = up, endAt = now + left, leftMs = 0L, numberUntil = 0L, refillUntil = 0L)

    fun longPress(now: Long, presets: List<Int>): TimerState {
        val list = presets.ifEmpty { DEFAULT_PRESETS }
        return when (phase) {
            Phase.READY, Phase.DONE -> cycle(now, list)
            Phase.RUNNING, Phase.PAUSED ->
                if (lastPressAt > 0L && now - lastPressAt <= DOUBLE_MS) cycle(now, list)
                else copy(
                    numberUntil = now + NUMBER_MS,
                    numberValue = ((timeLeft(now) + MIN - 1) / MIN).toInt().coerceAtLeast(1),
                    refillUntil = 0L,
                    lastPressAt = now,
                )
        }
    }

    private fun cycle(now: Long, list: List<Int>): TimerState {
        // The next time after the one showing, so editing the list (removing or adding times) never skips one.
        val minutes = durationMs / MIN
        val i = list.indexOfFirst { it > minutes }.takeIf { it >= 0 } ?: 0
        return TimerState(
            phase = Phase.READY, durationMs = list[i] * MIN, presetIndex = i, upSide = upSide,
            numberUntil = now + NUMBER_MS, numberValue = list[i], refillUntil = now + NUMBER_MS + REFILL_MS,
        )
    }

    /** The TIMER tab picks a preset directly. */
    fun select(index: Int, presets: List<Int>): TimerState {
        val list = presets.ifEmpty { DEFAULT_PRESETS }
        val i = index.coerceIn(0, list.size - 1)
        return TimerState(phase = Phase.READY, durationMs = list[i] * MIN, presetIndex = i, upSide = upSide)
    }

    /** The done alarm: finishes a RUNNING timer due within 1 s; anything else is left alone (null). */
    fun alarmFired(now: Long): TimerState? =
        if (phase == Phase.RUNNING && endAt <= now + 1000) copy(phase = Phase.DONE, doneAt = endAt) else null

    /** What is saved: the transient display and debounce fields are dropped. */
    fun persisted(): TimerState = copy(sideSince = 0L, numberUntil = 0L, numberValue = 0, refillUntil = 0L, lastPressAt = 0L)

    fun encode(): String = json.encodeToString(persisted())

    companion object {
        const val MIN = 60_000L
        const val SIDE_MS = 500L
        const val DOUBLE_MS = 2_000L
        const val NUMBER_MS = 1_500L
        const val REFILL_MS = 700L
        const val FLIP_ME_MS = 2_300L
        val DEFAULT_PRESETS = listOf(1, 3, 5, 10, 25)
        private val json = Json { ignoreUnknownKeys = true }

        /** The alarm's DONE transition from the stored state: the new JSON, or null when it isn't due or is already done (no second ring). */
        fun claimDone(storedJson: String, now: Long): String? = decode(storedJson).alarmFired(now)?.encode()

        fun decode(s: String): TimerState =
            if (s.isBlank()) TimerState() else runCatching { json.decodeFromString<TimerState>(s) }.getOrDefault(TimerState())

        /** m:ss, seconds rounded up. */
        fun clock(ms: Long): String {
            val s = (ms.coerceAtLeast(0) + 999) / 1000
            return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
        }
    }
}
