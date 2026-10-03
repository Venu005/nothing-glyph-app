package app.backlit.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import app.backlit.audio.MusicActivity
import app.backlit.audio.MusicEngine
import app.backlit.audio.OutputVisualizer
import app.backlit.audio.hasAudioPermission
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.render.PixelGrid
import app.backlit.render.viz.VizStyles
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

class MusicToyService : Service() {

    private var scope: CoroutineScope? = null
    private var loopJob: Job? = null
    private var output: GlyphOutput? = null
    private val engine = MusicEngine(SIZE)
    private val visualizer = OutputVisualizer()
    private lateinit var music: MusicActivity
    private lateinit var repo: SettingsRepo
    private var settings = Settings()
    private var frames = 0
    private var frameNanos = 0L

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            if (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA) == GlyphToy.EVENT_CHANGE) {
                scope?.launch { repo.update { it.copy(musicStyle = VizStyles.next(it.musicStyle)) } }
            }
        }
    }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder {
        val profile = DeviceProfile.detect()
        repo = SettingsRepo.get(this)
        music = MusicActivity(this)
        if (profile != DeviceProfile.PHONE_3) return messenger.binder

        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "music toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        output = GlyphOutput(this, profile) { startLoop() }.also { it.connect() }
        s.launch { repo.update { if (it.toyEverBound) it else it.copy(toyEverBound = true) } }
        s.launch {
            repo.settings.collect {
                settings = it
                engine.setStyle(it.musicStyle)
            }
        }
        if (hasAudioPermission(this)) visualizer.start()
        return messenger.binder
    }

    private fun startLoop() {
        val s = scope ?: return
        loopJob?.cancel()
        loopJob = s.launch {
            var last = SystemClock.elapsedRealtime()
            val pacer = FramePacer(FRAME_MS)
            var wait = FRAME_MS
            while (isActive) {
                delay(wait)
                val now = SystemClock.elapsedRealtime()
                val dt = now - last
                last = now
                if (engine.shouldRetryVisualizer(now) && hasAudioPermission(this@MusicToyService)) {
                    visualizer.release()
                    visualizer.start()
                }
                val t0 = SystemClock.elapsedRealtimeNanos()
                val grid = runCatching {
                    engine.tick(
                        now, dt, music.isPlaying(), visualizer.readFft(), visualizer.samplingRateHz,
                        settings.musicSensitivity.gain, visualizer.isActive,
                    )
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(SIZE) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = false))
                frameNanos += SystemClock.elapsedRealtimeNanos() - t0
                if (++frames == 200) {
                    Log.d(TAG, "avg frame %.2f ms, phase=%s".format(frameNanos / 200 / 1e6, engine.phase))
                    frames = 0
                    frameNanos = 0
                }
                // The SDK push takes ~15 ms; pace on a fixed 50 ms grid so it doesn't stretch every frame.
                wait = pacer.delayBeforeNext(SystemClock.elapsedRealtime())
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        loopJob?.cancel()
        scope?.cancel()
        scope = null
        visualizer.release()
        output?.close()
        output = null
        return false
    }

    private companion object {
        const val TAG = "BacklitMusic"
        const val SIZE = 25
        const val FRAME_MS = 50L
    }
}
