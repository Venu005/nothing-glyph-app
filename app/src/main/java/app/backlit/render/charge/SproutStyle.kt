package app.backlit.render.charge

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A little plant grows out of its pot; its height is the battery level. */
object SproutStyle : ChargeStyle {
    override val id = "sprout"
    override val label = "Sprout"

    override fun still(size: Int, level: Int): PixelGrid = PixelGrid(size).also { plant(it, level / 100.0, 0, 0.0, sway = false) }

    override fun plugIn(size: Int, level: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        plant(g, level / 100.0 * ChargeKit.easeInOut(tMs / 3000.0), tMs, 0.0, sway = true)
        return ChargeKit.reveal(g, level, tMs)
    }

    override fun charging(size: Int, level: Int, tMs: Long): PixelGrid =
        ChargeKit.periodicReveal(PixelGrid(size).also { plant(it, level / 100.0, tMs, 0.0, sway = true) }, level, tMs)

    override fun done(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val (topX, topY) = plant(g, 1.0, tMs, ChargeKit.easeInOut(tMs / 1200.0), sway = true)
        if (tMs > 1200) {
            val n = if (big) 6 else 3
            for (i in 0 until n) {
                val ph = (tMs / 900.0 + i.toDouble() / n) % 1.0
                val a = i * 2.4
                val r = (if (big) 4.0 else 2.0) + ph * (if (big) 6.0 else 3.0)
                g.dot(topX + cos(a) * r, topY - (if (big) 2 else 1) + sin(a) * r * 0.8 - ph * 2, 1 - ph)
            }
        }
        return g
    }

    /** Draws pot, stem, leaves and bud/flower; returns the stem top (x, y). */
    private fun plant(g: PixelGrid, h: Double, tMs: Long, bloom: Double, sway: Boolean): Pair<Double, Int> {
        val n = g.size
        val big = n >= 25
        if (big) {
            for (x in 7..17) g.dot(x.toDouble(), 19.0, 0.8)
            for (y in 20..22) { val i = y - 20; for (x in 8 + i..16 - i) g.dot(x.toDouble(), y.toDouble(), 0.45) }
        } else {
            for (x in 4..8) g.dot(x.toDouble(), 10.0, 0.8)
            for (x in 5..7) g.dot(x.toDouble(), 11.0, 0.45)
        }
        val base = if (big) 18 else 9
        val maxH = if (big) 12 else 6
        val len = (h * maxH).px()
        val c = (n - 1) / 2.0
        var topX = c
        for (k in 0 until len) {
            val y = base - k
            val off = if (sway && k > len * 0.6 && len > 3) (sin(tMs / 900.0) * 0.6 * (k.toDouble() / len)).px() else 0
            val x = c + off
            g.dot(x, y.toDouble(), 0.9)
            topX = x
            val every = if (big) 3 else 2
            if (k > 0 && k % every == 0 && k < len - 1) {
                val side = if ((k / every) % 2 == 1) 1 else -1
                if (big) {
                    g.dot(x + side, y.toDouble(), 0.75)
                    g.dot(x + 2 * side, y - 1.0, 0.75)
                    g.dot(x + side, y - 1.0, 0.5)
                    if (sway && sin(tMs / 600.0 + k) > 0.6) g.dot(x + 3 * side, y - 1.0, 0.4)
                } else {
                    g.dot(x + side, y - 1.0, 0.75)
                }
            }
        }
        val topY = base - len + 1
        if (bloom > 0) {
            val r = if (big) 2.6 * bloom else 1.4 * bloom
            val petals = if (big) 8 else 4
            val fy = topY - (if (big) 2 else 1).toDouble()
            for (i in 0 until petals) {
                val a = i.toDouble() / petals * 2 * PI + tMs / 2000.0
                g.dot(topX + cos(a) * r, fy + sin(a) * r, 0.85)
            }
            g.dot(topX, fy, 1.0)
        } else if (len > 0) {
            g.dot(topX, topY - 1.0, 0.55)
        }
        return topX to topY
    }
}
