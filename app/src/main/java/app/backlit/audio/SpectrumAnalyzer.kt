package app.backlit.audio

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max

/**
 * Visualizer FFT bytes → [AudioFrame].
 * Layout: [Re0, Re(n/2), Re1, Im1, Re2, Im2, …]; bin k sits at k × samplingRate / n Hz.
 * Each band is normalised against its own running peak (rises instantly, decays over ~3 s).
 */
class SpectrumAnalyzer {
    private val peaks = DoubleArray(AudioFrame.BANDS) { PEAK_FLOOR }
    private var bassAvg = 0.0
    private var kick = 0.0

    fun analyze(fft: ByteArray, samplingRateHz: Int, dtMs: Long, gain: Float): AudioFrame {
        val n = fft.size
        if (n < 4 || samplingRateHz <= 0) return AudioFrame.SILENT
        val sums = DoubleArray(AudioFrame.BANDS)
        val counts = IntArray(AudioFrame.BANDS)
        var maxMag = 0.0
        for (k in 1 until n / 2) {
            val mag = hypot(fft[2 * k].toDouble(), fft[2 * k + 1].toDouble())
            if (mag > maxMag) maxMag = mag
            val b = bandOf(k.toDouble() * samplingRateHz / n)
            if (b < 0) continue
            sums[b] += mag
            counts[b]++
        }
        if (maxMag < SILENCE) {
            kick = 0.0
            return AudioFrame.SILENT
        }
        val decay = exp(-dtMs / PEAK_DECAY_MS)
        val bands = FloatArray(AudioFrame.BANDS)
        for (i in 0 until AudioFrame.BANDS) {
            val raw = if (counts[i] == 0) 0.0 else sums[i] / counts[i]
            peaks[i] = max(raw, max(PEAK_FLOOR, peaks[i] * decay))
            bands[i] = (raw / peaks[i] * gain).coerceIn(0.0, 1.0).toFloat()
        }
        val raw0 = if (counts[0] == 0) 0.0 else sums[0] / counts[0]
        kick = if (raw0 > ONSET_RATIO * bassAvg && raw0 > ONSET_MIN) 1.0 else kick * 0.8
        bassAvg += (raw0 - bassAvg) * (dtMs / 1000.0).coerceAtMost(1.0)
        return AudioFrame(bands, bands.average().toFloat(), kick.toFloat())
    }

    private companion object {
        val EDGES = doubleArrayOf(20.0, 60.0, 150.0, 400.0, 1000.0, 2500.0, 6000.0, 12000.0, 20000.0)
        const val PEAK_FLOOR = 1.0
        const val PEAK_DECAY_MS = 3000.0
        const val SILENCE = 2.0
        const val ONSET_RATIO = 1.5
        const val ONSET_MIN = 4.0

        fun bandOf(hz: Double): Int {
            for (i in 0 until AudioFrame.BANDS) if (hz >= EDGES[i] && hz < EDGES[i + 1]) return i
            return -1
        }
    }
}
