package app.backlit.ui.toys

import app.backlit.data.Settings
import app.backlit.ui.home.ToyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ToyActionTest {
    @Test
    fun notSetUpAlwaysAsksToTurnOn() {
        for (id in ToyId.entries) assertEquals(ToyAction.TurnOn, ToyAction.of(id, setUp = false, supported = true, hasDrawing = true))
    }

    @Test
    fun clockAndMusicOpenGlyphToysOthersShow() {
        assertEquals(ToyAction.OpenGlyphToys, ToyAction.of(ToyId.CLOCK, true, true, false))
        assertEquals(ToyAction.OpenGlyphToys, ToyAction.of(ToyId.MUSIC, true, true, false))
        for (id in listOf(ToyId.CHARGE, ToyId.PET, ToyId.SAND, ToyId.BADGE)) assertTrue(ToyAction.of(id, true, true, false) is ToyAction.ShowOnGlyph)
        assertTrue(ToyAction.of(ToyId.CANVAS, true, true, hasDrawing = true) is ToyAction.ShowOnGlyph)
        assertEquals(ToyAction.NewDrawing, ToyAction.of(ToyId.CANVAS, true, true, hasDrawing = false))
    }

    @Test
    fun unsupportedPhonesHaveNoBarExceptNewDrawing() {
        for (id in ToyId.entries.filter { it != ToyId.CANVAS }) assertNull(ToyAction.of(id, false, supported = false, hasDrawing = false))
        assertEquals(ToyAction.NewDrawing, ToyAction.of(ToyId.CANVAS, false, supported = false, hasDrawing = false))
        assertNull(ToyAction.of(ToyId.CANVAS, false, supported = false, hasDrawing = true))
    }

    @Test
    fun previewIdsMatchTheExistingOnes() {
        val z = ZoneOffset.UTC
        assertEquals("pet:happy", ToyAction.previewFor(ToyId.PET, Settings(), 0, z).previewId)
        assertEquals("pet:frog:happy", ToyAction.previewFor(ToyId.PET, Settings(petKind = "frog"), 0, z).previewId)
        assertEquals("sand:running", ToyAction.previewFor(ToyId.SAND, Settings(), 0, z).previewId)
        assertEquals("charge:moon:plug_in", ToyAction.previewFor(ToyId.CHARGE, Settings(), 0, z).previewId)
        assertEquals("badge:laptop:IN A MEETING", ToyAction.previewFor(ToyId.BADGE, Settings(badgeActiveSince = 1), 0, z).previewId)
        assertNull(ToyAction.previewFor(ToyId.CANVAS, Settings(), 0, z).previewId)
    }
}
