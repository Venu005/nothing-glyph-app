package app.backlit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.backlit.data.DayLightResolver
import app.backlit.data.LocationMode
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.glyph.TickSchedule
import app.backlit.render.FaceContext
import app.backlit.render.Mode
import app.backlit.render.faces.DayRingFace
import app.backlit.render.faces.Faces
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId

enum class Screen { HOME, SETUP, LOCATION, ABOUT }

@Composable
fun HomeScreen(
    settings: Settings,
    profile: DeviceProfile,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Text("BACKLIT", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
            val supported = profile != DeviceProfile.UNSUPPORTED
            Box(Modifier.size(7.dp).background(if (supported) BacklitColors.Red else BacklitColors.Dim, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                if (supported) "LIVE ON MATRIX · ${profile.label}" else "THIS PHONE HAS NO GLYPH MATRIX · PREVIEW ONLY",
                style = MaterialTheme.typography.labelSmall,
                color = BacklitColors.Dim,
            )
        }

        if (profile != DeviceProfile.UNSUPPORTED && !settings.toyEverBound) {
            Box(
                Modifier.fillMaxWidth().border(1.dp, BacklitColors.Red).clickable { onNavigate(Screen.SETUP) }.padding(12.dp),
            ) {
                Text("TOY NOT SET UP YET — TAP TO SET UP →", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SquareChip("CLOCK", selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.weight(1f))
            SquareChip("MUSIC", selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.weight(1f))
            SquareChip("ALERTS", selected = tab == 2, onClick = { tab = 2 }, modifier = Modifier.weight(1f))
            SquareChip("CHARGE", selected = tab == 3, onClick = { tab = 3 }, modifier = Modifier.weight(1f))
        }

        when (tab) {
            0 -> ClockTab(settings, profile, onUpdate, onNavigate)
            1 -> MusicTab(settings, profile, onUpdate)
            2 -> AlertsTab(profile)
            else -> ChargeTab(settings, profile, onUpdate)
        }

        SettingRow("Glyph Toy setup", "→") { onNavigate(Screen.SETUP) }
        SettingRow("About", "→") { onNavigate(Screen.ABOUT) }
        DashedDivider()
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ClockTab(
    settings: Settings,
    profile: DeviceProfile,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(TickSchedule.delayToNextTick(System.currentTimeMillis(), perSecond = true))
            value = LocalDateTime.now()
        }
    }
    var previewSize by rememberSaveable { mutableIntStateOf(profile.size) }
    val resolver = remember { DayLightResolver() }
    val face = Faces.byId(settings.faceId)
    val ctx = FaceContext(
        hour = now.hour, minute = now.minute, second = now.second,
        size = previewSize,
        mode = if (previewSize == 13) Mode.AOD else Mode.ACTIVE,
        options = settings.faceOptions,
        dayLight = resolver.resolve(settings, now.toLocalDate(), ZoneId.systemDefault()),
    )
    val grid = face.render(ctx)

    MatrixPreview(grid, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            listOf(25, 13).joinToString("   ") { if (it == previewSize) "[${it}×$it]" else "${it}×$it" },
            style = MaterialTheme.typography.labelSmall,
            color = BacklitColors.Dim,
            modifier = Modifier.clickable { previewSize = if (previewSize == 25) 13 else 25 }.padding(8.dp),
        )
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Faces.all.forEach { f ->
            SquareChip(f.label, selected = f.id == face.id, onClick = { onUpdate { it.copy(faceId = f.id) } }, modifier = Modifier.weight(1f))
        }
    }

    if (face.id == "analog") {
        SettingRow("Second hand", if (settings.secondHand) "ON" else "OFF") {
            onUpdate { it.copy(secondHand = !it.secondHand) }
        }
    }
    if (face.id == DayRingFace.id) {
        // Only faces that show digits have a time format; the analog face has none.
        SettingRow("Time format", if (settings.use24h) "24H" else "12H") {
            onUpdate { it.copy(use24h = !it.use24h) }
        }
        val where = when (settings.locationMode) {
            LocationMode.FIXED -> "06–18"
            else -> settings.placeName ?: "—"
        }
        SettingRow("Sun times", "$where →") { onNavigate(Screen.LOCATION) }
    }
    BrightnessRow(settings.brightness) { pct -> onUpdate { it.copy(brightness = pct) } }
}

@Composable
internal fun BrightnessRow(value: Int, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    DashedDivider()
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Brightness", style = MaterialTheme.typography.bodyLarge)
            Text("${local.toInt()}%", style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.toInt()) },
            valueRange = 20f..100f,
            steps = 7,
            colors = SliderDefaults.colors(
                thumbColor = BacklitColors.White,
                activeTrackColor = BacklitColors.White,
                inactiveTrackColor = BacklitColors.Line,
            ),
        )
    }
}
