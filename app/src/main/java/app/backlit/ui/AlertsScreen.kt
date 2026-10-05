package app.backlit.ui

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
fun AlertsTab(profile: DeviceProfile, onEditDrawing: (String?) -> Unit = {}) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val config by runtime.config.collectAsStateWithLifecycle(initialValue = AlertConfig())
    val scope = rememberCoroutineScope()
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var listenerOn by remember { mutableStateOf(false) }
    var btGranted by remember { mutableStateOf(false) }
    var nothingCallLights by remember { mutableStateOf(false) }
    LaunchedEffect(resumed) {
        if (resumed) {
            listenerOn = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            btGranted = hasBtPermission(context)
            nothingCallLights = nothingCallLightsOn(context)
        }
    }

    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    var pickingDevice by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val animations: List<GlyphAnimation> = remember(config.imports) {
        BuiltInAnimations.all + config.imports.mapNotNull { runtime.library.load(it) }
    }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        val name = uri?.let { readContactName(context, it) } ?: return@rememberLauncherForActivityResult
        scope.launch {
            runtime.update { c ->
                if (c.contacts.any { NameMatch.matches(it.name, name) }) c
                else c.copy(contacts = c.contacts + ContactRule(name, BuiltInAnimations.DEFAULT_CONTACT))
            }
        }
        expanded = "c:$name"
        if (!listenerOn) showDisclosure = true
    }
    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        btGranted = ok
        pickingDevice = ok
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) message = importFromUri(context, runtime, uri, size)
    }

    // ── Status hints ──
    if (showDisclosure || (!listenerOn && config.contacts.isNotEmpty())) {
        Notice(DISCLOSURE)
        SquareChip("TURN ON NOTIFICATION ACCESS", selected = true, onClick = {
            showDisclosure = false
            context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
    }
    if (nothingCallLights && config.contacts.isNotEmpty()) {
        Notice("Nothing's ringtone lights take over the matrix a moment into each call. Backlit plays your contact's animation as the call starts, and again if you miss it.")
    }
    if (!btGranted && config.devices.isNotEmpty()) Notice("Allow Nearby devices so Backlit can notice your Bluetooth devices connecting.")
    Notice("Tip: set Backlit Clock as your always-on toy so alerts always show.")
    message?.let { Notice(it) }

    // ── Important contacts ──
    SectionTitle("IMPORTANT CONTACTS")
    config.contacts.forEach { rule ->
        val key = "c:${rule.name}"
        RuleRow(rule.name, animations.nameOf(rule.animationId)) { expanded = if (expanded == key) null else key }
        if (expanded == key) {
            AnimationPicker(animations, rule.animationId, size,
                onSelect = { id -> scope.launch { runtime.update { c -> c.copy(contacts = c.contacts.map { if (it == rule) it.copy(animationId = id) else it }) } } },
                onPreview = { runtime.preview(rule.animationId) },
                onRemove = { scope.launch { runtime.update { c -> c.copy(contacts = c.contacts - rule) } }; expanded = null },
            )
        }
    }
    SquareChip("+ ADD CONTACT", selected = false, onClick = { pickContact.launch(null) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))

    // ── Bluetooth devices ──
    SectionTitle("BLUETOOTH DEVICES")
    config.devices.forEach { rule ->
        val key = "d:${rule.address}"
        RuleRow(rule.name, animations.nameOf(rule.animationId)) { expanded = if (expanded == key) null else key }
        if (expanded == key) {
            AnimationPicker(animations, rule.animationId, size,
                onSelect = { id -> scope.launch { runtime.update { c -> c.copy(devices = c.devices.map { if (it == rule) it.copy(animationId = id) else it }) } } },
                onPreview = { runtime.preview(rule.animationId) },
                onRemove = { scope.launch { runtime.update { c -> c.copy(devices = c.devices - rule) } }; expanded = null },
            )
        }
    }
    if (pickingDevice) {
        val bonded = remember { bondedDevices(context) }
        if (bonded.isEmpty()) Notice("No paired Bluetooth devices found.")
        bonded.filter { (addr, _) -> config.devices.none { it.address.equals(addr, ignoreCase = true) } }.forEach { (addr, name) ->
            RuleRow(name, "ADD") {
                scope.launch { runtime.update { c -> c.copy(devices = c.devices + DeviceRule(addr, name, BuiltInAnimations.DEFAULT_DEVICE)) } }
                pickingDevice = false
                expanded = "d:$addr"
            }
        }
    }
    SquareChip("+ ADD DEVICE", selected = false, onClick = {
        if (btGranted) pickingDevice = !pickingDevice else btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))

    // ── Animation library ──
    SectionTitle("ANIMATIONS")
    animations.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { anim ->
                Column(Modifier.weight(1f).clickable { runtime.preview(anim.id) }) {
                    MiniPreview(anim, size)
                    Text(anim.name, style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
                    if (anim.id.startsWith("import:")) {
                        val entry = config.imports.firstOrNull { it.id == anim.id }
                        val isDrawing = entry?.kind == KIND_DRAWING
                        Text(if (isDrawing) "DRAWING" else "IMPORT", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
                        Text(if (isDrawing) "EDIT" else "EDIT IN STUDIO", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable {
                                if (isDrawing) onEditDrawing(anim.id)
                                else scope.launch { runtime.copyImportToDrawing(anim.id, size)?.let { onEditDrawing(it) } }
                            }.padding(vertical = 4.dp))
                        Text("DELETE", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Red,
                            modifier = Modifier.clickable { runtime.deleteImport(anim.id) }.padding(vertical = 4.dp))
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
    SquareChip("IMPORT FROM GLYPH MUSEUM", selected = true, onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    Text("Tap any animation to preview it on the matrix.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}

@Composable
private fun RuleRow(label: String, value: String, onClick: () -> Unit) = SettingRow(label, value, onClick)

@Composable
private fun AnimationPicker(
    animations: List<GlyphAnimation>,
    selectedId: String,
    size: Int,
    onSelect: (String) -> Unit,
    onPreview: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().border(1.dp, BacklitColors.Line).padding(10.dp)) {
        animations.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { anim ->
                    val selected = anim.id == selectedId
                    Column(Modifier.weight(1f).border(1.dp, if (selected) BacklitColors.White else BacklitColors.Black).clickable { onSelect(anim.id) }.padding(4.dp)) {
                        MiniPreview(anim, size)
                        Text(anim.name, style = MaterialTheme.typography.labelSmall, color = if (selected) BacklitColors.White else BacklitColors.Dim)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareChip("PREVIEW ON MATRIX", selected = true, onClick = onPreview, modifier = Modifier.weight(2f))
            Spacer(Modifier.width(0.dp))
            SquareChip("REMOVE", selected = false, onClick = onRemove, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniPreview(anim: GlyphAnimation, size: Int) {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(anim.id) {
        val start = System.currentTimeMillis()
        while (true) { delay(50); t = System.currentTimeMillis() - start }
    }
    MatrixPreview(anim.frame(size, t), Modifier.fillMaxWidth().padding(4.dp))
}

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
