package app.backlit.anim

import app.backlit.render.PixelGrid
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object Link : GlyphAnimation {
    override val id = "builtin:link"
    override val name = "Link"
    override val loopMs = 1600L

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val c = (size - 1) / 2
        val p = phase(tMs, loopMs)
        val e = 1 - (1 - min(1.0, p / 0.5)).pow(3)
        val edge = if (big) 2 else 1
        val meet = if (big) c - 1 else c
        val left = edge + floor(e * (meet - edge) + 1e-9).toInt()
        val right = (size - 1) - left                    // exact mirror: the dots always meet at the centre
        val s = if (big) 1 else 0
        for (dx in -s..s) for (dy in -s..s) {
            g.plot(left + dx, c + dy, 255)
            g.plot(right + dx, c + dy, 255)
        }
        if (p >= 0.5 && p < 0.8) {
            val q = (p - 0.5) / 0.3
            g.ringBand(q * (if (big) 10.0 else 5.0), ((1 - q) * 255).roundToInt(), width = 0.6)
        }
        return g
    }
}
