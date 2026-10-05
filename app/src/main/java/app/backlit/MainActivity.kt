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
import androidx.compose.runtime.mutableIntStateOf
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
import app.backlit.ui.Screen
import app.backlit.ui.SetupScreen
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
                var screen by rememberSaveable { mutableStateOf<Screen?>(null) }
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var editingId by rememberSaveable { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()
                val update: ((Settings) -> Settings) -> Unit = { t -> scope.launch { repo.update(t) } }

                Box(Modifier.fillMaxSize().background(BacklitColors.Black).safeDrawingPadding()) {
                    val s = settings ?: return@Box
                    LaunchedEffect(Unit) {
                        if (screen == null) {
                            screen = if (s.toyEverBound || profile == DeviceProfile.UNSUPPORTED) Screen.HOME else Screen.SETUP
                        }
                    }
                    LaunchedEffect(s.locationMode) {
                        LocationRefresher.refreshIfStale(this@MainActivity, repo, s, System.currentTimeMillis())
                    }
                    BackHandler(enabled = screen != Screen.HOME && screen != Screen.EDITOR && screen != null) { screen = Screen.HOME }
                    when (screen) {
                        null, Screen.HOME -> HomeScreen(s, profile, tab, { tab = it }, update, { screen = it }) { id ->
                            editingId = id
                            screen = Screen.EDITOR
                        }
                        Screen.SETUP -> SetupScreen(onDone = { screen = Screen.HOME })
                        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.HOME })
                        Screen.LOCATION -> LocationScreen(s, update, onBack = { screen = Screen.HOME })
                        Screen.EDITOR -> EditorScreen(editingId, profile, onClose = { screen = Screen.HOME }, onSaved = { editingId = it })
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
