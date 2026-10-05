package app.backlit.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class RasterTest {

    @Test
    fun maskMatchesRoundPanel() {
        assertTrue(Raster.hasLed(25, 12, 0)); assertFalse(Raster.hasLed(25, 0, 0))
        assertTrue(Raster.hasLed(13, 6, 6)); assertFalse(Raster.hasLed(13, 0, 0)); assertFalse(Raster.hasLed(13, 13, 6))
    }

    @Test
    fun lineHitsEndpointsAndIsEightConnected() {
        val p = Raster.line(2, 3, 20, 11)
        assertEquals(2 to 3, p.first()); assertEquals(20 to 11, p.last())
        p.zipWithNext().forEach { (a, b) -> assertEquals(1, max(abs(a.first - b.first), abs(a.second - b.second))) }
        assertEquals(listOf(5 to 5), Raster.line(5, 5, 5, 5))
    }

    @Test
    fun circleIsGapFreeAndSymmetric() {
        val p = Raster.circle(12, 12, 8).toSet()
        for (c in listOf(12 to 4, 12 to 20, 4 to 12, 20 to 12)) assertTrue("cardinal $c", c in p)
        p.forEach { (x, y) -> assertTrue((24 - x) to y in p); assertTrue(x to (24 - y) in p) }
        assertEquals(setOf(6 to 6), Raster.circle(6, 6, 0).toSet())
    }

    @Test
    fun fillStaysInsideItsRegionAndThePanel() {
        val size = 25
        val shades = ByteArray(size * size)
        for ((x, y) in Raster.circle(12, 12, 5)) shades[y * size + x] = 3     // a closed ring
        val inside = Raster.fillRegion(size, shades, 12, 12)
        assertTrue(inside.isNotEmpty())
        assertTrue(inside.all { val x = it % size; val y = it / size; (x - 12) * (x - 12) + (y - 12) * (y - 12) < 25 })
        val outside = Raster.fillRegion(size, shades, 12, 1)
        assertTrue(outside.all { Raster.hasLed(size, it % size, it / size) })
        assertFalse(outside.contains(12 * size + 12))
        assertEquals(emptySet<Int>(), Raster.fillRegion(size, shades, 0, 0))         // no LED there
    }
}
