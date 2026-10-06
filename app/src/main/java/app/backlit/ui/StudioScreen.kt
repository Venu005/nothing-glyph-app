package app.backlit.ui

import app.backlit.ui.components.Notice
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
fun StudioTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, onEdit: (String?) -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val drawings by runtime.drawings.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<AnimIndexEntry?>(null) }
    var deleting by remember { mutableStateOf<AnimIndexEntry?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val onCanvas = CanvasHint.pick(settings.canvasDrawingId, drawings.map { it.id })

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { message = importDrawingFromUri(context, runtime, uri, size) }
    }

    if (profile != DeviceProfile.UNSUPPORTED && !settings.canvasToyEverBound) {
        Notice("Turn on Backlit Canvas in Glyph Toys (Settings → Glyph Interface → Glyph Toys) to show a drawing on the back.")
    }
    message?.let { Notice(it) }

    Text("MY DRAWINGS", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    if (drawings.isEmpty()) {
        Text("Draw your own pictures and animations for the Glyph. Use them for calls, devices, charging, or on the Canvas toy.",
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
    }
    drawings.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { e ->
                val anim = remember(e) { runtime.library.load(e) }
                Column(
                    Modifier.weight(1f).border(1.dp, if (expanded == e.id) BacklitColors.White else BacklitColors.Line)
                        .clickable { expanded = if (expanded == e.id) null else e.id }.padding(4.dp),
                ) {
                    LoopingPreview(anim, size)
                    Text(e.name.uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    if (e.id == onCanvas) Text("● ON CANVAS", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Red)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        val open = row.firstOrNull { it.id == expanded }
        if (open != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SquareChip("EDIT", true, { onEdit(open.id) }, Modifier.weight(1f))
                SquareChip("CANVAS", false, { onUpdate { it.copy(canvasDrawingId = open.id) } }, Modifier.weight(1f))
                SquareChip("SHARE", false, {
                    (runtime.library.load(open) as ImportedAnimation?)?.let { shareAnimation(context, it, open.name) }
                }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SquareChip("RENAME", false, { renaming = open }, Modifier.weight(1f))
                SquareChip("DELETE", false, { deleting = open }, Modifier.weight(1f))
            }
        }
    }

    SquareChip("+ NEW DRAWING", true, { onEdit(null) }, Modifier.fillMaxWidth().padding(top = 12.dp))
    SquareChip("IMPORT FROM GLYPH MUSEUM", false, {
        importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
    Spacer(Modifier.height(8.dp))

    renaming?.let { e ->
        var name by remember(e.id) { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(MAX_NAME) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runtime.loadDrawing(e.id, size)?.let { d -> runtime.saveDrawing(d.copy(name = name.trim().ifBlank { e.name }), e.id) }
                    }
                    renaming = null
                }) { Text("SAVE") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("CANCEL") } },
        )
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${e.name}\"?") },
            text = { Text("Contacts, devices or charging that use it go back to their default.") },
            confirmButton = { TextButton(onClick = { runtime.deleteImport(e.id); expanded = null; deleting = null }) { Text("DELETE") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("CANCEL") } },
        )
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
