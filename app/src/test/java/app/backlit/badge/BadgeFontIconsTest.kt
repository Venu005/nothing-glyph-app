package app.backlit.badge

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeFontIconsTest {
    @Test
    fun everyAllowedCharacterHasAGlyphAtBothSizes() {
        for (c in BadgeMessage.ALLOWED) {
            assertTrue("25 '$c'", BadgeFont.supports(25, c))
            assertTrue("13 '$c'", BadgeFont.supports(13, c))
        }
    }

    @Test
    fun widthsAndHeights() {
        assertEquals(11, BadgeFont.width(25, "AB"))
        assertEquals(7, BadgeFont.width(13, "AB"))
        assertEquals(7, BadgeFont.height(25))
        assertEquals(5, BadgeFont.height(13))
        assertEquals(0, BadgeFont.width(25, ""))
    }

    @Test
    fun drawPutsTheLettersWhereAsked() {
        val g = PixelGrid(25)
        BadgeFont.draw(g, "T", 10, 14, 255)
        for (x in 10..14) assertEquals(255, g[x, 14])                 // the T's top bar
        assertEquals(255, g[12, 20])
        val h = PixelGrid(13)
        BadgeFont.draw(h, "T", 5, 7, 255)
        for (x in 5..7) assertEquals(255, h[x, 7])
    }

    @Test
    fun iconsFitTheirBoxesAndTheMask() {
        for (id in BadgeIcons.ids) for ((size, y) in listOf(25 to 3, 13 to 1)) {
            val big = size >= 25
            val rows = BadgeIcons.rows(id, big)
            val box = BadgeIcons.box(size)
            assertTrue("$id rows", rows.size <= box)
            assertTrue("$id width", rows.all { it.length == box })
            val g = PixelGrid(size)
            BadgeIcons.draw(g, id, (size - box) / 2, y, 255)
            assertEquals("$id at $size fits the mask", rows.sumOf { r -> r.count { it == 'X' } }, g.litCount())
        }
    }
}
