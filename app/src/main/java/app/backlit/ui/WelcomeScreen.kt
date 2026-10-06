package app.backlit.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.glyph.ToysManager
import app.backlit.ui.components.BacklitLogo

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    val canOpen = remember { ToysManager.canOpen(context) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        BacklitLogo(96.dp)
        Text("BACKLIT", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
        Text("Toys and tools for your Glyph Matrix.", style = MaterialTheme.typography.bodyLarge, color = BacklitColors.Dim, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(32.dp))
        Column(
            Modifier.fillMaxWidth().border(1.dp, BacklitColors.Line, RoundedCornerShape(14.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("01  Settings → Glyph Interface → Glyph Toys", style = MaterialTheme.typography.bodyLarge)
            Text("02  Turn on the Backlit toys you want", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(20.dp))
        if (canOpen) {
            SquareChip("OPEN GLYPH TOYS", selected = false, onClick = { ToysManager.open(context) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        SquareChip("GET STARTED", selected = true, onClick = onStart, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
    }
}
