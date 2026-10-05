package app.backlit.alerts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.backlit.anim.BuiltInAnimations
import app.backlit.charge.ChargePreviewAnimation
import app.backlit.pet.PetPreviewAnimation
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import app.backlit.studio.DrawingCodec
import app.backlit.studio.Drawing
import java.util.UUID

sealed interface ImportOutcome {
    data class Ok(val name: String, val sourceSize: Int) : ImportOutcome
    data object Invalid : ImportOutcome
}

sealed interface DrawingImport {
    data class Ok(val id: String, val name: String, val truncated: Boolean, val simplified: Boolean) : DrawingImport
    data object Invalid : DrawingImport
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

    /** Called by toys when they bind/unbind so the app-matrix player can step in or out. */
    fun toyChanged() = player.sync()

    val drawings: Flow<List<AnimIndexEntry>> = store.config.map { c -> c.imports.filter { it.kind == KIND_DRAWING } }

    @Volatile private var previewSlot: GlyphAnimation? = null

    /** "Show on Glyph" for frames that have no library id yet (the Studio editor). */
    fun previewAnimation(anim: GlyphAnimation, durationMs: Long) {
        previewSlot = anim
        preview(PREVIEW_ID, durationMs)
    }

    /** Creates (id = null) or overwrites a drawing; returns its id. */
    suspend fun saveDrawing(d: Drawing, id: String?): String {
        val newId = id ?: ("import:" + UUID.randomUUID())
        withContext(Dispatchers.IO) { library.save(DrawingCodec.encode(d, newId)) }
        val entry = AnimIndexEntry(newId, d.name, if (d.size >= 25) 1 else 4, KIND_DRAWING, d.fps)
        store.update { c ->
            c.copy(imports = if (c.imports.any { it.id == newId }) c.imports.map { if (it.id == newId) entry else it } else c.imports + entry)
        }
        return newId
    }

    suspend fun loadDrawing(id: String, deviceSize: Int): Drawing? {
        val entry = store.config.first().imports.firstOrNull { it.id == id && it.kind == KIND_DRAWING } ?: return null
        val anim = withContext(Dispatchers.IO) { library.load(entry) } ?: return null
        return DrawingCodec.decode(anim, entry.name, entry.fps, deviceSize).drawing
    }

    suspend fun importAsDrawing(json: String, name: String, deviceSize: Int): DrawingImport {
        val parsed = MuseumFormat.parse(json, "import:tmp", name) as? MuseumFormat.Result.Ok ?: return DrawingImport.Invalid
        val r = DrawingCodec.decode(parsed.animation, name, fps = 0, size = deviceSize)
        val id = saveDrawing(r.drawing, null)
        return DrawingImport.Ok(id, r.drawing.name, r.truncated, r.simplified && parsed.animation.sourceSize == deviceSize)
    }

    suspend fun copyImportToDrawing(importId: String, deviceSize: Int): String? {
        val entry = store.config.first().imports.firstOrNull { it.id == importId } ?: return null
        val anim = withContext(Dispatchers.IO) { library.load(entry) } ?: return null
        val r = DrawingCodec.decode(anim, "${entry.name} (edit)", fps = if (entry.kind == KIND_DRAWING) entry.fps else 0, size = deviceSize)
        return saveDrawing(r.drawing, null)
    }

    fun preview(animationId: String, durationMs: Long = AlertCoordinator.SHORT_MS) =
        dispatch { coordinator.preview(animationId, now(), durationMs) }

    fun animationFor(alert: ActiveAlert): GlyphAnimation =
        (if (alert.animationId == PREVIEW_ID) previewSlot else null)
            ?: ChargePreviewAnimation.parse(alert.animationId)
            ?: PetPreviewAnimation.parse(alert.animationId)
            ?: library.resolve(
                alert.animationId, current.imports,
                fallback = if (alert.kind == AlertKind.CALL) BuiltInAnimations.DEFAULT_CONTACT else BuiltInAnimations.DEFAULT_DEVICE,
            )

    /** For the Charge toy: an imported animation by id, or null (unset, deleted, or not an import). */
    fun importedAnimation(id: String): GlyphAnimation? = library.importedOnly(id, current.imports)

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
        const val PREVIEW_ID = "preview:studio"
        @Volatile private var instance: AlertsRuntime? = null

        fun get(context: Context): AlertsRuntime =
            instance ?: synchronized(this) { instance ?: AlertsRuntime(context.applicationContext).also { instance = it } }

        fun now(): Long = SystemClock.elapsedRealtime()
    }
}
