package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import app.backlit.render.PixelGrid
import kotlin.math.max
import kotlin.math.roundToInt

/** Style F: mirror bars with peak dots that hold 250 ms, then fall one pixel per 200 ms. */
class MirrorPeaks(private val size: Int) : VizStyle {
    override val id = "peaks"
    private val cols = ColumnLevels(size)
    private val peak = IntArray(size)
    private val timer = LongArray(size)

    override fun update(frame: AudioFrame, dtMs: Long) {
        cols.update(frame)
        for (x in 0 until size) {
            val h = cols.height(x)
            if (h >= peak[x]) {
                peak[x] = h
                timer[x] = 0
            } else {
                timer[x] += dtMs
                if (timer[x] >= HOLD_MS) {
                    peak[x] = max(h, peak[x] - 1)
                    timer[x] = HOLD_MS - FALL_MS
                }
            }
        }
    }

    internal fun peakHeight(x: Int): Int = peak[x]

    override fun render(): PixelGrid {
        val g = PixelGrid(size)
        val c = (size - 1) / 2
        for (x in 0 until size) {
            val h = cols.height(x)
            g.plot(x, c, BAR_LOW)
            for (k in 1..h) {
                val b = (BAR_LOW + (BAR_HIGH - BAR_LOW) * k / h.toDouble()).roundToInt()
                g.plot(x, c - k, b)
                g.plot(x, c + k, b)
            }
            if (peak[x] > 0) {
                g.plot(x, c - peak[x] - 1, PEAK)
                g.plot(x, c + peak[x] + 1, PEAK)
            }
        }
        return g
    }

    private companion object {
        const val HOLD_MS = 250L
        const val FALL_MS = 200L
        const val BAR_LOW = 90
        const val BAR_HIGH = 200
        const val PEAK = 255
    }
}
