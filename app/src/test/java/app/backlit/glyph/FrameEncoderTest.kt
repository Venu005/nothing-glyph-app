package app.backlit.glyph

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameEncoderTest {

    private fun gridWith(vararg values: Pair<Int, Int>): PixelGrid =
        PixelGrid(13).apply { values.forEachIndexed { i, (_, v) -> plot(3 + i, 6, v) } }

    @Test
    fun zeroStaysOffAndFullIsSdkMax() {
        val g = PixelGrid(13).apply { plot(6, 6, 255) }
        val out = FrameEncoder.encode(g, brightnessPercent = 100, aod = false)
        assertEquals(169, out.size)
        assertEquals(2047, out[6 * 13 + 6])
        assertEquals(0, out[0])
        assertEquals(0, out[6 * 13 + 5])
    }

    @Test
    fun nonZeroNeverBelowMinLit() {
        val g = PixelGrid(13).apply { plot(6, 6, 1) }
        for (pct in listOf(10, 20, 50, 100)) for (aod in listOf(true, false)) {
            val v = FrameEncoder.encode(g, pct, aod)[6 * 13 + 6]
            assertTrue("pct=$pct aod=$aod v=$v", v >= FrameEncoder.minLit)
        }
    }

    @Test
    fun aodIsDimmerThanActive() {
        val g = PixelGrid(13).apply { plot(6, 6, 200) }
        val active = FrameEncoder.encode(g, 80, aod = false)[6 * 13 + 6]
        val aod = FrameEncoder.encode(g, 80, aod = true)[6 * 13 + 6]
        assertTrue(aod < active)
    }

    @Test
    fun monotonicInDesignBrightness() {
        val g = gridWith(0 to 20, 0 to 90, 0 to 170, 0 to 255)
        val out = FrameEncoder.encode(g, 80, aod = false)
        val row = (3..6).map { out[6 * 13 + it] }
        assertEquals(row.sorted(), row)
        assertTrue(row.toSet().size == 4)
    }

    @Test
    fun brightnessIsClampedToTenToHundred() {
        val g = PixelGrid(13).apply { plot(6, 6, 255) }
        assertEquals(FrameEncoder.encode(g, 100, false)[84], FrameEncoder.encode(g, 400, false)[84])
        assertEquals(FrameEncoder.encode(g, 10, false)[84], FrameEncoder.encode(g, -5, false)[84])
    }
}
