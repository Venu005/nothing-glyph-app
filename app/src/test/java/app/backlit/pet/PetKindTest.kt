package app.backlit.pet

import org.junit.Assert.assertEquals
import org.junit.Test

class PetKindTest {
    @Test
    fun idsDefaultsAndFallback() {
        assertEquals(listOf("ghost", "frog", "penguin", "axolotl", "owl", "robot"), PetKind.entries.map { it.id })
        assertEquals(listOf("Boo", "Ribbit", "Waddles", "Lotl", "Hoot", "Bolt"), PetKind.entries.map { it.defaultName })
        assertEquals(PetKind.OWL, PetKind.byId("owl"))
        assertEquals(PetKind.GHOST, PetKind.byId("cat"))
        assertEquals(PetKind.GHOST, PetKind.byId(""))
    }
}
