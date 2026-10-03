package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** A stateful music visualizer drawn on a size×size grid. */
interface VizStyle {
    val id: String
    fun update(frame: AudioFrame, dtMs: Long)
    fun render(): PixelGrid
}

/**
 * Per-column level 0..1: centre column = band 0 (bass), edges = band 7 (treble), mirrored,
 * tapering towards the edges. Rises instantly, falls as old × 0.7 + new × 0.3.
 */
class ColumnLevels(private val size: Int) {
    val levels = FloatArray(size)

    fun update(frame: AudioFrame) {
        val c = (size - 1) / 2.0
        val last = AudioFrame.BANDS - 1
        for (x in 0 until size) {
            val d = if (c == 0.0) 0.0 else abs(x - c) / c
            val f = d * last
            val i = floor(f).toInt().coerceIn(0, last)
            val fr = f - i
            val j = min(last, i + 1)
            val v = ((frame.bands[i] * (1 - fr) + frame.bands[j] * fr) * (1 - 0.45 * d)).toFloat()
            levels[x] = if (v > levels[x]) v else levels[x] * 0.7f + v * 0.3f
        }
    }

    /** Bar half-height in pixels: 0..(size/2 − 0.5), e.g. 0..12 on 25×25. */
    fun height(x: Int): Int = (levels[x] * (size / 2.0 - 0.5)).px()
}
