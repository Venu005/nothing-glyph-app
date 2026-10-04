package app.backlit.render.charge

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The lit phase of the moon is the battery level: a crescent when low, a full moon at 100 %. */
object MoonStyle : ChargeStyle {
    override val id = "moon"
    override val label = "Moon"

    private val STARS = mapOf(
        25 to listOf(3 to 8, 20 to 4, 22 to 15, 5 to 19, 17 to 22, 2 to 13),
        13 to listOf(1 to 4, 11 to 3, 10 to 11),
    )
    private val CRATERS = setOf(14 to 10, 15 to 10, 10 to 15, 16 to 15, 13 to 16)

    override fun still(size: Int, level: Int) = moon(size, level / 100.0, 0, halo = 0.0)

    override fun plugIn(size: Int, level: Int, tMs: Long) =
        ChargeKit.reveal(moon(size, level / 100.0 * ChargeKit.easeInOut(tMs / 3200.0), tMs, 0.0), level, tMs)

    override fun charging(size: Int, level: Int, tMs: Long) = moon(size, level / 100.0, tMs, 0.0)

    override fun done(size: Int, tMs: Long) = moon(size, 1.0, tMs, halo = min(1.0, tMs / 900.0))

    private fun moon(size: Int, f: Double, tMs: Long, halo: Double): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val c = (size - 1) / 2.0
        val r = if (big) 8.6 else 4.6
        for (y in 0 until size) for (x in 0 until size) {
            val dx = x - c
            val dy = y - c
            if (hypot(dx, dy) > r) continue
            val w = sqrt(maxOf(0.0, r * r - dy * dy))
            val lit = dx > w * (1 - 2 * f)
            val crater = big && (x to y) in CRATERS
            g.dot(x.toDouble(), y.toDouble(), if (lit) (if (crater) 0.5 else 0.8) else 0.07)
        }
        STARS.getValue(if (big) 25 else 13).forEachIndexed { i, (sx, sy) ->
            g.dot(sx.toDouble(), sy.toDouble(), 0.15 + 0.35 * (0.5 + 0.5 * sin(tMs / 900.0 + i * 1.7)))
        }
        if (halo > 0) {
            val hr = r + 1.3 + 0.4 * sin(tMs / 500.0)
            for (y in 0 until size) for (x in 0 until size) {
                val d = abs(hypot(x - c, y - c) - hr)
                if (d < 0.7) g.dot(x.toDouble(), y.toDouble(), halo * (1 - d / 0.7) * 0.6)
            }
        }
        return g
    }
}
