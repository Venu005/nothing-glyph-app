package app.backlit.debug

import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.os.PowerManager
import android.util.Log
import app.backlit.glyph.DeviceProfile
import app.backlit.glyph.FrameEncoder
import app.backlit.glyph.GlyphOutput
import app.backlit.render.PixelFont
import app.backlit.render.PixelGrid
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Debug-only spike toy: while shown on the matrix, logs the output-mix Visualizer level every 100 ms. */
class AudioProbeToyService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var viz: Visualizer? = null
    private var output: GlyphOutput? = null

    override fun onBind(intent: Intent?): IBinder {
        val profile = DeviceProfile.detect()
        output = GlyphOutput(this, profile) {}.also { it.connect() }
        val v = runCatching {
            Visualizer(0).apply {
                setEnabled(false)
                setCaptureSize(Visualizer.getCaptureSizeRange()[1])
                setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
                setEnabled(true)
            }
        }.onFailure { Log.e(TAG, "Visualizer(0) failed", it) }.getOrNull()
        viz = v
        if (v != null) {
            val buf = ByteArray(v.captureSize)
            val pm = getSystemService(PowerManager::class.java)
            val am = getSystemService(AudioManager::class.java)
            handler.post(object : Runnable {
                override fun run() {
                    val rc = v.getFft(buf)
                    var sum = 0.0
                    for (k in 1 until buf.size / 2) sum += hypot(buf[2 * k].toDouble(), buf[2 * k + 1].toDouble())
                    val level = sum / (buf.size / 2)
                    Log.i(TAG, "toy rc=$rc level=%.2f screenOn=${pm.isInteractive} music=${am.isMusicActive}"
                        .format(level))
                    // Show the level ×10 as digits (e.g. 35 = 3.5) so the toy is visible on the matrix.
                    val text = (level * 10).roundToInt().coerceIn(0, 99).toString().padStart(2, '0')
                    val g = PixelGrid(profile.size)
                    PixelFont.text(g, text, (profile.size - 7) / 2, (profile.size - 5) / 2, 255)
                    output?.push(FrameEncoder.encode(g, 80, aod = false))
                    handler.postDelayed(this, 100)
                }
            })
        }
        return Messenger(Handler(Looper.getMainLooper())).binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        runCatching { viz?.release() }
        viz = null
        output?.close()
        output = null
        return false
    }

    private companion object { const val TAG = "BacklitProbe" }
}
