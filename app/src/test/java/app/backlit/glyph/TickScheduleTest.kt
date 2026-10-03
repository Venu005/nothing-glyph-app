package app.backlit.glyph

import org.junit.Assert.assertEquals
import org.junit.Test

class TickScheduleTest {
    @Test
    fun perSecondLandsJustAfterNextSecond() {
        assertEquals(1020L, TickSchedule.delayToNextTick(5_000L, perSecond = true))
        assertEquals(270L, TickSchedule.delayToNextTick(5_750L, perSecond = true))
    }

    @Test
    fun perMinuteLandsJustAfterNextMinute() {
        assertEquals(60_020L, TickSchedule.delayToNextTick(120_000L, perSecond = false))
        assertEquals(1_020L, TickSchedule.delayToNextTick(179_000L, perSecond = false))
    }
}
