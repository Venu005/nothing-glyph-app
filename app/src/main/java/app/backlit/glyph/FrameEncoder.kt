package app.backlit.glyph

import app.backlit.render.PixelGrid
import kotlin.math.roundToInt

/**
 * Design brightness (0–255) → the SDK's raw frame values (0–2047).
 * LEDs near the bottom of their range read as off, so every lit pixel is lifted to at least [minLit].
 * [minLit] is calibrated on a real Phone (3) in Task 12.
 */
object FrameEncoder {
    const val SDK_MAX = 2047
    const val AOD_FACTOR = 0.6
    var minLit: Int = 300

    fun encode(grid: PixelGrid, brightnessPercent: Int, aod: Boolean): IntArray {
        val scale = brightnessPercent.coerceIn(10, 100) / 100.0 * (if (aod) AOD_FACTOR else 1.0)
        val raw = grid.raw()
        return IntArray(raw.size) { i ->
            val v = raw[i]
            if (v == 0) 0
            else (minLit + (SDK_MAX - minLit) * (v / 255.0) * scale).roundToInt().coerceIn(minLit, SDK_MAX)
        }
    }
}
