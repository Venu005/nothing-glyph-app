package app.backlit.ui

import app.backlit.ui.components.AttentionCard
import app.backlit.ui.components.ChipRow
import app.backlit.ui.components.Section
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.StatusInputs
import app.backlit.ui.toys.ToyAction
import app.backlit.ui.toys.ToyStatus
import java.time.ZoneId
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
fun MusicPage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome) {
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
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && chrome.active
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

    ToyPageScaffold(
        chrome, "MUSIC", grid,
        ToyStatus.line(ToyId.MUSIC, settings, 0, ZoneId.systemDefault(), StatusInputs(micGranted = granted, musicSupported = profile == DeviceProfile.PHONE_3)),
        ToyAction.of(ToyId.MUSIC, chrome.setUp, chrome.supported, hasDrawing = false),
    ) {
        Text(if (live) "LIVE · REACTING TO YOUR MUSIC" else "DEMO AUDIO", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        if (profile == DeviceProfile.PHONE_3 && !granted) {
            if (!denied) AttentionCard("Let Backlit react to your music. Android calls this the microphone permission, but Backlit only reads what your phone is already playing. Nothing is recorded.") {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            } else AttentionCard("Music reactions are off: the toy shows a calm line. Tap to open settings.") {
                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Section("STYLE")
        ChipRow(VizStyles.ids.map { it to VizStyles.label(it) }, VizStyles.normalize(settings.musicStyle)) { id -> onUpdate { it.copy(musicStyle = id) } }
        Section("SENSITIVITY")
        ChipRow(Sensitivity.entries.map { it.name to it.name }, settings.musicSensitivity.name) { n -> onUpdate { it.copy(musicSensitivity = Sensitivity.valueOf(n)) } }
    }
}
