# Backlit Multiple Pets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user choose one of six pets in the PET tab: Ghost (existing), Frog, Penguin, Axolotl, Owl or Robot. The pets share one mood and behaviour, and each has its own art, name and signature move.

**Architecture:**
- **Pure `pet/`:**
  - `PetKind` (ids and default names)
  - `RigArt` (the shared expression system, ported line for line from the approved mockup)
  - one `Rig` object per new pet
  - `PetArt`, which dispatches GHOST to the unchanged `GhostArt` and everything else to `RigArt`
- `PetPreviewAnimation` gains a kind.
- **Settings:** `petKind`, plus per-pet names (the ghost keeps `petName`).
- **`PetToyService`:** draws through `PetArt`.
- **PET tab:** gets a chooser row.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, kotlinx-serialization-json, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-06-backlit-multi-pets-design.md`. The mockup, which is the visual source of truth, is `docs/superpowers/mockups/2026-10-06-pets-full.html`.

## Global Constraints

- **Branch and tooling:** branch `feat/multi-pets`. In every fresh shell, run `source .superpowers/env.sh`. Run tests with `.superpowers/runtests.sh [--tests 'pattern']`.
- **Imports:** `pet/` imports no `android.*`, `androidx.*` or `com.nothing.*`.
- **Drawing helpers:**
  - Brightness constants: `O = 0.9`, `F = 0.22`, `L = 0.5`.
  - Mockup `g.s(x, y, b)` becomes `ChargeKit.set` (round, overwrite, mask-safe).
  - Mockup `g.p` becomes `ChargeKit.dot` (round, lighten, ignores b ≤ 0).
  - `Math.round` becomes `.px()`.
- **The ghost must not change:** `GhostArt` stays byte-for-byte. `PetArt` delegates GHOST to it, and the existing `ToyPreviewsTest` golden must stay green.
- **Pet kinds and default names:** ghost "Boo", frog "Ribbit", penguin "Waddles", axolotl "Lotl", owl "Hoot", robot "Bolt". An unknown id means ghost.
- **Signature move:** `Reaction.BOO` (2600 ms) is each pet's signature move. The penguin slide is retimed to 2600 ms.
- **Preview ids:**
  - Ghost: `pet:<state>`, unchanged and legacy.
  - Other pets: `pet:<kind>:<state>`.
- **Settings:**
  - `petKind = "ghost"`.
  - `petNames: Map<String, String> = emptyMap()`, stored as JSON under `pet_names`. It holds non-ghost names only.
  - The ghost's name stays in `petName`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Existing users after upgrading** must see the ghost named Boo (or their custom name) with their mood unchanged. This is pinned by `SettingsRepoTest.petKindDefaultsToGhostAndKeepsGhostName` (Task 4).
2. **The ghost must render identically** through the new dispatch. This is pinned by `PetArtTest.ghostDelegatesUnchanged` (Task 3).
3. **Every pet × state × size** must stay inside the round mask and never crash. This includes the off-screen parts of the penguin slide and the peekaboo rise. This is pinned by `PetArtTest.everyPetEveryStateRendersInsideTheMask` (Task 3).
4. **Renaming one pet** must not rename another, and a blank field must keep that pet's previous name. This is pinned by `SettingsRepoTest.namesArePerPet` (Task 4).
5. **The AOD charging fill** must work for every new pet's body. This is pinned by `PetArtTest.aodFillGrowsForEveryPet` (Task 3).

---

## File Structure

```
app/src/main/java/app/backlit/pet/
  PetKind.kt          enum + byId
  RigArt.kt           Rig interface, specs, BodyOpts/FaceOpts, shared parts + moods/reactions/AOD
  rigs/FrogRig.kt  rigs/PenguinRig.kt  rigs/AxolotlRig.kt  rigs/OwlRig.kt  rigs/RobotRig.kt
  PetArt.kt           frame/still dispatch by kind
  PetPreviewAnimation.kt   (+ kind)
app/src/main/java/app/backlit/data/Settings.kt, SettingsRepo.kt   (+ petKind, petNames, helpers)
app/src/main/java/app/backlit/glyph/PetToyService.kt              (PetArt + kind)
app/src/main/java/app/backlit/ui/PetScreen.kt                     (chooser row, per-pet names)
```

---

### Task 1: PetKind and the shared expression system (RigArt)

**Files:**
- Create: `app/src/main/java/app/backlit/pet/PetKind.kt`, `app/src/main/java/app/backlit/pet/RigArt.kt`
- Test: `app/src/test/java/app/backlit/pet/PetKindTest.kt`

**Interfaces:**
- Consumes: `Base`, `Reaction`, `Pose` (existing), `ChargeKit.set/dot/circlePoints` and `px()` (existing).
- Produces:
  - `enum class PetKind(val id: String, val defaultName: String)` with `companion fun byId(id: String): PetKind`
  - `enum class EyeMode { HOLE, BRIGHT }`
  - `data class EyeSpec(cx: List<Int>, cy: Int, w: Int, h: Int, mode: EyeMode)`
  - `data class MouthSpec(x: Int, y: Int, scale: Int = 1)`
  - `enum class Gill { FAST, NORMAL, SLOW, STILL, DROOP }`
  - `data class BodyOpts(dx, dy, puff, b, t, gill, flap, tip, footLift)`
  - `data class FaceOpts(dx, dy, lx, ly, t)`
  - `interface Rig`:
    - specs: `eyes25`, `eyes13`, `mouth25`, `mouth13`, `cheeks25`, `cheeks13`, `top25`
    - required: `body(g, o)`, `signature(g, t)`
    - optional: `mouth(g, kind, o): Boolean` and `munch(g, t, o): Boolean`, both defaulting to false
  - `object RigArt`:
    - `const val O/F/L`
    - drawing helpers: `blob`, `line`, `bm`, `pts`
    - face parts: `eyes`, `mouth`, `cheeks`
    - `fun frame(rig: Rig, size: Int, pose: Pose, now: Long): PixelGrid`
    - `fun still(rig: Rig, size: Int, pose: Pose, minuteOfHour: Int): PixelGrid`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.pet

import org.junit.Assert.assertEquals
import org.junit.Test

class PetKindTest {
    @Test
    fun idsDefaultsAndFallback() {
        assertEquals(listOf("ghost", "frog", "penguin", "axolotl", "owl", "robot"), PetKind.entries.map { it.id })
        assertEquals(listOf("Boo", "Ribbit", "Waddles", "Lotl", "Hoot", "Bolt"), PetKind.entries.map { it.defaultName })
        assertEquals(PetKind.OWL, PetKind.byId("owl"))
        assertEquals(PetKind.GHOST, PetKind.byId("cat"))
        assertEquals(PetKind.GHOST, PetKind.byId(""))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.pet.PetKindTest'`
Expected: compilation FAIL, because `PetKind` is unresolved.

- [ ] **Step 3: Write `PetKind.kt` and `RigArt.kt`**

`PetKind.kt`:

```kotlin
package app.backlit.pet

enum class PetKind(val id: String, val defaultName: String) {
    GHOST("ghost", "Boo"),
    FROG("frog", "Ribbit"),
    PENGUIN("penguin", "Waddles"),
    AXOLOTL("axolotl", "Lotl"),
    OWL("owl", "Hoot"),
    ROBOT("robot", "Bolt");

    companion object {
        fun byId(id: String): PetKind = entries.firstOrNull { it.id == id } ?: GHOST
    }
}
```

`RigArt.kt` is a line-for-line port of the mockup's helpers, `eyes`/`mouth`/`cheeks`/`hearts`/`zz`/`steam` and `STATES`:

```kotlin
package app.backlit.pet

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

enum class EyeMode { HOLE, BRIGHT }
data class EyeSpec(val cx: List<Int>, val cy: Int, val w: Int, val h: Int, val mode: EyeMode)
data class MouthSpec(val x: Int, val y: Int, val scale: Int = 1)
enum class Gill { FAST, NORMAL, SLOW, STILL, DROOP }

data class BodyOpts(
    val dx: Int = 0, val dy: Int = 0, val puff: Boolean = false, val b: Double = RigArt.O, val t: Long = 0,
    val gill: Gill = Gill.NORMAL, val flap: Boolean = false, val tip: Double = 0.6, val footLift: Int = 0,
)

data class FaceOpts(val dx: Int = 0, val dy: Int = 0, val lx: Int = 0, val ly: Int = 0, val t: Long = 0)

/** A pet's body and face anchors. Moods and reactions are drawn by [RigArt] using these. */
interface Rig {
    val eyes25: EyeSpec
    val eyes13: EyeSpec
    val mouth25: MouthSpec
    val mouth13: MouthSpec
    val cheeks25: List<Pair<Int, Int>>
    val cheeks13: List<Pair<Int, Int>>
    val top25: Int
    fun body(g: PixelGrid, o: BodyOpts)
    /** Return true if this rig drew the mouth itself (beaks). */
    fun mouth(g: PixelGrid, kind: String, o: FaceOpts): Boolean = false
    fun signature(g: PixelGrid, t: Long)
    /** Return true if this rig drew its own munch. */
    fun munch(g: PixelGrid, t: Long, o: FaceOpts): Boolean = false
}

/** Shared expression system for rig pets, ported from docs/superpowers/mockups/2026-10-06-pets-full.html. */
object RigArt {
    const val O = 0.9
    const val F = 0.22
    const val L = 0.5
    private val HEART = listOf("101", "111", "010")
    private val Z = listOf("111", "010", "111")
    private val BOLT = listOf("01", "11", "10")
    private val LOOKS = listOf(0 to 0, 1 to 0, 0 to 1, -1 to 0)
    private val MOUTH = mapOf(
        "smile" to listOf(-1 to 0, 0 to 1, 1 to 0), "wide" to listOf(-2 to 0, -1 to 1, 0 to 1, 1 to 1, 2 to 0),
        "flat" to listOf(-1 to 0, 0 to 0, 1 to 0), "frown" to listOf(-1 to 1, 0 to 0, 1 to 1),
        "O" to listOf(0 to -1, -1 to 0, 1 to 0, 0 to 1), "zig" to listOf(-2 to 1, -1 to 0, 0 to 1, 1 to 0, 2 to 1),
        "chomp" to listOf(-1 to 0, 0 to 0, 1 to 0, -1 to 1, 1 to 1), "small" to listOf(0 to 0),
    )

    // ── drawing helpers ──

    fun blob(g: PixelGrid, cx: Double, cy: Double, a: Double, b: Double, fill: Double?, edge: Double?) {
        fun inE(x: Int, y: Int) = ((x - cx) / a).pow(2) + ((y - cy) / b).pow(2) <= 1
        for (y in 0 until g.size) for (x in 0 until g.size) {
            if (!inE(x, y)) continue
            val e = !inE(x + 1, y) || !inE(x - 1, y) || !inE(x, y + 1) || !inE(x, y - 1)
            if (e) { if (edge != null) g.set(x.toDouble(), y.toDouble(), edge) } else if (fill != null) g.set(x.toDouble(), y.toDouble(), fill)
        }
    }

    fun line(g: PixelGrid, x0: Double, y0: Double, x1: Double, y1: Double, v: Double) {
        val st = max(1, ceil(hypot(x1 - x0, y1 - y0) * 2).toInt())
        for (k in 0..st) { val f = k.toDouble() / st; g.set(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, v) }
    }

    fun bm(g: PixelGrid, rows: List<String>, x0: Int, y0: Int, map: Map<Char, Double>) =
        rows.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> map[ch]?.let { g.set((x0 + i).toDouble(), (y0 + j).toDouble(), it) } } }

    fun pts(g: PixelGrid, list: List<Pair<Int, Int>>, dx: Int, dy: Int, v: Double) =
        list.forEach { (x, y) -> g.set((x + dx).toDouble(), (y + dy).toDouble(), v) }

    private fun PixelGrid.s(x: Int, y: Int, v: Double = 1.0) = set(x.toDouble(), y.toDouble(), v)
    private fun float(t: Long, big: Boolean, amp: Double = 1.0): Int = (sin(t / 700.0) * (if (big) 1.0 else 0.6) * amp).px()

    // ── face parts ──

    fun eyes(g: PixelGrid, r: Rig, kind: String, o: FaceOpts) {
        val big = g.size >= 25
        val a = if (big) r.eyes25 else r.eyes13
        val ly = if (big) o.ly else 0
        val on = if (a.mode == EyeMode.HOLE) 0.0 else 1.0
        fun blk(xx: Int, yy: Int, ww: Int, hh: Int, v: Double) { for (i in 0 until ww) for (j in 0 until hh) g.s(xx + i, yy + j, v) }
        a.cx.forEachIndexed { idx, cx ->
            val x = cx + o.dx; val y = a.cy + o.dy; val w = a.w; val h = a.h; val left = idx == 0
            when (kind) {
                "open" -> blk(x + o.lx, y + ly, w, h, on)
                "blink", "closed" -> { val from = if (w > 1) 0 else -1; val to = if (w > 1) w else w + 1; for (i in from until to) g.s(x + i, y + h - 1, if (big) 1.0 else 0.5) }
                "half" -> blk(x + (if (big) 1 else 0), y + h - 1, w, 1, on)
                "happy" -> if (big) { g.s(x - 1, y + h - 1); for (i in 0 until w) g.s(x + i, y + h - 2); g.s(x + w, y + h - 1) } else { g.s(x - 1, y); g.s(x, y - 1); g.s(x + 1, y) }
                "sad" -> { blk(x, y + (if (h > 1) 1 else 0), w, if (h > 1) h - 1 else 1, on); g.s(if (left) x - 1 else x + w, y + h, 0.6) }
                "swirl" -> {
                    var cells = (0 until w).flatMap { i -> (0 until h).map { j -> i to j } }
                    if (cells.size == 1) cells = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
                    val ph = ((o.t / 110) % cells.size).toInt()
                    cells.forEachIndexed { k, (i, j) -> g.s(x + i, y + j, if (k == ph) (if (a.mode == EyeMode.HOLE) L else 0.15) else on) }
                }
                "angry" -> {
                    blk(x, y + h - 1, w, 1, on)
                    if (big) { val bx0 = if (left) x - 1 else x + w; val bx1 = if (left) x + w else x - 1; line(g, bx0.toDouble(), (y - 2).toDouble(), bx1.toDouble(), (y - 1).toDouble(), 1.0) }
                    else g.s(if (left) x + 1 else x - 1, y - 1)
                }
                "wide" -> blk(x - (if (big) 1 else 0), y - (if (big) 1 else 0), w + (if (big) 2 else 0), h + (if (big) 1 else 0), on)
            }
        }
    }

    fun mouth(g: PixelGrid, r: Rig, kind: String, o: FaceOpts) {
        if (r.mouth(g, kind, o)) return
        val m = if (g.size >= 25) r.mouth25 else r.mouth13
        MOUTH.getValue(kind).forEach { (px, py) ->
            val xs = px * m.scale
            g.s(m.x + xs + o.dx, m.y + py + o.dy)
            if (m.scale > 1 && px != 0) g.s(m.x + xs - xs.sign + o.dx, m.y + py + o.dy)
        }
    }

    fun cheeks(g: PixelGrid, r: Rig, o: FaceOpts, v: Double) =
        (if (g.size >= 25) r.cheeks25 else r.cheeks13).forEach { (x, y) -> g.dot((x + o.dx).toDouble(), (y + o.dy).toDouble(), v) }

    private fun hearts(g: PixelGrid, t: Long, count: Int) {
        val big = g.size >= 25
        for (i in 0 until count) {
            val ph = ((t / 1300.0) + i.toDouble() / count) % 1.0
            val hx = if (big) (if (i % 2 == 1) 20 else 2) else (if (i % 2 == 1) 11 else 1)
            val hy = ((if (big) 11.0 else 6.0) - ph * (if (big) 9 else 5)).px()
            if (big) HEART.forEachIndexed { j, row -> row.forEachIndexed { k, ch -> if (ch == '1') g.dot((hx + k).toDouble(), (hy + j).toDouble(), 1 - ph * 0.7) } }
            else g.dot(hx.toDouble(), hy.toDouble(), 1 - ph * 0.7)
        }
    }

    private fun zz(g: PixelGrid, t: Long, r: Rig) {
        val big = g.size >= 25
        for (i in 0..1) {
            val ph = ((t / 2400.0) + i * 0.5) % 1.0
            if (big) Z.forEachIndexed { j, row -> row.forEachIndexed { k, ch -> if (ch == '1') g.dot((18 + (ph * 3).px() + k).toDouble(), ((r.top25 + 1 - ph * 5).px() + j).toDouble(), 1 - ph) } }
            else g.dot((10 + ph.px()).toDouble(), (2 - ph * 2).px().toDouble(), 1 - ph)
        }
    }

    private fun steam(g: PixelGrid, t: Long, r: Rig) {
        val big = g.size >= 25
        for (i in 0..1) {
            val ph = ((t / 900.0) + i * 0.5) % 1.0
            val side = if (i == 1) 1 else -1
            if (big) { val x = 12 + side * (8 + ph * 3); val y = r.top25 - ph * 4; g.dot(x, y, 1 - ph); g.dot(x + side, y, 0.7 * (1 - ph)); g.dot(x, y - 1, 0.6 * (1 - ph)) }
            else g.dot(6 + side * (5 + ph * 1.5), 1 - ph * 2, 1 - ph)
        }
    }

    // ── moods, reactions, AOD ──

    fun frame(r: Rig, size: Int, pose: Pose, now: Long): PixelGrid {
        val g = PixelGrid(size)
        val reaction = pose.reaction
        if (reaction != null) {
            val t = (now - pose.reactionStart).coerceAtLeast(0)
            when (reaction) {
                Reaction.PET -> pet(g, r, t)
                Reaction.YAWN -> yawn(g, r, t)
                Reaction.DIZZY -> dizzy(g, r, t)
                Reaction.ANGRY -> angry(g, r, t)
                Reaction.CALMED -> calmed(g, r, t)
                Reaction.PEEK -> peek(g, r, t)
                Reaction.BOO -> r.signature(g, t)
            }
            return g
        }
        val t = now.coerceAtLeast(0)
        when (pose.base) {
            Base.HAPPY -> happy(g, r, t)
            Base.CONTENT -> content(g, r, t, pose)
            Base.BORED -> bored(g, r, t)
            Base.SAD -> sad(g, r, t)
            Base.ASLEEP -> asleep(g, r, t)
            Base.MUNCH -> munch(g, r, t)
        }
        return g
    }

    fun still(r: Rig, size: Int, pose: Pose, minuteOfHour: Int): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        when (pose.base) {
            Base.ASLEEP -> {
                val o = FaceOpts()
                r.body(g, BodyOpts(b = 0.55, gill = Gill.STILL, tip = 0.15)); eyes(g, r, "closed", o); mouth(g, r, "small", o)
                if (big) Z.forEachIndexed { j, row -> row.forEachIndexed { k, ch -> if (ch == '1') g.dot((19 + k).toDouble(), (r.top25 - 2 + j).toDouble(), 0.8) } }
                else g.dot(10.0, 1.0, 0.8)
            }
            Base.MUNCH -> aodFill(g, r, pose.level / 100.0)
            Base.BORED -> { r.body(g, BodyOpts(gill = Gill.STILL, tip = 0.3)); eyes(g, r, "half", FaceOpts()); mouth(g, r, "flat", FaceOpts()) }
            Base.SAD -> { r.body(g, BodyOpts(gill = Gill.DROOP, tip = 0.2, b = 0.7)); eyes(g, r, "sad", FaceOpts()); mouth(g, r, "frown", FaceOpts()) }
            else -> {
                val (lx, ly) = LOOKS[((minuteOfHour % 4) + 4) % 4]
                r.body(g, BodyOpts(gill = Gill.STILL, tip = 0.5))
                eyes(g, r, "open", FaceOpts(lx = lx, ly = ly))
                mouth(g, r, if (pose.base == Base.HAPPY) "wide" else "smile", FaceOpts())
            }
        }
        return g
    }

    private fun aodFill(g: PixelGrid, r: Rig, lv: Double) {
        val h = PixelGrid(g.size)
        r.body(h, BodyOpts(gill = Gill.STILL, tip = 1.0))
        val fv = (F * 255).toInt()                                         // ChargeKit maps 0.22 → 56
        val ys = (0 until g.size * g.size).filter { abs(h[it % g.size, it / g.size] - fv) <= 1 }.map { it / g.size }
        if (ys.isNotEmpty()) {
            val top = ys.min(); val bot = ys.max()
            val fillTop = (bot - (bot - top) * lv).px()
            for (y in 0 until g.size) for (x in 0 until g.size) if (abs(h[x, y] - fv) <= 1) h.put(x, y, if (y >= fillTop) (0.42 * 255).toInt() else 0)
        }
        for (y in 0 until g.size) for (x in 0 until g.size) if (h[x, y] > 0) g.plot(x, y, h[x, y])
        eyes(g, r, "happy", FaceOpts()); mouth(g, r, "smile", FaceOpts())
    }

    private fun happy(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val c = t % 2600
        val dy = if (c < 500) (-sin(c / 500.0 * PI) * (if (big) 2 else 1)).px() else float(t, big)
        r.body(g, BodyOpts(dy = dy, t = t, flap = c < 500 && (c / 120) % 2 == 0L, gill = Gill.FAST, tip = 1.0))
        val o = FaceOpts(dy = dy); eyes(g, r, "happy", o); mouth(g, r, "wide", o); cheeks(g, r, o, 0.5)
    }

    private fun content(g: PixelGrid, r: Rig, t: Long, pose: Pose) {
        val dy = float(t, g.size >= 25)
        r.body(g, BodyOpts(dy = dy, t = t, tip = if ((t / 600) % 2 == 1L) 1.0 else 0.3))
        val o = FaceOpts(dy = dy, lx = pose.lookX, ly = pose.lookY)
        eyes(g, r, if (t % 3200 < 160) "blink" else "open", o); mouth(g, r, "smile", o); cheeks(g, r, o, 0.3)
    }

    private fun bored(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val dy = float(t, big, 0.5)
        val yawning = t % 7000 > 5800
        r.body(g, BodyOpts(dy = dy, t = t, gill = Gill.SLOW, tip = 0.3))
        val o = FaceOpts(dy = dy); eyes(g, r, if (yawning) "closed" else "half", o); mouth(g, r, if (yawning) "O" else "flat", o)
        if (big && !yawning && (t / 350) % 2 == 1L) g.dot(6.0, 22.0, 0.5)
    }

    private fun sad(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val dy = if (t % 5200 > 4300) 1 else 0
        r.body(g, BodyOpts(dy = dy, t = t, b = 0.7, gill = Gill.DROOP, tip = 0.2))
        val o = FaceOpts(dy = dy); eyes(g, r, "sad", o); mouth(g, r, "frown", o)
        val a = if (big) r.eyes25 else r.eyes13
        val tr = (t % 2400) / 2400.0
        g.dot((a.cx[1] + a.w).toDouble(), a.cy + a.h + 1 + tr * (if (big) 4 else 2), 0.6 * (1 - tr))
    }

    private fun asleep(g: PixelGrid, r: Rig, t: Long) {
        val br = 0.5 + 0.25 * (0.5 + 0.5 * sin(t / 900.0))
        val dy = float(t, g.size >= 25, 0.5)
        r.body(g, BodyOpts(dy = dy, t = t, b = br, gill = Gill.STILL, tip = 0.15))
        val o = FaceOpts(dy = dy); eyes(g, r, "closed", o); mouth(g, r, "small", o); zz(g, t, r)
    }

    private fun pet(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val dy = float(t, big)
        r.body(g, BodyOpts(dy = dy, t = t, gill = Gill.FAST, tip = 1.0, flap = (t / 150) % 2 == 0L))
        val o = FaceOpts(dy = dy); eyes(g, r, "happy", o); mouth(g, r, "wide", o); cheeks(g, r, o, 0.7); hearts(g, t, if (big) 3 else 2)
    }

    private fun dizzy(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val dx = (sin(t / 110.0) * (if (big) 1.4 else 1.0)).px()
        val dy = float(t, big)
        r.body(g, BodyOpts(dx = dx, dy = dy, t = t * 3, gill = Gill.FAST))
        eyes(g, r, "swirl", FaceOpts(dx = dx, dy = dy, t = t)); mouth(g, r, "zig", FaceOpts(dx = dx, dy = dy))
    }

    private fun angry(g: PixelGrid, r: Rig, t: Long) {
        val sh = if ((t / 90) % 2 == 1L && t % 1500 < 500) 1 else 0
        r.body(g, BodyOpts(dx = sh, puff = true, t = t, gill = Gill.FAST, tip = 1.0))
        val o = FaceOpts(dx = sh); eyes(g, r, "angry", o); mouth(g, r, "zig", o); steam(g, t, r)
    }

    private fun calmed(g: PixelGrid, r: Rig, t: Long) {
        if (t % 4000 < 1600) { r.body(g, BodyOpts(t = t, gill = Gill.SLOW)); eyes(g, r, "closed", FaceOpts()); mouth(g, r, "small", FaceOpts()) }
        else pet(g, r, t)
    }

    private fun munch(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val o = FaceOpts(dy = float(t, big), t = t)
        if (r.munch(g, t, o)) return
        val c = t % 2200
        r.body(g, BodyOpts(dy = o.dy, t = t))
        eyes(g, r, if (c > 1000) "happy" else "open", o)
        if (c < 1000) {
            val m = if (big) r.mouth25 else r.mouth13
            val x = (if (big) 23.0 else 12.0) - c / 1000.0 * (if (big) (23 - m.x - 2) else (12 - m.x - 1))
            if (big) BOLT.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> if (ch == '1') g.s(x.px() + i, m.y - 1 + j + o.dy) } }
            else g.s(x.px(), m.y + o.dy)
            mouth(g, r, "O", o)
        } else mouth(g, r, if ((c / 180) % 2 == 1L) "chomp" else "flat", o)
    }

    private fun peek(g: PixelGrid, r: Rig, t: Long) {
        val big = g.size >= 25
        val c = t % 3900
        val rise = 1200L
        val off = if (c < rise) ((1 - c / rise.toDouble()) * (if (big) 14 else 8)).px() else 0
        r.body(g, BodyOpts(dy = off, t = t, tip = 1.0))
        val o = FaceOpts(dy = off); eyes(g, r, if (c < rise) "open" else "wide", o); mouth(g, r, if (c < rise) "small" else "O", o)
        if (c > rise && (c / 250) % 2 == 1L) { if (big) { for (y in 3..6) g.dot(22.0, y.toDouble(), 1.0); g.dot(22.0, 8.0, 1.0) } else { g.dot(11.0, 3.0, 1.0); g.dot(11.0, 5.0, 1.0) } }
    }

    private fun yawn(g: PixelGrid, r: Rig, t: Long) {
        val c = t % 3600
        val dy = float(t, g.size >= 25, 0.5)
        r.body(g, BodyOpts(dy = dy, t = t, gill = Gill.SLOW, tip = 0.3))
        val o = FaceOpts(dy = dy)
        if (c < 1800) { eyes(g, r, "closed", o); mouth(g, r, "O", o) } else { eyes(g, r, "half", o); mouth(g, r, "small", o) }
    }

}
```

Port note: the mockup finds fill cells by value `F`. ChargeKit stores `round(0.22 × 255) = 56`, so the fill-cell test compares within ±1.

- [ ] **Step 4: Run the test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.pet.PetKindTest'`
Expected: PASS (1 test). The build compiles `RigArt`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/pet/PetKind.kt app/src/main/java/app/backlit/pet/RigArt.kt app/src/test/java/app/backlit/pet/PetKindTest.kt
git commit -m "feat(pets): pet kinds and the shared rig expression system

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The five rigs

**Files:**
- Create: `app/src/main/java/app/backlit/pet/rigs/FrogRig.kt`, `PenguinRig.kt`, `AxolotlRig.kt`, `OwlRig.kt`, `RobotRig.kt`

**Interfaces:**
- Consumes: `Rig`, `RigArt` (blob/line/bm/pts/eyes/mouth and O/F/L), `BodyOpts`, `FaceOpts`, `EyeSpec`, `MouthSpec`, `Gill` (Task 1).
- Produces: `object FrogRig : Rig`, `object PenguinRig : Rig`, `object AxolotlRig : Rig`, `object OwlRig : Rig`, `object RobotRig : Rig`.

These are tested through Task 3's `PetArtTest`, which renders every state of every rig. This task's gate is compilation. Task 3 is written test-first and covers the rigs.

- [ ] **Step 1: Write the rigs** (ported from the mockup's `RIGS`)

`FrogRig.kt`:

```kotlin
package app.backlit.pet.rigs

import app.backlit.pet.BodyOpts
import app.backlit.pet.EyeMode
import app.backlit.pet.EyeSpec
import app.backlit.pet.FaceOpts
import app.backlit.pet.MouthSpec
import app.backlit.pet.Rig
import app.backlit.pet.RigArt
import app.backlit.pet.RigArt.F
import app.backlit.pet.RigArt.L
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object FrogRig : Rig {
    override val eyes25 = EyeSpec(listOf(6, 16), 9, 2, 2, EyeMode.HOLE)
    override val eyes13 = EyeSpec(listOf(3, 9), 5, 1, 1, EyeMode.HOLE)
    override val mouth25 = MouthSpec(12, 16, 2)
    override val mouth13 = MouthSpec(6, 8, 1)
    override val cheeks25 = listOf(5 to 16, 19 to 16)
    override val cheeks13 = listOf(2 to 8, 10 to 8)
    override val top25 = 5
    private val BOLT = listOf("01", "11", "10")

    override fun body(g: PixelGrid, o: BodyOpts) {
        val k = if (o.puff) 1.08 else 1.0
        if (g.size >= 25) {
            RigArt.blob(g, 12.0 + o.dx, 15.0 + o.dy, 9.5 * k, 6 * k, F, o.b)
            for (ex in listOf(7, 17)) RigArt.blob(g, (ex + o.dx).toDouble(), 9.0 + o.dy, 3.4, 3.4, L, o.b)
            RigArt.pts(g, listOf(7 to 21, 8 to 21, 9 to 21, 15 to 21, 16 to 21, 17 to 21), o.dx, o.dy, 0.75)
        } else {
            RigArt.bm(g, listOf("oo.....oo", "oLo...oLo", "ooooooooo", "oFFFFFFFo", "oFFFFFFFo", ".oFFFFFo."), 2 + o.dx, 4 + o.dy, mapOf('o' to o.b, 'L' to L, 'F' to F))
        }
    }

    override fun signature(g: PixelGrid, t: Long) {
        body(g, BodyOpts()); RigArt.eyes(g, this, "open", FaceOpts()); RigArt.mouth(g, this, "smile", FaceOpts())
        val c = t % 1500
        if (g.size >= 25) {
            val fa = t / 400.0
            val fx = (19 + cos(fa) * 1.5).px(); val fy = (5 + sin(fa * 1.3)).px()
            if (c < 1150) { g.set(fx.toDouble(), fy.toDouble(), 1.0); g.dot(fx - 1.0, fy - 1.0, 0.45); g.dot(fx + 1.0, fy - 1.0, 0.45) }
            if (c in 801..1199) { var f = (c - 800) / 200.0; if (f > 1) f = 2 - f; RigArt.line(g, 12.0, 17.0, 12 + (fx - 12) * f, 17 + (fy - 17) * f, 1.0) }
        } else {
            if (c in 801..1199) { g.set(8.0, 7.0, 1.0); g.set(9.0, 6.0, 1.0); g.set(10.0, 5.0, 1.0) } else g.set(10.0, 3.0, 1.0)
        }
    }

    override fun munch(g: PixelGrid, t: Long, o: FaceOpts): Boolean {
        val c = t % 2200
        body(g, BodyOpts(dy = o.dy, t = o.t))
        RigArt.eyes(g, this, if (c > 1300) "happy" else "open", o)
        if (g.size >= 25) {
            val bx = 23 - min(1.0, c / 900.0) * 4
            if (c < 1000) BOLT.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> if (ch == '1') g.set((bx.px() + i).toDouble(), (8 + j + o.dy).toDouble(), 1.0) } }
            if (c in 801..1099) { var f = (c - 800) / 150.0; if (f > 1) f = 2 - f; RigArt.line(g, 12.0, 17.0 + o.dy, 12 + (bx - 12) * f, 17 + (8 - 17) * f + o.dy, 1.0) }
        } else if (c < 900) g.set((11 - (c / 300.0).px()).toDouble(), 5.0, 1.0)
        RigArt.mouth(g, this, if (c > 1100) "chomp" else "smile", o)
        return true
    }
}
```

`PenguinRig.kt`:

```kotlin
package app.backlit.pet.rigs

import app.backlit.pet.BodyOpts
import app.backlit.pet.EyeMode
import app.backlit.pet.EyeSpec
import app.backlit.pet.FaceOpts
import app.backlit.pet.MouthSpec
import app.backlit.pet.Rig
import app.backlit.pet.RigArt
import app.backlit.pet.RigArt.F
import app.backlit.pet.RigArt.L
import app.backlit.pet.RigArt.O
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px

object PenguinRig : Rig {
    override val eyes25 = EyeSpec(listOf(10, 14), 9, 1, 2, EyeMode.HOLE)
    override val eyes13 = EyeSpec(listOf(5, 7), 5, 1, 1, EyeMode.HOLE)
    override val mouth25 = MouthSpec(12, 12)
    override val mouth13 = MouthSpec(6, 6)
    override val cheeks25 = listOf(9 to 12, 15 to 12)
    override val cheeks13 = emptyList<Pair<Int, Int>>()
    override val top25 = 4

    override fun mouth(g: PixelGrid, kind: String, o: FaceOpts): Boolean {
        val open = kind == "O" || kind == "chomp" || kind == "wide" || kind == "zig"
        if (g.size >= 25) {
            if (open) { RigArt.pts(g, listOf(11 to 12, 12 to 12, 13 to 12, 11 to 14, 12 to 14, 13 to 14), o.dx, o.dy, 1.0); g.set(12.0 + o.dx, 13.0 + o.dy, 0.0) }
            else RigArt.pts(g, listOf(11 to 12, 12 to 12, 13 to 12, 12 to 13), o.dx, o.dy, 1.0)
        } else {
            g.set(6.0 + o.dx, 6.0 + o.dy, 1.0); if (open) g.set(6.0 + o.dx, 7.0 + o.dy, 1.0)
        }
        return true
    }

    override fun body(g: PixelGrid, o: BodyOpts) {
        val k = if (o.puff) 1.08 else 1.0
        if (g.size >= 25) {
            RigArt.blob(g, 12.0 + o.dx, 13.0 + o.dy, 6.5 * k, 8.5 * k, F, o.b)
            RigArt.blob(g, 10.0 + o.dx, 9.5 + o.dy, 2.6, 2.6, L, null)
            RigArt.blob(g, 14.0 + o.dx, 9.5 + o.dy, 2.6, 2.6, L, null)
            RigArt.blob(g, 12.0 + o.dx, 15.5 + o.dy, 4.3, 5.5, L, null)
            if (o.flap) { RigArt.line(g, 5.0 + o.dx, 12.0 + o.dy, 2.0 + o.dx, 8.0 + o.dy, o.b); RigArt.line(g, 19.0 + o.dx, 12.0 + o.dy, 22.0 + o.dx, 8.0 + o.dy, o.b) }
            else { RigArt.line(g, 5.0 + o.dx, 11.0 + o.dy, 4.0 + o.dx, 16.0 + o.dy, o.b); RigArt.line(g, 19.0 + o.dx, 11.0 + o.dy, 20.0 + o.dx, 16.0 + o.dy, o.b) }
            RigArt.pts(g, listOf(9 to 22, 10 to 22, 11 to 22, 13 to 22, 14 to 22, 15 to 22), o.dx, o.dy + o.footLift, 1.0)
        } else {
            RigArt.bm(g, listOf("..ooooo..", ".oLLoLLo.", ".oLLoLLo.", "ooLLLLLoo", ".oLLLLLo.", ".oLLLLLo.", "..ooooo..", "..X...X.."), 2 + o.dx, 3 + o.dy, mapOf('o' to o.b, 'L' to L, 'X' to 1.0))
            if (o.flap) { g.set(1.0 + o.dx, 5.0 + o.dy, o.b); g.set(11.0 + o.dx, 5.0 + o.dy, o.b) }
        }
    }

    override fun signature(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val sx = (-15 + (t % 2600) / 2600.0 * 30 * (if (big) 1.0 else 0.6)).px()
        if (big) {
            RigArt.blob(g, 12.0 + sx, 16.0, 8.0, 4.2, F, O); RigArt.blob(g, 13.0 + sx, 17.0, 5.0, 2.4, L, null)
            g.set(20.0 + sx, 15.0, 1.0); g.set(21.0 + sx, 16.0, 1.0); g.set(17.0 + sx, 14.0, 0.0)
            g.dot(3.0 + sx, 19.0, 0.35); g.dot(1.0 + sx, 20.0, 0.25)
        } else {
            RigArt.blob(g, 6.0 + sx, 9.0, 4.2, 2.3, F, O); g.set(10.0 + sx, 9.0, 1.0)
        }
    }
}
```

`AxolotlRig.kt`:

```kotlin
package app.backlit.pet.rigs

import app.backlit.pet.BodyOpts
import app.backlit.pet.EyeMode
import app.backlit.pet.EyeSpec
import app.backlit.pet.FaceOpts
import app.backlit.pet.Gill
import app.backlit.pet.MouthSpec
import app.backlit.pet.Rig
import app.backlit.pet.RigArt
import app.backlit.pet.RigArt.F
import app.backlit.pet.RigArt.O
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.sin

object AxolotlRig : Rig {
    override val eyes25 = EyeSpec(listOf(9, 15), 12, 1, 2, EyeMode.BRIGHT)
    override val eyes13 = EyeSpec(listOf(4, 8), 6, 1, 1, EyeMode.BRIGHT)
    override val mouth25 = MouthSpec(12, 16)
    override val mouth13 = MouthSpec(6, 7)
    override val cheeks25 = listOf(7 to 16, 17 to 16)
    override val cheeks13 = listOf(3 to 8, 9 to 8)
    override val top25 = 8

    override fun body(g: PixelGrid, o: BodyOpts) {
        val k = if (o.puff) 1.08 else 1.0
        val sw = when (o.gill) { Gill.FAST -> sin(o.t / 100.0); Gill.SLOW -> sin(o.t / 1400.0); Gill.STILL -> 0.0; else -> sin(o.t / 550.0) }
        val droop = o.gill == Gill.DROOP
        val tipV = if (o.b >= O) 1.0 else o.b
        if (g.size >= 25) {
            RigArt.blob(g, 12.0 + o.dx, 14.0 + o.dy, 7.5 * k, 5.5 * k, F, o.b)
            for (sd in listOf(-1, 1)) listOf(10 to -2, 13 to 0, 16 to 2).forEachIndexed { i, (gy, gdy) ->
                val x0 = 12 + sd * 7 + o.dx; val y0 = gy + o.dy
                val w = if (droop) 2 else (sw * (if (i == 1) 0.6 else 1.0) * (if (i == 0) -1 else 1)).px()
                val x1 = x0 + sd * 3; val y1 = y0 + gdy + w
                RigArt.line(g, x0.toDouble(), y0.toDouble(), x1.toDouble(), y1.toDouble(), 0.75 * (o.b / O))
                g.set(x1.toDouble(), y1.toDouble(), tipV); g.set((x1 + sd).toDouble(), (y1 - 1).toDouble(), 0.8 * (o.b / O)); g.set((x1 + sd).toDouble(), (y1 + 1).toDouble(), 0.8 * (o.b / O))
            }
        } else {
            RigArt.bm(g, listOf("..ooooo..", ".oFFFFFo.", "oFFFFFFFo", "oFFFFFFFo", ".oFFFFFo.", "..ooooo.."), 2 + o.dx, 4 + o.dy, mapOf('o' to o.b, 'F' to F))
            val w = if (droop) 1 else sw.px()
            listOf(1 to 4, 0 to 7, 1 to 10).forEachIndexed { i, (px, py) ->
                val yo = if (droop) 1 else if (i == 1) 0 else w * (if (i == 0) -1 else 1)
                g.set((px + o.dx).toDouble(), (py + o.dy + yo).toDouble(), tipV); g.set((12 - px + o.dx).toDouble(), (py + o.dy + yo).toDouble(), tipV)
            }
        }
    }

    override fun signature(g: PixelGrid, t: Long) {
        body(g, BodyOpts(t = t)); RigArt.eyes(g, this, "open", FaceOpts()); RigArt.mouth(g, this, "O", FaceOpts())
        if (g.size >= 25) {
            for (i in 0..2) {
                val q = ((t / 1000.0) + i / 3.0) % 1.0
                val bx = 12 + sin(q * 7 + i) * 1.6; val by = 15 - q * 15
                if (q > 0.35) { g.dot(bx - 1, by, 1 - q); g.dot(bx + 1, by, 1 - q); g.dot(bx, by - 1, 1 - q); g.dot(bx, by + 1, 1 - q) } else g.dot(bx, by, 1 - q)
            }
        } else { val q = (t / 900.0) % 1.0; g.dot(6.0, 7 - q * 7, 1 - q) }
    }
}
```

`OwlRig.kt`:

```kotlin
package app.backlit.pet.rigs

import app.backlit.pet.BodyOpts
import app.backlit.pet.EyeMode
import app.backlit.pet.EyeSpec
import app.backlit.pet.FaceOpts
import app.backlit.pet.MouthSpec
import app.backlit.pet.Rig
import app.backlit.pet.RigArt
import app.backlit.pet.RigArt.F
import app.backlit.pet.RigArt.L
import app.backlit.pet.RigArt.O
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.sin

object OwlRig : Rig {
    override val eyes25 = EyeSpec(listOf(9, 15), 11, 1, 2, EyeMode.HOLE)
    override val eyes13 = EyeSpec(listOf(4, 8), 5, 1, 1, EyeMode.HOLE)
    override val mouth25 = MouthSpec(12, 13)
    override val mouth13 = MouthSpec(6, 6)
    override val cheeks25 = emptyList<Pair<Int, Int>>()
    override val cheeks13 = emptyList<Pair<Int, Int>>()
    override val top25 = 5

    override fun mouth(g: PixelGrid, kind: String, o: FaceOpts): Boolean {
        val open = kind == "O" || kind == "chomp" || kind == "wide" || kind == "zig"
        if (g.size >= 25) {
            g.set(12.0 + o.dx, 13.0 + o.dy, 1.0); g.set(12.0 + o.dx, 14.0 + o.dy, 1.0)
            if (open) { g.set(11.0 + o.dx, 15.0 + o.dy, 1.0); g.set(13.0 + o.dx, 15.0 + o.dy, 1.0) }
        } else { g.set(6.0 + o.dx, 6.0 + o.dy, 1.0); if (open) g.set(6.0 + o.dx, 7.0 + o.dy, 0.7) }
        return true
    }

    override fun body(g: PixelGrid, o: BodyOpts) {
        val k = if (o.puff) 1.08 else 1.0
        if (g.size >= 25) {
            RigArt.blob(g, 12.0 + o.dx, 14.0 + o.dy, 7.5 * k, 8.5 * k, F, o.b)
            RigArt.pts(g, listOf(6 to 5, 7 to 6, 18 to 5, 17 to 6), o.dx, o.dy, o.b)
            for (ex in listOf(9, 15)) RigArt.blob(g, (ex + o.dx).toDouble(), 11.5 + o.dy, 3.0, 3.0, L, if (o.b >= O) 1.0 else o.b)
            RigArt.pts(g, listOf(10 to 17, 11 to 18, 13 to 18, 14 to 17, 11 to 20, 12 to 21, 13 to 20), o.dx, o.dy, 0.6 * (o.b / O))
            if (o.flap) { RigArt.line(g, 4.0 + o.dx, 13.0 + o.dy, 1.0 + o.dx, 10.0 + o.dy, o.b); RigArt.line(g, 20.0 + o.dx, 13.0 + o.dy, 23.0 + o.dx, 10.0 + o.dy, o.b) }
            RigArt.pts(g, listOf(10 to 23, 11 to 23, 13 to 23, 14 to 23), o.dx, o.dy, 1.0)
        } else {
            RigArt.bm(g, listOf("o.......o", ".ooooooo.", "oLLoFoLLo", "oLLLFLLLo", "oLLoFoLLo", "oFFFFFFFo", ".oFdFdFo.", "..ooooo.."), 2 + o.dx, 2 + o.dy, mapOf('o' to o.b, 'L' to L, 'F' to F, 'd' to 0.6))
            if (o.flap) { g.set(1.0 + o.dx, 6.0 + o.dy, o.b); g.set(11.0 + o.dx, 6.0 + o.dy, o.b) }
        }
    }

    override fun signature(g: PixelGrid, t: Long) {
        val look = (sin(t / 420.0) * 1.4).px().coerceIn(-1, 1)
        body(g, BodyOpts()); RigArt.eyes(g, this, "open", FaceOpts(lx = look)); RigArt.mouth(g, this, "small", FaceOpts())
    }
}
```

`RobotRig.kt`:

```kotlin
package app.backlit.pet.rigs

import app.backlit.pet.BodyOpts
import app.backlit.pet.EyeMode
import app.backlit.pet.EyeSpec
import app.backlit.pet.FaceOpts
import app.backlit.pet.MouthSpec
import app.backlit.pet.Rig
import app.backlit.pet.RigArt
import app.backlit.pet.RigArt.F
import app.backlit.pet.RigArt.O
import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import kotlin.math.min
import kotlin.math.sign

object RobotRig : Rig {
    override val eyes25 = EyeSpec(listOf(9, 14), 10, 2, 2, EyeMode.BRIGHT)
    override val eyes13 = EyeSpec(listOf(5, 7), 5, 1, 1, EyeMode.BRIGHT)
    override val mouth25 = MouthSpec(12, 14, 1)
    override val mouth13 = MouthSpec(6, 7)
    override val cheeks25 = listOf(8 to 13, 16 to 13)
    override val cheeks13 = emptyList<Pair<Int, Int>>()
    override val top25 = 3

    override fun body(g: PixelGrid, o: BodyOpts) {
        val dim = o.b / O
        if (g.size >= 25) {
            val p = if (o.puff) 1 else 0
            for (y in 6 - p..17 + p) for (x in 5 - p..19 + p) {
                val edge = y == 6 - p || y == 17 + p || x == 5 - p || x == 19 + p
                val corner = (y == 6 - p || y == 17 + p) && (x == 5 - p || x == 19 + p)
                if (!corner) g.set((x + o.dx).toDouble(), (y + o.dy).toDouble(), if (edge) o.b else F)
            }
            for (y in 8..15) for (x in 7..17) g.set((x + o.dx).toDouble(), (y + o.dy).toDouble(), if (y == 8 || y == 15 || x == 7 || x == 17) 0.45 * dim else 0.0)
            RigArt.pts(g, listOf(12 to 5, 12 to 4, 4 to 11, 4 to 12, 20 to 11, 20 to 12), o.dx, o.dy, 0.7 * dim)
            g.set(12.0 + o.dx, 3.0 + o.dy, o.tip); g.set(12.0 + o.dx, 18.0 + o.dy, 0.6 * dim)
            for (y in 19..22) for (x in 8..16) g.set((x + o.dx).toDouble(), (y + o.dy).toDouble(), if (y == 19 || y == 22 || x == 8 || x == 16) o.b else F)
        } else {
            RigArt.bm(g, listOf("...o...", "...X...", "ooooooo", "oFFFFFo", "oFFFFFo", "oFFFFFo", "oFFFFFo", "ooooooo", "..ooo.."), 3 + o.dx, 1 + o.dy, mapOf('o' to o.b, 'F' to F, 'X' to o.tip))
        }
    }

    override fun signature(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val h = PixelGrid(g.size)
        body(h, BodyOpts(tip = 1.0)); RigArt.eyes(h, this, "open", FaceOpts()); RigArt.mouth(h, this, "flat", FaceOpts())
        val n = g.size
        val seed = t / 110
        for (y in 0 until n) {
            val r = ((y * 73 + seed * 31) % 11).toInt()
            var off = if (r < 2) (if (r == 1) 2 else -2) else if (r == 2) 1 else 0
            if (!big) off = off.sign
            for (x in 0 until n) { val sx = x - off; if (sx in 0 until n && h[sx, y] > 0) g.plot(x, y, h[sx, y]) }
        }
    }

    override fun munch(g: PixelGrid, t: Long, o: FaceOpts): Boolean {
        val c = t % 2400
        body(g, BodyOpts(dy = o.dy, tip = if ((t / 200) % 2 == 1L) 1.0 else 0.4))
        RigArt.eyes(g, this, if (c > 1200) "happy" else "open", o)
        RigArt.mouth(g, this, if (c > 1200) "wide" else "flat", o)
        if (g.size >= 25) {
            val reach = min(1.0, c / 900.0)
            RigArt.line(g, 24.0, 15.0, 24 - reach * 3, 12.0, 0.8)
            if (c > 900) g.dot(20.0, 12.0, if ((t / 120) % 2 == 1L) 1.0 else 0.5)
        } else if (c > 600) g.set(11.0, 6.0, if ((t / 150) % 2 == 1L) 1.0 else 0.4)
        return true
    }
}
```

- [ ] **Step 2: Build**

Run: `source .superpowers/env.sh && ./gradlew -q :app:compileDebugKotlin && echo COMPILE_OK`
Expected: `COMPILE_OK`. Fix only compile errors against the real APIs, without changing behaviour, and record any deviation as a ruling.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/backlit/pet/rigs/
git commit -m "feat(pets): frog, penguin, axolotl, owl and robot rigs

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: PetArt dispatch and preview ids

**Files:**
- Create: `app/src/main/java/app/backlit/pet/PetArt.kt`
- Modify: `app/src/main/java/app/backlit/pet/PetPreviewAnimation.kt`
- Test: `app/src/test/java/app/backlit/pet/PetArtTest.kt`, `app/src/test/java/app/backlit/pet/PetPreviewAnimationTest.kt`

**Interfaces:**
- Consumes: `PetKind` and `RigArt` (Task 1), the rigs (Task 2), and `GhostArt` (existing, unchanged).
- Produces:
  - `object PetArt { fun rigFor(kind: PetKind): Rig?; fun frame(kind: PetKind, size: Int, pose: Pose, now: Long): PixelGrid; fun still(kind: PetKind, size: Int, pose: Pose, minuteOfHour: Int): PixelGrid }`
  - `PetPreviewAnimation(base: Base?, reaction: Reaction?, kind: PetKind = PetKind.GHOST)`
  - `PetPreviewAnimation.idFor(kind: PetKind, base: Base): String`. The existing `idFor(base)` stays for the ghost.

- [ ] **Step 1: Write the failing tests**

`PetArtTest.kt`:

```kotlin
package app.backlit.pet

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetArtTest {
    private val rigPets = PetKind.entries - PetKind.GHOST
    private fun pose(b: Base = Base.CONTENT, r: Reaction? = null, level: Int = 62) = Pose(b, r, 0L, 0, 0, 0, level)

    private fun inMask(g: PixelGrid) {
        for (y in 0 until g.size) for (x in 0 until g.size) if (g[x, y] > 0) assertTrue("x=$x y=$y", g.hasLed(x, y))
    }

    @Test
    fun everyPetEveryStateRendersInsideTheMask() {
        for (kind in PetKind.entries) for (size in listOf(25, 13)) {
            for (b in Base.entries) for (t in 0L..6000L step 300) PetArt.frame(kind, size, pose(b), t).also { inMask(it); assertTrue("$kind $b", it.litCount() > 0) }
            for (r in Reaction.entries) {
                val frames = (0L..r.ms step 100).map { PetArt.frame(kind, size, pose(r = r), it) }
                frames.forEach { inMask(it) }
                assertTrue("$kind $r $size lit at some point", frames.any { it.litCount() > 0 })
            }
            for (b in Base.entries) for (m in 0..3) PetArt.still(kind, size, pose(b), m).also { inMask(it); assertTrue(it.litCount() > 0) }
        }
    }

    @Test
    fun ghostDelegatesUnchanged() {
        for (size in listOf(25, 13)) for (t in listOf(0L, 700L, 2199L, 5000L)) {
            for (b in Base.entries) assertEquals(GhostArt.frame(size, pose(b), t), PetArt.frame(PetKind.GHOST, size, pose(b), t))
            for (r in Reaction.entries) assertEquals(GhostArt.frame(size, pose(r = r), t), PetArt.frame(PetKind.GHOST, size, pose(r = r), t))
            for (b in Base.entries) assertEquals(GhostArt.still(size, pose(b), 1), PetArt.still(PetKind.GHOST, size, pose(b), 1))
        }
    }

    @Test
    fun aodFillGrowsForEveryPet() {
        for (kind in rigPets) {
            fun lit(level: Int) = PetArt.still(kind, 25, pose(Base.MUNCH, level = level), 0).raw().count { it in 100..115 }   // 0.42 → 107
            assertTrue("$kind", lit(30) < lit(62) && lit(62) < lit(95))
        }
    }

    @Test
    fun idleStillIsSymmetricForOwlAndRobot() {
        for (kind in listOf(PetKind.OWL, PetKind.ROBOT)) {
            val g = PetArt.still(kind, 25, pose(Base.CONTENT), 0)
            for (y in 0 until 25) for (x in 0 until 25) assertEquals("$kind x=$x y=$y", g[x, y], g[24 - x, y])
        }
    }

    @Test
    fun petsLookDifferent() {
        val frames = PetKind.entries.map { PetArt.still(it, 25, pose(Base.CONTENT), 0) }
        assertEquals(frames.size, frames.toSet().size)
    }
}
```

Add this to `PetPreviewAnimationTest.kt`:

```kotlin
    @Test
    fun kindedIds() {
        val f = PetPreviewAnimation.parse("pet:frog:happy")!!
        assertEquals("pet:frog:happy", f.id)
        assertEquals(PetArt.frame(PetKind.FROG, 25, Pose(Base.HAPPY, null, 0, 0, 0, 0, 62), 400), f.frame(25, 400))
        assertEquals("pet:owl:munch", PetPreviewAnimation.idFor(PetKind.OWL, Base.MUNCH))
        assertEquals("pet:happy", PetPreviewAnimation.idFor(PetKind.GHOST, Base.HAPPY))
        assertNull(PetPreviewAnimation.parse("pet:cat:happy"))
        assertNull(PetPreviewAnimation.parse("pet:frog:nope"))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.pet.PetArtTest' --tests 'app.backlit.pet.PetPreviewAnimationTest'`
Expected: compilation FAIL, because `PetArt` is unresolved.

- [ ] **Step 3: Write the implementation**

`PetArt.kt`:

```kotlin
package app.backlit.pet

import app.backlit.pet.rigs.AxolotlRig
import app.backlit.pet.rigs.FrogRig
import app.backlit.pet.rigs.OwlRig
import app.backlit.pet.rigs.PenguinRig
import app.backlit.pet.rigs.RobotRig
import app.backlit.render.PixelGrid

/** Draws any pet: the ghost through its original art, the others through the shared rig system. */
object PetArt {
    fun rigFor(kind: PetKind): Rig? = when (kind) {
        PetKind.GHOST -> null
        PetKind.FROG -> FrogRig
        PetKind.PENGUIN -> PenguinRig
        PetKind.AXOLOTL -> AxolotlRig
        PetKind.OWL -> OwlRig
        PetKind.ROBOT -> RobotRig
    }

    fun frame(kind: PetKind, size: Int, pose: Pose, now: Long): PixelGrid =
        rigFor(kind)?.let { RigArt.frame(it, size, pose, now) } ?: GhostArt.frame(size, pose, now)

    fun still(kind: PetKind, size: Int, pose: Pose, minuteOfHour: Int): PixelGrid =
        rigFor(kind)?.let { RigArt.still(it, size, pose, minuteOfHour) } ?: GhostArt.still(size, pose, minuteOfHour)
}
```

Replace `PetPreviewAnimation.kt` with:

```kotlin
package app.backlit.pet

import app.backlit.anim.GlyphAnimation
import app.backlit.render.PixelGrid

/** A pet pose as a GlyphAnimation for "Show on Glyph". Ids: pet:<state> (ghost, legacy) or pet:<kind>:<state>. */
class PetPreviewAnimation(
    private val base: Base?,
    private val reaction: Reaction?,
    private val kind: PetKind = PetKind.GHOST,
) : GlyphAnimation {
    private val state = (reaction?.name ?: base?.name ?: Base.CONTENT.name).lowercase()
    override val id: String = if (kind == PetKind.GHOST) "pet:$state" else "pet:${kind.id}:$state"
    override val name: String = "Pet"
    override val loopMs: Long = reaction?.ms ?: 3000L

    override fun frame(size: Int, tMs: Long): PixelGrid =
        PetArt.frame(kind, size, Pose(base ?: Base.CONTENT, reaction, 0, 0, 0, 0, 62), tMs)

    companion object {
        fun idFor(base: Base) = "pet:" + base.name.lowercase()
        fun idFor(kind: PetKind, base: Base) = if (kind == PetKind.GHOST) idFor(base) else "pet:${kind.id}:${base.name.lowercase()}"

        fun parse(id: String): PetPreviewAnimation? {
            if (!id.startsWith("pet:")) return null
            val parts = id.removePrefix("pet:").split(':')
            val (kind, key) = when (parts.size) {
                1 -> PetKind.GHOST to parts[0]
                2 -> (PetKind.entries.firstOrNull { it.id == parts[0] } ?: return null) to parts[1]
                else -> return null
            }
            Reaction.entries.firstOrNull { it.name.lowercase() == key }?.let { return PetPreviewAnimation(null, it, kind) }
            Base.entries.firstOrNull { it.name.lowercase() == key }?.let { return PetPreviewAnimation(it, null, kind) }
            return null
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.pet.*' --tests 'app.backlit.render.ToyPreviewsTest'`
Expected: PASS, including the ghost golden. If `idleStillIsSymmetricForOwlAndRobot` fails on a rounding detail, inspect the failing pixel. Fix the rig only if it's a port mistake against the mockup. If the mockup itself is asymmetric there, drop that pet from the test and record a ruling.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/pet/PetArt.kt app/src/main/java/app/backlit/pet/PetPreviewAnimation.kt app/src/test/java/app/backlit/pet/
git commit -m "feat(pets): PetArt dispatch and kind-aware preview ids

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Pet kind and per-pet names in settings

**Files:**
- Modify: `app/src/main/java/app/backlit/data/Settings.kt`, `SettingsRepo.kt`
- Test: `app/src/test/java/app/backlit/data/SettingsRepoTest.kt`

**Interfaces:**
- Consumes: `PetKind` (Task 1).
- Produces:
  - `Settings.petKind: String = "ghost"` and `Settings.petNames: Map<String, String> = emptyMap()`
  - `SettingsRepo.petNameFor(s: Settings, kind: PetKind): String`
  - `SettingsRepo.withPetName(s: Settings, kind: PetKind, name: String): Settings`
  - `SettingsRepo.cleanPetName(s: String, fallback: String = "Boo"): String`

- [ ] **Step 1: Write the failing tests.** Add these after `petName = "Casper", ...` in `roundTripsEveryField`:

```kotlin
            petKind = "owl", petNames = mapOf("owl" to "Professor", "frog" to "Kermie"),
```

And add these tests:

```kotlin
    @Test
    fun petKindDefaultsToGhostAndKeepsGhostName() = runBlocking {
        val r = repo()
        r.update { it.copy(petName = "Casper") }                     // an existing user, before multi-pets
        val s = r.settings.first()
        assertEquals("ghost", s.petKind)
        assertEquals("Casper", SettingsRepo.petNameFor(s, app.backlit.pet.PetKind.GHOST))
    }

    @Test
    fun namesArePerPet() {
        val s = Settings()
        val owl = app.backlit.pet.PetKind.OWL
        assertEquals("Hoot", SettingsRepo.petNameFor(s, owl))
        val named = SettingsRepo.withPetName(s, owl, "Professor")
        assertEquals("Professor", SettingsRepo.petNameFor(named, owl))
        assertEquals("Boo", SettingsRepo.petNameFor(named, app.backlit.pet.PetKind.GHOST))
        assertEquals("Ribbit", SettingsRepo.petNameFor(named, app.backlit.pet.PetKind.FROG))
        val ghost = SettingsRepo.withPetName(named, app.backlit.pet.PetKind.GHOST, "Spooky")
        assertEquals("Spooky", ghost.petName); assertEquals("Professor", SettingsRepo.petNameFor(ghost, owl))
        assertEquals("Hoot", SettingsRepo.petNameFor(SettingsRepo.withPetName(s, owl, "   "), owl))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.data.SettingsRepoTest'`
Expected: compilation FAIL, because there's no parameter `petKind`.

- [ ] **Step 3: Write the implementation**

In `Settings.kt`, add these after `petToyEverBound`:

```kotlin
    val petKind: String = "ghost",
    val petNames: Map<String, String> = emptyMap(),
```

In `SettingsRepo.kt`, add these keys after `PET_BOUND`, plus the helpers:

```kotlin
        private val PET_KIND = stringPreferencesKey("pet_kind")
        private val PET_NAMES = stringPreferencesKey("pet_names")
        private val namesJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        /** The name to show for [kind]: the ghost keeps the original petName; the others live in petNames. */
        fun petNameFor(s: Settings, kind: app.backlit.pet.PetKind): String =
            if (kind == app.backlit.pet.PetKind.GHOST) cleanPetName(s.petName, kind.defaultName)
            else s.petNames[kind.id]?.let { cleanPetName(it, kind.defaultName) } ?: kind.defaultName

        fun withPetName(s: Settings, kind: app.backlit.pet.PetKind, name: String): Settings {
            val clean = cleanPetName(name, kind.defaultName)
            return if (kind == app.backlit.pet.PetKind.GHOST) s.copy(petName = clean) else s.copy(petNames = s.petNames + (kind.id to clean))
        }
```

Change `cleanPetName` to take a fallback:

```kotlin
        fun cleanPetName(s: String, fallback: String = "Boo"): String = s.trim().take(12).trim().ifBlank { fallback }
```

Add these to `toSettings()` after `petToyEverBound = ...,`:

```kotlin
                petKind = app.backlit.pet.PetKind.byId(this[PET_KIND] ?: d.petKind).id,
                petNames = runCatching { namesJson.decodeFromString<Map<String, String>>(this[PET_NAMES] ?: "{}") }.getOrDefault(emptyMap()),
```

Add these to `write()` after `this[PET_BOUND] = ...`:

```kotlin
            this[PET_KIND] = s.petKind
            this[PET_NAMES] = namesJson.encodeToString(s.petNames)
```

Add `import kotlinx.serialization.decodeFromString` and `import kotlinx.serialization.encodeToString`, the same reified helpers `AlertStore` already uses.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.data.SettingsRepoTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/data/ app/src/test/java/app/backlit/data/SettingsRepoTest.kt
git commit -m "feat(pets): chosen pet and per-pet names in settings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Toy and PET tab

**Files:**
- Modify: `app/src/main/java/app/backlit/glyph/PetToyService.kt`, `app/src/main/java/app/backlit/ui/PetScreen.kt`

**Interfaces:**
- Consumes: `PetArt`, `PetKind`, `PetPreviewAnimation.idFor(kind, base)` (Task 3), and `SettingsRepo.petNameFor/withPetName/petNameToSave` (Task 4).
- Produces: the user-facing pet chooser.

This is Android glue plus UI. It's gated by the build and the full suite, and checked on the device in Task 6.

- [ ] **Step 1: The toy draws the chosen pet.** In `PetToyService.kt`:
  - Replace the two `GhostArt.` calls with `PetArt.still(PetKind.byId(settings.petKind), profile.size, …)` and `PetArt.frame(PetKind.byId(settings.petKind), profile.size, …)`.
  - Swap the import `app.backlit.pet.GhostArt` for `app.backlit.pet.PetArt` and `app.backlit.pet.PetKind`.

- [ ] **Step 2: Add the PET tab chooser and per-pet names.** In `PetScreen.kt`:

At the top of `PetTab`, after `val sleep = …`:

```kotlin
    val kind = PetKind.byId(settings.petKind)
    val petName = SettingsRepo.petNameFor(settings, kind)
```

Directly after the setup-hint `Notice(...)` block, insert the chooser:

```kotlin
    Text("CHOOSE YOUR PET", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PetKind.entries.forEach { k ->
            val selected = k == kind
            androidx.compose.foundation.layout.Column(
                Modifier.weight(1f).border(1.dp, if (selected) BacklitColors.White else BacklitColors.Line).clickable { onUpdate { it.copy(petKind = k.id) } }.padding(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MatrixPreview(PetArt.frame(k, size, Pose(brain.base(now), null, 0, 0, 0, 0, 62), now), Modifier.fillMaxWidth())
                Text(SettingsRepo.petNameFor(settings, k).uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    color = if (selected) BacklitColors.White else BacklitColors.Dim)
            }
        }
    }
```

Note: the chooser uses `brain` and `now`, so place it **after** their declarations. If the existing setup hint comes before them, put the chooser right after the `now` ticker `LaunchedEffect` and before the main preview.

Then make these replacements in the rest of `PetTab`:
- **Main preview:** `GhostArt.frame(size, pose, now)` → `PetArt.frame(kind, size, pose, now)`.
- **Headline:** `settings.petName.uppercase()` → `petName.uppercase()`.
- **How-it-works row title:** `"How ${settings.petName}'s mood works"` → `"How $petName's mood works"`.
- **Name field:** re-key its local state per pet, and save through `withPetName`:

```kotlin
    var name by remember(kind) { mutableStateOf(petName) }
    OutlinedTextField(
        value = name,
        onValueChange = { v ->
            name = v.take(12)
            SettingsRepo.petNameToSave(name)?.let { clean -> if (clean != petName) onUpdate { SettingsRepo.withPetName(it, kind, clean) } }
        },
        placeholder = { Text(kind.defaultName) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
```

- **Show on Glyph:** `runtime.preview(PetPreviewAnimation.idFor(Base.HAPPY), 3000L)` → `runtime.preview(PetPreviewAnimation.idFor(kind, Base.HAPPY), 3000L)`.

Add these imports: `app.backlit.pet.PetArt`, `app.backlit.pet.PetKind`, `app.backlit.pet.Pose`, `androidx.compose.foundation.border`, and `androidx.compose.ui.Alignment` (if it isn't already there). Remove the `GhostArt` import if it's unused.

- [ ] **Step 3: Build and run the full suite**

Run: `source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK && .superpowers/runtests.sh -q 2>&1 | tail -1`
Expected: `BUILD_OK`, then `N tests, 0 failures, 0 errors`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/backlit/glyph/PetToyService.kt app/src/main/java/app/backlit/ui/PetScreen.kt
git commit -m "feat(pets): choose your pet in the PET tab; the toy draws the chosen pet

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: On-device checks and docs

**Files:**
- Modify: `docs/testing/device-checklist.md`, `README.md`

- [ ] **Step 1: Install.** Run `source .superpowers/env.sh && adb devices && ./gradlew -q :app:installDebug && adb logcat -c`. Expected: the device is listed and the install succeeds.

- [ ] **Step 2: Run the spec §5 device checklist with the user**
  1. PET tab: all six tiles are live, and tapping each switches the main preview, headline name and Glyph.
  2. For each new pet on the Glyph: long press gives hearts, a shake gives dizzy, 3 hard shakes give angry with steam, peekaboo works, and munch works while charging (frog tongue, robot cable).
  3. The signature shows in the PET tab when the pet is happy (wait, or use Show on Glyph), or comes up naturally on the toy.
  4. AOD idle and the charging fill for two different pets.
  5. Mood stays the same when switching pets, and each pet keeps its own name after renaming.
  6. An existing Boo name is unchanged after the update.

  Read `adb logcat -d -s BacklitPet:V AndroidRuntime:E` after each step.

- [ ] **Step 3: Update the docs.** Append this to `docs/testing/device-checklist.md`:

```markdown
## Multiple pets (Phone (3))

- [ ] PET tab chooser: six live tiles; switching updates preview, headline and Glyph
- [ ] Each new pet: pet hearts, dizzy, angry + steam, peekaboo, munch (frog tongue, robot cable)
- [ ] Signature moves: frog tongue + fly, penguin slide, axolotl bubbles, owl swivel, robot glitch
- [ ] AOD idle and charging fill for two pets
- [ ] Mood kept when switching; names are per pet; existing Boo name unchanged
```

In `README.md`, replace the first bullet of "### Backlit Pet (Glyph Toy)" with:

```markdown
- **Six pets to choose from** in the PET tab: Ghost (Boo), Frog (Ribbit), Penguin (Waddles), Axolotl (Lotl),
  Owl (Hoot) and Robot (Bolt). They share one mood; each has its own name and signature move (tongue and fly,
  belly-slide, bubbles, head swivel, glitch).
```

- [ ] **Step 4: Commit**

```bash
git add docs/testing/device-checklist.md README.md
git commit -m "docs: multiple pets device checklist and README

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
