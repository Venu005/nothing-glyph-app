package app.backlit.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.backlit.audio.DemoAudio
import app.backlit.audio.MusicActivity
import app.backlit.audio.MusicEngine
import app.backlit.audio.OutputVisualizer
import app.backlit.audio.hasAudioPermission
import app.backlit.data.Sensitivity
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.render.PixelGrid
import app.backlit.render.viz.VizStyles
import kotlinx.coroutines.delay

@Composable
fun MusicTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }

    val current by rememberUpdatedState(settings)
    val engine = remember { MusicEngine(25) }
    val music = remember { MusicActivity(context) }
    // Only read audio and animate while this screen is in the foreground; leaving the app releases the Visualizer.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    // Re-check on every resume: the user may have granted the permission in system Settings.
    LaunchedEffect(resumed) {
        if (resumed) {
            granted = hasAudioPermission(context)
            if (granted) denied = false
        }
    }
    val viz = remember(granted, resumed) { OutputVisualizer().also { if (granted && resumed) it.start() } }
    DisposableEffect(viz) { onDispose { viz.release() } }
    var grid by remember { mutableStateOf(PixelGrid(25)) }
    var live by remember { mutableStateOf(false) }

    LaunchedEffect(settings.musicStyle) { engine.setStyle(settings.musicStyle) }
    LaunchedEffect(viz, resumed) {
        if (!resumed) return@LaunchedEffect
        val start = SystemClock.elapsedRealtime()
        var last = start
        while (true) {
            delay(50)
            val now = SystemClock.elapsedRealtime()
            val dt = now - last
            last = now
            live = viz.isActive && music.isPlaying()
            grid = if (live) {
                engine.tick(now, dt, true, viz.readFft(), viz.samplingRateHz, current.musicSensitivity.gain, true)
            } else {
                engine.tickDemo(dt, DemoAudio.frame(now - start))
            }
        }
    }

    MatrixPreview(grid, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
    Text(
        if (live) "LIVE · REACTING TO YOUR MUSIC" else "DEMO AUDIO",
        style = MaterialTheme.typography.labelSmall,
        color = BacklitColors.Dim,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    )

    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VizStyles.ids.forEach { id ->
            SquareChip(
                VizStyles.label(id),
                selected = VizStyles.normalize(settings.musicStyle) == id,
                onClick = { onUpdate { it.copy(musicStyle = id) } },
                modifier = Modifier.weight(1f),
            )
        }
    }

    when {
        profile != DeviceProfile.PHONE_3 -> Notice("The Music toy needs the Phone (3). The (4a) Pro only supports always-on toys.")
        !granted && !denied -> {
            Notice(
                "Let Backlit react to your music. Android files this under the microphone permission, but Backlit " +
                    "only reads the sound your phone is already playing. Nothing is recorded, stored, or sent anywhere.",
            )
            SquareChip("ALLOW", selected = true, onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        !granted -> {
            Notice("Music reactions are off. The toy will show a calm line instead.")
            SquareChip(
                "OPEN SETTINGS",
                selected = false,
                onClick = {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    SettingRow("Sensitivity", settings.musicSensitivity.name) {
        onUpdate { it.copy(musicSensitivity = Sensitivity.entries[(it.musicSensitivity.ordinal + 1) % Sensitivity.entries.size]) }
    }
    BrightnessRow(settings.brightness) { pct -> onUpdate { it.copy(brightness = pct) } }
}

@Composable
private fun Notice(text: String) {
    Column(Modifier.fillMaxWidth().border(1.dp, BacklitColors.Line).padding(12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(8.dp))
}
