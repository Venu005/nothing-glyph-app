package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.roundToInt

/** Style G: loudness history scrolling right → left, one sample every 70 ms, fading with age. */
class ScrollWave(private val size: Int) : VizStyle {
    override val id = "wave"
    private val samples = ArrayDeque<Float>()   // newest first
    private var acc = 0L

    override fun update(frame: AudioFrame, dtMs: Long) {
        acc += dtMs
        while (acc >= STEP_MS) {
            acc -= STEP_MS
            samples.addFirst(frame.level)
            if (samples.size > size) samples.removeLast()
        }
    }

    internal fun samplesNewestFirst(): List<Float> = samples.toList()

    override fun render(): PixelGrid {
        val g = PixelGrid(size)
        val c = (size - 1) / 2
        samples.forEachIndexed { i, s ->
            val x = size - 1 - i
            val b = (NEWEST - (NEWEST - OLDEST) * i / (size - 1).toDouble()).roundToInt()
            val h = (s * (size / 2.0 - 0.5)).px()
            g.plot(x, c, b)
            for (k in 1..h) {
                g.plot(x, c - k, b)
                g.plot(x, c + k, b)
            }
        }
        return g
    }

    private companion object {
        const val STEP_MS = 70L
        const val NEWEST = 255
        const val OLDEST = 90
    }
}
