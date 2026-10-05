package app.backlit.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasHintTest {

    @Test
    fun hintIsVisibleAtBothSizes() {
        for (size in listOf(25, 13)) {
            val g = CanvasHint.frame(size)
            assertTrue(g.litCount() >= 5)
            for (y in 0 until size) for (x in 0 until size) if (g[x, y] > 0) assertTrue(g.hasLed(x, y))
        }
    }

    @Test
    fun pickFallsBack() {
        assertEquals("b", CanvasHint.pick("b", listOf("a", "b")))
        assertEquals("a", CanvasHint.pick("gone", listOf("a", "b")))     // deleted → first drawing
        assertEquals("a", CanvasHint.pick("", listOf("a", "b")))
        assertNull(CanvasHint.pick("a", emptyList()))                    // nothing to show → hint
    }

    @Test
    fun nextWraps() {
        assertEquals("b", CanvasHint.next("a", listOf("a", "b")))
        assertEquals("a", CanvasHint.next("b", listOf("a", "b")))
        assertEquals("b", CanvasHint.next("gone", listOf("a", "b")))     // from the fallback "a"
        assertNull(CanvasHint.next("", emptyList()))
    }
}
