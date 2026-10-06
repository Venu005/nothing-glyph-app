package app.backlit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.backlit.glyph.ToysManager

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val canOpen = remember { ToysManager.canOpen(context) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("SET UP", onBack = onDone)
        Text("Turn on the Backlit toys you want:", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        listOf(
            "01" to "Open Settings → Glyph Interface → Glyph Toys.",
            "02" to "Switch on the Backlit toys (Clock, Music, Charge, Canvas, Pet, Sand, Badge).",
            "03" to "Phone (3): press the Glyph Button on the back until the clock shows. Long press to change face.",
            "04" to "Phone (3): also switch on \"Backlit Music\" to see your music on the back.",
            "05" to "Phone (4a) Pro: choose Backlit Clock as the always-on toy.",
        ).forEach { (n, step) ->
            DashedDivider()
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(n, style = MaterialTheme.typography.titleMedium)
                Text(step, style = MaterialTheme.typography.bodyLarge)
            }
        }
        DashedDivider()
        Spacer(Modifier.height(20.dp))
        if (canOpen) {
            SquareChip("OPEN GLYPH TOYS", selected = true, onClick = { ToysManager.open(context) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        SquareChip("DONE", selected = false, onClick = onDone, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
    }
}
