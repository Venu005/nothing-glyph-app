package app.backlit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.data.LocationMode
import app.backlit.data.Settings
import app.backlit.glyph.ToysManager
import app.backlit.ui.components.PageHeader
import app.backlit.ui.components.Section
import app.backlit.ui.nav.Route

@Composable
fun SettingsScreen(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit, onOpen: (Route) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        PageHeader("SETTINGS", onBack)
        Section("GLYPH")
        BrightnessRow(settings.brightness) { pct -> onUpdate { it.copy(brightness = pct) } }
        SettingRow("Glyph Toys setup", "→") { if (!ToysManager.open(context)) onOpen(Route.Setup) }
        SettingRow("Setup steps", "→") { onOpen(Route.Setup) }
        Section("CLOCK")
        val where = when (settings.locationMode) { LocationMode.FIXED -> "06–18"; else -> settings.placeName ?: "—" }
        SettingRow("Sun times", "$where →") { onOpen(Route.Location) }
        Section("APP")
        SettingRow("Privacy policy", "→") { onOpen(Route.Privacy) }
        SettingRow("About", "→") { onOpen(Route.About) }
    }
}
