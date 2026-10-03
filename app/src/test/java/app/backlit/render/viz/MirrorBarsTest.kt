package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorBarsTest {

    private fun bass(v: Float = 1f) = AudioFrame(FloatArray(8).also { it[0] = v }, v / 8, 0f)

    @Test
    fun zeroFrameLightsOnlyTheCentreRow() {
        val s = MirrorBars(25)
        s.update(AudioFrame.SILENT, 50)
        val g = s.render()
        assertEquals(25, g.litCount())
        for (x in 0 until 25) assertEquals(140, g[x, 12])
    }

    @Test
    fun symmetricAboutTheCentreRow() {
        val s = MirrorBars(25)
        s.update(AudioFrame(FloatArray(8) { 0.2f + it * 0.1f }, 0.5f, 0f), 50)
        val g = s.render()
        for (x in 0 until 25) for (k in 1..12) assertEquals("x=$x k=$k", g[x, 12 - k], g[x, 12 + k])
    }

    @Test
    fun bassMakesTheCentreColumnTallest() {
        val s = MirrorBars(25)
        s.update(bass(), 50)
        val cols = ColumnLevels(25).also { it.update(bass()) }
        assertEquals(12, cols.height(12))
        for (x in 0 until 25) assertTrue(cols.height(x) <= cols.height(12))
        val g = s.render()
        assertEquals(255, g[12, 0])
        assertEquals(255, g[12, 24])
    }

    @Test
    fun barsFallGraduallyNotInstantly() {
        val c = ColumnLevels(25)
        c.update(bass())
        c.update(AudioFrame.SILENT)
        assertEquals(0.7f, c.levels[12], 1e-4f)
    }
}
