package app.backlit.sand

import app.backlit.anim.GlyphAnimation
import app.backlit.render.PixelGrid

/** The hourglass as a GlyphAnimation: "Show on Glyph", the away-from-toy done moment, and picker previews. */
class SandPreviewAnimation private constructor(private val view: String) : GlyphAnimation {
    override val id: String = "sand:$view"
    override val name: String = "Sand"
    override val loopMs: Long = if (view == "done") TimerState.FLIP_ME_MS + 700 else 6_000L

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val shape = HourglassShape.forSize(size)
        val t = ((tMs % loopMs) + loopMs) % loopMs
        return when (view) {
            "ready" -> SandArt.frame(shape, SandLayout.layout(shape, 0.0, false), null, TimerState(), t)
            "done" -> SandArt.flipMe(shape, 1, t)
            else -> {
                val grains = SandLayout.layout(shape, 1.0 - t.toDouble() / loopMs, true)
                val bright = if ((t / 150) % 2 == 0L) SandLayout.streamCells(shape, 1).toSet() else emptySet()
                SandArt.picture(shape, grains, null, 0.16, bright)
            }
        }
    }

    companion object {
        const val READY_ID = "sand:ready"
        const val RUNNING_ID = "sand:running"
        const val DONE_ID = "sand:done"

        fun parse(id: String): SandPreviewAnimation? = when (id) {
            READY_ID -> SandPreviewAnimation("ready")
            RUNNING_ID -> SandPreviewAnimation("running")
            DONE_ID -> SandPreviewAnimation("done")
            else -> null
        }
    }
}
