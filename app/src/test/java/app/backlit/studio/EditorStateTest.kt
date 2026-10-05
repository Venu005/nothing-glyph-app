package app.backlit.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStateTest {

    private fun blank(size: Int = 25) = EditorState.of(Drawing.blank(size))
    private fun EditorState.at(x: Int, y: Int) = frame[doc.size, x, y]

    @Test
    fun blankDrawingDefaults() {
        val d = Drawing.blank(13)
        assertEquals(13, d.size); assertEquals(8, d.fps); assertEquals(1, d.frames.size); assertEquals(1, d.frames[0].hold)
        assertEquals(169, d.frames[0].shades.size)
    }

    @Test
    fun paintIgnoresOffPanelCellsAndMirrors() {
        val s = blank().paint(listOf(3 to 12, 0 to 0, 1 to 2), shade = 2, mirror = true)
        assertEquals(2, s.at(3, 12)); assertEquals(2, s.at(21, 12))       // mirror of x=3 is 21
        assertEquals(0, s.at(0, 0)); assertEquals(0, s.at(24, 0))         // no LED there, nor at its mirror
        assertEquals(0, s.at(1, 2)); assertEquals(0, s.at(23, 2))
    }

    @Test
    fun strokeFromSameBaseIsOneUndoStep() {
        val base = blank()
        var s = base.paint(listOf(10 to 10), 3, false)
        s = base.paint(Raster.line(10, 10, 16, 10), 3, false)             // the UI recomputes from the pointer-down state
        for (x in 10..16) assertEquals(3, s.at(x, 10))
        val undone = s.undo()
        assertEquals(base.doc, undone.doc)
        assertFalse(undone.canUndo)
    }

    @Test
    fun lineCircleFillAndText() {
        val l = blank().line(4, 12, 20, 12, 3, false)
        for (x in 4..20) assertEquals(3, l.at(x, 12))
        val c = blank().circle(12, 12, 8, 1, false)
        assertEquals(1, c.at(12, 4)); assertEquals(1, c.at(20, 12)); assertEquals(0, c.at(12, 12))
        val f = c.fill(12, 12, 2, false)
        assertEquals(2, f.at(12, 12)); assertEquals(1, f.at(12, 4)); assertEquals(0, f.at(12, 1))
        assertSame(f, f.fill(12, 12, 2, false))                            // same shade: no-op, no history
        val t = blank().text("hi", 3, false)
        assertEquals(3, t.at(9, 10)); assertEquals(3, t.at(15, 14))
    }

    @Test
    fun mirroredFillFillsBothSides() {
        val s = blank().line(12, 3, 12, 21, 3, false).fill(6, 12, 2, mirror = true)
        assertEquals(2, s.at(6, 12)); assertEquals(2, s.at(18, 12)); assertEquals(3, s.at(12, 12))
    }

    @Test
    fun shiftDropsEdgePixelsAndClearsOffPanelCells() {
        val s = blank().paint(listOf(24 to 12, 12 to 12), 3, false).shift(1, 0)
        assertEquals(3, s.at(13, 12)); assertEquals(0, s.at(12, 12))
        assertTrue((0 until 625).count { s.frame.shades[it].toInt() != 0 } == 1)   // (24,12) fell off the right edge
        val up = blank().paint(listOf(12 to 0), 3, false).shift(-4, 0)             // lands on (8,0), which has no LED → cleared
        assertEquals(0, up.frame.shades.count { it.toInt() != 0 })
    }

    @Test
    fun clearAndFrames() {
        var s = blank().paint(listOf(12 to 12), 3, false)
        s = s.addFrame()
        assertEquals(2, s.doc.frames.size); assertEquals(1, s.current); assertEquals(3, s.at(12, 12))    // copy of the current frame
        s = s.clear(); assertEquals(0, s.at(12, 12)); assertEquals(3, s.doc.frames[0][25, 12, 12])
        s = s.moveFrame(-1); assertEquals(0, s.current); assertEquals(0, s.at(12, 12))                   // the cleared frame moved first
        s = s.deleteFrame(); assertEquals(1, s.doc.frames.size); assertEquals(3, s.at(12, 12))
        assertSame(s, s.deleteFrame())                                                                    // never below 1 frame
        repeat(30) { s = s.addFrame() }
        assertEquals(24, s.doc.frames.size)
        assertSame(s, s.moveFrame(1))                                                                     // last frame can't move right
    }

    @Test
    fun holdFpsAndRenameAreClamped() {
        val s = blank().setHold(9).setFps(99).rename("  a very very long drawing name here  ")
        assertEquals(4, s.frame.hold); assertEquals(20, s.doc.fps)
        assertEquals(24, s.doc.name.length); assertEquals("a very very long drawing", s.doc.name)
        assertEquals(2, blank().setFps(0).doc.fps); assertEquals(1, blank().setHold(0).frame.hold)
    }

    @Test
    fun undoRedoAndHistoryCap() {
        var s = blank()
        val first = s.paint(listOf(12 to 12), 3, false)
        s = first.paint(listOf(13 to 12), 3, false)
        s = s.undo(); assertEquals(first.doc, s.doc); assertTrue(s.canRedo)
        s = s.redo(); assertEquals(3, s.at(13, 12))
        s = s.undo().paint(listOf(1 to 12), 1, false); assertFalse(s.canRedo)    // a new edit clears redo
        var h = blank()
        repeat(60) { i -> h = h.paint(listOf((i % 20) + 2 to 12), (i % 3) + 1, false) }
        var undos = 0
        while (h.canUndo) { h = h.undo(); undos++ }
        assertEquals(50, undos)
    }

    @Test
    fun selectIsNotAnUndoStep() {
        val s = blank().addFrame().select(0)
        assertEquals(0, s.current)
        assertEquals(1, s.undo().doc.frames.size)
    }
}
