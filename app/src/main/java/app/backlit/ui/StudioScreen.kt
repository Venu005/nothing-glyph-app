package app.backlit.ui

import app.backlit.ui.components.Notice
import app.backlit.ui.components.ActionSpec
import app.backlit.ui.components.ActionStyle
import app.backlit.ui.components.BottomActionBar
import app.backlit.ui.components.CenterNote
import app.backlit.ui.components.GalleryCard
import app.backlit.ui.components.OptionSheet
import app.backlit.ui.components.PageHeader
import app.backlit.ui.components.SheetAction
import app.backlit.ui.components.rememberTicker
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import app.backlit.render.PixelGrid
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.AnimIndexEntry
import app.backlit.alerts.importDrawingFromUri
import app.backlit.anim.ImportedAnimation
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.studio.CanvasHint
import app.backlit.studio.MAX_NAME
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StudioScreen(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, onEdit: (String?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val drawings by runtime.drawings.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val onCanvas = CanvasHint.pick(settings.canvasDrawingId, drawings.map { it.id })
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val now = rememberTicker(50)
    var barPx by remember { mutableIntStateOf(0) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { message = importDrawingFromUri(context, runtime, uri, size) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = with(LocalDensity.current) { barPx.toDp() } + 16.dp)) {
            PageHeader("STUDIO", onBack)
            Text("${drawings.size} DRAWINGS · TAP ONE FOR OPTIONS", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
            message?.let { Notice(it) }
            if (drawings.isEmpty()) CenterNote("Draw your own pictures and animations for the Glyph. Use them for calls, devices, charging, or on the Canvas toy.")
            drawings.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { e ->
                        val anim = remember(e) { runtime.library.load(e) }
                        val frames = (anim as? ImportedAnimation)?.durations?.size ?: 1
                        GalleryCard(anim?.frame(size, now) ?: PixelGrid(size), e.name, if (e.id == onCanvas) "● ON CANVAS" else "$frames FRAME" + (if (frames == 1) "" else "S"),
                            e.id == onCanvas, { openId = e.id; renaming = false; confirmDelete = false }, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        BottomActionBar(
            primary = ActionSpec("+ NEW DRAWING", ActionStyle.PRIMARY) { onEdit(null) },
            secondary = ActionSpec("IMPORT", ActionStyle.OUTLINE) { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onHeight = { barPx = it }, modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // The sheet looks its drawing up in the live list, so a drawing deleted elsewhere closes it instead of crashing.
    val open = drawings.firstOrNull { it.id == openId }
    if (openId != null && open == null) openId = null
    if (open != null) OptionSheet(open.name.uppercase(), { openId = null }) {
        val anim = remember(open) { runtime.library.load(open) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { MatrixPreview(anim?.frame(size, now) ?: PixelGrid(size), Modifier.fillMaxWidth(0.5f)) }
        when {
            renaming -> {
                var name by remember(open.id) { mutableStateOf(open.name) }
                OutlinedTextField(value = name, onValueChange = { name = it.take(MAX_NAME) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                SheetAction("SAVE") {
                    scope.launch { runtime.loadDrawing(open.id, size)?.let { d -> runtime.saveDrawing(d.copy(name = name.trim().ifBlank { open.name }), open.id) } }
                    renaming = false
                }
            }
            confirmDelete -> {
                Text("Delete \"${open.name}\"? Contacts, devices or charging that use it go back to their default.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                SheetAction("DELETE", danger = true) { runtime.deleteImport(open.id); openId = null }
                SheetAction("CANCEL") { confirmDelete = false }
            }
            else -> {
                SheetAction("EDIT") { openId = null; onEdit(open.id) }
                if (open.id != onCanvas) SheetAction("SHOW ON CANVAS") { onUpdate { it.copy(canvasDrawingId = open.id) }; openId = null }
                SheetAction("SHOW ON GLYPH") { anim?.let { runtime.previewAnimation(it, it.loopMs.coerceIn(3000L, 10_000L)) } }
                SheetAction("SHARE") { (anim as ImportedAnimation?)?.let { shareAnimation(context, it, open.name) } }
                SheetAction("RENAME") { renaming = true }
                SheetAction("DELETE", danger = true) { confirmDelete = true }
            }
        }
    }
}

/** A small looping preview of a stored animation (blank if it failed to load). */
@Composable
internal fun LoopingPreview(anim: app.backlit.anim.GlyphAnimation?, size: Int) {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(anim) {
        val start = System.currentTimeMillis()
        while (true) { delay(50); t = System.currentTimeMillis() - start }
    }
    MatrixPreview(anim?.frame(size, t) ?: app.backlit.render.PixelGrid(size), Modifier.fillMaxWidth().padding(2.dp))
}
