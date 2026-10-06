package app.backlit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.backlit.render.PixelGrid
import app.backlit.ui.BacklitColors
import app.backlit.ui.MatrixPreview
import kotlinx.coroutines.delay

private val Pill = RoundedCornerShape(50)
private val Card = RoundedCornerShape(14.dp)

/** Wall-clock ms, refreshed every [periodMs] while [active] and the screen is at least STARTED. */
@Composable
fun rememberTicker(periodMs: Long, active: Boolean = true): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, periodMs) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(periodMs) }
        }
    }
    return now
}

@Composable
private fun PillText(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(Pill).background(if (selected) BacklitColors.White else BacklitColors.Black)
            .border(1.dp, if (selected) BacklitColors.White else BacklitColors.Line, Pill)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) BacklitColors.Black else BacklitColors.White, maxLines = 1)
    }
}

/** Single-choice pills; scrolls sideways when they don't fit. */
@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (id, label) -> PillText(label, id == selected, { onSelect(id) }) }
    }
}

/** Equal-width pills across the screen (Alerts segments). */
@Composable
fun SegmentedControl(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (id, label) -> PillText(label, id == selected, { onSelect(id) }, Modifier.weight(1f)) }
    }
}

@Composable
fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) = PillText(label, false, onClick, modifier)

enum class ActionStyle { PRIMARY, OUTLINE, ATTENTION }

data class ActionSpec(val label: String, val style: ActionStyle, val onClick: () -> Unit)

@Composable
private fun ActionButton(spec: ActionSpec, modifier: Modifier) {
    val bg = if (spec.style == ActionStyle.PRIMARY) BacklitColors.White else BacklitColors.Black
    val border = when (spec.style) { ActionStyle.PRIMARY -> BacklitColors.White; ActionStyle.OUTLINE -> BacklitColors.Line; ActionStyle.ATTENTION -> BacklitColors.Red }
    Row(
        modifier.clip(Pill).background(bg).border(1.dp, border, Pill).clickable(onClick = spec.onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spec.style == ActionStyle.ATTENTION) {
            Box(Modifier.size(6.dp).background(BacklitColors.Red, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(spec.label, style = MaterialTheme.typography.labelSmall, color = if (spec.style == ActionStyle.PRIMARY) BacklitColors.Black else BacklitColors.White)
    }
}

/** The fixed bar at the bottom of a page; reports its height so the page can pad its content above it. */
@Composable
fun BottomActionBar(primary: ActionSpec?, secondary: ActionSpec? = null, onHeight: (Int) -> Unit = {}, modifier: Modifier = Modifier) {
    if (primary == null && secondary == null) return
    Column(modifier.fillMaxWidth().background(BacklitColors.Black).onSizeChanged { onHeight(it.height) }) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BacklitColors.Line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            secondary?.let { ActionButton(it, Modifier.weight(1f)) }
            primary?.let { ActionButton(it, Modifier.weight(2f)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BacklitColors.Black,
        contentColor = BacklitColors.White,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(width = 36.dp, height = 4.dp).background(BacklitColors.Line, Pill)) },
    ) {
        // Scrolls, so long galleries and their actions (REMOVE, SAVE) stay reachable, also above the keyboard.
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 10.dp))
            content()
        }
    }
}

@Composable
fun SheetAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) BacklitColors.Red else BacklitColors.White, modifier = Modifier.padding(vertical = 14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(BacklitColors.Line))
    }
}

@Composable
fun GalleryCard(grid: PixelGrid, name: String, subtitle: String, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(Card).border(1.dp, if (highlighted) BacklitColors.White else BacklitColors.Line, Card).clickable(onClick = onClick).padding(8.dp)) {
        MatrixPreview(grid, Modifier.fillMaxWidth())
        Text(name.uppercase(), style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = if (highlighted) BacklitColors.White else BacklitColors.Dim)
    }
}

@Composable
fun AttentionCard(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(Card).border(1.dp, BacklitColors.Red, Card).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(BacklitColors.Red, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("→", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun HeroPreview(grid: PixelGrid, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
        val m = Modifier.fillMaxWidth(0.62f)
        MatrixPreview(grid, if (onClick != null) m.clickable(onClick = onClick) else m)
    }
}

/** A bordered note (kept for the few places that still need a paragraph, e.g. import results). */
@Composable
fun Notice(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Card).border(1.dp, BacklitColors.Line, Card).padding(12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CenterNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = BacklitColors.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
}
