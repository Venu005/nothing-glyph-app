package app.backlit.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelFontTextTest {

    @Test
    fun cleansAndMeasures() {
        assertEquals("HI!", PixelFontText.clean("hi!"))
        assertEquals("ABCDEF", PixelFontText.clean("abcdefgh"))
        assertEquals("AB", PixelFontText.clean("a~b"))
        assertEquals(7, PixelFontText.width("HI"))
        assertEquals(5, PixelFontText.width("♥"))
        assertEquals(9, PixelFontText.width("A♥"))
        assertEquals(0, PixelFontText.width(""))
    }

    @Test
    fun pointsAreCentred() {
        val p = PixelFontText.points("HI", 25)               // w = 7, h = 5 → x0 = 9, y0 = 10
        assertTrue(9 to 10 in p)                              // H top-left
        assertTrue(15 to 14 in p)                             // I bottom-right
        assertTrue(p.all { (x, y) -> x in 9..15 && y in 10..14 })
        val s = PixelFontText.points("1", 13)                 // w = 3 → x0 = 5, y0 = 4; '1' row 0 is "010"
        assertTrue(6 to 4 in s)
    }
    @Test
    fun typingKeepsCaseSoTheKeyboardDoesNotFight() {
        assertEquals("venu", PixelFontText.cleanTyping("venu"))
        assertEquals("Vm!", PixelFontText.cleanTyping("Vm!#"))
        assertEquals("abcdef", PixelFontText.cleanTyping("abcdefgh"))
        assertEquals("VENU", PixelFontText.clean(PixelFontText.cleanTyping("venu")))
    }
}
