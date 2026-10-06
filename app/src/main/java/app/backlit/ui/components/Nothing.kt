package app.backlit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.backlit.render.LogoGeometry
import app.backlit.render.PixelGrid
import app.backlit.ui.BacklitColors
import app.backlit.ui.MatrixPreview

private val CardShape = RoundedCornerShape(14.dp)

/** The Diamond-ring logo, drawn from the same geometry as the launcher icon (cropped to its 18..90 window). */
@Composable
fun BacklitLogo(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 72f
        for (d in LogoGeometry.dots) {
            val c = if (d.red) BacklitColors.Red else BacklitColors.White.copy(alpha = d.alpha.toFloat())
            drawCircle(c, radius = (d.r * s).toFloat(), center = Offset(((d.x - 18) * s).toFloat(), ((d.y - 18) * s).toFloat()))
        }
    }
}

/** "● ON GLYPH" (white), "○ TURN ON" (grey; tappable when [onTurnOn] is given) or "PREVIEW" on phones without a matrix. */
@Composable
fun StatusPill(setUp: Boolean, supported: Boolean = true, onTurnOn: (() -> Unit)? = null) {
    val label = when { !supported -> "PREVIEW"; setUp -> "● ON GLYPH"; else -> "○ TURN ON" }
    val m = if (supported && !setUp && onTurnOn != null) Modifier.clickable(onClick = onTurnOn) else Modifier
    Text(label, style = MaterialTheme.typography.labelSmall, color = if (supported && setUp) BacklitColors.White else BacklitColors.Dim, modifier = m)
}

@Composable
fun ToyCard(grid: PixelGrid, name: String, setUp: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, supported: Boolean = true) {
    Column(
        modifier.clip(CardShape).border(1.dp, BacklitColors.Line, CardShape).background(BacklitColors.Black)
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        MatrixPreview(grid, Modifier.fillMaxWidth())
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        StatusPill(setUp, supported)
    }
}

@Composable
fun ToolCard(grid: PixelGrid, name: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CardShape).border(1.dp, BacklitColors.Line, CardShape).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MatrixPreview(grid, Modifier.size(44.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        }
    }
}

/** Toy page header: ← GLYPH TOYS on the left, the toy's status on the right. */
@Composable
fun ToyHeader(onBack: () -> Unit, setUp: Boolean, supported: Boolean, onTurnOn: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("←", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.minimumInteractiveComponentSize().clickable(onClick = onBack).padding(end = 10.dp))
        Text("GLYPH TOYS", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        Spacer(Modifier.weight(1f))
        StatusPill(setUp, supported, onTurnOn)
    }
}

@Composable
fun PagerDots(count: Int, index: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(Modifier.padding(horizontal = 3.dp).size(6.dp).background(if (i == index) BacklitColors.White else BacklitColors.Line, CircleShape))
        }
    }
}

@Composable
fun Section(label: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

/** Header for full pages (Studio, Alerts, Settings…): ← and a Doto title. */
@Composable
fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("←", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.minimumInteractiveComponentSize().clickable(onClick = onBack).padding(end = 12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(4.dp))
    }
}
