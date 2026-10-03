package app.backlit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.glyph.DeviceProfile
import app.backlit.ui.BacklitColors
import app.backlit.ui.BacklitTheme
import app.backlit.ui.HomeScreen
import app.backlit.ui.Screen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = SettingsRepo.get(this)
        val profile = DeviceProfile.detect()

        setContent {
            BacklitTheme {
                val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
                var screen by rememberSaveable { mutableStateOf<Screen?>(null) }
                val scope = rememberCoroutineScope()
                val update: ((Settings) -> Settings) -> Unit = { t -> scope.launch { repo.update(t) } }

                Box(Modifier.fillMaxSize().background(BacklitColors.Black).safeDrawingPadding()) {
                    val s = settings ?: return@Box
                    LaunchedEffect(Unit) {
                        if (screen == null) {
                            screen = if (s.toyEverBound || profile == DeviceProfile.UNSUPPORTED) Screen.HOME else Screen.SETUP
                        }
                    }
                    BackHandler(enabled = screen != Screen.HOME && screen != null) { screen = Screen.HOME }
                    when (screen) {
                        null, Screen.HOME -> HomeScreen(s, profile, update) { screen = it }
                        else -> Text("${screen} — coming in Tasks 10–11", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
