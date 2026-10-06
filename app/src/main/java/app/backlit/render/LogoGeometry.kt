package app.backlit.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

data class LogoDot(val x: Double, val y: Double, val r: Double, val red: Boolean, val alpha: Double)

/**
 * The Backlit logo (C3 "Diamond ring", mockup 2026-10-06-logo-eclipse): a ring of dots lit from the upper right that
 * bursts into a sparkle with a red dot. Coordinates in the 108×108 adaptive-icon viewport.
 */
object LogoGeometry {
    const val CENTER = 54.0
    const val L = -PI / 4

    val dots: List<LogoDot> by lazy {
        val out = ArrayList<LogoDot>()
        for (i in 0 until 30) {
            val a = i / 30.0 * 2 * PI
            out += LogoDot(CENTER + cos(a) * 27, CENTER + sin(a) * 27, 2.3, false, 0.12 + 0.88 * max(0.0, cos(a - L)).pow(3))
        }
        val dx = CENTER + cos(L) * 27
        val dy = CENTER + sin(L) * 27
        for ((ox, oy) in listOf(0.0 to -6.0, 6.0 to 0.0, -4.0 to 4.0, 4.0 to -4.0)) out += LogoDot(dx + ox, dy + oy, 1.5, false, 0.7)
        out += LogoDot(dx, dy, 4.0, true, 1.0)
        out
    }
}
