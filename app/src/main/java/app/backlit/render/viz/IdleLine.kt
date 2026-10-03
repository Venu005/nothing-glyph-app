package app.backlit.render.viz

import app.backlit.render.PixelGrid
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Breathing centre row: 60–140 over 4 s when idle, 80–180 over 2 s in fallback. */
class IdleLine(private val size: Int) {
    private var t = 0L
    private var fallback = false

    fun update(dtMs: Long, fallback: Boolean) {
        t += dtMs
        this.fallback = fallback
    }

    fun brightness(): Int {
        val lo = if (fallback) 80 else 60
        val hi = if (fallback) 180 else 140
        val period = if (fallback) 2_000L else 4_000L
        val s = sin(2 * PI * (t % period) / period)
        return (lo + (hi - lo) * (0.5 + 0.5 * s)).roundToInt()
    }

    fun render(): PixelGrid {
        val g = PixelGrid(size)
        val c = (size - 1) / 2
        val b = brightness()
        for (x in 0 until size) g.plot(x, c, b)
        return g
    }
}
