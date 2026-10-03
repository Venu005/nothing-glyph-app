package app.backlit.audio

import kotlin.math.exp
import kotlin.math.sin

/** Deterministic fake music (120 BPM kick, snare on the off-beat bar, wobbling mids/highs). */
object DemoAudio {
    fun frame(tMs: Long): AudioFrame {
        val kick = exp(-((tMs % 500) / 500.0) * 7)
        val snare = if ((tMs / 500) % 2 == 1L) exp(-((tMs % 500) / 500.0) * 9) else 0.1
        fun wob(period: Double, shift: Double) = 0.5 + 0.5 * sin(tMs / period + shift)
        val raw = doubleArrayOf(
            kick, kick * 0.85, 0.3 + 0.3 * wob(700.0, 0.0), 0.3 + 0.5 * snare,
            0.25 + 0.35 * wob(430.0, 1.0), 0.15 + 0.6 * snare, 0.2 + 0.3 * wob(170.0, 2.0), 0.15 + 0.3 * wob(110.0, 3.0),
        )
        val bands = FloatArray(AudioFrame.BANDS) { raw[it].coerceIn(0.0, 1.0).toFloat() }
        return AudioFrame(bands, bands.average().toFloat(), kick.toFloat())
    }
}
