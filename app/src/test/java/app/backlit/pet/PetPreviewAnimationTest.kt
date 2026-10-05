package app.backlit.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetPreviewAnimationTest {
    @Test
    fun parsesBasesAndReactions() {
        val h = PetPreviewAnimation.parse("pet:happy")!!
        assertEquals("pet:happy", h.id); assertEquals(3000L, h.loopMs)
        assertEquals(GhostArt.frame(25, Pose(Base.HAPPY, null, 0, 0, 0, 0, 62), 400), h.frame(25, 400))
        val d = PetPreviewAnimation.parse("pet:dizzy")!!
        assertEquals(2000L, d.loopMs)
        assertEquals(GhostArt.frame(13, Pose(Base.CONTENT, Reaction.DIZZY, 0, 0, 0, 0, 62), 300), d.frame(13, 300))
        assertEquals("pet:munch", PetPreviewAnimation.idFor(Base.MUNCH))
        assertNull(PetPreviewAnimation.parse("pet:nope")); assertNull(PetPreviewAnimation.parse("charge:moon:still"))
    }
}
