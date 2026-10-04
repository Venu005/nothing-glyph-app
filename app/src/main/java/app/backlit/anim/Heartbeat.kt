package app.backlit.anim

import app.backlit.render.PixelGrid

object Heartbeat : GlyphAnimation {
    override val id = "builtin:heart"
    override val name = "Heartbeat"
    override val loopMs = 1200L

    private val SMALL_25 = listOf(".XXX...XXX.", "XXXXX.XXXXX", "XXXXXXXXXXX", "XXXXXXXXXXX", ".XXXXXXXXX.", "..XXXXXXX..", "...XXXXX...", "....XXX....", ".....X.....")
    private val BIG_25 = listOf(
        "..XXXX...XXXX..", ".XXXXXX.XXXXXX.", "XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXX",
        ".XXXXXXXXXXXXX.", "..XXXXXXXXXXX..", "...XXXXXXXXX...", "....XXXXXXX....", ".....XXXXX.....", "......XXX......", ".......X.......",
    )
    private val SMALL_13 = listOf(".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X...")
    private val BIG_13 = listOf(".XX...XX.", "XXXX.XXXX", "XXXXXXXXX", "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...", "....X....")

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val p = phase(tMs, loopMs)
        val big = p < 0.12 || (p >= 0.24 && p < 0.36)
        val rows = if (size >= 25) (if (big) BIG_25 else SMALL_25) else (if (big) BIG_13 else SMALL_13)
        Bitmaps.draw(g, rows, if (big) 255 else 153)
        return g
    }
}
