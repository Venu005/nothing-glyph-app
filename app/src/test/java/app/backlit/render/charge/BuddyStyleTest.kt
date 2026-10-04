package app.backlit.render.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuddyStyleTest {

    @Test
    fun outlineIsRoundAndSymmetric() {
        for (level in listOf(0, 62, 100)) {
            val g = BuddyStyle.still(25, level)
            for (y in 0 until 25) for (x in 0 until 25) assertEquals("25 x=$x y=$y", g[x, y], g[24 - x, y])
            val s = BuddyStyle.still(13, level)
            for (y in 0 until 13) for (x in 0 until 13) assertEquals("13 x=$x y=$y", s[x, y], s[12 - x, y])
        }
        val g = BuddyStyle.still(25, 0)
        for ((x, y) in listOf(12 to 5, 12 to 21, 4 to 13, 20 to 13)) assertTrue("cardinal $x,$y", g[x, y] >= 200)
        val s = BuddyStyle.still(13, 0)                  // hand-placed 9×9: top row x 4..8 at y 3
        for (x in 4..8) assertTrue(s[x, 3] >= 200)
        assertEquals(0, s[3, 3]); assertTrue(s[3, 4] >= 200); assertTrue(s[2, 5] >= 200)
    }

    @Test
    fun fillFollowsLevel() {
        val empty = BuddyStyle.still(25, 0).raw().count { it in 80..100 }     // 0.35 → 89
        val full = BuddyStyle.still(25, 100).raw().count { it in 80..100 }
        val half = BuddyStyle.still(25, 50).raw().count { it in 80..100 }
        assertEquals(0, empty)
        assertTrue(half in (full / 3)..(full * 2 / 3))
    }

    @Test
    fun faceIsBright() {
        val g = BuddyStyle.still(25, 100)
        for ((x, y) in listOf(8 to 11, 9 to 12, 15 to 11, 16 to 12, 11 to 15, 12 to 16, 13 to 15)) assertEquals(255, g[x, y])
        val s = BuddyStyle.still(13, 100)
        for ((x, y) in listOf(4 to 6, 8 to 6, 5 to 8, 6 to 9, 7 to 8)) assertEquals(255, s[x, y])
    }

    @Test
    fun chargingBlinksAndDoneIsHappy() {
        val open = BuddyStyle.charging(25, 62, 1000)
        val blink = BuddyStyle.charging(25, 62, 2600 + 50)
        assertEquals(255, open[8, 11]); assertTrue(blink[8, 11] < 255)
        val happy = BuddyStyle.done(25, 1000)           // after the hop: ^ eyes at (7,12),(8,11),(9,11),(10,12)
        assertEquals(255, happy[7, 12]); assertEquals(255, happy[8, 11])
    }
}
