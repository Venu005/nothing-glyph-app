package app.backlit.glyph

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.ToyPresence
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.pet.GhostArt
import app.backlit.pet.MoodState
import app.backlit.pet.PetBrain
import app.backlit.pet.SleepWindow
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.math.sqrt

/** The ghost pet toy: animates while ACTIVE (with motion sensors), still pose in AOD. */
class PetToyService : Service() {

    private var scope: CoroutineScope? = null
    private var renderJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private var settings = Settings()
    private var alerts: AlertsRuntime? = null
    private var brain: PetBrain? = null
    private var sensors: SensorManager? = null
    private var sensorsOn = false
    private var lastSave = 0L
    private val gravity = FloatArray(3)

    private fun now() = System.currentTimeMillis()

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> { brain?.onLongPress(now(), active = !isAod()); kick() }
                GlyphToy.EVENT_AOD -> { modes.onAodEvent(now()); kick() }
            }
        }
    }
    private val messenger = Messenger(handler)
    private val rekick = Runnable { kick() }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val b = brain ?: return
            for (i in 0..2) gravity[i] = 0.8f * gravity[i] + 0.2f * e.values[i]
            val lx = e.values[0] - gravity[0]
            val ly = e.values[1] - gravity[1]
            val lz = e.values[2] - gravity[2]
            val t = now()
            val active = !isAod()
            b.onGravity(gravity[0], gravity[1], gravity[2], t, active)
            b.onShake(sqrt(lx * lx + ly * ly + lz * lz), t, active)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = applyBattery(intent)
    }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        if (profile == DeviceProfile.UNSUPPORTED) return messenger.binder

        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "pet toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        ToyPresence.enter()
        val rt = AlertsRuntime.get(this).also { alerts = it }
        rt.toyChanged()
        sensors = getSystemService(SensorManager::class.java)
        s.launch {
            settings = repo.settings.first()
            val start = MoodState(settings.petMood.toDouble(), settings.petMoodAt)
            brain = PetBrain(start, { SleepWindow(settings.petSleepStart, settings.petSleepEnd) }, ZoneId.systemDefault())
            applyBattery(registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED))
            output = GlyphOutput(this@PetToyService, profile) { kick() }.also { it.connect() }
            repo.update { if (it.petToyEverBound) it else it.copy(petToyEverBound = true) }
            launch { repo.settings.collect { settings = it; kick() } }
            launch { rt.bus.collect { kick() } }
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(rekick)
        setSensors(false)
        runCatching { unregisterReceiver(batteryReceiver) }
        save(force = true)
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

    private fun applyBattery(i: Intent?) {
        i ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        brain?.onCharging(i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0, level * 100 / scale, now())
    }

    private fun isAod() = modes.mode(now()) == Mode.AOD

    private fun setSensors(on: Boolean) {
        val sm = sensors ?: return
        if (on == sensorsOn) return
        sensorsOn = on
        if (on) sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        else sm.unregisterListener(sensorListener)
    }

    /** Persist the fractional mood: when changed (≤ once / 10 s), every 5 min, and on unbind. */
    private fun save(force: Boolean = false) {
        val b = brain ?: return
        val t = now()
        if (!force && !(b.dirty && t - lastSave >= 10_000) && t - lastSave < 300_000) return
        lastSave = t
        val snap = b.snapshot(t)
        val write: suspend () -> Unit = { repo.update { it.copy(petMood = snap.mood.toFloat(), petMoodAt = snap.at) } }
        val sc = scope
        if (sc != null && !force) sc.launch { write() }
        else CoroutineScope(Dispatchers.IO).launch { write() }
    }

    private fun kick() {
        val s = scope ?: return
        if (brain == null || renderJob?.isActive == true) return
        handler.removeCallbacks(rekick)
        renderJob = s.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val b = brain ?: break
                val t = now()
                val aod = isAod()
                setSensors(!aod)
                b.tick(t, active = !aod)
                save()
                val alert = alerts?.bus?.value
                val grid = runCatching {
                    when {
                        alert != null -> alerts!!.animationFor(alert).frame(profile.size, AlertsRuntime.now() - alert.startedAt)
                        aod -> GhostArt.still(profile.size, b.pose(t, profile.size), Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).minute)
                        else -> GhostArt.frame(profile.size, b.pose(t, profile.size), t)
                    }
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = alert == null && aod))
                if (aod && alert == null) {
                    modes.msUntilActive(t)?.let { handler.postDelayed(rekick, it + 100) }
                    break
                }
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    private companion object {
        const val TAG = "BacklitPet"
        const val FRAME_MS = 50L
    }
}
