package app.backlit.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicStateTest {

    @Test
    fun startsIdle() = assertEquals(MusicPhase.IDLE, MusicState().phase)

    @Test
    fun musicWithAudioGoesLive() {
        assertEquals(MusicPhase.LIVE, MusicState().update(0, true, true, 0.4f))
    }

    @Test
    fun stoppingMusicGoesIdleWithOneSecondDecay() {
        val s = MusicState()
        s.update(0, true, true, 0.4f)
        assertEquals(MusicPhase.IDLE, s.update(100, false, true, 0f))
        assertTrue(s.inDecay(100))
        assertTrue(s.inDecay(1099))
        assertFalse(s.inDecay(1100))
    }

    @Test
    fun musicWithoutVisualizerIsFallback() {
        assertEquals(MusicPhase.FALLBACK, MusicState().update(0, true, false, 0f))
    }

    @Test
    fun shortSilenceDuringLiveStaysLive() {
        val s = MusicState()
        s.update(0, true, true, 0.5f)
        assertEquals(MusicPhase.LIVE, s.update(1000, true, true, 0f))
        assertEquals(MusicPhase.LIVE, s.update(4000, true, true, 0f))
        assertEquals(MusicPhase.LIVE, s.update(5500, true, true, 0.3f))
    }

    @Test
    fun fiveSecondsOfSilenceWhileMusicPlaysIsFallback() {
        val s = MusicState()
        s.update(0, true, true, 0.5f)
        s.update(1000, true, true, 0f)
        assertEquals(MusicPhase.LIVE, s.update(5999, true, true, 0f))
        assertEquals(MusicPhase.FALLBACK, s.update(6000, true, true, 0f))
    }

    @Test
    fun fallbackRecoversWhenAudioReturns() {
        val s = MusicState()
        s.update(0, true, false, 0f)
        assertEquals(MusicPhase.LIVE, s.update(500, true, true, 0.2f))
    }

    @Test
    fun retriesVisualizerEveryTenSecondsInFallback() {
        val s = MusicState()
        s.update(1000, true, false, 0f)
        assertFalse(s.shouldRetryVisualizer(10_999))
        assertTrue(s.shouldRetryVisualizer(11_000))
        assertFalse(s.shouldRetryVisualizer(11_001))
        assertTrue(s.shouldRetryVisualizer(21_000))
    }

    @Test
    fun noRetryOutsideFallback() {
        val s = MusicState()
        s.update(0, true, true, 0.5f)
        assertFalse(s.shouldRetryVisualizer(60_000))
    }
}
