package app.backlit.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
            ToyId.CANVAS -> CanvasPage(settings, profile, onUpdate, chrome, onEdit = { onOpen(Route.Editor(it, Route.Toy(ToyId.CANVAS))) }, onOpenStudio = { onOpen(Route.Studio()) })
            ToyId.PET -> PetPage(settings, profile, onUpdate, chrome)
            ToyId.SAND -> SandPage(settings, profile, onUpdate, chrome)
            ToyId.BADGE -> BadgePage(settings, profile, onUpdate, chrome)
        }
    }
}
