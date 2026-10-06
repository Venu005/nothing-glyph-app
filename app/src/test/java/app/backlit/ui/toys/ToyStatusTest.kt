package app.backlit.ui.toys

import app.backlit.data.Settings
import app.backlit.sand.Phase
import app.backlit.sand.TimerState
import app.backlit.ui.home.ToyId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class ToyStatusTest {
    private val z = ZoneOffset.UTC
    private fun line(id: ToyId, s: Settings = Settings(), now: Long = 0, i: StatusInputs = StatusInputs()) = ToyStatus.line(id, s, now, z, i)

    @Test
    fun clockShowsTheFace() {
        assertEquals("ANALOG", line(ToyId.CLOCK))
        assertEquals("DAY RING", line(ToyId.CLOCK, Settings(faceId = "dayring")))
    }

    @Test
    fun musicStates() {
        assertEquals("LISTENING", line(ToyId.MUSIC))
        assertEquals("NEEDS PERMISSION", line(ToyId.MUSIC, i = StatusInputs(micGranted = false)))
        assertEquals("PHONE (3) ONLY", line(ToyId.MUSIC, i = StatusInputs(musicSupported = false)))
    }

    @Test
    fun chargeWithAndWithoutBattery() {
        assertEquals("62 % · MOON", line(ToyId.CHARGE, i = StatusInputs(battery = 62)))
        assertEquals("MOON", line(ToyId.CHARGE))
    }

    @Test
    fun canvasAndPet() {
        assertEquals("NO DRAWING YET", line(ToyId.CANVAS))
        assertEquals("HEART", line(ToyId.CANVAS, i = StatusInputs(drawingName = "Heart")))
        assertEquals("BOO IS HAPPY · MOOD 72", line(ToyId.PET, i = StatusInputs(petBase = "HAPPY", petMood = 72)))
        assertEquals("RIBBIT", line(ToyId.PET, Settings(petKind = "frog")))
    }

    @Test
    fun sandEveryPhase() {
        val m = TimerState.MIN
        assertEquals("READY · 5 MIN", line(ToyId.SAND))
        val run = TimerState(phase = Phase.RUNNING, durationMs = 5 * m, endAt = 192_000).encode()
        assertEquals("RUNNING · 3:12 LEFT", line(ToyId.SAND, Settings(sandTimer = run), now = 0))
        val paused = TimerState(phase = Phase.PAUSED, durationMs = 5 * m, leftMs = 192_000).encode()
        assertEquals("PAUSED · 3:12 LEFT", line(ToyId.SAND, Settings(sandTimer = paused)))
        assertEquals("TIME'S UP", line(ToyId.SAND, Settings(sandTimer = TimerState(phase = Phase.DONE).encode())))
    }

    @Test
    fun badgeShowsTheLiveText() {
        assertEquals("IN A MEETING", line(ToyId.BADGE, Settings(badgeActiveSince = 1)))
    }
}
