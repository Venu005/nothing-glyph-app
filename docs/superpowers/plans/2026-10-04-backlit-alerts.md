# Backlit Alerts (Important Callers + Bluetooth) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Play a chosen animation on the Glyph Matrix while an important contact's call rings, or when a chosen Bluetooth device connects. Animations are 6 built-ins or Glyph Museum JSON imports, managed from a new ALERTS tab.

**Architecture:** Pure-Kotlin `anim/` (animations, built-ins, Glyph Museum format) and `alerts/` (rules, name matching, call-notification tracking, alert coordinator, storage). An in-process `AlertsRuntime` turns trigger events into an `ActiveAlert` `StateFlow`. A Backlit toy that is showing renders the alert itself. Otherwise `AppMatrixPlayer` draws it with `setAppMatrixFrame`. The triggers are a `NotificationListenerService` (calls) and an `ACL_CONNECTED` receiver (Bluetooth).

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, kotlinx-coroutines, kotlinx-serialization-json (new), JUnit 4, Nothing GlyphMatrix SDK.

**Spec:** `docs/superpowers/specs/2026-10-04-backlit-alerts-design.md`

## Global Constraints

- Branch `feat/backlit-alerts`. Package root `app.backlit`. In every fresh shell: `source .superpowers/env.sh`. Unit tests with a summary: `.superpowers/runtests.sh [gradle args]`.
- `anim/` and the pure files in `alerts/` (`Rules.kt`, `NameMatch.kt`, `AlertCoordinator.kt`, `CallNotificationTracker.kt`, `AnimationLibrary.kt`) import no `android.*`, `androidx.*` or `com.nothing.*`.
- Only `glyph/` imports `com.nothing.ketchum.*`.
- No network. Notification contents and caller names are never logged or stored, apart from the contact names the user picked.
- No `READ_CONTACTS`, `READ_CALL_LOG` or `READ_PHONE_STATE`. New permissions are only `BLUETOOTH_CONNECT` and the listener service's `BIND_NOTIFICATION_LISTENER_SERVICE`.
- Built-in ids: `builtin:heart`, `builtin:smiley`, `builtin:ring`, `builtin:burst`, `builtin:link`, `builtin:bounce`. Default for contacts: `builtin:heart`. Default for devices: `builtin:link`.
- Timing: call alert capped at 60 000 ms, device alert / preview 3 000 ms, device cooldown 30 000 ms, frame pacing 50 ms. All alert times use `SystemClock.elapsedRealtime()` (the pure classes take `nowMs`).
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Answered / declined / missed call** must end the alert (the notification is updated to non-incoming, or removed). Pinned by `CallNotificationTrackerTest.answeringEndsTheCall` and `removalEndsTheCall` (Task 4).
2. **Flaky Bluetooth reconnects** must not replay the animation within 30 s. Pinned by `AlertCoordinatorTest.deviceCooldownThirtySeconds` (Task 4).
3. **An import made for the other phone** must play correctly at the device's size. Pinned by `MuseumFormatTest.resamplingKeepsCentreDotCentred` (Task 3).
4. **A rule pointing at a deleted import** must fall back to the default and not crash. Pinned by `AnimationLibraryTest.missingImportFallsBack` (Task 5).
5. **A contact name with odd spacing, case or bidi marks** (as dialers often render it) must still match, and an empty title must never match. Pinned by `NameMatchTest` (Task 4).

---

## File Structure

```
gradle/libs.versions.toml, build.gradle.kts, app/build.gradle.kts   (+ kotlinx-serialization)
app/src/main/java/app/backlit/anim/
  GlyphAnimation.kt        interface + phase() + Bitmaps + plotD helper
  Heartbeat.kt  SmileyWink.kt  Ringing.kt  Burst.kt  Link.kt  Bounce.kt
  BuiltInAnimations.kt     registry + defaults
  MuseumFormat.kt          parse / toJson / ImportedAnimation / resample
app/src/main/java/app/backlit/alerts/
  Rules.kt                 ContactRule, DeviceRule, AnimIndexEntry, AlertKind, ActiveAlert, AlertConfig
  NameMatch.kt             contact-name normalisation
  CallNotificationTracker.kt   incoming-call notification → ringing/ended (pure)
  AlertCoordinator.kt      alert state machine (pure)
  AnimationLibrary.kt      imported files on disk + resolve(id) (pure, java.io.File)
  AlertStore.kt            DataStore persistence (JSON strings)
  AlertsRuntime.kt         process singleton: config, coordinator, bus, imports, player hook
  ToyPresence.kt           count of bound Backlit toys
  ImportHelper.kt          read a content Uri → import → user message
  CallAlertListener.kt     NotificationListenerService
  BluetoothAlertReceiver.kt  ACL_CONNECTED receiver
app/src/main/java/app/backlit/glyph/
  GlyphOutput.kt           (+ appMatrix mode)
  AppMatrixPlayer.kt       plays alerts when no Backlit toy is showing
  ClockToyService.kt, MusicToyService.kt   (+ render alerts, ToyPresence)
app/src/main/java/app/backlit/ui/
  AlertsScreen.kt          ALERTS tab, animation picker, mini previews
  HomeScreen.kt            (+ ALERTS tab)   MusicScreen.kt (Notice → internal)
app/src/main/java/app/backlit/MainActivity.kt   (+ share-to-import)
app/src/main/AndroidManifest.xml
docs/privacy-policy.md, docs/release/play-listing.md, docs/testing/device-checklist.md
tests: app/src/test/java/app/backlit/{anim,alerts}/
```

---

### Task 1: Animation core + Heartbeat, Smiley wink, Ringing

**Files:**
- Create: `app/src/main/java/app/backlit/anim/GlyphAnimation.kt`, `Heartbeat.kt`, `SmileyWink.kt`, `Ringing.kt` (all in `anim/`)
- Test: `app/src/test/java/app/backlit/anim/HandDrawnAnimationsTest.kt`

**Interfaces:**
- Consumes: `PixelGrid`, `Double.px()` (Part 1).
- Produces:
  - `interface GlyphAnimation { val id: String; val name: String; val loopMs: Long; fun frame(size: Int, tMs: Long): PixelGrid }`
  - `fun phase(tMs: Long, loopMs: Long): Double` (0 until 1)
  - `object Bitmaps { fun draw(g: PixelGrid, rows: List<String>, b: Int); fun points(g: PixelGrid, pts: List<Pair<Int, Int>>, b: Int, dx: Int = 0) }`
  - `fun PixelGrid.plotD(x: Double, y: Double, b: Int)`
  - `fun PixelGrid.ringBand(r: Double, b: Int, width: Double = 0.5)`
  - `object Heartbeat`, `object SmileyWink`, `object Ringing` : `GlyphAnimation`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class HandDrawnAnimationsTest {

    @Test
    fun heartBigBitmapOn25IsExactAndSymmetric() {
        val g = Heartbeat.frame(25, 0)                 // phase 0 → big heart
        for (x in 7..10) assertEquals(255, g[x, 6])    // "..XXXX...XXXX.." at x0 = 5, y0 = 6
        for (x in 14..17) assertEquals(255, g[x, 6])
        assertEquals(0, g[11, 6])
        assertEquals(255, g[12, 18])                   // tip
        for (y in 0 until 25) for (x in 0 until 25) assertEquals("x=$x y=$y", g[x, y], g[24 - x, y])
    }

    @Test
    fun heartRestsSmallAndDimmer() {
        val g = Heartbeat.frame(25, 600)               // phase 0.5 → small heart at 153
        assertEquals(153, g[12, 16])                   // tip of 11×9 at x0 = 7, y0 = 8
        assertEquals(0, g[12, 18])
        val s = Heartbeat.frame(13, 600)
        assertEquals(153, s[6, 8])                     // tip of 7×6 at x0 = 3, y0 = 3
        for (y in 0 until 13) for (x in 0 until 13) assertEquals(s[x, y], s[12 - x, y])
    }

    @Test
    fun heart13BigBitmap() {
        val g = Heartbeat.frame(13, 0)                 // 9×8 at x0 = 2, y0 = 2
        assertEquals(255, g[3, 2]); assertEquals(255, g[4, 2]); assertEquals(0, g[5, 2])
        assertEquals(255, g[6, 9])                     // tip
    }

    @Test
    fun smileyOutlineIsARoundDistanceBand() {
        for ((size, r) in listOf(25 to 8.5, 13 to 5.0)) {
            val g = SmileyWink.frame(size, 0)
            val c = (size - 1) / 2.0
            for (y in 0 until size) for (x in 0 until size) {
                if (g[x, y] == 140) assertTrue("size=$size x=$x y=$y", abs(hypot(x - c, y - c) - r) < 0.5)
            }
            for (y in 0 until size) for (x in 0 until size) assertEquals("size=$size", g[x, y], g[size - 1 - x, y])
        }
    }

    @Test
    fun smileyFacePixelsAndWink() {
        val open = SmileyWink.frame(25, 0)
        assertEquals(255, open[9, 9]); assertEquals(255, open[15, 10])
        assertEquals(255, open[12, 15]); assertEquals(0, open[8, 13])
        val wink = SmileyWink.frame(25, 1500)          // phase 0.625 → wink
        assertEquals(255, wink[13, 10]); assertEquals(255, wink[16, 10])
        assertEquals(0, wink[14, 9])
        assertEquals(255, wink[8, 13]); assertEquals(255, wink[16, 13])
        val small = SmileyWink.frame(13, 0)
        assertEquals(255, small[4, 4]); assertEquals(255, small[8, 4]); assertEquals(255, small[6, 8])
    }

    @Test
    fun ringing13IsHandPlaced() {
        val mid = Ringing.frame(13, 600)               // phase 0.5: phone still, inner arcs bright
        for ((x, y) in listOf(5 to 4, 6 to 4, 7 to 4, 5 to 6, 7 to 6, 6 to 8)) assertEquals(255, mid[x, y])
        assertEquals(255, mid[3, 6]); assertEquals(255, mid[9, 6])
        assertEquals(0, mid[1, 6])
        val late = Ringing.frame(13, 1000)             // phase 0.83: inner dim, outer on
        assertEquals(115, late[3, 6]); assertEquals(255, late[1, 6]); assertEquals(255, late[10, 8])
    }

    @Test
    fun everyFrameIsNonEmptyAtBothSizes() {
        for (a in listOf(Heartbeat, SmileyWink, Ringing)) for (size in listOf(25, 13)) {
            var t = 0L
            while (t < a.loopMs) { assertTrue("${a.id} $size $t", a.frame(size, t).litCount() > 0); t += 37 }
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
.superpowers/runtests.sh --tests "app.backlit.anim.HandDrawnAnimationsTest" 2>&1 | grep -E "^e:" | head -2
```

Expected: `Unresolved reference 'Heartbeat'`.

- [ ] **Step 3: Implement**

`app/src/main/java/app/backlit/anim/GlyphAnimation.kt`:

```kotlin
package app.backlit.anim

import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.abs
import kotlin.math.hypot

/** A looping matrix animation that can draw itself at 25×25 (Phone (3)) or 13×13 (Phone (4a) Pro). */
interface GlyphAnimation {
    val id: String
    val name: String
    val loopMs: Long
    fun frame(size: Int, tMs: Long): PixelGrid
}

/** Position within the loop, 0 until 1. */
fun phase(tMs: Long, loopMs: Long): Double = (((tMs % loopMs) + loopMs) % loopMs).toDouble() / loopMs

fun PixelGrid.plotD(x: Double, y: Double, b: Int) = plot(x.px(), y.px(), b)

/** Every pixel whose centre lies within [width] of radius [r] — a clean, round outline. */
fun PixelGrid.ringBand(r: Double, b: Int, width: Double = 0.5) {
    for (y in 0 until size) for (x in 0 until size) {
        if (abs(hypot(x - center, y - center) - r) < width) plot(x, y, b)
    }
}

object Bitmaps {
    /** Draws 'X' cells of [rows], centred on the grid. */
    fun draw(g: PixelGrid, rows: List<String>, b: Int) {
        val x0 = (g.size - rows[0].length) / 2
        val y0 = (g.size - rows.size) / 2
        rows.forEachIndexed { r, row -> row.forEachIndexed { i, ch -> if (ch == 'X') g.plot(x0 + i, y0 + r, b) } }
    }

    fun points(g: PixelGrid, pts: List<Pair<Int, Int>>, b: Int, dx: Int = 0) =
        pts.forEach { (x, y) -> g.plot(x + dx, y, b) }
}
```

`app/src/main/java/app/backlit/anim/Heartbeat.kt`:

```kotlin
package app.backlit.anim

import app.backlit.render.PixelGrid

object Heartbeat : GlyphAnimation {
    override val id = "builtin:heart"
    override val name = "Heartbeat"
    override val loopMs = 1200L

    private val SMALL_25 = listOf(".XXX...XXX.", "XXXXX.XXXXX", "XXXXXXXXXXX", "XXXXXXXXXXX", ".XXXXXXXXX.", "..XXXXXXX..", "...XXXXX...", "....XXX....", ".....X.....")
    private val BIG_25 = listOf(
        "..XXXX...XXXX..", ".XXXXXX.XXXXXX.", "XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXX",
        ".XXXXXXXXXXXXX.", "..XXXXXXXXXXX..", "...XXXXXXXXX...", "....XXXXXXX....", ".....XXXXX.....", "......XXX......", ".......X.......",
    )
    private val SMALL_13 = listOf(".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X...")
    private val BIG_13 = listOf(".XX...XX.", "XXXX.XXXX", "XXXXXXXXX", "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...", "....X....")

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val p = phase(tMs, loopMs)
        val big = p < 0.12 || (p >= 0.24 && p < 0.36)
        val rows = if (size >= 25) (if (big) BIG_25 else SMALL_25) else (if (big) BIG_13 else SMALL_13)
        Bitmaps.draw(g, rows, if (big) 255 else 153)
        return g
    }
}
```

`app/src/main/java/app/backlit/anim/SmileyWink.kt`:

```kotlin
package app.backlit.anim

import app.backlit.render.PixelGrid

object SmileyWink : GlyphAnimation {
    override val id = "builtin:smiley"
    override val name = "Smiley wink"
    override val loopMs = 2400L

    private class Face(
        val r: Double, val eyeL: List<Pair<Int, Int>>, val eyeR: List<Pair<Int, Int>>, val wink: List<Pair<Int, Int>>,
        val smile: List<Pair<Int, Int>>, val wide: List<Pair<Int, Int>>,
    )

    private val F25 = Face(
        r = 8.5,
        eyeL = listOf(9 to 9, 10 to 9, 9 to 10, 10 to 10),
        eyeR = listOf(14 to 9, 15 to 9, 14 to 10, 15 to 10),
        wink = listOf(13 to 10, 14 to 10, 15 to 10, 16 to 10),
        smile = listOf(9 to 14, 10 to 15, 11 to 15, 12 to 15, 13 to 15, 14 to 15, 15 to 14),
        wide = listOf(8 to 13, 9 to 14, 10 to 15, 11 to 15, 12 to 15, 13 to 15, 14 to 15, 15 to 14, 16 to 13),
    )
    private val F13 = Face(
        r = 5.0,
        eyeL = listOf(4 to 4), eyeR = listOf(8 to 4), wink = listOf(7 to 5, 8 to 5, 9 to 5),
        smile = listOf(4 to 7, 5 to 8, 6 to 8, 7 to 8, 8 to 7),
        wide = listOf(3 to 6, 4 to 7, 5 to 8, 6 to 8, 7 to 8, 8 to 7, 9 to 6),
    )

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val f = if (size >= 25) F25 else F13
        val p = phase(tMs, loopMs)
        val winking = p >= 0.55 && p < 0.80
        g.ringBand(f.r, 140)
        Bitmaps.points(g, f.eyeL, 255)
        Bitmaps.points(g, if (winking) f.wink else f.eyeR, 255)
        Bitmaps.points(g, if (winking) f.wide else f.smile, 255)
        return g
    }
}
```

`app/src/main/java/app/backlit/anim/Ringing.kt`:

```kotlin
package app.backlit.anim

import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object Ringing : GlyphAnimation {
    override val id = "builtin:ring"
    override val name = "Ringing"
    override val loopMs = 1200L

    private val PHONE_13 = listOf(5 to 4, 6 to 4, 7 to 4, 5 to 5, 7 to 5, 5 to 6, 7 to 6, 5 to 7, 7 to 7, 5 to 8, 6 to 8, 7 to 8)
    private val INNER_13 = listOf(3 to 5, 3 to 6, 3 to 7, 9 to 5, 9 to 6, 9 to 7)
    private val OUTER_13 = listOf(2 to 4, 1 to 5, 1 to 6, 1 to 7, 2 to 8, 10 to 4, 11 to 5, 11 to 6, 11 to 7, 10 to 8)

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val p = phase(tMs, loopMs)
        if (size < 25) {
            val shake = if (p < 0.33) (if ((tMs / 60) % 2 == 1L) 1 else -1) else 0
            Bitmaps.points(g, PHONE_13, 255, dx = shake)
            if (p >= 0.33) Bitmaps.points(g, INNER_13, if (p < 0.66) 255 else 115)
            if (p >= 0.66) Bitmaps.points(g, OUTER_13, 255)
            return g
        }
        val c = g.center.px()
        val shake = if (p < 0.5) sin(tMs / 40.0).roundToInt() else 0
        for (y in -4..4) for (x in -2..2) if (y == -4 || y == 4 || x == -2 || x == 2) g.plot(c + x + shake, c + y, 255)
        for (k in 0 until 3) {
            val r = 4 + ((p + k / 3.0) % 1.0) * 8
            val b = ((1 - (r - 4) / 9) * 255).roundToInt()
            var a = -0.6
            while (a <= 0.6) {
                g.plotD(c + cos(a) * r, c + sin(a) * r, b)
                g.plotD(c - cos(a) * r, c + sin(a) * r, b)
                a += 0.06
            }
        }
        return g
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
.superpowers/runtests.sh --tests "app.backlit.anim.HandDrawnAnimationsTest" 2>&1 | tail -1
```

Expected: `7 tests, 0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/anim/ app/src/test/java/app/backlit/anim/
git commit -m "feat: animation core with hand-drawn heartbeat, smiley wink and ringing

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Burst, Link, Bounce + registry

**Files:**
- Create: `app/src/main/java/app/backlit/anim/Burst.kt`, `Link.kt`, `Bounce.kt`, `BuiltInAnimations.kt`
- Test: `app/src/test/java/app/backlit/anim/MotionAnimationsTest.kt`

**Interfaces:**
- Consumes: Task 1 (`GlyphAnimation`, `phase`, `plotD`, `ringBand`).
- Produces: `object Burst`, `object Link`, `object Bounce`; `object BuiltInAnimations { val all: List<GlyphAnimation>; const val DEFAULT_CONTACT = "builtin:heart"; const val DEFAULT_DEVICE = "builtin:link"; fun byId(id: String): GlyphAnimation? }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.anim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionAnimationsTest {

    @Test
    fun linkDotsAreMirrorSymmetricAndMeetAtTheCentre() {
        for (size in listOf(25, 13)) {
            var t = 0L
            while (t < Link.loopMs) {
                val g = Link.frame(size, t)
                for (y in 0 until size) for (x in 0 until size) assertEquals("size=$size t=$t", g[x, y], g[size - 1 - x, y])
                t += 50
            }
        }
        val met13 = Link.frame(13, 800)          // phase 0.5 → both dots on x = 6
        assertEquals(255, met13[6, 6])
        assertEquals(0, met13[5, 6]); assertEquals(0, met13[7, 6])
        val met25 = Link.frame(25, 800)          // dots centred on 11 and 13, overlapping column 12
        assertEquals(255, met25[12, 12]); assertEquals(255, met25[10, 12]); assertEquals(255, met25[14, 12])
    }

    @Test
    fun linkStartsAtTheEdges() {
        val g = Link.frame(13, 0)
        assertEquals(255, g[1, 6]); assertEquals(255, g[11, 6])
    }

    @Test
    fun burstFlashesTheCentreFirst() {
        val big = Burst.frame(25, 0)
        for (x in 11..13) for (y in 11..13) assertEquals(255, big[x, y])
        assertEquals(255, Burst.frame(13, 0)[6, 6])
        assertEquals(0, Burst.frame(13, 500)[6, 6])
    }

    @Test
    fun bounceHasAGroundLineAndABall() {
        for ((size, ground) in listOf(25 to 21, 13 to 11)) {
            val g = Bounce.frame(size, 300)
            assertEquals(77, g[size / 2, ground])
            assertTrue("ball above ground", (0 until ground).any { y -> (0 until size).any { x -> g[x, y] == 255 } })
        }
    }

    @Test
    fun everyFrameIsNonEmpty() {
        for (a in listOf(Burst, Link, Bounce)) for (size in listOf(25, 13)) {
            var t = 0L
            while (t < a.loopMs) { assertTrue("${a.id} $size $t", a.frame(size, t).litCount() > 0); t += 37 }
        }
    }

    @Test
    fun registry() {
        assertEquals(
            listOf("builtin:heart", "builtin:smiley", "builtin:ring", "builtin:burst", "builtin:link", "builtin:bounce"),
            BuiltInAnimations.all.map { it.id },
        )
        assertEquals(Link, BuiltInAnimations.byId(BuiltInAnimations.DEFAULT_DEVICE))
        assertEquals(Heartbeat, BuiltInAnimations.byId(BuiltInAnimations.DEFAULT_CONTACT))
        assertNull(BuiltInAnimations.byId("import:nope"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
.superpowers/runtests.sh --tests "app.backlit.anim.MotionAnimationsTest" 2>&1 | grep -E "^e:" | head -2
```

Expected: unresolved `Link` / `Burst` / `Bounce` / `BuiltInAnimations`.

- [ ] **Step 3: Implement**

`app/src/main/java/app/backlit/anim/Burst.kt`:

```kotlin
package app.backlit.anim

import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object Burst : GlyphAnimation {
    override val id = "builtin:burst"
    override val name = "Burst"
    override val loopMs = 1100L

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val c = g.center.px()
        val p = phase(tMs, loopMs)
        val reach = if (big) 12.0 else 6.0
        val rays = if (big) 16 else 8
        val trail = if (big) 4 else 2
        for (k in 0 until rays) {
            val a = k * 2 * PI / rays
            for (tr in 0 until trail) {
                val r = p * reach - tr * 0.8
                if (r > 0) g.plotD(c + cos(a) * r, c + sin(a) * r, ((1 - p) * (1 - tr / 4.0) * 255).roundToInt())
            }
        }
        if (p < 0.15) {
            val s = if (big) 1 else 0
            for (dx in -s..s) for (dy in -s..s) g.plot(c + dx, c + dy, 255)
        }
        return g
    }
}
```

`app/src/main/java/app/backlit/anim/Link.kt`:

```kotlin
package app.backlit.anim

import app.backlit.render.PixelGrid
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object Link : GlyphAnimation {
    override val id = "builtin:link"
    override val name = "Link"
    override val loopMs = 1600L

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val c = (size - 1) / 2
        val p = phase(tMs, loopMs)
        val e = 1 - (1 - min(1.0, p / 0.5)).pow(3)
        val edge = if (big) 2 else 1
        val meet = if (big) c - 1 else c
        val left = edge + floor(e * (meet - edge) + 1e-9).toInt()
        val right = (size - 1) - left                    // exact mirror: the dots always meet at the centre
        val s = if (big) 1 else 0
        for (dx in -s..s) for (dy in -s..s) {
            g.plot(left + dx, c + dy, 255)
            g.plot(right + dx, c + dy, 255)
        }
        if (p >= 0.5 && p < 0.8) {
            val q = (p - 0.5) / 0.3
            g.ringBand(q * (if (big) 10.0 else 5.0), ((1 - q) * 255).roundToInt(), width = 0.6)
        }
        return g
    }
}
```

`app/src/main/java/app/backlit/anim/Bounce.kt`:

```kotlin
package app.backlit.anim

import app.backlit.render.PixelGrid
import kotlin.math.floor
import kotlin.math.min

object Bounce : GlyphAnimation {
    override val id = "builtin:bounce"
    override val name = "Bounce"
    override val loopMs = 2200L

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val p = phase(tMs, loopMs)
        val tt = p * 3
        val k = floor(tt).toInt()
        val f = tt - k
        val heights = if (big) doubleArrayOf(9.0, 5.5, 2.5) else doubleArrayOf(5.0, 3.0, 1.5)
        val h = heights[min(k, 2)]
        val ground = if (big) 21 else 11
        val y = ground - (if (big) 2 else 1) - h * 4 * f * (1 - f)
        val x = (if (big) 5.0 else 2.0) + p * (if (big) 15 else 8)
        val squash = if (f < 0.08 || f > 0.92) 1 else 0
        for (gx in 0 until size) g.plot(gx, ground, 77)
        if (big) {
            for (dx in (-1 - squash)..(1 + squash)) for (dy in (-1 + squash)..1) g.plotD(x + dx, y + dy, 255)
        } else {
            for (dx in 0..(1 + squash)) for (dy in squash..1) g.plotD(x + dx - 0.5, y + dy - 0.5, 255)
        }
        return g
    }
}
```

`app/src/main/java/app/backlit/anim/BuiltInAnimations.kt`:

```kotlin
package app.backlit.anim

object BuiltInAnimations {
    const val DEFAULT_CONTACT = "builtin:heart"
    const val DEFAULT_DEVICE = "builtin:link"

    val all: List<GlyphAnimation> = listOf(Heartbeat, SmileyWink, Ringing, Burst, Link, Bounce)

    fun byId(id: String): GlyphAnimation? = all.firstOrNull { it.id == id }
}
```

- [ ] **Step 4: Run anim tests**

```bash
.superpowers/runtests.sh --tests "app.backlit.anim.*" 2>&1 | tail -1
```

Expected: `13 tests, 0 failures, 0 errors`. If the Burst centre assertion at t = 500 fails because a ray crosses the centre pixel, check the trail maths against the spec rather than loosening the test.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/anim/ app/src/test/java/app/backlit/anim/
git commit -m "feat: burst, link and bounce animations plus built-in registry

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Glyph Museum import format

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
- Create: `app/src/main/java/app/backlit/anim/MuseumFormat.kt`
- Test: `app/src/test/java/app/backlit/anim/MuseumFormatTest.kt`

**Interfaces:**
- Consumes: `GlyphAnimation`, `PixelGrid`.
- Produces:
  - `class ImportedAnimation(id: String, name: String, val sourceSize: Int, val frames25: List<PixelGrid>, val frames13: List<PixelGrid>, val durations: List<Long>) : GlyphAnimation`
  - `object MuseumFormat { sealed interface Result { data class Ok(val animation: ImportedAnimation) : Result; data object Invalid : Result }; fun parse(json: String, id: String, name: String): Result; fun toJson(anim: ImportedAnimation): String; fun resample(src: PixelGrid, size: Int): PixelGrid }`

- [ ] **Step 1: Add kotlinx-serialization**

In `gradle/libs.versions.toml` add under `[versions]`:

```toml
serialization = "1.9.0"
```

under `[libraries]`:

```toml
serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
```

and under `[plugins]`:

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

In the root `build.gradle.kts` add inside `plugins { }`:

```kotlin
    alias(libs.plugins.kotlin.serialization) apply false
```

In `app/build.gradle.kts`, add inside `plugins { }`:

```kotlin
    alias(libs.plugins.kotlin.serialization)
```

and inside `dependencies { }`:

```kotlin
    implementation(libs.serialization.json)
```

Verify:

```bash
source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK
```

Expected: `BUILD_OK`. If `1.9.0` doesn't resolve, use the newest 1.x release from https://github.com/Kotlin/kotlinx.serialization/releases and ledger the change.

- [ ] **Step 2: Write the failing test**

```kotlin
package app.backlit.anim

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuseumFormatTest {

    private fun leds(size: Int) = PixelGrid.ledCount(size)
    private fun file(v: Int, frames: List<Pair<Int?, List<Int>>>): String =
        """{"v":$v,"frames":[""" + frames.joinToString(",") { (d, p) ->
            (if (d != null) """{"d":$d,"p":[""" else """{"p":[""") + p.joinToString(",") + "]}"
        } + "]}"

    private fun ok(r: MuseumFormat.Result) = (r as MuseumFormat.Result.Ok).animation

    @Test
    fun parsesPhone3FileWithDurations() {
        val first = List(leds(25)) { if (it == 0) 255 else 0 }
        val a = ok(MuseumFormat.parse(file(1, listOf(200 to first, null to List(leds(25)) { 0 })), "import:a", "A"))
        assertEquals(25, a.sourceSize)
        assertEquals(255, a.frames25[0][9, 0])          // first LED of 25×25 is (9, 0)
        assertEquals(listOf(200L, 100L), a.durations)
        assertEquals(300L, a.loopMs)
        assertEquals(0, a.frame(25, 250)[9, 0])         // t = 250 → second frame
    }

    @Test
    fun parsesPhone4aProFile() {
        val first = List(leds(13)) { if (it == 0) 200 else 0 }
        val a = ok(MuseumFormat.parse(file(4, listOf(null to first)), "import:b", "B"))
        assertEquals(13, a.sourceSize)
        assertEquals(200, a.frames13[0][4, 0])           // first LED of 13×13 is (4, 0)
    }

    @Test
    fun clampsValuesAndDurations() {
        val p = List(leds(25)) { when (it) { 0 -> 300; 1 -> -5; else -> 0 } }
        val a = ok(MuseumFormat.parse(file(1, listOf(1 to p, 99999 to p)), "import:c", "C"))
        assertEquals(255, a.frames25[0][9, 0])
        assertEquals(0, a.frames25[0][10, 0])
        assertEquals(listOf(20L, 5000L), a.durations)
    }

    @Test
    fun rejectsBadFiles() {
        val good = List(leds(25)) { 0 }
        for (bad in listOf(
            "hello",
            file(2, listOf(null to good)),
            file(1, listOf(null to List(leds(25) - 1) { 0 })),
            """{"v":1,"frames":[]}""",
            file(1, List(601) { null to good }),
        )) assertEquals(bad.take(30), MuseumFormat.Result.Invalid, MuseumFormat.parse(bad, "import:x", "X"))
    }

    @Test
    fun roundTripsThroughJson() {
        val p = List(leds(13)) { it % 256 }
        val a = ok(MuseumFormat.parse(file(4, listOf(150 to p)), "import:d", "D"))
        val b = ok(MuseumFormat.parse(MuseumFormat.toJson(a), "import:d", "D"))
        assertEquals(a.frames13, b.frames13)
        assertEquals(a.durations, b.durations)
        assertEquals(13, b.sourceSize)
    }

    @Test
    fun resamplingKeepsCentreDotCentred() {
        val big = PixelGrid(25).apply { plot(12, 12, 255) }
        val small = MuseumFormat.resample(big, 13)
        assertEquals(255, small[6, 6]); assertEquals(0, small[5, 6])
        val up = MuseumFormat.resample(PixelGrid(13).apply { plot(6, 6, 255) }, 25)
        assertEquals(255, up[12, 12]); assertEquals(0, up[11, 12])
    }

    @Test
    fun resamplingAFullFrameStaysBright() {
        val full = PixelGrid(13).apply { for (y in 0 until 13) for (x in 0 until 13) plot(x, y, 255) }
        val up = MuseumFormat.resample(full, 25)
        assertTrue(up[12, 12] == 255 && up[12, 1] > 0)
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

```bash
.superpowers/runtests.sh --tests "app.backlit.anim.MuseumFormatTest" 2>&1 | grep -E "^e:" | head -2
```

Expected: unresolved `MuseumFormat`.

- [ ] **Step 4: Implement**

`app/src/main/java/app/backlit/anim/MuseumFormat.kt`:

```kotlin
package app.backlit.anim

import app.backlit.render.PixelGrid
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.floor
import kotlin.math.roundToInt

/** A frame-based animation imported from Glyph Museum, prepared for both grid sizes. */
class ImportedAnimation(
    override val id: String,
    override val name: String,
    val sourceSize: Int,
    val frames25: List<PixelGrid>,
    val frames13: List<PixelGrid>,
    val durations: List<Long>,
) : GlyphAnimation {
    override val loopMs: Long = durations.sum().coerceAtLeast(1)

    override fun frame(size: Int, tMs: Long): PixelGrid {
        val frames = if (size >= 25) frames25 else frames13
        var t = ((tMs % loopMs) + loopMs) % loopMs
        for (i in durations.indices) {
            if (t < durations[i]) return frames[i]
            t -= durations[i]
        }
        return frames.last()
    }
}

/**
 * Glyph Museum / Glyph Matrix Editor JSON: {"v":1|4,"frames":[{"d":ms?,"p":[0..255 per LED]}]}.
 * LEDs are listed row by row, only positions inside the round mask (same as PixelGrid.hasLed).
 * Format learned by reading the editor's source; no code copied.
 */
object MuseumFormat {
    sealed interface Result {
        data class Ok(val animation: ImportedAnimation) : Result
        data object Invalid : Result
    }

    @Serializable private data class FileJson(val v: Int, val frames: List<FrameJson>)
    @Serializable private data class FrameJson(val d: Int? = null, val p: List<Int>)

    private val json = Json { ignoreUnknownKeys = true }
    private const val MAX_FRAMES = 600

    fun parse(text: String, id: String, name: String): Result {
        val file = runCatching { json.decodeFromString<FileJson>(text) }.getOrNull() ?: return Result.Invalid
        val size = when (file.v) { 1 -> 25; 4 -> 13; else -> return Result.Invalid }
        if (file.frames.isEmpty() || file.frames.size > MAX_FRAMES) return Result.Invalid
        val count = PixelGrid.ledCount(size)
        if (file.frames.any { it.p.size != count }) return Result.Invalid
        val source = file.frames.map { unpack(it.p, size) }
        val durations = file.frames.map { (it.d ?: 100).coerceIn(20, 5000).toLong() }
        val other = if (size == 25) 13 else 25
        val converted = source.map { resample(it, other) }
        return Result.Ok(
            ImportedAnimation(
                id, name, size,
                frames25 = if (size == 25) source else converted,
                frames13 = if (size == 13) source else converted,
                durations = durations,
            ),
        )
    }

    fun toJson(anim: ImportedAnimation): String {
        val frames = if (anim.sourceSize == 25) anim.frames25 else anim.frames13
        val file = FileJson(
            v = if (anim.sourceSize == 25) 1 else 4,
            frames = frames.mapIndexed { i, g -> FrameJson(anim.durations[i].toInt(), pack(g)) },
        )
        return json.encodeToString(FileJson.serializer(), file)
    }

    private fun unpack(values: List<Int>, size: Int): PixelGrid {
        val g = PixelGrid(size)
        var i = 0
        for (y in 0 until size) for (x in 0 until size) {
            if (g.hasLed(x, y)) g.put(x, y, values[i++].coerceIn(0, 255))
        }
        return g
    }

    private fun pack(g: PixelGrid): List<Int> {
        val out = ArrayList<Int>(PixelGrid.ledCount(g.size))
        for (y in 0 until g.size) for (x in 0 until g.size) if (g.hasLed(x, y)) out += g[x, y]
        return out
    }

    /** Area-average resampling: each target LED averages the source pixels whose centres fall in its box. */
    fun resample(src: PixelGrid, size: Int): PixelGrid {
        val n = src.size
        val g = PixelGrid(size)
        val scale = n.toDouble() / size
        for (ty in 0 until size) for (tx in 0 until size) {
            if (!g.hasLed(tx, ty)) continue
            var sum = 0
            var count = 0
            for (sy in 0 until n) for (sx in 0 until n) {
                val cx = sx + 0.5
                val cy = sy + 0.5
                if (cx >= tx * scale && cx < (tx + 1) * scale && cy >= ty * scale && cy < (ty + 1) * scale) {
                    sum += src[sx, sy]; count++
                }
            }
            val v = if (count > 0) (sum.toDouble() / count).roundToInt()
            else src[floor((tx + 0.5) * scale).toInt(), floor((ty + 0.5) * scale).toInt()]
            g.put(tx, ty, v)
        }
        return g
    }
}
```

- [ ] **Step 5: Run it to verify it passes**

```bash
.superpowers/runtests.sh --tests "app.backlit.anim.MuseumFormatTest" 2>&1 | tail -1
```

Expected: `7 tests, 0 failures, 0 errors`.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/java/app/backlit/anim/MuseumFormat.kt app/src/test/java/app/backlit/anim/MuseumFormatTest.kt
git commit -m "feat: Glyph Museum JSON import with validation and 25/13 resampling

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Rules, name matching, call tracking, alert coordinator

**Files:**
- Create: `app/src/main/java/app/backlit/alerts/Rules.kt`, `NameMatch.kt`, `CallNotificationTracker.kt`, `AlertCoordinator.kt`
- Test: `app/src/test/java/app/backlit/alerts/NameMatchTest.kt`, `CallNotificationTrackerTest.kt`, `AlertCoordinatorTest.kt`

**Interfaces:**
- Consumes: nothing Android.
- Produces:
  - `@Serializable data class ContactRule(val name: String, val animationId: String)`
  - `@Serializable data class DeviceRule(val address: String, val name: String, val animationId: String)`
  - `@Serializable data class AnimIndexEntry(val id: String, val name: String, val sourceV: Int)`
  - `enum class AlertKind { CALL, DEVICE }`
  - `data class ActiveAlert(val animationId: String, val kind: AlertKind, val startedAt: Long, val endsAt: Long)`
  - `data class AlertConfig(val contacts: List<ContactRule> = emptyList(), val devices: List<DeviceRule> = emptyList(), val imports: List<AnimIndexEntry> = emptyList())`
  - `object NameMatch { fun normalize(s: String): String; fun matches(a: String, b: String): Boolean }`
  - `class CallNotificationTracker { sealed interface Event { data class Ringing(val callerName: String) : Event; data object Ended : Event }; fun onPosted(key: String, isCall: Boolean, incoming: Boolean, title: String?): Event?; fun onRemoved(key: String): Event? }`
  - `class AlertCoordinator(contacts: () -> List<ContactRule>, devices: () -> List<DeviceRule>) { val active: ActiveAlert?; fun onCallRinging(name: String, nowMs: Long); fun onCallEnded(); fun onDeviceConnected(address: String, nowMs: Long); fun preview(animationId: String, nowMs: Long); fun tick(nowMs: Long) }`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/backlit/alerts/NameMatchTest.kt`:

```kotlin
package app.backlit.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatchTest {
    @Test
    fun matchesIgnoringCaseSpacingAndBidiMarks() {
        assertTrue(NameMatch.matches("Mom", "mom"))
        assertTrue(NameMatch.matches("  Ravi   Kumar ", "Ravi Kumar"))
        assertTrue(NameMatch.matches("‪Mom‬", "Mom"))
        assertTrue(NameMatch.matches("⁨Dad⁩", "dad"))
    }

    @Test
    fun noPartialOrEmptyMatches() {
        assertFalse(NameMatch.matches("Mom", "Mom Work"))
        assertFalse(NameMatch.matches("", ""))
        assertFalse(NameMatch.matches("   ", "Mom"))
        assertFalse(NameMatch.matches("+91 98765 43210", "Mom"))
    }
}
```

`app/src/test/java/app/backlit/alerts/CallNotificationTrackerTest.kt`:

```kotlin
package app.backlit.alerts

import app.backlit.alerts.CallNotificationTracker.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallNotificationTrackerTest {

    @Test
    fun incomingCallStartsRinging() {
        val t = CallNotificationTracker()
        assertEquals(Event.Ringing("Mom"), t.onPosted("k1", isCall = true, incoming = true, title = "Mom"))
    }

    @Test
    fun otherNotificationsAreIgnored() {
        val t = CallNotificationTracker()
        assertNull(t.onPosted("chat", isCall = false, incoming = false, title = "Hi"))
        assertNull(t.onPosted("call", isCall = true, incoming = true, title = null))
        assertNull(t.onRemoved("chat"))
    }

    @Test
    fun answeringEndsTheCall() {
        val t = CallNotificationTracker()
        t.onPosted("k1", true, true, "Mom")
        assertEquals(Event.Ended, t.onPosted("k1", isCall = true, incoming = false, title = "Mom"))   // now "ongoing"
        assertNull(t.onPosted("k1", isCall = true, incoming = false, title = "Mom"))
    }

    @Test
    fun removalEndsTheCall() {
        val t = CallNotificationTracker()
        t.onPosted("k1", true, true, "Mom")
        assertNull(t.onRemoved("other"))
        assertEquals(Event.Ended, t.onRemoved("k1"))
        assertNull(t.onRemoved("k1"))
    }

    @Test
    fun repeatedIncomingUpdatesDoNotRestart() {
        val t = CallNotificationTracker()
        t.onPosted("k1", true, true, "Mom")
        assertNull(t.onPosted("k1", true, true, "Mom"))
    }
}
```

`app/src/test/java/app/backlit/alerts/AlertCoordinatorTest.kt`:

```kotlin
package app.backlit.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertCoordinatorTest {

    private val contacts = listOf(ContactRule("Mom", "builtin:heart"))
    private val devices = listOf(DeviceRule("AA:BB:CC:DD:EE:FF", "Buds", "builtin:link"))
    private fun coordinator() = AlertCoordinator({ contacts }, { devices })

    @Test
    fun importantCallerLoopsUntilEnded() {
        val c = coordinator()
        c.onCallRinging("mom", 1_000)
        assertEquals(ActiveAlert("builtin:heart", AlertKind.CALL, 1_000, 61_000), c.active)
        c.tick(30_000)
        assertEquals(AlertKind.CALL, c.active?.kind)
        c.onCallEnded()
        assertNull(c.active)
    }

    @Test
    fun callIsCappedAtSixtySeconds() {
        val c = coordinator()
        c.onCallRinging("Mom", 0)
        c.tick(59_999)
        assertEquals(AlertKind.CALL, c.active?.kind)
        c.tick(60_000)
        assertNull(c.active)
    }

    @Test
    fun otherCallersDoNothing() {
        val c = coordinator()
        c.onCallRinging("Unknown", 0)
        assertNull(c.active)
    }

    @Test
    fun deviceAlertLastsThreeSeconds() {
        val c = coordinator()
        c.onDeviceConnected("aa:bb:cc:dd:ee:ff", 10_000)
        assertEquals(ActiveAlert("builtin:link", AlertKind.DEVICE, 10_000, 13_000), c.active)
        c.tick(12_999); assertEquals(AlertKind.DEVICE, c.active?.kind)
        c.tick(13_000); assertNull(c.active)
    }

    @Test
    fun unknownDeviceDoesNothing() {
        val c = coordinator()
        c.onDeviceConnected("11:22:33:44:55:66", 0)
        assertNull(c.active)
    }

    @Test
    fun deviceCooldownThirtySeconds() {
        val c = coordinator()
        c.onDeviceConnected("AA:BB:CC:DD:EE:FF", 0)
        c.tick(5_000)
        c.onDeviceConnected("AA:BB:CC:DD:EE:FF", 20_000)
        assertNull(c.active)
        c.onDeviceConnected("AA:BB:CC:DD:EE:FF", 30_000)
        assertEquals(AlertKind.DEVICE, c.active?.kind)
    }

    @Test
    fun callBeatsDevice() {
        val c = coordinator()
        c.onCallRinging("Mom", 0)
        c.onDeviceConnected("AA:BB:CC:DD:EE:FF", 1_000)
        assertEquals(AlertKind.CALL, c.active?.kind)
    }

    @Test
    fun callReplacesDevice() {
        val c = coordinator()
        c.onDeviceConnected("AA:BB:CC:DD:EE:FF", 0)
        c.onCallRinging("Mom", 500)
        assertEquals(AlertKind.CALL, c.active?.kind)
    }

    @Test
    fun previewPlaysForThreeSecondsButNotOverACall() {
        val c = coordinator()
        c.preview("builtin:bounce", 0)
        assertEquals(ActiveAlert("builtin:bounce", AlertKind.DEVICE, 0, 3_000), c.active)
        c.onCallRinging("Mom", 100)
        c.preview("builtin:burst", 200)
        assertEquals(AlertKind.CALL, c.active?.kind)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
.superpowers/runtests.sh --tests "app.backlit.alerts.*" 2>&1 | grep -E "^e:" | head -3
```

Expected: unresolved `NameMatch`, `CallNotificationTracker`, `AlertCoordinator`.

- [ ] **Step 3: Implement**

`app/src/main/java/app/backlit/alerts/Rules.kt`:

```kotlin
package app.backlit.alerts

import kotlinx.serialization.Serializable

@Serializable
data class ContactRule(val name: String, val animationId: String)

@Serializable
data class DeviceRule(val address: String, val name: String, val animationId: String)

/** An imported animation stored on disk; sourceV is the Glyph Museum "v" (1 = Phone (3), 4 = (4a) Pro). */
@Serializable
data class AnimIndexEntry(val id: String, val name: String, val sourceV: Int)

enum class AlertKind { CALL, DEVICE }

data class ActiveAlert(val animationId: String, val kind: AlertKind, val startedAt: Long, val endsAt: Long)

data class AlertConfig(
    val contacts: List<ContactRule> = emptyList(),
    val devices: List<DeviceRule> = emptyList(),
    val imports: List<AnimIndexEntry> = emptyList(),
)
```

`app/src/main/java/app/backlit/alerts/NameMatch.kt`:

```kotlin
package app.backlit.alerts

/** Matches the caller name a dialer shows against a picked contact name. Exact after normalising. */
object NameMatch {
    private val BIDI = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
    private val SPACES = Regex("\\s+")

    fun normalize(s: String): String = s.replace(BIDI, "").trim().replace(SPACES, " ").lowercase()

    fun matches(a: String, b: String): Boolean {
        val na = normalize(a)
        return na.isNotEmpty() && na == normalize(b)
    }
}
```

`app/src/main/java/app/backlit/alerts/CallNotificationTracker.kt`:

```kotlin
package app.backlit.alerts

/**
 * Turns the Phone app's call notifications into ringing / ended events.
 * Ringing = a CALL notification marked incoming. Ended = the same notification becomes non-incoming
 * (answered) or is removed (declined / missed / hung up).
 */
class CallNotificationTracker {
    sealed interface Event {
        data class Ringing(val callerName: String) : Event
        data object Ended : Event
    }

    private var ringingKey: String? = null

    fun onPosted(key: String, isCall: Boolean, incoming: Boolean, title: String?): Event? {
        if (!isCall) return null
        if (incoming) {
            if (key == ringingKey) return null
            val name = title?.takeIf { it.isNotBlank() } ?: return null
            ringingKey = key
            return Event.Ringing(name)
        }
        if (key == ringingKey) {
            ringingKey = null
            return Event.Ended
        }
        return null
    }

    fun onRemoved(key: String): Event? {
        if (key != ringingKey) return null
        ringingKey = null
        return Event.Ended
    }
}
```

`app/src/main/java/app/backlit/alerts/AlertCoordinator.kt`:

```kotlin
package app.backlit.alerts

/** Decides which alert (if any) is playing. Pure: callers pass the time. */
class AlertCoordinator(
    private val contacts: () -> List<ContactRule>,
    private val devices: () -> List<DeviceRule>,
) {
    var active: ActiveAlert? = null
        private set

    private val lastDeviceFire = mutableMapOf<String, Long>()

    fun onCallRinging(name: String, nowMs: Long) {
        val rule = contacts().firstOrNull { NameMatch.matches(it.name, name) } ?: return
        active = ActiveAlert(rule.animationId, AlertKind.CALL, nowMs, nowMs + CALL_CAP_MS)
    }

    fun onCallEnded() {
        if (active?.kind == AlertKind.CALL) active = null
    }

    fun onDeviceConnected(address: String, nowMs: Long) {
        val rule = devices().firstOrNull { it.address.equals(address, ignoreCase = true) } ?: return
        if (active?.kind == AlertKind.CALL) return
        val key = rule.address.uppercase()
        val last = lastDeviceFire[key]
        if (last != null && nowMs - last < DEVICE_COOLDOWN_MS) return
        lastDeviceFire[key] = nowMs
        active = ActiveAlert(rule.animationId, AlertKind.DEVICE, nowMs, nowMs + SHORT_MS)
    }

    fun preview(animationId: String, nowMs: Long) {
        if (active?.kind == AlertKind.CALL) return
        active = ActiveAlert(animationId, AlertKind.DEVICE, nowMs, nowMs + SHORT_MS)
    }

    fun tick(nowMs: Long) {
        val a = active ?: return
        if (nowMs >= a.endsAt) active = null
    }

    companion object {
        const val CALL_CAP_MS = 60_000L
        const val SHORT_MS = 3_000L
        const val DEVICE_COOLDOWN_MS = 30_000L
    }
}
```

- [ ] **Step 4: Run them to verify they pass**

```bash
.superpowers/runtests.sh --tests "app.backlit.alerts.*" 2>&1 | tail -1
```

Expected: `16 tests, 0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/alerts/ app/src/test/java/app/backlit/alerts/
git commit -m "feat: alert rules, name matching, call tracking and coordinator

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Storage — AlertStore + AnimationLibrary

**Files:**
- Create: `app/src/main/java/app/backlit/alerts/AnimationLibrary.kt`, `app/src/main/java/app/backlit/alerts/AlertStore.kt`
- Test: `app/src/test/java/app/backlit/alerts/AnimationLibraryTest.kt`, `app/src/test/java/app/backlit/alerts/AlertStoreTest.kt`

**Interfaces:**
- Consumes: `ImportedAnimation`, `MuseumFormat`, `BuiltInAnimations`, `GlyphAnimation` (Tasks 1–3); `AlertConfig`, `AnimIndexEntry`, rules (Task 4).
- Produces:
  - `class AnimationLibrary(dir: File) { fun save(anim: ImportedAnimation); fun load(entry: AnimIndexEntry): ImportedAnimation?; fun delete(id: String); fun resolve(id: String, imports: List<AnimIndexEntry>, fallback: String): GlyphAnimation }`
  - `class AlertStore(store: DataStore<Preferences>) { val config: Flow<AlertConfig>; suspend fun update(transform: (AlertConfig) -> AlertConfig) }`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/backlit/alerts/AnimationLibraryTest.kt`:

```kotlin
package app.backlit.alerts

import app.backlit.anim.Bounce
import app.backlit.anim.Heartbeat
import app.backlit.anim.Link
import app.backlit.anim.MuseumFormat
import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnimationLibraryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun sample(id: String) = (MuseumFormat.parse(
        """{"v":4,"frames":[{"d":120,"p":[${List(PixelGrid.ledCount(13)) { 255 }.joinToString(",")}]}]}""", id, "Sample",
    ) as MuseumFormat.Result.Ok).animation

    @Test
    fun savesLoadsAndDeletes() {
        val lib = AnimationLibrary(tmp.root.resolve("animations"))
        val a = sample("import:abc-123")
        lib.save(a)
        val entry = AnimIndexEntry("import:abc-123", "Sample", 4)
        val fresh = AnimationLibrary(tmp.root.resolve("animations"))     // no cache: reads the file
        assertEquals(a.frames13, fresh.load(entry)?.frames13)
        fresh.delete("import:abc-123")
        assertNull(AnimationLibrary(tmp.root.resolve("animations")).load(entry))
    }

    @Test
    fun resolvesBuiltInsAndImports() {
        val lib = AnimationLibrary(tmp.root)
        lib.save(sample("import:x"))
        val imports = listOf(AnimIndexEntry("import:x", "Sample", 4))
        assertEquals(Bounce, lib.resolve("builtin:bounce", imports, "builtin:heart"))
        assertEquals("import:x", lib.resolve("import:x", imports, "builtin:heart").id)
    }

    @Test
    fun missingImportFallsBack() {
        val lib = AnimationLibrary(tmp.root)
        assertEquals(Heartbeat, lib.resolve("import:gone", emptyList(), "builtin:heart"))
        assertEquals(Link, lib.resolve("import:gone", listOf(AnimIndexEntry("import:gone", "Gone", 1)), "builtin:link"))
    }
}
```

`app/src/test/java/app/backlit/alerts/AlertStoreTest.kt`:

```kotlin
package app.backlit.alerts

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AlertStoreTest {

    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private fun store() = AlertStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { tmp.root.resolve("a.preferences_pb") }))

    @After fun tearDown() = scope.cancel()

    @Test
    fun emptyByDefault() = runBlocking { assertEquals(AlertConfig(), store().config.first()) }

    @Test
    fun roundTripsRulesAndImports() = runBlocking {
        val s = store()
        val cfg = AlertConfig(
            contacts = listOf(ContactRule("Mom", "builtin:heart"), ContactRule("Dad \"D\"", "import:x")),
            devices = listOf(DeviceRule("AA:BB:CC:DD:EE:FF", "Buds", "builtin:link")),
            imports = listOf(AnimIndexEntry("import:x", "Fireworks", 1)),
        )
        s.update { cfg }
        assertEquals(cfg, s.config.first())
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
.superpowers/runtests.sh --tests "app.backlit.alerts.AnimationLibraryTest" --tests "app.backlit.alerts.AlertStoreTest" 2>&1 | grep -E "^e:" | head -2
```

Expected: unresolved `AnimationLibrary` / `AlertStore`.

- [ ] **Step 3: Implement**

`app/src/main/java/app/backlit/alerts/AnimationLibrary.kt`:

```kotlin
package app.backlit.alerts

import app.backlit.anim.BuiltInAnimations
import app.backlit.anim.GlyphAnimation
import app.backlit.anim.ImportedAnimation
import app.backlit.anim.MuseumFormat
import java.io.File

/** Imported animations stored as normalised Glyph Museum JSON files in [dir]. */
class AnimationLibrary(private val dir: File) {
    private val cache = mutableMapOf<String, ImportedAnimation>()

    fun save(anim: ImportedAnimation) {
        dir.mkdirs()
        file(anim.id).writeText(MuseumFormat.toJson(anim))
        cache[anim.id] = anim
    }

    fun load(entry: AnimIndexEntry): ImportedAnimation? {
        cache[entry.id]?.let { return it }
        val f = file(entry.id)
        if (!f.exists()) return null
        val result = runCatching { MuseumFormat.parse(f.readText(), entry.id, entry.name) }.getOrNull()
        return (result as? MuseumFormat.Result.Ok)?.animation?.also { cache[entry.id] = it }
    }

    fun delete(id: String) {
        cache.remove(id)
        file(id).delete()
    }

    fun resolve(id: String, imports: List<AnimIndexEntry>, fallback: String): GlyphAnimation =
        BuiltInAnimations.byId(id)
            ?: imports.firstOrNull { it.id == id }?.let { load(it) }
            ?: BuiltInAnimations.byId(fallback)
            ?: BuiltInAnimations.all.first()

    private fun file(id: String) = File(dir, id.removePrefix("import:").filter { it.isLetterOrDigit() || it == '-' } + ".json")
}
```

`app/src/main/java/app/backlit/alerts/AlertStore.kt`:

```kotlin
package app.backlit.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/** Alert rules and the import index, stored as JSON strings in the app's settings DataStore. */
class AlertStore(private val store: DataStore<Preferences>) {

    val config: Flow<AlertConfig> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { decode(it) }

    suspend fun update(transform: (AlertConfig) -> AlertConfig) {
        store.edit { prefs -> encode(prefs, transform(decode(prefs))) }
    }

    private companion object {
        val CONTACTS = stringPreferencesKey("alert_contacts")
        val DEVICES = stringPreferencesKey("alert_devices")
        val IMPORTS = stringPreferencesKey("anim_imports")
        val json = Json { ignoreUnknownKeys = true }

        fun decode(p: Preferences) = AlertConfig(
            contacts = runCatching { json.decodeFromString<List<ContactRule>>(p[CONTACTS] ?: "[]") }.getOrDefault(emptyList()),
            devices = runCatching { json.decodeFromString<List<DeviceRule>>(p[DEVICES] ?: "[]") }.getOrDefault(emptyList()),
            imports = runCatching { json.decodeFromString<List<AnimIndexEntry>>(p[IMPORTS] ?: "[]") }.getOrDefault(emptyList()),
        )

        fun encode(p: MutablePreferences, c: AlertConfig) {
            p[CONTACTS] = json.encodeToString(c.contacts)
            p[DEVICES] = json.encodeToString(c.devices)
            p[IMPORTS] = json.encodeToString(c.imports)
        }
    }
}
```

- [ ] **Step 4: Run them to verify they pass**

```bash
.superpowers/runtests.sh --tests "app.backlit.alerts.*" 2>&1 | tail -1
```

Expected: `21 tests, 0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/alerts/ app/src/test/java/app/backlit/alerts/
git commit -m "feat: alert rule storage and imported animation library

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Runtime, matrix player and toy integration

**Files:**
- Create: `app/src/main/java/app/backlit/alerts/ToyPresence.kt`, `app/src/main/java/app/backlit/alerts/AlertsRuntime.kt`, `app/src/main/java/app/backlit/glyph/AppMatrixPlayer.kt`
- Modify: `app/src/main/java/app/backlit/glyph/GlyphOutput.kt`, `ClockToyService.kt`, `MusicToyService.kt`

**Interfaces:**
- Consumes: Tasks 1–5; Part 1/2 `GlyphOutput`, `FrameEncoder`, `FramePacer`, `DeviceProfile`, `SettingsRepo`, `settingsDataStore`.
- Produces:
  - `object ToyPresence { val count: StateFlow<Int>; fun enter(); fun leave() }`
  - `class AlertsRuntime { val bus: StateFlow<ActiveAlert?>; val config: Flow<AlertConfig>; val library: AnimationLibrary; val brightness: Int; fun onCallRinging(name: String); fun onCallEnded(); fun onDeviceConnected(address: String); fun preview(animationId: String); fun toyChanged(); fun animationFor(alert: ActiveAlert): GlyphAnimation; suspend fun update(transform: (AlertConfig) -> AlertConfig); fun importJson(json: String, name: String): ImportOutcome; fun deleteImport(id: String); companion object { fun get(context: Context): AlertsRuntime } }`
  - `sealed interface ImportOutcome { data class Ok(val name: String, val sourceSize: Int) : ImportOutcome; data object Invalid : ImportOutcome }`
  - `GlyphOutput(context, profile, appMatrix: Boolean = false, onReady)`. In app-matrix mode it pushes with `setAppMatrixFrame` and calls `closeAppMatrix()` on close.

This task is Android and SDK glue. It's verified by building now and on the device in Task 7 (PREVIEW ON MATRIX).

- [ ] **Step 1: GlyphOutput app-matrix mode**

In `GlyphOutput.kt`, replace:

```kotlin
class GlyphOutput(
    private val context: Context,
    private val profile: DeviceProfile,
    private val onReady: () -> Unit,
) {
```

with:

```kotlin
class GlyphOutput(
    private val context: Context,
    private val profile: DeviceProfile,
    private val appMatrix: Boolean = false,
    private val onReady: () -> Unit,
) {
```

replace `        runCatching { manager?.setMatrixFrame(frame) }` with:

```kotlin
        runCatching { if (appMatrix) manager?.setAppMatrixFrame(frame) else manager?.setMatrixFrame(frame) }
```

and in `close()` replace:

```kotlin
        handler.removeCallbacksAndMessages(null)
        runCatching { manager?.unInit() }
```

with:

```kotlin
        handler.removeCallbacksAndMessages(null)
        if (appMatrix) runCatching { manager?.closeAppMatrix() }
        runCatching { manager?.unInit() }
```

Existing callers use trailing-lambda syntax (`GlyphOutput(this, profile) { … }`), so they keep compiling unchanged.

- [ ] **Step 2: ToyPresence and AlertsRuntime**

`app/src/main/java/app/backlit/alerts/ToyPresence.kt`:

```kotlin
package app.backlit.alerts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How many Backlit toys are currently bound (showing). Main thread only. */
object ToyPresence {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    fun enter() { _count.value += 1 }
    fun leave() { _count.value = (_count.value - 1).coerceAtLeast(0) }
}
```

`app/src/main/java/app/backlit/alerts/AlertsRuntime.kt`:

```kotlin
package app.backlit.alerts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.backlit.anim.BuiltInAnimations
import app.backlit.anim.GlyphAnimation
import app.backlit.anim.MuseumFormat
import app.backlit.data.SettingsRepo
import app.backlit.data.settingsDataStore
import app.backlit.glyph.AppMatrixPlayer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed interface ImportOutcome {
    data class Ok(val name: String, val sourceSize: Int) : ImportOutcome
    data object Invalid : ImportOutcome
}

/** Process-wide alert state: triggers in, ActiveAlert out. Main thread. */
class AlertsRuntime private constructor(private val app: Context) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> Log.e(TAG, "alerts failed", e) },
    )
    private val store = AlertStore(app.settingsDataStore)
    private val handler = Handler(Looper.getMainLooper())
    private var current = AlertConfig()
    private var loaded = false
    private val coordinator = AlertCoordinator({ current.contacts }, { current.devices })
    private val _bus = MutableStateFlow<ActiveAlert?>(null)
    private val player = AppMatrixPlayer(app, this)

    val bus: StateFlow<ActiveAlert?> = _bus
    val config: Flow<AlertConfig> = store.config
    val library = AnimationLibrary(File(app.filesDir, "animations"))
    var brightness: Int = 80
        private set

    init {
        scope.launch { store.config.collect { current = it; loaded = true } }
        scope.launch { SettingsRepo.get(app).settings.collect { brightness = it.brightness } }
    }

    fun onCallRinging(name: String) = dispatch { coordinator.onCallRinging(name, now()) }
    fun onCallEnded() = dispatch { coordinator.onCallEnded() }
    fun onDeviceConnected(address: String) = dispatch { coordinator.onDeviceConnected(address, now()) }
    fun preview(animationId: String) = dispatch { coordinator.preview(animationId, now()) }

    /** Called by toys when they bind/unbind so the app-matrix player can step in or out. */
    fun toyChanged() = player.sync()

    fun animationFor(alert: ActiveAlert): GlyphAnimation = library.resolve(
        alert.animationId, current.imports,
        fallback = if (alert.kind == AlertKind.CALL) BuiltInAnimations.DEFAULT_CONTACT else BuiltInAnimations.DEFAULT_DEVICE,
    )

    suspend fun update(transform: (AlertConfig) -> AlertConfig) = store.update(transform)

    fun importJson(json: String, name: String): ImportOutcome {
        val id = "import:" + UUID.randomUUID()
        return when (val r = MuseumFormat.parse(json, id, name)) {
            MuseumFormat.Result.Invalid -> ImportOutcome.Invalid
            is MuseumFormat.Result.Ok -> {
                library.save(r.animation)
                val v = if (r.animation.sourceSize == 25) 1 else 4
                scope.launch { store.update { it.copy(imports = it.imports + AnimIndexEntry(id, name, v)) } }
                ImportOutcome.Ok(name, r.animation.sourceSize)
            }
        }
    }

    fun deleteImport(id: String) {
        library.delete(id)
        scope.launch { store.update { c -> c.copy(imports = c.imports.filterNot { it.id == id }) } }
    }

    private fun dispatch(event: () -> Unit) {
        scope.launch {
            if (!loaded) { current = store.config.first(); loaded = true }   // cold start: wait for rules
            event()
            publish()
        }
    }

    private val expire = Runnable { publish() }

    private fun publish() {
        coordinator.tick(now())
        _bus.value = coordinator.active
        handler.removeCallbacks(expire)
        coordinator.active?.let { handler.postDelayed(expire, (it.endsAt - now()).coerceAtLeast(0) + 10) }
        player.sync()
    }

    companion object {
        private const val TAG = "BacklitAlerts"
        @Volatile private var instance: AlertsRuntime? = null

        fun get(context: Context): AlertsRuntime =
            instance ?: synchronized(this) { instance ?: AlertsRuntime(context.applicationContext).also { instance = it } }

        fun now(): Long = SystemClock.elapsedRealtime()
    }
}
```

- [ ] **Step 3: AppMatrixPlayer**

`app/src/main/java/app/backlit/glyph/AppMatrixPlayer.kt`:

```kotlin
package app.backlit.glyph

import android.content.Context
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.ToyPresence
import app.backlit.render.PixelGrid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Plays the active alert with setAppMatrixFrame while no Backlit toy is showing. Main thread. */
class AppMatrixPlayer(private val app: Context, private val runtime: AlertsRuntime) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var output: GlyphOutput? = null

    fun sync() {
        val shouldPlay = runtime.bus.value != null && ToyPresence.count.value == 0
        if (shouldPlay && job == null) start() else if (!shouldPlay && job != null) stop()
    }

    private fun start() {
        val profile = DeviceProfile.detect()
        if (profile == DeviceProfile.UNSUPPORTED) return
        val out = GlyphOutput(app, profile, appMatrix = true) {}.also { it.connect() }
        output = out
        job = scope.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val alert = runtime.bus.value ?: break
                if (ToyPresence.count.value > 0) break
                val now = AlertsRuntime.now()
                val grid = runCatching { runtime.animationFor(alert).frame(profile.size, now - alert.startedAt) }
                    .getOrElse { Log.e(TAG, "alert render failed", it); PixelGrid(profile.size) }
                out.push(FrameEncoder.encode(grid, runtime.brightness, aod = false))
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
            stop()
        }
    }

    private fun stop() {
        val j = job
        job = null
        output?.close()
        output = null
        j?.cancel()
    }

    private companion object {
        const val TAG = "BacklitAlerts"
        const val FRAME_MS = 50L
    }
}
```

- [ ] **Step 4: Clock toy renders alerts**

In `ClockToyService.kt`:

- add fields after `private var settings = Settings()`:

```kotlin
    private var alertJob: Job? = null
    private var alerts: AlertsRuntime? = null
```

- inside `onBind`, at the start of the `if (profile != DeviceProfile.UNSUPPORTED) {` block, add:

```kotlin
            ToyPresence.enter()
            val rt = AlertsRuntime.get(this).also { alerts = it }
            rt.toyChanged()
            s.launch { rt.bus.collect { a -> if (a != null) startAlert() else stopAlert() } }
```

- at the top of `draw()`, before `val out = output ?: return`, add:

```kotlin
        if (alertJob != null) return
```

- at the top of `restartTicker()`, before `val s = scope ?: return`, add:

```kotlin
        if (alertJob != null) return
```

- add these two functions before `private companion object`:

```kotlin
    private fun startAlert() {
        val s = scope ?: return
        val rt = alerts ?: return
        if (alertJob != null) return
        tickJob?.cancel()
        alertJob = s.launch {
            val pacer = FramePacer(50)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val a = rt.bus.value ?: break
                val grid = runCatching { rt.animationFor(a).frame(profile.size, AlertsRuntime.now() - a.startedAt) }
                    .getOrElse { PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = false))
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    private fun stopAlert() {
        alertJob?.cancel()
        alertJob = null
        draw()
        restartTicker()
    }
```

- in `onUnbind`, replace:

```kotlin
        output?.close()
        output = null
        return false
```

with:

```kotlin
        alertJob?.cancel()
        alertJob = null
        output?.close()
        output = null
        if (alerts != null) {
            ToyPresence.leave()
            alerts?.toyChanged()
            alerts = null
        }
        return false
```

- add imports: `app.backlit.alerts.AlertsRuntime`, `app.backlit.alerts.ToyPresence`.

- [ ] **Step 5: Music toy renders alerts**

In `MusicToyService.kt`:

- add field `private var alerts: AlertsRuntime? = null`.
- in `onBind`, right after `scope = s`, add:

```kotlin
        ToyPresence.enter()
        alerts = AlertsRuntime.get(this).also { it.toyChanged() }
```

- replace:

```kotlin
                val grid = runCatching {
                    engine.tick(
                        now, dt, music.isPlaying(), visualizer.readFft(), visualizer.samplingRateHz,
                        settings.musicSensitivity.gain, visualizer.isActive,
                    )
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(SIZE) }
```

with:

```kotlin
                val alert = alerts?.bus?.value
                val grid = runCatching {
                    if (alert != null) {
                        alerts!!.animationFor(alert).frame(SIZE, AlertsRuntime.now() - alert.startedAt)
                    } else {
                        engine.tick(
                            now, dt, music.isPlaying(), visualizer.readFft(), visualizer.samplingRateHz,
                            settings.musicSensitivity.gain, visualizer.isActive,
                        )
                    }
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(SIZE) }
```

- in `onUnbind`, after `output = null`, add:

```kotlin
        if (alerts != null) {
            ToyPresence.leave()
            alerts?.toyChanged()
            alerts = null
        }
```

- add imports: `app.backlit.alerts.AlertsRuntime`, `app.backlit.alerts.ToyPresence`.

- [ ] **Step 6: Build and run all tests**

```bash
source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && .superpowers/runtests.sh -q 2>&1 | tail -1
```

Expected: the build succeeds and all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/backlit/alerts/ app/src/main/java/app/backlit/glyph/
git commit -m "feat: alerts runtime, app-matrix player and alert rendering inside toys

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: ALERTS tab, import and share-to-Backlit

**Files:**
- Create: `app/src/main/java/app/backlit/ui/AlertsScreen.kt`, `app/src/main/java/app/backlit/alerts/ImportHelper.kt`
- Modify: `app/src/main/java/app/backlit/ui/HomeScreen.kt`, `app/src/main/java/app/backlit/ui/MusicScreen.kt`, `app/src/main/java/app/backlit/MainActivity.kt`, `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AlertsRuntime`, `ImportOutcome`, `AlertConfig`, rules, `NameMatch` (Tasks 4–6); `BuiltInAnimations`, `GlyphAnimation` (Tasks 1–2); UI components and `MatrixPreview` (Part 1); `Notice` (Part 2, made internal).
- Produces: `@Composable fun AlertsTab(profile: DeviceProfile)`; `fun importFromUri(context: Context, runtime: AlertsRuntime, uri: Uri, deviceSize: Int): String`.

- [ ] **Step 1: Make `Notice` reusable**

In `MusicScreen.kt`, replace `private fun Notice(text: String) {` with `internal fun Notice(text: String) {`.

- [ ] **Step 2: Import helper**

`app/src/main/java/app/backlit/alerts/ImportHelper.kt`:

```kotlin
package app.backlit.alerts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Reads a Glyph Museum JSON file from [uri], imports it, and returns a message for the user. */
fun importFromUri(context: Context, runtime: AlertsRuntime, uri: Uri, deviceSize: Int): String {
    val resolver = context.contentResolver
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Imported animation"
    val text = runCatching {
        resolver.openInputStream(uri)?.use { s -> s.readNBytes(MAX_BYTES + 1) }
    }.getOrNull()?.takeIf { it.size <= MAX_BYTES }?.toString(Charsets.UTF_8)
        ?: return BAD
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

private const val MAX_BYTES = 4 * 1024 * 1024
private const val BAD = "This file isn't a Glyph Museum animation."
```

- [ ] **Step 3: ALERTS tab**

`app/src/main/java/app/backlit/ui/AlertsScreen.kt`:

```kotlin
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
import app.backlit.alerts.NameMatch
import app.backlit.alerts.importFromUri
import app.backlit.anim.BuiltInAnimations
import app.backlit.anim.GlyphAnimation
import app.backlit.glyph.DeviceProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DISCLOSURE =
    "Backlit only looks at incoming-call notifications from your Phone app, to check the caller's name " +
        "against your important contacts. Nothing else is read, stored or sent."

@Composable
fun AlertsTab(profile: DeviceProfile) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val config by runtime.config.collectAsStateWithLifecycle(initialValue = AlertConfig())
    val scope = rememberCoroutineScope()
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var listenerOn by remember { mutableStateOf(false) }
    var btGranted by remember { mutableStateOf(false) }
    LaunchedEffect(resumed) {
        if (resumed) {
            listenerOn = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            btGranted = hasBtPermission(context)
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
```

- [ ] **Step 4: Wire the tab and share-to-import**

In `HomeScreen.kt`, replace:

```kotlin
            SquareChip("CLOCK", selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.weight(1f))
            SquareChip("MUSIC", selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.weight(1f))
```

with:

```kotlin
            SquareChip("CLOCK", selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.weight(1f))
            SquareChip("MUSIC", selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.weight(1f))
            SquareChip("ALERTS", selected = tab == 2, onClick = { tab = 2 }, modifier = Modifier.weight(1f))
```

and replace:

```kotlin
        if (tab == 0) ClockTab(settings, profile, onUpdate, onNavigate) else MusicTab(settings, profile, onUpdate)
```

with:

```kotlin
        when (tab) {
            0 -> ClockTab(settings, profile, onUpdate, onNavigate)
            1 -> MusicTab(settings, profile, onUpdate)
            else -> AlertsTab(profile)
        }
```

In `MainActivity.kt`:
- after `enableEdgeToEdge()` add `handleShare(intent)`
- add these members to the class:

```kotlin
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: return
        val size = if (DeviceProfile.detect() == DeviceProfile.PHONE_4A_PRO) 13 else 25
        Toast.makeText(this, importFromUri(this, AlertsRuntime.get(this), uri, size), Toast.LENGTH_LONG).show()
    }
```

- add imports: `android.content.Intent`, `android.net.Uri`, `android.widget.Toast`, `app.backlit.alerts.AlertsRuntime`, `app.backlit.alerts.importFromUri`.

In `AndroidManifest.xml`, inside the `MainActivity` `<activity>` after its existing `<intent-filter>`, add:

```xml
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/json" />
            </intent-filter>
```

- [ ] **Step 5: Build, test, and check on the device**

```bash
source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && .superpowers/runtests.sh -q 2>&1 | tail -1 && ./gradlew -q :app:installDebug
```

Human + agent on the Phone (3), with screenshots via `adb exec-out screencap -p`:
1. ALERTS tab shows 3 sections, the 6 animated previews, and import.
2. Tapping an animation tile plays it on the **real matrix for 3 s** with the Glyph idle. Then with Backlit Clock showing, the clock shows the animation and returns.
3. + ADD CONTACT → the picker → the contact appears. The disclosure shows. Change its animation. PREVIEW works.
4. IMPORT a real Glyph Museum JSON export (ask the human to export one). It appears in ANIMATIONS and previews on the matrix. A non-JSON file shows the error message.

Ledger anything that differs.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/backlit/ui/ app/src/main/java/app/backlit/alerts/ImportHelper.kt app/src/main/java/app/backlit/MainActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: ALERTS tab with contacts, devices, animation library and Glyph Museum import

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Bluetooth trigger

**Files:**
- Create: `app/src/main/java/app/backlit/alerts/BluetoothAlertReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `AlertsRuntime.onDeviceConnected` (Task 6).
- Produces: a manifest receiver for `android.bluetooth.device.action.ACL_CONNECTED`.

- [ ] **Step 1: Receiver**

```kotlin
package app.backlit.alerts

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper

/** A chosen Bluetooth device connected → play its animation (~3 s). */
class BluetoothAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val pending = goAsync()
        AlertsRuntime.get(context).onDeviceConnected(device.address)
        Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, 3_500)
    }
}
```

- [ ] **Step 2: Manifest**

After `<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />` add:

```xml
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

Inside `<application>`, before `</application>`, add:

```xml
        <receiver
            android:name=".alerts.BluetoothAlertReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.bluetooth.device.action.ACL_CONNECTED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 3: Build, test, and check on the device**

```bash
source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && .superpowers/runtests.sh -q 2>&1 | tail -1 && ./gradlew -q :app:installDebug
```

Human: ALERTS → + ADD DEVICE → allow Nearby devices → add the earbuds or watch. Disconnect and reconnect them with the Glyph idle: the animation plays for ~3 s. Reconnect again within 30 s: nothing plays.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/backlit/alerts/BluetoothAlertReceiver.kt app/src/main/AndroidManifest.xml
git commit -m "feat: play an animation when a chosen Bluetooth device connects

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Important-caller trigger

**Files:**
- Create: `app/src/main/java/app/backlit/alerts/CallAlertListener.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `CallNotificationTracker` (Task 4), `AlertsRuntime.onCallRinging/onCallEnded` (Task 6).
- Produces: a `NotificationListenerService`.

- [ ] **Step 1: Listener**

```kotlin
package app.backlit.alerts

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/** Watches only incoming-call notifications; everything else is ignored immediately. Never logs contents. */
class CallAlertListener : NotificationListenerService() {
    private val tracker = CallNotificationTracker()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val isCall = n.category == Notification.CATEGORY_CALL
        if (!isCall) return
        val incoming = n.extras.getInt(Notification.EXTRA_CALL_TYPE, 0) == Notification.CALL_TYPE_INCOMING
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        handle(tracker.onPosted(sbn.key, isCall = true, incoming = incoming, title = title))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = handle(tracker.onRemoved(sbn.key))

    private fun handle(event: CallNotificationTracker.Event?) {
        val rt = AlertsRuntime.get(this)
        when (event) {
            is CallNotificationTracker.Event.Ringing -> { Log.d(TAG, "incoming call notification"); rt.onCallRinging(event.callerName) }
            CallNotificationTracker.Event.Ended -> rt.onCallEnded()
            null -> Unit
        }
    }

    private companion object { const val TAG = "BacklitAlerts" }
}
```

- [ ] **Step 2: Manifest**

Inside `<application>`, before `</application>`, add:

```xml
        <service
            android:name=".alerts.CallAlertListener"
            android:exported="true"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
```

- [ ] **Step 3: Build, test, and check on the device**

```bash
source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && .superpowers/runtests.sh -q 2>&1 | tail -1 && ./gradlew -q :app:installDebug && adb logcat -c
```

Human: in ALERTS, add an important contact, then TURN ON NOTIFICATION ACCESS for Backlit. Then have that contact (or a second phone saved under that name) call:
- with the Glyph idle and the screen off: the animation loops while ringing and stops on decline
- with Backlit Clock showing: the clock shows the animation, then returns
- answer instead of declining: it stops
- a non-important caller: nothing

Agent:

```bash
adb logcat -d -s BacklitAlerts:V | tail -5
```

Expected: an `incoming call notification` line per call. No names in the log.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/backlit/alerts/CallAlertListener.kt app/src/main/AndroidManifest.xml
git commit -m "feat: play an important contact's animation while their call rings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Privacy texts and device checklist

**Files:**
- Modify: `docs/privacy-policy.md`, `docs/release/play-listing.md`, `app/src/main/java/app/backlit/ui/AboutScreen.kt`, `docs/testing/device-checklist.md`

- [ ] **Step 1: Texts**

In `docs/privacy-policy.md`, add after the microphone bullet:

```markdown
- **Notification access (important callers):** Backlit only looks at incoming-call notifications
  from your Phone app, to compare the caller's name with the contacts you chose. Nothing else is
  read, and no notification content is stored or shared. Only the names of the contacts you pick
  are saved, on your phone.
- **Bluetooth (Nearby devices):** used to notice when a Bluetooth device you chose connects. Only
  the name and address of the devices you pick are saved, on your phone.
```

In `AboutScreen.kt`, replace:

```kotlin
                "Music is analysed on the phone in real time and is never recorded, stored or shared. " +
```

with:

```kotlin
                "Music is analysed on the phone in real time and is never recorded, stored or shared. " +
                "Caller names are only compared with the contacts you pick; nothing from your notifications is kept. " +
```

In `docs/release/play-listing.md`, add after the MUSIC line:

```markdown
• ALERTS — your own animation when important contacts call or your Bluetooth devices connect; import from Glyph Museum
```

- [ ] **Step 2: Checklist**

Append to `docs/testing/device-checklist.md`:

```markdown

## Alerts
- [ ] Dialer's incoming-call notification is recognised (log: "incoming call notification", no name).
- [ ] Important caller, Glyph idle, screen off: animation loops while ringing; stops on decline / answer / missed.
- [ ] Important caller while Backlit Clock shows (carousel) and while it is the AOD toy: alert plays, clock returns. (AOD: note whether 20 fps or minute updates.)
- [ ] Non-important caller: nothing.
- [ ] Chosen BT device connects: 3 s animation; reconnect within 30 s: nothing.
- [ ] Real Glyph Museum export imports via file picker and via Share → Backlit; plays on the matrix.
- [ ] Bad file: "This file isn't a Glyph Museum animation."
- [ ] Tap-to-preview plays on the matrix (idle and inside the clock toy).
- [ ] Notification access off / Nearby devices denied: hints shown, no crash.
```

- [ ] **Step 3 (human + agent):** run the checklist on the Phone (3), tick the boxes, and fix any failures (test-first where testable, a ledgered ruling otherwise).

- [ ] **Step 4: Commit**

```bash
git add docs/ app/src/main/java/app/backlit/ui/AboutScreen.kt
git commit -m "docs: alerts privacy texts and device checklist

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
