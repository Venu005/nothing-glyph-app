package app.backlit.render

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelFontTest {

    private fun rows(g: PixelGrid, x: Int, y: Int): List<String> =
        (0 until 5).map { r -> (0 until 3).joinToString("") { c -> if (g[x + c, y + r] > 0) "1" else "0" } }

    @Test
    fun everyDigitMatchesItsBitmap() {
        val expected = mapOf(
            0 to listOf("111", "101", "101", "101", "111"),
            1 to listOf("010", "110", "010", "010", "111"),
            2 to listOf("111", "001", "111", "100", "111"),
            3 to listOf("111", "001", "111", "001", "111"),
            4 to listOf("101", "101", "111", "001", "001"),
            5 to listOf("111", "100", "111", "001", "111"),
            6 to listOf("111", "100", "111", "101", "111"),
            7 to listOf("111", "001", "001", "001", "001"),
            8 to listOf("111", "101", "111", "101", "111"),
            9 to listOf("111", "101", "111", "001", "111"),
        )
        for ((d, bitmap) in expected) {
            val g = PixelGrid(25)
            PixelFont.digit(g, d, 10, 10, 200)
            assertEquals("digit $d", bitmap, rows(g, 10, 10))
        }
    }

    @Test
    fun textLaysOutDigitsAndColon() {
        val g = PixelGrid(25)
        PixelFont.text(g, "15:07", 4, 10, 230)
        assertEquals(230, g[5, 10])   // '1' top middle
        assertEquals(230, g[8, 10])   // '5' top left
        assertEquals(230, g[12, 11])  // colon upper dot
        assertEquals(230, g[12, 13])  // colon lower dot
        assertEquals(0, g[12, 12])
        assertEquals(230, g[14, 10])  // '0' top left
        assertEquals(230, g[20, 10])  // '7' top right
        assertEquals(0, g[21, 10])
    }
}
