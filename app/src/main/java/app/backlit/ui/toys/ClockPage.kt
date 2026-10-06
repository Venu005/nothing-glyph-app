package app.backlit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.backlit.data.DayLightResolver
import app.backlit.data.LocationMode
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.render.FaceContext
import app.backlit.render.Mode
import app.backlit.render.faces.DayRingFace
import app.backlit.render.faces.Faces
import app.backlit.ui.components.ChipRow
import app.backlit.ui.components.Section
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.StatusInputs
import app.backlit.ui.toys.ToyAction
import app.backlit.ui.toys.ToyStatus
import java.time.Instant
import java.time.ZoneId

@Composable
fun ClockPage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome, onOpenLocation: () -> Unit) {
    val nowMs = rememberTicker(1000, chrome.active)
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    var previewSize by rememberSaveable { mutableIntStateOf(profile.size) }
    val resolver = remember { DayLightResolver() }
    val face = Faces.byId(settings.faceId)
    val grid = face.render(
        FaceContext(
            hour = now.hour, minute = now.minute, second = now.second, size = previewSize,
            mode = if (previewSize == 13) Mode.AOD else Mode.ACTIVE, options = settings.faceOptions,
            dayLight = resolver.resolve(settings, now.toLocalDate(), zone),
        ),
    )
    ToyPageScaffold(
        chrome, "CLOCK", grid, ToyStatus.line(ToyId.CLOCK, settings, nowMs, zone, StatusInputs()),
        ToyAction.of(ToyId.CLOCK, chrome.setUp, chrome.supported, hasDrawing = false),
    ) {
        Section("FACE")
        ChipRow(Faces.all.map { it.id to it.label.uppercase() }, face.id) { id -> onUpdate { it.copy(faceId = id) } }
        Section("PREVIEW")
        ChipRow(listOf("25" to "25 × 25", "13" to "13 × 13 ALWAYS-ON"), previewSize.toString()) { previewSize = it.toInt() }
        if (face.id == "analog") {
            SettingRow("Second hand", if (settings.secondHand) "ON" else "OFF") { onUpdate { it.copy(secondHand = !it.secondHand) } }
        }
        if (face.id == DayRingFace.id) {
            SettingRow("Time format", if (settings.use24h) "24H" else "12H") { onUpdate { it.copy(use24h = !it.use24h) } }
            val where = when (settings.locationMode) { LocationMode.FIXED -> "06–18"; else -> settings.placeName ?: "—" }
            SettingRow("Sun times", "$where →") { onOpenLocation() }
        }
    }
}
