package app.backlit.sand

import app.backlit.render.px
import kotlin.math.abs

/** Sand at rest, laid out deterministically (mockup `layout`): stills, rebuilds after a gap, and the refill. */
object SandLayout {

    /**
     * [fracUp] of the load rests in the bulb that is up ([upSide]) and the rest piles up in the other. [fill] scales the
     * whole load (the refill). [stream] adds the neck dot and one falling dot when both bulbs hold sand.
     * Built upright, then mirrored for upSide = −1 (the glass is symmetric top to bottom).
     */
    fun layout(shape: HourglassShape, fracUp: Double, stream: Boolean, upSide: Int = 1, fill: Double = 1.0): BooleanArray {
        val n = shape.size
        val c = shape.center
        val load = (shape.total * fill.coerceIn(0.0, 1.0)).px()
        val top = (shape.total * fracUp.coerceIn(0.0, 1.0)).px().coerceAtMost(load)
        val g = BooleanArray(n * n)
        // Equal scores: keep each left/right mirror pair together (then left first), so the pile stays balanced.
        fun order(score: (Int) -> Double) = compareBy<Int>({ score(it) }, { abs(it % n - c) }, { it / n }, { it % n > c })
        val upper = shape.cellsOn(1).sortedWith(order { i -> (c - i / n) - abs(i % n - c) * 0.35 })
        val lower = shape.cellsOn(-1).sortedWith(order { i -> (n - 1 - i / n) + abs(i % n - c) * 0.75 })
        for (k in 0 until top) g[upper[k]] = true
        for (k in 0 until load - top) g[lower[k]] = true
        if (stream && top in 1 until load) for (i in streamCells(shape, 1)) g[i] = true
        if (upSide >= 0) return g
        val m = BooleanArray(n * n)
        for (y in 0 until n) for (x in 0 until n) m[(n - 1 - y) * n + x] = g[y * n + x]
        return m
    }

    /** The neck gate and the dot two rows below it (towards the lower bulb). */
    fun streamCells(shape: HourglassShape, upSide: Int): IntArray {
        val n = shape.size
        val c = shape.center
        val below = if (upSide >= 0) c + 2 else c - 2
        return intArrayOf(shape.gate, below * n + c)
    }
}
