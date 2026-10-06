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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.backlit.pet.MoodState
import app.backlit.pet.PetArt
import app.backlit.pet.PetBrain
import app.backlit.pet.PetInsight
import app.backlit.pet.PetKind
import app.backlit.pet.PetPreviewAnimation
import app.backlit.pet.Pose
import app.backlit.pet.SleepWindow
import app.backlit.ui.components.GalleryCard
import app.backlit.ui.components.OptionSheet
import app.backlit.ui.components.PillButton
import app.backlit.ui.components.Section
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.StatusInputs
import app.backlit.ui.toys.ToyAction
import app.backlit.ui.toys.ToyStatus
import java.time.ZoneId

@Composable
fun PetPage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val sleep = SleepWindow(settings.petSleepStart, settings.petSleepEnd)
    val kind = PetKind.byId(settings.petKind)
    val petName = SettingsRepo.petNameFor(settings, kind)
    val zone = ZoneId.systemDefault()
    val brain = remember(settings.petMood, settings.petMoodAt, settings.petSleepStart, settings.petSleepEnd) {
        PetBrain(MoodState(settings.petMood.toDouble(), settings.petMoodAt), { sleep }, zone)
    }
    LaunchedEffect(brain) {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), android.content.Context.RECEIVER_NOT_EXPORTED)
        if (i != null) {
            val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            brain.onCharging(i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0, lv, System.currentTimeMillis())
        }
    }
    val now = rememberTicker(50, chrome.active)
    val pose = brain.pose(now, size)
    val mood = brain.mood(now)
    val word = when (pose.base) {
        Base.HAPPY -> "HAPPY"; Base.CONTENT -> "CONTENT"; Base.BORED -> "BORED"
        Base.SAD -> "SAD"; Base.ASLEEP -> "ASLEEP"; Base.MUNCH -> "SNACKING"
    }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var howOpen by rememberSaveable { mutableStateOf(false) }

    ToyPageScaffold(
        chrome, "PET", PetArt.frame(kind, size, pose, now),
        ToyStatus.line(ToyId.PET, settings, now, zone, StatusInputs(petBase = word, petMood = mood)),
        ToyAction.of(ToyId.PET, chrome.setUp, chrome.supported, hasDrawing = false),
        onShow = { runtime.preview(PetPreviewAnimation.idFor(kind, Base.HAPPY), 3000L) },
        heroClick = { brain.onLongPress(System.currentTimeMillis(), true) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = BuildConfig.DEBUG) {
                val next = when { mood >= 70 -> 55f; mood >= 40 -> 25f; mood >= 15 -> 5f; else -> 90f }
                onUpdate { it.copy(petMood = next, petMoodAt = System.currentTimeMillis()) }
            },
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(10) { i -> Box(Modifier.padding(horizontal = 3.dp).size(10.dp).background(if (i < (mood + 5) / 10) BacklitColors.White else BacklitColors.LedOff, CircleShape)) }
        }
        Text(PetInsight.hint(brain.moodExact(now), now, sleep, zone, charging = pose.base == Base.MUNCH, asleep = pose.base == Base.ASLEEP),
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
        SettingRow("Pet", "${kind.id.uppercase()} →") { choosing = true }
        Section("NAME")
        var name by remember(kind) { mutableStateOf(petName) }
        OutlinedTextField(
            value = name,
            onValueChange = { v ->
                name = v.take(12)
                SettingsRepo.petNameToSave(name)?.let { clean -> if (clean != petName) onUpdate { SettingsRepo.withPetName(it, kind, clean) } }
            },
            placeholder = { Text(kind.defaultName) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Section("SLEEP HOURS")
        HourRow("From", settings.petSleepStart) { h -> onUpdate { it.copy(petSleepStart = h) } }
        HourRow("To", settings.petSleepEnd) { h -> onUpdate { it.copy(petSleepEnd = h) } }
        SettingRow("How $petName's mood works", if (howOpen) "−" else "+") { howOpen = !howOpen }
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
                style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        Text("Tap him to pet him here. Long press the Glyph button to pet him on the back.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(top = 6.dp))
    }

    if (choosing) OptionSheet("CHOOSE YOUR PET", { choosing = false }) {
        PetKind.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    GalleryCard(PetArt.frame(k, size, Pose(brain.base(now), null, 0, 0, 0, 0, 62), now), SettingsRepo.petNameFor(settings, k), k.id.uppercase(), k == kind,
                        { onUpdate { it.copy(petKind = k.id) }; choosing = false }, Modifier.weight(1f).padding(bottom = 8.dp))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HourRow(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        PillButton("−", { onChange((hour + 23) % 24) })
        Text("%02d:00".format(hour), style = MaterialTheme.typography.titleMedium)
        PillButton("+", { onChange((hour + 1) % 24) })
    }
}
