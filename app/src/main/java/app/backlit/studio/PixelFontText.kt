package app.backlit.studio

/** 3×5 text glyphs for the studio's TEXT tool (♥ is 5 wide). 1 px gap between characters. */
object PixelFontText {
    const val MAX_CHARS = 6
    const val HEIGHT = 5

    private val GLYPHS: Map<Char, List<String>> = mapOf(
        '0' to listOf("111", "101", "101", "101", "111"), '1' to listOf("010", "110", "010", "010", "111"),
        '2' to listOf("111", "001", "111", "100", "111"), '3' to listOf("111", "001", "111", "001", "111"),
        '4' to listOf("101", "101", "111", "001", "001"), '5' to listOf("111", "100", "111", "001", "111"),
        '6' to listOf("111", "100", "111", "101", "111"), '7' to listOf("111", "001", "001", "001", "001"),
        '8' to listOf("111", "101", "111", "101", "111"), '9' to listOf("111", "101", "111", "001", "111"),
        'A' to listOf("010", "101", "111", "101", "101"), 'B' to listOf("110", "101", "110", "101", "110"),
        'C' to listOf("011", "100", "100", "100", "011"), 'D' to listOf("110", "101", "101", "101", "110"),
        'E' to listOf("111", "100", "110", "100", "111"), 'F' to listOf("111", "100", "110", "100", "100"),
        'G' to listOf("011", "100", "101", "101", "011"), 'H' to listOf("101", "101", "111", "101", "101"),
        'I' to listOf("111", "010", "010", "010", "111"), 'J' to listOf("001", "001", "001", "101", "010"),
        'K' to listOf("101", "101", "110", "101", "101"), 'L' to listOf("100", "100", "100", "100", "111"),
        'M' to listOf("101", "111", "111", "101", "101"), 'N' to listOf("110", "101", "101", "101", "101"),
        'O' to listOf("010", "101", "101", "101", "010"), 'P' to listOf("110", "101", "110", "100", "100"),
        'Q' to listOf("010", "101", "101", "110", "011"), 'R' to listOf("110", "101", "110", "101", "101"),
        'S' to listOf("011", "100", "010", "001", "110"), 'T' to listOf("111", "010", "010", "010", "010"),
        'U' to listOf("101", "101", "101", "101", "111"), 'V' to listOf("101", "101", "101", "101", "010"),
        'W' to listOf("101", "101", "111", "111", "101"), 'X' to listOf("101", "101", "010", "101", "101"),
        'Y' to listOf("101", "101", "010", "010", "010"), 'Z' to listOf("111", "001", "010", "100", "111"),
        '!' to listOf("010", "010", "010", "000", "010"), '?' to listOf("111", "001", "010", "000", "010"),
        '.' to listOf("000", "000", "000", "000", "010"), '-' to listOf("000", "000", "111", "000", "000"),
        ':' to listOf("000", "010", "000", "010", "000"), '+' to listOf("000", "010", "111", "010", "000"),
        '♥' to listOf("01010", "11111", "11111", "01110", "00100"),
    )

    fun clean(text: String): String = text.uppercase().filter { it in GLYPHS }.take(MAX_CHARS)

    /** For the text field while typing: keeps the keyboard's case (changing it mid-word confuses the IME); [clean] uppercases on use. */
    fun cleanTyping(text: String): String = text.filter { it.uppercaseChar() in GLYPHS }.take(MAX_CHARS)

    /** The rows of [c], or null when the font has no such character. */
    fun glyph(c: Char): List<String>? = GLYPHS[c]

    fun width(text: String): Int {
        val g = text.mapNotNull { GLYPHS[it] }
        return if (g.isEmpty()) 0 else g.sumOf { it[0].length } + g.size - 1
    }

    /** Lit cells of [text] centred on a size×size grid. Unknown characters are skipped. */
    fun points(text: String, size: Int): List<Pair<Int, Int>> {
        val glyphs = text.mapNotNull { GLYPHS[it] }
        var x = (size - width(text)) / 2
        val y0 = (size - HEIGHT) / 2
        val out = ArrayList<Pair<Int, Int>>()
        for (g in glyphs) {
            for (r in g.indices) for (c in g[r].indices) if (g[r][c] == '1') out += (x + c) to (y0 + r)
            x += g[0].length + 1
        }
        return out
    }
}
