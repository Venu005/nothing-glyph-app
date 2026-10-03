package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorPeaksTest {

    private val bass = AudioFrame(FloatArray(8).also { it[0] = 1f }, 0.125f, 0f)

    @Test
    fun peakDotSitsJustBeyondTheBar() {
        val s = MirrorPeaks(25)
        s.update(AudioFrame(FloatArray(8).also { it[0] = 0.5f }, 0.06f, 0f), 50)   // centre bar h = 6
        val g = s.render()
        assertEquals(6, s.peakHeight(12))
        assertEquals(255, g[12, 12 - 7])
        assertEquals(255, g[12, 12 + 7])
        assertEquals(200, g[12, 12 - 6])     // bar tip
    }

    @Test
    fun peakHoldsThenFallsOnePixelPer200ms() {
        val s = MirrorPeaks(25)
        s.update(bass, 50)
        assertEquals(12, s.peakHeight(12))
        repeat(4) { s.update(AudioFrame.SILENT, 50) }   // t = 200 ms
        assertEquals(12, s.peakHeight(12))
        s.update(AudioFrame.SILENT, 50)                  // t = 250 ms
        assertEquals(11, s.peakHeight(12))
        repeat(4) { s.update(AudioFrame.SILENT, 50) }   // t = 450 ms
        assertEquals(10, s.peakHeight(12))
    }
}
