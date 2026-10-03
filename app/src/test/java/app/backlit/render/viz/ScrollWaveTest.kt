package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollWaveTest {

    private fun lv(v: Float) = AudioFrame(FloatArray(8), v, 0f)

    @Test
    fun newSamplesEnterOnTheRightEvery70ms() {
        val s = ScrollWave(25)
        s.update(lv(0.5f), 70)
        s.update(lv(1.0f), 70)
        assertEquals(listOf(1.0f, 0.5f), s.samplesNewestFirst())
        s.update(lv(0f), 140)
        assertEquals(listOf(0f, 0f, 1.0f, 0.5f), s.samplesNewestFirst())
        s.update(lv(0.3f), 30)
        assertEquals(4, s.samplesNewestFirst().size)
    }

    @Test
    fun historyIsCappedAtGridWidth() {
        val s = ScrollWave(25)
        repeat(40) { s.update(lv(0.2f), 70) }
        assertEquals(25, s.samplesNewestFirst().size)
    }

    @Test
    fun newestColumnIsBrightest() {
        val s = ScrollWave(25)
        s.update(lv(0.5f), 70)
        s.update(lv(1.0f), 70)
        val g = s.render()
        assertEquals(255, g[24, 12])
        assertTrue(g[23, 12] in 1..254)
        assertEquals(0, g[22, 12])
    }
}
