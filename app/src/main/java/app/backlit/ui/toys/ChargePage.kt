package app.backlit.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.backlit.alerts.AlertConfig
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.importFromUri
import app.backlit.charge.ChargePreviewAnimation
import app.backlit.charge.Moment
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.glyph.DeviceProfile
import app.backlit.render.charge.ChargeStyles
import app.backlit.ui.components.ChipRow
import app.backlit.ui.components.GalleryCard
import app.backlit.ui.components.Notice
import app.backlit.ui.components.OptionSheet
import app.backlit.ui.components.PillButton
import app.backlit.ui.components.Section
import app.backlit.ui.components.SheetAction
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.StatusInputs
import app.backlit.ui.toys.ToyAction
import app.backlit.ui.toys.ToyStatus
import java.time.ZoneId

@Composable
fun ChargePage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val config by runtime.config.collectAsStateWithLifecycle(initialValue = AlertConfig())
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val style = ChargeStyles.byId(settings.chargeStyle)
    var moment by rememberSaveable { mutableStateOf(Moment.CHARGING) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }   // "style" | "plug" | "done"
    var message by remember { mutableStateOf<String?>(null) }
    val battery = remember {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), android.content.Context.RECEIVER_NOT_EXPORTED)?.let {
            it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        }?.takeIf { it >= 0 }
    }
    val now = rememberTicker(50, chrome.active)
    val t = now % ChargePreviewAnimation.durationMs(moment)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) message = importFromUri(context, runtime, uri, size)
    }
    val names = remember(config.imports) { config.imports.associate { it.id to it.name } }
    val action = ToyAction.of(ToyId.CHARGE, chrome.setUp, chrome.supported, hasDrawing = false)

    ToyPageScaffold(
        chrome, "CHARGE", ChargePreviewAnimation(style, moment).frame(size, t),
        ToyStatus.line(ToyId.CHARGE, settings, now, ZoneId.systemDefault(), StatusInputs(battery = battery)), action,
        onShow = { runtime.preview(ChargePreviewAnimation.idFor(style.id, moment), ChargePreviewAnimation.durationMs(moment)) },
    ) {
        ChipRow(listOf(Moment.STILL to "STILL", Moment.PLUG_IN to "PLUG-IN", Moment.CHARGING to "CHARGING", Moment.DONE to "DONE").map { it.first.name to it.second }, moment.name) { moment = Moment.valueOf(it) }
        SettingRow("Style", "${style.label.uppercase()} →") { sheet = "style" }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Done at", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            PillButton("−", { onUpdate { it.copy(chargeTarget = SettingsRepo.clampTarget(it.chargeTarget - 5)) } })
            Text("${settings.chargeTarget} %", style = MaterialTheme.typography.titleMedium)
            PillButton("+", { onUpdate { it.copy(chargeTarget = SettingsRepo.clampTarget(it.chargeTarget + 5)) } })
        }
        Text("Plays once per charge when your battery reaches this level. If your phone stops charging early (battery protection), set it at or below that limit.",
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        Section("CUSTOM ANIMATIONS")
        SettingRow("Plug-in animation", "${(names[settings.chargePlugInAnim] ?: "Style default").uppercase()} →") { sheet = "plug" }
        SettingRow("Done animation", "${(names[settings.chargeDoneAnim] ?: "Style default").uppercase()} →") { sheet = "done" }
        SettingRow("Import from Glyph Museum", "→") { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        message?.let { Notice(it) }
        Text("Imported animations play instead of the style's own, but can't show your level.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
    }

    when (sheet) {
        "style" -> OptionSheet("STYLE", { sheet = null }) {
            ChargeStyles.all.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { s ->
                        GalleryCard(s.still(size, ChargePreviewAnimation.DEMO_LEVEL), s.label, if (s.id == style.id) "● CHOSEN" else "", s.id == style.id,
                            { onUpdate { it.copy(chargeStyle = s.id) }; sheet = null }, Modifier.weight(1f).padding(bottom = 8.dp))
                    }
                    if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
        "plug", "done" -> OptionSheet(if (sheet == "plug") "PLUG-IN ANIMATION" else "DONE ANIMATION", { sheet = null }) {
            val current = if (sheet == "plug") settings.chargePlugInAnim else settings.chargeDoneAnim
            (listOf("" to "Style default") + names.toList()).forEach { (id, name) ->
                val chosen = id == current || (id == "" && current !in names)
                SheetAction((if (chosen) "● " else "○ ") + name) {
                    onUpdate { if (sheet == "plug") it.copy(chargePlugInAnim = id) else it.copy(chargeDoneAnim = id) }
                    sheet = null
                }
            }
            if (names.isEmpty()) Text("No imported animations yet.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        }
    }
}
