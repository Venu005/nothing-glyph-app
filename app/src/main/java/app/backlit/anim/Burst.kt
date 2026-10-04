package app.backlit.anim

import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object Burst : GlyphAnimation {
    override val id = "builtin:burst"
    override val name = "Burst"
    override val loopMs = 1100L

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val c = g.center.px()
        val p = phase(tMs, loopMs)
        val reach = if (big) 12.0 else 6.0
        val rays = if (big) 16 else 8
        val trail = if (big) 4 else 2
        for (k in 0 until rays) {
            val a = k * 2 * PI / rays
            for (tr in 0 until trail) {
                val r = p * reach - tr * 0.8
                if (r > 0) g.plotD(c + cos(a) * r, c + sin(a) * r, ((1 - p) * (1 - tr / 4.0) * 255).roundToInt())
            }
        }
        if (p < 0.15) {
            val s = if (big) 1 else 0
            for (dx in -s..s) for (dy in -s..s) g.plot(c + dx, c + dy, 255)
        }
        return g
    }
}
