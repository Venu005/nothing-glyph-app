package app.backlit.render

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Round half up. The epsilon keeps 14.4999…98 from JVM trig on the same side as 14.5. */
fun Double.px(): Int = floor(this + 0.5 + 1e-9).toInt()

object Draw {

    /** Xiaolin Wu anti-aliased line; brightness is split between the two nearest pixels. */
    fun wuLine(g: PixelGrid, x0: Double, y0: Double, x1: Double, y1: Double, b: Int) {
        var ax = x0; var ay = y0; var bx = x1; var by = y1
        val steep = abs(by - ay) > abs(bx - ax)
        if (steep) {
            var t = ax; ax = ay; ay = t
            t = bx; bx = by; by = t
        }
        if (ax > bx) {
            var t = ax; ax = bx; bx = t
            t = ay; ay = by; by = t
        }
        val dx = bx - ax
        val gradient = if (dx == 0.0) 0.0 else (by - ay) / dx
        for (x in ax.px()..bx.px()) {
            val y = ay + gradient * (x - ax)
            val yi = floor(y + 1e-9).toInt()
            val frac = (y - yi).coerceAtLeast(0.0)
            plotWeighted(g, steep, x, yi, b * (1 - frac))
            plotWeighted(g, steep, x, yi + 1, b * frac)
        }
    }

    private fun plotWeighted(g: PixelGrid, steep: Boolean, a: Int, c: Int, v: Double) {
        val iv = v.roundToInt()
        if (iv <= 0) return
        if (steep) g.plot(c, a, iv) else g.plot(a, c, iv)
    }

    /** Filled disc centred on the nearest pixel to (cx, cy). */
    fun disc(g: PixelGrid, cx: Double, cy: Double, r: Double, b: Int) {
        val x0 = cx.px(); val y0 = cy.px()
        val limit = r * r + 0.5
        val reach = r.toInt() + 1
        for (dx in -reach..reach) for (dy in -reach..reach) {
            if (dx * dx + dy * dy <= limit) g.plot(x0 + dx, y0 + dy, b)
        }
    }

    /** Small crescent moon: a radius-2 disc with its upper-right bitten out. Never erases. */
    fun crescent(g: PixelGrid, cx: Double, cy: Double, b: Int) {
        val x0 = cx.px(); val y0 = cy.px()
        for (dx in -3..3) for (dy in -3..3) {
            val inMoon = dx * dx + dy * dy <= 4.5
            val inBite = (dx - 1) * (dx - 1) + (dy + 1) * (dy + 1) <= 4.84
            if (inMoon && !inBite) g.plot(x0 + dx, y0 + dy, b)
        }
    }
}
