package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import app.backlit.render.PixelGrid

/** Style E: one column per LED, mirrored up and down from the centre row. */
class MirrorBars(private val size: Int) : VizStyle {
    override val id = "mirror"
    private val cols = ColumnLevels(size)

    override fun update(frame: AudioFrame, dtMs: Long) = cols.update(frame)

    override fun render(): PixelGrid {
        val g = PixelGrid(size)
        val c = (size - 1) / 2
        for (x in 0 until size) {
            g.plot(x, c, CENTRE)
            for (k in 1..cols.height(x)) {
                g.plot(x, c - k, BAR)
                g.plot(x, c + k, BAR)
            }
        }
        return g
    }

    private companion object {
        const val CENTRE = 140
        const val BAR = 255
    }
}
