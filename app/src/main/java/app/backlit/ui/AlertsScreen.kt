package app.backlit.ui

import app.backlit.ui.components.Notice
import app.backlit.ui.components.ActionSpec
import app.backlit.ui.components.ActionStyle
import app.backlit.ui.components.AttentionCard
import app.backlit.ui.components.BottomActionBar
import app.backlit.ui.components.CenterNote
import app.backlit.ui.components.GalleryCard
import app.backlit.ui.components.OptionSheet
import app.backlit.ui.components.PageHeader
import app.backlit.ui.components.SegmentedControl
import app.backlit.ui.components.SheetAction
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.components.LiveSelection
import app.backlit.ui.alerts.AlertsAttention
import app.backlit.ui.alerts.AlertsSegment
import app.backlit.ui.alerts.Attention
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableIntStateOf
import app.backlit.render.PixelGrid
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import app.backlit.alerts.AlertConfig
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.ContactRule
import app.backlit.alerts.DeviceRule
import app.backlit.alerts.KIND_DRAWING
import app.backlit.alerts.NameMatch
import app.backlit.alerts.importFromUri
import app.backlit.anim.BuiltInAnimations
import app.backlit.anim.GlyphAnimation
import app.backlit.glyph.DeviceProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DISCLOSURE =
    "Backlit only looks at incoming-call and missed-call notifications from your calling apps, to check the " +
        "caller's name against your important contacts. Nothing else is read, stored or sent."

@Composable
fun AlertsScreen(profile: DeviceProfile, onEdit: (String?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    // null until the first real emission, so a rotation's empty start never closes an open sheet.
    val loadedConfig by runtime.config.collectAsStateWithLifecycle<AlertConfig?>(initialValue = null)
    val config = loadedConfig ?: AlertConfig()
    val scope = rememberCoroutineScope()
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var listenerOn by remember { mutableStateOf(true) }
    var btGranted by remember { mutableStateOf(true) }
    var callLights by remember { mutableStateOf(false) }
    LaunchedEffect(resumed) {
        if (resumed) {
            listenerOn = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            btGranted = hasBtPermission(context)
            callLights = nothingCallLightsOn(context)
        }
    }
    var segment by rememberSaveable { mutableStateOf(AlertsSegment.CONTACTS) }
    var ruleKey by rememberSaveable { mutableStateOf<String?>(null) }      // "c:<name>" or "d:<address>"
    var addingDevice by rememberSaveable { mutableStateOf(false) }
    var animId by rememberSaveable { mutableStateOf<String?>(null) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    // Prominent disclosure before any permission or settings jump: "notif" (notification access) or "bt" (Nearby devices).
    var disclosing by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var barPx by remember { mutableIntStateOf(0) }
    val now = rememberTicker(50)
    val animations: List<GlyphAnimation> = remember(config.imports) { BuiltInAnimations.all + config.imports.mapNotNull { runtime.library.load(it) } }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        val name = uri?.let { readContactName(context, it) } ?: return@rememberLauncherForActivityResult
        scope.launch {
            runtime.update { c -> if (c.contacts.any { NameMatch.matches(it.name, name) }) c else c.copy(contacts = c.contacts + ContactRule(name, BuiltInAnimations.DEFAULT_CONTACT)) }
        }
        ruleKey = "c:$name"
        if (!listenerOn) disclosing = "notif"
    }
    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> btGranted = ok; if (ok) addingDevice = true }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) message = importFromUri(context, runtime, uri, size) }

    val attention = AlertsAttention.pick(listenerOn, btGranted, config.contacts.size, config.devices.size, segment)
    val bar = when (segment) {
        AlertsSegment.CONTACTS -> ActionSpec("+ ADD CONTACT", ActionStyle.PRIMARY) { pickContact.launch(null) }
        AlertsSegment.DEVICES -> ActionSpec("+ ADD DEVICE", ActionStyle.PRIMARY) { if (btGranted) addingDevice = true else disclosing = "bt" }
        AlertsSegment.ANIMATIONS -> ActionSpec("IMPORT FROM GLYPH MUSEUM", ActionStyle.PRIMARY) { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = with(LocalDensity.current) { barPx.toDp() } + 16.dp)) {
            PageHeader("ALERTS", onBack)
            when (attention) {
                Attention.NOTIFICATION_ACCESS -> AttentionCard("Allow notification access so calls light the Glyph") { disclosing = "notif" }
                Attention.NEARBY -> AttentionCard("Allow Nearby devices so Backlit can notice your devices connecting") { disclosing = "bt" }
                null -> Unit
            }
            SegmentedControl(AlertsSegment.entries.map { it.name to it.label }, segment.name) { segment = AlertsSegment.valueOf(it) }
            message?.let { Notice(it) }
            when (segment) {
                AlertsSegment.CONTACTS -> {
                    if (config.contacts.isEmpty()) CenterNote("Add the people who matter. Their calls play an animation on the Glyph as they ring, and again if you miss them.")
                    config.contacts.forEach { r -> RuleListRow(initials(r.name), r.name, animations.nameOf(r.animationId), animations.firstOrNull { it.id == r.animationId }, size, now) { ruleKey = "c:${r.name}" } }
                }
                AlertsSegment.DEVICES -> {
                    Text("Plays when the device connects.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
                    if (config.devices.isEmpty()) CenterNote("Add a Bluetooth device — earbuds, car, watch — and its animation plays on the Glyph when it connects.")
                    config.devices.forEach { r -> RuleListRow("◖◗", r.name, animations.nameOf(r.animationId), animations.firstOrNull { it.id == r.animationId }, size, now) { ruleKey = "d:${r.address}" } }
                }
                AlertsSegment.ANIMATIONS -> {
                    Text("Tap to preview on the Glyph.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
                    animations.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { a ->
                                val own = a.id.startsWith("import:")
                                GalleryCard(a.frame(size, now), a.name, if (own) "TAP · ⋯" else "BUILT-IN", false, {
                                    runtime.preview(a.id)
                                    if (own) animId = a.id
                                }, Modifier.weight(1f))
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            SettingRow("About alerts", if (aboutOpen) "−" else "+") { aboutOpen = !aboutOpen }
            if (aboutOpen) {
                Text(
                    listOfNotNull(DISCLOSURE,
                        if (callLights) "Nothing's ringtone lights take over the matrix a moment into each call. Backlit plays your contact's animation as the call starts, and again if you miss it." else null,
                        "Tip: set Backlit Clock as your always-on toy so alerts always show.").joinToString("\n\n"),
                    style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
        BottomActionBar(bar, onHeight = { barPx = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // Rule sheet: looks the rule up in the live config, so a removed rule just closes the sheet.
    val contact = ruleKey?.takeIf { it.startsWith("c:") }?.let { k -> config.contacts.firstOrNull { "c:${it.name}" == k } }
    val device = ruleKey?.takeIf { it.startsWith("d:") }?.let { k -> config.devices.firstOrNull { "d:${it.address}" == k } }
    val ruleSelection = remember { LiveSelection() }
    LaunchedEffect(ruleKey, loadedConfig) { val k = ruleSelection.resolve(ruleKey, contact != null || device != null, loadedConfig != null); if (k != ruleKey) ruleKey = k }
    if (contact != null || device != null) {
        val title = contact?.name ?: device!!.name
        val selected = contact?.animationId ?: device!!.animationId
        OptionSheet(title.uppercase(), { ruleKey = null }) {
            animations.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { a ->
                        GalleryCard(a.frame(size, now), a.name, if (a.id == selected) "● CHOSEN" else "", a.id == selected, {
                            scope.launch {
                                runtime.update { c ->
                                    if (contact != null) c.copy(contacts = c.contacts.map { if (it == contact) it.copy(animationId = a.id) else it })
                                    else c.copy(devices = c.devices.map { if (it == device) it.copy(animationId = a.id) else it })
                                }
                            }
                        }, Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            SheetAction("PREVIEW ON GLYPH") { runtime.preview(selected) }
            SheetAction("REMOVE", danger = true) {
                scope.launch { runtime.update { c -> if (contact != null) c.copy(contacts = c.contacts - contact) else c.copy(devices = c.devices - device!!) } }
                ruleKey = null
            }
        }
    }

    when (disclosing) {
        "notif" -> OptionSheet("NOTIFICATION ACCESS", { disclosing = null }) {
            Text(DISCLOSURE, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
            Text(
                listOf(
                    "• Read: incoming-call and missed-call notifications from your calling apps, to match the caller's name.",
                    "• Saved: only the names of the contacts you pick, on your phone.",
                    "• Never: other notifications, message content, or anything sent off your phone.",
                ).joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium, color = BacklitColors.Dim, modifier = Modifier.padding(bottom = 8.dp),
            )
            SheetAction("CONTINUE TO SETTINGS") {
                disclosing = null
                context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            SheetAction("NOT NOW") { disclosing = null }
        }
        "bt" -> OptionSheet("NEARBY DEVICES", { disclosing = null }) {
            Text(
                "Backlit asks for Nearby devices so it can list your paired Bluetooth devices and notice when the ones you choose connect. " +
                    "Only their name and address are saved, on your phone. Nothing is sent anywhere.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp),
            )
            SheetAction("CONTINUE") { disclosing = null; btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }
            SheetAction("NOT NOW") { disclosing = null }
        }
    }

    if (addingDevice) OptionSheet("ADD A BLUETOOTH DEVICE", { addingDevice = false }) {
        val bonded = remember { bondedDevices(context) }.filter { (addr, _) -> config.devices.none { it.address.equals(addr, ignoreCase = true) } }
        if (bonded.isEmpty()) CenterNote("No paired Bluetooth devices found.")
        bonded.forEach { (addr, name) ->
            SheetAction(name) {
                scope.launch { runtime.update { c -> c.copy(devices = c.devices + DeviceRule(addr, name, BuiltInAnimations.DEFAULT_DEVICE)) } }
                addingDevice = false
                ruleKey = "d:$addr"
            }
        }
    }

    val ownAnim = animId?.let { id -> config.imports.firstOrNull { it.id == id } }
    val animSelection = remember { LiveSelection() }
    LaunchedEffect(animId, loadedConfig) { val k = animSelection.resolve(animId, ownAnim != null, loadedConfig != null); if (k != animId) animId = k }
    if (ownAnim != null) OptionSheet(ownAnim.name.uppercase(), { animId = null }) {
        val isDrawing = ownAnim.kind == KIND_DRAWING
        SheetAction(if (isDrawing) "EDIT IN STUDIO" else "COPY TO STUDIO AND EDIT") {
            if (isDrawing) onEdit(ownAnim.id) else scope.launch { runtime.copyImportToDrawing(ownAnim.id, size)?.let { onEdit(it) } }
            animId = null
        }
        SheetAction("DELETE", danger = true) { runtime.deleteImport(ownAnim.id); animId = null }
    }
}

@Composable
private fun RuleListRow(badge: String, name: String, animName: String, anim: GlyphAnimation?, size: Int, now: Long, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).border(1.dp, BacklitColors.Line, CircleShape), contentAlignment = Alignment.Center) { Text(badge, style = MaterialTheme.typography.labelSmall) }
        Column(Modifier.weight(1f)) {
            Text(name.uppercase(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(animName, style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        }
        MatrixPreview(anim?.frame(size, now) ?: PixelGrid(size), Modifier.size(34.dp))
    }
}

private fun initials(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }

private fun List<GlyphAnimation>.nameOf(id: String): String = (firstOrNull { it.id == id } ?: BuiltInAnimations.byId(id))?.name?.uppercase() ?: "DEFAULT"

private fun readContactName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()?.takeIf { it.isNotBlank() }

private fun hasBtPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private fun bondedDevices(context: Context): List<Pair<String, String>> = runCatching {
    if (!hasBtPermission(context)) return emptyList()
    context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
        ?.map { it.address to (it.name ?: it.address) }?.sortedBy { it.second.lowercase() }
}.getOrNull().orEmpty()

/** Nothing OS keeps its "Glyph for calls" switch in a global setting (its key really is spelled "enalbe"). */
private fun nothingCallLightsOn(context: Context): Boolean = runCatching {
    val cr = context.contentResolver
    android.provider.Settings.Global.getInt(cr, "led_effect_call_enalbe", 0) == 1 ||
        android.provider.Settings.Global.getInt(cr, "led_effect_call_enable", 0) == 1
}.getOrDefault(false)
