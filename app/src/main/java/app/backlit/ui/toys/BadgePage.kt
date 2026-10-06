package app.backlit.ui

import app.backlit.ui.components.OptionSheet
import app.backlit.ui.components.PillButton
import app.backlit.ui.components.Section
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.ToyAction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.backlit.badge.BadgeArt
import app.backlit.badge.BadgeIcons
import app.backlit.badge.BadgeMessage
import app.backlit.badge.BadgeMessage.Kind
import app.backlit.badge.BadgePreviewAnimation
import app.backlit.badge.BadgeText
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import kotlinx.coroutines.delay
import java.time.ZoneId

@Composable
fun BadgePage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val messages = BadgeMessage.decodeList(settings.badgeMessages)
    val active = BadgeMessage.activeIndex(settings.badgeActive, messages.size)
    val current = messages[active]
    val opened = remember { System.currentTimeMillis() }
    val now = rememberTicker(50, chrome.active)
    val since = settings.badgeActiveSince.takeIf { it > 0 } ?: opened
    val zone = ZoneId.systemDefault()
    val text = BadgeText.scroll(current, since, now, settings.use24h, zone)

    fun save(list: List<BadgeMessage>, newActive: Int, restart: Boolean) = onUpdate {
        val clean = BadgeMessage.cleanList(list)
        it.copy(
            badgeMessages = BadgeMessage.encodeList(clean),
            badgeActive = BadgeMessage.activeIndex(newActive, clean.size),
            badgeActiveSince = if (restart) System.currentTimeMillis() else it.badgeActiveSince,
        )
    }

    var editing by remember { mutableStateOf<Int?>(null) }   // index being edited, or -1 for a new message
    // The open editor holds an index: if the list changes underneath it (here or on the toy), close it rather than
    // let SAVE overwrite a different message.
    LaunchedEffect(settings.badgeMessages) { editing = null }
    val idle = editing == null

    ToyPageScaffold(
        chrome, "BADGE", BadgeArt.frame(size, current, text, now - opened, BadgeArt.FLASH_MS, BadgeText.spanText(current, settings.use24h)),
        text.ifBlank { "(ICON ONLY)" },
        ToyAction.of(ToyId.BADGE, chrome.setUp, chrome.supported, hasDrawing = false),
        onShow = { runtime.preview(BadgePreviewAnimation.idFor(current.icon, text), 6000L) },
    ) {
        Section("MESSAGES")
        // Tapping the message that's already showing doesn't restart its countdown.
        messages.forEachIndexed { i, m ->
            Row(
                Modifier.fillMaxWidth().border(1.dp, if (i == active) BacklitColors.White else BacklitColors.Line, RoundedCornerShape(14.dp))
                    .clickable(enabled = idle) { save(messages, i, restart = i != active) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MatrixPreview(BadgeArt.tile(m.icon), Modifier.size(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.text.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(kindLabel(m, settings.use24h), style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
                }
                PillButton("↑", {
                    if (idle && i > 0) save(messages.toMutableList().also { it[i] = messages[i - 1]; it[i - 1] = m }, BadgeMessage.moveActive(active, i, i - 1), restart = false)
                })
                PillButton("↓", {
                    if (idle && i < messages.size - 1) save(messages.toMutableList().also { it[i] = messages[i + 1]; it[i + 1] = m }, BadgeMessage.moveActive(active, i, i + 1), restart = false)
                })
                PillButton("EDIT", { if (idle) editing = i })
            }
            Spacer(Modifier.height(6.dp))
        }
        if (messages.size < BadgeMessage.MAX_MESSAGES) SettingRow("+ Add message", "→") { if (idle) editing = -1 }
        Text("Tap a message to show it. Long press the Glyph button to switch messages on the back.",
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(top = 10.dp))
    }

    editing?.let { i ->
        val isNew = i == -1
        val start = if (isNew) BadgeMessage("", "heart") else messages.getOrNull(i)
        if (start == null) { editing = null } else OptionSheet(if (isNew) "NEW MESSAGE" else "EDIT MESSAGE", { editing = null }) {
            BadgeEditor(start, settings.use24h, canDelete = !isNew && messages.size > 1,
                onSave = { e -> if (isNew) save(messages + e, messages.size, restart = true) else save(messages.toMutableList().also { it[i] = e }, active, restart = i == active); editing = null },
                onDelete = { save(messages.filterIndexed { k, _ -> k != i }, BadgeMessage.afterDelete(active, i, messages.size), restart = i == active); editing = null },
                onCancel = { editing = null })
        }
    }
}

private fun kindLabel(m: BadgeMessage, use24h: Boolean): String = when (m.kind) {
    Kind.PLAIN -> "PLAIN"
    Kind.COUNTDOWN -> "COUNTDOWN · ${m.minutes} MIN"
    Kind.UNTIL -> "UNTIL " + BadgeText.timeLabel(m.untilMinuteOfDay, use24h)
}

@Composable
private fun BadgeEditor(
    initial: BadgeMessage,
    use24h: Boolean,
    canDelete: Boolean,
    onSave: (BadgeMessage) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.text) }
    var icon by remember { mutableStateOf(initial.icon) }
    var kind by remember { mutableStateOf(initial.kind) }
    var minutes by remember { mutableIntStateOf(initial.minutes) }
    var until by remember { mutableIntStateOf(initial.untilMinuteOfDay) }

    Column(Modifier.fillMaxWidth().border(1.dp, BacklitColors.White).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = BadgeMessage.cleanText(it) },
            placeholder = { Text("MESSAGE") },
            supportingText = { Text("${text.length} / ${BadgeMessage.MAX_TEXT}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BadgeIcons.ids.forEach { id ->
                Box(Modifier.weight(1f).border(1.dp, if (id == icon) BacklitColors.White else BacklitColors.Line).clickable { icon = id }.padding(2.dp)) {
                    MatrixPreview(BadgeArt.tile(id), Modifier.fillMaxWidth())
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Kind.PLAIN to "PLAIN", Kind.COUNTDOWN to "COUNTDOWN", Kind.UNTIL to "UNTIL").forEach { (k, label) ->
                SquareChip(label, kind == k, { kind = k }, Modifier.weight(1f))
            }
        }
        when (kind) {
            Kind.COUNTDOWN -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SquareChip("−5", false, { minutes = (minutes - 5).coerceAtLeast(1) })
                SquareChip("−1", false, { minutes = (minutes - 1).coerceAtLeast(1) })
                Text("$minutes MIN", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SquareChip("+1", false, { minutes = (minutes + 1).coerceAtMost(180) })
                SquareChip("+5", false, { minutes = (minutes + 5).coerceAtMost(180) })
            }
            Kind.UNTIL -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SquareChip("H−", false, { until = (until - 60 + 1440) % 1440 })
                SquareChip("H+", false, { until = (until + 60) % 1440 })
                Text(BadgeText.timeLabel(until, use24h), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SquareChip("M−", false, { until = (until - 5 + 1440) % 1440 })
                SquareChip("M+", false, { until = (until + 5) % 1440 })
            }
            Kind.PLAIN -> Unit
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SquareChip("SAVE", true, { onSave(BadgeMessage(text, icon, kind, minutes, until).clean()) }, Modifier.weight(1f))
            SquareChip("CANCEL", false, onCancel, Modifier.weight(1f))
            if (canDelete) SquareChip("DELETE", false, onDelete, Modifier.weight(1f))
        }
    }
}
