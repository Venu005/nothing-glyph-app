package app.backlit.anim

import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.abs
import kotlin.math.hypot

/** A looping matrix animation that can draw itself at 25×25 (Phone (3)) or 13×13 (Phone (4a) Pro). */
interface GlyphAnimation {
    val id: String
    val name: String
    val loopMs: Long
    fun frame(size: Int, tMs: Long): PixelGrid
}

/** Position within the loop, 0 until 1. */
fun phase(tMs: Long, loopMs: Long): Double = (((tMs % loopMs) + loopMs) % loopMs).toDouble() / loopMs

fun PixelGrid.plotD(x: Double, y: Double, b: Int) = plot(x.px(), y.px(), b)

/** Every pixel whose centre lies within [width] of radius [r] — a clean, round outline. */
fun PixelGrid.ringBand(r: Double, b: Int, width: Double = 0.5) {
    for (y in 0 until size) for (x in 0 until size) {
        if (abs(hypot(x - center, y - center) - r) < width) plot(x, y, b)
    }
}

object Bitmaps {
    /** Draws 'X' cells of [rows], centred on the grid. */
    fun draw(g: PixelGrid, rows: List<String>, b: Int) {
        val x0 = (g.size - rows[0].length) / 2
        val y0 = (g.size - rows.size) / 2
        rows.forEachIndexed { r, row -> row.forEachIndexed { i, ch -> if (ch == 'X') g.plot(x0 + i, y0 + r, b) } }
    }

    fun points(g: PixelGrid, pts: List<Pair<Int, Int>>, b: Int, dx: Int = 0) =
        pts.forEach { (x, y) -> g.plot(x + dx, y, b) }
}
