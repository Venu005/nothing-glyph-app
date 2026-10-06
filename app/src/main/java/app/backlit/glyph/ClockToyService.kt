package app.backlit.glyph

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.ToyPresence
import app.backlit.data.DayLightResolver
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.render.FaceContext
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import app.backlit.render.faces.Faces
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
import java.time.LocalDateTime
import java.time.ZoneId

class ClockToyService : Service() {

    private var scope: CoroutineScope? = null
    private var tickJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private val resolver = DayLightResolver()
    private var settings = Settings()
    private var alertJob: Job? = null
    private var alerts: AlertsRuntime? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> scope?.launch {
                    repo.update { it.copy(faceId = Faces.next(it.faceId).id) }
                }
                GlyphToy.EVENT_AOD -> {
                    modes.onAodEvent(System.currentTimeMillis())
                    draw()
                }
            }
        }
    }
    private val messenger = Messenger(handler)

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            draw()
            restartTicker()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s

        if (profile != DeviceProfile.UNSUPPORTED) {
            ToyPresence.enter()
            val rt = AlertsRuntime.get(this).also { alerts = it }
            rt.toyChanged()
            s.launch { rt.bus.collect { a -> if (a != null) startAlert() else stopAlert() } }
            output = GlyphOutput(this, profile) { draw(); restartTicker() }.also { it.connect() }
            s.launch {
                repo.update { if (it.toyEverBound && it.clockToyEverBound) it else it.copy(toyEverBound = true, clockToyEverBound = true) }
            }
            s.launch {
                repo.settings.collect {
                    settings = it
                    draw()
                    restartTicker()
                }
            }
            registerReceiver(timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }, Context.RECEIVER_NOT_EXPORTED)
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { unregisterReceiver(timeReceiver) }
        tickJob?.cancel()
        scope?.cancel()
        scope = null
        alertJob?.cancel()
        alertJob = null
        output?.close()
        output = null
        if (alerts != null) {
            ToyPresence.leave()
            alerts?.toyChanged()
            alerts = null
        }
        return false
    }

    private fun context(now: LocalDateTime = LocalDateTime.now()): FaceContext = FaceContext(
        hour = now.hour,
        minute = now.minute,
        second = now.second,
        size = profile.size,
        mode = modes.mode(System.currentTimeMillis()),
        options = settings.faceOptions,
        dayLight = resolver.resolve(settings, now.toLocalDate(), ZoneId.systemDefault()),
    )

    private fun draw() {
        if (alertJob?.isActive == true) return
        val out = output ?: return
        val ctx = context()
        val face = Faces.byId(settings.faceId)
        val grid = runCatching { face.render(ctx) }
            .getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
        out.push(FrameEncoder.encode(grid, settings.brightness, aod = ctx.mode == Mode.AOD))
    }

    private fun restartTicker() {
        if (alertJob?.isActive == true) return
        val s = scope ?: return
        tickJob?.cancel()
        tickJob = s.launch {
            while (isActive) {
                val perSecond = Faces.byId(settings.faceId).needsSecondTicks(context())
                delay(TickSchedule.delayToNextTick(System.currentTimeMillis(), perSecond))
                draw()
            }
        }
    }

    private fun startAlert() {
        val s = scope ?: return
        val rt = alerts ?: return
        if (alertJob?.isActive == true) return
        tickJob?.cancel()
        alertJob = s.launch {
            val pacer = FramePacer(50)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val a = rt.bus.value ?: break
                val grid = runCatching { rt.animationFor(a).frame(profile.size, AlertsRuntime.now() - a.startedAt) }
                    .getOrElse { PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = false))
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    private fun stopAlert() {
        alertJob?.cancel()
        alertJob = null
        draw()
        restartTicker()
    }

    private companion object {
        const val TAG = "BacklitToy"
    }
}
