package app.backlit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.ui.components.PageHeader

@Composable
fun AlertsScreen(profile: DeviceProfile, onEdit: (String?) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        PageHeader("ALERTS", onBack)
        AlertsTab(profile, onEdit)
    }
}
