package app.backlit.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostArtTest {
    private fun pose(base: Base = Base.CONTENT, r: Reaction? = null, level: Int = 50) = Pose(base, r, 0L, 0, 0, 0, level)

    @Test
    fun everyPoseRendersInsideTheMask() {
        for (size in listOf(25, 13)) {
            for (b in Base.entries) for (t in 0L..6000L step 200) check(GhostArt.frame(size, pose(b), t))
            for (r in Reaction.entries) for (t in 0L..r.ms step 100) check(GhostArt.frame(size, pose(r = r), t))
            for (b in Base.entries) for (m in 0..3) check(GhostArt.still(size, pose(b), m))
        }
    }

    private fun check(g: app.backlit.render.PixelGrid) {
        assertTrue(g.litCount() > 0)                                  // peekaboo's first frame is mostly below the panel
        for (y in 0 until g.size) for (x in 0 until g.size) if (g[x, y] > 0) assertTrue(g.hasLed(x, y))
    }

    @Test
    fun idleStillIsSymmetricAboveTheHem() {
        val g = GhostArt.still(25, pose(Base.CONTENT), 0)
        for (y in 0..19) for (x in 0 until 25) assertEquals("x=$x y=$y", g[x, y], g[24 - x, y])
        val s = GhostArt.still(13, pose(Base.CONTENT), 0)
        for (y in 0..9) for (x in 0 until 13) assertEquals("13 x=$x y=$y", s[x, y], s[12 - x, y])
    }

    @Test
    fun chargingStillFillsWithTheLevel() {
        fun dim(level: Int) = GhostArt.still(25, pose(Base.MUNCH, level = level), 0).raw().count { it in 70..80 }   // 0.3 → 77
        assertTrue(dim(30) < dim(62)); assertTrue(dim(62) < dim(95))
    }

    @Test
    fun contentEyesAreOpenAndBodyOutlineIsLit() {
        val g = GhostArt.frame(25, pose(Base.CONTENT), 2199)         // t ≈ 700π: float 0, not blinking (blink is t % 3200 < 160)
        for ((x, y) in listOf(9 to 10, 10 to 12, 14 to 10, 15 to 12)) assertEquals(255, g[x, y])
        assertTrue(g[12, 4] >= 200)                                   // dome top (12, 11−7)
        assertTrue(g[5, 15] >= 200 && g[19, 15] >= 200)               // sides
    }

    @Test
    fun peekabooRisesSlowlyFromTheBottom() {
        val mid = GhostArt.frame(25, pose(r = Reaction.PEEK), 600)     // halfway up: dome top not yet at its resting row
        assertEquals(0, mid[12, 4])
        val up = GhostArt.frame(25, pose(r = Reaction.PEEK), 1300)     // fully up after ~1.2 s
        assertTrue(up[12, 4] >= 200)
    }
}
