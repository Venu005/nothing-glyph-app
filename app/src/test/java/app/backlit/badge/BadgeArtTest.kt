package app.backlit.badge

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeArtTest {
    private fun litRows(g: PixelGrid) = (0 until g.size).filter { y -> (0 until g.size).any { x -> g[x, y] > 0 } }.toSet()
    private val long = "ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123"

    @Test
    fun everyIconAndLongTextStayInsideAtBothSizes() {
        for (id in BadgeIcons.ids) for (size in listOf(25, 13)) {
            val m = BadgeMessage(long, id)
            for (t in 0L..20_000L step 700) {
                val g = BadgeArt.frame(size, m, long, t, BadgeArt.FLASH_MS)
                assertTrue("$id $size $t", g.litCount() > 0)
                for (y in 0 until size) for (x in 0 until size) if (g[x, y] > 0) assertTrue(g.hasLed(x, y))
            }
        }
    }

    @Test
    fun at13TheIconAndTextNeverShareARow() {
        for (id in BadgeIcons.ids) {
            val m = BadgeMessage("IN A MEETING", id)
            val icon = litRows(BadgeArt.frame(13, m, "", 0, BadgeArt.FLASH_MS))
            assertTrue("$id icon rows $icon", icon.all { it in 1..5 })
            for (t in 0L..6_000L step 160) {
                val rows = litRows(BadgeArt.frame(13, m, "IN A MEETING", t, BadgeArt.FLASH_MS))
                assertTrue("$id t=$t rows $rows", rows.all { it in 1..5 || it in 7..11 })
            }
        }
        val rows25 = litRows(BadgeArt.frame(25, BadgeMessage("HI", "heart"), "HI", 600, BadgeArt.FLASH_MS))
        assertTrue(rows25.all { it in 3..11 || it in 14..20 })
    }

    @Test
    fun theTickerMoves() {
        val m = BadgeMessage("ON A CALL", "phone")
        assertNotEquals(BadgeArt.frame(25, m, "ON A CALL", 0, 9999).raw().toList(), BadgeArt.frame(25, m, "ON A CALL", 550, 9999).raw().toList())
        assertNotEquals(BadgeArt.frame(13, m, "ON A CALL", 0, 9999).raw().toList(), BadgeArt.frame(13, m, "ON A CALL", 800, 9999).raw().toList())
    }

    @Test
    fun theChangeFlashIsBrighterThanTheSteadyIcon() {
        val m = BadgeMessage("", "heart")
        val flash = BadgeArt.frame(25, m, "", 50, 50)
        val steady = BadgeArt.frame(25, m, "", 50, BadgeArt.FLASH_MS)
        val mid = BadgeArt.frame(25, m, "", 200, 200)
        assertEquals(255, flash[12, 6])
        assertEquals(217, steady[12, 6])
        assertEquals(217, mid[12, 6])
    }

    @Test
    fun stillShowsIconAndShortText() {
        val m = BadgeMessage("BACK IN", "coffee")
        val s = BadgeArt.still(25, m, "4M")
        val rows = litRows(s)
        assertTrue(rows.any { it in 4..12 })
        assertTrue(rows.any { it in 15..21 })
        assertEquals(BadgeArt.still(13, m, "ABC").raw().toList(), BadgeArt.still(13, m, "ABCD").raw().toList())
    }

    @Test
    fun tilesHoldTheWholeBigIcon() {
        for (id in BadgeIcons.ids) {
            assertEquals(id, BadgeIcons.rows(id, true).sumOf { r -> r.count { it == 'X' } }, BadgeArt.tile(id).litCount())
        }
    }

    @Test
    fun previewsParse() {
        val a = BadgePreviewAnimation.parse(BadgePreviewAnimation.idFor("coffee", "BACK IN 4:59"))
        assertNotNull(a)
        assertTrue(a!!.frame(25, 1200).litCount() > 0)
        assertNull(BadgePreviewAnimation.parse("badge:nope:X"))
        assertNull(BadgePreviewAnimation.parse("pet:happy"))
    }

    @Test
    fun alwaysOnWordsAreNeverClipped() {
        val words = BadgeMessage.STARTERS.map { it.text.substringBefore(' ') } +
            listOf("SOON", "11AM", "12PM", "LUNCH", "BREAK", "99M", "3H", "MEETING", "W")
        for (size in listOf(25, 13)) for (w in words) {
            val t = BadgeArt.stillText(size, w)
            val drawn = PixelGrid(size).also { BadgeFont.draw(it, t.text, t.x, t.y, 255, t.small) }.litCount()
            val whole = PixelGrid(61).also { BadgeFont.draw(it, t.text, 20, 20, 255, t.small) }.litCount()
            assertEquals("$size '$w' -> '${t.text}'", whole, drawn)
            assertTrue("$size '$w' keeps a gap under the icon", t.y > (if (size >= 25) 12 else 5))
        }
        assertEquals("THANK", BadgeArt.stillText(25, "THANK").text)    // 5 letters still fit, in the small font
        assertEquals("SOO", BadgeArt.stillText(13, "SOON").text)
    }
}
