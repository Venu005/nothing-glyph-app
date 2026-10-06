package app.backlit.ui

import app.backlit.ui.components.Notice
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay

@Composable
fun SandTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val shape = HourglassShape.forSize(if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(200); now = System.currentTimeMillis() } }
    val st = TimerState.decode(settings.sandTimer).tick(now)
    val presets = settings.sandPresets
    val busy = st.phase == Phase.RUNNING || st.phase == Phase.PAUSED

    if (profile != DeviceProfile.UNSUPPORTED && !settings.sandToyEverBound) {
        Notice("Turn on Backlit Sand in Glyph Toys (Settings → Glyph Interface → Glyph Toys), then flip your phone over to start.")
    }

    MatrixPreview(SandArt.still(shape, st, now), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
    Text(
        when (st.phase) {
            Phase.READY -> "READY · ${st.durationMs / TimerState.MIN} MIN · FLIP TO START"
            Phase.RUNNING -> "RUNNING · ${TimerState.clock(st.timeLeft(now))} LEFT"
            Phase.PAUSED -> "PAUSED ON ITS SIDE · ${TimerState.clock(st.timeLeft(now))} LEFT"
            Phase.DONE -> "TIME'S UP · FLIP TO GO AGAIN"
        },
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )

    var editing by remember { mutableStateOf(false) }
    Text("TIMES", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEachIndexed { i, m ->
            val selected = !editing && !busy && i == st.presetIndex.coerceIn(0, presets.size - 1) && st.durationMs == m * TimerState.MIN
            SquareChip(if (editing) "$m ×" else "$m MIN", selected, {
                if (editing) {
                    if (presets.size > 1) onUpdate { it.copy(sandPresets = SettingsRepo.cleanPresets(it.sandPresets - m)) }
                } else if (!busy) {
                    onUpdate { it.copy(sandTimer = st.select(i, presets).encode()) }
                }
            })
        }
    }
    Text(
        if (editing) "Tap a time to remove it." else if (busy) "A timer is running. Long press the Glyph twice to change it." else "Tap a time to pick it, or long press the Glyph button.",
        style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(top = 4.dp),
    )
    SettingRow("Edit times", if (editing) "DONE" else "→") { editing = !editing }
    if (editing) {
        var add by remember { mutableIntStateOf(15) }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ADD", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.weight(1f))
            SquareChip("−", false, { add = (add - 1).coerceAtLeast(1) })
            Text("$add MIN", style = MaterialTheme.typography.titleMedium)
            SquareChip("+", false, { add = (add + 1).coerceAtMost(99) })
            SquareChip("ADD", presets.size < 8 && add !in presets, {
                if (presets.size < 8) onUpdate { it.copy(sandPresets = SettingsRepo.cleanPresets(it.sandPresets + add)) }
            })
        }
    }

    Text("WHEN TIME'S UP", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("glyph" to "GLYPH ONLY", "vibrate" to "VIBRATE", "chime" to "+ CHIME").forEach { (id, label) ->
            SquareChip(label, settings.sandAlert == id, { onUpdate { it.copy(sandAlert = id) } }, Modifier.weight(1f))
        }
    }

    val canExact = context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    val exactOn = settings.sandExact && canExact
    SettingRow("Ring exactly on time", if (exactOn) "ON" else "OFF") {
        if (exactOn) {
            onUpdate { it.copy(sandExact = false) }
            SandAlarm.sync(context, st, false)
        } else {
            onUpdate { it.copy(sandExact = true) }
            if (canExact) SandAlarm.sync(context, st, true)
            else context.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    Text(
        "Off: when the phone is asleep, Android may ring up to about a minute late. On: Android asks you once to allow alarms.",
        style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim,
    )

    var howOpen by remember { mutableStateOf(false) }
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
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }

    SquareChip("SHOW ON GLYPH", true, { runtime.preview(SandPreviewAnimation.RUNNING_ID, 4000L) }, Modifier.fillMaxWidth().padding(vertical = 12.dp))
    Spacer(Modifier.height(8.dp))
}
