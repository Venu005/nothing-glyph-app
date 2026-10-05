package app.backlit.pet

import app.backlit.anim.GlyphAnimation
import app.backlit.render.PixelGrid

/** A pet pose as a GlyphAnimation, for "Show on Glyph" via the Alerts preview path. Ids: pet:<base|reaction>. */
class PetPreviewAnimation(private val base: Base?, private val reaction: Reaction?) : GlyphAnimation {
    override val id: String = "pet:" + (reaction?.name ?: base?.name ?: Base.CONTENT.name).lowercase()
    override val name: String = "Pet"
    override val loopMs: Long = reaction?.ms ?: 3000L

    override fun frame(size: Int, tMs: Long): PixelGrid =
        GhostArt.frame(size, Pose(base ?: Base.CONTENT, reaction, 0, 0, 0, 0, 62), tMs)

    companion object {
        fun idFor(base: Base) = "pet:" + base.name.lowercase()

        fun parse(id: String): PetPreviewAnimation? {
            if (!id.startsWith("pet:")) return null
            val key = id.removePrefix("pet:")
            Reaction.entries.firstOrNull { it.name.lowercase() == key }?.let { return PetPreviewAnimation(null, it) }
            Base.entries.firstOrNull { it.name.lowercase() == key }?.let { return PetPreviewAnimation(it, null) }
            return null
        }
    }
}
