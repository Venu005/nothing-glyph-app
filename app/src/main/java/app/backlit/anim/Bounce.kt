package app.backlit.anim

import app.backlit.render.PixelGrid
import kotlin.math.floor
import kotlin.math.min

object Bounce : GlyphAnimation {
    override val id = "builtin:bounce"
    override val name = "Bounce"
    override val loopMs = 2200L

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val p = phase(tMs, loopMs)
        val tt = p * 3
        val k = floor(tt).toInt()
        val f = tt - k
        val heights = if (big) doubleArrayOf(9.0, 5.5, 2.5) else doubleArrayOf(5.0, 3.0, 1.5)
        val h = heights[min(k, 2)]
        val ground = if (big) 21 else 11
        val y = ground - (if (big) 2 else 1) - h * 4 * f * (1 - f)
        val x = (if (big) 5.0 else 2.0) + p * (if (big) 15 else 8)
        val squash = if (f < 0.08 || f > 0.92) 1 else 0
        for (gx in 0 until size) g.plot(gx, ground, 77)
        if (big) {
            for (dx in (-1 - squash)..(1 + squash)) for (dy in (-1 + squash)..1) g.plotD(x + dx, y + dy, 255)
        } else {
            for (dx in 0..(1 + squash)) for (dy in squash..1) g.plotD(x + dx - 0.5, y + dy - 0.5, 255)
        }
        return g
    }
}
