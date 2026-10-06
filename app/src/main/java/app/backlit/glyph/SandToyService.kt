package app.backlit.glyph

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import app.backlit.sand.EchoFilter
import app.backlit.sand.HourglassShape
import app.backlit.sand.Orientation
import app.backlit.sand.Phase
import app.backlit.sand.SandAlarm
import app.backlit.sand.SandArt
import app.backlit.sand.SandLayout
import app.backlit.sand.SandSim
import app.backlit.sand.TimerState
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
import kotlin.math.hypot

/** The hourglass toy: live tilt sand while ACTIVE, a still each minute in AOD. The timer (TimerState) is the truth. */
class SandToyService : Service() {

    private var scope: CoroutineScope? = null
    private var renderJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private lateinit var shape: HourglassShape
    private lateinit var sim: SandSim
    private var settings = Settings()
    private var alerts: AlertsRuntime? = null
    private var loaded = false
    private var state = TimerState()
    private val echoes = EchoFilter()

    private var sensors: SensorManager? = null
    private var sensorsOn = false
    private var oneShot = false
    private val grav = FloatArray(2)
    private var haveReading = false
    private var needBaseline = true
    private var needRebuild = true
    private var dirX = 0.0
    private var dirY = 1.0
    private var lastRateAt = 0L
    private var handledRefill = 0L

    private fun now() = System.currentTimeMillis()

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> if (loaded) { commit(state.longPress(now(), settings.sandPresets)); kick() }
                GlyphToy.EVENT_AOD -> {
                    modes.onAodEvent(now())
                    if (profile.aodOnly && loaded && scope != null) sampleOnce()
                    kick()
                }
            }
        }
    }
    private val messenger = Messenger(handler)
    private val rekick = Runnable { kick() }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // Matrix coordinates: the matrix is on the back, so device +x reads as matrix gx = accelX; +y down the matrix = accelY.
            val gx = X_SIGN * e.values[0]
            val gy = e.values[1]
            if (!haveReading || oneShot) { grav[0] = gx; grav[1] = gy } else {
                grav[0] = 0.8f * grav[0] + 0.2f * gx
                grav[1] = 0.8f * grav[1] + 0.2f * gy
            }
            haveReading = true
            val m = hypot(grav[0], grav[1])
            // Flat (face-down on a desk): read the matrix upright, so sand falls to its bottom edge.
            if (m >= 3f) { dirX = grav[0] / m.toDouble(); dirY = grav[1] / m.toDouble() } else { dirX = 0.0; dirY = 1.0 }
            if (oneShot) {
                oneShot = false
                setSensors(false)
                applyOrientation(now())
                kick()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        if (profile == DeviceProfile.UNSUPPORTED) return messenger.binder

        shape = HourglassShape.forSize(profile.size)
        sim = SandSim(shape)
        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "sand toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        ToyPresence.enter()
        val rt = AlertsRuntime.get(this).also { alerts = it }
        rt.toyChanged()
        sensors = getSystemService(SensorManager::class.java)
        s.launch {
            settings = repo.settings.first()
            state = TimerState.decode(settings.sandTimer)
            dirY = state.upSide.toDouble()
            handledRefill = state.refillUntil
            loaded = true
            commit(state.tick(now()))
            output = GlyphOutput(this@SandToyService, profile) { kick() }.also { it.connect() }
            repo.update { if (it.sandToyEverBound) it else it.copy(sandToyEverBound = true) }
            launch { repo.settings.collect { onSettings(it) } }
            launch { rt.bus.collect { kick() } }
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(rekick)
        setSensors(false)
        SandAlarm.liveLoop = false
        if (loaded) persist()
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

    /** Settings changed (ours echoing back, or the app / alarm changed the timer). */
    private fun onSettings(s: Settings) {
        settings = s
        if (!echoes.isEcho(s.sandTimer)) {
            val incoming = TimerState.decode(s.sandTimer)
            if (incoming.persisted() != state.persisted()) {
                state = incoming.copy(upSide = state.upSide.takeIf { incoming.phase == Phase.READY } ?: incoming.upSide)
                needRebuild = true
                lastRateAt = 0L
            }
        }
        kick()
    }

    private fun isAod() = modes.mode(now()) == Mode.AOD

    private fun setSensors(on: Boolean) {
        val sm = sensors ?: return
        if (on == sensorsOn) return
        sensorsOn = on
        if (on) {
            if (!oneShot) {
                // A fresh live session: forget the pre-AOD reading and wait for a definite orientation again.
                needBaseline = true
                haveReading = false
            }
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        } else sm.unregisterListener(sensorListener)
    }

    /** 4a Pro (AOD only): one accelerometer sample per AOD tick so flips still count. */
    private fun sampleOnce() {
        if (sensorsOn) return
        oneShot = true
        setSensors(true)
    }

    private fun applyOrientation(t: Long) {
        if (!loaded || !haveReading) return
        val o = Orientation.of(grav[0], grav[1])
        val next = if (needBaseline) {
            val (s, settled) = state.firstReading(o, t)
            if (settled) needBaseline = false
            s
        } else state.onOrientation(o, t)
        if (o == Orientation.FLAT && next.upSide != state.upSide) needRebuild = true   // view turned upright: redraw the sand
        commit(next)
    }

    private fun commit(next: TimerState) {
        val prev = state
        if (next == prev) return
        state = next
        if (next.phase != prev.phase) lastRateAt = 0L
        if (next.phase == Phase.DONE && prev.phase != Phase.DONE) {
            needRebuild = true
            SandAlarm.cancel(this)
            SandAlarm.ring(this, settings.sandAlert)
        }
        if (next.persisted() != prev.persisted()) persist()
    }

    private fun persist() {
        val json = state.encode()
        echoes.record(json)
        SandAlarm.sync(this, state, settings.sandExact)
        // Detached, so a save made just before unbind isn't cancelled with the toy's scope.
        CoroutineScope(Dispatchers.IO).launch { repo.update { it.copy(sandTimer = json) } }
    }

    /** Steer and step the sand; rebuild it from the clock after any gap (bind, AOD, alert, refill). */
    private fun stepSim(t: Long) {
        val st = state
        if (t < st.numberUntil || t < st.refillUntil) return
        if (needRebuild || handledRefill != st.refillUntil) {
            handledRefill = st.refillUntil
            needRebuild = false
            sim.load(SandLayout.layout(shape, st.fractionUp(t), false, st.upSide))
        }
        if (st.phase != Phase.RUNNING) sim.gateRate = 0.0
        else if (t - lastRateAt >= 1000) {
            lastRateAt = t
            sim.gateRate = SandSim.gateRateFor(shape.total, st.durationMs, sim.countOn(st.upSide), st.timeLeft(t))
        }
        sim.step(dirX, dirY, FRAME_MS.toDouble())
    }

    private fun kick() {
        val s = scope ?: return
        if (!loaded || renderJob?.isActive == true) return
        handler.removeCallbacks(rekick)
        renderJob = s.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val t = now()
                val aod = isAod()
                if (!profile.aodOnly) setSensors(!aod)
                SandAlarm.liveLoop = !aod
                commit(state.tick(t))
                if (!aod) applyOrientation(t)
                val alert = alerts?.bus?.value
                if (alert != null || aod) needRebuild = true
                val grid = runCatching {
                    when {
                        alert != null -> alerts!!.animationFor(alert).frame(profile.size, AlertsRuntime.now() - alert.startedAt)
                        aod -> SandArt.still(shape, state, t)
                        else -> { stepSim(t); SandArt.frame(shape, sim.grid, sim.moved, state, t) }
                    }
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = alert == null && aod))
                if (aod && alert == null) {
                    SandAlarm.liveLoop = false
                    modes.msUntilActive(t)?.let { handler.postDelayed(rekick, it + 100) }
                    if (t < state.numberUntil) handler.postDelayed(rekick, state.numberUntil - t + 50)
                    break
                }
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    private companion object {
        const val TAG = "BacklitSand"
        const val FRAME_MS = 50L
        /** Flip to -1f if tilting the phone pours the sand the wrong way on the device (checked in Task 9). */
        const val X_SIGN = 1f
    }
}
