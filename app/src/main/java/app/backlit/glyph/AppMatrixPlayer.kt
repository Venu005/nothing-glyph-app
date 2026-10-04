package app.backlit.glyph

import android.content.Context
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.ToyPresence
import app.backlit.render.PixelGrid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Plays the active alert with setAppMatrixFrame while no Backlit toy is showing. Main thread. */
class AppMatrixPlayer(private val app: Context, private val runtime: AlertsRuntime) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var output: GlyphOutput? = null

    fun sync() {
        val shouldPlay = runtime.bus.value != null && ToyPresence.count.value == 0
        if (shouldPlay && job == null) start() else if (!shouldPlay && job != null) stop()
    }

    private fun start() {
        val profile = DeviceProfile.detect()
        if (profile == DeviceProfile.UNSUPPORTED) return
        val out = GlyphOutput(app, profile, appMatrix = true) {}.also { it.connect() }
        output = out
        job = scope.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val alert = runtime.bus.value ?: break
                if (ToyPresence.count.value > 0) break
                val now = AlertsRuntime.now()
                val grid = runCatching { runtime.animationFor(alert).frame(profile.size, now - alert.startedAt) }
                    .getOrElse { Log.e(TAG, "alert render failed", it); PixelGrid(profile.size) }
                out.push(FrameEncoder.encode(grid, runtime.brightness, aod = false))
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
            stop()
        }
    }

    private fun stop() {
        val j = job
        job = null
        output?.close()
        output = null
        j?.cancel()
    }

    private companion object {
        const val TAG = "BacklitAlerts"
        const val FRAME_MS = 50L
    }
}
