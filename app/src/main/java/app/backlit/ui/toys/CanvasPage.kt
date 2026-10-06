package app.backlit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.backlit.alerts.AlertsRuntime
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.studio.CanvasHint
import app.backlit.ui.components.CenterNote
import app.backlit.ui.components.GalleryCard
import app.backlit.ui.components.OptionSheet
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.StatusInputs
import app.backlit.ui.toys.ToyAction
import app.backlit.ui.toys.ToyStatus
import java.time.ZoneId

@Composable
fun CanvasPage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome, onEdit: (String?) -> Unit, onOpenStudio: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val drawings by runtime.drawings.collectAsStateWithLifecycle(initialValue = emptyList())
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val chosen = CanvasHint.pick(settings.canvasDrawingId, drawings.map { it.id })
    val entry = drawings.firstOrNull { it.id == chosen }
    val anim = remember(entry) { entry?.let { runtime.library.load(it) } }
    var picking by rememberSaveable { mutableStateOf(false) }
    val now = rememberTicker(50, chrome.active)
    val action = ToyAction.of(ToyId.CANVAS, chrome.setUp, chrome.supported, hasDrawing = entry != null)

    ToyPageScaffold(
        chrome, "CANVAS", anim?.frame(size, now) ?: CanvasHint.frame(size),
        ToyStatus.line(ToyId.CANVAS, settings, now, ZoneId.systemDefault(), StatusInputs(drawingName = entry?.name)), action,
        onShow = { anim?.let { runtime.previewAnimation(it, it.loopMs.coerceIn(3000L, 10_000L)) } },
        onNewDrawing = { onEdit(null) },
    ) {
        SettingRow("Drawing", "${(entry?.name ?: "None").uppercase()} →") { picking = true }
        SettingRow("Open Studio", "→") { onOpenStudio() }
    }

    if (picking) OptionSheet("SHOW ON CANVAS", { picking = false }) {
        if (drawings.isEmpty()) CenterNote("No drawings yet. Make one in Studio.")
        drawings.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { d ->
                    val a = remember(d) { runtime.library.load(d) }
                    GalleryCard(a?.frame(size, now) ?: CanvasHint.frame(size), d.name, if (d.id == chosen) "● ON CANVAS" else "", d.id == chosen,
                        { onUpdate { it.copy(canvasDrawingId = d.id) }; picking = false }, Modifier.weight(1f).padding(bottom = 8.dp))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
