# Backlit Pixel Studio + Canvas Toy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an in-app pixel studio for drawing still images and animations of up to 24 frames, in 3 shades, at the phone's matrix size, and a Backlit Canvas toy that shows a drawing.
- Drawings are saved as Glyph Museum entries in the existing animation library, so they appear in every picker.
- They can be shared as JSON.
- Glyph Museum files import as editable drawings.

**Architecture:**
- **Pure Kotlin `studio/`:** `Raster`, `PixelFontText`, `Drawing`/`Frame`, the immutable `EditorState` with undo history, `DrawingCodec` (Drawing ↔ `ImportedAnimation`) and `CanvasHint`, all JVM-tested.
- **`AlertsRuntime`** gains drawing save, load and import plus a transient preview slot.
- **Android side:** `CanvasToyService` copies `ChargeToyService`. Compose `StudioScreen` (the tab) and `EditorScreen` (full screen, layout A) build on the pure core.

**Tech Stack:** Kotlin, Jetpack Compose (material3), DataStore, kotlinx-coroutines, kotlinx-serialization, JUnit 4, Nothing GlyphMatrix SDK, and androidx.core FileProvider (already a transitive dependency).

**Spec:** `docs/superpowers/specs/2026-10-05-backlit-studio-design.md`. The layout mockup is `docs/superpowers/mockups/2026-10-05-studio-layout.html` (layout A).

## Global Constraints

- Work on branch `feat/backlit-studio`. In every fresh shell, run `source .superpowers/env.sh`. Run tests via `.superpowers/runtests.sh [--tests 'pattern']`.
- `studio/` imports no `android.*`, `androidx.*` or `com.nothing.*`. It may import `app.backlit.render.*` and `app.backlit.anim.*`.
- **Shades:** index 0 off, 1 = 70 (DIM), 2 = 150 (MED), 3 = 255 (BRIGHT). Nearest-shade thresholds: <35 → 0, <110 → 1, <203 → 2, else 3.
- **Limits:**
  - frames 1..24, fps 2..20 (default 8), hold 1..4
  - frame duration = `hold × round(1000 / fps)` ms
  - undo history 50, name ≤ 24 characters, text ≤ 6 characters from `0-9 A-Z ! ? . - : + ♥`
- **Library:**
  - A drawing id is `import:<uuid>`.
  - `AnimIndexEntry(kind = "drawing", fps, sourceV = 1 for 25 / 4 for 13)`. Its file is `MuseumFormat.toJson` output.
- The preview id is `preview:studio`. "Show on Glyph" duration is `clamp(loopMs, 3000, 10000)`.
- **Settings:** `canvasDrawingId = ""`, `canvasToyEverBound = false`.
- No new permissions and no network. The FileProvider authority is `${applicationId}.share`, with path `cache/share/`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **A fast finger drag** must give a continuous stroke, not dotted cells, and one stroke must be one undo step. This is pinned by `EditorStateTest.strokeFromSameBaseIsOneUndoStep` (Task 2). The UI recomputes the stroke from the state at pointer-down.
2. **Mirror plus off-panel cells:** painting near the round edge must never light a pixel without an LED, including its mirror. This is pinned by `EditorStateTest.paintIgnoresOffPanelCellsAndMirrors` (Task 2).
3. **Old stored import index JSON without `kind`/`fps`** must still load after the upgrade, otherwise every import vanishes. This is pinned by `AlertStoreTest.oldImportIndexWithoutKindStillDecodes` (Task 4).
4. **A Glyph Museum file with 600 frames or odd durations** must import as at most 24 frames with sane fps and holds, and no crash. This is pinned by `DrawingCodecTest.truncatesAndDerivesTiming` (Task 3).
5. **Deleting the drawing the Canvas toy shows** must fall back to the first drawing or the hint, not a blank screen or a crash. This is pinned by `CanvasHintTest.pickFallsBack` (Task 6), which tests the pure id choice.

---

## File Structure

```
app/src/main/java/app/backlit/studio/
  Raster.kt          mask check, line samples, midpoint circle, 4-way flood fill
  PixelFontText.kt   3×5 text glyphs (+ 5-wide ♥), width, centred points
  Drawing.kt         Frame (shades ByteArray + hold), Drawing, constants
  EditorState.kt     immutable editor document + current frame + undo/redo
  DrawingCodec.kt    Drawing ↔ ImportedAnimation, nearest shade, timing derivation
  CanvasHint.kt      hint pattern + pure "which drawing to show / next" helpers
app/src/main/java/app/backlit/alerts/
  Rules.kt (AnimIndexEntry + kind/fps), AnimationLibrary.kt (@Synchronized),
  AlertsRuntime.kt (drawings, save/load/import/copy, previewAnimation), ImportHelper.kt (shared read + drawing import)
app/src/main/java/app/backlit/data/Settings.kt, SettingsRepo.kt   (+ canvas fields)
app/src/main/java/app/backlit/glyph/CanvasToyService.kt
app/src/main/java/app/backlit/ui/StudioScreen.kt, EditorScreen.kt, Share.kt
app/src/main/java/app/backlit/ui/HomeScreen.kt, AlertsScreen.kt, MainActivity.kt   (5th tab, hoisted tab, EDITOR route, labels, Edit in Studio)
app/src/main/AndroidManifest.xml, res/xml/share_paths.xml, res/values/strings.xml, res/drawable/ic_canvas_preview.xml
```

---

### Task 1: Raster and text font

**Files:**
- Create: `app/src/main/java/app/backlit/studio/Raster.kt`, `app/src/main/java/app/backlit/studio/PixelFontText.kt`
- Test: `app/src/test/java/app/backlit/studio/RasterTest.kt`, `app/src/test/java/app/backlit/studio/PixelFontTextTest.kt`

**Interfaces:**
- Produces:
  - `object Raster`:
    - `hasLed(size: Int, x: Int, y: Int): Boolean`
    - `line(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>>`
    - `circle(cx: Int, cy: Int, r: Int): List<Pair<Int, Int>>`
    - `fillRegion(size: Int, shades: ByteArray, sx: Int, sy: Int): Set<Int>` (cell indices `y*size+x`)
  - `object PixelFontText`:
    - `const val MAX_CHARS = 6`
    - `fun clean(text: String): String` (uppercases, keeps allowed characters, caps at 6)
    - `fun width(text: String): Int`
    - `fun points(text: String, size: Int): List<Pair<Int, Int>>` (centred)

- [ ] **Step 1: Write the failing tests**

`RasterTest.kt`:

```kotlin
package app.backlit.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class RasterTest {

    @Test
    fun maskMatchesRoundPanel() {
        assertTrue(Raster.hasLed(25, 12, 0)); assertFalse(Raster.hasLed(25, 0, 0))
        assertTrue(Raster.hasLed(13, 6, 6)); assertFalse(Raster.hasLed(13, 0, 0)); assertFalse(Raster.hasLed(13, 13, 6))
    }

    @Test
    fun lineHitsEndpointsAndIsEightConnected() {
        val p = Raster.line(2, 3, 20, 11)
        assertEquals(2 to 3, p.first()); assertEquals(20 to 11, p.last())
        p.zipWithNext().forEach { (a, b) -> assertEquals(1, max(abs(a.first - b.first), abs(a.second - b.second))) }
        assertEquals(listOf(5 to 5), Raster.line(5, 5, 5, 5))
    }

    @Test
    fun circleIsGapFreeAndSymmetric() {
        val p = Raster.circle(12, 12, 8).toSet()
        for (c in listOf(12 to 4, 12 to 20, 4 to 12, 20 to 12)) assertTrue("cardinal $c", c in p)
        p.forEach { (x, y) -> assertTrue((24 - x) to y in p); assertTrue(x to (24 - y) in p) }
        assertEquals(setOf(6 to 6), Raster.circle(6, 6, 0).toSet())
    }

    @Test
    fun fillStaysInsideItsRegionAndThePanel() {
        val size = 25
        val shades = ByteArray(size * size)
        for ((x, y) in Raster.circle(12, 12, 5)) shades[y * size + x] = 3     // a closed ring
        val inside = Raster.fillRegion(size, shades, 12, 12)
        assertTrue(inside.isNotEmpty())
        assertTrue(inside.all { val x = it % size; val y = it / size; (x - 12) * (x - 12) + (y - 12) * (y - 12) < 25 })
        val outside = Raster.fillRegion(size, shades, 12, 1)
        assertTrue(outside.all { Raster.hasLed(size, it % size, it / size) })
        assertFalse(outside.contains(12 * size + 12))
        assertEquals(emptySet<Int>(), Raster.fillRegion(size, shades, 0, 0))         // no LED there
    }
}
```

`PixelFontTextTest.kt`:

```kotlin
package app.backlit.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelFontTextTest {

    @Test
    fun cleansAndMeasures() {
        assertEquals("HI!", PixelFontText.clean("hi!"))
        assertEquals("ABCDEF", PixelFontText.clean("abcdefgh"))
        assertEquals("AB", PixelFontText.clean("a~b"))
        assertEquals(7, PixelFontText.width("HI"))
        assertEquals(5, PixelFontText.width("♥"))
        assertEquals(9, PixelFontText.width("A♥"))
        assertEquals(0, PixelFontText.width(""))
    }

    @Test
    fun pointsAreCentred() {
        val p = PixelFontText.points("HI", 25)               // w = 7, h = 5 → x0 = 9, y0 = 10
        assertTrue(9 to 10 in p)                              // H top-left
        assertTrue(15 to 14 in p)                             // I bottom-right
        assertTrue(p.all { (x, y) -> x in 9..15 && y in 10..14 })
        val s = PixelFontText.points("1", 13)                 // w = 3 → x0 = 5, y0 = 4; '1' row 0 is "010"
        assertTrue(6 to 4 in s)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.studio.*'`
Expected: compilation FAIL, because `Raster` and `PixelFontText` are unresolved.

- [ ] **Step 3: Write the implementation**

`Raster.kt`:

```kotlin
package app.backlit.studio

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/** Pixel geometry for the editor. Pure; cells are (x, y), frames are row-major size×size. */
object Raster {
    fun hasLed(size: Int, x: Int, y: Int): Boolean {
        if (x !in 0 until size || y !in 0 until size) return false
        val c = (size - 1) / 2.0
        return hypot(x - c, y - c) <= size / 2.0
    }

    /** Samples every 0.5 px, rounded half up: endpoints included, 8-connected, no duplicates. */
    fun line(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
        val steps = max(1, ceil(hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()) * 2).toInt())
        val out = ArrayList<Pair<Int, Int>>()
        for (k in 0..steps) {
            val f = k.toDouble() / steps
            val p = round(x0 + (x1 - x0) * f) to round(y0 + (y1 - y0) * f)
            if (out.isEmpty() || out.last() != p) out += p
        }
        return out
    }

    /** Midpoint (Bresenham) circle outline; r <= 0 is the centre cell. */
    fun circle(cx: Int, cy: Int, r: Int): List<Pair<Int, Int>> {
        if (r <= 0) return listOf(cx to cy)
        val out = LinkedHashSet<Pair<Int, Int>>()
        var x = r
        var y = 0
        var err = 1 - r
        while (x >= y) {
            for ((dx, dy) in listOf(x to y, y to x, -y to x, -x to y, -x to -y, -y to -x, y to -x, x to -y)) out += (cx + dx) to (cy + dy)
            y++
            if (err < 0) err += 2 * y + 1 else { x--; err += 2 * (y - x) + 1 }
        }
        return out.toList()
    }

    /** 4-way connected cells with the seed's shade, on the panel only. Empty if the seed has no LED. */
    fun fillRegion(size: Int, shades: ByteArray, sx: Int, sy: Int): Set<Int> {
        if (!hasLed(size, sx, sy)) return emptySet()
        val target = shades[sy * size + sx]
        val seen = HashSet<Int>()
        val stack = ArrayDeque<Int>().apply { add(sy * size + sx) }
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (!seen.add(i)) continue
            val x = i % size
            val y = i / size
            for ((nx, ny) in listOf(x + 1 to y, x - 1 to y, x to y + 1, x to y - 1)) {
                if (hasLed(size, nx, ny) && shades[ny * size + nx] == target && (ny * size + nx) !in seen) stack.add(ny * size + nx)
            }
        }
        return seen
    }

    private fun round(v: Double): Int = floor(v + 0.5 + 1e-9).toInt()
}
```

`PixelFontText.kt`:

```kotlin
package app.backlit.studio

/** 3×5 text glyphs for the studio's TEXT tool (♥ is 5 wide). 1 px gap between characters. */
object PixelFontText {
    const val MAX_CHARS = 6
    const val HEIGHT = 5

    private val GLYPHS: Map<Char, List<String>> = mapOf(
        '0' to listOf("111", "101", "101", "101", "111"), '1' to listOf("010", "110", "010", "010", "111"),
        '2' to listOf("111", "001", "111", "100", "111"), '3' to listOf("111", "001", "111", "001", "111"),
        '4' to listOf("101", "101", "111", "001", "001"), '5' to listOf("111", "100", "111", "001", "111"),
        '6' to listOf("111", "100", "111", "101", "111"), '7' to listOf("111", "001", "001", "001", "001"),
        '8' to listOf("111", "101", "111", "101", "111"), '9' to listOf("111", "101", "111", "001", "111"),
        'A' to listOf("010", "101", "111", "101", "101"), 'B' to listOf("110", "101", "110", "101", "110"),
        'C' to listOf("011", "100", "100", "100", "011"), 'D' to listOf("110", "101", "101", "101", "110"),
        'E' to listOf("111", "100", "110", "100", "111"), 'F' to listOf("111", "100", "110", "100", "100"),
        'G' to listOf("011", "100", "101", "101", "011"), 'H' to listOf("101", "101", "111", "101", "101"),
        'I' to listOf("111", "010", "010", "010", "111"), 'J' to listOf("001", "001", "001", "101", "010"),
        'K' to listOf("101", "101", "110", "101", "101"), 'L' to listOf("100", "100", "100", "100", "111"),
        'M' to listOf("101", "111", "111", "101", "101"), 'N' to listOf("110", "101", "101", "101", "101"),
        'O' to listOf("010", "101", "101", "101", "010"), 'P' to listOf("110", "101", "110", "100", "100"),
        'Q' to listOf("010", "101", "101", "110", "011"), 'R' to listOf("110", "101", "110", "101", "101"),
        'S' to listOf("011", "100", "010", "001", "110"), 'T' to listOf("111", "010", "010", "010", "010"),
        'U' to listOf("101", "101", "101", "101", "111"), 'V' to listOf("101", "101", "101", "101", "010"),
        'W' to listOf("101", "101", "111", "111", "101"), 'X' to listOf("101", "101", "010", "101", "101"),
        'Y' to listOf("101", "101", "010", "010", "010"), 'Z' to listOf("111", "001", "010", "100", "111"),
        '!' to listOf("010", "010", "010", "000", "010"), '?' to listOf("111", "001", "010", "000", "010"),
        '.' to listOf("000", "000", "000", "000", "010"), '-' to listOf("000", "000", "111", "000", "000"),
        ':' to listOf("000", "010", "000", "010", "000"), '+' to listOf("000", "010", "111", "010", "000"),
        '♥' to listOf("01010", "11111", "11111", "01110", "00100"),
    )

    fun clean(text: String): String = text.uppercase().filter { it in GLYPHS }.take(MAX_CHARS)

    fun width(text: String): Int {
        val g = text.mapNotNull { GLYPHS[it] }
        return if (g.isEmpty()) 0 else g.sumOf { it[0].length } + g.size - 1
    }

    /** Lit cells of [text] centred on a size×size grid. Unknown characters are skipped. */
    fun points(text: String, size: Int): List<Pair<Int, Int>> {
        val glyphs = text.mapNotNull { GLYPHS[it] }
        var x = (size - width(text)) / 2
        val y0 = (size - HEIGHT) / 2
        val out = ArrayList<Pair<Int, Int>>()
        for (g in glyphs) {
            for (r in g.indices) for (c in g[r].indices) if (g[r][c] == '1') out += (x + c) to (y0 + r)
            x += g[0].length + 1
        }
        return out
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.studio.*'`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/studio/ app/src/test/java/app/backlit/studio/
git commit -m "feat(studio): raster helpers and 3x5 text font

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Drawing model and EditorState

**Files:**
- Create: `app/src/main/java/app/backlit/studio/Drawing.kt`, `app/src/main/java/app/backlit/studio/EditorState.kt`
- Test: `app/src/test/java/app/backlit/studio/EditorStateTest.kt`

**Interfaces:**
- Consumes: `Raster`, `PixelFontText` (Task 1).
- Produces:
  - `class Frame(val shades: ByteArray, val hold: Int)` with content `equals`/`hashCode`, `fun copy(shades = …, hold = …)` and `operator fun get(size: Int, x: Int, y: Int): Int`
  - `data class Drawing(val name: String, val size: Int, val fps: Int, val frames: List<Frame>)` with companion `fun blank(size: Int, name: String = ""): Drawing`
  - Constants `MAX_FRAMES = 24`, `MIN_FPS = 2`, `MAX_FPS = 20`, `DEFAULT_FPS = 8`, `MAX_HOLD = 4`, `MAX_NAME = 24`, and `SHADE_VALUES = intArrayOf(0, 70, 150, 255)`
  - `class EditorState`:
    - `doc`, `current`, `frame`, `canUndo`, `canRedo`
    - `select(i)`, `paint(points, shade, mirror)`, `line(x0, y0, x1, y1, shade, mirror)`, `circle(cx, cy, r, shade, mirror)`, `fill(x, y, shade, mirror)`, `text(text, shade, mirror)`
    - `shift(dx, dy)`, `clear()`, `addFrame()`, `deleteFrame()`, `moveFrame(delta)`, `setHold(h)`, `setFps(f)`, `rename(name)`
    - `undo()`, `redo()`
    - companion `of(doc: Drawing)` and `HISTORY = 50`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStateTest {

    private fun blank(size: Int = 25) = EditorState.of(Drawing.blank(size))
    private fun EditorState.at(x: Int, y: Int) = frame[doc.size, x, y]

    @Test
    fun blankDrawingDefaults() {
        val d = Drawing.blank(13)
        assertEquals(13, d.size); assertEquals(8, d.fps); assertEquals(1, d.frames.size); assertEquals(1, d.frames[0].hold)
        assertEquals(169, d.frames[0].shades.size)
    }

    @Test
    fun paintIgnoresOffPanelCellsAndMirrors() {
        val s = blank().paint(listOf(3 to 12, 0 to 0, 1 to 2), shade = 2, mirror = true)
        assertEquals(2, s.at(3, 12)); assertEquals(2, s.at(21, 12))       // mirror of x=3 is 21
        assertEquals(0, s.at(0, 0)); assertEquals(0, s.at(24, 0))         // no LED there, nor at its mirror
        assertEquals(0, s.at(1, 2)); assertEquals(0, s.at(23, 2))
    }

    @Test
    fun strokeFromSameBaseIsOneUndoStep() {
        val base = blank()
        var s = base.paint(listOf(10 to 10), 3, false)
        s = base.paint(Raster.line(10, 10, 16, 10), 3, false)             // the UI recomputes from the pointer-down state
        for (x in 10..16) assertEquals(3, s.at(x, 10))
        val undone = s.undo()
        assertEquals(base.doc, undone.doc)
        assertFalse(undone.canUndo)
    }

    @Test
    fun lineCircleFillAndText() {
        val l = blank().line(4, 12, 20, 12, 3, false)
        for (x in 4..20) assertEquals(3, l.at(x, 12))
        val c = blank().circle(12, 12, 8, 1, false)
        assertEquals(1, c.at(12, 4)); assertEquals(1, c.at(20, 12)); assertEquals(0, c.at(12, 12))
        val f = c.fill(12, 12, 2, false)
        assertEquals(2, f.at(12, 12)); assertEquals(1, f.at(12, 4)); assertEquals(0, f.at(12, 1))
        assertSame(f, f.fill(12, 12, 2, false))                            // same shade: no-op, no history
        val t = blank().text("hi", 3, false)
        assertEquals(3, t.at(9, 10)); assertEquals(3, t.at(15, 14))
    }

    @Test
    fun mirroredFillFillsBothSides() {
        val s = blank().line(12, 3, 12, 21, 3, false).fill(6, 12, 2, mirror = true)
        assertEquals(2, s.at(6, 12)); assertEquals(2, s.at(18, 12)); assertEquals(3, s.at(12, 12))
    }

    @Test
    fun shiftDropsEdgePixelsAndClearsOffPanelCells() {
        val s = blank().paint(listOf(24 to 12, 12 to 12), 3, false).shift(1, 0)
        assertEquals(3, s.at(13, 12)); assertEquals(0, s.at(12, 12))
        assertTrue((0 until 625).count { s.frame.shades[it].toInt() != 0 } == 1)   // (24,12) fell off the right edge
        val up = blank().paint(listOf(12 to 0), 3, false).shift(-4, 0)             // lands on (8,0), which has no LED → cleared
        assertEquals(0, up.frame.shades.count { it.toInt() != 0 })
    }

    @Test
    fun clearAndFrames() {
        var s = blank().paint(listOf(12 to 12), 3, false)
        s = s.addFrame()
        assertEquals(2, s.doc.frames.size); assertEquals(1, s.current); assertEquals(3, s.at(12, 12))    // copy of the current frame
        s = s.clear(); assertEquals(0, s.at(12, 12)); assertEquals(3, s.doc.frames[0][25, 12, 12])
        s = s.moveFrame(-1); assertEquals(0, s.current); assertEquals(0, s.at(12, 12))                   // the cleared frame moved first
        s = s.deleteFrame(); assertEquals(1, s.doc.frames.size); assertEquals(3, s.at(12, 12))
        assertSame(s, s.deleteFrame())                                                                    // never below 1 frame
        repeat(30) { s = s.addFrame() }
        assertEquals(24, s.doc.frames.size)
        assertSame(s, s.moveFrame(1))                                                                     // last frame can't move right
    }

    @Test
    fun holdFpsAndRenameAreClamped() {
        val s = blank().setHold(9).setFps(99).rename("  a very very long drawing name here  ")
        assertEquals(4, s.frame.hold); assertEquals(20, s.doc.fps)
        assertEquals(24, s.doc.name.length); assertEquals("a very very long drawing", s.doc.name)
        assertEquals(2, blank().setFps(0).doc.fps); assertEquals(1, blank().setHold(0).frame.hold)
    }

    @Test
    fun undoRedoAndHistoryCap() {
        var s = blank()
        val first = s.paint(listOf(12 to 12), 3, false)
        s = first.paint(listOf(13 to 12), 3, false)
        s = s.undo(); assertEquals(first.doc, s.doc); assertTrue(s.canRedo)
        s = s.redo(); assertEquals(3, s.at(13, 12))
        s = s.undo().paint(listOf(1 to 12), 1, false); assertFalse(s.canRedo)    // a new edit clears redo
        var h = blank()
        repeat(60) { i -> h = h.paint(listOf((i % 20) + 2 to 12), (i % 3) + 1, false) }
        var undos = 0
        while (h.canUndo) { h = h.undo(); undos++ }
        assertEquals(50, undos)
    }

    @Test
    fun selectIsNotAnUndoStep() {
        val s = blank().addFrame().select(0)
        assertEquals(0, s.current)
        assertEquals(1, s.undo().doc.frames.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.studio.EditorStateTest'`
Expected: compilation FAIL, because `EditorState` and `Drawing` are unresolved.

- [ ] **Step 3: Write the implementation**

`Drawing.kt`:

```kotlin
package app.backlit.studio

const val MAX_FRAMES = 24
const val MIN_FPS = 2
const val MAX_FPS = 20
const val DEFAULT_FPS = 8
const val MAX_HOLD = 4
const val MAX_NAME = 24
val SHADE_VALUES = intArrayOf(0, 70, 150, 255)

/** One frame: a shade index (0 off, 1 dim, 2 med, 3 bright) per cell, row-major, plus how many frame-times it holds. */
class Frame(val shades: ByteArray, val hold: Int) {
    operator fun get(size: Int, x: Int, y: Int): Int = shades[y * size + x].toInt()
    fun copy(shades: ByteArray = this.shades.copyOf(), hold: Int = this.hold) = Frame(shades, hold)
    override fun equals(other: Any?) = other is Frame && other.hold == hold && other.shades.contentEquals(shades)
    override fun hashCode() = 31 * hold + shades.contentHashCode()
}

data class Drawing(val name: String, val size: Int, val fps: Int, val frames: List<Frame>) {
    companion object {
        fun blank(size: Int, name: String = "") = Drawing(name, size, DEFAULT_FPS, listOf(Frame(ByteArray(size * size), 1)))
    }
}
```

`EditorState.kt`:

```kotlin
package app.backlit.studio

/**
 * The editor's document, current frame and undo/redo history. Immutable: every edit returns a new state
 * (or the same instance when nothing changes). For a drag, the UI keeps the pointer-down state and recomputes
 * the whole stroke from it, so one stroke is one undo step.
 */
class EditorState private constructor(
    val doc: Drawing,
    val current: Int,
    private val past: List<Snap>,
    private val future: List<Snap>,
) {
    private data class Snap(val doc: Drawing, val current: Int)

    val frame: Frame get() = doc.frames[current]
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun select(i: Int) = EditorState(doc, i.coerceIn(0, doc.frames.lastIndex), past, future)

    fun undo(): EditorState {
        val p = past.lastOrNull() ?: return this
        return EditorState(p.doc, p.current, past.dropLast(1), future + Snap(doc, current))
    }

    fun redo(): EditorState {
        val f = future.lastOrNull() ?: return this
        return EditorState(f.doc, f.current, past + Snap(doc, current), future.dropLast(1))
    }

    // ── drawing ──

    fun paint(points: List<Pair<Int, Int>>, shade: Int, mirror: Boolean): EditorState {
        val n = doc.size
        val s = frame.shades.copyOf()
        val v = shade.coerceIn(0, 3).toByte()
        for ((x, y) in points) {
            if (Raster.hasLed(n, x, y)) s[y * n + x] = v
            if (mirror && Raster.hasLed(n, n - 1 - x, y)) s[y * n + (n - 1 - x)] = v
        }
        return withFrame(frame.copy(shades = s))
    }

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, shade: Int, mirror: Boolean) = paint(Raster.line(x0, y0, x1, y1), shade, mirror)

    fun circle(cx: Int, cy: Int, r: Int, shade: Int, mirror: Boolean) = paint(Raster.circle(cx, cy, r), shade, mirror)

    fun fill(x: Int, y: Int, shade: Int, mirror: Boolean): EditorState {
        val n = doc.size
        val cells = Raster.fillRegion(n, frame.shades, x, y).toMutableSet()
        if (mirror) cells += Raster.fillRegion(n, frame.shades, n - 1 - x, y)
        return paint(cells.map { (it % n) to (it / n) }, shade, mirror = false)
    }

    fun text(text: String, shade: Int, mirror: Boolean) = paint(PixelFontText.points(PixelFontText.clean(text), doc.size), shade, mirror)

    fun shift(dx: Int, dy: Int): EditorState {
        val n = doc.size
        val s = ByteArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val sx = x - dx
            val sy = y - dy
            if (sx in 0 until n && sy in 0 until n && Raster.hasLed(n, x, y)) s[y * n + x] = frame.shades[sy * n + sx]
        }
        return withFrame(frame.copy(shades = s))
    }

    fun clear() = withFrame(frame.copy(shades = ByteArray(doc.size * doc.size)))

    // ── frames & timing ──

    fun addFrame(): EditorState {
        if (doc.frames.size >= MAX_FRAMES) return this
        val frames = doc.frames.toMutableList().apply { add(current + 1, frame.copy()) }
        return commit(doc.copy(frames = frames), current + 1)
    }

    fun deleteFrame(): EditorState {
        if (doc.frames.size <= 1) return this
        val frames = doc.frames.toMutableList().apply { removeAt(current) }
        return commit(doc.copy(frames = frames), current.coerceAtMost(frames.lastIndex))
    }

    fun moveFrame(delta: Int): EditorState {
        val to = current + delta
        if (to !in doc.frames.indices || delta == 0) return this
        val frames = doc.frames.toMutableList()
        val f = frames.removeAt(current)
        frames.add(to, f)
        return commit(doc.copy(frames = frames), to)
    }

    fun setHold(h: Int) = withFrame(frame.copy(hold = h.coerceIn(1, MAX_HOLD)))

    fun setFps(f: Int) = commit(doc.copy(fps = f.coerceIn(MIN_FPS, MAX_FPS)), current)

    fun rename(name: String) = commit(doc.copy(name = name.trim().take(MAX_NAME).trim()), current)

    private fun withFrame(f: Frame): EditorState =
        commit(doc.copy(frames = doc.frames.toMutableList().also { it[current] = f }), current)

    private fun commit(newDoc: Drawing, newCurrent: Int): EditorState {
        if (newDoc == doc && newCurrent == current) return this
        return EditorState(newDoc, newCurrent, (past + Snap(doc, current)).takeLast(HISTORY), emptyList())
    }

    companion object {
        const val HISTORY = 50
        fun of(doc: Drawing) = EditorState(doc, 0, emptyList(), emptyList())
    }
}
```

Note: in `shiftDropsEdgePixelsAndClearsOffPanelCells`, `(12,0)` shifted by −4 lands at `(8,0)`, which has no LED (hypot(4,12) = 12.65 > 12.5), so it's cleared and every cell is 0.

- [ ] **Step 4: Run the test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.studio.*'`
Expected: PASS (all studio tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/studio/ app/src/test/java/app/backlit/studio/EditorStateTest.kt
git commit -m "feat(studio): drawing model and immutable editor state with undo

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: DrawingCodec

**Files:**
- Create: `app/src/main/java/app/backlit/studio/DrawingCodec.kt`
- Test: `app/src/test/java/app/backlit/studio/DrawingCodecTest.kt`

**Interfaces:**
- Consumes: `Drawing`, `Frame`, `SHADE_VALUES` (Task 2), `ImportedAnimation`, `MuseumFormat.resample`, `PixelGrid` (existing).
- Produces: `object DrawingCodec`:
  - `fun shadeOf(v: Int): Int`
  - `fun frameMs(fps: Int): Long`
  - `fun grid(d: Drawing, frame: Frame): PixelGrid`
  - `fun encode(d: Drawing, id: String): ImportedAnimation`
  - `data class Decoded(val drawing: Drawing, val truncated: Boolean, val simplified: Boolean)`
  - `fun decode(anim: ImportedAnimation, name: String, fps: Int, size: Int): Decoded` (`fps` 0 means derive; `size` is the target 25 or 13)

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.studio

import app.backlit.anim.ImportedAnimation
import app.backlit.anim.MuseumFormat
import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingCodecTest {

    private fun sample(): Drawing {
        var s = EditorState.of(Drawing.blank(25, "Wink")).circle(12, 12, 8, 3, false).fill(12, 12, 1, false)
        s = s.addFrame().line(8, 10, 10, 10, 2, true).setHold(3).setFps(10)
        return s.doc
    }

    @Test
    fun shadeThresholds() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3), listOf(34, 35, 109, 110, 202, 203).map { DrawingCodec.shadeOf(it) })
        assertEquals(100L, DrawingCodec.frameMs(10)); assertEquals(125L, DrawingCodec.frameMs(8)); assertEquals(333L, DrawingCodec.frameMs(3))
    }

    @Test
    fun encodeDecodeRoundTripIsExact() {
        val d = sample()
        val anim = DrawingCodec.encode(d, "import:x")
        assertEquals(listOf(100L, 300L), anim.durations)
        assertEquals(25, anim.sourceSize); assertEquals(2, anim.frames13.size)
        assertEquals(255, anim.frames25[0][12, 4]); assertEquals(70, anim.frames25[0][12, 12])
        val back = DrawingCodec.decode(anim, "Wink", fps = 10, size = 25)
        assertEquals(d, back.drawing); assertFalse(back.truncated); assertFalse(back.simplified)
    }

    @Test
    fun roundTripsThroughMuseumJson() {
        val d = sample()
        val parsed = MuseumFormat.parse(MuseumFormat.toJson(DrawingCodec.encode(d, "import:x")), "import:x", "Wink") as MuseumFormat.Result.Ok
        assertEquals(d, DrawingCodec.decode(parsed.animation, "Wink", fps = 10, size = 25).drawing)
    }

    @Test
    fun truncatesAndDerivesTiming() {
        val frames = (0 until 30).map { i -> PixelGrid(25).also { it.put(12, 12, if (i % 2 == 0) 160 else 255) } }
        val durations = (0 until 30).map { if (it == 1) 300L else 100L }
        val anim = ImportedAnimation("import:y", "Big", 25, frames, frames.map { MuseumFormat.resample(it, 13) }, durations)
        val r = DrawingCodec.decode(anim, "Big", fps = 0, size = 25)
        assertEquals(24, r.drawing.frames.size); assertTrue(r.truncated); assertTrue(r.simplified)   // 160 isn't a shade value
        assertEquals(10, r.drawing.fps)
        assertEquals(listOf(1, 3, 1), r.drawing.frames.take(3).map { it.hold })
        assertEquals(2, r.drawing.frames[0][25, 12, 12])                                              // 160 → MED
        val odd = ImportedAnimation("import:z", "Odd", 25, frames.take(2), frames.take(2).map { MuseumFormat.resample(it, 13) }, listOf(20L, 5000L))
        val o = DrawingCodec.decode(odd, "Odd", fps = 0, size = 25)
        assertEquals(20, o.drawing.fps); assertEquals(listOf(1, 4), o.drawing.frames.map { it.hold })
    }

    @Test
    fun decodesAtTheOtherSize() {
        val anim = DrawingCodec.encode(sample(), "import:x")
        val small = DrawingCodec.decode(anim, "Wink", fps = 10, size = 13).drawing
        assertEquals(13, small.size); assertEquals(169, small.frames[0].shades.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.studio.DrawingCodecTest'`
Expected: compilation FAIL, because `DrawingCodec` is unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package app.backlit.studio

import app.backlit.anim.ImportedAnimation
import app.backlit.anim.MuseumFormat
import app.backlit.render.PixelGrid
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Drawing ↔ ImportedAnimation (the Glyph Museum representation the library stores). */
object DrawingCodec {
    data class Decoded(val drawing: Drawing, val truncated: Boolean, val simplified: Boolean)

    fun shadeOf(v: Int): Int = when {
        v < 35 -> 0
        v < 110 -> 1
        v < 203 -> 2
        else -> 3
    }

    fun frameMs(fps: Int): Long = (1000.0 / fps.coerceIn(MIN_FPS, MAX_FPS)).roundToLong()

    fun grid(d: Drawing, frame: Frame): PixelGrid {
        val g = PixelGrid(d.size)
        for (y in 0 until d.size) for (x in 0 until d.size) g.put(x, y, SHADE_VALUES[frame[d.size, x, y]])
        return g
    }

    fun encode(d: Drawing, id: String): ImportedAnimation {
        val src = d.frames.map { grid(d, it) }
        val other = if (d.size >= 25) 13 else 25
        val converted = src.map { MuseumFormat.resample(it, other) }
        return ImportedAnimation(
            id, d.name, d.size,
            frames25 = if (d.size >= 25) src else converted,
            frames13 = if (d.size >= 25) converted else src,
            durations = d.frames.map { it.hold * frameMs(d.fps) },
        )
    }

    fun decode(anim: ImportedAnimation, name: String, fps: Int, size: Int): Decoded {
        val all = if (size >= 25) anim.frames25 else anim.frames13
        val truncated = all.size > MAX_FRAMES
        val frames = all.take(MAX_FRAMES)
        val durations = anim.durations.take(MAX_FRAMES)
        val useFps = if (fps > 0) fps.coerceIn(MIN_FPS, MAX_FPS)
        else (1000.0 / durations.min()).roundToInt().coerceIn(MIN_FPS, MAX_FPS)
        val ms = frameMs(useFps)
        var simplified = false
        val out = frames.mapIndexed { i, g ->
            val shades = ByteArray(size * size)
            for (y in 0 until size) for (x in 0 until size) {
                if (!g.hasLed(x, y)) continue
                val v = g[x, y]
                if (v !in SHADE_VALUES) simplified = true
                shades[y * size + x] = shadeOf(v).toByte()
            }
            Frame(shades, (durations[i].toDouble() / ms).roundToInt().coerceIn(1, MAX_HOLD))
        }
        return Decoded(Drawing(name.trim().take(MAX_NAME), size, useFps, out), truncated, simplified)
    }
}
```

Note: in a resampled (other-size) decode, averaged values are rarely exact shades, so `simplified` may be true. Callers only show `simplified` for real imports.

- [ ] **Step 4: Run the test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.studio.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/studio/DrawingCodec.kt app/src/test/java/app/backlit/studio/DrawingCodecTest.kt
git commit -m "feat(studio): drawing codec to and from Glyph Museum animations

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Library and runtime support for drawings

**Files:**
- Modify: `app/src/main/java/app/backlit/alerts/Rules.kt` (`AnimIndexEntry`)
- Modify: `app/src/main/java/app/backlit/alerts/AnimationLibrary.kt` (`@Synchronized`)
- Modify: `app/src/main/java/app/backlit/alerts/AlertsRuntime.kt`
- Modify: `app/src/main/java/app/backlit/alerts/ImportHelper.kt`
- Test: `app/src/test/java/app/backlit/alerts/AlertStoreTest.kt` (one new test)

**Interfaces:**
- Consumes: `DrawingCodec`, `Drawing` (Tasks 2–3).
- Produces:
  - `AnimIndexEntry(id, name, sourceV, kind: String = "import", fps: Int = 0)`, plus `const val KIND_DRAWING = "drawing"` and `KIND_IMPORT = "import"` in `Rules.kt`
  - `AlertsRuntime`:
    - `val drawings: Flow<List<AnimIndexEntry>>`
    - `suspend fun saveDrawing(d: Drawing, id: String?): String`
    - `suspend fun loadDrawing(id: String, deviceSize: Int): Drawing?`
    - `suspend fun importAsDrawing(json: String, name: String, deviceSize: Int): DrawingImport`
    - `suspend fun copyImportToDrawing(importId: String, deviceSize: Int): String?`
    - `fun previewAnimation(anim: GlyphAnimation, durationMs: Long)`
    - companion `const val PREVIEW_ID = "preview:studio"`
  - `sealed interface DrawingImport { data class Ok(val id: String, val name: String, val truncated: Boolean, val simplified: Boolean); data object Invalid }`
  - `ImportHelper.kt`:
    - `fun readImportFile(context, uri): Pair<String, String>?` returns (name, text)
    - `importFromUri(...)` is unchanged in behaviour
    - `suspend fun importDrawingFromUri(context, runtime, uri, deviceSize): String`

- [ ] **Step 1: Write the failing test.** Add this to `AlertStoreTest`:

```kotlin
    @Test
    fun oldImportIndexWithoutKindStillDecodes() = runBlocking {
        val prefs = PreferenceDataStoreFactory.create(scope = scope, produceFile = { tmp.root.resolve("old.preferences_pb") })
        prefs.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("anim_imports")] = """[{"id":"import:a","name":"Old","sourceV":1}]""" }
        val c = AlertStore(prefs).config.first()
        assertEquals(listOf(AnimIndexEntry("import:a", "Old", 1, kind = "import", fps = 0)), c.imports)
        val s = AlertStore(prefs)
        s.update { it.copy(imports = it.imports + AnimIndexEntry("import:d", "Mine", 4, kind = KIND_DRAWING, fps = 12)) }
        assertEquals(AnimIndexEntry("import:d", "Mine", 4, KIND_DRAWING, 12), s.config.first().imports.last())
    }
```

Add `import androidx.datastore.preferences.core.edit` to the test imports.

- [ ] **Step 2: Run the test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.alerts.AlertStoreTest'`
Expected: compilation FAIL, because there's no parameter `kind` and `KIND_DRAWING` is unresolved.

- [ ] **Step 3: Write the implementation**

In `Rules.kt`, replace `AnimIndexEntry` with:

```kotlin
const val KIND_IMPORT = "import"
const val KIND_DRAWING = "drawing"

/**
 * An animation stored on disk as Glyph Museum JSON. sourceV is the Museum "v" (1 = 25×25, 4 = 13×13).
 * kind = "drawing" for Studio drawings (editable; fps is their speed), "import" for Glyph Museum imports.
 */
@Serializable
data class AnimIndexEntry(val id: String, val name: String, val sourceV: Int, val kind: String = KIND_IMPORT, val fps: Int = 0)
```

In `AnimationLibrary.kt`, add `@Synchronized` to `save`, `load` and `delete`, because Studio saves from `Dispatchers.IO`.

In `AlertsRuntime.kt`:
- Add imports: `app.backlit.studio.Drawing`, `app.backlit.studio.DrawingCodec`, `kotlinx.coroutines.flow.map`, `kotlinx.coroutines.withContext`.
- Add `DrawingImport` above the class.
- Add these members:

```kotlin
sealed interface DrawingImport {
    data class Ok(val id: String, val name: String, val truncated: Boolean, val simplified: Boolean) : DrawingImport
    data object Invalid : DrawingImport
}
```

```kotlin
    val drawings: Flow<List<AnimIndexEntry>> = store.config.map { c -> c.imports.filter { it.kind == KIND_DRAWING } }

    @Volatile private var previewSlot: GlyphAnimation? = null

    /** "Show on Glyph" for frames that have no library id yet (the Studio editor). */
    fun previewAnimation(anim: GlyphAnimation, durationMs: Long) {
        previewSlot = anim
        preview(PREVIEW_ID, durationMs)
    }

    /** Creates (id = null) or overwrites a drawing; returns its id. */
    suspend fun saveDrawing(d: Drawing, id: String?): String {
        val newId = id ?: ("import:" + UUID.randomUUID())
        withContext(Dispatchers.IO) { library.save(DrawingCodec.encode(d, newId)) }
        val entry = AnimIndexEntry(newId, d.name, if (d.size >= 25) 1 else 4, KIND_DRAWING, d.fps)
        store.update { c ->
            c.copy(imports = if (c.imports.any { it.id == newId }) c.imports.map { if (it.id == newId) entry else it } else c.imports + entry)
        }
        return newId
    }

    suspend fun loadDrawing(id: String, deviceSize: Int): Drawing? {
        val entry = store.config.first().imports.firstOrNull { it.id == id && it.kind == KIND_DRAWING } ?: return null
        val anim = withContext(Dispatchers.IO) { library.load(entry) } ?: return null
        return DrawingCodec.decode(anim, entry.name, entry.fps, deviceSize).drawing
    }

    suspend fun importAsDrawing(json: String, name: String, deviceSize: Int): DrawingImport {
        val parsed = MuseumFormat.parse(json, "import:tmp", name) as? MuseumFormat.Result.Ok ?: return DrawingImport.Invalid
        val r = DrawingCodec.decode(parsed.animation, name, fps = 0, size = deviceSize)
        val id = saveDrawing(r.drawing, null)
        return DrawingImport.Ok(id, r.drawing.name, r.truncated, r.simplified && parsed.animation.sourceSize == deviceSize)
    }

    suspend fun copyImportToDrawing(importId: String, deviceSize: Int): String? {
        val entry = store.config.first().imports.firstOrNull { it.id == importId } ?: return null
        val anim = withContext(Dispatchers.IO) { library.load(entry) } ?: return null
        val r = DrawingCodec.decode(anim, "${entry.name} (edit)", fps = if (entry.kind == KIND_DRAWING) entry.fps else 0, size = deviceSize)
        return saveDrawing(r.drawing, null)
    }
```

Change `animationFor` so the preview slot resolves first:

```kotlin
    fun animationFor(alert: ActiveAlert): GlyphAnimation =
        (if (alert.animationId == PREVIEW_ID) previewSlot else null)
            ?: ChargePreviewAnimation.parse(alert.animationId)
            ?: library.resolve(
                alert.animationId, current.imports,
                fallback = if (alert.kind == AlertKind.CALL) BuiltInAnimations.DEFAULT_CONTACT else BuiltInAnimations.DEFAULT_DEVICE,
            )
```

Add `const val PREVIEW_ID = "preview:studio"` to the companion. Add the imports `kotlinx.coroutines.Dispatchers` (already present) and `app.backlit.anim.MuseumFormat` (already present). `ImportedAnimation` comes from `app.backlit.anim`.

In `ImportHelper.kt`, refactor the file reading into a shared helper, then add the drawing import:

```kotlin
/** Reads a picked/shared JSON file: (display name without extension, text), or null if unreadable or too big. */
fun readImportFile(context: Context, uri: Uri): Pair<String, String>? {
    val resolver = context.contentResolver
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Imported animation"
    val text = runCatching {
        resolver.openInputStream(uri)?.use { s -> s.readNBytes(MAX_BYTES + 1) }
    }.getOrNull()?.takeIf { it.size <= MAX_BYTES }?.toString(Charsets.UTF_8) ?: return null
    return name to text
}

/** Reads a Glyph Museum JSON file from [uri], imports it, and returns a message for the user. */
fun importFromUri(context: Context, runtime: AlertsRuntime, uri: Uri, deviceSize: Int): String {
    val (name, text) = readImportFile(context, uri) ?: return BAD
    return when (val r = runtime.importJson(text, name)) {
        ImportOutcome.Invalid -> BAD
        is ImportOutcome.Ok -> {
            val note = when {
                r.sourceSize == deviceSize -> ""
                r.sourceSize == 25 -> " · made for Phone (3), scaled for 4a Pro"
                else -> " · made for 4a Pro, scaled for Phone (3)"
            }
            "Imported \"${r.name}\"$note"
        }
    }
}

/** Imports a Glyph Museum JSON file as an editable Studio drawing; returns a message for the user. */
suspend fun importDrawingFromUri(context: Context, runtime: AlertsRuntime, uri: Uri, deviceSize: Int): String {
    val (name, text) = readImportFile(context, uri) ?: return BAD
    return when (val r = runtime.importAsDrawing(text, name, deviceSize)) {
        DrawingImport.Invalid -> BAD
        is DrawingImport.Ok -> buildString {
            append("Imported \"${r.name}\" as a drawing")
            if (r.truncated) append(" · first 24 frames kept")
            if (r.simplified) append(" · brightness simplified to 3 shades")
        }
    }
}
```

- [ ] **Step 4: Run tests and build**

Run: `.superpowers/runtests.sh -q 2>&1 | tail -1 && source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK`
Expected: `N tests, 0 failures, 0 errors`, then `BUILD_OK`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/alerts/ app/src/test/java/app/backlit/alerts/AlertStoreTest.kt
git commit -m "feat(studio): drawings in the animation library (save, load, import, copy, transient preview)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Canvas settings

**Files:**
- Modify: `app/src/main/java/app/backlit/data/Settings.kt`, `SettingsRepo.kt`
- Test: `app/src/test/java/app/backlit/data/SettingsRepoTest.kt`

**Interfaces:**
- Produces: `Settings.canvasDrawingId: String = ""` and `Settings.canvasToyEverBound: Boolean = false`.

- [ ] **Step 1: Write the failing test.** In `roundTripsEveryField`, extend the `Settings(...)` by adding these after `chargeToyEverBound = true,`:

```kotlin
            canvasDrawingId = "import:q", canvasToyEverBound = true,
```

Then add this test:

```kotlin
    @Test
    fun canvasDefaults() = runBlocking {
        val s = repo().settings.first()
        assertEquals("", s.canvasDrawingId)
        assertEquals(false, s.canvasToyEverBound)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.data.SettingsRepoTest'`
Expected: compilation FAIL, because there's no parameter `canvasDrawingId`.

- [ ] **Step 3: Write the implementation**

In `Settings.kt`, add these after `chargeToyEverBound`:

```kotlin
    val canvasDrawingId: String = "",
    val canvasToyEverBound: Boolean = false,
```

In `SettingsRepo.kt`, add these keys after `CHARGE_BOUND`:

```kotlin
        private val CANVAS_DRAWING = stringPreferencesKey("canvas_drawing_id")
        private val CANVAS_BOUND = booleanPreferencesKey("canvas_toy_ever_bound")
```

Add these to `toSettings()` after `chargeToyEverBound = ...,`:

```kotlin
                canvasDrawingId = this[CANVAS_DRAWING] ?: d.canvasDrawingId,
                canvasToyEverBound = this[CANVAS_BOUND] ?: d.canvasToyEverBound,
```

Add these to `write()` after `this[CHARGE_BOUND] = ...`:

```kotlin
            this[CANVAS_DRAWING] = s.canvasDrawingId
            this[CANVAS_BOUND] = s.canvasToyEverBound
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.data.SettingsRepoTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/data/ app/src/test/java/app/backlit/data/SettingsRepoTest.kt
git commit -m "feat(studio): canvas toy settings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Canvas hint, drawing choice and the Canvas toy

**Files:**
- Create: `app/src/main/java/app/backlit/studio/CanvasHint.kt`
- Create: `app/src/main/java/app/backlit/glyph/CanvasToyService.kt`
- Create: `app/src/main/res/drawable/ic_canvas_preview.xml`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/app/backlit/studio/CanvasHintTest.kt`

**Interfaces:**
- Consumes: `Settings.canvas*` (Task 5), `AlertsRuntime.drawings`/`importedAnimation`/`animationFor`/`bus` (Task 4), and the existing toy plumbing (`GlyphOutput`, `FramePacer`, `FrameEncoder`, `ModeTracker.msUntilActive`, `ToyPresence`).
- Produces:
  - `object CanvasHint`:
    - `fun frame(size: Int): PixelGrid`
    - `fun pick(chosenId: String, ids: List<String>): String?`
    - `fun next(chosenId: String, ids: List<String>): String?`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasHintTest {

    @Test
    fun hintIsVisibleAtBothSizes() {
        for (size in listOf(25, 13)) {
            val g = CanvasHint.frame(size)
            assertTrue(g.litCount() >= 5)
            for (y in 0 until size) for (x in 0 until size) if (g[x, y] > 0) assertTrue(g.hasLed(x, y))
        }
    }

    @Test
    fun pickFallsBack() {
        assertEquals("b", CanvasHint.pick("b", listOf("a", "b")))
        assertEquals("a", CanvasHint.pick("gone", listOf("a", "b")))     // deleted → first drawing
        assertEquals("a", CanvasHint.pick("", listOf("a", "b")))
        assertNull(CanvasHint.pick("a", emptyList()))                    // nothing to show → hint
    }

    @Test
    fun nextWraps() {
        assertEquals("b", CanvasHint.next("a", listOf("a", "b")))
        assertEquals("a", CanvasHint.next("b", listOf("a", "b")))
        assertEquals("b", CanvasHint.next("gone", listOf("a", "b")))     // from the fallback "a"
        assertNull(CanvasHint.next("", emptyList()))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.studio.CanvasHintTest'`
Expected: compilation FAIL, because `CanvasHint` is unresolved.

- [ ] **Step 3: Write `CanvasHint.kt`**

```kotlin
package app.backlit.studio

import app.backlit.render.PixelGrid

/** What the Canvas toy shows: which drawing, the next one on long press, and a hint when there are none. */
object CanvasHint {
    /** A small dotted pencil, pointing down-left. */
    fun frame(size: Int): PixelGrid {
        val g = PixelGrid(size)
        if (size >= 25) {
            for (i in 0..8 step 2) g.put(8 + i, 16 - i, 150)                  // dotted body
            g.put(6, 18, 255); g.put(7, 17, 70)                               // tip
            g.put(17, 7, 255); g.put(18, 6, 255)                              // eraser end
        } else {
            for (i in 0..4 step 2) g.put(4 + i, 8 - i, 150)
            g.put(3, 9, 255)
            g.put(9, 3, 255); g.put(10, 2, 255)
        }
        return g
    }

    fun pick(chosenId: String, ids: List<String>): String? = if (chosenId in ids) chosenId else ids.firstOrNull()

    fun next(chosenId: String, ids: List<String>): String? {
        val cur = pick(chosenId, ids) ?: return null
        return ids[(ids.indexOf(cur) + 1) % ids.size]
    }
}
```

Run `.superpowers/runtests.sh --tests 'app.backlit.studio.CanvasHintTest'` and expect PASS (3 tests).

- [ ] **Step 4: Write the toy service**

`app/src/main/java/app/backlit/glyph/CanvasToyService.kt`:

```kotlin
package app.backlit.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.AnimIndexEntry
import app.backlit.alerts.ToyPresence
import app.backlit.anim.GlyphAnimation
import app.backlit.anim.ImportedAnimation
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import app.backlit.studio.CanvasHint
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Shows one of your Studio drawings; long press cycles through them. */
class CanvasToyService : Service() {

    private var scope: CoroutineScope? = null
    private var renderJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private var settings = Settings()
    private var alerts: AlertsRuntime? = null
    private var drawings: List<AnimIndexEntry> = emptyList()
    private var shownId: String? = null
    private var shownSince = 0L

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> {
                    val next = CanvasHint.next(settings.canvasDrawingId, drawings.map { it.id }) ?: return
                    scope?.launch { repo.update { it.copy(canvasDrawingId = next) } }
                }
                GlyphToy.EVENT_AOD -> { modes.onAodEvent(System.currentTimeMillis()); kick() }
            }
        }
    }
    private val messenger = Messenger(handler)
    private val rekick = Runnable { kick() }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        if (profile == DeviceProfile.UNSUPPORTED) return messenger.binder

        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "canvas toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        ToyPresence.enter()
        val rt = AlertsRuntime.get(this).also { alerts = it }
        rt.toyChanged()
        output = GlyphOutput(this, profile) { kick() }.also { it.connect() }
        s.launch { repo.update { if (it.canvasToyEverBound) it else it.copy(canvasToyEverBound = true) } }
        s.launch { repo.settings.collect { settings = it; kick() } }
        s.launch { rt.drawings.collect { drawings = it; kick() } }
        s.launch { rt.bus.collect { kick() } }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(rekick)
        renderJob?.cancel()
        renderJob = null
        scope?.cancel()
        scope = null
        output?.close()
        output = null
        if (alerts != null) {
            ToyPresence.leave()
            alerts?.toyChanged()
            alerts = null
        }
        return false
    }

    private fun isAod() = modes.mode(System.currentTimeMillis()) == Mode.AOD

    private fun kick() {
        val s = scope ?: return
        if (renderJob?.isActive == true) return
        handler.removeCallbacks(rekick)
        renderJob = s.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val now = AlertsRuntime.now()
                val alert = alerts?.bus?.value
                val aod = isAod()
                val anim = current(now)
                val grid = runCatching {
                    when {
                        alert != null -> alerts!!.animationFor(alert).frame(profile.size, now - alert.startedAt)
                        anim == null -> CanvasHint.frame(profile.size)
                        aod -> anim.frame(profile.size, 0)
                        else -> anim.frame(profile.size, now - shownSince)
                    }
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = alert == null && aod))
                val multiFrame = ((anim as? ImportedAnimation)?.durations?.size ?: 1) > 1
                val animating = alert != null || (!aod && multiFrame)
                if (!animating) {
                    if (aod) modes.msUntilActive(System.currentTimeMillis())?.let { handler.postDelayed(rekick, it + 100) }
                    break
                }
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    /** The drawing to show (restarting its loop when it changes), or null for the hint. */
    private fun current(now: Long): GlyphAnimation? {
        val id = CanvasHint.pick(settings.canvasDrawingId, drawings.map { it.id })
        if (id != shownId) { shownId = id; shownSince = now }
        return id?.let { alerts?.importedAnimation(it) }
    }

    private companion object {
        const val TAG = "BacklitCanvas"
        const val FRAME_MS = 50L
    }
}
```

- [ ] **Step 5: Add the manifest entry, strings and toy image**

`strings.xml`, after `charge_toy_summary`:

```xml
    <string name="canvas_toy_name">Backlit Canvas</string>
    <string name="canvas_toy_summary">Shows your own drawings from Backlit Studio. Long press for the next one.</string>
```

`AndroidManifest.xml`, right after the closing `</service>` of `.glyph.ChargeToyService`:

```xml
        <service
            android:name=".glyph.CanvasToyService"
            android:exported="true"
            tools:ignore="ExportedService">
            <intent-filter>
                <action android:name="com.nothing.glyph.TOY" />
            </intent-filter>
            <meta-data
                android:name="com.nothing.glyph.toy.name"
                android:resource="@string/canvas_toy_name" />
            <meta-data
                android:name="com.nothing.glyph.toy.image"
                android:resource="@drawable/ic_canvas_preview" />
            <meta-data
                android:name="com.nothing.glyph.toy.summary"
                android:resource="@string/canvas_toy_summary" />
            <meta-data
                android:name="com.nothing.glyph.toy.longpress"
                android:value="1" />
            <meta-data
                android:name="com.nothing.glyph.toy.aod_support"
                android:value="1" />
        </service>
```

`res/drawable/ic_canvas_preview.xml` is a circle with a pencil:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp"
    android:height="96dp"
    android:viewportWidth="96"
    android:viewportHeight="96">
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="6"
        android:pathData="M48,6a42,42 0,1 1,0 84a42,42 0,1 1,0 -84" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF"
        android:strokeLineCap="round"
        android:strokeWidth="7"
        android:pathData="M32,64L60,36" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M26,70l4,-10l6,6z" />
</vector>
```

- [ ] **Step 6: Build and run the full suite**

Run: `source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK && .superpowers/runtests.sh -q 2>&1 | tail -1`
Expected: `BUILD_OK`, then `N tests, 0 failures, 0 errors`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/backlit/studio/CanvasHint.kt app/src/test/java/app/backlit/studio/CanvasHintTest.kt app/src/main/java/app/backlit/glyph/CanvasToyService.kt app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/main/res/drawable/ic_canvas_preview.xml
git commit -m "feat(studio): Backlit Canvas toy

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: STUDIO tab, sharing and navigation

**Files:**
- Create: `app/src/main/java/app/backlit/ui/StudioScreen.kt`, `app/src/main/java/app/backlit/ui/Share.kt`, `app/src/main/res/xml/share_paths.xml`
- Modify: `app/src/main/java/app/backlit/ui/HomeScreen.kt` (hoisted tab, 5th chip, `onEdit`)
- Modify: `app/src/main/java/app/backlit/MainActivity.kt` (tab state, `Screen.EDITOR`, editing id)
- Modify: `AndroidManifest.xml` (FileProvider)

**Interfaces:**
- Consumes: `AlertsRuntime.drawings`/`library`/`deleteImport`/`importedAnimation` and `importDrawingFromUri` (Task 4), `Settings.canvas*` (Task 5).
- Produces:
  - `@Composable fun StudioTab(settings, profile, onUpdate, onEdit: (String?) -> Unit)` (`null` means a new drawing)
  - `fun shareAnimation(context: Context, anim: ImportedAnimation, name: String)`
  - `enum class Screen { HOME, SETUP, LOCATION, ABOUT, EDITOR }`
  - `HomeScreen(settings, profile, tab: Int, onTab: (Int) -> Unit, onUpdate, onNavigate, onEdit: (String?) -> Unit)`
  - The editor screen comes from Task 8. In this task, `MainActivity` routes `Screen.EDITOR` to `EditorScreen(...)`, so create a minimal stub in Task 7 Step 1 and Task 8 replaces it.

This is UI glue. Its gate is a clean build plus the full suite, and the device checks happen in Task 10.

- [ ] **Step 1: Create the editor stub so navigation compiles**

`app/src/main/java/app/backlit/ui/EditorScreen.kt`. Task 8 replaces the whole file.

```kotlin
package app.backlit.ui

import androidx.compose.runtime.Composable
import app.backlit.glyph.DeviceProfile

@Composable
fun EditorScreen(drawingId: String?, profile: DeviceProfile, onClose: () -> Unit) {
    ScreenHeader("STUDIO", onBack = onClose)
}
```

- [ ] **Step 2: Write `Share.kt` and the FileProvider config**

```kotlin
package app.backlit.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.backlit.anim.ImportedAnimation
import app.backlit.anim.MuseumFormat
import java.io.File

/** Shares an animation as a Glyph Museum JSON file through the system share sheet. */
fun shareAnimation(context: Context, anim: ImportedAnimation, name: String) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val safe = name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim().ifBlank { "drawing" }
    val file = File(dir, "$safe.json").apply { writeText(MuseumFormat.toJson(anim)) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share $safe").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
```

`app/src/main/res/xml/share_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="share" path="share/" />
</paths>
```

In `AndroidManifest.xml`, inside `<application>` after the last `</service>`:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.share"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/share_paths" />
        </provider>
```

- [ ] **Step 3: Write `StudioScreen.kt`**

```kotlin
package app.backlit.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.AnimIndexEntry
import app.backlit.alerts.importDrawingFromUri
import app.backlit.anim.ImportedAnimation
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.studio.CanvasHint
import app.backlit.studio.MAX_NAME
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StudioTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, onEdit: (String?) -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val drawings by runtime.drawings.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<AnimIndexEntry?>(null) }
    var deleting by remember { mutableStateOf<AnimIndexEntry?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val onCanvas = CanvasHint.pick(settings.canvasDrawingId, drawings.map { it.id })

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { message = importDrawingFromUri(context, runtime, uri, size) }
    }

    if (profile != DeviceProfile.UNSUPPORTED && !settings.canvasToyEverBound) {
        Notice("Turn on Backlit Canvas in Glyph Toys (Settings → Glyph Interface → Glyph Toys) to show a drawing on the back.")
    }
    message?.let { Notice(it) }

    Text("MY DRAWINGS", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    if (drawings.isEmpty()) {
        Text("Draw your own pictures and animations for the Glyph. Use them for calls, devices, charging, or on the Canvas toy.",
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
    }
    drawings.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { e ->
                val anim = remember(e) { runtime.library.load(e) }
                Column(
                    Modifier.weight(1f).border(1.dp, if (expanded == e.id) BacklitColors.White else BacklitColors.Line)
                        .clickable { expanded = if (expanded == e.id) null else e.id }.padding(4.dp),
                ) {
                    LoopingPreview(anim, size)
                    Text(e.name.uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    if (e.id == onCanvas) Text("● ON CANVAS", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Red)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
        val open = row.firstOrNull { it.id == expanded }
        if (open != null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SquareChip("EDIT", true, { onEdit(open.id) }, Modifier.weight(1f))
                SquareChip("CANVAS", false, { onUpdate { it.copy(canvasDrawingId = open.id) } }, Modifier.weight(1f))
                SquareChip("SHARE", false, {
                    (runtime.library.load(open) as ImportedAnimation?)?.let { shareAnimation(context, it, open.name) }
                }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SquareChip("RENAME", false, { renaming = open }, Modifier.weight(1f))
                SquareChip("DELETE", false, { deleting = open }, Modifier.weight(1f))
            }
        }
    }

    SquareChip("+ NEW DRAWING", true, { onEdit(null) }, Modifier.fillMaxWidth().padding(top = 12.dp))
    SquareChip("IMPORT FROM GLYPH MUSEUM", false, {
        importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
    Spacer(Modifier.height(8.dp))

    renaming?.let { e ->
        var name by remember(e.id) { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(MAX_NAME) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runtime.loadDrawing(e.id, size)?.let { d -> runtime.saveDrawing(d.copy(name = name.trim().ifBlank { e.name }), e.id) }
                    }
                    renaming = null
                }) { Text("SAVE") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("CANCEL") } },
        )
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${e.name}\"?") },
            text = { Text("Contacts, devices or charging that use it go back to their default.") },
            confirmButton = { TextButton(onClick = { runtime.deleteImport(e.id); expanded = null; deleting = null }) { Text("DELETE") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("CANCEL") } },
        )
    }
}

/** A small looping preview of a stored animation (blank if it failed to load). */
@Composable
internal fun LoopingPreview(anim: app.backlit.anim.GlyphAnimation?, size: Int) {
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(anim) {
        val start = System.currentTimeMillis()
        while (true) { delay(50); t = System.currentTimeMillis() - start }
    }
    MatrixPreview(anim?.frame(size, t) ?: app.backlit.render.PixelGrid(size), Modifier.fillMaxWidth().padding(2.dp))
}
```

Note: `runtime.library.load(e)` returns `ImportedAnimation?`, so the `as ImportedAnimation?` cast is a no-op that keeps the type explicit. If the compiler flags it as redundant, drop it.

- [ ] **Step 4: Hoist the tab, add the 5th chip and the `onEdit` route**

In `HomeScreen.kt`:
- Change the signature to:
  `fun HomeScreen(settings: Settings, profile: DeviceProfile, tab: Int, onTab: (Int) -> Unit, onUpdate: ((Settings) -> Settings) -> Unit, onNavigate: (Screen) -> Unit, onEdit: (String?) -> Unit)`
- Delete the line `var tab by rememberSaveable { mutableIntStateOf(0) }`.
- Add `EDITOR` to `enum class Screen`.

Replace the chip row and the `when` with:

```kotlin
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("CLOCK", "MUSIC", "ALERTS", "CHARGE", "STUDIO").forEachIndexed { i, label ->
                SquareChip(label, selected = tab == i, onClick = { onTab(i) }, modifier = Modifier.weight(1f))
            }
        }

        when (tab) {
            0 -> ClockTab(settings, profile, onUpdate, onNavigate)
            1 -> MusicTab(settings, profile, onUpdate)
            2 -> AlertsTab(profile, onEdit)
            3 -> ChargeTab(settings, profile, onUpdate)
            else -> StudioTab(settings, profile, onUpdate, onEdit)
        }
```

`AlertsTab` gains the `onEdit` parameter in Task 9. For this task, change its signature now to `fun AlertsTab(profile: DeviceProfile, onEditDrawing: (String?) -> Unit = {})` so it compiles. Task 9 uses it.

In `MainActivity.kt`, inside `setContent` after `var screen ...`:

```kotlin
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var editingId by rememberSaveable { mutableStateOf<String?>(null) }
```

Replace the `when (screen)` block with:

```kotlin
                    BackHandler(enabled = screen != Screen.HOME && screen != Screen.EDITOR && screen != null) { screen = Screen.HOME }
                    when (screen) {
                        null, Screen.HOME -> HomeScreen(s, profile, tab, { tab = it }, update, { screen = it }) { id ->
                            editingId = id
                            screen = Screen.EDITOR
                        }
                        Screen.SETUP -> SetupScreen(onDone = { screen = Screen.HOME })
                        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.HOME })
                        Screen.LOCATION -> LocationScreen(s, update, onBack = { screen = Screen.HOME })
                        Screen.EDITOR -> EditorScreen(editingId, profile, onClose = { screen = Screen.HOME })
                    }
```

This replaces the existing `BackHandler` line, because the editor handles Back itself. Add the imports `androidx.compose.runtime.mutableIntStateOf` and `app.backlit.ui.EditorScreen`. Remove the now-unused `mutableIntStateOf`/`rememberSaveable` imports from `HomeScreen.kt` only if the compiler warns.

- [ ] **Step 5: Build and run the full suite**

Run: `source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK && .superpowers/runtests.sh -q 2>&1 | tail -1`
Expected: `BUILD_OK`, then `N tests, 0 failures, 0 errors`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/backlit/ui/ app/src/main/java/app/backlit/MainActivity.kt app/src/main/AndroidManifest.xml app/src/main/res/xml/share_paths.xml
git commit -m "feat(studio): STUDIO tab, sharing, editor route and hoisted tab state

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: The editor (layout A)

**Files:**
- Replace: `app/src/main/java/app/backlit/ui/EditorScreen.kt`

**Interfaces:**
- Consumes:
  - `EditorState`, `Drawing`, `Raster`, `PixelFontText`, `SHADE_VALUES`, `MAX_FRAMES`, `MIN_FPS`, `MAX_FPS`, `MAX_NAME` (Tasks 1–2)
  - `DrawingCodec` (Task 3)
  - `AlertsRuntime.loadDrawing`/`saveDrawing`/`previewAnimation` (Task 4)
- Produces: `@Composable fun EditorScreen(drawingId: String?, profile: DeviceProfile, onClose: () -> Unit)`

This is UI. Its gate is a clean build plus the full suite, and drawing by finger is checked in Task 10. All editing logic is already tested in `EditorState`.

- [ ] **Step 1: Write the editor**

```kotlin
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
```

Notes for the implementer:
- The grid size is `n`. Inside `pointerInput` and `Canvas`, `size` is Compose's pixel size (`IntSize` or `Size`). Never name the grid size `size` in this file.
- The blank-name rule ("Drawing N", spec §10) is simplified to "Drawing" here. Record a ruling if you keep it.
- Inside `awaitEachGesture`, `awaitPointerEvent` returns events for all pointers. Only the first pointer is followed.

- [ ] **Step 2: Build and run the full suite**

Run: `source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK && .superpowers/runtests.sh -q 2>&1 | tail -1`
Expected: `BUILD_OK`, then `N tests, 0 failures, 0 errors`. Fix compile errors against the real Compose API without changing behaviour, and ledger any deviation.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/backlit/ui/EditorScreen.kt
git commit -m "feat(studio): pixel editor (layout A) with tools, frames, speed and Show on Glyph

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Labels and Edit in Studio in ALERTS

**Files:**
- Modify: `app/src/main/java/app/backlit/ui/AlertsScreen.kt`

**Interfaces:**
- Consumes: `AnimIndexEntry.kind`, `KIND_DRAWING`, `AlertsRuntime.copyImportToDrawing` (Task 4), and `onEditDrawing` from Task 7's signature.
- Produces: in the ALERTS ANIMATIONS library, each import shows a "DRAWING" or "IMPORT" tag. Imports show **EDIT IN STUDIO** next to DELETE, and drawings show **EDIT**.

- [ ] **Step 1: Change the library cell**

In `AlertsTab`, replace the block inside `if (anim.id.startsWith("import:")) { ... }` in the ANIMATIONS library with:

```kotlin
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
```

Add `import app.backlit.alerts.KIND_DRAWING`.

- [ ] **Step 2: Build and run the full suite**

Run: `source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK && .superpowers/runtests.sh -q 2>&1 | tail -1`
Expected: `BUILD_OK`, then `N tests, 0 failures, 0 errors`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/backlit/ui/AlertsScreen.kt
git commit -m "feat(studio): drawing/import labels and Edit in Studio in the ALERTS library

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: On-device checks and docs

**Files:**
- Modify: `docs/testing/device-checklist.md`, `README.md`

- [ ] **Step 1: Install**

Run: `source .superpowers/env.sh && adb devices && ./gradlew -q :app:installDebug && adb logcat -c`
Expected: the device is listed and the install succeeds. If no device is listed, ask the user to plug in the phone with USB debugging on.

- [ ] **Step 2: Run the spec §11 device checklist with the user and record each result**

1. STUDIO → **+ NEW DRAWING**. Draw with PEN (a fast drag gives a continuous line), ERASE, LINE, ○, FILL and TEXT. Then test MIRROR, undo/redo, shift arrows and CLR.
2. Add 3 frames using + (each a copy) and change them. Set hold ×2 on one and speed 6. ▶ PLAY loops in the editor, and SHOW ON GLYPH plays it on the matrix with the Glyph idle.
3. SAVE asks for a name. The drawing appears in STUDIO, in the ALERTS contact animation picker and in CHARGE → Plug-in animation.
4. Turn on Backlit Canvas in Glyph Toys. It shows the drawing marked ● ON CANVAS. Long press goes to the next drawing, and AOD shows frame 1.
5. Delete the drawing on the Canvas. The toy falls back to another drawing, or to the pencil hint.
6. SHARE opens the share sheet. Sharing to Backlit re-imports the file, as an ALERTS import.
7. STUDIO → IMPORT FROM GLYPH MUSEUM with the real JSON from the Alerts tests imports as a drawing (note the message). In ALERTS → ANIMATIONS, EDIT IN STUDIO on an import opens an editable copy.
8. Leave the editor with unsaved changes: "Discard changes?" appears. Returning from the editor keeps the STUDIO tab selected.

Read `adb logcat -d -s BacklitCanvas:V AndroidRuntime:E` after each step for errors.

- [ ] **Step 3: Update the docs**

Append this to `docs/testing/device-checklist.md`:

```markdown
## Pixel Studio + Canvas toy (Phone (3))

- [ ] Tools by finger: pen (continuous on fast drags), erase, line, circle, fill, text; mirror; undo/redo; shift; clear
- [ ] Frames: + copies, hold ×2, speed; ▶ PLAY in editor; SHOW ON GLYPH on the matrix
- [ ] Save asks for a name; drawing appears in STUDIO, ALERTS pickers and CHARGE pickers
- [ ] Backlit Canvas toy shows the chosen drawing; long press cycles; AOD shows frame 1
- [ ] Deleting the Canvas drawing falls back to another drawing / the pencil hint
- [ ] SHARE opens the share sheet; the shared file re-imports
- [ ] Import from Glyph Museum into STUDIO; EDIT IN STUDIO on an ALERTS import
- [ ] Discard prompt on unsaved Back; STUDIO tab kept after leaving the editor
```

In `README.md`, add a section after the Backlit Charge features:

```markdown
### Backlit Studio + Canvas (Glyph Toy)
- Draw your own pictures and animations at your phone's matrix size, in 3 shades: pen, erase, line,
  circle, fill, text, mirror, undo/redo and shift, with up to 24 frames, a speed setting and per-frame hold.
- Drawings appear everywhere Backlit picks an animation (contacts, devices, charging), can be shared as
  Glyph Museum JSON, and Glyph Museum files import as editable drawings.
- **Backlit Canvas** shows a drawing on the back; long-press for the next one. AOD shows its first frame.
```

Add a row to the docs table:

```markdown
| Studio + Canvas | [`2026-10-05-backlit-studio-design.md`](docs/superpowers/specs/2026-10-05-backlit-studio-design.md) | [`2026-10-05-backlit-studio.md`](docs/superpowers/plans/2026-10-05-backlit-studio.md) |
```

In the Roadmap, remove the "Pixel studio" line.

- [ ] **Step 4: Commit**

```bash
git add docs/testing/device-checklist.md README.md
git commit -m "docs: pixel studio device checklist and README

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
