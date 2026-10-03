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
}
