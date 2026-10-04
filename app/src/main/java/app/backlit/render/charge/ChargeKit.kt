package app.backlit.render.charge

import app.backlit.render.PixelFont
import app.backlit.render.PixelFont5x7
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Shared drawing helpers, ported from the approved mockup (docs/superpowers/mockups/2026-10-04-charge-styles.html). */
object ChargeKit {
    const val REVEAL_AT_MS = 3800L

    /** Check-mark polylines per matrix size. */
    val CHECK: Map<Int, List<Pair<Double, Double>>> = mapOf(
        25 to listOf(6.0 to 13.0, 10.0 to 17.0, 19.0 to 7.0),
        13 to listOf(3.0 to 6.0, 5.0 to 8.0, 9.0 to 4.0),
    )

    fun easeOut(t: Double): Double = 1 - (1 - t.coerceIn(0.0, 1.0)).pow(3)

    fun easeInOut(t: Double): Double {
        val c = t.coerceIn(0.0, 1.0)
        return if (c < 0.5) 2 * c * c else 1 - (-2 * c + 2).pow(2) / 2
    }

    /** Mockup brightness 0..1 → design brightness 0..255. */
    fun b(v: Double): Int = (v.coerceIn(0.0, 1.0) * 255).roundToInt()

    /** Mockup g.p: round the position, lighten. */
    fun PixelGrid.dot(x: Double, y: Double, v: Double) {
        if (v > 0) plot(x.px(), y.px(), b(v))
    }

    /** Mockup g.s: round the position, overwrite. */
    fun PixelGrid.set(x: Double, y: Double, v: Double) = put(x.px(), y.px(), b(v))

    fun line(g: PixelGrid, x0: Double, y0: Double, x1: Double, y1: Double, v: Double) {
        val steps = max(1, ceil(hypot(x1 - x0, y1 - y0) * 2).toInt())
        for (k in 0..steps) {
            val f = k.toDouble() / steps
            g.dot(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, v)
        }
    }

    /** Draws the first [frac] (0..1) of the polyline's length; thick adds a copy one row lower. */
    fun polyline(g: PixelGrid, pts: List<Pair<Double, Double>>, frac: Double, v: Double, thick: Boolean) {
        val lens = (1 until pts.size).map { hypot(pts[it].first - pts[it - 1].first, pts[it].second - pts[it - 1].second) }
        var left = frac.coerceIn(0.0, 1.0) * lens.sum()
        for (i in 1 until pts.size) {
            if (left <= 0) break
            val q = min(1.0, left / lens[i - 1])
            val (ax, ay) = pts[i - 1]
            val (bx, by) = pts[i]
            line(g, ax, ay, ax + (bx - ax) * q, ay + (by - ay) * q, v)
            if (thick) line(g, ax, ay + 1, ax + (bx - ax) * q, ay + 1 + (by - ay) * q, v)
            left -= lens[i - 1]
        }
    }

    /** Digits centred on (cx, cy): 5×7 when [big], else 3×5. */
    fun number(g: PixelGrid, text: String, cx: Double, cy: Double, big: Boolean, v: Double, gap: Int = 1) {
        val cw = if (big) PixelFont5x7.WIDTH else PixelFont.WIDTH
        val ch = if (big) PixelFont5x7.HEIGHT else PixelFont.HEIGHT
        val w = text.length * (cw + gap) - gap
        val x0 = (cx - (w - 1) / 2.0).px()
        val y0 = (cy - (ch - 1) / 2.0).px()
        text.forEachIndexed { i, c ->
            val d = c - '0'
            if (big) PixelFont5x7.digit(g, d, x0 + i * (cw + gap), y0, b(v))
            else PixelFont.digit(g, d, x0 + i * (cw + gap), y0, b(v))
        }
    }

    /** End of plug-in: dim the scene to 20 % over 250 ms, then show the % in a cleared window. */
    fun reveal(g: PixelGrid, level: Int, tMs: Long): PixelGrid {
        if (tMs < REVEAL_AT_MS) return g
        val n = g.size
        val k = max(0.2, 1 - (tMs - REVEAL_AT_MS) / 250.0)
        val raw = g.raw()
        for (i in raw.indices) if (raw[i] > 0) g.put(i % n, i / n, (raw[i] * k).roundToInt())
        val s = level.coerceIn(0, 100).toString()
        val w = s.length * 4 - 1
        val c = (n - 1) / 2.0
        val x0 = (c - (w - 1) / 2.0).px()
        val y0 = (c - 2).px()
        for (y in y0 - 1..y0 + 5) for (x in x0 - 1..x0 + w) g.put(x, y, 0)
        PixelFont.text(g, s, x0, y0, 255)
        return g
    }

    /** Midpoint (Bresenham) circle around any integer centre; may contain duplicates at octant joins. */
    fun circlePoints(cx: Int, cy: Int, r: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var x = r
        var y = 0
        var err = 1 - r
        while (x >= y) {
            for ((dx, dy) in listOf(x to y, y to x, -y to x, -x to y, -x to -y, -y to -x, y to -x, x to -y)) out += (cx + dx) to (cy + dy)
            y++
            if (err < 0) err += 2 * y + 1 else { x--; err += 2 * (y - x) + 1 }
        }
        return out
    }

    private val rims = HashMap<Int, List<Pair<Int, Int>>>()

    /** Ordered rim ring (r = 11 on 25×25, r = 5 on 13×13) starting at the top, clockwise, no duplicates. */
    fun rimRing(size: Int): List<Pair<Int, Int>> = rims.getOrPut(size) {
        val c = (size - 1) / 2
        val r = if (size >= 25) 11 else 5
        circlePoints(c, c, r).distinct().sortedBy { (x, y) ->
            val a = atan2((x - c).toDouble(), (c - y).toDouble())
            if (a < 0) a + 2 * PI else a
        }
    }
}
