package app.backlit.badge

import app.backlit.render.PixelGrid
import app.backlit.render.px

/** Style A: icon on top, ticker underneath (mockups 2026-10-06-badge-styles / badge-icons). */
object BadgeArt {
    const val FLASH_MS = 600L
    private const val ICON = 0.85

    private fun b(v: Double): Int = (v * 255).px()

    private fun iconX(size: Int): Int = (size - BadgeIcons.box(size)) / 2

    /** [tMs]: time since the ticker started; [sinceChangeMs]: time since this message became current (the flash). */
    fun frame(size: Int, msg: BadgeMessage, text: String, tMs: Long, sinceChangeMs: Long, spanText: String = text): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val flash = sinceChangeMs in 0 until 150 || sinceChangeMs in 300 until 450
        BadgeIcons.draw(g, msg.icon, iconX(size), if (big) 3 else 1, b(if (flash) 1.0 else ICON))
        if (text.isNotEmpty()) {
            val span = maxOf(BadgeFont.width(size, text), BadgeFont.width(size, spanText)) + size + 4
            val step = if (big) 55L else 80L
            val off = ((tMs.coerceAtLeast(0) / step) % span).toInt()
            BadgeFont.draw(g, text, size - off, if (big) 14 else 7, 255)
        }
        return g
    }

    /** Always-on: the icon and a short word (cut to 3 characters on 13×13). */
    fun still(size: Int, msg: BadgeMessage, short: String): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        BadgeIcons.draw(g, msg.icon, iconX(size), if (big) 4 else 1, b(ICON))
        val t = stillText(size, short)
        BadgeFont.draw(g, t.text, t.x, t.y, 255, t.small)
        return g
    }

    data class StillText(val text: String, val small: Boolean, val x: Int, val y: Int)

    /**
     * Where the always-on word goes so no pixel falls off the round panel. 25×25: up to 17 px in 5×7, otherwise up to
     * 5 characters in 3×5. 13×13: 3 characters in 3×5 on rows 6–10, which are wide enough for all 11 px.
     */
    fun stillText(size: Int, short: String): StillText {
        if (size < 25) {
            val s = short.take(3)
            return StillText(s, true, (size - BadgeFont.width(13, s)) / 2, 6)
        }
        if (BadgeFont.width(25, short) <= 17) return StillText(short, false, (size - BadgeFont.width(25, short)) / 2, 15)
        val s = short.take(5)
        return StillText(s, true, (size - BadgeFont.width(13, s)) / 2, 15)
    }

    /** A UI tile: the 9×9 icon on a 13×13 dot grid (corners stay inside the round mask). */
    fun tile(id: String): PixelGrid = PixelGrid(13).also { BadgeIcons.draw(it, id, 2, 2, 255, big = true) }
}
