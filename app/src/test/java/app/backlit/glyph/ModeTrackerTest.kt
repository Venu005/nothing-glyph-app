package app.backlit.glyph

import app.backlit.render.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class ModeTrackerTest {
    @Test
    fun aodOnlyDevicesAreAlwaysAod() {
        assertEquals(Mode.AOD, ModeTracker(aodOnly = true).mode(0L))
    }

    @Test
    fun activeUntilAnAodEventThenBackAfterSeventySeconds() {
        val t = ModeTracker(aodOnly = false)
        assertEquals(Mode.ACTIVE, t.mode(1_000L))
        t.onAodEvent(10_000L)
        assertEquals(Mode.AOD, t.mode(10_001L))
        assertEquals(Mode.AOD, t.mode(79_999L))
        assertEquals(Mode.ACTIVE, t.mode(80_001L))
    }

    @Test
    fun msUntilActiveCountsDownTheAodWindow() {
        val m = ModeTracker(aodOnly = false)
        assertEquals(null, m.msUntilActive(0))         // never in AOD
        m.onAodEvent(1_000)
        assertEquals(70_000L, m.msUntilActive(1_000))
        assertEquals(10_000L, m.msUntilActive(61_000))
        assertEquals(null, m.msUntilActive(71_000))    // already active again
        assertEquals(null, ModeTracker(aodOnly = true).msUntilActive(0))   // (4a) Pro never leaves AOD
    }
}
