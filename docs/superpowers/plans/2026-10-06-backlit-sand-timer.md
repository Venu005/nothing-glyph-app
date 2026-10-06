# Backlit Sand Timer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship "Backlit Sand", a Glyph toy that is a real hourglass: live tilt sand on the Phone (3), flipping to start and reverse, a long-press preset picker, a "Flip me" done moment with a vibration or chime that fires even when the toy isn't showing, always-on stills, and a TIMER tab.

**Architecture:**
- **Pure Kotlin core** in `app.backlit.sand`: `HourglassShape`, `SandLayout`, `SandSim`, `TimerState` and `SandArt`, each with JVM tests. The time (`TimerState`) is the truth; the sand sim is steered to match it.
- **`SandToyService`** follows `PetToyService` exactly: `ModeTracker`, `GlyphOutput`, `FramePacer(50)`, `kick()`, and an accelerometer only while ACTIVE.
- **`SandAlarm` plus a receiver** gives the done alert when the toy's live loop isn't running.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore with kotlinx-serialization, the Nothing GlyphMatrix SDK, AlarmManager, VibratorManager and RingtoneManager, and JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-06-backlit-sand-timer-design.md`. The mockups are `docs/superpowers/mockups/2026-10-06-sand-styles.html` (style A) and `docs/superpowers/mockups/2026-10-06-sand-moments.html` ("Flip me" is option C).

## Global Constraints
- **Tooling:** `source .superpowers/env.sh` before Gradle. Run tests with `.superpowers/runtests.sh [--tests '<pattern>']`, which prints `N tests, F failures, E errors` last.
- **Brightness** maps 0..1 → 0..255 with `(v * 255).px()`, where `px()` is round half up in `app.backlit.render`.
- **Mask:** a cell has an LED when `hypot(x − c, y − c) ≤ n / 2` with `c = (n − 1) / 2`, matching `PixelGrid.hasLed`.
- **Glass:** half-widths `[gate,0,1,2,3,4,5,6,6,6,5]` at 25×25 and `[gate,0,1,2,3,3]` at 13×13. The sand load is `floor(0.72 × min(bulb cells))`, which gives 61 at 25×25 and 16 at 13×13.
- **Brightness constants:**
  - wall 0.16
  - resting grain 0.7
  - moving or stream grain 1.0
  - READY wall `0.16 + 0.12·max(0, sin(now/500))`
  - DONE wall `0.16 + 0.08·max(0, sin(now/1500))`
  - DONE still wall 0.35
  - "Flip me" source wall 0.3, blink 0.85
- **Timings:**
  - side debounce 500 ms
  - double long press 2000 ms
  - number 1500 ms
  - refill 700 ms
  - "Flip me" spin 1200 ms, then blinks from 1300 to 2300 ms at 250 ms on and off
  - frame step 50 ms
- **Presets** default to `[1, 3, 5, 10, 25]` minutes. Each is 1..99, with no duplicates, sorted, at most 8 and at least 1.
- **Done vibration** is the waveform `0, 400, 200, 400, 200, 600` ms. The chime is only played when the ringer mode is NORMAL.
- **Permissions:** add only `VIBRATE`, `SCHEDULE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED`. No runtime prompts.
- **Commits:**
  - end every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
  - never commit the Nothing SDK aar
  - never change system settings on the phone

## Review Focus
1. **Reopening the toy mid-timer:** a long press, then swiping away and back, or turning the screen off and on. The sand should be redrawn at the right level and must not restart. This is pinned by the `TimerState` restore test (Task 3) and the `baseline` tests (Task 3).
2. **A flip while the toy isn't showing:** READY or DONE must not start on its own when the toy reappears in a different orientation. This is pinned by `baselineNeverStartsReadyOrDone` (Task 3).
3. **Removing the selected preset or emptying the list:** the index must never go out of range. This is pinned by `longPressWithIndexPastEndWraps` (Task 3) and `presetsAreCleaned` (Task 5).
4. **The alarm fires while the timer is paused or already done:** there should be no ring and no state change. This is pinned by `alarmOnlyFinishesARunningTimer` (Task 3).
5. **The 13×13 neck at its narrowest:** the sand must still drain fully with the clock. This is pinned by `drainsWithTheClock` at both sizes (Task 2).

---

## File Structure
| File | Responsibility |
|---|---|
| `app/src/main/java/app/backlit/sand/HourglassShape.kt` | glass cells per size: IN/WALL/GATE/OUT, bulb side, load |
| `app/src/main/java/app/backlit/sand/SandLayout.kt` | deterministic resting sand for a fraction (stills, rebuilds, refill), plus stream cells |
| `app/src/main/java/app/backlit/sand/SandSim.kt` | grain physics for any gravity direction, the gate budget, and the `gateRateFor` controller |
| `app/src/main/java/app/backlit/sand/TimerState.kt` | `Phase`, `Orientation`, and the timer state machine (serializable) |
| `app/src/main/java/app/backlit/sand/SandArt.kt` | frames and stills: picture, number overlay, refill, "Flip me", breathing glass |
| `app/src/main/java/app/backlit/sand/SandPreviewAnimation.kt` | `sand:ready`, `sand:running` and `sand:done` GlyphAnimations |
| `app/src/main/java/app/backlit/sand/SandAlarm.kt` | schedule or cancel the done alarm, and the vibration and chime |
| `app/src/main/java/app/backlit/sand/SandAlarmReceiver.kt` | alarm, boot and package-replaced handling |
| `app/src/main/java/app/backlit/glyph/SandToyService.kt` | the toy |
| `app/src/main/java/app/backlit/ui/SandScreen.kt` | the TIMER tab |
| Modify: `data/Settings.kt`, `data/SettingsRepo.kt`, `alerts/AlertsRuntime.kt`, `ui/HomeScreen.kt`, `AndroidManifest.xml`, `res/values/strings.xml`, `test/.../render/ToyPreviewsTest.kt`, `README.md`, `docs/testing/device-checklist.md` | |

---

### Task 1: Hourglass shape and resting layout

**Files:**
- Create: `app/src/main/java/app/backlit/sand/HourglassShape.kt`
- Create: `app/src/main/java/app/backlit/sand/SandLayout.kt`
- Test: `app/src/test/java/app/backlit/sand/HourglassShapeTest.kt`
- Test: `app/src/test/java/app/backlit/sand/SandLayoutTest.kt`

**Interfaces:**
- Produces:
  - `enum class Cell { OUT, IN, WALL, GATE }`
  - `HourglassShape.forSize(n: Int): HourglassShape`, with members `size`, `center: Int`, `kind: Array<Cell>`, `side: IntArray` (+1 top, −1 bottom, 0 centre row), `gate: Int`, `total: Int`, `hasLed(x, y)`, `isOpen(i)` and `cellsOn(side): List<Int>`
  - `SandLayout.layout(shape, fracUp: Double, stream: Boolean, upSide: Int = 1, fill: Double = 1.0): BooleanArray`
  - `SandLayout.streamCells(shape, upSide: Int): IntArray`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/app/backlit/sand/HourglassShapeTest.kt
package app.backlit.sand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class HourglassShapeTest {
    @Test
    fun loadsMatchTheMockup() {
        assertEquals(61, HourglassShape.forSize(25).total)
        assertEquals(16, HourglassShape.forSize(13).total)
    }

    @Test
    fun bulbsAreMirrorImagesAndTheGateIsTheCentre() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            assertEquals(Cell.GATE, s.kind[s.gate])
            assertEquals(s.center * n + s.center, s.gate)
            assertEquals(s.cellsOn(1).size, s.cellsOn(-1).size)
            for (y in 0 until n) for (x in 0 until n) {
                assertEquals(s.kind[y * n + x], s.kind[(n - 1 - y) * n + x])
                assertEquals(s.kind[y * n + x], s.kind[y * n + (n - 1 - x)])
            }
        }
    }

    @Test
    fun everythingIsInsideTheMaskAndWallsTouchTheGlass() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            val c = (n - 1) / 2.0
            for (i in 0 until n * n) {
                val x = i % n; val y = i / n
                if (s.kind[i] != Cell.OUT) assertTrue(hypot(x - c, y - c) <= n / 2.0)
                if (s.kind[i] == Cell.WALL) {
                    val near = (-1..1).any { dy -> (-1..1).any { dx ->
                        val X = x + dx; val Y = y + dy
                        X in 0 until n && Y in 0 until n && s.isOpen(Y * n + X)
                    } }
                    assertTrue("wall $x,$y", near)
                }
            }
        }
    }
}
```

```kotlin
// app/src/test/java/app/backlit/sand/SandLayoutTest.kt
package app.backlit.sand

import app.backlit.render.px
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SandLayoutTest {
    private fun count(g: BooleanArray, cells: List<Int>) = cells.count { g[it] }

    @Test
    fun fractionSplitsTheLoad() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            for (f in listOf(0.0, 0.2, 0.5, 0.6, 1.0)) {
                val g = SandLayout.layout(s, f, stream = false)
                val top = (s.total * f).px()
                assertEquals("n=$n f=$f top", top, count(g, s.cellsOn(1)))
                assertEquals("n=$n f=$f bottom", s.total - top, count(g, s.cellsOn(-1)))
                assertEquals(s.total, g.count { it })
            }
        }
    }

    @Test
    fun upsideDownMirrorsTopToBottom() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            val a = SandLayout.layout(s, 0.6, false, upSide = 1)
            val b = SandLayout.layout(s, 0.6, false, upSide = -1)
            for (y in 0 until n) for (x in 0 until n) assertEquals(a[y * n + x], b[(n - 1 - y) * n + x])
        }
    }

    @Test
    fun streamAddsTheNeckAndOneFallingDot() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            val plain = SandLayout.layout(s, 0.5, false)
            val st = SandLayout.layout(s, 0.5, true)
            assertEquals(plain.count { it } + 2, st.count { it })
            for (i in SandLayout.streamCells(s, 1)) assertTrue(st[i])
            assertTrue(st[s.gate])
            // no stream when a bulb is empty
            assertEquals(s.total, SandLayout.layout(s, 0.0, true).count { it })
            assertEquals(s.total, SandLayout.layout(s, 1.0, true).count { it })
        }
    }

    @Test
    fun balancedLeftRightWithinOneGrain() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            for (f in listOf(0.0, 0.3, 0.6, 1.0)) {
                val g = SandLayout.layout(s, f, false)
                for (side in listOf(1, -1)) {
                    val cells = s.cellsOn(side)
                    val left = cells.count { g[it] && it % n < s.center }
                    val right = cells.count { g[it] && it % n > s.center }
                    assertTrue("n=$n f=$f side=$side $left vs $right", abs(left - right) <= 1)
                }
            }
        }
    }

    @Test
    fun refillScalesTheWholeLoad() {
        val s = HourglassShape.forSize(25)
        assertEquals(0, SandLayout.layout(s, 0.0, false, fill = 0.0).count { it })
        assertEquals((s.total * 0.5).px(), SandLayout.layout(s, 0.0, false, fill = 0.5).count { it })
        assertEquals(s.total, SandLayout.layout(s, 0.0, false, fill = 1.0).count { it })
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.sand.*'`
Expected: compilation FAIL with `Unresolved reference: HourglassShape`.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/app/backlit/sand/HourglassShape.kt
package app.backlit.sand

import kotlin.math.abs
import kotlin.math.hypot

enum class Cell { OUT, IN, WALL, GATE }

/**
 * The classic hourglass glass (mockup style A) at 25×25 or 13×13. Bulb side: +1 top, −1 bottom, 0 the centre row.
 * The neck gate is the centre cell; walls are LED cells next to the glass.
 */
class HourglassShape private constructor(val size: Int) {
    val center: Int = (size - 1) / 2
    val kind: Array<Cell> = Array(size * size) { Cell.OUT }
    val side: IntArray = IntArray(size * size)
    val gate: Int = center * size + center
    val total: Int

    init {
        val hw = if (size >= 25) intArrayOf(-1, 0, 1, 2, 3, 4, 5, 6, 6, 6, 5) else intArrayOf(-1, 0, 1, 2, 3, 3)
        for (y in 0 until size) for (x in 0 until size) {
            if (!hasLed(x, y)) continue
            val i = y * size + x
            val dy = abs(y - center)
            if (dy < hw.size) {
                if (dy == 0) { if (x == center) kind[i] = Cell.GATE }
                else if (abs(x - center) <= hw[dy]) kind[i] = Cell.IN
            }
            side[i] = if (y < center) 1 else if (y > center) -1 else 0
        }
        for (y in 0 until size) for (x in 0 until size) {
            val i = y * size + x
            if (kind[i] != Cell.OUT || !hasLed(x, y)) continue
            var near = false
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until size && ny in 0 until size) {
                    val k = kind[ny * size + nx]
                    if (k == Cell.IN || k == Cell.GATE) near = true
                }
            }
            if (near) kind[i] = Cell.WALL
        }
        total = (0.72 * minOf(cellsOn(1).size, cellsOn(-1).size)).toInt()
    }

    fun hasLed(x: Int, y: Int): Boolean =
        x in 0 until size && y in 0 until size && hypot(x - (size - 1) / 2.0, y - (size - 1) / 2.0) <= size / 2.0

    fun isOpen(i: Int): Boolean = kind[i] == Cell.IN || kind[i] == Cell.GATE

    fun cellsOn(s: Int): List<Int> = kind.indices.filter { kind[it] == Cell.IN && side[it] == s }

    companion object {
        private val BIG by lazy { HourglassShape(25) }
        private val SMALL by lazy { HourglassShape(13) }
        fun forSize(n: Int): HourglassShape = if (n >= 25) BIG else SMALL
    }
}
```

```kotlin
// app/src/main/java/app/backlit/sand/SandLayout.kt
package app.backlit.sand

import app.backlit.render.px
import kotlin.math.abs

/** Sand at rest, laid out deterministically (mockup `layout`): stills, rebuilds after a gap, and the refill. */
object SandLayout {

    /**
     * [fracUp] of the load rests in the bulb that is up ([upSide]) and the rest piles up in the other. [fill] scales the
     * whole load (the refill). [stream] adds the neck dot and one falling dot when both bulbs hold sand.
     * Built upright, then mirrored for upSide = −1 (the glass is symmetric top to bottom).
     */
    fun layout(shape: HourglassShape, fracUp: Double, stream: Boolean, upSide: Int = 1, fill: Double = 1.0): BooleanArray {
        val n = shape.size
        val c = shape.center
        val load = (shape.total * fill.coerceIn(0.0, 1.0)).px()
        val top = (shape.total * fracUp.coerceIn(0.0, 1.0)).px().coerceAtMost(load)
        val g = BooleanArray(n * n)
        fun tie(i: Int) = if (i % n > c) 0.01 else 0.0
        val upper = shape.cellsOn(1).sortedBy { i -> (c - i / n) - abs(i % n - c) * 0.35 + tie(i) }
        val lower = shape.cellsOn(-1).sortedBy { i -> (n - 1 - i / n) + abs(i % n - c) * 0.75 + tie(i) }
        for (k in 0 until top) g[upper[k]] = true
        for (k in 0 until load - top) g[lower[k]] = true
        if (stream && top in 1 until load) for (i in streamCells(shape, 1)) g[i] = true
        if (upSide >= 0) return g
        val m = BooleanArray(n * n)
        for (y in 0 until n) for (x in 0 until n) m[(n - 1 - y) * n + x] = g[y * n + x]
        return m
    }

    /** The neck gate and the dot two rows below it (towards the lower bulb). */
    fun streamCells(shape: HourglassShape, upSide: Int): IntArray {
        val n = shape.size
        val c = shape.center
        val below = if (upSide >= 0) c + 2 else c - 2
        return intArrayOf(shape.gate, below * n + c)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.sand.*'`
Expected: PASS. If `balancedLeftRightWithinOneGrain` fails for one fraction, check the `tie` term: it breaks ties left-first, exactly as the mockup's `(x>c?0.01:0)` does, so the imbalance can be at most one grain per bulb.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/sand app/src/test/java/app/backlit/sand
git commit -m "feat(sand): hourglass glass and resting sand layout

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Sand physics and the clock-steered gate

**Files:**
- Create: `app/src/main/java/app/backlit/sand/SandSim.kt`
- Test: `app/src/test/java/app/backlit/sand/SandSimTest.kt`

**Interfaces:**
- Consumes: `HourglassShape`, `SandLayout` (Task 1).
- Produces:
  - `class SandSim(shape, random: java.util.Random = java.util.Random())`, with `grid: BooleanArray`, `moved: BooleanArray`, `var gateRate: Double` (grains per ms), `load(grains)`, `count()`, `countOn(side)` (IN cells only, never the gate), and `step(gx: Double, gy: Double, dtMs: Double)`. The `(gx, gy)` argument is a unit vector in matrix coordinates, with +y down.
  - `SandSim.gateRateFor(total: Int, durationMs: Long, countUp: Int, leftMs: Long): Double`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/app/backlit/sand/SandSimTest.kt
package app.backlit.sand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

class SandSimTest {
    private fun full(n: Int, seed: Long = 1) = SandSim(HourglassShape.forSize(n), Random(seed)).also {
        it.load(SandLayout.layout(it.shape, 1.0, false))
    }

    @Test
    fun grainsAreConservedAndStayInTheGlass() {
        for (n in listOf(25, 13)) {
            val sim = full(n)
            sim.gateRate = 0.01
            val r = Random(7)
            repeat(10_000) {
                val a = r.nextDouble() * 2 * Math.PI
                sim.step(sin(a), cos(a), 50.0)
                if (it % 500 == 0) {
                    assertEquals(sim.shape.total, sim.count())
                    for (i in sim.grid.indices) if (sim.grid[i]) assertTrue(sim.shape.isOpen(i))
                }
            }
            assertEquals(sim.shape.total, sim.count())
        }
    }

    @Test
    fun withTheGateClosedNoGrainChangesBulb() {
        for (n in listOf(25, 13)) {
            val sim = full(n)
            sim.gateRate = 0.0
            val r = Random(3)
            repeat(2_000) {
                val a = r.nextDouble() * 2 * Math.PI
                sim.step(sin(a), cos(a), 50.0)
                assertEquals(sim.shape.total, sim.countOn(1))
            }
        }
    }

    @Test
    fun onItsSideNothingCrossesTheNeck() {
        for (n in listOf(25, 13)) for (gx in listOf(1.0, -1.0)) {
            val sim = full(n)
            sim.gateRate = 1.0
            repeat(500) { sim.step(gx, 0.0, 50.0) }
            assertEquals(sim.shape.total, sim.countOn(1))
        }
    }

    @Test
    fun drainsWithTheClock() {
        for (n in listOf(25, 13)) {
            val sim = full(n)
            val d = 60_000L
            var t = 0L
            var finished = -1L
            while (t < 2 * d) {
                if (t % 1000 == 0L) sim.gateRate = SandSim.gateRateFor(sim.shape.total, d, sim.countOn(1), (d - t).coerceAtLeast(0))
                sim.step(0.0, 1.0, 50.0)
                t += 50
                if (sim.countOn(1) == 0 && !sim.grid[sim.shape.gate]) { finished = t; break }
            }
            assertTrue("n=$n finished at $finished", finished in (d * 95 / 100)..(d * 105 / 100))
        }
    }

    @Test
    fun gateRateControllerFollowsTheClock() {
        val base = 61.0 / 60_000
        assertEquals(base, SandSim.gateRateFor(61, 60_000, 61, 60_000), 1e-12)        // on track
        assertEquals(base + 3 / 1000.0, SandSim.gateRateFor(61, 60_000, 34, 30_000), 1e-12)  // 3 extra grains on top
        assertEquals(0.0, SandSim.gateRateFor(61, 60_000, 20, 30_000), 1e-12)          // too few: wait for the clock
    }
}
```

The "3 extra" case works like this: `should = ceil(61 × 30000 / 60000) = ceil(30.5) = 31`, and 34 − 31 = 3.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.sand.SandSimTest'`
Expected: compilation FAIL with `Unresolved reference: SandSim`.

- [ ] **Step 3: Implement** (a line-for-line port of the mockup's `step`)

```kotlin
// app/src/main/java/app/backlit/sand/SandSim.kt
package app.backlit.sand

import java.util.Random
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min

/**
 * Falling sand in the hourglass. Grains try the neighbour moves that point along gravity (dot > 0.38), best first,
 * furthest-along grains first; they cross bulbs only through the neck gate, which lets one grain in per budget unit.
 */
class SandSim(val shape: HourglassShape, private val random: Random = Random()) {
    private val n = shape.size
    val grid = BooleanArray(n * n)
    val moved = BooleanArray(n * n)
    /** Grains per ms the neck lets through. */
    var gateRate = 0.0
    private var budget = 0.0

    fun load(grains: BooleanArray) {
        grains.copyInto(grid)
        moved.fill(false)
        budget = 0.0
    }

    fun count(): Int = grid.count { it }

    /** Grains resting in bulb [s] (the gate cell is never counted). */
    fun countOn(s: Int): Int = grid.indices.count { grid[it] && shape.kind[it] == Cell.IN && shape.side[it] == s }

    /** One step. (gx, gy) is the unit gravity direction in matrix coordinates (+y is down the matrix). */
    fun step(gx: Double, gy: Double, dtMs: Double) {
        moved.fill(false)
        budget = min(2.0, budget + gateRate * dtMs)
        val grains = grid.indices.filter { grid[it] }.sortedByDescending { (it % n) * gx + (it / n) * gy }
        val moves = DIRS
            .map { (dx, dy) -> Move(dx, dy, (dx * gx + dy * gy) / hypot(dx.toDouble(), dy.toDouble()) + random.nextDouble() * 0.02) }
            .filter { it.dot > 0.38 }
            .sortedByDescending { it.dot }
        for (i in grains) {
            val x = i % n
            val y = i / n
            for (m in moves) {
                val nx = x + m.dx
                val ny = y + m.dy
                if (nx !in 0 until n || ny !in 0 until n) continue
                val j = ny * n + nx
                if (!shape.isOpen(j) || grid[j]) continue
                if (shape.side[i] * shape.side[j] == -1) continue
                if (m.dx != 0 && m.dy != 0 && !shape.isOpen(y * n + nx) && !shape.isOpen(ny * n + x)) continue
                if (shape.kind[j] == Cell.GATE) {
                    if (budget < 1) continue
                    budget -= 1
                }
                grid[i] = false
                grid[j] = true
                moved[j] = true
                break
            }
        }
    }

    private data class Move(val dx: Int, val dy: Int, val dot: Double)

    companion object {
        private val DIRS = (-1..1).flatMap { dy -> (-1..1).map { dx -> dx to dy } }.filter { it != 0 to 0 }

        /**
         * The neck rate that keeps the sand on the clock: [countUp] grains rest in the up bulb and
         * ceil(total × left / duration) should. More → base + extra / 1000; fewer → 0 until the clock catches up.
         */
        fun gateRateFor(total: Int, durationMs: Long, countUp: Int, leftMs: Long): Double {
            val base = total.toDouble() / durationMs
            val should = ceil(total * leftMs.toDouble() / durationMs - 1e-9).toInt()
            val gap = countUp - should
            return when {
                gap > 0 -> base + gap / 1000.0
                gap < 0 -> 0.0
                else -> base
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.sand.SandSimTest'`
Expected: PASS. If `drainsWithTheClock` finishes late at 13×13, it is because grains can't reach the one-cell neck column fast enough. Log the finish time, then use superpowers:systematic-debugging. Do not widen the 5% window.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/sand/SandSim.kt app/src/test/java/app/backlit/sand/SandSimTest.kt
git commit -m "feat(sand): grain physics with a clock-steered neck

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Timer state machine

**Files:**
- Create: `app/src/main/java/app/backlit/sand/TimerState.kt`
- Test: `app/src/test/java/app/backlit/sand/TimerStateTest.kt`

**Interfaces:**
- Produces:
  - `enum class Phase { READY, RUNNING, PAUSED, DONE }`
  - `enum class Orientation { UP, DOWN, SIDE, FLAT }`, with `Orientation.of(gx: Float, gy: Float)`. Both values are m/s² in matrix coordinates, with +y down.
  - `@Serializable data class TimerState(phase, durationMs, presetIndex, upSide, endAt, leftMs, doneAt, sideSince, numberUntil, numberValue, refillUntil, lastPressAt)`. Its members are:
    - `timeLeft(now)` and `fractionUp(now)`
    - `tick(now)`
    - `onOrientation(o, now)` and `baseline(o, now)`
    - `longPress(now, presets)` and `select(index, presets)`
    - `alarmFired(now): TimerState?`
    - `persisted()`: a copy without the transient fields
    - `encode()`
  - Companion: `decode(json)`, `clock(ms): String`, and the constants `MIN`, `SIDE_MS`, `DOUBLE_MS`, `NUMBER_MS`, `REFILL_MS`, `FLIP_ME_MS = 2300` and `DEFAULT_PRESETS`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/app/backlit/sand/TimerStateTest.kt
package app.backlit.sand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimerStateTest {
    private val m = TimerState.MIN
    private val presets = listOf(1, 3, 5, 10, 25)
    private fun running(left: Long, now: Long = 0, d: Long = 5 * m, up: Int = 1) =
        TimerState(phase = Phase.RUNNING, durationMs = d, upSide = up, endAt = now + left)

    @Test
    fun orientationFromGravity() {
        assertEquals(Orientation.UP, Orientation.of(0f, 9.8f))
        assertEquals(Orientation.DOWN, Orientation.of(0f, -9.8f))
        assertEquals(Orientation.SIDE, Orientation.of(9.8f, 0.5f))
        assertEquals(Orientation.FLAT, Orientation.of(1f, 1.5f))
    }

    @Test
    fun readyStartsOnlyOnARealFlip() {
        val r = TimerState(upSide = 1)
        assertEquals(Phase.READY, r.onOrientation(Orientation.UP, 10).phase)
        assertEquals(Phase.READY, r.onOrientation(Orientation.FLAT, 10).phase)
        val s = r.onOrientation(Orientation.DOWN, 10)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(-1, s.upSide)
        assertEquals(10 + 5 * m, s.endAt)
    }

    @Test
    fun flipMidRunSwapsLeftAndRun() {
        val s = running(left = 2 * m).onOrientation(Orientation.DOWN, 0)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(3 * m, s.timeLeft(0))
        assertEquals(-1, s.upSide)
    }

    @Test
    fun flipWithNothingRunGoesStraightToDone() {
        val s = running(left = 5 * m).onOrientation(Orientation.DOWN, 0)
        assertEquals(Phase.DONE, s.phase)
        assertEquals(0L, s.doneAt)
    }

    @Test
    fun flipFromDoneRunsTheFullTimeAgain() {
        val done = TimerState(phase = Phase.DONE, durationMs = 3 * m, upSide = 1)
        val s = done.onOrientation(Orientation.DOWN, 100)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(3 * m, s.timeLeft(100))
    }

    @Test
    fun tickFinishesAtEndAt() {
        val s = running(left = 1000)
        assertEquals(Phase.RUNNING, s.tick(999).phase)
        val d = s.tick(1000)
        assertEquals(Phase.DONE, d.phase)
        assertEquals(1000L, d.doneAt)
    }

    @Test
    fun sidePausesAfterDebounceAndKeepsTimeFromWhenItTipped() {
        val s0 = running(left = 2 * m)
        val s1 = s0.onOrientation(Orientation.SIDE, 1000)
        assertEquals(Phase.RUNNING, s1.phase)
        val s2 = s1.onOrientation(Orientation.SIDE, 1499)
        assertEquals(Phase.RUNNING, s2.phase)
        val s3 = s2.onOrientation(Orientation.SIDE, 1500)
        assertEquals(Phase.PAUSED, s3.phase)
        assertEquals(2 * m - 1000, s3.timeLeft(99_999))
    }

    @Test
    fun aWobbleDoesNotPause() {
        val s = running(left = 2 * m).onOrientation(Orientation.SIDE, 0).onOrientation(Orientation.UP, 300).onOrientation(Orientation.SIDE, 400)
        assertEquals(Phase.RUNNING, s.onOrientation(Orientation.SIDE, 800).phase)
    }

    @Test
    fun resumeSameWayKeepsTimeOppositeWayFlips() {
        val paused = TimerState(phase = Phase.PAUSED, durationMs = 5 * m, upSide = 1, leftMs = 2 * m)
        assertEquals(2 * m, paused.onOrientation(Orientation.UP, 0).timeLeft(0))
        val flipped = paused.onOrientation(Orientation.DOWN, 0)
        assertEquals(3 * m, flipped.timeLeft(0))
        assertEquals(-1, flipped.upSide)
    }

    @Test
    fun flatChangesNothing() {
        val s = running(left = 2 * m)
        assertEquals(s, s.onOrientation(Orientation.FLAT, 10))
    }

    @Test
    fun baselineNeverStartsReadyOrDone() {
        val r = TimerState(upSide = 1).baseline(Orientation.DOWN, 0)
        assertEquals(Phase.READY, r.phase)
        assertEquals(-1, r.upSide)
        val d = TimerState(phase = Phase.DONE, upSide = 1).baseline(Orientation.DOWN, 0)
        assertEquals(Phase.DONE, d.phase)
        // a running timer that was flipped while away still flips
        assertEquals(3 * m, running(left = 2 * m).baseline(Orientation.DOWN, 0).timeLeft(0))
    }

    @Test
    fun longPressInReadyCyclesPresetsAndShowsTheNumber() {
        val s = TimerState(presetIndex = 2, durationMs = 5 * m).longPress(1000, presets)
        assertEquals(Phase.READY, s.phase)
        assertEquals(3, s.presetIndex)
        assertEquals(10 * m, s.durationMs)
        assertEquals(10, s.numberValue)
        assertEquals(1000 + TimerState.NUMBER_MS, s.numberUntil)
        assertEquals(1000 + TimerState.NUMBER_MS + TimerState.REFILL_MS, s.refillUntil)
        assertEquals(0, TimerState(presetIndex = 4).longPress(0, presets).presetIndex)   // wraps
    }

    @Test
    fun longPressWithIndexPastEndWraps() {
        val s = TimerState(presetIndex = 9).longPress(0, listOf(2, 4))
        assertEquals(0, s.presetIndex)
        assertEquals(2 * m, s.durationMs)
        assertEquals(1, TimerState(presetIndex = 0).longPress(0, emptyList()).presetIndex)   // empty → defaults
    }

    @Test
    fun longPressWhileRunningShowsMinutesLeftThenASecondPressCycles() {
        val r = running(left = 2 * m + 1, now = 1000)
        val shown = r.longPress(1000, presets)
        assertEquals(Phase.RUNNING, shown.phase)
        assertEquals(3, shown.numberValue)
        assertEquals(r.endAt, shown.endAt)
        val again = shown.longPress(1000 + TimerState.DOUBLE_MS, presets)
        assertEquals(Phase.READY, again.phase)
        val late = shown.longPress(1000 + TimerState.DOUBLE_MS + 1, presets)
        assertEquals(Phase.RUNNING, late.phase)
    }

    @Test
    fun selectSetsReadyAtThatPreset() {
        val s = running(left = m).select(1, presets)
        assertEquals(Phase.READY, s.phase)
        assertEquals(3 * m, s.durationMs)
        assertEquals(1, s.presetIndex)
    }

    @Test
    fun alarmOnlyFinishesARunningTimer() {
        assertEquals(Phase.DONE, running(left = 1000).alarmFired(500)?.phase)          // within 1 s early
        assertNull(running(left = 5000).alarmFired(500))
        assertNull(TimerState(phase = Phase.PAUSED, leftMs = 0).alarmFired(10))
        assertNull(TimerState(phase = Phase.DONE).alarmFired(10))
    }

    @Test
    fun restoreRoundTripAndBadJson() {
        val s = running(left = 90_000, now = 123).copy(presetIndex = 3, sideSince = 5)
        assertEquals(s.persisted(), TimerState.decode(s.encode()))
        assertEquals(TimerState(), TimerState.decode(""))
        assertEquals(TimerState(), TimerState.decode("{nope"))
        assertEquals(s.copy(sideSince = 0), s.persisted())
    }

    @Test
    fun fractionsAndClock() {
        assertEquals(0.0, TimerState().fractionUp(0), 0.0)
        assertEquals(0.4, running(left = 2 * m).fractionUp(0), 1e-9)
        assertEquals("2:00", TimerState.clock(2 * m))
        assertEquals("0:01", TimerState.clock(1))
        assertEquals("25:00", TimerState.clock(25 * m))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.sand.TimerStateTest'`
Expected: compilation FAIL with `Unresolved reference: TimerState`.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/app/backlit/sand/TimerState.kt
package app.backlit.sand

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.hypot

enum class Phase { READY, RUNNING, PAUSED, DONE }

enum class Orientation {
    UP, DOWN, SIDE, FLAT;

    companion object {
        /** (gx, gy): gravity in matrix coordinates, m/s² (+y down the matrix, so UP means the top bulb is up). */
        fun of(gx: Float, gy: Float): Orientation {
            val m = hypot(gx, gy)
            if (m < 3f) return FLAT
            val ny = gy / m
            return when {
                ny > 0.2f -> UP
                ny < -0.2f -> DOWN
                else -> SIDE
            }
        }
    }
}

/**
 * The hourglass clock, kept apart from the pixels. The bulb that is up ([upSide]) holds the time left, so a flip
 * swaps time left and time run. Wall-clock ms throughout.
 */
@Serializable
data class TimerState(
    val phase: Phase = Phase.READY,
    val durationMs: Long = 5 * MIN,
    val presetIndex: Int = 2,
    val upSide: Int = 1,
    val endAt: Long = 0L,
    val leftMs: Long = 0L,
    val doneAt: Long = 0L,
    val sideSince: Long = 0L,
    val numberUntil: Long = 0L,
    val numberValue: Int = 0,
    val refillUntil: Long = 0L,
    val lastPressAt: Long = 0L,
) {
    fun timeLeft(now: Long): Long = when (phase) {
        Phase.READY -> durationMs
        Phase.RUNNING -> (endAt - now).coerceIn(0L, durationMs)
        Phase.PAUSED -> leftMs
        Phase.DONE -> 0L
    }

    /** Share of the sand in the up bulb (READY and DONE rest in the down bulb). */
    fun fractionUp(now: Long): Double = when (phase) {
        Phase.RUNNING, Phase.PAUSED -> timeLeft(now).toDouble() / durationMs
        else -> 0.0
    }

    fun tick(now: Long): TimerState =
        if (phase == Phase.RUNNING && now >= endAt) copy(phase = Phase.DONE, doneAt = endAt) else this

    fun onOrientation(o: Orientation, now: Long): TimerState {
        val t = tick(now)
        return when (o) {
            Orientation.FLAT -> t
            Orientation.SIDE -> t.onSide(now)
            Orientation.UP -> t.onUp(1, now)
            Orientation.DOWN -> t.onUp(-1, now)
        }
    }

    /** First reading after the toy (re)appears: READY/DONE only learn the orientation; a running timer still flips. */
    fun baseline(o: Orientation, now: Long): TimerState {
        val up = when (o) { Orientation.UP -> 1; Orientation.DOWN -> -1; else -> return tick(now) }
        return if (phase == Phase.READY || phase == Phase.DONE) copy(upSide = up, sideSince = 0L) else onOrientation(o, now)
    }

    private fun onSide(now: Long): TimerState {
        if (sideSince == 0L) return copy(sideSince = now)
        if (phase != Phase.RUNNING || now - sideSince < SIDE_MS) return this
        return copy(phase = Phase.PAUSED, leftMs = (endAt - sideSince).coerceIn(0L, durationMs))
    }

    private fun onUp(up: Int, now: Long): TimerState {
        val s = copy(sideSince = 0L)
        return when (phase) {
            Phase.READY, Phase.DONE -> if (up != upSide) s.run(durationMs, up, now) else s
            Phase.RUNNING -> if (up != upSide) s.run(durationMs - timeLeft(now), up, now) else s
            Phase.PAUSED -> if (up == upSide) s.run(leftMs, up, now) else s.run(durationMs - leftMs, up, now)
        }
    }

    private fun run(left: Long, up: Int, now: Long): TimerState =
        if (left <= 0L) copy(phase = Phase.DONE, upSide = up, doneAt = now, leftMs = 0L, numberUntil = 0L, refillUntil = 0L)
        else copy(phase = Phase.RUNNING, upSide = up, endAt = now + left, leftMs = 0L, numberUntil = 0L, refillUntil = 0L)

    fun longPress(now: Long, presets: List<Int>): TimerState {
        val list = presets.ifEmpty { DEFAULT_PRESETS }
        return when (phase) {
            Phase.READY, Phase.DONE -> cycle(now, list)
            Phase.RUNNING, Phase.PAUSED ->
                if (lastPressAt > 0L && now - lastPressAt <= DOUBLE_MS) cycle(now, list)
                else copy(
                    numberUntil = now + NUMBER_MS,
                    numberValue = ((timeLeft(now) + MIN - 1) / MIN).toInt().coerceAtLeast(1),
                    refillUntil = 0L,
                    lastPressAt = now,
                )
        }
    }

    private fun cycle(now: Long, list: List<Int>): TimerState {
        val i = (presetIndex.coerceIn(0, list.size - 1) + 1) % list.size
        return TimerState(
            phase = Phase.READY, durationMs = list[i] * MIN, presetIndex = i, upSide = upSide,
            numberUntil = now + NUMBER_MS, numberValue = list[i], refillUntil = now + NUMBER_MS + REFILL_MS,
        )
    }

    /** The TIMER tab picks a preset directly. */
    fun select(index: Int, presets: List<Int>): TimerState {
        val list = presets.ifEmpty { DEFAULT_PRESETS }
        val i = index.coerceIn(0, list.size - 1)
        return TimerState(phase = Phase.READY, durationMs = list[i] * MIN, presetIndex = i, upSide = upSide)
    }

    /** The done alarm: finishes a RUNNING timer due within 1 s; anything else is left alone (null). */
    fun alarmFired(now: Long): TimerState? =
        if (phase == Phase.RUNNING && endAt <= now + 1000) copy(phase = Phase.DONE, doneAt = endAt) else null

    /** What is saved: the transient display and debounce fields are dropped. */
    fun persisted(): TimerState = copy(sideSince = 0L, numberUntil = 0L, numberValue = 0, refillUntil = 0L, lastPressAt = 0L)

    fun encode(): String = json.encodeToString(persisted())

    companion object {
        const val MIN = 60_000L
        const val SIDE_MS = 500L
        const val DOUBLE_MS = 2_000L
        const val NUMBER_MS = 1_500L
        const val REFILL_MS = 700L
        const val FLIP_ME_MS = 2_300L
        val DEFAULT_PRESETS = listOf(1, 3, 5, 10, 25)
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(s: String): TimerState =
            if (s.isBlank()) TimerState() else runCatching { json.decodeFromString<TimerState>(s) }.getOrDefault(TimerState())

        /** m:ss, seconds rounded up. */
        fun clock(ms: Long): String {
            val s = (ms.coerceAtLeast(0) + 999) / 1000
            return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.sand.TimerStateTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/sand/TimerState.kt app/src/test/java/app/backlit/sand/TimerStateTest.kt
git commit -m "feat(sand): hourglass timer state (flip, side, long press, restore)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Sand art, previews and the alert hook

**Files:**
- Create: `app/src/main/java/app/backlit/sand/SandArt.kt`
- Create: `app/src/main/java/app/backlit/sand/SandPreviewAnimation.kt`
- Modify: `app/src/main/java/app/backlit/alerts/AlertsRuntime.kt` (`animationFor`)
- Test: `app/src/test/java/app/backlit/sand/SandArtTest.kt`

**Interfaces:**
- Consumes: Tasks 1–3, `PixelGrid`, `PixelFont` (3×5), `PixelFont5x7`, `px()`, `GlyphAnimation`.
- Produces:
  - `SandArt.frame(shape, grains: BooleanArray, moved: BooleanArray?, st: TimerState, now: Long): PixelGrid`
  - `SandArt.still(shape, st, now): PixelGrid`
  - `SandArt.picture(shape, grains, moved, wall: Double, bright: Set<Int> = emptySet())`
  - `SandArt.flipMe(shape, upSide, t)`
  - `SandPreviewAnimation.parse(id)`, with the ids `READY_ID`, `RUNNING_ID` and `DONE_ID`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/app/backlit/sand/SandArtTest.kt
package app.backlit.sand

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SandArtTest {
    private val m = TimerState.MIN

    private fun assertInMask(g: PixelGrid, label: String) {
        assertTrue("$label has lit pixels", g.litCount() > 0)
        for (y in 0 until g.size) for (x in 0 until g.size) if (g[x, y] > 0) assertTrue("$label $x,$y", g.hasLed(x, y))
    }

    @Test
    fun everyViewRendersInsideTheMask() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            val grains = SandLayout.layout(s, 0.5, false)
            val states = listOf(
                TimerState(),
                TimerState(phase = Phase.RUNNING, endAt = 2 * m),
                TimerState(phase = Phase.PAUSED, leftMs = m),
                TimerState(phase = Phase.DONE, doneAt = 0),
                TimerState(numberUntil = 100, numberValue = 25, refillUntil = 800),
            )
            for (st in states) for (t in listOf(0L, 50L, 400L, 1250L, 1400L, 5000L)) {
                assertInMask(SandArt.frame(s, grains, null, st, t), "frame n=$n ${st.phase} t=$t")
                assertInMask(SandArt.still(s, st, t), "still n=$n ${st.phase} t=$t")
            }
        }
    }

    @Test
    fun flipMeSpinsTheDonePictureHalfATurn() {
        for (n in listOf(25, 13)) {
            val s = HourglassShape.forSize(n)
            val src = SandArt.picture(s, SandLayout.layout(s, 0.0, false), null, 0.3)
            val start = SandArt.flipMe(s, 1, 0)
            val end = SandArt.flipMe(s, 1, 1250)
            for (y in 0 until n) for (x in 0 until n) {
                assertEquals(src[x, y], start[x, y])
                assertEquals("n=$n $x,$y", src[n - 1 - x, n - 1 - y], end[x, y])
            }
        }
    }

    @Test
    fun numberViewShowsTheDigits() {
        val s25 = HourglassShape.forSize(25)
        val g = SandArt.frame(s25, SandLayout.layout(s25, 0.0, false), null, TimerState(numberUntil = 1000, numberValue = 5), 0)
        for (x in 10..14) assertEquals(255, g[x, 9])        // 5×7 "5" top bar at x0=10, y0=9
        val s13 = HourglassShape.forSize(13)
        val h = SandArt.frame(s13, SandLayout.layout(s13, 0.0, false), null, TimerState(numberUntil = 1000, numberValue = 5), 0)
        for (x in 5..7) assertEquals(255, h[x, 4])          // 3×5 "5" top bar at x0=5, y0=4
    }

    @Test
    fun runningStillHasABrightStreamAndDoneHasABrightGlass() {
        val s = HourglassShape.forSize(25)
        val run = SandArt.still(s, TimerState(phase = Phase.RUNNING, durationMs = 100_000, endAt = 60_000), 0)
        assertEquals(255, run[s.center, s.center])
        val done = SandArt.still(s, TimerState(phase = Phase.DONE), 0)
        val wallCell = s.kind.indices.first { s.kind[it] == Cell.WALL }
        assertEquals(89, done[wallCell % 25, wallCell / 25])   // 0.35 × 255 = 89.25 → 89
    }

    @Test
    fun previewsParse() {
        assertNotNull(SandPreviewAnimation.parse(SandPreviewAnimation.RUNNING_ID))
        assertNotNull(SandPreviewAnimation.parse("sand:done"))
        assertEquals(null, SandPreviewAnimation.parse("pet:happy"))
        for (id in listOf("sand:ready", "sand:running", "sand:done")) for (n in listOf(25, 13)) {
            val a = SandPreviewAnimation.parse(id)!!
            for (t in 0L..a.loopMs step 250) assertInMask(a.frame(n, t), "$id n=$n t=$t")
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.sand.SandArtTest'`
Expected: compilation FAIL with `Unresolved reference: SandArt`.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/app/backlit/sand/SandArt.kt
package app.backlit.sand

import app.backlit.render.PixelFont
import app.backlit.render.PixelFont5x7
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Draws the hourglass (mockups 2026-10-06-sand-styles style A and sand-moments). */
object SandArt {
    private const val WALL = 0.16
    private const val GRAIN = 0.7

    private fun b(v: Double): Int = (v * 255).px()

    /** Live frame while the toy is ACTIVE. [grains]/[moved] come from the SandSim. */
    fun frame(shape: HourglassShape, grains: BooleanArray, moved: BooleanArray?, st: TimerState, now: Long): PixelGrid {
        if (now < st.numberUntil) return number(shape, grains, st.numberValue)
        if (now < st.refillUntil) {
            val f = (now - st.numberUntil).toDouble() / TimerState.REFILL_MS
            return picture(shape, SandLayout.layout(shape, 0.0, false, st.upSide, f.coerceIn(0.0, 1.0)), null, WALL)
        }
        if (st.phase == Phase.DONE && now - st.doneAt in 0 until TimerState.FLIP_ME_MS) return flipMe(shape, st.upSide, now - st.doneAt)
        val wall = when (st.phase) {
            Phase.READY -> 0.16 + 0.12 * max(0.0, sin(now / 500.0))
            Phase.DONE -> 0.16 + 0.08 * max(0.0, sin(now / 1500.0))
            else -> WALL
        }
        return picture(shape, grains, moved, wall)
    }

    /** Always-on still (one frame a minute). */
    fun still(shape: HourglassShape, st: TimerState, now: Long): PixelGrid {
        val running = st.phase == Phase.RUNNING
        val grains = SandLayout.layout(shape, st.fractionUp(now), running, st.upSide)
        if (now < st.numberUntil) return number(shape, grains, st.numberValue)
        val bright = if (running) SandLayout.streamCells(shape, st.upSide).toSet() else emptySet()
        return picture(shape, grains, null, if (st.phase == Phase.DONE) 0.35 else WALL, bright)
    }

    fun picture(shape: HourglassShape, grains: BooleanArray, moved: BooleanArray?, wall: Double, bright: Set<Int> = emptySet()): PixelGrid {
        val n = shape.size
        val g = PixelGrid(n)
        for (i in 0 until n * n) {
            val x = i % n
            val y = i / n
            if (grains[i]) g.put(x, y, b(if (moved?.get(i) == true || i in bright) 1.0 else GRAIN))
            else if (shape.kind[i] == Cell.WALL) g.put(x, y, b(wall))
        }
        return g
    }

    /** The long-press number on a cleared window over the dimmed hourglass. 5×7 digits at 25×25, 3×5 at 13×13. */
    private fun number(shape: HourglassShape, grains: BooleanArray, value: Int): PixelGrid {
        val n = shape.size
        val base = picture(shape, grains, null, WALL)
        val g = PixelGrid(n)
        for (y in 0 until n) for (x in 0 until n) g.put(x, y, (base[x, y] * 0.25).px())
        val s = value.coerceIn(0, 99).toString()
        val big = n >= 25
        val w = if (big) PixelFont5x7.WIDTH else PixelFont.WIDTH
        val h = if (big) PixelFont5x7.HEIGHT else PixelFont.HEIGHT
        val width = s.length * (w + 1) - 1
        val x0 = (shape.center - (width - 1) / 2.0).px()
        val y0 = (shape.center - (h - 1) / 2.0).px()
        for (y in y0 - 1..y0 + h) for (x in x0 - 1..x0 + width) g.put(x, y, 0)
        s.forEachIndexed { k, ch ->
            val x = x0 + k * (w + 1)
            if (big) PixelFont5x7.digit(g, ch - '0', x, y0, 255) else PixelFont.digit(g, ch - '0', x, y0, 255)
        }
        return g
    }

    /** "Flip me": the done picture spins half a turn (eased, 1200 ms), then the glass blinks twice. */
    fun flipMe(shape: HourglassShape, upSide: Int, t: Long): PixelGrid {
        val n = shape.size
        val c = shape.center.toDouble()
        val src = picture(shape, SandLayout.layout(shape, 0.0, false, upSide), null, 0.3)
        val a = if (t < 1200) (1 - cos(t / 1200.0 * PI)) / 2 * PI else PI
        val ca = cos(-a)
        val sa = sin(-a)
        val blink = t in 1300 until 2300 && ((t - 1300) / 250) % 2 == 0L
        val g = PixelGrid(n)
        for (y in 0 until n) for (x in 0 until n) {
            if (!g.hasLed(x, y)) continue
            val dx = x - c
            val dy = y - c
            var v = src[(c + dx * ca - dy * sa).px(), (c + dx * sa + dy * ca).px()]
            if (blink && v in 1 until 128) v = b(0.85)
            g.put(x, y, v)
        }
        return g
    }
}
```

```kotlin
// app/src/main/java/app/backlit/sand/SandPreviewAnimation.kt
package app.backlit.sand

import app.backlit.anim.GlyphAnimation
import app.backlit.render.PixelGrid

/** The hourglass as a GlyphAnimation: "Show on Glyph", the away-from-toy done moment, and picker previews. */
class SandPreviewAnimation private constructor(private val view: String) : GlyphAnimation {
    override val id: String = "sand:$view"
    override val name: String = "Sand"
    override val loopMs: Long = if (view == "done") TimerState.FLIP_ME_MS + 700 else 6_000L

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val shape = HourglassShape.forSize(size)
        val t = ((tMs % loopMs) + loopMs) % loopMs
        return when (view) {
            "ready" -> SandArt.frame(shape, SandLayout.layout(shape, 0.0, false), null, TimerState(), t)
            "done" -> SandArt.flipMe(shape, 1, t)
            else -> {
                val grains = SandLayout.layout(shape, 1.0 - t.toDouble() / loopMs, true)
                val bright = if ((t / 150) % 2 == 0L) SandLayout.streamCells(shape, 1).toSet() else emptySet()
                SandArt.picture(shape, grains, null, 0.16, bright)
            }
        }
    }

    companion object {
        const val READY_ID = "sand:ready"
        const val RUNNING_ID = "sand:running"
        const val DONE_ID = "sand:done"

        fun parse(id: String): SandPreviewAnimation? = when (id) {
            READY_ID -> SandPreviewAnimation("ready")
            RUNNING_ID -> SandPreviewAnimation("running")
            DONE_ID -> SandPreviewAnimation("done")
            else -> null
        }
    }
}
```

In `alerts/AlertsRuntime.kt`, add `import app.backlit.sand.SandPreviewAnimation` and change `animationFor` to:

```kotlin
    fun animationFor(alert: ActiveAlert): GlyphAnimation =
        (if (alert.animationId == PREVIEW_ID) previewSlot else null)
            ?: ChargePreviewAnimation.parse(alert.animationId)
            ?: PetPreviewAnimation.parse(alert.animationId)
            ?: SandPreviewAnimation.parse(alert.animationId)
            ?: library.resolve(
                alert.animationId, current.imports,
                fallback = if (alert.kind == AlertKind.CALL) BuiltInAnimations.DEFAULT_CONTACT else BuiltInAnimations.DEFAULT_DEVICE,
            )
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.sand.*'`
Expected: PASS. Pay attention to the done-still wall value: `0.35 × 255 = 89.25` and `px()` gives 89.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/sand app/src/test/java/app/backlit/sand app/src/main/java/app/backlit/alerts/AlertsRuntime.kt
git commit -m "feat(sand): hourglass art, Flip me, number view and previews

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Sand settings

**Files:**
- Modify: `app/src/main/java/app/backlit/data/Settings.kt`
- Modify: `app/src/main/java/app/backlit/data/SettingsRepo.kt`
- Test: `app/src/test/java/app/backlit/data/SettingsRepoTest.kt`

**Interfaces:**
- Produces: the `Settings` fields `sandPresets: List<Int> = listOf(1, 3, 5, 10, 25)`, `sandAlert: String = "vibrate"`, `sandExact: Boolean = false`, `sandTimer: String = ""` and `sandToyEverBound: Boolean = false`, plus `SettingsRepo.cleanPresets(list): List<Int>` and `SettingsRepo.parseSandAlert(s: String?): String`.

- [ ] **Step 1: Write the failing tests** (append to `SettingsRepoTest`, and extend `roundTripsEveryField`)

In `roundTripsEveryField`, add these to the `Settings(...)` constructor after `petNames = ...`:

```kotlin
            sandPresets = listOf(2, 7, 45), sandAlert = "chime", sandExact = true,
            sandTimer = "{\"phase\":\"RUNNING\",\"endAt\":5}", sandToyEverBound = true,
```

Then append these tests:

```kotlin
    @Test
    fun sandDefaults() = runBlocking {
        val s = repo().settings.first()
        assertEquals(listOf(1, 3, 5, 10, 25), s.sandPresets)
        assertEquals("vibrate", s.sandAlert); assertEquals(false, s.sandExact)
        assertEquals("", s.sandTimer); assertEquals(false, s.sandToyEverBound)
    }

    @Test
    fun presetsAreCleaned() {
        assertEquals(listOf(1, 5, 99), SettingsRepo.cleanPresets(listOf(5, 0, 1, 5, 120, 99, -3)))
        assertEquals(listOf(1, 3, 5, 10, 25), SettingsRepo.cleanPresets(emptyList()))
        assertEquals(listOf(1, 3, 5, 10, 25), SettingsRepo.cleanPresets(listOf(0, 100)))
        assertEquals(8, SettingsRepo.cleanPresets((1..20).toList()).size)
    }

    @Test
    fun unknownSandAlertFallsBackToVibrate() {
        assertEquals("vibrate", SettingsRepo.parseSandAlert("loud"))
        assertEquals("vibrate", SettingsRepo.parseSandAlert(null))
        assertEquals("glyph", SettingsRepo.parseSandAlert("glyph"))
        assertEquals("chime", SettingsRepo.parseSandAlert("chime"))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.data.SettingsRepoTest'`
Expected: compilation FAIL with `No parameter with name 'sandPresets'`.

- [ ] **Step 3: Implement**

In `Settings.kt`, add these after `val petNames: Map<String, String> = emptyMap(),`:

```kotlin
    val sandPresets: List<Int> = listOf(1, 3, 5, 10, 25),
    val sandAlert: String = "vibrate",
    val sandExact: Boolean = false,
    val sandTimer: String = "",
    val sandToyEverBound: Boolean = false,
```

In `SettingsRepo.kt`:
- Add these keys after `PET_NAMES`:

```kotlin
        private val SAND_PRESETS = stringPreferencesKey("sand_presets")
        private val SAND_ALERT = stringPreferencesKey("sand_alert")
        private val SAND_EXACT = booleanPreferencesKey("sand_exact")
        private val SAND_TIMER = stringPreferencesKey("sand_timer")
        private val SAND_BOUND = booleanPreferencesKey("sand_toy_ever_bound")
```

- Add these helpers after `clampTarget`:

```kotlin
        /** 1..99, no duplicates, sorted, at most 8; empty → the defaults. */
        fun cleanPresets(list: List<Int>): List<Int> =
            list.filter { it in 1..99 }.distinct().sorted().take(8).ifEmpty { listOf(1, 3, 5, 10, 25) }

        fun parseSandAlert(s: String?): String = if (s in setOf("glyph", "vibrate", "chime")) s!! else "vibrate"
```

- In `toSettings()`, add after `petNames = ...,`:

```kotlin
                sandPresets = cleanPresets(runCatching { namesJson.decodeFromString<List<Int>>(this[SAND_PRESETS] ?: "") }.getOrDefault(d.sandPresets)),
                sandAlert = parseSandAlert(this[SAND_ALERT]),
                sandExact = this[SAND_EXACT] ?: d.sandExact,
                sandTimer = this[SAND_TIMER] ?: d.sandTimer,
                sandToyEverBound = this[SAND_BOUND] ?: d.sandToyEverBound,
```

- In `write()`, add after `this[PET_NAMES] = ...`:

```kotlin
            this[SAND_PRESETS] = namesJson.encodeToString(s.sandPresets)
            this[SAND_ALERT] = s.sandAlert
            this[SAND_EXACT] = s.sandExact
            this[SAND_TIMER] = s.sandTimer
            this[SAND_BOUND] = s.sandToyEverBound
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.data.SettingsRepoTest'`
Expected: PASS. An empty `SAND_PRESETS` string fails to decode, which falls back to the defaults, so `defaultsWhenEmpty` still matches `Settings()`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/data app/src/test/java/app/backlit/data
git commit -m "feat(sand): timer settings (presets, done alert, exact, saved state)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Done alarm, vibration and chime

**Files:**
- Create: `app/src/main/java/app/backlit/sand/SandAlarm.kt`
- Create: `app/src/main/java/app/backlit/sand/SandAlarmReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `TimerState.alarmFired/encode/decode` (Task 3), `SettingsRepo`, `AlertsRuntime.preview`, `SandPreviewAnimation.DONE_ID`.
- Produces:
  - `SandAlarm.sync(context, st: TimerState, exact: Boolean)`: cancels, then schedules when RUNNING
  - `SandAlarm.cancel(context)`
  - `SandAlarm.ring(context, alert: String)`
  - `SandAlarm.liveLoop`: a `@Volatile var Boolean`, set by the toy while its ACTIVE loop runs
  - `SandAlarm.finishFromAlarm(context, rescheduleIfNotDue: Boolean)`: a suspend function, shared by the receiver paths (alarm: `false`; boot or update: `true`)

The pure logic (`alarmFired`) is tested in Task 3. This task is Android glue: it is verified by building now and on the device in Task 9.

- [ ] **Step 1: Write `SandAlarm`**

```kotlin
// app/src/main/java/app/backlit/sand/SandAlarm.kt
package app.backlit.sand

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.data.SettingsRepo
import kotlinx.coroutines.flow.first

/** Schedules the hourglass's done moment so it fires even when the toy isn't on screen. */
object SandAlarm {
    const val ACTION_DONE = "app.backlit.sand.DONE"
    private const val TAG = "BacklitSand"
    private val PATTERN = longArrayOf(0, 400, 200, 400, 200, 600)

    /** True while the toy's ACTIVE render loop runs: it then handles "done" itself. */
    @Volatile var liveLoop = false

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, SandAlarmReceiver::class.java).setAction(ACTION_DONE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun cancel(context: Context) {
        runCatching { context.getSystemService(AlarmManager::class.java).cancel(pending(context)) }
    }

    fun sync(context: Context, st: TimerState, exact: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        am.cancel(pi)
        if (st.phase != Phase.RUNNING) return
        runCatching {
            if (exact && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, st.endAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, st.endAt, pi)
        }.onFailure { Log.w(TAG, "schedule failed; falling back to inexact", it); am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, st.endAt, pi) }
    }

    /** "glyph": nothing extra; "vibrate": the pattern; "chime": the pattern plus the notification sound when the ringer is on. */
    fun ring(context: Context, alert: String) {
        if (alert == "glyph") return
        runCatching {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                ?.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
        }.onFailure { Log.w(TAG, "vibrate failed", it) }
        if (alert != "chime") return
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching { RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play() }
            .onFailure { Log.w(TAG, "chime failed", it) }
    }

    /** Alarm (or boot) path: finish a due RUNNING timer, ring, and play "Flip me" on the matrix if no toy is showing. */
    suspend fun finishFromAlarm(context: Context, rescheduleIfNotDue: Boolean) {
        val app = context.applicationContext
        val repo = SettingsRepo.get(app)
        val s = repo.settings.first()
        val st = TimerState.decode(s.sandTimer)
        val done = st.alarmFired(System.currentTimeMillis())
        if (done == null) {
            if (rescheduleIfNotDue) sync(app, st, s.sandExact)
            return
        }
        if (liveLoop) return
        repo.update { it.copy(sandTimer = done.encode()) }
        ring(app, s.sandAlert)
        AlertsRuntime.get(app).preview(SandPreviewAnimation.DONE_ID, TimerState.FLIP_ME_MS + 700)
    }
}
```

- [ ] **Step 2: Write the receiver**

```kotlin
// app/src/main/java/app/backlit/sand/SandAlarmReceiver.kt
package app.backlit.sand

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The done alarm, plus re-arming after a reboot or an app update. */
class SandAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                SandAlarm.finishFromAlarm(context, rescheduleIfNotDue = intent.action != SandAlarm.ACTION_DONE)
            } catch (e: Exception) {
                Log.e("BacklitSand", "alarm handling failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
```

- [ ] **Step 3: Update the manifest**

Add these after the `BLUETOOTH_CONNECT` permission:

```xml
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Add this before the `.alerts.BluetoothAlertReceiver` receiver:

```xml
        <receiver
            android:name=".sand.SandAlarmReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 4: Build and run all the tests**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon :app:assembleDebug && .superpowers/runtests.sh`
Expected: `BUILD SUCCESSFUL`, and the summary line shows `0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/sand/SandAlarm.kt app/src/main/java/app/backlit/sand/SandAlarmReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat(sand): done alarm with vibration/chime, re-armed after reboot

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The Backlit Sand toy

**Files:**
- Create: `app/src/main/java/app/backlit/glyph/SandToyService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create (generated): `app/src/main/res/drawable/ic_sand_preview.xml`
- Modify: `app/src/test/java/app/backlit/render/ToyPreviewsTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 1–6. It also uses `DeviceProfile`, `ModeTracker`, `GlyphOutput`, `FramePacer`, `FrameEncoder`, `ToyPresence` and `AlertsRuntime`.
- Produces: the toy service, registered in the manifest.

- [ ] **Step 1: Add the failing golden test for the picker image**

In `ToyPreviewsTest`, add the imports `app.backlit.sand.HourglassShape`, `app.backlit.sand.Phase`, `app.backlit.sand.SandArt` and `app.backlit.sand.TimerState`. Then add this entry to `previews`:

```kotlin
        "ic_sand_preview" to SandArt.still(HourglassShape.forSize(25), TimerState(phase = Phase.RUNNING, durationMs = 100_000, endAt = 60_000), 0),
```

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.ToyPreviewsTest'`
Expected: FAIL with `FileNotFoundException` for `ic_sand_preview.xml`.

Generate it: `source .superpowers/env.sh && UPDATE_PREVIEWS=1 ./gradlew --no-daemon :app:testDebugUnitTest --tests 'app.backlit.render.ToyPreviewsTest'`
Then rerun the plain test. Expected: PASS.

- [ ] **Step 2: Add the strings**

In `res/values/strings.xml`, add these after `pet_toy_summary`:

```xml
    <string name="sand_toy_name">Backlit Sand</string>
    <string name="sand_toy_summary">A real hourglass on your Glyph. Flip to start, lay it on its side to pause, long press to pick a time.</string>
```

- [ ] **Step 3: Register the service in the manifest**

Add this after the `PetToyService` service block:

```xml
        <service
            android:name=".glyph.SandToyService"
            android:exported="true"
            tools:ignore="ExportedService">
            <intent-filter>
                <action android:name="com.nothing.glyph.TOY" />
            </intent-filter>
            <meta-data
                android:name="com.nothing.glyph.toy.name"
                android:resource="@string/sand_toy_name" />
            <meta-data
                android:name="com.nothing.glyph.toy.image"
                android:resource="@drawable/ic_sand_preview" />
            <meta-data
                android:name="com.nothing.glyph.toy.summary"
                android:resource="@string/sand_toy_summary" />
            <meta-data
                android:name="com.nothing.glyph.toy.longpress"
                android:value="1" />
            <meta-data
                android:name="com.nothing.glyph.toy.aod_support"
                android:value="1" />
        </service>
```

- [ ] **Step 4: Write the service** (modelled on `PetToyService`)

```kotlin
// app/src/main/java/app/backlit/glyph/SandToyService.kt
package app.backlit.glyph

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.ToyPresence
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import app.backlit.sand.HourglassShape
import app.backlit.sand.Orientation
import app.backlit.sand.Phase
import app.backlit.sand.SandAlarm
import app.backlit.sand.SandArt
import app.backlit.sand.SandLayout
import app.backlit.sand.SandSim
import app.backlit.sand.TimerState
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** The hourglass toy: live tilt sand while ACTIVE, a still each minute in AOD. The timer (TimerState) is the truth. */
class SandToyService : Service() {

    private var scope: CoroutineScope? = null
    private var renderJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private lateinit var shape: HourglassShape
    private lateinit var sim: SandSim
    private var settings = Settings()
    private var alerts: AlertsRuntime? = null
    private var loaded = false
    private var state = TimerState()
    private val myWrites = ArrayDeque<String>()

    private var sensors: SensorManager? = null
    private var sensorsOn = false
    private var oneShot = false
    private val grav = FloatArray(2)
    private var haveReading = false
    private var needBaseline = true
    private var needRebuild = true
    private var dirX = 0.0
    private var dirY = 1.0
    private var lastRateAt = 0L
    private var handledRefill = 0L

    private fun now() = System.currentTimeMillis()

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> if (loaded) { commit(state.longPress(now(), settings.sandPresets)); kick() }
                GlyphToy.EVENT_AOD -> {
                    modes.onAodEvent(now())
                    if (profile.aodOnly) sampleOnce()
                    kick()
                }
            }
        }
    }
    private val messenger = Messenger(handler)
    private val rekick = Runnable { kick() }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // Matrix coordinates: the matrix is on the back, so device +x reads as matrix gx = accelX; +y down the matrix = accelY.
            val gx = X_SIGN * e.values[0]
            val gy = e.values[1]
            if (!haveReading || oneShot) { grav[0] = gx; grav[1] = gy } else {
                grav[0] = 0.8f * grav[0] + 0.2f * gx
                grav[1] = 0.8f * grav[1] + 0.2f * gy
            }
            haveReading = true
            val m = hypot(grav[0], grav[1])
            if (m >= 3f) { dirX = grav[0] / m.toDouble(); dirY = grav[1] / m.toDouble() }
            if (oneShot) {
                oneShot = false
                setSensors(false)
                applyOrientation(now())
                kick()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        if (profile == DeviceProfile.UNSUPPORTED) return messenger.binder

        shape = HourglassShape.forSize(profile.size)
        sim = SandSim(shape)
        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "sand toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        ToyPresence.enter()
        val rt = AlertsRuntime.get(this).also { alerts = it }
        rt.toyChanged()
        sensors = getSystemService(SensorManager::class.java)
        s.launch {
            settings = repo.settings.first()
            state = TimerState.decode(settings.sandTimer).tick(now())
            dirY = state.upSide.toDouble()
            handledRefill = state.refillUntil
            loaded = true
            output = GlyphOutput(this@SandToyService, profile) { kick() }.also { it.connect() }
            repo.update { if (it.sandToyEverBound) it else it.copy(sandToyEverBound = true) }
            launch { repo.settings.collect { onSettings(it) } }
            launch { rt.bus.collect { kick() } }
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(rekick)
        setSensors(false)
        SandAlarm.liveLoop = false
        if (loaded) persist()
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

    /** Settings changed (ours echoing back, or the app / alarm changed the timer). */
    private fun onSettings(s: Settings) {
        settings = s
        if (s.sandTimer !in myWrites) {
            val incoming = TimerState.decode(s.sandTimer)
            if (incoming.persisted() != state.persisted()) {
                state = incoming.copy(upSide = state.upSide.takeIf { incoming.phase == Phase.READY } ?: incoming.upSide)
                needRebuild = true
                lastRateAt = 0L
            }
        }
        kick()
    }

    private fun isAod() = modes.mode(now()) == Mode.AOD

    private fun setSensors(on: Boolean) {
        val sm = sensors ?: return
        if (on == sensorsOn) return
        sensorsOn = on
        if (on) {
            if (!oneShot) needBaseline = true   // 4a Pro samples keep counting flips; only a fresh live session re-baselines
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        } else sm.unregisterListener(sensorListener)
    }

    /** 4a Pro (AOD only): one accelerometer sample per AOD tick so flips still count. */
    private fun sampleOnce() {
        if (sensorsOn) return
        oneShot = true
        setSensors(true)
    }

    private fun applyOrientation(t: Long) {
        if (!haveReading) return
        val o = Orientation.of(grav[0], grav[1])
        val next = if (needBaseline) { needBaseline = false; state.baseline(o, t) } else state.onOrientation(o, t)
        commit(next)
    }

    private fun commit(next: TimerState) {
        val prev = state
        if (next == prev) return
        state = next
        if (next.phase != prev.phase) lastRateAt = 0L
        if (next.phase == Phase.DONE && prev.phase != Phase.DONE) {
            SandAlarm.cancel(this)
            SandAlarm.ring(this, settings.sandAlert)
        }
        if (next.persisted() != prev.persisted()) persist()
    }

    private fun persist() {
        val json = state.encode()
        myWrites.addLast(json)
        while (myWrites.size > 8) myWrites.removeFirst()
        SandAlarm.sync(this, state, settings.sandExact)
        val sc = scope
        if (sc != null) sc.launch { repo.update { it.copy(sandTimer = json) } }
        else CoroutineScope(Dispatchers.IO).launch { repo.update { it.copy(sandTimer = json) } }
    }

    /** Steer and step the sand; rebuild it from the clock after any gap (bind, AOD, alert, refill). */
    private fun stepSim(t: Long) {
        val st = state
        if (t < st.numberUntil || t < st.refillUntil) return
        if (needRebuild || handledRefill != st.refillUntil) {
            handledRefill = st.refillUntil
            needRebuild = false
            sim.load(SandLayout.layout(shape, st.fractionUp(t), false, st.upSide))
        }
        if (st.phase != Phase.RUNNING) sim.gateRate = 0.0
        else if (t - lastRateAt >= 1000) {
            lastRateAt = t
            sim.gateRate = SandSim.gateRateFor(shape.total, st.durationMs, sim.countOn(st.upSide), st.timeLeft(t))
        }
        sim.step(dirX, dirY, FRAME_MS.toDouble())
    }

    private fun kick() {
        val s = scope ?: return
        if (!loaded || renderJob?.isActive == true) return
        handler.removeCallbacks(rekick)
        renderJob = s.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val t = now()
                val aod = isAod()
                if (!profile.aodOnly) setSensors(!aod)
                SandAlarm.liveLoop = !aod
                commit(state.tick(t))
                if (!aod) applyOrientation(t)
                val alert = alerts?.bus?.value
                if (alert != null || aod) needRebuild = true
                val grid = runCatching {
                    when {
                        alert != null -> alerts!!.animationFor(alert).frame(profile.size, AlertsRuntime.now() - alert.startedAt)
                        aod -> SandArt.still(shape, state, t)
                        else -> { stepSim(t); SandArt.frame(shape, sim.grid, sim.moved, state, t) }
                    }
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = alert == null && aod))
                if (aod && alert == null) {
                    SandAlarm.liveLoop = false
                    modes.msUntilActive(t)?.let { handler.postDelayed(rekick, it + 100) }
                    if (t < state.numberUntil) handler.postDelayed(rekick, state.numberUntil - t + 50)
                    break
                }
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    private companion object {
        const val TAG = "BacklitSand"
        const val FRAME_MS = 50L
        /** Flip to -1f if tilting the phone pours the sand the wrong way on the device (checked in Task 9). */
        const val X_SIGN = 1f
    }
}
```

`onSettings` keeps the toy's own READY orientation, because the app's `select()` copies `upSide` from a possibly stale state. `TimerState.persisted()` is used for the comparison, because `encode()` drops the transient fields.

- [ ] **Step 5: Build and run all the tests**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon :app:assembleDebug && .superpowers/runtests.sh`
Expected: `BUILD SUCCESSFUL`, and the summary line shows `0 failures, 0 errors`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/backlit/glyph/SandToyService.kt app/src/main/AndroidManifest.xml app/src/main/res app/src/test/java/app/backlit/render/ToyPreviewsTest.kt
git commit -m "feat(sand): Backlit Sand Glyph toy

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: TIMER tab

**Files:**
- Create: `app/src/main/java/app/backlit/ui/SandScreen.kt`
- Modify: `app/src/main/java/app/backlit/ui/HomeScreen.kt`

**Interfaces:**
- Consumes:
  - `SandArt.still`, `HourglassShape`, `TimerState` (`decode`, `tick`, `select`, `encode`, `clock`) and `SandPreviewAnimation.RUNNING_ID`
  - `SandAlarm.sync`, `SettingsRepo.cleanPresets`
  - `MatrixPreview`, `SquareChip`, `SettingRow` and `Notice` from the existing UI
- Produces: `SandTab(settings, profile, onUpdate)`.

- [ ] **Step 1: Write the screen**

```kotlin
// app/src/main/java/app/backlit/ui/SandScreen.kt
package app.backlit.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
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
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.glyph.DeviceProfile
import app.backlit.sand.HourglassShape
import app.backlit.sand.Phase
import app.backlit.sand.SandAlarm
import app.backlit.sand.SandArt
import app.backlit.sand.SandPreviewAnimation
import app.backlit.sand.TimerState
import kotlinx.coroutines.delay

@Composable
fun SandTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val shape = HourglassShape.forSize(if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(200); now = System.currentTimeMillis() } }
    val st = TimerState.decode(settings.sandTimer).tick(now)
    val presets = settings.sandPresets
    val busy = st.phase == Phase.RUNNING || st.phase == Phase.PAUSED

    if (profile != DeviceProfile.UNSUPPORTED && !settings.sandToyEverBound) {
        Notice("Turn on Backlit Sand in Glyph Toys (Settings → Glyph Interface → Glyph Toys), then flip your phone over to start.")
    }

    MatrixPreview(SandArt.still(shape, st, now), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
    Text(
        when (st.phase) {
            Phase.READY -> "READY · ${st.durationMs / TimerState.MIN} MIN · FLIP TO START"
            Phase.RUNNING -> "RUNNING · ${TimerState.clock(st.timeLeft(now))} LEFT"
            Phase.PAUSED -> "PAUSED ON ITS SIDE · ${TimerState.clock(st.timeLeft(now))} LEFT"
            Phase.DONE -> "TIME'S UP · FLIP TO GO AGAIN"
        },
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )

    var editing by remember { mutableStateOf(false) }
    Text("TIMES", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEachIndexed { i, m ->
            val selected = !editing && !busy && i == st.presetIndex.coerceIn(0, presets.size - 1) && st.durationMs == m * TimerState.MIN
            SquareChip(if (editing) "$m ×" else "$m MIN", selected, {
                if (editing) {
                    if (presets.size > 1) onUpdate { it.copy(sandPresets = SettingsRepo.cleanPresets(it.sandPresets - m)) }
                } else if (!busy) {
                    onUpdate { it.copy(sandTimer = st.select(i, presets).encode()) }
                }
            })
        }
    }
    Text(
        if (editing) "Tap a time to remove it." else if (busy) "A timer is running. Long press the Glyph twice to change it." else "Tap a time to pick it, or long press the Glyph button.",
        style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(top = 4.dp),
    )
    SettingRow("Edit times", if (editing) "DONE" else "→") { editing = !editing }
    if (editing) {
        var add by remember { mutableIntStateOf(15) }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ADD", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.weight(1f))
            SquareChip("−", false, { add = (add - 1).coerceAtLeast(1) })
            Text("$add MIN", style = MaterialTheme.typography.titleMedium)
            SquareChip("+", false, { add = (add + 1).coerceAtMost(99) })
            SquareChip("ADD", presets.size < 8 && add !in presets, {
                if (presets.size < 8) onUpdate { it.copy(sandPresets = SettingsRepo.cleanPresets(it.sandPresets + add)) }
            })
        }
    }

    Text("WHEN TIME'S UP", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("glyph" to "GLYPH ONLY", "vibrate" to "VIBRATE", "chime" to "+ CHIME").forEach { (id, label) ->
            SquareChip(label, settings.sandAlert == id, { onUpdate { it.copy(sandAlert = id) } }, Modifier.weight(1f))
        }
    }

    val canExact = context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    val exactOn = settings.sandExact && canExact
    SettingRow("Ring exactly on time", if (exactOn) "ON" else "OFF") {
        if (exactOn) {
            onUpdate { it.copy(sandExact = false) }
            SandAlarm.sync(context, st, false)
        } else {
            onUpdate { it.copy(sandExact = true) }
            if (canExact) SandAlarm.sync(context, st, true)
            else context.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    Text(
        "Off: when the phone is asleep, Android may ring up to about a minute late. On: Android asks you once to allow alarms.",
        style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim,
    )

    var howOpen by remember { mutableStateOf(false) }
    SettingRow("How it works", if (howOpen) "−" else "+") { howOpen = !howOpen }
    if (howOpen) {
        Text(
            listOf(
                "⟲ Flip the phone over to start, like a real hourglass",
                "⟲ Flip it mid-way and the sand runs back: time left becomes time run",
                "↔ Lay it on its side to pause",
                "▭ Face-down on a desk keeps it running",
                "● Long press: pick a time. While it runs, the first press shows the minutes left; press again within 2 s to change it",
            ).joinToString("\n"),
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }

    SquareChip("SHOW ON GLYPH", true, { runtime.preview(SandPreviewAnimation.RUNNING_ID, 4000L) }, Modifier.fillMaxWidth().padding(vertical = 12.dp))
    Spacer(Modifier.height(8.dp))
}
```

- [ ] **Step 2: Add the tab to `HomeScreen`**

In `HomeScreen.kt`, change the tab list to `listOf("CLOCK", "MUSIC", "ALERTS", "CHARGE", "STUDIO", "PET", "TIMER")`. Then change the `when (tab)` tail from `else -> PetTab(settings, profile, onUpdate)` to:

```kotlin
            5 -> PetTab(settings, profile, onUpdate)
            else -> SandTab(settings, profile, onUpdate)
```

- [ ] **Step 3: Build and run all the tests**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon :app:assembleDebug && .superpowers/runtests.sh`
Expected: `BUILD SUCCESSFUL`, and `0 failures, 0 errors`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/backlit/ui/SandScreen.kt app/src/main/java/app/backlit/ui/HomeScreen.kt
git commit -m "feat(sand): TIMER tab with presets, done alert and exact switch

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Docs and on-device verification

**Files:**
- Modify: `README.md` (Features, Permissions and privacy, Project structure, Roadmap)
- Modify: `docs/testing/device-checklist.md`

- [ ] **Step 1: Add the README section** after the Pet section under `## Features`

```markdown
### Backlit Sand (Glyph Toy)
A real hourglass. Live sand pours as you tilt the phone (Phone (3)).
- **Flip** the phone over to start. Flip it mid-way and the sand runs back, so the time left becomes the time run.
- **On its side** pauses it, and **face-down** on a desk keeps it running.
- **Long press** the Glyph button to pick 1, 3, 5, 10 or 25 minutes (editable in the TIMER tab). While it runs, the first press shows the minutes left.
- **Time's up:** the hourglass spins itself over ("Flip me"), plus a vibration (or Glyph only, or with a chime). This works even when another toy is showing or the screen is off.
- **Always-on:** a still that updates every minute. On the 4a Pro, flips are picked up within a minute.
```

Under "Permissions and privacy", add one line each:
- `VIBRATE`: the done buzz.
- `SCHEDULE_EXACT_ALARM`: only if you turn on "Ring exactly on time".
- `RECEIVE_BOOT_COMPLETED`: keeps a running timer's alarm after a restart.

Add `sand/` to the Project structure list as "hourglass shape, sand physics, timer state, art, done alarm". In the Roadmap, mark the sand timer as done.

- [ ] **Step 2: Add the device checklist section** to `docs/testing/device-checklist.md`

```markdown
## Backlit Sand
- [ ] Glyph Toys picker shows "Backlit Sand" with the hourglass image; selecting it shows sand in the lower bulb, glass breathing
- [ ] Tilt left/right: sand pours towards the lower side (if mirrored, flip X_SIGN in SandToyService)
- [ ] Flip the phone over: timer starts, thin stream through the neck
- [ ] Flip mid-way: sand runs back; TIMER tab shows time left = time run
- [ ] Lay on its side ~1 s: pauses (TIMER tab says PAUSED); stand back up the same way: resumes
- [ ] Face-down on the desk: keeps running
- [ ] Long press when ready: number shows, lower bulb refills, next preset
- [ ] Long press while running: minutes left shows, timer untouched; second press within 2 s: next preset, reset
- [ ] Time's up with the toy showing: Flip me spin + blink + vibration; settles with sand at the bottom
- [ ] Time's up after swiping to another Backlit toy: vibration + Flip me plays there
- [ ] Time's up with the screen off: vibration (+ chime if chosen and ringer on)
- [ ] Glyph only / Vibrate / + Chime each behave as named
- [ ] "Ring exactly on time": opens Alarms & reminders when not allowed; shows ON after allowing
- [ ] Reboot mid-timer: alarm still fires (or fires right after boot if the time has passed)
- [ ] Always-on still updates each minute with the right level
- [ ] 4a Pro: AOD still; flipping is picked up within a minute
```

- [ ] **Step 3: Run the full suite and build**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon :app:assembleDebug && .superpowers/runtests.sh`
Expected: `BUILD SUCCESSFUL`, and `0 failures, 0 errors`.

- [ ] **Step 4: Install on the connected Phone (3)**

Run: `source .superpowers/env.sh && adb devices && ./gradlew --no-daemon :app:installDebug`
Expected: the device is listed and `Installed on 1 device`. Then ask the user to work through the "Backlit Sand" checklist. If the sand pours the wrong way, change `X_SIGN` to `-1f`, then rebuild and reinstall.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/testing/device-checklist.md
git commit -m "docs: Backlit Sand README and device checklist

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
