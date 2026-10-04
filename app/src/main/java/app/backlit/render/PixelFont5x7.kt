package app.backlit.render

/** 5×7 digits for large numbers on the 25×25 matrix. */
object PixelFont5x7 {
    const val WIDTH = 5
    const val HEIGHT = 7

    private val DIGITS = arrayOf(
        arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        arrayOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
        arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    )

    fun digit(g: PixelGrid, d: Int, x: Int, y: Int, b: Int) {
        val rows = DIGITS[d.coerceIn(0, 9)]
        for (r in 0 until HEIGHT) for (c in 0 until WIDTH) {
            if (rows[r][c] == '1') g.plot(x + c, y + r, b)
        }
    }
}
