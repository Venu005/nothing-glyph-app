package app.backlit.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicEngineTest {

    private val sr = 44100
    private fun bassFft() = ByteArray(1024).also { it[2] = 100 }   // bin 1 ≈ 43 Hz

    private fun onlyCentreRow(g: app.backlit.render.PixelGrid): Boolean =
        g.litCount() == 25 && (0 until 25).all { g[it, 12] > 0 }

    @Test
    fun noMusicShowsTheIdleLine() {
        val e = MusicEngine()
        val g = e.tick(50, 50, musicActive = false, fft = null, samplingRateHz = sr, gain = 1f, hasVisualizer = false)
        assertEquals(MusicPhase.IDLE, e.phase)
        assertTrue(onlyCentreRow(g))
    }

    @Test
    fun musicWithAudioDrawsBars() {
        val e = MusicEngine()
        val g = e.tick(50, 50, true, bassFft(), sr, 1f, true)
        assertEquals(MusicPhase.LIVE, e.phase)
        assertTrue(g[12, 11] > 0)
        assertTrue(g[12, 1] > 0)
    }

    @Test
    fun musicWithoutVisualizerShowsFallbackLine() {
        val e = MusicEngine()
        val g = e.tick(50, 50, true, null, sr, 1f, false)
        assertEquals(MusicPhase.FALLBACK, e.phase)
        assertTrue(onlyCentreRow(g))
    }

    @Test
    fun missingFftWhileMusicPlaysFallsBack() {
        val e = MusicEngine()
        e.tick(0, 50, true, bassFft(), sr, 1f, true)
        var t = 0L
        while (t < 5_100) { t += 50; e.tick(t, 50, true, null, sr, 1f, true) }   // silence began at t = 50
        assertEquals(MusicPhase.FALLBACK, e.phase)
    }

    @Test
    fun pauseDecaysThenShowsIdleLine() {
        val e = MusicEngine()
        e.tick(0, 50, true, bassFft(), sr, 1f, true)
        val falling = e.tick(50, 50, false, null, sr, 1f, true)
        assertTrue("bars still visible while decaying", falling[12, 11] > 0)
        var g = falling
        var t = 50L
        while (t < 1_200) { t += 50; g = e.tick(t, 50, false, null, sr, 1f, true) }
        assertTrue(onlyCentreRow(g))
    }

    @Test
    fun switchingStyleMidSongKeepsRendering() {
        val e = MusicEngine()
        e.tick(0, 50, true, bassFft(), sr, 1f, true)
        for (id in listOf("peaks", "mirror", "peaks", "mirror")) {
            e.setStyle(id)
            assertEquals(id, e.styleId)
            val g = e.tick(100, 70, true, bassFft(), sr, 1f, true)
            assertTrue(g.litCount() > 0)
        }
    }

    @Test
    fun demoTickRendersTheCurrentStyle() {
        val e = MusicEngine()
        val g = e.tickDemo(50, DemoAudio.frame(0))
        assertTrue(g[12, 11] > 0)
    }
}
