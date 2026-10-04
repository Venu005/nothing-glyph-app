package app.backlit.alerts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.backlit.anim.BuiltInAnimations
import app.backlit.anim.GlyphAnimation
import app.backlit.anim.MuseumFormat
import app.backlit.data.SettingsRepo
import app.backlit.data.settingsDataStore
import app.backlit.glyph.AppMatrixPlayer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed interface ImportOutcome {
    data class Ok(val name: String, val sourceSize: Int) : ImportOutcome
    data object Invalid : ImportOutcome
}

/** Process-wide alert state: triggers in, ActiveAlert out. Main thread. */
class AlertsRuntime private constructor(private val app: Context) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> Log.e(TAG, "alerts failed", e) },
    )
    private val store = AlertStore(app.settingsDataStore)
    private val handler = Handler(Looper.getMainLooper())
    private var current = AlertConfig()
    private var loaded = false
    private val coordinator = AlertCoordinator({ current.contacts }, { current.devices })
    private val _bus = MutableStateFlow<ActiveAlert?>(null)
    private val player = AppMatrixPlayer(app, this)

    val bus: StateFlow<ActiveAlert?> = _bus
    val config: Flow<AlertConfig> = store.config
    val library = AnimationLibrary(File(app.filesDir, "animations"))
    var brightness: Int = 80
        private set

    init {
        scope.launch { store.config.collect { current = it; loaded = true } }
        scope.launch { SettingsRepo.get(app).settings.collect { brightness = it.brightness } }
    }

    fun onCallRinging(name: String) = dispatch { coordinator.onCallRinging(name, now()) }
    fun onCallEnded() = dispatch { coordinator.onCallEnded() }
    fun onMissedCall(key: String, texts: List<String>) = dispatch { coordinator.onMissedCall(key, texts, now()) }
    fun onMissedCleared(key: String) = dispatch { coordinator.onMissedCleared(key) }
    fun onDeviceConnected(address: String) = dispatch { coordinator.onDeviceConnected(address, now()) }
    fun preview(animationId: String) = dispatch { coordinator.preview(animationId, now()) }

    /** Called by toys when they bind/unbind so the app-matrix player can step in or out. */
    fun toyChanged() = player.sync()

    fun animationFor(alert: ActiveAlert): GlyphAnimation = library.resolve(
        alert.animationId, current.imports,
        fallback = if (alert.kind == AlertKind.CALL) BuiltInAnimations.DEFAULT_CONTACT else BuiltInAnimations.DEFAULT_DEVICE,
    )

    suspend fun update(transform: (AlertConfig) -> AlertConfig) = store.update(transform)

    fun importJson(json: String, name: String): ImportOutcome {
        val id = "import:" + UUID.randomUUID()
        return when (val r = MuseumFormat.parse(json, id, name)) {
            MuseumFormat.Result.Invalid -> ImportOutcome.Invalid
            is MuseumFormat.Result.Ok -> {
                library.save(r.animation)
                val v = if (r.animation.sourceSize == 25) 1 else 4
                scope.launch { store.update { it.copy(imports = it.imports + AnimIndexEntry(id, name, v)) } }
                ImportOutcome.Ok(name, r.animation.sourceSize)
            }
        }
    }

    fun deleteImport(id: String) {
        library.delete(id)
        scope.launch { store.update { c -> c.copy(imports = c.imports.filterNot { it.id == id }) } }
    }

    private fun dispatch(event: () -> Unit) {
        scope.launch {
            if (!loaded) { current = store.config.first(); loaded = true }   // cold start: wait for rules
            event()
            publish()
        }
    }

    private val expire = Runnable { publish() }

    private fun publish() {
        coordinator.tick(now())
        _bus.value = coordinator.active
        handler.removeCallbacks(expire)
        coordinator.nextWakeAt()?.let { handler.postDelayed(expire, (it - now()).coerceAtLeast(0) + 10) }
        player.sync()
    }

    companion object {
        private const val TAG = "BacklitAlerts"
        @Volatile private var instance: AlertsRuntime? = null

        fun get(context: Context): AlertsRuntime =
            instance ?: synchronized(this) { instance ?: AlertsRuntime(context.applicationContext).also { instance = it } }

        fun now(): Long = SystemClock.elapsedRealtime()
    }
}
