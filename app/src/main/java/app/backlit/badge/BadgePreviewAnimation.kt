package app.backlit.badge

import app.backlit.anim.GlyphAnimation
import app.backlit.render.PixelGrid

/** A badge as a GlyphAnimation: "Show on Glyph" and the Glyph Toys picker image. Id: badge:<icon>:<text>. */
class BadgePreviewAnimation private constructor(private val icon: String, private val text: String) : GlyphAnimation {
    override val id: String = idFor(icon, text)
    override val name: String = "Badge"
    override val loopMs: Long = (BadgeFont.width(25, text) + 25 + 4) * 55L

    override fun frame(size: Int, tMs: Long): PixelGrid =
        BadgeArt.frame(size, BadgeMessage(text, icon), text, tMs, BadgeArt.FLASH_MS)

    companion object {
        fun idFor(icon: String, text: String) = "badge:$icon:$text"

        fun parse(id: String): BadgePreviewAnimation? {
            if (!id.startsWith("badge:")) return null
            val parts = id.split(':', limit = 3)
            if (parts.size < 3 || parts[1] !in BadgeIcons.ids) return null
            return BadgePreviewAnimation(parts[1], BadgeMessage.cleanText(parts[2]).trim())
        }
    }
}
