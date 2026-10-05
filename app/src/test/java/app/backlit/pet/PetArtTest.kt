package app.backlit.pet

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetArtTest {
    private val rigPets = PetKind.entries - PetKind.GHOST
    private fun pose(b: Base = Base.CONTENT, r: Reaction? = null, level: Int = 62) = Pose(b, r, 0L, 0, 0, 0, level)

    private fun inMask(g: PixelGrid) {
        for (y in 0 until g.size) for (x in 0 until g.size) if (g[x, y] > 0) assertTrue("x=$x y=$y", g.hasLed(x, y))
    }

    @Test
    fun everyPetEveryStateRendersInsideTheMask() {
        for (kind in PetKind.entries) for (size in listOf(25, 13)) {
            for (b in Base.entries) for (t in 0L..6000L step 300) PetArt.frame(kind, size, pose(b), t).also { inMask(it); assertTrue("$kind $b", it.litCount() > 0) }
            for (r in Reaction.entries) {
                val frames = (0L..r.ms step 100).map { PetArt.frame(kind, size, pose(r = r), it) }
                frames.forEach { inMask(it) }
                assertTrue("$kind $r $size lit at some point", frames.any { it.litCount() > 0 })
            }
            for (b in Base.entries) for (m in 0..3) PetArt.still(kind, size, pose(b), m).also { inMask(it); assertTrue(it.litCount() > 0) }
        }
    }

    @Test
    fun ghostDelegatesUnchanged() {
        for (size in listOf(25, 13)) for (t in listOf(0L, 700L, 2199L, 5000L)) {
            for (b in Base.entries) assertEquals(GhostArt.frame(size, pose(b), t), PetArt.frame(PetKind.GHOST, size, pose(b), t))
            for (r in Reaction.entries) assertEquals(GhostArt.frame(size, pose(r = r), t), PetArt.frame(PetKind.GHOST, size, pose(r = r), t))
            for (b in Base.entries) assertEquals(GhostArt.still(size, pose(b), 1), PetArt.still(PetKind.GHOST, size, pose(b), 1))
        }
    }

    @Test
    fun aodFillGrowsForEveryPet() {
        for (kind in rigPets) {
            fun lit(level: Int) = PetArt.still(kind, 25, pose(Base.MUNCH, level = level), 0).raw().count { it in 100..115 }   // 0.42 → 107
            assertTrue("$kind", lit(30) < lit(62) && lit(62) < lit(95))
        }
    }

    @Test
    fun idleStillIsSymmetricForOwlAndRobot() {
        for (kind in listOf(PetKind.OWL, PetKind.ROBOT)) {
            val g = PetArt.still(kind, 25, pose(Base.CONTENT), 0)
            for (y in 0 until 25) for (x in 0 until 25) assertEquals("$kind x=$x y=$y", g[x, y], g[24 - x, y])
        }
    }

    @Test
    fun petsLookDifferent() {
        val frames = PetKind.entries.map { PetArt.still(it, 25, pose(Base.CONTENT), 0) }
        assertEquals(frames.size, frames.toSet().size)
    }
}
