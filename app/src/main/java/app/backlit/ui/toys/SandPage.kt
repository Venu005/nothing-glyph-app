package app.backlit.ui

import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.LaunchedEffect
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.alerts.AlertsRuntime
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.glyph.DeviceProfile
import app.backlit.sand.HourglassShape
import app.backlit.sand.Phase
import app.backlit.sand.SandAlarm
import app.backlit.sand.SandArt
import app.backlit.sand.SandPreviewAnimation
import app.backlit.sand.TimerState
import app.backlit.ui.components.ChipRow
import app.backlit.ui.components.PillButton
import app.backlit.ui.components.Section
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.StatusInputs
import app.backlit.ui.toys.ToyAction
import app.backlit.ui.toys.ToyStatus
import java.time.ZoneId

@Composable
fun SandPage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val shape = HourglassShape.forSize(if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25)
    val now = rememberTicker(200, chrome.active)
    val st = TimerState.decode(settings.sandTimer).tick(now)
    val presets = settings.sandPresets
    val busy = st.phase == Phase.RUNNING || st.phase == Phase.PAUSED
    var editing by rememberSaveable { mutableStateOf(false) }
    var howOpen by rememberSaveable { mutableStateOf(false) }
    val canExact = context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    val exactOn = settings.sandExact && canExact
    // Coming back from system settings: re-arm the timer's alarm with the access Android now gives us.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleState.isAtLeast(Lifecycle.State.RESUMED), canExact) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && st.phase == Phase.RUNNING) SandAlarm.sync(context, st, settings.sandExact && canExact)
    }

    ToyPageScaffold(
        chrome, "SAND", SandArt.still(shape, st, now),
        ToyStatus.line(ToyId.SAND, settings, now, ZoneId.systemDefault(), StatusInputs()),
        ToyAction.of(ToyId.SAND, chrome.setUp, chrome.supported, hasDrawing = false),
        onShow = { runtime.preview(SandPreviewAnimation.RUNNING_ID, 4000L) },
    ) {
        Section("TIMES")
        if (editing) {
            ChipRow(presets.map { it.toString() to "$it ×" }, "") { m -> if (presets.size > 1) onUpdate { it.copy(sandPresets = SettingsRepo.cleanPresets(it.sandPresets - m.toInt())) } }
            var add by remember { mutableIntStateOf(15) }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                PillButton("−", { add = (add - 1).coerceAtLeast(1) })
                Text("$add MIN", style = MaterialTheme.typography.titleMedium)
                PillButton("+", { add = (add + 1).coerceAtMost(99) })
                PillButton("ADD", { if (presets.size < 8) onUpdate { it.copy(sandPresets = SettingsRepo.cleanPresets(it.sandPresets + add)) } })
            }
        } else {
            val selected = if (busy) "" else presets.getOrNull(st.presetIndex.coerceIn(0, presets.size - 1))?.takeIf { it * TimerState.MIN == st.durationMs }?.toString() ?: ""
            ChipRow(presets.map { it.toString() to "$it MIN" }, selected) { m ->
                if (!busy) onUpdate { it.copy(sandTimer = st.select(presets.indexOf(m.toInt()), presets).encode()) }
            }
        }
        SettingRow("Edit times", if (editing) "DONE" else "→") { editing = !editing }
        Text(if (busy) "A timer is running. Long press the Glyph twice to change it." else "Tap a time, or long press the Glyph button.",
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        Section("WHEN TIME'S UP")
        ChipRow(listOf("glyph" to "GLYPH ONLY", "vibrate" to "VIBRATE", "chime" to "+ CHIME"), settings.sandAlert) { id -> onUpdate { it.copy(sandAlert = id) } }
        SettingRow("Ring exactly on time", if (exactOn) "ON" else "OFF") {
            if (exactOn) { onUpdate { it.copy(sandExact = false) }; SandAlarm.sync(context, st, false) }
            else {
                onUpdate { it.copy(sandExact = true) }
                if (canExact) SandAlarm.sync(context, st, true)
                else context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Text("Off: when the phone is asleep, Android may ring up to about a minute late. On: Android asks you once to allow alarms.",
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        SettingRow("How it works", if (howOpen) "−" else "+") { howOpen = !howOpen }
        if (howOpen) {
            Text(
                listOf(
                    "⟲ Flip the phone over to start, like a real hourglass",
                    "⟲ Flip it mid-way and the sand runs back: time left becomes time run",
                    "↔ Lay it on its side to pause",
                    "▭ Face-down on a desk keeps it running",
                    "● Long press: pick a time. While it runs, the first press shows the minutes left; press again within 2 s to change it",
                ).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}
