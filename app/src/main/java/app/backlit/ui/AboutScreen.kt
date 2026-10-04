package app.backlit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("ABOUT", onBack = onBack)
        SettingRow("Version", version)
        DashedDivider()
        Text(
            "Backlit collects no data. Everything, including your location if you share it, stays on your phone. " +
                "Music is analysed on the phone in real time and is never recorded, stored or shared. " +
                "Caller names are only compared with the contacts you pick; nothing from your notifications is kept. " +
                "The app makes no network requests.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 14.dp),
        )
        DashedDivider()
        Text("LICENSES", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        listOf(
            "Doto — SIL Open Font License 1.1",
            "Space Grotesk — SIL Open Font License 1.1",
            "City data — GeoNames (geonames.org), CC BY 4.0",
            "Glyph Matrix SDK — Nothing Technology Ltd.",
        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp)) }
        Spacer(Modifier.height(32.dp))
    }
}
