package app.backlit.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.BuildConfig
import app.backlit.alerts.AlertsRuntime
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.glyph.DeviceProfile
import app.backlit.pet.Base
import app.backlit.pet.GhostArt
import app.backlit.pet.MoodState
import app.backlit.pet.PetBrain
import app.backlit.pet.PetInsight
import app.backlit.pet.PetPreviewAnimation
import app.backlit.pet.SleepWindow
import kotlinx.coroutines.delay
import java.time.ZoneId

@Composable
fun PetTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val sleep = SleepWindow(settings.petSleepStart, settings.petSleepEnd)

    // A local preview brain from the stored mood; taps pet it locally (no saved mood change).
    val brain = remember(settings.petMood, settings.petMoodAt, settings.petSleepStart, settings.petSleepEnd) {
        PetBrain(MoodState(settings.petMood.toDouble(), settings.petMoodAt), { sleep }, ZoneId.systemDefault())
    }
    LaunchedEffect(brain) {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), android.content.Context.RECEIVER_NOT_EXPORTED)
        if (i != null) {
            val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            brain.onCharging(i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0, lv, System.currentTimeMillis())
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(50); now = System.currentTimeMillis() } }

    if (profile != DeviceProfile.UNSUPPORTED && !settings.petToyEverBound) {
        Notice("Turn on Backlit Pet in Glyph Toys (Settings → Glyph Interface → Glyph Toys). He snacks while you charge with him on the Glyph.")
    }

    val pose = brain.pose(now, size)
    MatrixPreview(GhostArt.frame(size, pose, now), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clickable { brain.onLongPress(System.currentTimeMillis(), true) })

    val mood = brain.mood(now)
    val state = when (pose.base) {
        Base.HAPPY -> "HAPPY"; Base.CONTENT -> "CONTENT"; Base.BORED -> "BORED"
        Base.SAD -> "SAD"; Base.ASLEEP -> "ASLEEP"; Base.MUNCH -> "SNACKING"
    }
    Text("${settings.petName.uppercase()} IS $state", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
    Row(
        Modifier.padding(vertical = 8.dp).clickable(enabled = BuildConfig.DEBUG) {
            val next = when { mood >= 70 -> 55f; mood >= 40 -> 25f; mood >= 15 -> 5f; else -> 90f }
            onUpdate { it.copy(petMood = next, petMoodAt = System.currentTimeMillis()) }
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(10) { i -> Box(Modifier.size(10.dp).background(if (i < (mood + 5) / 10) BacklitColors.White else BacklitColors.LedOff, CircleShape)) }
    }
    Text(
        "MOOD $mood / 100 · " + PetInsight.hint(brain.moodExact(now), now, sleep, ZoneId.systemDefault(),
            charging = pose.base == Base.MUNCH, asleep = pose.base == Base.ASLEEP),
        style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim,
    )

    var howOpen by remember { mutableStateOf(false) }
    SettingRow("How ${settings.petName}'s mood works", if (howOpen) "−" else "+") { howOpen = !howOpen }
    if (howOpen) {
        Text(
            listOf(
                "↑ Long press to pet: +15 (once every 30 s)",
                "↑ Charging with him on the Glyph: +1 a minute",
                "↑ Peekaboo +5 · calming him down +10",
                "↓ Awake and ignored: about −10 an hour",
                "↓ Big shake −3 · getting angry −5",
                "Asleep: no change. He never drops below 0.",
                "70+ happy · 40+ content · 15+ bored · below 15 sad",
            ).joinToString("\n"),
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
    Text("Tap him to pet him here. Long press the Glyph button to pet him on the back.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim,
        modifier = Modifier.padding(top = 6.dp))

    Text("NAME", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    // Local text is the source of truth while editing (not re-keyed by saves), so typing and clearing work smoothly.
    var name by remember { mutableStateOf(settings.petName) }
    OutlinedTextField(
        value = name,
        onValueChange = { v ->
            name = v.take(12)
            SettingsRepo.petNameToSave(name)?.let { clean -> if (clean != settings.petName) onUpdate { it.copy(petName = clean) } }
        },
        placeholder = { Text("Boo") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text("SLEEP HOURS", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    HourStepper("FROM", settings.petSleepStart) { h -> onUpdate { it.copy(petSleepStart = h) } }
    HourStepper("TO", settings.petSleepEnd) { h -> onUpdate { it.copy(petSleepEnd = h) } }
    Text("He sleeps (and his mood doesn't drop) between these hours.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)

    SquareChip("SHOW ON GLYPH", true, { runtime.preview(PetPreviewAnimation.idFor(Base.HAPPY), 3000L) }, Modifier.fillMaxWidth().padding(vertical = 12.dp))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.weight(1f))
        SquareChip("−", false, { onChange((hour + 23) % 24) })
        Text("%02d:00".format(hour), style = MaterialTheme.typography.titleMedium)
        SquareChip("+", false, { onChange((hour + 1) % 24) })
    }
}
