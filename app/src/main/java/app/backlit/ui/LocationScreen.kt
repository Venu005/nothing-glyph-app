package app.backlit.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.data.Cities
import app.backlit.data.LocationMode
import app.backlit.data.LocationSource
import app.backlit.data.Settings
import kotlinx.coroutines.launch

@Composable
fun LocationScreen(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember { LocationSource(context) }
    val cities = remember { runCatching { Cities.load(context.assets) }.getOrDefault(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var showCities by remember { mutableStateOf(settings.locationMode == LocationMode.CITY) }

    fun useApproximate() {
        status = "LOCATING…"
        scope.launch {
            val c = source.current()
            if (c == null) {
                status = "COULDN'T GET A LOCATION — TRY A CITY"
            } else {
                onUpdate {
                    it.copy(locationMode = LocationMode.APPROXIMATE, lat = c.lat, lon = c.lon,
                        placeName = "APPROX.", locationUpdatedAt = System.currentTimeMillis())
                }
                status = null
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useApproximate() else status = "PERMISSION DENIED — USING FIXED HOURS"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("SUN TIMES", onBack = onBack)
        Text("The day ring lights up the hours between sunrise and sunset.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))

        SettingRow("Fixed 06:00–18:00", if (settings.locationMode == LocationMode.FIXED) "●" else "") {
            onUpdate { it.copy(locationMode = LocationMode.FIXED) }
            showCities = false
        }
        SettingRow("Approximate location", if (settings.locationMode == LocationMode.APPROXIMATE) "●" else "") {
            showCities = false
            if (source.hasPermission()) useApproximate() else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        Text("Read on your phone only, rounded to about 1 km, and never sent anywhere.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        SettingRow(
            "Choose a city",
            if (settings.locationMode == LocationMode.CITY) (settings.placeName ?: "●") else "",
        ) { showCities = true }
        DashedDivider()

        status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = BacklitColors.Red, modifier = Modifier.padding(vertical = 10.dp)) }

        if (showCities) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search cities") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BacklitColors.White,
                    unfocusedBorderColor = BacklitColors.Line,
                    cursorColor = BacklitColors.White,
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            Cities.search(cities, query).forEach { city ->
                Text(
                    city.display,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUpdate {
                                it.copy(locationMode = LocationMode.CITY, lat = city.lat, lon = city.lon, placeName = city.display)
                            }
                            showCities = false
                            query = ""
                        }
                        .padding(vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
