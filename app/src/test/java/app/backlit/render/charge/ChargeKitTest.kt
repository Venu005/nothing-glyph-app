package app.backlit.render.charge

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class ChargeKitTest {

    @Test
    fun brightnessAndEasing() {
        assertEquals(255, ChargeKit.b(1.0))
        assertEquals(0, ChargeKit.b(-0.2))
        assertEquals(89, ChargeKit.b(0.35))
        assertEquals(0.0, ChargeKit.easeOut(0.0), 1e-9)
        assertEquals(1.0, ChargeKit.easeOut(2.0), 1e-9)
        assertEquals(0.5, ChargeKit.easeInOut(0.5), 1e-9)
    }

    @Test
    fun rimRingIsClosedAndEightConnected() {
        for (size in listOf(25, 13)) {
            val p = ChargeKit.rimRing(size)
            assertTrue(p.size > 20)
            assertEquals("no duplicates", p.size, p.toSet().size)
            for (i in p.indices) {
                val (ax, ay) = p[i]
                val (bx, by) = p[(i + 1) % p.size]
                assertEquals("step $i on $size", 1, max(abs(ax - bx), abs(ay - by)))
            }
        }
    }

    @Test
    fun rimRingStartsAtTopAndGoesClockwise() {
        val p = ChargeKit.rimRing(25)
        assertEquals(12 to 1, p.first())           // r = 11 around (12,12)
        assertTrue(p[3].first > 12)                // moving right first
    }

    @Test
    fun revealClearsABoxAndDrawsDigits() {
        val g = PixelGrid(25)
        for (y in 0 until 25) for (x in 0 until 25) g.plot(x, y, 200)
        ChargeKit.reveal(g, 62, 5000)
        // "62": w = 7, x0 = 9, y0 = 10; the box is x 8..16, y 9..15
        assertEquals(0, g[8, 9]); assertEquals(0, g[16, 15])
        assertEquals(255, g[9, 10])                // top-left of '6'
        assertEquals(40, g[3, 12])                 // scene dimmed to 20 % (200 * 0.2)
        val untouched = PixelGrid(25).also { it.plot(5, 5, 200) }
        ChargeKit.reveal(untouched, 62, 3000)
        assertEquals(200, untouched[5, 5])         // before 3800 ms nothing changes
    }

    @Test
    fun bigNumberUses5x7AndGap() {
        val g = PixelGrid(25)
        ChargeKit.number(g, "62", 12.0, 13.0, big = true, v = 1.0)
        // w = 11 → x0 = 7, y0 = 10; '6' row 0 is "00110"
        assertEquals(0, g[7, 10]); assertEquals(255, g[9, 10]); assertEquals(255, g[10, 10])
        val s = PixelGrid(13)
        ChargeKit.number(s, "62", 6.0, 6.0, big = false, v = 1.0, gap = 2)
        // w = 8 → x0 = 3 (6 - 3.5 = 2.5 → px 3), y0 = 4; '6' covers x 3..5, '2' covers x 8..10
        assertEquals(255, s[3, 4]); assertEquals(0, s[6, 4]); assertEquals(0, s[7, 4]); assertEquals(255, s[8, 4])
    }

    @Test
    fun chargingLoopShowsThePercentTwoSecondsEveryTen() {
        for (style in listOf(MoonStyle, SproutStyle, BuddyStyle)) {
            val shown = style.charging(25, 62, 19_000)            // cycle 9000 ms → in the 8000..9999 window
            assertEquals("${style.id} digits", 255, shown[9, 10])  // '6' top-left (x0 = 9, y0 = 10)
            assertEquals("${style.id} box cleared", 0, shown[8, 9])
            val hidden = style.charging(25, 62, 13_000)           // cycle 3000 ms → normal frame
            assertEquals("${style.id} plain", style.charging(25, 62, 13_000), hidden)
            assertTrue("${style.id} no window", hidden[8, 9] > 0 || hidden[9, 10] != 255)
        }
        val small = MoonStyle.charging(13, 62, 9_000)
        assertEquals(255, small[3, 4])                            // 13×13: "62" x0 = 3, y0 = 4
    }
}
