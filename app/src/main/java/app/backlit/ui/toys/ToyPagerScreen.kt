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
        val chrome = PageChrome(
            onBack = onBack,
            setUp = ToyCatalog.isSetUp(settings, id),
            supported = profile != DeviceProfile.UNSUPPORTED,
            onTurnOn = { if (!ToysManager.open(context)) onOpen(Route.Setup(Route.Toy(id))) },
            index = page, count = toys.size,
            active = pager.settledPage == page,
        )
        when (id) {
            ToyId.CLOCK -> ClockPage(settings, profile, onUpdate, chrome) { onOpen(Route.Location(Route.Toy(ToyId.CLOCK))) }
            ToyId.MUSIC -> MusicPage(settings, profile, onUpdate, chrome)
            ToyId.CHARGE -> ChargePage(settings, profile, onUpdate, chrome)
            ToyId.CANVAS -> CanvasPage(settings, profile, onUpdate, chrome, onEdit = { onOpen(Route.Editor(it, Route.Toy(ToyId.CANVAS))) }, onOpenStudio = { onOpen(Route.Studio) })
            ToyId.PET -> PetTab(settings, profile, onUpdate)
            ToyId.SAND -> SandTab(settings, profile, onUpdate)
            ToyId.BADGE -> BadgeTab(settings, profile, onUpdate)
        }
    }
}
