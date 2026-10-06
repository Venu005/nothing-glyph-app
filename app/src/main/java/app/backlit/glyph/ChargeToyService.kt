package app.backlit.glyph

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.ToyPresence
import app.backlit.charge.Battery
import app.backlit.charge.ChargeSession
import app.backlit.charge.Moment
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeStyles
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

/** Battery toy: still level all day, plug-in / charging / done animations while charging. */
class ChargeToyService : Service() {

    private var scope: CoroutineScope? = null
    private var renderJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private var settings = Settings()
    private var alerts: AlertsRuntime? = null
    private val session = ChargeSession { settings.chargeTarget }
    private var lastBattery: Battery? = null
    // Nothing is drawn until settings and the import list have loaded, so the default style never flashes first.
    private var settingsLoaded = false
    private var imports: List<app.backlit.alerts.AnimIndexEntry>? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> scope?.launch { repo.update { it.copy(chargeStyle = ChargeStyles.next(it.chargeStyle)) } }
                GlyphToy.EVENT_AOD -> { modes.onAodEvent(System.currentTimeMillis()); kick() }
            }
        }
    }
    private val messenger = Messenger(handler)
    private val rekick = Runnable { kick() }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val b = batteryOf(intent) ?: return
            if (b == lastBattery) return                 // voltage/temperature-only updates
            lastBattery = b
            session.onBattery(b, AlertsRuntime.now(), active = !isAod())
            kick()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        if (profile == DeviceProfile.UNSUPPORTED) return messenger.binder

        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "charge toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        ToyPresence.enter()
        val rt = AlertsRuntime.get(this).also { alerts = it }
        rt.toyChanged()

        PlugWatcher.ensure(this)
        val sticky = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        batteryOf(sticky)?.let { lastBattery = it; session.onBind(it, AlertsRuntime.now(), PlugWatcher.lastPluggedAt) }

        output = GlyphOutput(this, profile) { kick() }.also { it.connect() }
        s.launch { repo.update { if (it.chargeToyEverBound) it else it.copy(chargeToyEverBound = true) } }
        s.launch { repo.settings.collect { settings = it; settingsLoaded = true; kick() } }
        s.launch { rt.config.collect { imports = it.imports; kick() } }
        s.launch { rt.bus.collect { kick() } }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { unregisterReceiver(batteryReceiver) }
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

    /** Draws now; keeps a 50 ms loop running while anything moves, and stops when the frame is still. */
    private fun kick() {
        val s = scope ?: return
        if (!settingsLoaded || imports == null || renderJob?.isActive == true) return
        handler.removeCallbacks(rekick)
        renderJob = s.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val now = AlertsRuntime.now()
                val alert = alerts?.bus?.value
                session.setHeld(alert != null, now)
                session.tick(now)
                val aod = isAod()
                val grid = runCatching {
                    if (alert != null) alerts!!.animationFor(alert).frame(profile.size, now - alert.startedAt) else frame(now, aod)
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = alert == null && aod))
                val animating = alert != null || (!aod && session.show.moment != Moment.STILL)
                if (!animating) {
                    // EVENT_AOD stops arriving when the phone wakes; redraw (and resume animating) once AOD lapses.
                    if (aod) modes.msUntilActive(System.currentTimeMillis())?.let { handler.postDelayed(rekick, it + 100) }
                    break
                }
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    private fun frame(now: Long, aod: Boolean): PixelGrid {
        val style = ChargeStyles.byId(settings.chargeStyle)
        val size = profile.size
        val level = session.level
        if (aod) return style.still(size, level)
        val show = session.show
        val t = now - show.startedAt
        return when (show.moment) {
            Moment.STILL -> style.still(size, level)
            Moment.PLUG_IN -> alerts?.library?.importedOnly(settings.chargePlugInAnim, imports.orEmpty())?.frame(size, t) ?: style.plugIn(size, level, t)
            Moment.CHARGING -> style.charging(size, level, t)
            Moment.DONE -> alerts?.library?.importedOnly(settings.chargeDoneAnim, imports.orEmpty())?.frame(size, t) ?: style.done(size, t)
        }
    }

    private companion object {
        const val TAG = "BacklitCharge"
        const val FRAME_MS = 50L

        fun batteryOf(i: Intent?): Battery? {
            i ?: return null
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return null
            return Battery(plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0, level = (level * 100 / scale).coerceIn(0, 100))
        }
    }
}
