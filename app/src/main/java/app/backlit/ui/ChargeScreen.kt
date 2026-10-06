package app.backlit.ui

import app.backlit.ui.components.Notice
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ChargeTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val config by runtime.config.collectAsStateWithLifecycle(initialValue = AlertConfig())
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val style = ChargeStyles.byId(settings.chargeStyle)
    var moment by rememberSaveable { mutableStateOf(Moment.CHARGING) }
    var message by remember { mutableStateOf<String?>(null) }

    // Live preview clock, restarted whenever the style or moment changes.
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(style.id, moment) {
        val start = System.currentTimeMillis()
        while (true) { delay(50); t = (System.currentTimeMillis() - start) % ChargePreviewAnimation.durationMs(moment) }
    }
    val anim = ChargePreviewAnimation(style, moment)

    if (profile != DeviceProfile.UNSUPPORTED && !settings.chargeToyEverBound) {
        Notice("Turn on Backlit Charge in Glyph Toys (Settings → Glyph Interface → Glyph Toys).")
    }

    MatrixPreview(anim.frame(size, t), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Moment.STILL to "STILL", Moment.PLUG_IN to "PLUG-IN", Moment.CHARGING to "CHARGING", Moment.DONE to "DONE").forEach { (m, label) ->
            SquareChip(label, selected = m == moment, onClick = { moment = m }, modifier = Modifier.weight(1f))
        }
    }
    SquareChip("SHOW ON GLYPH", selected = true, onClick = {
        runtime.preview(ChargePreviewAnimation.idFor(style.id, moment), ChargePreviewAnimation.durationMs(moment))
    }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))

    // ── Style ──
    Text("STYLE", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChargeStyles.all.forEach { s ->
            val selected = s.id == style.id
            Column(
                Modifier.weight(1f).border(1.dp, if (selected) BacklitColors.White else BacklitColors.Line)
                    .clickable { onUpdate { it.copy(chargeStyle = s.id) } }.padding(4.dp),
            ) {
                MatrixPreview(s.still(size, ChargePreviewAnimation.DEMO_LEVEL), Modifier.fillMaxWidth().padding(2.dp))
                Text(s.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (selected) BacklitColors.White else BacklitColors.Dim)
            }
        }
    }

    // ── Done at ──
    Text("DONE AT ${settings.chargeTarget}%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 2.dp))
    var target by remember(settings.chargeTarget) { mutableFloatStateOf(settings.chargeTarget.toFloat()) }
    Slider(
        value = target,
        onValueChange = { target = it },
        onValueChangeFinished = { val v = SettingsRepo.clampTarget(target.roundToInt()); onUpdate { it.copy(chargeTarget = v) } },
        valueRange = 50f..100f,
        steps = 9,
        colors = SliderDefaults.colors(thumbColor = BacklitColors.White, activeTrackColor = BacklitColors.White, inactiveTrackColor = BacklitColors.Line),
    )
    Text(
        "Plays once per charge when your battery reaches this level. If your phone stops charging early (battery protection), set it at or below that limit.",
        style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim,
    )

    // ── Custom animations ──
    Text("CUSTOM ANIMATIONS", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) message = importFromUri(context, runtime, uri, size)
    }
    message?.let { Notice(it) }
    val names = remember(config.imports) { config.imports.associate { it.id to it.name } }
    AnimChoice("PLUG-IN ANIMATION", settings.chargePlugInAnim, names) { id -> onUpdate { it.copy(chargePlugInAnim = id) } }
    AnimChoice("DONE ANIMATION", settings.chargeDoneAnim, names) { id -> onUpdate { it.copy(chargeDoneAnim = id) } }
    SquareChip("IMPORT FROM GLYPH MUSEUM", selected = false, onClick = {
        importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    Text("Imported animations play instead of the style's own, but can't show your level.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
    Spacer(Modifier.height(12.dp))
}

/** "Style default" plus every imported animation; a selected id that no longer exists reads as the default. */
@Composable
private fun AnimChoice(label: String, selectedId: String, imports: Map<String, String>, onSelect: (String) -> Unit) {
    var open by rememberSaveable(label) { mutableStateOf(false) }
    val current = imports[selectedId] ?: "STYLE DEFAULT"
    SettingRow(label, current.uppercase()) { open = !open }
    if (open) {
        Column(Modifier.fillMaxWidth().border(1.dp, BacklitColors.Line).padding(8.dp)) {
            (listOf("" to "Style default") + imports.toList()).forEach { (id, name) ->
                val selected = id == selectedId || (id == "" && selectedId !in imports)
                Text(
                    (if (selected) "● " else "○ ") + name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) BacklitColors.White else BacklitColors.Dim,
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(id); open = false }.padding(vertical = 8.dp),
                )
            }
            if (imports.isEmpty()) Text("No imported animations yet.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        }
    }
}
