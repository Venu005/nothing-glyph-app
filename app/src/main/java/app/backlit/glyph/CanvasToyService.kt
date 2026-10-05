package app.backlit.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.AnimIndexEntry
import app.backlit.alerts.ToyPresence
import app.backlit.anim.GlyphAnimation
import app.backlit.anim.ImportedAnimation
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import app.backlit.studio.CanvasHint
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Shows one of your Studio drawings; long press cycles through them. */
class CanvasToyService : Service() {

    private var scope: CoroutineScope? = null
    private var renderJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private var settings = Settings()
    private var alerts: AlertsRuntime? = null
    private var drawings: List<AnimIndexEntry> = emptyList()
    private var shownId: String? = null
    private var shownSince = 0L

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> {
                    val next = CanvasHint.next(settings.canvasDrawingId, drawings.map { it.id }) ?: return
                    scope?.launch { repo.update { it.copy(canvasDrawingId = next) } }
                }
                GlyphToy.EVENT_AOD -> { modes.onAodEvent(System.currentTimeMillis()); kick() }
            }
        }
    }
    private val messenger = Messenger(handler)
    private val rekick = Runnable { kick() }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        if (profile == DeviceProfile.UNSUPPORTED) return messenger.binder

        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "canvas toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        ToyPresence.enter()
        val rt = AlertsRuntime.get(this).also { alerts = it }
        rt.toyChanged()
        output = GlyphOutput(this, profile) { kick() }.also { it.connect() }
        s.launch { repo.update { if (it.canvasToyEverBound) it else it.copy(canvasToyEverBound = true) } }
        s.launch { repo.settings.collect { settings = it; kick() } }
        s.launch { rt.drawings.collect { drawings = it; kick() } }
        s.launch { rt.bus.collect { kick() } }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(rekick)
        renderJob?.cancel()
        renderJob = null
        scope?.cancel()
        scope = null
        output?.close()
        output = null
        if (alerts != null) {
            ToyPresence.leave()
            alerts?.toyChanged()
            alerts = null
        }
        return false
    }

    private fun isAod() = modes.mode(System.currentTimeMillis()) == Mode.AOD

    private fun kick() {
        val s = scope ?: return
        if (renderJob?.isActive == true) return
        handler.removeCallbacks(rekick)
        renderJob = s.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val now = AlertsRuntime.now()
                val alert = alerts?.bus?.value
                val aod = isAod()
                val anim = current(now)
                val grid = runCatching {
                    when {
                        alert != null -> alerts!!.animationFor(alert).frame(profile.size, now - alert.startedAt)
                        anim == null -> CanvasHint.frame(profile.size)
                        aod -> anim.frame(profile.size, 0)
                        else -> anim.frame(profile.size, now - shownSince)
                    }
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = alert == null && aod))
                val multiFrame = ((anim as? ImportedAnimation)?.durations?.size ?: 1) > 1
                val animating = alert != null || (!aod && multiFrame)
                if (!animating) {
                    if (aod) modes.msUntilActive(System.currentTimeMillis())?.let { handler.postDelayed(rekick, it + 100) }
                    break
                }
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    /** The drawing to show (restarting its loop when it changes), or null for the hint. */
    private fun current(now: Long): GlyphAnimation? {
        val id = CanvasHint.pick(settings.canvasDrawingId, drawings.map { it.id })
        if (id != shownId) { shownId = id; shownSince = now }
        return id?.let { alerts?.importedAnimation(it) }
    }

    private companion object {
        const val TAG = "BacklitCanvas"
        const val FRAME_MS = 50L
    }
}
