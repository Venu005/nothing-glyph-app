package app.backlit.badge

import app.backlit.render.PixelFont5x7
import app.backlit.render.PixelGrid
import app.backlit.studio.PixelFontText

/** Badge text: 5×7 capitals on 25×25 (digits from PixelFont5x7), the Studio's 3×5 font on 13×13. 1 px gaps. */
object BadgeFont {
    private val DIGIT = List(7) { "00000" }   // width placeholder; digits are drawn by PixelFont5x7

    private val F7: Map<Char, List<String>> = mapOf(
        'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        'B' to listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
        'C' to listOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
        'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
        'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
        'F' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
        'G' to listOf("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
        'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
        'I' to listOf("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
        'J' to listOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
        'K' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
        'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
        'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
        'N' to listOf("10001", "10001", "11001", "10101", "10011", "10001", "10001"),
        'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
        'Q' to listOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
        'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
        'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
        'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
        'V' to listOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
        'W' to listOf("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
        'X' to listOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
        'Y' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
        'Z' to listOf("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
        ':' to listOf("0", "1", "1", "0", "1", "1", "0"),
        '!' to listOf("1", "1", "1", "1", "1", "0", "1"),
        '?' to listOf("01110", "10001", "00001", "00010", "00100", "00000", "00100"),
        '.' to listOf("0", "0", "0", "0", "0", "0", "1"),
        '-' to listOf("000", "000", "000", "111", "000", "000", "000"),
        ' ' to listOf("000", "000", "000", "000", "000", "000", "000"),
        '\'' to listOf("1", "1", "0", "0", "0", "0", "0"),
    )

    private val EXTRA3: Map<Char, List<String>> = mapOf(
        ' ' to listOf("00", "00", "00", "00", "00"),
        '\'' to listOf("1", "1", "0", "0", "0"),
    )

    fun height(size: Int): Int = if (size >= 25) PixelFont5x7.HEIGHT else PixelFontText.HEIGHT

    private fun glyph(size: Int, c: Char): List<String>? =
        if (size >= 25) (if (c.isDigit()) DIGIT else F7[c]) else EXTRA3[c] ?: PixelFontText.glyph(c)

    fun supports(size: Int, c: Char): Boolean = glyph(size, c) != null

    fun width(size: Int, text: String): Int {
        val gs = text.mapNotNull { glyph(size, it) }
        return if (gs.isEmpty()) 0 else gs.sumOf { it[0].length } + gs.size - 1
    }

    /** Draws [text] with its top-left at (x, y); the grid's size picks the font. Off-panel pixels are dropped. */
    fun draw(g: PixelGrid, text: String, x: Int, y: Int, b: Int) {
        var cx = x
        for (c in text) {
            val rows = glyph(g.size, c) ?: continue
            if (g.size >= 25 && c.isDigit()) PixelFont5x7.digit(g, c - '0', cx, y, b)
            else for (r in rows.indices) for (col in rows[r].indices) if (rows[r][col] == '1') g.plot(cx + col, y + r, b)
            cx += rows[0].length + 1
        }
    }
}
