package app.backlit.glyph

import org.junit.Assert.assertEquals
import org.junit.Test

class FramePacerTest {

    @Test
    fun workTimeIsSubtractedFromTheWait() {
        val p = FramePacer(periodMs = 50)
        assertEquals(50, p.delayBeforeNext(nowMs = 1_000))   // first frame due at 1050
        assertEquals(30, p.delayBeforeNext(nowMs = 1_070))   // 20 ms of work after waking at 1050 → due 1100
        assertEquals(50, p.delayBeforeNext(nowMs = 1_100))   // no work → due 1150
    }

    @Test
    fun runningLateMeansNoWaitButNoBurstEither() {
        val p = FramePacer(periodMs = 50)
        p.delayBeforeNext(nowMs = 0)                          // due 50
        assertEquals(20, p.delayBeforeNext(nowMs = 80))       // 30 ms of work fits the budget → due 100
        assertEquals(0, p.delayBeforeNext(nowMs = 170))       // woke at 100, 70 ms of work overran due 150 → run now
        assertEquals(30, p.delayBeforeNext(nowMs = 170))      // back on the 50 ms grid: due 200
        assertEquals(0, p.delayBeforeNext(nowMs = 400))       // far behind → run now and resync…
        assertEquals(50, p.delayBeforeNext(nowMs = 400))      // …next due 450, no catch-up burst
    }
}
