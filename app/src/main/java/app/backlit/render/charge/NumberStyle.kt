package app.backlit.render.charge

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** A large clean %, a softly pulsing bolt and one quiet dot circling the rim (25×25). */
object NumberStyle : ChargeStyle {
    override val id = "number"
    override val label = "Big number"

    private val BOLT = listOf("001", "010", "111", "010", "100")
    private const val TRAIL = 8

    override fun still(size: Int, level: Int): PixelGrid = PixelGrid(size).also { digits(it, level.toString(), 1.0) }

    override fun plugIn(size: Int, level: Int, tMs: Long): PixelGrid =
        frame(size, (level * ChargeKit.easeOut(tMs / 1800.0)).roundToInt(), tMs)

    override fun charging(size: Int, level: Int, tMs: Long): PixelGrid = frame(size, level, tMs)

    override fun done(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        if (tMs < 1300) {
            val on = (tMs / 220) % 2 == 0L
            if (big) {
                ChargeKit.number(g, "100", 12.0, 13.0, big = true, v = if (on) 1.0 else 0.25)
                bolt(g, 1.0)
            } else {
                ChargeKit.rimRing(size).forEach { (x, y) -> g.dot(x.toDouble(), y.toDouble(), if (on) 1.0 else 0.3) }
            }
        } else {
            ChargeKit.rimRing(size).forEach { (x, y) -> g.dot(x.toDouble(), y.toDouble(), 0.35) }
            ChargeKit.polyline(g, ChargeKit.CHECK.getValue(if (big) 25 else 13), ChargeKit.easeOut((tMs - 1300) / 500.0), 1.0, thick = big)
        }
        return g
    }

    private fun frame(size: Int, shown: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        digits(g, shown.toString(), 1.0)
        val pulse = 0.35 + 0.65 * (0.5 + 0.5 * sin(tMs / 420.0))
        if (size >= 25) {
            bolt(g, pulse)
            rimDot(g, tMs)
        } else {
            g.dot(6.0, 10.0, pulse)
        }
        return g
    }

    private fun digits(g: PixelGrid, s: String, v: Double) {
        if (g.size >= 25) ChargeKit.number(g, s, 12.0, 13.0, big = true, v = v)
        else ChargeKit.number(g, s, 6.0, 6.0, big = false, v = v, gap = if (s.length < 3) 2 else 1)
    }

    private fun bolt(g: PixelGrid, v: Double) {
        BOLT.forEachIndexed { r, row -> row.forEachIndexed { k, ch -> if (ch == '1') g.dot(11.0 + k, 3.0 + r, v) } }
    }

    private fun rimDot(g: PixelGrid, tMs: Long) {
        val ring = ChargeKit.rimRing(g.size)
        val pos = (tMs / 75.0) % ring.size
        for (k in 0 until TRAIL) {
            val idx = floor(pos - k + ring.size).toInt() % ring.size
            val (x, y) = ring[idx]
            g.dot(x.toDouble(), y.toDouble(), if (k == 0) 1.0 else 0.75 * (1 - k / TRAIL.toDouble()))
        }
    }
}
