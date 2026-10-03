package app.backlit.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelGridTest {

    @Test
    fun ledCountsMatchTheRealPanels() {
        // Phone (4a) Pro allocation diagram has 137 LEDs on the 13×13 grid.
        assertEquals(137, PixelGrid.ledCount(13))
        // Disc of radius 12.5 on a 25×25 grid.
        assertEquals(489, PixelGrid.ledCount(25))
    }

    @Test
    fun cornersHaveNoLedCentreAndEdgesDo() {
        val g = PixelGrid(25)
        assertFalse(g.hasLed(0, 0))
        assertTrue(g.hasLed(12, 0))
        assertTrue(g.hasLed(0, 12))
        assertTrue(g.hasLed(12, 12))
        assertFalse(g.hasLed(-1, 12))
        assertFalse(g.hasLed(25, 12))
    }

    @Test
    fun plotKeepsBrighterValueAndClamps() {
        val g = PixelGrid(13)
        g.plot(6, 6, 100)
        g.plot(6, 6, 50)
        assertEquals(100, g[6, 6])
        g.plot(6, 6, 999)
        assertEquals(255, g[6, 6])
    }

    @Test
    fun putOverwritesIncludingZero() {
        val g = PixelGrid(13)
        g.plot(6, 6, 200)
        g.put(6, 6, 0)
        assertEquals(0, g[6, 6])
    }

    @Test
    fun writesOutsideTheMaskAreIgnored() {
        val g = PixelGrid(13)
        g.plot(0, 0, 255)
        g.put(0, 0, 255)
        g.plot(-3, 40, 255)
        assertEquals(0, g[0, 0])
        assertEquals(0, g.litCount())
    }

    @Test
    fun rawIsRowMajorCopy() {
        val g = PixelGrid(13)
        g.plot(3, 2, 77)
        val raw = g.raw()
        assertEquals(169, raw.size)
        assertEquals(77, raw[2 * 13 + 3])
        raw[2 * 13 + 3] = 0
        assertEquals(77, g[3, 2])
    }

    @Test
    fun valueEquality() {
        val a = PixelGrid(13).apply { plot(6, 6, 10) }
        val b = PixelGrid(13).apply { plot(6, 6, 10) }
        val c = PixelGrid(13).apply { plot(6, 6, 11) }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun asciiUsesBrightnessBands() {
        val g = PixelGrid(13)
        g.plot(6, 6, 255)
        g.plot(5, 6, 100)
        g.plot(4, 6, 20)
        val row6 = g.toAscii().lines()[6]
        assertEquals('#', row6[6])
        assertEquals('+', row6[5])
        assertEquals('-', row6[4])
        assertEquals('.', row6[3])
        assertEquals(' ', g.toAscii().lines()[0][0])
    }
}
