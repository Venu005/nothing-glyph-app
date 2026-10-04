package app.backlit.charge

import app.backlit.anim.GlyphAnimation
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeStyle
import app.backlit.render.charge.ChargeStyles

/** A charge style moment as a GlyphAnimation, so "Show on Glyph" can reuse the Alerts preview path. */
class ChargePreviewAnimation(
    private val style: ChargeStyle,
    private val moment: Moment,
    private val level: Int = DEMO_LEVEL,
) : GlyphAnimation {
    override val id: String = idFor(style.id, moment)
    override val name: String = style.label
    override val loopMs: Long = durationMs(moment)

    override fun frame(size: Int, tMs: Long): PixelGrid = when (moment) {
        Moment.STILL -> style.still(size, level)
        Moment.PLUG_IN -> style.plugIn(size, level, tMs)
        Moment.CHARGING -> style.charging(size, level, tMs)
        Moment.DONE -> style.done(size, tMs)
    }

    companion object {
        const val DEMO_LEVEL = 62
        private const val PREFIX = "charge:"

        fun idFor(styleId: String, moment: Moment) = "$PREFIX$styleId:${moment.name.lowercase()}"

        fun parse(id: String): ChargePreviewAnimation? {
            if (!id.startsWith(PREFIX)) return null
            val parts = id.split(':')
            if (parts.size != 3) return null
            val moment = Moment.entries.firstOrNull { it.name.lowercase() == parts[2] } ?: return null
            return ChargePreviewAnimation(ChargeStyles.byId(parts[1]), moment)
        }

        fun durationMs(moment: Moment): Long = when (moment) {
            Moment.PLUG_IN -> ChargeSession.PLUG_IN_MS
            Moment.DONE -> ChargeSession.DONE_MS
            else -> 3000L
        }
    }
}
