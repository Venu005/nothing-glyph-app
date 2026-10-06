package app.backlit.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPersistenceTest {

    private fun doc(): Drawing {
        var s = EditorState.of(Drawing.blank(13, "Wink ♥")).circle(6, 6, 4, 3, false)
        s = s.addFrame().line(3, 6, 9, 6, 1, true).setHold(2).setFps(14)
        return s.doc
    }

    @Test
    fun drawingSurvivesBytesRoundTrip() {
        val d = doc()
        assertEquals(d, DrawingBytes.decode(DrawingBytes.encode(d)))
        assertEquals(Drawing.blank(25), DrawingBytes.decode(DrawingBytes.encode(Drawing.blank(25))))
        assertNull(DrawingBytes.decode(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun saveAssignsTheIdBeforeTheWriteFinishes() {
        val t = SaveTracker(id = null, saved = null)
        val d = doc()
        val first = t.begin(d) { "import:new" }
        assertEquals("import:new", first); assertEquals("import:new", t.id)
        val second = t.begin(d) { "import:other" }          // a second tap while the first is in flight
        assertEquals("import:new", second)                   // same drawing, no duplicate
    }

    @Test
    fun editsDuringASaveStayDirty() {
        val t = SaveTracker(id = "import:a", saved = null)
        val d = doc()
        t.begin(d) { error("unused") }
        val edited = EditorState.of(d).paint(listOf(6 to 6), 2, false).doc
        t.finished(d)
        assertFalse(t.dirty(d))
        assertTrue(t.dirty(edited))
    }
    @Test
    fun anUntouchedNewDrawingIsNotDirty() {
        val t = SaveTracker(id = null, saved = null)
        val blank = doc()
        t.baseline(blank)
        assertFalse(t.dirty(blank))
        assertTrue(t.dirty(EditorState.of(blank).paint(listOf(6 to 6), 2, false).doc))
    }

    @Test
    fun touchesOutsideTheCanvasAreNotCells() {
        assertEquals(0 to 0, EditorGeometry.cellAt(1f, 1f, 250f, 250f, 25))
        assertEquals(24 to 24, EditorGeometry.cellAt(249f, 249f, 250f, 250f, 25))
        assertEquals(null, EditorGeometry.cellAt(-3f, 100f, 250f, 250f, 25))
        assertEquals(null, EditorGeometry.cellAt(100f, 251f, 250f, 250f, 25))
        assertEquals(24 to 0, EditorGeometry.clampedCellAt(400f, -9f, 250f, 250f, 25))
    }
}
