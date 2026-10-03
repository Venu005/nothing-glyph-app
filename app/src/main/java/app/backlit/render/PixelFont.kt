package app.backlit.render

/** 3×5 digits, shared by the 25×25 and 13×13 faces. */
object PixelFont {
    const val WIDTH = 3
    const val HEIGHT = 5

    private val DIGITS = arrayOf(
        arrayOf("111", "101", "101", "101", "111"),
        arrayOf("010", "110", "010", "010", "111"),
        arrayOf("111", "001", "111", "100", "111"),
        arrayOf("111", "001", "111", "001", "111"),
        arrayOf("101", "101", "111", "001", "001"),
        arrayOf("111", "100", "111", "001", "111"),
        arrayOf("111", "100", "111", "101", "111"),
        arrayOf("111", "001", "001", "001", "001"),
        arrayOf("111", "101", "111", "101", "111"),
        arrayOf("111", "101", "111", "001", "111"),
    )

    fun digit(g: PixelGrid, d: Int, x: Int, y: Int, b: Int) {
        val rows = DIGITS[d.coerceIn(0, 9)]
        for (r in 0 until HEIGHT) for (c in 0 until WIDTH) {
            if (rows[r][c] == '1') g.plot(x + c, y + r, b)
        }
    }

    /** Digits are 3 wide + 1 gap; ':' is 1 wide (dots on rows 1 and 3) + 1 gap. */
    fun text(g: PixelGrid, s: String, x: Int, y: Int, b: Int) {
        var cx = x
        for (ch in s) {
            when {
                ch.isDigit() -> { digit(g, ch - '0', cx, y, b); cx += WIDTH + 1 }
                ch == ':' -> { g.plot(cx, y + 1, b); g.plot(cx, y + 3, b); cx += 2 }
                else -> cx += WIDTH + 1
            }
        }
    }
}
