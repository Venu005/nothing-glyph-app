package app.backlit.debug

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import kotlin.math.hypot

/** Debug-only spike: logs the output-mix Visualizer level every 100 ms for 2 minutes. */
class AudioProbeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var viz: Visualizer? = null
    private var ticks = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "grant RECORD_AUDIO first: adb shell pm grant app.backlit android.permission.RECORD_AUDIO")
            finish(); return
        }
        val v = runCatching {
            Visualizer(0).apply {
                setEnabled(false)
                setCaptureSize(Visualizer.getCaptureSizeRange()[1])
                setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
                setEnabled(true)
            }
        }.onFailure { Log.e(TAG, "Visualizer(0) failed", it) }.getOrNull()
        if (v == null) { finish(); return }
        viz = v
        val buf = ByteArray(v.captureSize)
        val pm = getSystemService(PowerManager::class.java)
        val am = getSystemService(AudioManager::class.java)
        handler.post(object : Runnable {
            override fun run() {
                val rc = v.getFft(buf)
                var sum = 0.0
                for (k in 1 until buf.size / 2) sum += hypot(buf[2 * k].toDouble(), buf[2 * k + 1].toDouble())
                Log.i(TAG, "rc=$rc level=%.2f screenOn=${pm.isInteractive} music=${am.isMusicActive} rate=${v.samplingRate}"
                    .format(sum / (buf.size / 2)))
                if (++ticks < 1200) handler.postDelayed(this, 100) else finish()
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { viz?.release() }
        super.onDestroy()
    }

    private companion object { const val TAG = "BacklitProbe" }
}
