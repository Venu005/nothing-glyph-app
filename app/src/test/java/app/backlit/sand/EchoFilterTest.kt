package app.backlit.sand

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoFilterTest {
    @Test
    fun ownWritesEchoOnceThenTheSameValueCountsAsNew() {
        val f = EchoFilter()
        f.record("A"); f.record("B")
        assertEquals(true, f.isEcho("A"))
        assertEquals(true, f.isEcho("B"))
        assertEquals(false, f.isEcho("A"))      // the app writing A again is a real change
    }

    @Test
    fun anEchoConsumesOlderPendingWrites() {
        val f = EchoFilter()
        f.record("A"); f.record("B"); f.record("C")
        assertEquals(true, f.isEcho("B"))       // DataStore may coalesce: A is gone too
        assertEquals(false, f.isEcho("A"))
        assertEquals(true, f.isEcho("C"))
    }

    @Test
    fun foreignValuesAreNotEchoes() {
        val f = EchoFilter()
        f.record("A")
        assertEquals(false, f.isEcho("X"))
        assertEquals(true, f.isEcho("A"))
    }
}
