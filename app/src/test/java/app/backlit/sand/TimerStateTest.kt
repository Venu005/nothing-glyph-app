package app.backlit.sand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimerStateTest {
    private val m = TimerState.MIN
    private val presets = listOf(1, 3, 5, 10, 25)
    private fun running(left: Long, now: Long = 0, d: Long = 5 * m, up: Int = 1) =
        TimerState(phase = Phase.RUNNING, durationMs = d, upSide = up, endAt = now + left)

    @Test
    fun orientationFromGravity() {
        assertEquals(Orientation.UP, Orientation.of(0f, 9.8f))
        assertEquals(Orientation.DOWN, Orientation.of(0f, -9.8f))
        assertEquals(Orientation.SIDE, Orientation.of(9.8f, 0.5f))
        assertEquals(Orientation.FLAT, Orientation.of(1f, 1.5f))
    }

    @Test
    fun readyStartsOnlyOnARealFlip() {
        val r = TimerState(upSide = 1)
        assertEquals(Phase.READY, r.onOrientation(Orientation.UP, 10).phase)
        assertEquals(Phase.READY, r.onOrientation(Orientation.FLAT, 10).phase)
        val s = r.onOrientation(Orientation.DOWN, 10)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(-1, s.upSide)
        assertEquals(10 + 5 * m, s.endAt)
    }

    @Test
    fun flipMidRunSwapsLeftAndRun() {
        val s = running(left = 2 * m).onOrientation(Orientation.DOWN, 0)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(3 * m, s.timeLeft(0))
        assertEquals(-1, s.upSide)
    }

    @Test
    fun flipWithNothingRunGoesStraightToDone() {
        val s = running(left = 5 * m).onOrientation(Orientation.DOWN, 0)
        assertEquals(Phase.DONE, s.phase)
        assertEquals(0L, s.doneAt)
    }

    @Test
    fun flipFromDoneRunsTheFullTimeAgain() {
        val done = TimerState(phase = Phase.DONE, durationMs = 3 * m, upSide = 1)
        val s = done.onOrientation(Orientation.DOWN, 100)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(3 * m, s.timeLeft(100))
    }

    @Test
    fun tickFinishesAtEndAt() {
        val s = running(left = 1000)
        assertEquals(Phase.RUNNING, s.tick(999).phase)
        val d = s.tick(1000)
        assertEquals(Phase.DONE, d.phase)
        assertEquals(1000L, d.doneAt)
    }

    @Test
    fun sidePausesAfterDebounceAndKeepsTimeFromWhenItTipped() {
        val s0 = running(left = 2 * m)
        val s1 = s0.onOrientation(Orientation.SIDE, 1000)
        assertEquals(Phase.RUNNING, s1.phase)
        val s2 = s1.onOrientation(Orientation.SIDE, 1499)
        assertEquals(Phase.RUNNING, s2.phase)
        val s3 = s2.onOrientation(Orientation.SIDE, 1500)
        assertEquals(Phase.PAUSED, s3.phase)
        assertEquals(2 * m - 1000, s3.timeLeft(99_999))
    }

    @Test
    fun aWobbleDoesNotPause() {
        val s = running(left = 2 * m).onOrientation(Orientation.SIDE, 0).onOrientation(Orientation.UP, 300).onOrientation(Orientation.SIDE, 400)
        assertEquals(Phase.RUNNING, s.onOrientation(Orientation.SIDE, 800).phase)
    }

    @Test
    fun resumeSameWayKeepsTimeOppositeWayFlips() {
        val paused = TimerState(phase = Phase.PAUSED, durationMs = 5 * m, upSide = 1, leftMs = 2 * m)
        assertEquals(2 * m, paused.onOrientation(Orientation.UP, 0).timeLeft(0))
        val flipped = paused.onOrientation(Orientation.DOWN, 0)
        assertEquals(3 * m, flipped.timeLeft(0))
        assertEquals(-1, flipped.upSide)
    }

    @Test
    fun flatChangesNothing() {
        val s = running(left = 2 * m)
        assertEquals(s, s.onOrientation(Orientation.FLAT, 10))
    }

    @Test
    fun baselineNeverStartsReadyOrDone() {
        val r = TimerState(upSide = 1).baseline(Orientation.DOWN, 0)
        assertEquals(Phase.READY, r.phase)
        assertEquals(-1, r.upSide)
        val d = TimerState(phase = Phase.DONE, upSide = 1).baseline(Orientation.DOWN, 0)
        assertEquals(Phase.DONE, d.phase)
        // a running timer that was flipped while away still flips
        assertEquals(3 * m, running(left = 2 * m).baseline(Orientation.DOWN, 0).timeLeft(0))
    }

    @Test
    fun longPressInReadyCyclesPresetsAndShowsTheNumber() {
        val s = TimerState(presetIndex = 2, durationMs = 5 * m).longPress(1000, presets)
        assertEquals(Phase.READY, s.phase)
        assertEquals(3, s.presetIndex)
        assertEquals(10 * m, s.durationMs)
        assertEquals(10, s.numberValue)
        assertEquals(1000 + TimerState.NUMBER_MS, s.numberUntil)
        assertEquals(1000 + TimerState.NUMBER_MS + TimerState.REFILL_MS, s.refillUntil)
        assertEquals(0, TimerState(presetIndex = 4).longPress(0, presets).presetIndex)   // wraps
    }

    @Test
    fun longPressWithIndexPastEndWraps() {
        val s = TimerState(presetIndex = 9).longPress(0, listOf(2, 4))
        assertEquals(0, s.presetIndex)
        assertEquals(2 * m, s.durationMs)
        assertEquals(1, TimerState(presetIndex = 0).longPress(0, emptyList()).presetIndex)   // empty → defaults
    }

    @Test
    fun longPressWhileRunningShowsMinutesLeftThenASecondPressCycles() {
        val r = running(left = 2 * m + 1, now = 1000)
        val shown = r.longPress(1000, presets)
        assertEquals(Phase.RUNNING, shown.phase)
        assertEquals(3, shown.numberValue)
        assertEquals(r.endAt, shown.endAt)
        val again = shown.longPress(1000 + TimerState.DOUBLE_MS, presets)
        assertEquals(Phase.READY, again.phase)
        val late = shown.longPress(1000 + TimerState.DOUBLE_MS + 1, presets)
        assertEquals(Phase.RUNNING, late.phase)
    }

    @Test
    fun selectSetsReadyAtThatPreset() {
        val s = running(left = m).select(1, presets)
        assertEquals(Phase.READY, s.phase)
        assertEquals(3 * m, s.durationMs)
        assertEquals(1, s.presetIndex)
    }

    @Test
    fun alarmOnlyFinishesARunningTimer() {
        assertEquals(Phase.DONE, running(left = 1000).alarmFired(500)?.phase)          // within 1 s early
        assertNull(running(left = 5000).alarmFired(500))
        assertNull(TimerState(phase = Phase.PAUSED, leftMs = 0).alarmFired(10))
        assertNull(TimerState(phase = Phase.DONE).alarmFired(10))
    }

    @Test
    fun restoreRoundTripAndBadJson() {
        val s = running(left = 90_000, now = 123).copy(presetIndex = 3, sideSince = 5)
        assertEquals(s.persisted(), TimerState.decode(s.encode()))
        assertEquals(TimerState(), TimerState.decode(""))
        assertEquals(TimerState(), TimerState.decode("{nope"))
        assertEquals(s.copy(sideSince = 0), s.persisted())
    }

    @Test
    fun fractionsAndClock() {
        assertEquals(0.0, TimerState().fractionUp(0), 0.0)
        assertEquals(0.4, running(left = 2 * m).fractionUp(0), 1e-9)
        assertEquals("2:00", TimerState.clock(2 * m))
        assertEquals("0:01", TimerState.clock(1))
        assertEquals("25:00", TimerState.clock(25 * m))
    }
    @Test
    fun firstReadingWaitsForADefiniteOrientationBeforeBaselining() {
        val done = TimerState(phase = Phase.DONE, upSide = -1)
        val (s1, settled1) = done.firstReading(Orientation.FLAT, 0)
        assertEquals(false, settled1)
        val (s2, settled2) = s1.firstReading(Orientation.UP, 10)
        assertEquals(true, settled2)
        assertEquals(Phase.DONE, s2.phase)      // learned the orientation, did not start
        assertEquals(1, s2.upSide)
        val (r, settledR) = TimerState(upSide = -1).firstReading(Orientation.SIDE, 0)
        assertEquals(false, settledR)
        assertEquals(Phase.READY, r.phase)
        // a running timer takes any reading as normal
        val (run, settledRun) = running(left = 2 * m).firstReading(Orientation.FLAT, 0)
        assertEquals(true, settledRun)
        assertEquals(Phase.RUNNING, run.phase)
    }

    @Test
    fun lyingFlatShowsTheTimeLeftInTheTopBulbWithoutChangingTime() {
        // face-down on a desk there is no "down" on the matrix: the top bulb holds the time left, sand falls to the bottom
        val upsideDown = running(left = 2 * m, up = -1)
        val flat = upsideDown.onOrientation(Orientation.FLAT, 0)
        assertEquals(Phase.RUNNING, flat.phase)
        assertEquals(1, flat.upSide)
        assertEquals(2 * m, flat.timeLeft(0))
        assertEquals(1, TimerState(upSide = -1).onOrientation(Orientation.FLAT, 0).upSide)
        assertEquals(Phase.READY, TimerState(upSide = -1).onOrientation(Orientation.FLAT, 0).phase)
        // then turning it upside down in the hand is a real flip
        assertEquals(3 * m, flat.onOrientation(Orientation.DOWN, 0).timeLeft(0))
        val (first, settled) = TimerState(phase = Phase.DONE, upSide = -1).firstReading(Orientation.FLAT, 0)
        assertEquals(1, first.upSide)
        assertEquals(false, settled)
    }
}
