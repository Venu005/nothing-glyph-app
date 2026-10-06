package app.backlit.badge

import app.backlit.render.PixelGrid

/** The 8 badge icons (spec §2 table): 9×9 for 25×25, 5×5 for 13×13. Short icons sit vertically centred in the box. */
object BadgeIcons {
    val ids: List<String> = listOf("laptop", "coffee", "phone", "moon", "heart", "car", "headphones", "food")

    private val BIG: Map<String, List<String>> = mapOf(
        "laptop" to listOf(".XXXXXXX.", ".X.....X.", ".X.....X.", ".X.....X.", ".XXXXXXX.", "XXXXXXXXX"),
        "coffee" to listOf("..X..X...", "...X..X..", ".........", "XXXXXXX..", "XXXXXXXXX", "XXXXXXX.X", "XXXXXXXXX", ".XXXXX..."),
        "phone" to listOf("XXX......", "XXXX.....", "XXX......", ".XX......", ".XX......", ".XXX.....", "..XXX.XXX", "...XXXXXX", ".....XXX."),
        "moon" to listOf("..XXXX...", ".XXX.....", "XXX......", "XXX......", "XXX......", "XXX......", "XXXX...XX", ".XXXXXXX.", "..XXXXX.."),
        "heart" to listOf(".XX...XX.", "XXXX.XXXX", "XXXXXXXXX", "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...", "....X...."),
        "car" to listOf("..XXXXX..", ".X.....X.", "X.......X", "XXXXXXXXX", "X.XXXXX.X", "XXXXXXXXX", "XX.....XX"),
        "headphones" to listOf("..XXXXX..", ".X.....X.", "X.......X", "X.......X", "XX.....XX", "XXX...XXX", "XXX...XXX", ".X.....X."),
        "food" to listOf("X.X.X..X.", "X.X.X.XX.", "X.X.X.XX.", "XXXXX.XX.", ".XXX..XX.", "..X....X.", "..X....X.", "..X....X.", "..X....X."),
    )

    private val SMALL: Map<String, List<String>> = mapOf(
        "laptop" to listOf(".XXX.", ".X.X.", ".XXX.", "XXXXX"),
        "coffee" to listOf(".X.X.", "X.X..", "XXXX.", "XXXXX", ".XX.."),
        "phone" to listOf("XX...", "X....", "X....", "XX.XX", ".XXX."),
        "moon" to listOf(".XX..", "XX...", "X....", "XX..X", ".XXX."),
        "heart" to listOf("XX.XX", "XXXXX", "XXXXX", ".XXX.", "..X.."),
        "car" to listOf(".XXX.", "XXXXX", "X.X.X", "XXXXX", "X...X"),
        "headphones" to listOf(".XXX.", "X...X", "X...X", "XX.XX", "XX.XX"),
        "food" to listOf("X.X.X", "X.X.X", "XXX.X", ".X..X", ".X..X"),
    )

    fun box(size: Int): Int = if (size >= 25) 9 else 5

    fun rows(id: String, big: Boolean): List<String> = (if (big) BIG else SMALL)[id] ?: (if (big) BIG else SMALL).getValue("heart")

    fun draw(g: PixelGrid, id: String, x: Int, y: Int, b: Int, big: Boolean = g.size >= 25) {
        val rows = rows(id, big)
        val dy = ((if (big) 9 else 5) - rows.size) / 2
        for (r in rows.indices) for (c in rows[r].indices) if (rows[r][c] == 'X') g.plot(x + c, y + dy + r, b)
    }
}
