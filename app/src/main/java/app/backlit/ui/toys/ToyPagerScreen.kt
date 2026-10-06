package app.backlit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.glyph.ToysManager
import app.backlit.ui.components.PagerDots
import app.backlit.ui.components.ToyHeader
import app.backlit.ui.home.ToyCatalog
import app.backlit.ui.home.ToyId
import app.backlit.ui.nav.Route

/** One page per toy, swipeable. Part 1: the new header, name and dots above each toy's existing settings body. */
@Composable
fun ToyPagerScreen(
    start: ToyId,
    settings: Settings,
    profile: DeviceProfile,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onOpen: (Route) -> Unit,
    onPage: (ToyId) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val toys = remember(profile) { ToyCatalog.visible(hideMusic = profile == DeviceProfile.PHONE_4A_PRO) }
    val pager = rememberPagerState(initialPage = toys.indexOf(start).coerceAtLeast(0)) { toys.size }
    LaunchedEffect(pager.settledPage) { onPage(toys[pager.settledPage]) }

    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { toys[it].key }) { page ->
        val id = toys[page]
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            ToyHeader(onBack, ToyCatalog.isSetUp(settings, id), profile != DeviceProfile.UNSUPPORTED, onTurnOn = { if (!ToysManager.open(context)) onOpen(Route.Setup(Route.Toy(id))) })
            Text(id.label, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            PagerDots(toys.size, page)
            when (id) {
                ToyId.CLOCK -> ClockTab(settings, profile, onUpdate) { onOpen(Route.Location(Route.Toy(ToyId.CLOCK))) }
                ToyId.MUSIC -> MusicTab(settings, profile, onUpdate)
                ToyId.CHARGE -> ChargeTab(settings, profile, onUpdate)
                ToyId.CANVAS -> StudioTab(settings, profile, onUpdate) { onOpen(Route.Editor(it, Route.Toy(ToyId.CANVAS))) }
                ToyId.PET -> PetTab(settings, profile, onUpdate)
                ToyId.SAND -> SandTab(settings, profile, onUpdate)
                ToyId.BADGE -> BadgeTab(settings, profile, onUpdate)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
