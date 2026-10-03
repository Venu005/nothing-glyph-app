package app.backlit.render

import kotlin.math.hypot

/**
 * One frame for a round Glyph Matrix: `size × size` design brightness values (0–255).
 * Positions outside the round LED mask can never be lit.
 */
class PixelGrid(val size: Int) {

    private val px = IntArray(size * size)

    /** Centre coordinate, e.g. 12.0 on 25×25 and 6.0 on 13×13. */
    val center: Double = (size - 1) / 2.0

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size

    /** True when the panel has a physical LED at (x, y). */
    fun hasLed(x: Int, y: Int): Boolean =
        inBounds(x, y) && hypot(x - center, y - center) <= size / 2.0

    operator fun get(x: Int, y: Int): Int = if (inBounds(x, y)) px[y * size + x] else 0

    /** Lighten: keeps the brighter of the current and new value. */
    fun plot(x: Int, y: Int, b: Int) {
        if (!hasLed(x, y)) return
        val i = y * size + x
        val v = b.coerceIn(0, 255)
        if (v > px[i]) px[i] = v
    }

    /** Overwrite, including with 0 (used to carve space, e.g. behind digits). */
    fun put(x: Int, y: Int, b: Int) {
        if (!hasLed(x, y)) return
        px[y * size + x] = b.coerceIn(0, 255)
    }

    /** Row-major copy of all values. */
    fun raw(): IntArray = px.copyOf()

    fun litCount(): Int = px.count { it > 0 }

    /** Debug view: ' ' no LED, '.' off, '-' 1–84, '+' 85–169, '#' 170–255. */
    fun toAscii(): String = buildString {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val v = this@PixelGrid[x, y]
                append(
                    when {
                        !hasLed(x, y) -> ' '
                        v == 0 -> '.'
                        v < 85 -> '-'
                        v < 170 -> '+'
                        else -> '#'
                    }
                )
            }
            if (y < size - 1) append('\n')
        }
    }

    override fun equals(other: Any?): Boolean =
        other is PixelGrid && other.size == size && other.px.contentEquals(px)

    override fun hashCode(): Int = 31 * size + px.contentHashCode()

    override fun toString(): String = "PixelGrid($size)\n${toAscii()}"

    companion object {
        fun ledCount(size: Int): Int {
            val g = PixelGrid(size)
            return (0 until size).sumOf { y -> (0 until size).count { x -> g.hasLed(x, y) } }
        }
    }
}
