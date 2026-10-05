package app.backlit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.alerts.AlertsRuntime
import app.backlit.glyph.DeviceProfile
import app.backlit.studio.Drawing
import app.backlit.studio.DrawingCodec
import app.backlit.studio.EditorState
import app.backlit.studio.MAX_FPS
import app.backlit.studio.MAX_FRAMES
import app.backlit.studio.MAX_NAME
import app.backlit.studio.MIN_FPS
import app.backlit.studio.PixelFontText
import app.backlit.studio.Raster
import app.backlit.studio.SHADE_VALUES
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class Tool { PEN, ERASE, LINE, CIRCLE, FILL }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(drawingId: String?, profile: DeviceProfile, onClose: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val scope = rememberCoroutineScope()
    val n = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25   // grid size; `size` is taken by Compose scopes

    var editor by remember { mutableStateOf<EditorState?>(null) }
    var savedId by remember { mutableStateOf(drawingId) }
    var savedDoc by remember { mutableStateOf<Drawing?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(drawingId) {
        val d = if (drawingId == null) Drawing.blank(n) else runtime.loadDrawing(drawingId, n)
        if (d == null) failed = true else { editor = EditorState.of(d); savedDoc = if (drawingId == null) null else d }
    }

    var tool by remember { mutableStateOf(Tool.PEN) }
    var shade by remember { mutableIntStateOf(3) }
    var mirror by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var playT by remember { mutableLongStateOf(0L) }
    var askText by remember { mutableStateOf(false) }
    var askName by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var frameMenu by remember { mutableStateOf<Int?>(null) }

    val state = editor
    val dirty = state != null && state.doc != savedDoc
    fun leave() { if (dirty) askDiscard = true else onClose() }
    BackHandler { leave() }

    if (failed) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ScreenHeader("STUDIO", onBack = onClose)
            Notice("Couldn't open this drawing.")
        }
        return
    }
    if (state == null) return

    fun save(name: String? = null) {
        val named = if (name != null) state.rename(name) else state
        val fixed = if (named.doc.name.isBlank()) named.rename("Drawing") else named
        scope.launch {
            savedId = runtime.saveDrawing(fixed.doc, savedId)
            editor = fixed
            savedDoc = fixed.doc
        }
    }

    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (true) { delay(50); playT = System.currentTimeMillis() - start }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // ── header ──
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("← BACK", style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { leave() }.padding(end = 12.dp))
            Text(state.doc.name.ifBlank { "NEW DRAWING" }.uppercase(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(if (playing) "■ STOP" else "▶ PLAY", style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { playing = !playing }.padding(start = 12.dp))
        }

        // ── canvas ──
        val current by rememberUpdatedState(state)
        val toolNow by rememberUpdatedState(tool)
        val shadeNow by rememberUpdatedState(shade)
        val mirrorNow by rememberUpdatedState(mirror)
        val shown = if (playing) DrawingCodec.encode(state.doc, "play").frame(n, playT) else DrawingCodec.grid(state.doc, state.frame)
        val onion = if (!playing && state.current > 0) state.doc.frames[state.current - 1] else null
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = 4.dp)
                .pointerInput(playing, n) {
                    if (playing) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun cell(o: Offset) = Pair(
                            floor(o.x / size.width * n).toInt().coerceIn(0, n - 1),
                            floor(o.y / size.height * n).toInt().coerceIn(0, n - 1),
                        )
                        val before = current
                        val start = cell(down.position)
                        val stroke = mutableListOf(start)
                        fun apply(end: Pair<Int, Int>): EditorState = when (toolNow) {
                            Tool.PEN -> before.paint(stroke, shadeNow, mirrorNow)
                            Tool.ERASE -> before.paint(stroke, 0, mirrorNow)
                            Tool.LINE -> before.line(start.first, start.second, end.first, end.second, shadeNow, mirrorNow)
                            Tool.CIRCLE -> before.circle(start.first, start.second,
                                hypot((end.first - start.first).toDouble(), (end.second - start.second).toDouble()).roundToInt(), shadeNow, mirrorNow)
                            Tool.FILL -> before.fill(start.first, start.second, shadeNow, mirrorNow)
                        }
                        editor = apply(start)
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val c = cell(ch.position)
                            if (c != stroke.last() && toolNow != Tool.FILL) {
                                stroke += Raster.line(stroke.last().first, stroke.last().second, c.first, c.second).drop(1)
                                editor = apply(c)
                            }
                            ch.consume()
                        }
                    }
                },
        ) {
            val cellPx = size.width / n
            for (y in 0 until n) for (x in 0 until n) {
                if (!Raster.hasLed(n, x, y)) continue
                val v = shown[x, y]
                val color = when {
                    v > 0 -> BacklitColors.White.copy(alpha = 0.12f + 0.88f * v / 255f)
                    onion != null && onion[n, x, y] > 0 -> BacklitColors.White.copy(alpha = 0.12f)
                    else -> BacklitColors.LedOff
                }
                drawCircle(color, radius = cellPx * 0.4f, center = Offset(x * cellPx + cellPx / 2, y * cellPx + cellPx / 2))
            }
            if (mirror) drawLine(BacklitColors.Red, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height),
                strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        }

        // ── tool row ──
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Tool.PEN to "PEN", Tool.ERASE to "ERASE", Tool.LINE to "LINE", Tool.CIRCLE to "○", Tool.FILL to "FILL").forEach { (t, l) ->
                SquareChip(l, tool == t, { tool = t }, Modifier.weight(1f))
            }
            SquareChip("TEXT", false, { askText = true }, Modifier.weight(1f))
        }
        // ── shade row ──
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(3 to "BRIGHT", 2 to "MED", 1 to "DIM").forEach { (s, l) ->
                SquareChip(l, shade == s && tool != Tool.ERASE, { shade = s; if (tool == Tool.ERASE) tool = Tool.PEN }, Modifier.weight(1f))
            }
            SquareChip("MIRROR", mirror, { mirror = !mirror }, Modifier.weight(1f))
        }
        // ── edit row ──
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SquareChip("↶", false, { editor = state.undo() }, Modifier.weight(1f))
            SquareChip("↷", false, { editor = state.redo() }, Modifier.weight(1f))
            SquareChip("←", false, { editor = state.shift(-1, 0) }, Modifier.weight(1f))
            SquareChip("↑", false, { editor = state.shift(0, -1) }, Modifier.weight(1f))
            SquareChip("↓", false, { editor = state.shift(0, 1) }, Modifier.weight(1f))
            SquareChip("→", false, { editor = state.shift(1, 0) }, Modifier.weight(1f))
            SquareChip("CLR", false, { editor = state.clear() }, Modifier.weight(1f))
        }

        // ── frames ──
        Text("FRAMES · ${state.doc.frames.size} / $MAX_FRAMES", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.doc.frames.forEachIndexed { i, f ->
                Box {
                    Box(
                        Modifier.size(52.dp).border(1.dp, if (i == state.current) BacklitColors.White else BacklitColors.Line)
                            .combinedClickable(onClick = { editor = state.select(i) }, onLongClick = { editor = state.select(i); frameMenu = i }),
                    ) {
                        MatrixPreview(DrawingCodec.grid(state.doc, f), Modifier.fillMaxSize().padding(3.dp))
                        Text(if (f.hold > 1) "${i + 1} ×${f.hold}" else "${i + 1}", style = MaterialTheme.typography.labelSmall,
                            color = BacklitColors.Dim, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
                    }
                    DropdownMenu(expanded = frameMenu == i, onDismissRequest = { frameMenu = null }) {
                        DropdownMenuItem(text = { Text("DELETE") }, enabled = state.doc.frames.size > 1, onClick = { editor = state.deleteFrame(); frameMenu = null })
                        DropdownMenuItem(text = { Text("MOVE LEFT") }, enabled = i > 0, onClick = { editor = state.moveFrame(-1); frameMenu = null })
                        DropdownMenuItem(text = { Text("MOVE RIGHT") }, enabled = i < state.doc.frames.lastIndex, onClick = { editor = state.moveFrame(1); frameMenu = null })
                    }
                }
            }
            if (state.doc.frames.size < MAX_FRAMES) {
                Box(Modifier.size(52.dp).border(1.dp, BacklitColors.Dim).clickable { editor = state.addFrame() }, contentAlignment = Alignment.Center) {
                    Text("+", style = MaterialTheme.typography.titleMedium, color = BacklitColors.Dim)
                }
            }
        }

        // ── speed & hold ──
        Text("SPEED · ${state.doc.fps} FPS", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(top = 12.dp))
        var fps by remember(state.doc.fps) { mutableStateOf(state.doc.fps.toFloat()) }
        Slider(
            value = fps, onValueChange = { fps = it }, onValueChangeFinished = { editor = state.setFps(fps.roundToInt()) },
            valueRange = MIN_FPS.toFloat()..MAX_FPS.toFloat(), steps = MAX_FPS - MIN_FPS - 1,
            colors = SliderDefaults.colors(thumbColor = BacklitColors.White, activeTrackColor = BacklitColors.White, inactiveTrackColor = BacklitColors.Line),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("HOLD", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(end = 4.dp))
            (1..4).forEach { h -> SquareChip("×$h", state.frame.hold == h, { editor = state.setHold(h) }, Modifier.weight(1f)) }
        }

        // ── save / show ──
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SquareChip(if (dirty || savedId == null) "SAVE" else "SAVED", true, {
                if (savedId == null && state.doc.name.isBlank()) askName = true else save()
            }, Modifier.weight(1f))
            SquareChip("SHOW ON GLYPH", false, {
                val anim = DrawingCodec.encode(state.doc, AlertsRuntime.PREVIEW_ID)
                runtime.previewAnimation(anim, anim.loopMs.coerceIn(3000L, 10_000L))
            }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }

    if (askText) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askText = false },
            title = { Text("Text") },
            text = { OutlinedTextField(value = text, onValueChange = { text = PixelFontText.clean(it) }, singleLine = true,
                supportingText = { Text("Up to ${PixelFontText.MAX_CHARS}: A–Z 0–9 ! ? . - : + ♥") }) },
            confirmButton = { TextButton(onClick = { editor = state.text(text, shade, mirror); askText = false }) { Text("PLACE") } },
            dismissButton = { TextButton(onClick = { askText = false }) { Text("CANCEL") } },
        )
    }
    if (askName) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askName = false },
            title = { Text("Name your drawing") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(MAX_NAME) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { save(name.ifBlank { "Drawing" }); askName = false }) { Text("SAVE") } },
            dismissButton = { TextButton(onClick = { askName = false }) { Text("CANCEL") } },
        )
    }
    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = { askDiscard = false; onClose() }) { Text("DISCARD") } },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("KEEP EDITING") } },
        )
    }
}
