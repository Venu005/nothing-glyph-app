package app.backlit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.backlit.data.LocationRefresher
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.importFromUri
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.glyph.DeviceProfile
import app.backlit.ui.AboutScreen
import app.backlit.ui.BacklitColors
import app.backlit.ui.BacklitTheme
import app.backlit.ui.EditorScreen
import app.backlit.ui.HomeScreen
import app.backlit.ui.LocationScreen
import app.backlit.ui.SetupScreen
import app.backlit.ui.ToyPagerScreen
import app.backlit.ui.StudioScreen
import app.backlit.ui.AlertsScreen
import app.backlit.ui.SettingsScreen
import app.backlit.ui.PrivacyScreen
import app.backlit.ui.WelcomeScreen
import app.backlit.ui.nav.Route
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        val repo = SettingsRepo.get(this)
        val profile = DeviceProfile.detect()

        setContent {
            BacklitTheme {
                val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
                var routeKey by rememberSaveable { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                val update: ((Settings) -> Settings) -> Unit = { t -> scope.launch { repo.update(t) } }

                Box(Modifier.fillMaxSize().background(BacklitColors.Black).safeDrawingPadding()) {
                    val s = settings ?: return@Box
                    LaunchedEffect(Unit) {
                        if (routeKey == null) routeKey = Route.start(s, profile != DeviceProfile.UNSUPPORTED).save()
                    }
                    LaunchedEffect(s.locationMode) {
                        LocationRefresher.refreshIfStale(this@MainActivity, repo, s, System.currentTimeMillis())
                    }
                    val route = routeKey?.let { Route.restore(it) } ?: return@Box
                    val go: (Route) -> Unit = { routeKey = it.save() }
                    val up: () -> Unit = {
                        if (route == Route.Welcome) update { it.copy(welcomeSeen = true) }
                        route.parent()?.let(go)
                    }
                    // The Editor handles Back itself (unsaved changes); everything else goes one level up.
                    BackHandler(enabled = route.parent() != null && route !is Route.Editor) { up() }
                    when (route) {
                        Route.Welcome -> WelcomeScreen(onStart = up)
                        Route.Home -> HomeScreen(s, profile, go)
                        is Route.Toy -> ToyPagerScreen(route.id, s, profile, update, go, onPage = { id -> if (id != route.id) go(Route.Toy(id)) }, onBack = up)
                        Route.Studio -> StudioScreen(s, profile, update, onEdit = { go(Route.Editor(it, Route.Studio)) }, onBack = up)
                        is Route.Editor -> EditorScreen(route.drawingId, profile, onClose = up, onSaved = { id -> go(Route.Editor(id, route.from)) })
                        Route.Alerts -> AlertsScreen(profile, onEdit = { go(Route.Editor(it, Route.Alerts)) }, onBack = up)
                        Route.Settings -> SettingsScreen(s, update, go, onBack = up)
                        is Route.Location -> LocationScreen(s, update, onBack = up)
                        Route.Privacy -> PrivacyScreen(onBack = up)
                        is Route.Setup -> SetupScreen(onDone = up)
                        Route.About -> AboutScreen(onBack = up)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: return
        val size = if (DeviceProfile.detect() == DeviceProfile.PHONE_4A_PRO) 13 else 25
        Toast.makeText(this, importFromUri(this, AlertsRuntime.get(this), uri, size), Toast.LENGTH_LONG).show()
    }
}
