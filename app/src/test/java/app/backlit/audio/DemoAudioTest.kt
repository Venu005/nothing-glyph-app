package app.backlit.audio

import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAudioTest {
    @Test
    fun beatsAndStaysInRange() {
        assertTrue(DemoAudio.frame(0).kick > 0.99f)
        assertTrue(DemoAudio.frame(400).kick < 0.1f)
        var t = 0L
        while (t < 10_000) {
            val f = DemoAudio.frame(t)
            assertTrue(f.bands.all { it in 0f..1f })
            assertTrue(f.level > 0f)
            t += 37
        }
    }
}
