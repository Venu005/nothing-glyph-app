# Backlit Charge Toy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new Glyph toy, "Backlit Charge". It shows the battery level in one of four styles (Sprout, Buddy, Big number, Moon). It plays a plug-in animation, a gentle charging loop and a once-per-charge "done" moment at a user-set target, and it can use Glyph Museum imports for the plug-in and done moments. The settings live in a new CHARGE tab.

**Architecture:**
- Pure Kotlin `render/charge/` (styles and drawing helpers) and `charge/` (the `ChargeSession` state machine and `ChargePreviewAnimation`), unit-tested on the JVM.
- `glyph/ChargeToyService` copies the `ClockToyService` structure. It listens to `ACTION_BATTERY_CHANGED` only while bound, feeds `ChargeSession`, and renders at 50 ms while animating.
- Alerts are reused for the alert bus, imports and "Show on Glyph" previews.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, kotlinx-coroutines, JUnit 4, Nothing GlyphMatrix SDK.

**Spec:** `docs/superpowers/specs/2026-10-04-backlit-charge-design.md`. The visual source of truth is `docs/superpowers/mockups/2026-10-04-charge-styles.html`. The Kotlin below is a line-by-line port of it.

## Global Constraints

- Work on branch `feat/backlit-charge`. The package root is `app.backlit`.
- In every fresh shell, run `source .superpowers/env.sh`. Run unit tests with a summary via `.superpowers/runtests.sh [gradle args]`, for example `.superpowers/runtests.sh --tests 'app.backlit.render.charge.*'`.
- `render/charge/` and `charge/` import no `android.*`, `androidx.*` or `com.nothing.*`. Only `glyph/` imports `com.nothing.ketchum.*`.
- No new permissions. No background work: the battery receiver is registered only while the toy is bound.
- The brightness mapping from mockup to Kotlin is `v (0..1) → (v * 255).roundToInt()`. A mockup `Math.round(x)` becomes `x.px()` (round half up). Mockup `g.p` becomes `PixelGrid.plot` and mockup `g.s` becomes `PixelGrid.put`.
- Style ids are `sprout`, `buddy`, `number` and `moon`, in that order. The default is `moon`.
- **Timings:** plug-in 5000 ms, done 3500 ms, "Show on Glyph" for still and charging 3000 ms, frame pacing 50 ms. The % reveal starts at 3800 ms.
- **Settings defaults:** `chargeStyle="moon"`, `chargeTarget=100` (stored 50..100 in steps of 5 and clamped on read), `chargePlugInAnim=""`, `chargeDoneAnim=""`, `chargeToyEverBound=false`.
- **Preview ids:** `charge:<styleId>:<moment>`, where moment is `still|plug_in|charging|done`. The demo level is 62.
- **Spec refinement:** Big number `still()` draws the number only, with no bolt and no rim dot, because the bolt means "charging".
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Battery update spam.** `ACTION_BATTERY_CHANGED` fires for voltage and temperature changes with the same level and plug state. That must not restart the plug-in or replay done. This is pinned by `ChargeSessionTest.repeatedIdenticalUpdatesChangeNothing` (Task 6).
2. **Plugging in when the battery is already at or above the target** (for example, the phone stops at 80 % with battery protection and the target is 80). Done must not play, because there is no crossing. This is pinned by `ChargeSessionTest.plugInAlreadyAboveTargetNeverPlaysDone` (Task 6).
3. **An alert that lasts longer than the plug-in.** When the alert ends, a pending DONE must still play in full, not be skipped as already expired. This is pinned by `ChargeSessionTest.lateTickStillPlaysFullDone` (Task 6).
4. **Level 0 and level 100 edge frames.** No crash, no out-of-mask pixels, and the Moon is empty at 0 and full at 100. This is pinned by `ChargeStylesTest.allStylesAllLevelsStayInMask` and `MoonStyleTest.emptyAtZeroFullAtHundred` (Tasks 2 and 6).
5. **A charge setting pointing at a deleted import, or at a built-in alert id.** The style's own animation must be used, never Heartbeat. This is pinned by `AlertsChargeHooksTest.importedAnimationIgnoresNonImports` (Task 8).

---

## File Structure

```
app/src/main/java/app/backlit/render/
  PixelFont5x7.kt              5×7 digits (Big number at 25×25)
  charge/ChargeStyle.kt        interface ChargeStyle
  charge/ChargeKit.kt          easing, brightness, dot/line/polyline, % reveal, ordered rim ring, midpoint circle
  charge/MoonStyle.kt  charge/NumberStyle.kt  charge/SproutStyle.kt  charge/BuddyStyle.kt
  charge/ChargeStyles.kt       registry: all, byId, next
app/src/main/java/app/backlit/charge/
  ChargeSession.kt             Battery, Moment, Show, ChargeSession (pure state machine)
  ChargePreviewAnimation.kt    GlyphAnimation wrapper for "charge:<style>:<moment>" ids
app/src/main/java/app/backlit/data/Settings.kt, SettingsRepo.kt   (+5 charge fields)
app/src/main/java/app/backlit/alerts/AlertCoordinator.kt          (preview durationMs)
app/src/main/java/app/backlit/alerts/AlertsRuntime.kt             (preview duration, charge ids, importedAnimation)
app/src/main/java/app/backlit/glyph/ChargeToyService.kt           the toy
app/src/main/AndroidManifest.xml, res/values/strings.xml, res/drawable/ic_charge_preview.xml
app/src/main/java/app/backlit/ui/ChargeScreen.kt                  CHARGE tab
app/src/main/java/app/backlit/ui/HomeScreen.kt                    (+ CHARGE chip)
docs/testing/device-checklist.md, README.md                       (+ Charge)
```

---

### Task 1: Drawing kit, 5×7 font and the ChargeStyle interface

**Files:**
- Create: `app/src/main/java/app/backlit/render/PixelFont5x7.kt`
- Create: `app/src/main/java/app/backlit/render/charge/ChargeStyle.kt`
- Create: `app/src/main/java/app/backlit/render/charge/ChargeKit.kt`
- Test: `app/src/test/java/app/backlit/render/charge/ChargeKitTest.kt`

**Interfaces:**
- Produces:
  - `interface ChargeStyle { val id: String; val label: String; fun still(size: Int, level: Int): PixelGrid; fun plugIn(size: Int, level: Int, tMs: Long): PixelGrid; fun charging(size: Int, level: Int, tMs: Long): PixelGrid; fun done(size: Int, tMs: Long): PixelGrid }`
  - `object PixelFont5x7 { const val WIDTH = 5; const val HEIGHT = 7; fun digit(g, d, x, y, b) }`
  - `object ChargeKit`:
    - `easeOut(t: Double): Double`, `easeInOut(t: Double): Double`, `b(v: Double): Int`
    - `PixelGrid.dot(x: Double, y: Double, v: Double)`, `PixelGrid.set(x: Double, y: Double, v: Double)`
    - `line(g, x0, y0, x1, y1, v)`, `polyline(g, pts: List<Pair<Double, Double>>, frac: Double, v: Double, thick: Boolean)`
    - `number(g, text: String, cx: Double, cy: Double, big: Boolean, v: Double, gap: Int = 1)`
    - `reveal(g: PixelGrid, level: Int, tMs: Long): PixelGrid`
    - `circlePoints(cx: Int, cy: Int, r: Int): List<Pair<Int, Int>>`, `rimRing(size: Int): List<Pair<Int, Int>>`
    - `CHECK: Map<Int, List<Pair<Double, Double>>>`
    - `const val REVEAL_AT_MS = 3800L`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.render.charge

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class ChargeKitTest {

    @Test
    fun brightnessAndEasing() {
        assertEquals(255, ChargeKit.b(1.0))
        assertEquals(0, ChargeKit.b(-0.2))
        assertEquals(89, ChargeKit.b(0.35))
        assertEquals(0.0, ChargeKit.easeOut(0.0), 1e-9)
        assertEquals(1.0, ChargeKit.easeOut(2.0), 1e-9)
        assertEquals(0.5, ChargeKit.easeInOut(0.5), 1e-9)
    }

    @Test
    fun rimRingIsClosedAndEightConnected() {
        for (size in listOf(25, 13)) {
            val p = ChargeKit.rimRing(size)
            assertTrue(p.size > 20)
            assertEquals("no duplicates", p.size, p.toSet().size)
            for (i in p.indices) {
                val (ax, ay) = p[i]
                val (bx, by) = p[(i + 1) % p.size]
                assertEquals("step $i on $size", 1, max(abs(ax - bx), abs(ay - by)))
            }
        }
    }

    @Test
    fun rimRingStartsAtTopAndGoesClockwise() {
        val p = ChargeKit.rimRing(25)
        assertEquals(12 to 1, p.first())           // r = 11 around (12,12)
        assertTrue(p[3].first > 12)                // moving right first
    }

    @Test
    fun revealClearsABoxAndDrawsDigits() {
        val g = PixelGrid(25)
        for (y in 0 until 25) for (x in 0 until 25) g.plot(x, y, 200)
        ChargeKit.reveal(g, 62, 5000)
        // "62": w = 7, x0 = 9, y0 = 10; the box is x 8..16, y 9..15
        assertEquals(0, g[8, 9]); assertEquals(0, g[16, 15])
        assertEquals(255, g[9, 10])                // top-left of '6'
        assertEquals(40, g[3, 12])                 // scene dimmed to 20 % (200 * 0.2)
        val untouched = PixelGrid(25).also { it.plot(5, 5, 200) }
        ChargeKit.reveal(untouched, 62, 3000)
        assertEquals(200, untouched[5, 5])         // before 3800 ms nothing changes
    }

    @Test
    fun bigNumberUses5x7AndGap() {
        val g = PixelGrid(25)
        ChargeKit.number(g, "62", 12.0, 13.0, big = true, v = 1.0)
        // w = 11 → x0 = 7, y0 = 10; '6' row 0 is "00110"
        assertEquals(0, g[7, 10]); assertEquals(255, g[9, 10]); assertEquals(255, g[10, 10])
        val s = PixelGrid(13)
        ChargeKit.number(s, "62", 6.0, 6.0, big = false, v = 1.0, gap = 2)
        // w = 8 → x0 = 3 (6 - 3.5 = 2.5 → px 3), y0 = 4; '6' covers x 3..5, '2' covers x 8..10
        assertEquals(255, s[3, 4]); assertEquals(0, s[6, 4]); assertEquals(0, s[7, 4]); assertEquals(255, s[8, 4])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.ChargeKitTest'`
Expected: compilation FAIL, because `ChargeKit` is unresolved.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/app/backlit/render/PixelFont5x7.kt`:

```kotlin
package app.backlit.render

/** 5×7 digits for large numbers on the 25×25 matrix. */
object PixelFont5x7 {
    const val WIDTH = 5
    const val HEIGHT = 7

    private val DIGITS = arrayOf(
        arrayOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        arrayOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        arrayOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        arrayOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
        arrayOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        arrayOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        arrayOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        arrayOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        arrayOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        arrayOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    )

    fun digit(g: PixelGrid, d: Int, x: Int, y: Int, b: Int) {
        val rows = DIGITS[d.coerceIn(0, 9)]
        for (r in 0 until HEIGHT) for (c in 0 until WIDTH) {
            if (rows[r][c] == '1') g.plot(x + c, y + r, b)
        }
    }
}
```

`app/src/main/java/app/backlit/render/charge/ChargeStyle.kt`:

```kotlin
package app.backlit.render.charge

import app.backlit.render.PixelGrid

/** One charging look. level is 0..100; times are ms since the moment started. */
interface ChargeStyle {
    val id: String
    val label: String
    /** Resting display (not charging, and always in AOD). No motion. */
    fun still(size: Int, level: Int): PixelGrid
    /** 0..5000 ms after plugging in; ends with the % reveal (except Big number). */
    fun plugIn(size: Int, level: Int, tMs: Long): PixelGrid
    /** Gentle loop while charging. */
    fun charging(size: Int, level: Int, tMs: Long): PixelGrid
    /** 0..3500 ms when the target is reached. */
    fun done(size: Int, tMs: Long): PixelGrid
}
```

`app/src/main/java/app/backlit/render/charge/ChargeKit.kt`:

```kotlin
package app.backlit.render.charge

import app.backlit.render.PixelFont
import app.backlit.render.PixelFont5x7
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Shared drawing helpers, ported from the approved mockup (docs/superpowers/mockups/2026-10-04-charge-styles.html). */
object ChargeKit {
    const val REVEAL_AT_MS = 3800L

    /** Check-mark polylines per matrix size. */
    val CHECK: Map<Int, List<Pair<Double, Double>>> = mapOf(
        25 to listOf(6.0 to 13.0, 10.0 to 17.0, 19.0 to 7.0),
        13 to listOf(3.0 to 6.0, 5.0 to 8.0, 9.0 to 4.0),
    )

    fun easeOut(t: Double): Double = 1 - (1 - t.coerceIn(0.0, 1.0)).pow(3)

    fun easeInOut(t: Double): Double {
        val c = t.coerceIn(0.0, 1.0)
        return if (c < 0.5) 2 * c * c else 1 - (-2 * c + 2).pow(2) / 2
    }

    /** Mockup brightness 0..1 → design brightness 0..255. */
    fun b(v: Double): Int = (v.coerceIn(0.0, 1.0) * 255).roundToInt()

    /** Mockup g.p: round the position, lighten. */
    fun PixelGrid.dot(x: Double, y: Double, v: Double) {
        if (v > 0) plot(x.px(), y.px(), b(v))
    }

    /** Mockup g.s: round the position, overwrite. */
    fun PixelGrid.set(x: Double, y: Double, v: Double) = put(x.px(), y.px(), b(v))

    fun line(g: PixelGrid, x0: Double, y0: Double, x1: Double, y1: Double, v: Double) {
        val steps = max(1, ceil(hypot(x1 - x0, y1 - y0) * 2).toInt())
        for (k in 0..steps) {
            val f = k.toDouble() / steps
            g.dot(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, v)
        }
    }

    /** Draws the first [frac] (0..1) of the polyline's length; thick adds a copy one row lower. */
    fun polyline(g: PixelGrid, pts: List<Pair<Double, Double>>, frac: Double, v: Double, thick: Boolean) {
        val lens = (1 until pts.size).map { hypot(pts[it].first - pts[it - 1].first, pts[it].second - pts[it - 1].second) }
        var left = frac.coerceIn(0.0, 1.0) * lens.sum()
        for (i in 1 until pts.size) {
            if (left <= 0) break
            val q = min(1.0, left / lens[i - 1])
            val (ax, ay) = pts[i - 1]
            val (bx, by) = pts[i]
            line(g, ax, ay, ax + (bx - ax) * q, ay + (by - ay) * q, v)
            if (thick) line(g, ax, ay + 1, ax + (bx - ax) * q, ay + 1 + (by - ay) * q, v)
            left -= lens[i - 1]
        }
    }

    /** Digits centred on (cx, cy): 5×7 when [big], else 3×5. */
    fun number(g: PixelGrid, text: String, cx: Double, cy: Double, big: Boolean, v: Double, gap: Int = 1) {
        val cw = if (big) PixelFont5x7.WIDTH else PixelFont.WIDTH
        val ch = if (big) PixelFont5x7.HEIGHT else PixelFont.HEIGHT
        val w = text.length * (cw + gap) - gap
        val x0 = (cx - (w - 1) / 2.0).px()
        val y0 = (cy - (ch - 1) / 2.0).px()
        text.forEachIndexed { i, c ->
            val d = c - '0'
            if (big) PixelFont5x7.digit(g, d, x0 + i * (cw + gap), y0, b(v))
            else PixelFont.digit(g, d, x0 + i * (cw + gap), y0, b(v))
        }
    }

    /** End of plug-in: dim the scene to 20 % over 250 ms, then show the % in a cleared window. */
    fun reveal(g: PixelGrid, level: Int, tMs: Long): PixelGrid {
        if (tMs < REVEAL_AT_MS) return g
        val n = g.size
        val k = max(0.2, 1 - (tMs - REVEAL_AT_MS) / 250.0)
        val raw = g.raw()
        for (i in raw.indices) if (raw[i] > 0) g.put(i % n, i / n, (raw[i] * k).roundToInt())
        val s = level.coerceIn(0, 100).toString()
        val w = s.length * 4 - 1
        val c = (n - 1) / 2.0
        val x0 = (c - (w - 1) / 2.0).px()
        val y0 = (c - 2).px()
        for (y in y0 - 1..y0 + 5) for (x in x0 - 1..x0 + w) g.put(x, y, 0)
        PixelFont.text(g, s, x0, y0, 255)
        return g
    }

    /** Midpoint (Bresenham) circle around any integer centre; may contain duplicates at octant joins. */
    fun circlePoints(cx: Int, cy: Int, r: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var x = r
        var y = 0
        var err = 1 - r
        while (x >= y) {
            for ((dx, dy) in listOf(x to y, y to x, -y to x, -x to y, -x to -y, -y to -x, y to -x, x to -y)) out += (cx + dx) to (cy + dy)
            y++
            if (err < 0) err += 2 * y + 1 else { x--; err += 2 * (y - x) + 1 }
        }
        return out
    }

    private val rims = HashMap<Int, List<Pair<Int, Int>>>()

    /** Ordered rim ring (r = 11 on 25×25, r = 5 on 13×13) starting at the top, clockwise, no duplicates. */
    fun rimRing(size: Int): List<Pair<Int, Int>> = rims.getOrPut(size) {
        val c = (size - 1) / 2
        val r = if (size >= 25) 11 else 5
        circlePoints(c, c, r).distinct().sortedBy { (x, y) ->
            val a = atan2((x - c).toDouble(), (c - y).toDouble())
            if (a < 0) a + 2 * PI else a
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.ChargeKitTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/render/PixelFont5x7.kt app/src/main/java/app/backlit/render/charge/ app/src/test/java/app/backlit/render/charge/ChargeKitTest.kt
git commit -m "feat(charge): drawing kit, 5x7 font and ChargeStyle interface

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Moon style

**Files:**
- Create: `app/src/main/java/app/backlit/render/charge/MoonStyle.kt`
- Test: `app/src/test/java/app/backlit/render/charge/MoonStyleTest.kt`

**Interfaces:**
- Consumes: `ChargeStyle`, `ChargeKit` (Task 1).
- Produces: `object MoonStyle : ChargeStyle` (id `moon`, label `Moon`).

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.render.charge

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class MoonStyleTest {

    private fun inDisc(size: Int, x: Int, y: Int): Boolean {
        val c = (size - 1) / 2.0; val r = if (size >= 25) 8.6 else 4.6
        return hypot(x - c, y - c) <= r
    }
    /** Lit moon pixels (stars sit outside the disc and can reach 127, so they're excluded). */
    private fun bright(g: PixelGrid) = (0 until g.size).sumOf { y -> (0 until g.size).count { x -> inDisc(g.size, x, y) && g[x, y] >= 120 } }
    private fun discArea(size: Int) = (0 until size).sumOf { y -> (0 until size).count { x -> inDisc(size, x, y) } }

    @Test
    fun emptyAtZeroFullAtHundred() {
        for (size in listOf(25, 13)) {
            assertEquals(0, bright(MoonStyle.still(size, 0)))
            assertEquals(discArea(size), bright(MoonStyle.still(size, 100)))
        }
    }

    @Test
    fun litAreaMatchesLevel() {
        val area = discArea(25)
        for (level in listOf(25, 50, 62, 80)) {
            val lit = bright(MoonStyle.still(25, level))
            assertTrue("level $level lit $lit", kotlin.math.abs(lit - area * level / 100.0) <= area * 0.10)
        }
    }

    @Test
    fun crescentGrowsFromTheRight() {
        val g = MoonStyle.still(25, 20)
        assertTrue(g[19, 12] >= 120)               // right edge lit
        assertTrue(g[5, 12] in 1..40)              // left edge dark (0.07 → 18)
    }

    @Test
    fun plugInEndsWithRevealAndDoneHasHalo() {
        val g = MoonStyle.plugIn(25, 62, 4500)
        assertEquals(255, g[9, 10])                // '6' of the reveal at x0 = 9, y0 = 10
        val d = MoonStyle.done(25, 1000)
        assertTrue((0 until 25).any { x -> d[x, 2] > 0 })   // halo ring at r ≈ 9.9 near the top
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.MoonStyleTest'`
Expected: compilation FAIL, because `MoonStyle` is unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package app.backlit.render.charge

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The lit phase of the moon is the battery level: a crescent when low, a full moon at 100 %. */
object MoonStyle : ChargeStyle {
    override val id = "moon"
    override val label = "Moon"

    private val STARS = mapOf(
        25 to listOf(3 to 8, 20 to 4, 22 to 15, 5 to 19, 17 to 22, 2 to 13),
        13 to listOf(1 to 4, 11 to 3, 10 to 11),
    )
    private val CRATERS = setOf(14 to 10, 15 to 10, 10 to 15, 16 to 15, 13 to 16)

    override fun still(size: Int, level: Int) = moon(size, level / 100.0, 0, halo = 0.0)

    override fun plugIn(size: Int, level: Int, tMs: Long) =
        ChargeKit.reveal(moon(size, level / 100.0 * ChargeKit.easeInOut(tMs / 3200.0), tMs, 0.0), level, tMs)

    override fun charging(size: Int, level: Int, tMs: Long) = moon(size, level / 100.0, tMs, 0.0)

    override fun done(size: Int, tMs: Long) = moon(size, 1.0, tMs, halo = min(1.0, tMs / 900.0))

    private fun moon(size: Int, f: Double, tMs: Long, halo: Double): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val c = (size - 1) / 2.0
        val r = if (big) 8.6 else 4.6
        for (y in 0 until size) for (x in 0 until size) {
            val dx = x - c
            val dy = y - c
            if (hypot(dx, dy) > r) continue
            val w = sqrt(maxOf(0.0, r * r - dy * dy))
            val lit = dx > w * (1 - 2 * f)
            val crater = big && (x to y) in CRATERS
            g.dot(x.toDouble(), y.toDouble(), if (lit) (if (crater) 0.5 else 0.8) else 0.07)
        }
        STARS.getValue(if (big) 25 else 13).forEachIndexed { i, (sx, sy) ->
            g.dot(sx.toDouble(), sy.toDouble(), 0.15 + 0.35 * (0.5 + 0.5 * sin(tMs / 900.0 + i * 1.7)))
        }
        if (halo > 0) {
            val hr = r + 1.3 + 0.4 * sin(tMs / 500.0)
            for (y in 0 until size) for (x in 0 until size) {
                val d = abs(hypot(x - c, y - c) - hr)
                if (d < 0.7) g.dot(x.toDouble(), y.toDouble(), halo * (1 - d / 0.7) * 0.6)
            }
        }
        return g
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.MoonStyleTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/render/charge/MoonStyle.kt app/src/test/java/app/backlit/render/charge/MoonStyleTest.kt
git commit -m "feat(charge): Moon style

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Big number style

**Files:**
- Create: `app/src/main/java/app/backlit/render/charge/NumberStyle.kt`
- Test: `app/src/test/java/app/backlit/render/charge/NumberStyleTest.kt`

**Interfaces:**
- Consumes: `ChargeStyle`, `ChargeKit` (`number`, `rimRing`, `polyline`, `CHECK`, `dot`, `b`).
- Produces: `object NumberStyle : ChargeStyle` (id `number`, label `Big number`).

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.render.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberStyleTest {

    @Test
    fun stillIsJustTheNumber() {
        val g = NumberStyle.still(25, 62)
        assertEquals(255, g[9, 10])                     // '6' (5×7, x0 = 7, y0 = 10, row "00110")
        assertEquals(0, g[13, 5])                       // no bolt (the bolt lives at x 11..13, y 3..7)
        assertEquals(0, ChargeKit.rimRing(25).count { (x, y) -> g[x, y] > 0 })
    }

    @Test
    fun thirteenHasATwoColumnGapBetweenDigits() {
        val g = NumberStyle.still(13, 62)
        val litCols = (0 until 13).filter { x -> (4..8).any { y -> g[x, y] > 0 } }
        assertEquals(listOf(3, 4, 5, 8, 9, 10), litCols)
        val hundred = NumberStyle.still(13, 100)       // 3 digits fall back to a 1 px gap so they fit
        assertEquals(listOf(1, 2, 3, 5, 6, 7, 9, 10, 11), (0 until 13).filter { x -> (4..8).any { y -> hundred[x, y] > 0 } })
    }

    @Test
    fun chargingHasBoltAndOneTrailingRimDot() {
        val g = NumberStyle.charging(25, 62, 0)
        assertTrue(g[11, 5] > 0 || g[12, 5] > 0)       // bolt middle row "111" at y = 5
        val ring = ChargeKit.rimRing(25)
        assertEquals(255, g[ring[0].first, ring[0].second])          // head at index 0 when t = 0
        assertEquals(8, ring.count { (x, y) -> g[x, y] > 0 })        // head + 7 tail pixels
    }

    @Test
    fun plugInCountsUp() {
        val start = NumberStyle.plugIn(25, 62, 0)       // shows "0": w = 5 → x0 = 10, row 0 "01110" → x 11..13
        assertEquals(255, start[11, 10]); assertEquals(0, start[10, 10])
        val end = NumberStyle.plugIn(25, 62, 2000)      // count finished → "62"
        assertEquals(NumberStyle.still(25, 62)[9, 10], end[9, 10])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.NumberStyleTest'`
Expected: compilation FAIL, because `NumberStyle` is unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package app.backlit.render.charge

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** A large clean %, a softly pulsing bolt and one quiet dot circling the rim (25×25). */
object NumberStyle : ChargeStyle {
    override val id = "number"
    override val label = "Big number"

    private val BOLT = listOf("001", "010", "111", "010", "100")
    private const val TRAIL = 8

    override fun still(size: Int, level: Int): PixelGrid = PixelGrid(size).also { digits(it, level.toString(), 1.0) }

    override fun plugIn(size: Int, level: Int, tMs: Long): PixelGrid =
        frame(size, (level * ChargeKit.easeOut(tMs / 1800.0)).roundToInt(), tMs)

    override fun charging(size: Int, level: Int, tMs: Long): PixelGrid = frame(size, level, tMs)

    override fun done(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        if (tMs < 1300) {
            val on = (tMs / 220) % 2 == 0L
            if (big) {
                ChargeKit.number(g, "100", 12.0, 13.0, big = true, v = if (on) 1.0 else 0.25)
                bolt(g, 1.0)
            } else {
                ChargeKit.rimRing(size).forEach { (x, y) -> g.dot(x.toDouble(), y.toDouble(), if (on) 1.0 else 0.3) }
            }
        } else {
            ChargeKit.rimRing(size).forEach { (x, y) -> g.dot(x.toDouble(), y.toDouble(), 0.35) }
            ChargeKit.polyline(g, ChargeKit.CHECK.getValue(if (big) 25 else 13), ChargeKit.easeOut((tMs - 1300) / 500.0), 1.0, thick = big)
        }
        return g
    }

    private fun frame(size: Int, shown: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        digits(g, shown.toString(), 1.0)
        val pulse = 0.35 + 0.65 * (0.5 + 0.5 * sin(tMs / 420.0))
        if (size >= 25) {
            bolt(g, pulse)
            rimDot(g, tMs)
        } else {
            g.dot(6.0, 10.0, pulse)
        }
        return g
    }

    private fun digits(g: PixelGrid, s: String, v: Double) {
        if (g.size >= 25) ChargeKit.number(g, s, 12.0, 13.0, big = true, v = v)
        else ChargeKit.number(g, s, 6.0, 6.0, big = false, v = v, gap = if (s.length < 3) 2 else 1)
    }

    private fun bolt(g: PixelGrid, v: Double) {
        BOLT.forEachIndexed { r, row -> row.forEachIndexed { k, ch -> if (ch == '1') g.dot(11.0 + k, 3.0 + r, v) } }
    }

    private fun rimDot(g: PixelGrid, tMs: Long) {
        val ring = ChargeKit.rimRing(g.size)
        val pos = (tMs / 75.0) % ring.size
        for (k in 0 until TRAIL) {
            val idx = floor(pos - k + ring.size).toInt() % ring.size
            val (x, y) = ring[idx]
            g.dot(x.toDouble(), y.toDouble(), if (k == 0) 1.0 else 0.75 * (1 - k / TRAIL.toDouble()))
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.NumberStyleTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/render/charge/NumberStyle.kt app/src/test/java/app/backlit/render/charge/NumberStyleTest.kt
git commit -m "feat(charge): Big number style

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Sprout style

**Files:**
- Create: `app/src/main/java/app/backlit/render/charge/SproutStyle.kt`
- Test: `app/src/test/java/app/backlit/render/charge/SproutStyleTest.kt`

**Interfaces:**
- Consumes: `ChargeStyle`, `ChargeKit` (`dot`, `easeInOut`, `reveal`).
- Produces: `object SproutStyle : ChargeStyle` (id `sprout`, label `Sprout`).

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.render.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SproutStyleTest {

    @Test
    fun potIsAlwaysThereAndStemHeightFollowsLevel() {
        val empty = SproutStyle.still(25, 0)
        assertTrue(empty[12, 19] > 0)                   // pot rim
        assertEquals(0, empty[12, 18])                  // no stem at 0 %
        val half = SproutStyle.still(25, 50)            // len = round(0.5 × 12) = 6 → stem y 18..13, bud at y 12
        assertTrue(half[12, 13] > 0); assertEquals(0, half[12, 11])
        val full = SproutStyle.still(25, 100)           // len = 12 → stem y 18..7, bud at y 6
        assertTrue(full[12, 7] > 0); assertTrue(full[12, 6] > 0)
    }

    @Test
    fun litCountRisesWithLevel() {
        for (size in listOf(25, 13)) {
            val counts = listOf(0, 30, 60, 100).map { SproutStyle.still(size, it).litCount() }
            assertTrue("$size $counts", counts.zipWithNext().all { (a, b) -> b >= a } && counts.last() > counts.first())
        }
    }

    @Test
    fun stillIsStaticButChargingSways() {
        assertEquals(SproutStyle.still(25, 80), SproutStyle.still(25, 80))
        val frames = (0L until 4000L step 100).map { SproutStyle.charging(25, 100, it) }.toSet()
        assertTrue(frames.size > 1)
    }

    @Test
    fun doneBloomsAFlower() {
        val g = SproutStyle.done(25, 2400)              // full bloom; sway is 0 here (sin(2.67)·0.55 < 0.5) → centre (12, 5)
        assertEquals(255, g[12, 5])
        assertTrue(g.litCount() > SproutStyle.still(25, 100).litCount())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.SproutStyleTest'`
Expected: compilation FAIL, because `SproutStyle` is unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package app.backlit.render.charge

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A little plant grows out of its pot; its height is the battery level. */
object SproutStyle : ChargeStyle {
    override val id = "sprout"
    override val label = "Sprout"

    override fun still(size: Int, level: Int): PixelGrid = PixelGrid(size).also { plant(it, level / 100.0, 0, 0.0, sway = false) }

    override fun plugIn(size: Int, level: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        plant(g, level / 100.0 * ChargeKit.easeInOut(tMs / 3000.0), tMs, 0.0, sway = true)
        return ChargeKit.reveal(g, level, tMs)
    }

    override fun charging(size: Int, level: Int, tMs: Long): PixelGrid =
        PixelGrid(size).also { plant(it, level / 100.0, tMs, 0.0, sway = true) }

    override fun done(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val (topX, topY) = plant(g, 1.0, tMs, ChargeKit.easeInOut(tMs / 1200.0), sway = true)
        if (tMs > 1200) {
            val n = if (big) 6 else 3
            for (i in 0 until n) {
                val ph = (tMs / 900.0 + i.toDouble() / n) % 1.0
                val a = i * 2.4
                val r = (if (big) 4.0 else 2.0) + ph * (if (big) 6.0 else 3.0)
                g.dot(topX + cos(a) * r, topY - (if (big) 2 else 1) + sin(a) * r * 0.8 - ph * 2, 1 - ph)
            }
        }
        return g
    }

    /** Draws pot, stem, leaves and bud/flower; returns the stem top (x, y). */
    private fun plant(g: PixelGrid, h: Double, tMs: Long, bloom: Double, sway: Boolean): Pair<Double, Int> {
        val n = g.size
        val big = n >= 25
        if (big) {
            for (x in 7..17) g.dot(x.toDouble(), 19.0, 0.8)
            for (y in 20..22) { val i = y - 20; for (x in 8 + i..16 - i) g.dot(x.toDouble(), y.toDouble(), 0.45) }
        } else {
            for (x in 4..8) g.dot(x.toDouble(), 10.0, 0.8)
            for (x in 5..7) g.dot(x.toDouble(), 11.0, 0.45)
        }
        val base = if (big) 18 else 9
        val maxH = if (big) 12 else 6
        val len = (h * maxH).px()
        val c = (n - 1) / 2.0
        var topX = c
        for (k in 0 until len) {
            val y = base - k
            val off = if (sway && k > len * 0.6 && len > 3) (sin(tMs / 900.0) * 0.6 * (k.toDouble() / len)).px() else 0
            val x = c + off
            g.dot(x, y.toDouble(), 0.9)
            topX = x
            val every = if (big) 3 else 2
            if (k > 0 && k % every == 0 && k < len - 1) {
                val side = if ((k / every) % 2 == 1) 1 else -1
                if (big) {
                    g.dot(x + side, y.toDouble(), 0.75)
                    g.dot(x + 2 * side, y - 1.0, 0.75)
                    g.dot(x + side, y - 1.0, 0.5)
                    if (sway && sin(tMs / 600.0 + k) > 0.6) g.dot(x + 3 * side, y - 1.0, 0.4)
                } else {
                    g.dot(x + side, y - 1.0, 0.75)
                }
            }
        }
        val topY = base - len + 1
        if (bloom > 0) {
            val r = if (big) 2.6 * bloom else 1.4 * bloom
            val petals = if (big) 8 else 4
            val fy = topY - (if (big) 2 else 1).toDouble()
            for (i in 0 until petals) {
                val a = i.toDouble() / petals * 2 * PI + tMs / 2000.0
                g.dot(topX + cos(a) * r, fy + sin(a) * r, 0.85)
            }
            g.dot(topX, fy, 1.0)
        } else if (len > 0) {
            g.dot(topX, topY - 1.0, 0.55)
        }
        return topX to topY
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.SproutStyleTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/render/charge/SproutStyle.kt app/src/test/java/app/backlit/render/charge/SproutStyleTest.kt
git commit -m "feat(charge): Sprout style

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Buddy style

**Files:**
- Create: `app/src/main/java/app/backlit/render/charge/BuddyStyle.kt`
- Test: `app/src/test/java/app/backlit/render/charge/BuddyStyleTest.kt`

**Interfaces:**
- Consumes: `ChargeStyle`, `ChargeKit` (`dot`, `set`, `circlePoints`, `easeInOut`, `reveal`).
- Produces: `object BuddyStyle : ChargeStyle` (id `buddy`, label `Buddy`).

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.render.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuddyStyleTest {

    @Test
    fun outlineIsRoundAndSymmetric() {
        for (level in listOf(0, 62, 100)) {
            val g = BuddyStyle.still(25, level)
            for (y in 0 until 25) for (x in 0 until 25) assertEquals("25 x=$x y=$y", g[x, y], g[24 - x, y])
            val s = BuddyStyle.still(13, level)
            for (y in 0 until 13) for (x in 0 until 13) assertEquals("13 x=$x y=$y", s[x, y], s[12 - x, y])
        }
        val g = BuddyStyle.still(25, 0)
        for ((x, y) in listOf(12 to 5, 12 to 21, 4 to 13, 20 to 13)) assertTrue("cardinal $x,$y", g[x, y] >= 200)
        val s = BuddyStyle.still(13, 0)                  // hand-placed 9×9: top row x 4..8 at y 3
        for (x in 4..8) assertTrue(s[x, 3] >= 200)
        assertEquals(0, s[3, 3]); assertTrue(s[3, 4] >= 200); assertTrue(s[2, 5] >= 200)
    }

    @Test
    fun fillFollowsLevel() {
        val empty = BuddyStyle.still(25, 0).raw().count { it in 80..100 }     // 0.35 → 89
        val full = BuddyStyle.still(25, 100).raw().count { it in 80..100 }
        val half = BuddyStyle.still(25, 50).raw().count { it in 80..100 }
        assertEquals(0, empty)
        assertTrue(half in (full / 3)..(full * 2 / 3))
    }

    @Test
    fun faceIsBright() {
        val g = BuddyStyle.still(25, 100)
        for ((x, y) in listOf(8 to 11, 9 to 12, 15 to 11, 16 to 12, 11 to 15, 12 to 16, 13 to 15)) assertEquals(255, g[x, y])
        val s = BuddyStyle.still(13, 100)
        for ((x, y) in listOf(4 to 6, 8 to 6, 5 to 8, 6 to 9, 7 to 8)) assertEquals(255, s[x, y])
    }

    @Test
    fun chargingBlinksAndDoneIsHappy() {
        val open = BuddyStyle.charging(25, 62, 1000)
        val blink = BuddyStyle.charging(25, 62, 2600 + 50)
        assertEquals(255, open[8, 11]); assertTrue(blink[8, 11] < 255)
        val happy = BuddyStyle.done(25, 1000)           // after the hop: ^ eyes at (7,12),(8,11),(9,11),(10,12)
        assertEquals(255, happy[7, 12]); assertEquals(255, happy[8, 11])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.BuddyStyleTest'`
Expected: compilation FAIL, because `BuddyStyle` is unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package app.backlit.render.charge

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/** A small round blob with eyes that fills up like a battery; its antenna glows while charging. */
object BuddyStyle : ChargeStyle {
    override val id = "buddy"
    override val label = "Buddy"

    private val RING13 = listOf("..#####..", ".#.....#.", "#.......#", "#.......#", "#.......#", "#.......#", "#.......#", ".#.....#.", "..#####..")
    private val HEART = listOf("101", "111", "010")

    override fun still(size: Int, level: Int) = PixelGrid(size).also { buddy(it, level / 100.0, null, happy = false) }

    override fun plugIn(size: Int, level: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        buddy(g, level / 100.0 * ChargeKit.easeInOut(tMs / 2800.0), tMs, happy = false)
        return ChargeKit.reveal(g, level, tMs)
    }

    override fun charging(size: Int, level: Int, tMs: Long) = PixelGrid(size).also { buddy(it, level / 100.0, tMs, happy = false) }

    override fun done(size: Int, tMs: Long): PixelGrid {
        val big = size >= 25
        val hop = if (tMs < 900) (abs(sin(tMs / 900.0 * PI * 2)) * (if (big) -2 else -1)).px() else 0
        val body = PixelGrid(size).also { buddy(it, 1.0, tMs, happy = true) }
        val g = PixelGrid(size)
        for (y in 0 until size) for (x in 0 until size) { val v = body[x, y]; if (v > 0) g.plot(x, y + hop, v) }
        if (tMs > 800) {
            for (i in 0 until (if (big) 2 else 1)) {
                val ph = ((tMs - 800) / 1400.0 + i * 0.5) % 1.0
                val hx = if (big) (if (i == 1) 18 else 4) else 10
                val hy = ((if (big) 9.0 else 4.0) - ph * (if (big) 6 else 3)).px()
                val v = 1 - ph * 0.8
                if (big) HEART.forEachIndexed { r, row -> row.forEachIndexed { k, ch -> if (ch == '1') g.dot((hx + k).toDouble(), (hy + r).toDouble(), v) } }
                else g.dot(hx.toDouble(), hy.toDouble(), v)
            }
        }
        return g
    }

    /** [tMs] null = still: no blink, antenna dim. */
    private fun buddy(g: PixelGrid, lvl: Double, tMs: Long?, happy: Boolean) {
        val big = g.size >= 25
        val cx = if (big) 12 else 6
        val cy = if (big) 13 else 7
        val r = if (big) 8 else 4
        val ring = if (big) ChargeKit.circlePoints(cx, cy, r).distinct()
        else RING13.flatMapIndexed { row, s -> s.mapIndexedNotNull { k, ch -> if (ch == '#') (2 + k) to (3 + row) else null } }
        val onRing = ring.toSet()
        val top = if (big) cy - r + 1 else 4
        val bot = if (big) cy + r - 1 else 10
        val topFill = bot + 1 - (bot - top + 1) * lvl
        for (y in cy - r - 1..cy + r + 1) for (x in cx - r - 1..cx + r + 1) {
            if ((x to y) in onRing) continue
            val inside = if (big) hypot((x - cx).toDouble(), (y - cy).toDouble()) < r - 0.3
            else x in 3..9 && y in 4..10 && !((y == 4 || y == 10) && (x == 3 || x == 9))
            if (inside && y >= topFill - 0.001) g.dot(x.toDouble(), y.toDouble(), 0.35)
        }
        ring.forEach { (x, y) -> g.dot(x.toDouble(), y.toDouble(), 0.85) }

        // antenna with charging tip
        val ay = if (big) cy - r else 3
        if (big) { g.dot(cx.toDouble(), ay - 1.0, 0.7); g.dot(cx.toDouble(), ay - 2.0, 0.7) } else g.dot(cx.toDouble(), ay - 1.0, 0.7)
        val tip = when {
            happy -> 1.0
            tMs == null -> 0.3
            else -> 0.3 + 0.7 * abs(sin(tMs / 350.0))
        }
        g.dot(cx.toDouble(), ay - (if (big) 3.0 else 2.0), tip)

        // face
        val blink = tMs != null && !happy && tMs % 2600 < 140
        if (big) {
            val ey = cy - 2
            for (ex in listOf(8, 15)) {
                when {
                    happy -> { g.set(ex - 1.0, ey + 1.0, 1.0); g.set(ex.toDouble(), ey.toDouble(), 1.0); g.set(ex + 1.0, ey.toDouble(), 1.0); g.set(ex + 2.0, ey + 1.0, 1.0) }
                    blink -> { g.set(ex.toDouble(), ey + 1.0, 1.0); g.set(ex + 1.0, ey + 1.0, 1.0) }
                    else -> for (dx in 0..1) for (dy in 0..1) g.set(ex + dx.toDouble(), ey + dy.toDouble(), 1.0)
                }
            }
            val my = cy + 2
            val mouth = if (happy) listOf(-2 to 0, -1 to 1, 0 to 1, 1 to 1, 2 to 0) else listOf(-1 to 0, 0 to 1, 1 to 0)
            mouth.forEach { (dx, dy) -> g.set((cx + dx).toDouble(), (my + dy).toDouble(), 1.0) }
        } else {
            val ey = cy - 1
            for (ex in listOf(4, 8)) if (!blink || happy) g.set(ex.toDouble(), ey.toDouble(), 1.0)
            val mouth = if (happy) listOf(4 to 8, 5 to 9, 6 to 9, 7 to 9, 8 to 8) else listOf(5 to 8, 6 to 9, 7 to 8)
            mouth.forEach { (x, y) -> g.set(x.toDouble(), y.toDouble(), 1.0) }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.BuddyStyleTest'`
Expected: PASS (4 tests). If `fillFollowsLevel` is off by a row, check `topFill` against the mockup. The fill starts at `y >= topFill`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/render/charge/BuddyStyle.kt app/src/test/java/app/backlit/render/charge/BuddyStyleTest.kt
git commit -m "feat(charge): Buddy style

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Style registry and ChargeSession

**Files:**
- Create: `app/src/main/java/app/backlit/render/charge/ChargeStyles.kt`
- Create: `app/src/main/java/app/backlit/charge/ChargeSession.kt`
- Test: `app/src/test/java/app/backlit/render/charge/ChargeStylesTest.kt`
- Test: `app/src/test/java/app/backlit/charge/ChargeSessionTest.kt`

**Interfaces:**
- Consumes: the four styles (Tasks 2–5).
- Produces:
  - `object ChargeStyles { const val DEFAULT = "moon"; val all: List<ChargeStyle>; fun byId(id: String): ChargeStyle; fun next(id: String): String }`
  - `data class Battery(val plugged: Boolean, val level: Int)`
  - `enum class Moment { STILL, PLUG_IN, CHARGING, DONE }`
  - `data class Show(val moment: Moment, val startedAt: Long)`
  - `class ChargeSession(target: () -> Int)` with `onBind(b, now)`, `onBattery(b, now, active)`, `tick(now)`, `show`, `level`, `nextWakeAt()`, and companion `PLUG_IN_MS = 5000L`, `DONE_MS = 3500L`

- [ ] **Step 1: Write the failing tests**

`ChargeStylesTest.kt`:

```kotlin
package app.backlit.render.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeStylesTest {

    @Test
    fun registryOrderDefaultAndCycling() {
        assertEquals(listOf("sprout", "buddy", "number", "moon"), ChargeStyles.all.map { it.id })
        assertEquals("moon", ChargeStyles.byId("nope").id)
        assertEquals("sprout", ChargeStyles.next("moon"))
        assertEquals("buddy", ChargeStyles.next("sprout"))
        assertEquals("sprout", ChargeStyles.next("unknown"))
    }

    @Test
    fun allStylesAllLevelsStayInMask() {
        for (style in ChargeStyles.all) for (size in listOf(25, 13)) {
            for (level in listOf(0, 5, 50, 62, 99, 100)) {
                val frames = listOf(style.still(size, level)) +
                    (0L..5000L step 250).map { style.plugIn(size, level, it) } +
                    (0L..6000L step 300).map { style.charging(size, level, it) }
                frames.forEach { g ->
                    assertEquals(size, g.size)
                    for (y in 0 until size) for (x in 0 until size) if (g[x, y] > 0) assertTrue("${style.id} $size $level", g.hasLed(x, y))
                }
            }
            (0L..3500L step 100).forEach { assertEquals(size, style.done(size, it).size) }
        }
    }
}
```

`ChargeSessionTest.kt`:

```kotlin
package app.backlit.charge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeSessionTest {

    private var target = 80
    private fun session() = ChargeSession { target }
    private fun on(level: Int) = Battery(plugged = true, level = level)
    private fun off(level: Int) = Battery(plugged = false, level = level)

    @Test
    fun plugInThenCharging() {
        val s = session(); s.onBind(off(40), 0)
        assertEquals(Moment.STILL, s.show.moment)
        s.onBattery(on(40), 1000, active = true)
        assertEquals(Show(Moment.PLUG_IN, 1000), s.show)
        assertEquals(6000L, s.nextWakeAt())
        s.tick(5999); assertEquals(Moment.PLUG_IN, s.show.moment)
        s.tick(6000); assertEquals(Moment.CHARGING, s.show.moment)
        assertNull(s.nextWakeAt())
    }

    @Test
    fun crossingPlaysDoneOnceThenCharging() {
        val s = session(); s.onBind(on(78), 0)
        s.onBattery(on(79), 10, true); assertEquals(Moment.CHARGING, s.show.moment)
        s.onBattery(on(80), 20, true); assertEquals(Show(Moment.DONE, 20), s.show)
        s.tick(20 + 3500); assertEquals(Moment.CHARGING, s.show.moment)
        s.onBattery(on(79), 5000, true); s.onBattery(on(80), 6000, true)   // jitter
        assertEquals(Moment.CHARGING, s.show.moment)
    }

    @Test
    fun crossingDuringPlugInIsQueued() {
        val s = session(); s.onBind(off(79), 0)
        s.onBattery(on(79), 0, true)
        s.onBattery(on(80), 1000, true)
        assertEquals(Moment.PLUG_IN, s.show.moment)
        s.tick(5000); assertEquals(Show(Moment.DONE, 5000), s.show)
    }

    @Test
    fun lateTickStillPlaysFullDone() {
        val s = session(); s.onBind(off(79), 0)
        s.onBattery(on(79), 0, true); s.onBattery(on(80), 100, true)
        s.tick(30_000)                                   // a long alert covered the plug-in
        assertEquals(Show(Moment.DONE, 30_000), s.show)
    }

    @Test
    fun unplugGoesStillFromEveryMoment() {
        val s = session(); s.onBind(off(50), 0)
        s.onBattery(on(50), 0, true); s.onBattery(off(50), 100, true)
        assertEquals(Moment.STILL, s.show.moment)
        s.onBattery(on(79), 200, true); s.tick(6000); s.onBattery(on(80), 7000, true)
        assertEquals(Moment.DONE, s.show.moment)
        s.onBattery(off(80), 7100, true); assertEquals(Moment.STILL, s.show.moment)
    }

    @Test
    fun bindWhileChargingAboveTargetNeverPlaysDone() {
        val s = session(); s.onBind(on(85), 0)
        assertEquals(Moment.CHARGING, s.show.moment)
        s.onBattery(on(86), 10, true); assertEquals(Moment.CHARGING, s.show.moment)
    }

    @Test
    fun plugInAlreadyAboveTargetNeverPlaysDone() {
        val s = session(); s.onBind(off(80), 0)
        s.onBattery(on(80), 0, true); s.tick(5000)
        s.onBattery(on(81), 6000, true)
        assertEquals(Moment.CHARGING, s.show.moment)
    }

    @Test
    fun replugStartsANewSession() {
        val s = session(); s.onBind(on(79), 0)
        s.onBattery(on(80), 10, true); s.tick(4000)
        s.onBattery(off(80), 5000, true)
        s.onBattery(off(75), 6000, true)
        s.onBattery(on(75), 7000, true); s.tick(12_000)
        s.onBattery(on(80), 13_000, true)
        assertEquals(Moment.DONE, s.show.moment)
    }

    @Test
    fun loweringTargetBelowLevelDoesNotFire() {
        val s = session(); target = 100
        s.onBind(on(70), 0)
        target = 70
        s.onBattery(on(71), 10, true)
        assertEquals(Moment.CHARGING, s.show.moment)
    }

    @Test
    fun aodPlugSkipsPlugInAndAodCrossingIsConsumed() {
        val s = session(); s.onBind(off(79), 0)
        s.onBattery(on(79), 0, active = false)
        assertEquals(Moment.CHARGING, s.show.moment)
        s.onBattery(on(80), 10, active = false)
        assertEquals(Moment.CHARGING, s.show.moment)
        s.onBattery(on(79), 20, true); s.onBattery(on(80), 30, true)
        assertEquals(Moment.CHARGING, s.show.moment)
    }

    @Test
    fun repeatedIdenticalUpdatesChangeNothing() {
        val s = session(); s.onBind(off(40), 0)
        s.onBattery(on(40), 0, true)
        repeat(20) { s.onBattery(on(40), 100L + it, true) }
        assertEquals(Show(Moment.PLUG_IN, 0), s.show)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.ChargeStylesTest' --tests 'app.backlit.charge.*'`
Expected: compilation FAIL, because `ChargeStyles` and `ChargeSession` are unresolved.

- [ ] **Step 3: Write the implementation**

`ChargeStyles.kt`:

```kotlin
package app.backlit.render.charge

object ChargeStyles {
    const val DEFAULT = "moon"
    val all: List<ChargeStyle> = listOf(SproutStyle, BuddyStyle, NumberStyle, MoonStyle)

    fun byId(id: String): ChargeStyle = all.firstOrNull { it.id == id } ?: all.first { it.id == DEFAULT }

    fun next(id: String): String {
        val i = all.indexOfFirst { it.id == id }
        return if (i < 0) all.first().id else all[(i + 1) % all.size].id
    }
}
```

`app/src/main/java/app/backlit/charge/ChargeSession.kt`:

```kotlin
package app.backlit.charge

data class Battery(val plugged: Boolean, val level: Int)

enum class Moment { STILL, PLUG_IN, CHARGING, DONE }

data class Show(val moment: Moment, val startedAt: Long)

/**
 * What the Charge toy shows, from battery events. Pure: callers pass the time.
 * A session runs from plug-in to unplug; DONE plays at most once per session, and only when the level
 * crosses the target while charging and the toy is active.
 */
class ChargeSession(private val target: () -> Int) {
    var show = Show(Moment.STILL, 0)
        private set
    var level = 0
        private set

    private var plugged = false
    private var donePlayed = false
    private var donePending = false

    fun onBind(b: Battery, now: Long) {
        level = b.level
        plugged = b.plugged
        donePending = false
        donePlayed = b.plugged && b.level >= target()
        show = Show(if (b.plugged) Moment.CHARGING else Moment.STILL, now)
    }

    fun onBattery(b: Battery, now: Long, active: Boolean) {
        val prev = level
        level = b.level
        if (!b.plugged) {
            if (plugged || show.moment != Moment.STILL) show = Show(Moment.STILL, now)
            plugged = false
            donePending = false
            return
        }
        if (!plugged) {                                  // a new session
            plugged = true
            donePending = false
            donePlayed = b.level >= target()             // already there: no crossing, no done
            show = Show(if (active) Moment.PLUG_IN else Moment.CHARGING, now)
            return
        }
        if (!donePlayed && prev < target() && level >= target()) {
            donePlayed = true
            if (!active) return
            if (show.moment == Moment.PLUG_IN) donePending = true else show = Show(Moment.DONE, now)
        }
    }

    fun tick(now: Long) {
        val s = show
        when (s.moment) {
            Moment.PLUG_IN -> if (now - s.startedAt >= PLUG_IN_MS) {
                show = if (donePending) Show(Moment.DONE, now) else Show(Moment.CHARGING, now)
                donePending = false
            }
            Moment.DONE -> if (now - s.startedAt >= DONE_MS) show = Show(Moment.CHARGING, now)
            else -> Unit
        }
    }

    fun nextWakeAt(): Long? = when (show.moment) {
        Moment.PLUG_IN -> show.startedAt + PLUG_IN_MS
        Moment.DONE -> show.startedAt + DONE_MS
        else -> null
    }

    companion object {
        const val PLUG_IN_MS = 5000L
        const val DONE_MS = 3500L
    }
}
```

Note: in `crossingDuringPlugInIsQueued`, `tick(5000)` starts DONE at 5000 because the transition uses `now`. In `crossingPlaysDoneOnceThenCharging`, `tick(3520)` → CHARGING.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.charge.*' --tests 'app.backlit.charge.*'`
Expected: PASS (all charge tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/render/charge/ChargeStyles.kt app/src/main/java/app/backlit/charge/ app/src/test/java/app/backlit/render/charge/ChargeStylesTest.kt app/src/test/java/app/backlit/charge/
git commit -m "feat(charge): style registry and ChargeSession state machine

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Charge settings

**Files:**
- Modify: `app/src/main/java/app/backlit/data/Settings.kt`
- Modify: `app/src/main/java/app/backlit/data/SettingsRepo.kt`
- Test: `app/src/test/java/app/backlit/data/SettingsRepoTest.kt`

**Interfaces:**
- Produces: `Settings.chargeStyle: String = "moon"`, `chargeTarget: Int = 100`, `chargePlugInAnim: String = ""`, `chargeDoneAnim: String = ""`, `chargeToyEverBound: Boolean = false`, and `SettingsRepo.clampTarget(v: Int): Int`.

- [ ] **Step 1: Write the failing test.** Add these to `SettingsRepoTest`, and extend `roundTripsEveryField`'s `Settings(...)` with the new fields.

In `roundTripsEveryField`, replace `musicStyle = "wave", musicSensitivity = Sensitivity.HIGH,` with:

```kotlin
            musicStyle = "wave", musicSensitivity = Sensitivity.HIGH,
            chargeStyle = "buddy", chargeTarget = 85, chargePlugInAnim = "import:abc", chargeDoneAnim = "import:def",
            chargeToyEverBound = true,
```

Add these new tests:

```kotlin
    @Test
    fun chargeDefaults() = runBlocking {
        val s = repo().settings.first()
        assertEquals("moon", s.chargeStyle)
        assertEquals(100, s.chargeTarget)
        assertEquals("", s.chargePlugInAnim)
        assertEquals("", s.chargeDoneAnim)
        assertEquals(false, s.chargeToyEverBound)
    }

    @Test
    fun chargeTargetIsClampedToFivePercentSteps() {
        assertEquals(50, SettingsRepo.clampTarget(10))
        assertEquals(100, SettingsRepo.clampTarget(140))
        assertEquals(85, SettingsRepo.clampTarget(86))
        assertEquals(90, SettingsRepo.clampTarget(88))
    }

    @Test
    fun outOfRangeStoredTargetReadsClamped() = runBlocking {
        val r = repo()
        r.update { it.copy(chargeTarget = 7) }
        assertEquals(50, r.settings.first().chargeTarget)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.data.SettingsRepoTest'`
Expected: compilation FAIL, because there's no parameter `chargeStyle`.

- [ ] **Step 3: Write the implementation**

In `Settings.kt`, add these after `musicSensitivity`:

```kotlin
    val chargeStyle: String = "moon",
    val chargeTarget: Int = 100,
    val chargePlugInAnim: String = "",
    val chargeDoneAnim: String = "",
    val chargeToyEverBound: Boolean = false,
```

In `SettingsRepo.kt` companion, add these keys after `MUSIC_SENS`:

```kotlin
        private val CHARGE_STYLE = stringPreferencesKey("charge_style")
        private val CHARGE_TARGET = intPreferencesKey("charge_target")
        private val CHARGE_PLUG_ANIM = stringPreferencesKey("charge_plugin_anim")
        private val CHARGE_DONE_ANIM = stringPreferencesKey("charge_done_anim")
        private val CHARGE_BOUND = booleanPreferencesKey("charge_toy_ever_bound")

        /** 50..100 in steps of 5 (nearest step, halves round up). */
        fun clampTarget(v: Int): Int = (((v.coerceIn(50, 100) + 2) / 5) * 5).coerceIn(50, 100)
```

In `toSettings()`, add these after `musicSensitivity = ...,`:

```kotlin
                chargeStyle = this[CHARGE_STYLE] ?: d.chargeStyle,
                chargeTarget = clampTarget(this[CHARGE_TARGET] ?: d.chargeTarget),
                chargePlugInAnim = this[CHARGE_PLUG_ANIM] ?: d.chargePlugInAnim,
                chargeDoneAnim = this[CHARGE_DONE_ANIM] ?: d.chargeDoneAnim,
                chargeToyEverBound = this[CHARGE_BOUND] ?: d.chargeToyEverBound,
```

In `write()`, add these after `this[MUSIC_SENS] = ...`:

```kotlin
            this[CHARGE_STYLE] = s.chargeStyle
            this[CHARGE_TARGET] = s.chargeTarget
            this[CHARGE_PLUG_ANIM] = s.chargePlugInAnim
            this[CHARGE_DONE_ANIM] = s.chargeDoneAnim
            this[CHARGE_BOUND] = s.chargeToyEverBound
```

Check: `clampTarget(86)`: (86+2)/5 = 17, ×5 = 85 ✓. `clampTarget(88)`: 90/5 = 18 → 90 ✓. `clampTarget(7)` → 50 ✓.

- [ ] **Step 4: Run test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.data.SettingsRepoTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/data/ app/src/test/java/app/backlit/data/SettingsRepoTest.kt
git commit -m "feat(charge): charge settings (style, target, custom animations, setup flag)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Alerts hooks (preview duration, charge preview ids, import lookup)

**Files:**
- Create: `app/src/main/java/app/backlit/charge/ChargePreviewAnimation.kt`
- Modify: `app/src/main/java/app/backlit/alerts/AlertCoordinator.kt` (`preview`)
- Modify: `app/src/main/java/app/backlit/alerts/AnimationLibrary.kt` (add `importedOnly`)
- Modify: `app/src/main/java/app/backlit/alerts/AlertsRuntime.kt` (`preview`, `animationFor`, `importedAnimation`)
- Test: `app/src/test/java/app/backlit/charge/ChargePreviewAnimationTest.kt`
- Test: `app/src/test/java/app/backlit/alerts/AlertsChargeHooksTest.kt`
- Test: `app/src/test/java/app/backlit/alerts/AlertCoordinatorTest.kt` (one new test)

**Interfaces:**
- Consumes: `ChargeStyles`, `Moment` (Task 6), `GlyphAnimation` (existing).
- Produces:
  - `class ChargePreviewAnimation(style: ChargeStyle, moment: Moment, level: Int = DEMO_LEVEL) : GlyphAnimation`, with companion `DEMO_LEVEL = 62`, `fun idFor(styleId: String, moment: Moment): String`, `fun parse(id: String): ChargePreviewAnimation?` and `fun durationMs(moment: Moment): Long`
  - `AlertCoordinator.preview(animationId: String, nowMs: Long, durationMs: Long = SHORT_MS)`
  - `AlertsRuntime.preview(animationId: String, durationMs: Long = AlertCoordinator.SHORT_MS)`
  - `AlertsRuntime.importedAnimation(id: String): GlyphAnimation?`
  - `AnimationLibrary.importedOnly(id: String, imports: List<AnimIndexEntry>): GlyphAnimation?`

- [ ] **Step 1: Write the failing tests**

`ChargePreviewAnimationTest.kt`:

```kotlin
package app.backlit.charge

import app.backlit.render.charge.MoonStyle
import app.backlit.render.charge.NumberStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargePreviewAnimationTest {

    @Test
    fun idsRoundTrip() {
        val id = ChargePreviewAnimation.idFor("moon", Moment.PLUG_IN)
        assertEquals("charge:moon:plug_in", id)
        val a = ChargePreviewAnimation.parse(id)!!
        assertEquals(id, a.id)
        assertEquals(5000L, a.loopMs)
        assertEquals(MoonStyle.plugIn(25, 62, 1200), a.frame(25, 1200))
    }

    @Test
    fun badIdsDoNotParse() {
        assertNull(ChargePreviewAnimation.parse("charge:moon"))
        assertNull(ChargePreviewAnimation.parse("charge:moon:sideways"))
        assertNull(ChargePreviewAnimation.parse("builtin:heart"))
        assertEquals("moon", ChargePreviewAnimation.parse("charge:nope:still")!!.id.split(':')[1])   // unknown style → default
    }

    @Test
    fun durationsAndFrames() {
        assertEquals(3500L, ChargePreviewAnimation.durationMs(Moment.DONE))
        assertEquals(3000L, ChargePreviewAnimation.durationMs(Moment.STILL))
        assertEquals(NumberStyle.still(13, 62), ChargePreviewAnimation(NumberStyle, Moment.STILL).frame(13, 999))
    }
}
```

`AlertsChargeHooksTest.kt` (this tests the pure `AnimationLibrary.importedOnly` that `AlertsRuntime.importedAnimation` delegates to):

```kotlin
package app.backlit.alerts

import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AlertsChargeHooksTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun importedAnimationIgnoresNonImports() {
        val lib = AnimationLibrary(tmp.root)
        assertNull(lib.importedOnly("builtin:heart", emptyList()))
        assertNull(lib.importedOnly("", emptyList()))
        assertNull(lib.importedOnly("import:gone", listOf(AnimIndexEntry("import:gone", "Gone", 1))))   // file missing
    }
}
```

Add this to `AlertCoordinatorTest.kt`:

```kotlin
    @Test
    fun previewHonoursCustomDuration() {
        val c = AlertCoordinator({ emptyList() }, { emptyList() })
        c.preview("charge:moon:plug_in", 0, durationMs = 5000)
        c.tick(4999); assertEquals("charge:moon:plug_in", c.active?.animationId)
        c.tick(5000); assertNull(c.active)
    }
```

(If `assertNull` isn't imported in that file, add `import org.junit.Assert.assertNull`.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.charge.ChargePreviewAnimationTest' --tests 'app.backlit.alerts.*'`
Expected: compilation FAIL, because `ChargePreviewAnimation` and `importedOnly` are unresolved and there's no `durationMs` parameter.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/app/backlit/charge/ChargePreviewAnimation.kt`:

```kotlin
package app.backlit.charge

import app.backlit.anim.GlyphAnimation
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeStyle
import app.backlit.render.charge.ChargeStyles

/** A charge style moment as a GlyphAnimation, so "Show on Glyph" can reuse the Alerts preview path. */
class ChargePreviewAnimation(
    private val style: ChargeStyle,
    private val moment: Moment,
    private val level: Int = DEMO_LEVEL,
) : GlyphAnimation {
    override val id: String = idFor(style.id, moment)
    override val name: String = style.label
    override val loopMs: Long = durationMs(moment)

    override fun frame(size: Int, tMs: Long): PixelGrid = when (moment) {
        Moment.STILL -> style.still(size, level)
        Moment.PLUG_IN -> style.plugIn(size, level, tMs)
        Moment.CHARGING -> style.charging(size, level, tMs)
        Moment.DONE -> style.done(size, tMs)
    }

    companion object {
        const val DEMO_LEVEL = 62
        private const val PREFIX = "charge:"

        fun idFor(styleId: String, moment: Moment) = "$PREFIX$styleId:${moment.name.lowercase()}"

        fun parse(id: String): ChargePreviewAnimation? {
            if (!id.startsWith(PREFIX)) return null
            val parts = id.split(':')
            if (parts.size != 3) return null
            val moment = Moment.entries.firstOrNull { it.name.lowercase() == parts[2] } ?: return null
            return ChargePreviewAnimation(ChargeStyles.byId(parts[1]), moment)
        }

        fun durationMs(moment: Moment): Long = when (moment) {
            Moment.PLUG_IN -> ChargeSession.PLUG_IN_MS
            Moment.DONE -> ChargeSession.DONE_MS
            else -> 3000L
        }
    }
}
```

In `AlertCoordinator.kt`, replace `preview`:

```kotlin
    fun preview(animationId: String, nowMs: Long, durationMs: Long = SHORT_MS) {
        if (active?.kind == AlertKind.CALL) return
        active = ActiveAlert(animationId, AlertKind.DEVICE, nowMs, nowMs + durationMs)
    }
```

In `AnimationLibrary.kt`, add this after `resolve`:

```kotlin
    /** Only an imported animation (no built-in fallback); null if [id] isn't a known, loadable import. */
    fun importedOnly(id: String, imports: List<AnimIndexEntry>): GlyphAnimation? =
        if (!id.startsWith("import:")) null else imports.firstOrNull { it.id == id }?.let { load(it) }
```

In `AlertsRuntime.kt`:

```kotlin
    fun preview(animationId: String, durationMs: Long = AlertCoordinator.SHORT_MS) =
        dispatch { coordinator.preview(animationId, now(), durationMs) }

    fun animationFor(alert: ActiveAlert): GlyphAnimation =
        ChargePreviewAnimation.parse(alert.animationId) ?: library.resolve(
            alert.animationId, current.imports,
            fallback = if (alert.kind == AlertKind.CALL) BuiltInAnimations.DEFAULT_CONTACT else BuiltInAnimations.DEFAULT_DEVICE,
        )

    /** For the Charge toy: an imported animation by id, or null (unset, deleted, or not an import). */
    fun importedAnimation(id: String): GlyphAnimation? = library.importedOnly(id, current.imports)
```

Also add `import app.backlit.charge.ChargePreviewAnimation`. Replace the old one-line `preview` and `animationFor`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.superpowers/runtests.sh`
Expected: the full suite PASSES, with 0 failures.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/charge/ChargePreviewAnimation.kt app/src/main/java/app/backlit/alerts/ app/src/test/java/app/backlit/charge/ChargePreviewAnimationTest.kt app/src/test/java/app/backlit/alerts/
git commit -m "feat(charge): preview ids, preview duration and import lookup in Alerts

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: ChargeToyService, manifest, strings and toy image

**Files:**
- Create: `app/src/main/java/app/backlit/glyph/ChargeToyService.kt`
- Create: `app/src/main/res/drawable/ic_charge_preview.xml`
- Modify: `app/src/main/AndroidManifest.xml` (new `<service>` after MusicToyService)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes:
  - `ChargeSession`, `Battery`, `Moment`, `ChargeStyles` (Task 6)
  - `Settings.charge*` (Task 7)
  - `AlertsRuntime.importedAnimation`, `animationFor`, `bus`, `toyChanged`, and `AlertsRuntime.now()` (Task 8 and existing)
  - `ToyPresence`, `GlyphOutput`, `FramePacer`, `FrameEncoder`, `ModeTracker`, `DeviceProfile` (existing)
- Produces: the toy service `app.backlit.glyph.ChargeToyService`.

This task is Android glue with no JVM unit test. Its gate is a clean build plus the full test suite, and the on-device checks happen in Task 11.

- [ ] **Step 1: Write the service**

```kotlin
package app.backlit.glyph

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.ToyPresence
import app.backlit.charge.Battery
import app.backlit.charge.ChargeSession
import app.backlit.charge.Moment
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeStyles
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

/** Battery toy: still level all day, plug-in / charging / done animations while charging. */
class ChargeToyService : Service() {

    private var scope: CoroutineScope? = null
    private var renderJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private var settings = Settings()
    private var alerts: AlertsRuntime? = null
    private val session = ChargeSession { settings.chargeTarget }
    private var lastBattery: Battery? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> scope?.launch { repo.update { it.copy(chargeStyle = ChargeStyles.next(it.chargeStyle)) } }
                GlyphToy.EVENT_AOD -> { modes.onAodEvent(System.currentTimeMillis()); kick() }
            }
        }
    }
    private val messenger = Messenger(handler)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val b = batteryOf(intent) ?: return
            if (b == lastBattery) return                 // voltage/temperature-only updates
            lastBattery = b
            session.onBattery(b, AlertsRuntime.now(), active = !isAod())
            kick()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        if (profile == DeviceProfile.UNSUPPORTED) return messenger.binder

        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "charge toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        ToyPresence.enter()
        val rt = AlertsRuntime.get(this).also { alerts = it }
        rt.toyChanged()

        val sticky = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        batteryOf(sticky)?.let { lastBattery = it; session.onBind(it, AlertsRuntime.now()) }

        output = GlyphOutput(this, profile) { kick() }.also { it.connect() }
        s.launch { repo.update { if (it.chargeToyEverBound) it else it.copy(chargeToyEverBound = true) } }
        s.launch { repo.settings.collect { settings = it; kick() } }
        s.launch { rt.bus.collect { kick() } }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { unregisterReceiver(batteryReceiver) }
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

    /** Draws now; keeps a 50 ms loop running while anything moves, and stops when the frame is still. */
    private fun kick() {
        val s = scope ?: return
        if (renderJob?.isActive == true) return
        renderJob = s.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val now = AlertsRuntime.now()
                session.tick(now)
                val alert = alerts?.bus?.value
                val aod = isAod()
                val grid = runCatching {
                    if (alert != null) alerts!!.animationFor(alert).frame(profile.size, now - alert.startedAt) else frame(now, aod)
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = alert == null && aod))
                val animating = alert != null || (!aod && session.show.moment != Moment.STILL)
                if (!animating) break
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    private fun frame(now: Long, aod: Boolean): PixelGrid {
        val style = ChargeStyles.byId(settings.chargeStyle)
        val size = profile.size
        val level = session.level
        if (aod) return style.still(size, level)
        val show = session.show
        val t = now - show.startedAt
        return when (show.moment) {
            Moment.STILL -> style.still(size, level)
            Moment.PLUG_IN -> alerts?.importedAnimation(settings.chargePlugInAnim)?.frame(size, t) ?: style.plugIn(size, level, t)
            Moment.CHARGING -> style.charging(size, level, t)
            Moment.DONE -> alerts?.importedAnimation(settings.chargeDoneAnim)?.frame(size, t) ?: style.done(size, t)
        }
    }

    private companion object {
        const val TAG = "BacklitCharge"
        const val FRAME_MS = 50L

        fun batteryOf(i: Intent?): Battery? {
            i ?: return null
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return null
            return Battery(plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0, level = (level * 100 / scale).coerceIn(0, 100))
        }
    }
}
```

- [ ] **Step 2: Add strings, the manifest entry and the toy image**

Add to `strings.xml` after `music_toy_summary`:

```xml
    <string name="charge_toy_name">Backlit Charge</string>
    <string name="charge_toy_summary">Your battery level, and a little show while charging. Long press to change style.</string>
```

Add to `AndroidManifest.xml` right after the closing `</service>` of `.glyph.MusicToyService`:

```xml
        <service
            android:name=".glyph.ChargeToyService"
            android:exported="true"
            tools:ignore="ExportedService">
            <intent-filter>
                <action android:name="com.nothing.glyph.TOY" />
            </intent-filter>
            <meta-data
                android:name="com.nothing.glyph.toy.name"
                android:resource="@string/charge_toy_name" />
            <meta-data
                android:name="com.nothing.glyph.toy.image"
                android:resource="@drawable/ic_charge_preview" />
            <meta-data
                android:name="com.nothing.glyph.toy.summary"
                android:resource="@string/charge_toy_summary" />
            <meta-data
                android:name="com.nothing.glyph.toy.longpress"
                android:value="1" />
            <meta-data
                android:name="com.nothing.glyph.toy.aod_support"
                android:value="1" />
        </service>
```

Create `app/src/main/res/drawable/ic_charge_preview.xml`. It shows a moon at about 62 %: an outer circle, plus a lit gibbous shape as the union of the right half-disc and an ellipse.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="96dp"
    android:height="96dp"
    android:viewportWidth="96"
    android:viewportHeight="96">
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="4"
        android:pathData="M48,10a38,38 0,1 1,0 76a38,38 0,1 1,0 -76" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M48,14a34,34 0,0 1,0 68a8.8,34 0,0 1,0 -68z" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M48,14a8.8,34 0,0 0,0 68a8.8,34 0,0 0,0 -68z" />
</vector>
```

- [ ] **Step 3: Build and run the full suite**

Run: `source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK && .superpowers/runtests.sh -q 2>&1 | tail -1`
Expected: `BUILD_OK`, then `N tests, 0 failures, 0 errors`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/backlit/glyph/ChargeToyService.kt app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/main/res/drawable/ic_charge_preview.xml
git commit -m "feat(charge): Backlit Charge toy service

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: CHARGE tab

**Files:**
- Create: `app/src/main/java/app/backlit/ui/ChargeScreen.kt`
- Modify: `app/src/main/java/app/backlit/ui/HomeScreen.kt` (4th chip and tab branch)

**Interfaces:**
- Consumes:
  - `ChargeStyles`, `ChargePreviewAnimation`, `Moment` (Tasks 6 and 8)
  - `Settings.charge*`, `SettingsRepo.clampTarget` (Task 7)
  - `AlertsRuntime.get/config/library/preview`, `importFromUri` (existing and Task 8)
  - `MatrixPreview`, `SquareChip`, `SettingRow`, `Notice`, `BacklitColors` (existing UI)
- Produces: `@Composable fun ChargeTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit)`

This is UI glue. Its gate is a clean build plus a manual look in Task 11.

- [ ] **Step 1: Write the screen**

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
```

- [ ] **Step 2: Wire up the tab in HomeScreen.kt**

Replace the three-chip row and the `when (tab)` with:

```kotlin
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SquareChip("CLOCK", selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.weight(1f))
            SquareChip("MUSIC", selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.weight(1f))
            SquareChip("ALERTS", selected = tab == 2, onClick = { tab = 2 }, modifier = Modifier.weight(1f))
            SquareChip("CHARGE", selected = tab == 3, onClick = { tab = 3 }, modifier = Modifier.weight(1f))
        }

        when (tab) {
            0 -> ClockTab(settings, profile, onUpdate, onNavigate)
            1 -> MusicTab(settings, profile, onUpdate)
            2 -> AlertsTab(profile)
            else -> ChargeTab(settings, profile, onUpdate)
        }
```

If `BacklitColors.Line` doesn't exist, use the colour `AlertsScreen` uses for its picker border. It does use `BacklitColors.Line`, so check with `grep -n "Line" app/src/main/java/app/backlit/ui/Theme.kt`.

- [ ] **Step 3: Build and run the full suite**

Run: `source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK && .superpowers/runtests.sh -q 2>&1 | tail -1`
Expected: `BUILD_OK`, then `N tests, 0 failures, 0 errors`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/backlit/ui/ChargeScreen.kt app/src/main/java/app/backlit/ui/HomeScreen.kt
git commit -m "feat(charge): CHARGE tab with live preview, styles, target and custom animations

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: On-device checks and docs

**Files:**
- Modify: `docs/testing/device-checklist.md` (add a "Charge toy" section)
- Modify: `README.md` (add Charge to the features list)
- Possibly modify: `app/src/main/java/app/backlit/ui/ChargeScreen.kt` (add the Nothing on-charge note only if check 2 shows it's needed)

**Interfaces:**
- Consumes: the whole feature.

- [ ] **Step 1: Install on the Phone (3)**

Run: `source .superpowers/env.sh && adb devices && ./gradlew -q :app:installDebug && adb logcat -c`
Expected: the device is listed and the install succeeds. If no device is listed, ask the user to plug in the phone with USB debugging on, then retry.

- [ ] **Step 2: Run the spec §9 checks with the user and record each result**

Ask the user to do each step and report back. Read `adb logcat -d -s BacklitCharge:V BacklitToy:V` for errors after each step.

1. In Settings → Glyph Interface → Glyph Toys, enable **Backlit Charge** and select it. The still level shows, and long press cycles Sprout → Buddy → Big number → Moon.
2. While unplugged with the toy showing, plug in. **Record** whether Backlit's plug-in animation shows, or whether Nothing's On Charge Animation covers it. If it's covered, add this to `ChargeTab` above the preview: `Notice("Nothing's own charging animation can cover Backlit's for a few seconds. To see Backlit's, turn off Nothing's in Settings → Glyph Interface.")`. Re-test with Nothing's turned off (the user does that). Never change it from the app.
3. The charging loop runs, and unplugging returns to still within about a second.
4. In the CHARGE tab, set "Done at" to the next 5 % step above the current level and keep charging. Done plays once when it's crossed, and the toy continues in charging.
5. Set Backlit Charge as the always-on toy and lock the phone. The still level shows and stays static.
6. With an important contact set in ALERTS, have them call. The call animation interrupts the toy, and the toy resumes after.
7. Import a Glyph Museum JSON and pick it as the plug-in animation. Replug, and it plays instead of the style's own. Delete it in ALERTS → ANIMATIONS, replug, and the style's own animation plays.
8. In the CHARGE tab, each of STILL, PLUG-IN, CHARGING and DONE previews correctly, and SHOW ON GLYPH plays the moment on the matrix with the Glyph idle.

- [ ] **Step 3: Update the docs**

Append this to `docs/testing/device-checklist.md`:

```markdown
## Charge toy (Phone (3))

- [ ] Toy listed as "Backlit Charge"; still level shows; long press cycles Sprout → Buddy → Big number → Moon
- [ ] Plug in with toy showing → plug-in animation (ends with %), then charging loop
- [ ] Nothing's On Charge Animation: covers Backlit? (record result and OS version)
- [ ] Unplug → still within ~1 s
- [ ] Done plays once when the level crosses "Done at"; not on jitter; not when plugging in above the target
- [ ] AOD toy: still level, no animation
- [ ] Important-contact call interrupts and the toy resumes
- [ ] Imported plug-in / done animations play; deleted import falls back to the style
- [ ] CHARGE tab previews all four moments; SHOW ON GLYPH works with the Glyph idle
```

In `README.md`, add a **Charge** entry to the features section, in the same format as the Clock, Music and Alerts entries:

```markdown
- **Charge**: a battery toy with four styles (Sprout, Buddy, Big number, Moon). It shows your level all day, plays a plug-in animation and a gentle loop while charging, and a "done" moment once you reach your chosen level (50–100 %). Plug-in and done can use Glyph Museum imports.
```

- [ ] **Step 4: Commit**

```bash
git add docs/testing/device-checklist.md README.md app/src/main/java/app/backlit/ui/ChargeScreen.kt
git commit -m "docs: charge toy device checklist and README

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
