package app.backlit.studio

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/** Pixel geometry for the editor. Pure; cells are (x, y), frames are row-major size×size. */
object Raster {
    fun hasLed(size: Int, x: Int, y: Int): Boolean {
        if (x !in 0 until size || y !in 0 until size) return false
        val c = (size - 1) / 2.0
        return hypot(x - c, y - c) <= size / 2.0
    }

    /** Samples every 0.5 px, rounded half up: endpoints included, 8-connected, no duplicates. */
    fun line(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
        val steps = max(1, ceil(hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()) * 2).toInt())
        val out = ArrayList<Pair<Int, Int>>()
        for (k in 0..steps) {
            val f = k.toDouble() / steps
            val p = round(x0 + (x1 - x0) * f) to round(y0 + (y1 - y0) * f)
            if (out.isEmpty() || out.last() != p) out += p
        }
        return out
    }

    /** Midpoint (Bresenham) circle outline; r <= 0 is the centre cell. */
    fun circle(cx: Int, cy: Int, r: Int): List<Pair<Int, Int>> {
        if (r <= 0) return listOf(cx to cy)
        val out = LinkedHashSet<Pair<Int, Int>>()
        var x = r
        var y = 0
        var err = 1 - r
        while (x >= y) {
            for ((dx, dy) in listOf(x to y, y to x, -y to x, -x to y, -x to -y, -y to -x, y to -x, x to -y)) out += (cx + dx) to (cy + dy)
            y++
            if (err < 0) err += 2 * y + 1 else { x--; err += 2 * (y - x) + 1 }
        }
        return out.toList()
    }

    /** 4-way connected cells with the seed's shade, on the panel only. Empty if the seed has no LED. */
    fun fillRegion(size: Int, shades: ByteArray, sx: Int, sy: Int): Set<Int> {
        if (!hasLed(size, sx, sy)) return emptySet()
        val target = shades[sy * size + sx]
        val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>().apply { add(sy * size + sx) }
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (!seen.add(i)) continue
            val x = i % size
            val y = i / size
            for ((nx, ny) in listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1)) {
                if (hasLed(size, nx, ny) && shades[ny * size + nx] == target && (ny * size + nx) !in seen) stack.add(ny * size + nx)
            }
        }
        return seen
    }

    private fun round(v: Double): Int = floor(v + 0.5 + 1e-9).toInt()
}
