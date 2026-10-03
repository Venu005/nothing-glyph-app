package app.backlit.audio

import android.media.audiofx.Visualizer
import android.util.Log

/** The system output mix (session 0) as FFT bytes. Polled; never throws. */
class OutputVisualizer {
    private var viz: Visualizer? = null
    private var buffer = ByteArray(0)

    var samplingRateHz: Int = 44_100
        private set

    val isActive: Boolean get() = viz != null

    fun start(): Boolean = runCatching {
        val v = Visualizer(0)
        v.setEnabled(false)
        v.setCaptureSize(Visualizer.getCaptureSizeRange()[1])
        v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
        v.setEnabled(true)
        samplingRateHz = v.samplingRate / 1000
        buffer = ByteArray(v.captureSize)
        viz = v
    }.onFailure {
        Log.w(TAG, "Visualizer(0) unavailable", it)
        release()
    }.isSuccess

    fun readFft(): ByteArray? {
        val v = viz ?: return null
        val rc = runCatching { v.getFft(buffer) }.getOrDefault(Visualizer.ERROR)
        return if (rc == Visualizer.SUCCESS) buffer else null
    }

    fun release() {
        runCatching { viz?.setEnabled(false) }
        runCatching { viz?.release() }
        viz = null
    }

    private companion object { const val TAG = "BacklitAudio" }
}
