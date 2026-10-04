package app.backlit.anim

import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object Ringing : GlyphAnimation {
    override val id = "builtin:ring"
    override val name = "Ringing"
    override val loopMs = 1200L

    private val PHONE_13 = listOf(5 to 4, 6 to 4, 7 to 4, 5 to 5, 7 to 5, 5 to 6, 7 to 6, 5 to 7, 7 to 7, 5 to 8, 6 to 8, 7 to 8)
    private val INNER_13 = listOf(3 to 5, 3 to 6, 3 to 7, 9 to 5, 9 to 6, 9 to 7)
    private val OUTER_13 = listOf(2 to 4, 1 to 5, 1 to 6, 1 to 7, 2 to 8, 10 to 4, 11 to 5, 11 to 6, 11 to 7, 10 to 8)

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val p = phase(tMs, loopMs)
        if (size < 25) {
            val shake = if (p < 0.33) (if ((tMs / 60) % 2 == 1L) 1 else -1) else 0
            Bitmaps.points(g, PHONE_13, 255, dx = shake)
            if (p >= 0.33) Bitmaps.points(g, INNER_13, if (p < 0.66) 255 else 115)
            if (p >= 0.66) Bitmaps.points(g, OUTER_13, 255)
            return g
        }
        val c = g.center.px()
        val shake = if (p < 0.5) sin(tMs / 40.0).roundToInt() else 0
        for (y in -4..4) for (x in -2..2) if (y == -4 || y == 4 || x == -2 || x == 2) g.plot(c + x + shake, c + y, 255)
        for (k in 0 until 3) {
            val r = 4 + ((p + k / 3.0) % 1.0) * 8
            val b = ((1 - (r - 4) / 9) * 255).roundToInt()
            var a = -0.6
            while (a <= 0.6) {
                g.plotD(c + cos(a) * r, c + sin(a) * r, b)
                g.plotD(c - cos(a) * r, c + sin(a) * r, b)
                a += 0.06
            }
        }
        return g
    }
}
