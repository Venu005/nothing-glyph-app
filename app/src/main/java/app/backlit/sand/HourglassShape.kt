package app.backlit.sand

import kotlin.math.abs
import kotlin.math.hypot

enum class Cell { OUT, IN, WALL, GATE }

/**
 * The classic hourglass glass (mockup style A) at 25×25 or 13×13. Bulb side: +1 top, −1 bottom, 0 the centre row.
 * The neck gate is the centre cell; walls are LED cells next to the glass.
 */
class HourglassShape private constructor(val size: Int) {
    val center: Int = (size - 1) / 2
    val kind: Array<Cell> = Array(size * size) { Cell.OUT }
    val side: IntArray = IntArray(size * size)
    val gate: Int = center * size + center
    val total: Int

    init {
        val hw = if (size >= 25) intArrayOf(-1, 0, 1, 2, 3, 4, 5, 6, 6, 6, 5) else intArrayOf(-1, 0, 1, 2, 3, 3)
        for (y in 0 until size) for (x in 0 until size) {
            if (!hasLed(x, y)) continue
            val i = y * size + x
            val dy = abs(y - center)
            if (dy < hw.size) {
                if (dy == 0) { if (x == center) kind[i] = Cell.GATE }
                else if (abs(x - center) <= hw[dy]) kind[i] = Cell.IN
            }
            side[i] = if (y < center) 1 else if (y > center) -1 else 0
        }
        for (y in 0 until size) for (x in 0 until size) {
            val i = y * size + x
            if (kind[i] != Cell.OUT || !hasLed(x, y)) continue
            var near = false
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until size && ny in 0 until size) {
                    val k = kind[ny * size + nx]
                    if (k == Cell.IN || k == Cell.GATE) near = true
                }
            }
            if (near) kind[i] = Cell.WALL
        }
        total = (0.72 * minOf(cellsOn(1).size, cellsOn(-1).size)).toInt()
    }

    fun hasLed(x: Int, y: Int): Boolean =
        x in 0 until size && y in 0 until size && hypot(x - (size - 1) / 2.0, y - (size - 1) / 2.0) <= size / 2.0

    fun isOpen(i: Int): Boolean = kind[i] == Cell.IN || kind[i] == Cell.GATE

    fun cellsOn(s: Int): List<Int> = kind.indices.filter { kind[it] == Cell.IN && side[it] == s }

    companion object {
        private val BIG by lazy { HourglassShape(25) }
        private val SMALL by lazy { HourglassShape(13) }
        fun forSize(n: Int): HourglassShape = if (n >= 25) BIG else SMALL
    }
}
