package app.backlit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.alerts.AlertConfig
import app.backlit.alerts.AlertsRuntime
import app.backlit.data.DayLightResolver
import app.backlit.data.LocationMode
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.glyph.TickSchedule
import app.backlit.render.FaceContext
import app.backlit.render.Mode
import app.backlit.render.faces.DayRingFace
import app.backlit.render.faces.Faces
import app.backlit.studio.CanvasHint
import app.backlit.ui.components.BacklitLogo
import app.backlit.ui.components.Section
import app.backlit.ui.components.ToolCard
import app.backlit.ui.components.ToyCard
import app.backlit.ui.home.ToyCatalog
import app.backlit.ui.home.ToyThumbs
import app.backlit.ui.nav.Route
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(settings: Settings, profile: DeviceProfile, onOpen: (Route) -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val toys = remember(profile) { ToyCatalog.visible(hideMusic = profile == DeviceProfile.PHONE_4A_PRO) }
    val drawings by runtime.drawings.collectAsState(initial = emptyList())
    val config by runtime.config.collectAsState(initial = AlertConfig())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(50); now = System.currentTimeMillis() } }
    val zone = ZoneId.systemDefault()
    val canvasAnim = remember(settings.canvasDrawingId, drawings) {
        CanvasHint.pick(settings.canvasDrawingId, drawings.map { it.id })?.let { runtime.importedAnimation(it) }
    }
    val on = ToyCatalog.setUpCount(settings, toys)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BacklitLogo(28.dp)
            Spacer(Modifier.width(8.dp))
            Text("BACKLIT", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.weight(1f))
            Text("⚙", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable { onOpen(Route.Settings) }.padding(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
            val supported = profile != DeviceProfile.UNSUPPORTED
            Box(Modifier.size(6.dp).background(if (supported && on == 0) BacklitColors.Red else BacklitColors.White, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                if (supported) "LIVE ON MATRIX · ${profile.label} · $on OF ${toys.size} TOYS ON" else "THIS PHONE HAS NO GLYPH MATRIX · PREVIEW ONLY",
                style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim,
            )
        }

        Section("GLYPH TOYS")
        toys.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { id ->
                    ToyCard(
                        ToyThumbs.frame(id, settings, size, now, zone, canvasAnim), id.label, ToyCatalog.isSetUp(settings, id),
                        onClick = { onOpen(Route.Toy(id)) }, modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Section("TOOLS")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolCard(ToyThumbs.studio(size, now), "STUDIO", "${drawings.size} DRAWINGS", { onOpen(Route.Studio) }, Modifier.weight(1f))
            ToolCard(ToyThumbs.alerts(size, now), "ALERTS", "${config.contacts.size} CONTACTS · ${config.devices.size} DEVICES", { onOpen(Route.Alerts) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(32.dp))
    }
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
