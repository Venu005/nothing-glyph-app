package app.backlit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.backlit.glyph.ToysManager
import app.backlit.render.PixelGrid
import app.backlit.ui.components.ActionSpec
import app.backlit.ui.components.ActionStyle
import app.backlit.ui.components.BottomActionBar
import app.backlit.ui.components.HeroPreview
import app.backlit.ui.components.PagerDots
import app.backlit.ui.components.ToyHeader
import app.backlit.ui.toys.ToyAction

/** What the pager tells each toy page. [active]: this page has settled on screen (only it animates). */
data class PageChrome(
    val onBack: () -> Unit,
    val setUp: Boolean,
    val supported: Boolean,
    val onTurnOn: () -> Unit,
    val index: Int,
    val count: Int,
    val active: Boolean,
)

/** The shape of every toy page: header, hero, name, dots, status, content, and a fixed bottom bar. */
@Composable
fun ToyPageScaffold(
    chrome: PageChrome,
    name: String,
    hero: PixelGrid,
    status: String,
    action: ToyAction?,
    onShow: () -> Unit = {},
    onNewDrawing: () -> Unit = {},
    heroClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var barPx by remember { mutableIntStateOf(0) }
    val barDp = with(LocalDensity.current) { barPx.toDp() }
    val spec = when (action) {
        null -> null
        ToyAction.TurnOn -> ActionSpec("TURN ON IN GLYPH TOYS", ActionStyle.ATTENTION, chrome.onTurnOn)
        ToyAction.OpenGlyphToys -> ActionSpec("OPEN GLYPH TOYS", ActionStyle.OUTLINE) { if (!ToysManager.open(context)) chrome.onTurnOn() }
        ToyAction.NewDrawing -> ActionSpec("+ NEW DRAWING", ActionStyle.PRIMARY, onNewDrawing)
        is ToyAction.ShowOnGlyph -> ActionSpec("SHOW ON GLYPH", ActionStyle.PRIMARY, onShow)
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = barDp + 16.dp)) {
            ToyHeader(chrome.onBack, chrome.setUp, chrome.supported, chrome.onTurnOn)
            HeroPreview(hero, heroClick)
            Text(name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            PagerDots(chrome.count, chrome.index)
            Text(status, style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            content()
        }
        BottomActionBar(spec, onHeight = { barPx = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
