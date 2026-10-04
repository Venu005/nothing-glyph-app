package app.backlit.anim

import app.backlit.render.PixelGrid

object SmileyWink : GlyphAnimation {
    override val id = "builtin:smiley"
    override val name = "Smiley wink"
    override val loopMs = 2400L

    private class Face(
        val r: Int, val eyeL: List<Pair<Int, Int>>, val eyeR: List<Pair<Int, Int>>, val wink: List<Pair<Int, Int>>,
        val smile: List<Pair<Int, Int>>, val wide: List<Pair<Int, Int>>,
    )

    private val F25 = Face(
        r = 8,
        eyeL = listOf(9 to 9, 10 to 9, 9 to 10, 10 to 10),
        eyeR = listOf(14 to 9, 15 to 9, 14 to 10, 15 to 10),
        wink = listOf(13 to 10, 14 to 10, 15 to 10, 16 to 10),
        smile = listOf(9 to 14, 10 to 15, 11 to 15, 12 to 15, 13 to 15, 14 to 15, 15 to 14),
        wide = listOf(8 to 13, 9 to 14, 10 to 15, 11 to 15, 12 to 15, 13 to 15, 14 to 15, 15 to 14, 16 to 13),
    )
    private val F13 = Face(
        r = 5,
        eyeL = listOf(4 to 4), eyeR = listOf(8 to 4), wink = listOf(7 to 5, 8 to 5, 9 to 5),
        smile = listOf(4 to 7, 5 to 8, 6 to 8, 7 to 8, 8 to 7),
        wide = listOf(3 to 6, 4 to 7, 5 to 8, 6 to 8, 7 to 8, 8 to 7, 9 to 6),
    )

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val f = if (size >= 25) F25 else F13
        val p = phase(tMs, loopMs)
        val winking = p >= 0.55 && p < 0.80
        g.circle(f.r, 140)
        Bitmaps.points(g, f.eyeL, 255)
        Bitmaps.points(g, if (winking) f.wink else f.eyeR, 255)
        Bitmaps.points(g, if (winking) f.wide else f.smile, 255)
        return g
    }
}
