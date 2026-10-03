package app.backlit.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nothing.ketchum.GlyphMatrixManager

class GlyphOutput(
    private val context: Context,
    private val profile: DeviceProfile,
    private val onReady: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var manager: GlyphMatrixManager? = null
    private var ready = false
    private var closed = false
    private var attempt = 0
    private var last: IntArray? = null

    fun connect() {
        val device = profile.sdkDevice ?: return
        closed = false
        runCatching {
            val m = GlyphMatrixManager.getInstance(context.applicationContext)
            manager = m
            m.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName?) {
                    if (closed) return
                    runCatching { m.register(device) }
                        .onSuccess {
                            ready = true
                            attempt = 0
                            last = null
                            onReady()
                        }
                        .onFailure { Log.w(TAG, "register failed", it); retry() }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    ready = false
                    last = null
                    if (!closed) retry()
                }
            })
        }.onFailure { Log.w(TAG, "init failed", it); retry() }
    }

    private fun retry() {
        if (closed || attempt >= RETRY_DELAYS_MS.size) {
            Log.w(TAG, "giving up after $attempt retries")
            return
        }
        val delay = RETRY_DELAYS_MS[attempt++]
        handler.postDelayed({
            runCatching { manager?.unInit() }
            connect()
        }, delay)
    }

    fun push(frame: IntArray) {
        if (!ready) return
        if (last?.contentEquals(frame) == true) return
        runCatching { manager?.setMatrixFrame(frame) }
            .onSuccess { last = frame }
            .onFailure { Log.w(TAG, "setMatrixFrame failed", it) }
    }

    fun close() {
        closed = true
        ready = false
        last = null
        handler.removeCallbacksAndMessages(null)
        runCatching { manager?.unInit() }
        manager = null
    }

    private companion object {
        const val TAG = "BacklitGlyph"
        val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L)
    }
}
