package app.backlit.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawTest {

    @Test
    fun pxRoundsHalfUpAndAbsorbsFloatNoise() {
        assertEquals(3, 2.5.px())
        assertEquals(2, 2.4999.px())
        assertEquals(12, (12.0 + 6e-17).px())
        assertEquals(15, 14.499999999999998.px())
        assertEquals(-1, (-1.2).px())
    }

    @Test
    fun verticalLineIsOnePixelWideAtFullBrightness() {
        val g = PixelGrid(25)
        Draw.wuLine(g, 12.0, 12.0, 12.0, 2.5, 170)
        for (y in 3..12) assertEquals("row $y", 170, g[12, y])
        assertEquals(0, g[12, 2])
        assertEquals(0, g[11, 6])
        assertEquals(0, g[13, 6])
    }

    @Test
    fun horizontalLineIsOnePixelWide() {
        val g = PixelGrid(25)
        Draw.wuLine(g, 12.0, 12.0, 18.0, 12.0, 255)
        for (x in 12..18) assertEquals(255, g[x, 12])
        assertEquals(0, g[12, 11])
        assertEquals(0, g[12, 13])
    }

    @Test
    fun diagonalLineSplitsBrightnessBetweenNeighbours() {
        val g = PixelGrid(25)
        // Slope 0.5: at x = 13 the ideal y is 12.5, shared equally by rows 12 and 13.
        Draw.wuLine(g, 12.0, 12.0, 16.0, 14.0, 200)
        assertEquals(100, g[13, 12])
        assertEquals(100, g[13, 13])
        assertEquals(200, g[14, 13])
    }

    @Test
    fun discOfRadiusOneAndAHalfIsThreeByThree() {
        val g = PixelGrid(25)
        Draw.disc(g, 12.0, 12.0, 1.5, 255)
        for (x in 11..13) for (y in 11..13) assertEquals(255, g[x, y])
        assertEquals(9, g.litCount())
    }

    @Test
    fun crescentIsLitOnTheLeftAndDarkOnTheUpperRight() {
        val g = PixelGrid(25)
        Draw.crescent(g, 12.0, 12.0, 170)
        assertEquals(170, g[10, 12])
        assertEquals(0, g[13, 11])
        assertTrue(g.litCount() in 4..12)
    }
}
