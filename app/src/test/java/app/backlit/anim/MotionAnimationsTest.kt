package app.backlit.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionAnimationsTest {

    @Test
    fun linkDotsAreMirrorSymmetricAndMeetAtTheCentre() {
        for (size in listOf(25, 13)) {
            var t = 0L
            while (t < Link.loopMs) {
                val g = Link.frame(size, t)
                for (y in 0 until size) for (x in 0 until size) assertEquals("size=$size t=$t", g[x, y], g[size - 1 - x, y])
                t += 50
            }
        }
        val met13 = Link.frame(13, 800)          // phase 0.5 → both dots on x = 6
        assertEquals(255, met13[6, 6])
        assertEquals(0, met13[5, 6]); assertEquals(0, met13[7, 6])
        val met25 = Link.frame(25, 800)          // dots centred on 11 and 13, overlapping column 12
        assertEquals(255, met25[12, 12]); assertEquals(255, met25[10, 12]); assertEquals(255, met25[14, 12])
    }

    @Test
    fun linkStartsAtTheEdges() {
        val g = Link.frame(13, 0)
        assertEquals(255, g[1, 6]); assertEquals(255, g[11, 6])
    }

    @Test
    fun burstFlashesTheCentreFirst() {
        val big = Burst.frame(25, 0)
        for (x in 11..13) for (y in 11..13) assertEquals(255, big[x, y])
        assertEquals(255, Burst.frame(13, 0)[6, 6])
        assertEquals(0, Burst.frame(13, 500)[6, 6])
    }

    @Test
    fun bounceHasAGroundLineAndABall() {
        for ((size, ground) in listOf(25 to 21, 13 to 11)) {
            val g = Bounce.frame(size, 300)
            assertEquals(77, g[size / 2, ground])
            assertTrue("ball above ground", (0 until ground).any { y -> (0 until size).any { x -> g[x, y] == 255 } })
        }
    }

    @Test
    fun everyFrameIsNonEmpty() {
        for (a in listOf(Burst, Link, Bounce)) for (size in listOf(25, 13)) {
            var t = 0L
            while (t < a.loopMs) { assertTrue("${a.id} $size $t", a.frame(size, t).litCount() > 0); t += 37 }
        }
    }

    @Test
    fun registry() {
        assertEquals(
            listOf("builtin:heart", "builtin:smiley", "builtin:ring", "builtin:burst", "builtin:link", "builtin:bounce"),
            BuiltInAnimations.all.map { it.id },
        )
        assertEquals(Link, BuiltInAnimations.byId(BuiltInAnimations.DEFAULT_DEVICE))
        assertEquals(Heartbeat, BuiltInAnimations.byId(BuiltInAnimations.DEFAULT_CONTACT))
        assertNull(BuiltInAnimations.byId("import:nope"))
    }
}
