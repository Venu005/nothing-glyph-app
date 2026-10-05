package app.backlit.pet

import app.backlit.anim.GlyphAnimation
import app.backlit.render.PixelGrid

/** A pet pose as a GlyphAnimation for "Show on Glyph". Ids: pet:<state> (ghost, legacy) or pet:<kind>:<state>. */
class PetPreviewAnimation(
    private val base: Base?,
    private val reaction: Reaction?,
    private val kind: PetKind = PetKind.GHOST,
) : GlyphAnimation {
    private val state = (reaction?.name ?: base?.name ?: Base.CONTENT.name).lowercase()
    override val id: String = if (kind == PetKind.GHOST) "pet:$state" else "pet:${kind.id}:$state"
    override val name: String = "Pet"
    override val loopMs: Long = reaction?.ms ?: 3000L

    override fun frame(size: Int, tMs: Long): PixelGrid =
        PetArt.frame(kind, size, Pose(base ?: Base.CONTENT, reaction, 0, 0, 0, 0, 62), tMs)

    companion object {
        fun idFor(base: Base) = "pet:" + base.name.lowercase()
        fun idFor(kind: PetKind, base: Base) = if (kind == PetKind.GHOST) idFor(base) else "pet:${kind.id}:${base.name.lowercase()}"

        fun parse(id: String): PetPreviewAnimation? {
            if (!id.startsWith("pet:")) return null
            val parts = id.removePrefix("pet:").split(':')
            val (kind, key) = when (parts.size) {
                1 -> PetKind.GHOST to parts[0]
                2 -> (PetKind.entries.firstOrNull { it.id == parts[0] } ?: return null) to parts[1]
                else -> return null
            }
            Reaction.entries.firstOrNull { it.name.lowercase() == key }?.let { return PetPreviewAnimation(null, it, kind) }
            Base.entries.firstOrNull { it.name.lowercase() == key }?.let { return PetPreviewAnimation(it, null, kind) }
            return null
        }
    }
}
