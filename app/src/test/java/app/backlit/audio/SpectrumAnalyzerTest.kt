package app.backlit.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumAnalyzerTest {

    private val sr = 44100
    private val n = 1024   // bin width ≈ 43 Hz

    private fun bin(k: Int, amp: Int) = ByteArray(n).also { it[2 * k] = amp.toByte() }
    private fun flat(amp: Int) = ByteArray(n).also { for (k in 1 until n / 2) it[2 * k] = amp.toByte() }

    @Test
    fun bassToneFillsBandZero() {
        val f = SpectrumAnalyzer().analyze(bin(1, 100), sr, 50, 1f)   // 43 Hz
        assertEquals(1f, f.bands[0], 1e-4f)
        for (i in 1 until 8) assertEquals("band $i", 0f, f.bands[i], 1e-6f)
    }

    @Test
    fun trebleToneLightsBandSeven() {
        val f = SpectrumAnalyzer().analyze(bin(348, 100), sr, 50, 1f)  // ≈ 15 kHz
        assertTrue(f.bands[7] > 0.3f)
        for (i in 0 until 7) assertEquals("band $i", 0f, f.bands[i], 1e-6f)
    }

    @Test
    fun silenceGivesZeros() {
        val f = SpectrumAnalyzer().analyze(ByteArray(n), sr, 50, 1f)
        assertEquals(0f, f.level, 0f)
        assertTrue(f.bands.all { it == 0f })
        assertEquals(0f, f.kick, 0f)
    }

    @Test
    fun quietAndLoudNormaliseToTheSameShape() {
        val loud = SpectrumAnalyzer(); val quiet = SpectrumAnalyzer()
        var a = AudioFrame.SILENT; var b = AudioFrame.SILENT
        repeat(20) { a = loud.analyze(flat(80), sr, 50, 1f); b = quiet.analyze(flat(20), sr, 50, 1f) }
        for (i in 0 until 8) assertEquals("band $i", a.bands[i], b.bands[i], 0.05f)
    }

    @Test
    fun sensitivityScalesOutput() {
        fun after(gain: Float): Float {
            val s = SpectrumAnalyzer()
            s.analyze(flat(100), sr, 50, gain)
            return s.analyze(flat(50), sr, 50, gain).bands[3]
        }
        assertEquals(0.508f, after(1.0f), 0.02f)
        assertEquals(0.712f, after(1.4f), 0.02f)
        assertEquals(0.356f, after(0.7f), 0.02f)
    }

    @Test
    fun outputIsClampedAtHighSensitivity() {
        val s = SpectrumAnalyzer()
        repeat(10) { val f = s.analyze(flat(127), sr, 50, 1.4f); assertTrue(f.bands.all { it in 0f..1f }); assertTrue(f.level <= 1f) }
    }

    @Test
    fun kickFiresOnBassOnset() {
        val s = SpectrumAnalyzer()
        var f = AudioFrame.SILENT
        repeat(60) { f = s.analyze(bin(1, 10), sr, 50, 1f) }   // 3 s of steady bass: the running average catches up
        assertTrue("steady bass decays kick, was ${f.kick}", f.kick < 0.05f)
        f = s.analyze(bin(1, 80), sr, 50, 1f)
        assertEquals(1f, f.kick, 0f)
    }
}
