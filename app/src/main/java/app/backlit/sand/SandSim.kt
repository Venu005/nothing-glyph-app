package app.backlit.sand

import java.util.Random
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min

/**
 * Falling sand in the hourglass. Grains try the neighbour moves that point along gravity (dot > 0.38), best first,
 * furthest-along grains first; they cross bulbs only through the neck gate, which lets one grain in per budget unit.
 */
class SandSim(val shape: HourglassShape, private val random: Random = Random()) {
    private val n = shape.size
    val grid = BooleanArray(n * n)
    val moved = BooleanArray(n * n)
    /** Grains per ms the neck lets through. */
    var gateRate = 0.0
    private var budget = 0.0

    fun load(grains: BooleanArray) {
        grains.copyInto(grid)
        moved.fill(false)
        budget = 0.0
    }

    fun count(): Int = grid.count { it }

    /** Grains resting in bulb [s] (the gate cell is never counted). */
    fun countOn(s: Int): Int = grid.indices.count { grid[it] && shape.kind[it] == Cell.IN && shape.side[it] == s }

    /** One step. (gx, gy) is the unit gravity direction in matrix coordinates (+y is down the matrix). */
    fun step(gx: Double, gy: Double, dtMs: Double) {
        moved.fill(false)
        budget = min(2.0, budget + gateRate * dtMs)
        val grains = grid.indices.filter { grid[it] }.sortedByDescending { (it % n) * gx + (it / n) * gy }
        val moves = DIRS
            .map { (dx, dy) -> Move(dx, dy, (dx * gx + dy * gy) / hypot(dx.toDouble(), dy.toDouble()) + random.nextDouble() * 0.02) }
            .filter { it.dot > 0.38 }
            .sortedByDescending { it.dot }
        for (i in grains) {
            val x = i % n
            val y = i / n
            for (m in moves) {
                val nx = x + m.dx
                val ny = y + m.dy
                if (nx !in 0 until n || ny !in 0 until n) continue
                val j = ny * n + nx
                if (!shape.isOpen(j) || grid[j]) continue
                if (shape.side[i] * shape.side[j] == -1) continue
                if (m.dx != 0 && m.dy != 0 && !shape.isOpen(y * n + nx) && !shape.isOpen(ny * n + x)) continue
                if (shape.kind[j] == Cell.GATE) {
                    if (budget < 1) continue
                    budget -= 1
                }
                grid[i] = false
                grid[j] = true
                moved[j] = true
                break
            }
        }
    }

    private data class Move(val dx: Int, val dy: Int, val dot: Double)

    companion object {
        private val DIRS = (-1..1).flatMap { dy -> (-1..1).map { dx -> dx to dy } }.filter { it != 0 to 0 }

        /**
         * The neck rate that keeps the sand on the clock: [countUp] grains rest in the up bulb and
         * ceil(total × left / duration) should. More → base + extra / 1000; fewer → 0 until the clock catches up.
         */
        fun gateRateFor(total: Int, durationMs: Long, countUp: Int, leftMs: Long): Double {
            val base = total.toDouble() / durationMs
            val should = ceil(total * leftMs.toDouble() / durationMs - 1e-9).toInt()
            val gap = countUp - should
            return when {
                gap > 0 -> base + gap / 1000.0
                gap < 0 -> 0.0
                else -> base
            }
        }
    }
}
