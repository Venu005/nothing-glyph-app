# Backlit Glyph Pet (ghost) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Backlit Pet" Glyph toy: a ghost with a persisted fractional mood that reacts to long presses, shakes, tilt, being turned face-down, charging and night. It shows a still pose in AOD (filling up while charging), and a new PET tab shows it live.

**Architecture:**
- **Pure Kotlin `pet/`:**
  - `PetMood`: fractional mood with timestamp decay that skips sleep hours
  - `PetBrain`: the state machine that turns events into a `Pose`
  - `GhostArt`: a line-for-line port of the approved mockup at 25×25 and 13×13
  - `PetPreviewAnimation`: wraps a pose for the Alerts preview path
- **`glyph/PetToyService`:** copies the Charge/Canvas toy plumbing, and adds the accelerometer (only while ACTIVE) and a battery receiver.
- **`ui/PetScreen.kt`:** the PET tab in a horizontally scrolling tab row.

**Tech Stack:** Kotlin, Jetpack Compose (material3), DataStore, kotlinx-coroutines, Android `SensorManager`, JUnit 4, Nothing GlyphMatrix SDK.

**Spec:** `docs/superpowers/specs/2026-10-05-backlit-pet-design.md`. Mockups: `docs/superpowers/mockups/2026-10-05-pet-ghost-faces.html` (faces and reactions) and `docs/superpowers/mockups/2026-10-05-pet-ghost-aod.html` (the AOD charging "fills up" pose).

## Global Constraints

- **Branch and tooling:**
  - Branch `feat/backlit-pet`.
  - In every fresh shell, `source .superpowers/env.sh`.
  - Tests: `.superpowers/runtests.sh [--tests 'pattern']`.
- **Imports:** `pet/` imports no `android.*`, `androidx.*` or `com.nothing.*`. It may import `app.backlit.render.*`, `app.backlit.render.charge.ChargeKit` and `app.backlit.anim.GlyphAnimation`.
- **Mood:**
  - A Double in 0..100 that starts at 70.
  - Decay is continuous, −1 per 6 awake minutes (360 000 ms).
  - Gains:
    - pet +15 (30 s cooldown)
    - yawn +5
    - calmed +10
    - peek +5
    - charging +1 per full minute while bound
  - Losses:
    - big shake −3 (10 s cooldown)
    - angry −5
- **Thresholds and timings:**
  - Big shake: linear acceleration ≥ 12 m/s².
  - Angry: the 3rd big shake within 10 s.
  - Dizzy: 1500 ms cooldown.
  - Face-down: z ≤ −7 for 500 ms, armed after ≥ 2000 ms not face-down.
  - Night yawn: keeps him awake for 60 s.
  - Boo: HAPPY and ACTIVE only, checked once per 60 s with a 1-in-5 chance, at most once per 10 min.
- **Base order:** ASLEEP, then MUNCH (charging), then HAPPY ≥70, CONTENT ≥40, BORED ≥15, SAD.
- **Reaction lengths (ms):** PET 2600, YAWN 3600, DIZZY 2000, ANGRY 6000, CALMED 4000, PEEK 3200, BOO 2600. Only CALMED (or ANGRY again) replaces ANGRY. Reactions never start when `active = false` (AOD).
- **Settings:**
  - `petName = "Boo"`
  - `petMood = 70f` (Float)
  - `petMoodAt = 0L` (wall-clock ms; 0 means a new pet)
  - `petSleepStart = 23`, `petSleepEnd = 7`
  - `petToyEverBound = false`
- **Persistence:** save when the brain is dirty, at most once per 10 s, at least every 5 min while bound, and on unbind.
- **Preview ids:** `pet:<base or reaction lowercase>`. "Show on Glyph" uses `pet:happy` for 3000 ms.
- No new permissions. The accelerometer is registered only while bound and ACTIVE.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Mood after the phone sits overnight, or for days, with the toy unbound.** It must decay by awake minutes only, never go negative, and never "jump" on the first save. This is pinned by `PetMoodTest.multiDaySpanSkipsSleepAndFloorsAtZero` (Task 1).
2. **Frequent saves must not freeze decay.** Saving every few minutes must still let mood fall. This is pinned by `PetMoodTest.repeatedShortSpansAddUp` (Task 1).
3. **Button-mashing long press** must not max out the mood. The reaction replays, but mood rises at most once per 30 s. This is pinned by `PetBrainTest.petCooldown` (Task 2).
4. **A quick flip or wobble** must not trigger peekaboo, and peekaboo must not re-trigger while the phone stays face-down. This is pinned by `PetBrainTest.quickFlipDoesNotPeek` and `peekOnlyOncePerFaceDown` (Task 2).
5. **AOD:** events received while in AOD (long press, charging) change mood but never start an animation. This is pinned by `PetBrainTest.aodEventsChangeMoodOnly` (Task 2).

---

## File Structure

```
app/src/main/java/app/backlit/pet/
  PetMood.kt              MoodState, SleepWindow, PetMood.decay/awakeMs/clamp
  PetBrain.kt             Base, Reaction, Pose, PetBrain
  GhostArt.kt             ghost drawing: frame(size, pose, now), still(size, pose, minuteOfHour)
  PetPreviewAnimation.kt  GlyphAnimation for pet:<key> ids
app/src/main/java/app/backlit/alerts/AlertsRuntime.kt   (resolve pet: ids)
app/src/main/java/app/backlit/data/Settings.kt, SettingsRepo.kt   (+ pet fields)
app/build.gradle.kts                                     (buildConfig = true)
app/src/main/java/app/backlit/glyph/PetToyService.kt
app/src/main/AndroidManifest.xml, res/values/strings.xml, res/drawable/ic_pet_preview.xml
app/src/main/java/app/backlit/ui/PetScreen.kt, HomeScreen.kt   (PET tab, scrolling tab row)
```

---

### Task 1: PetMood

**Files:**
- Create: `app/src/main/java/app/backlit/pet/PetMood.kt`
- Test: `app/src/test/java/app/backlit/pet/PetMoodTest.kt`

**Interfaces:**
- Produces:
  - `data class MoodState(val mood: Double, val at: Long)`
  - `data class SleepWindow(val startHour: Int, val endHour: Int)` with `val enabled: Boolean` and `fun contains(epochMs: Long, zone: ZoneId): Boolean`
  - `object PetMood`:
    - `const val START = 70.0`
    - `const val DECAY_MS = 360_000L`
    - `fun clamp(v: Double): Double`
    - `fun awakeMs(from: Long, to: Long, sleep: SleepWindow, zone: ZoneId): Long`
    - `fun decay(state: MoodState, now: Long, sleep: SleepWindow, zone: ZoneId): Double`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class PetMoodTest {
    private val utc = ZoneOffset.UTC
    private val night = SleepWindow(23, 7)
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun sleepWindowContainsWrapsMidnight() {
        assertTrue(night.contains(at("2026-10-05T23:30:00Z"), utc))
        assertTrue(night.contains(at("2026-10-06T06:59:00Z"), utc))
        assertFalse(night.contains(at("2026-10-06T07:00:00Z"), utc))
        assertFalse(night.contains(at("2026-10-05T12:00:00Z"), utc))
        val early = SleepWindow(1, 5)
        assertTrue(early.contains(at("2026-10-05T03:00:00Z"), utc)); assertFalse(early.contains(at("2026-10-05T05:00:00Z"), utc))
        assertFalse(SleepWindow(9, 9).enabled); assertFalse(SleepWindow(9, 9).contains(at("2026-10-05T09:30:00Z"), utc))
    }

    @Test
    fun decaysOnePointPerSixAwakeMinutes() {
        val s = MoodState(70.0, at("2026-10-05T12:00:00Z"))
        assertEquals(60.0, PetMood.decay(s, at("2026-10-05T13:00:00Z"), night, utc), 1e-9)
        assertEquals(69.5, PetMood.decay(s, at("2026-10-05T12:03:00Z"), night, utc), 1e-9)
    }

    @Test
    fun sleepHoursDontDecay() {
        val s = MoodState(70.0, at("2026-10-05T22:00:00Z"))
        // 22:00 → 08:00: awake 22–23 and 07–08 = 2 h → −20
        assertEquals(50.0, PetMood.decay(s, at("2026-10-06T08:00:00Z"), night, utc), 1e-9)
        assertEquals(60.0, PetMood.decay(MoodState(70.0, at("2026-10-05T12:00:00Z")), at("2026-10-05T13:00:00Z"), SleepWindow(9, 9), utc), 1e-9)
    }

    @Test
    fun multiDaySpanSkipsSleepAndFloorsAtZero() {
        val s = MoodState(70.0, at("2026-10-05T12:00:00Z"))
        assertEquals(0.0, PetMood.decay(s, at("2026-10-08T12:00:00Z"), night, utc), 1e-9)
        assertEquals(48L * 3_600_000, PetMood.awakeMs(s.at, at("2026-10-08T12:00:00Z"), night, utc))
    }

    @Test
    fun futureOrUnsetTimestampMeansNoDecay() {
        assertEquals(70.0, PetMood.decay(MoodState(70.0, at("2026-10-05T14:00:00Z")), at("2026-10-05T12:00:00Z"), night, utc), 1e-9)
        assertEquals(70.0, PetMood.decay(MoodState(70.0, 0L), at("2026-10-05T12:00:00Z"), night, utc), 1e-9)
        assertEquals(100.0, PetMood.clamp(130.0), 1e-9); assertEquals(0.0, PetMood.clamp(-4.0), 1e-9)
    }

    @Test
    fun repeatedShortSpansAddUp() {
        var s = MoodState(70.0, at("2026-10-05T12:00:00Z"))
        repeat(12) {                                                   // saved every 5 minutes for an hour
            val now = s.at + 300_000
            s = MoodState(PetMood.decay(s, now, night, utc), now)
        }
        assertEquals(60.0, s.mood, 1e-9)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.pet.PetMoodTest'`
Expected: compilation FAIL, because `SleepWindow`, `MoodState` and `PetMood` are unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package app.backlit.pet

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class MoodState(val mood: Double, val at: Long)

/** Whole-hour sleep window; may wrap midnight; start == end means no sleep. */
data class SleepWindow(val startHour: Int, val endHour: Int) {
    val enabled: Boolean get() = startHour != endHour

    fun contains(epochMs: Long, zone: ZoneId): Boolean {
        if (!enabled) return false
        val h = Instant.ofEpochMilli(epochMs).atZone(zone).hour
        return if (startHour < endHour) h in startHour until endHour else h >= startHour || h < endHour
    }
}

/** Mood math from timestamps; nothing ticks in the background. */
object PetMood {
    const val START = 70.0
    const val DECAY_MS = 360_000L
    private const val MAX_SPAN_MS = 60L * 24 * 3_600_000

    fun clamp(v: Double): Double = v.coerceIn(0.0, 100.0)

    /** Milliseconds between [from] and [to] that fall outside the sleep window (walks whole hours). */
    fun awakeMs(from: Long, to: Long, sleep: SleepWindow, zone: ZoneId): Long {
        if (to <= from) return 0
        var t = maxOf(from, to - MAX_SPAN_MS)
        var awake = 0L
        while (t < to) {
            val nextHour = Instant.ofEpochMilli(t).atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val end = minOf(to, nextHour)
            if (!sleep.contains(t, zone)) awake += end - t
            t = end
        }
        return awake
    }

    fun decay(state: MoodState, now: Long, sleep: SleepWindow, zone: ZoneId): Double {
        if (state.at <= 0 || now <= state.at) return clamp(state.mood)
        return clamp(state.mood - awakeMs(state.at, now, sleep, zone).toDouble() / DECAY_MS)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.pet.PetMoodTest'`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/pet/PetMood.kt app/src/test/java/app/backlit/pet/PetMoodTest.kt
git commit -m "feat(pet): mood decay from timestamps that skips sleep hours

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: PetBrain

**Files:**
- Create: `app/src/main/java/app/backlit/pet/PetBrain.kt`
- Test: `app/src/test/java/app/backlit/pet/PetBrainTest.kt`

**Interfaces:**
- Consumes: `MoodState`, `SleepWindow`, `PetMood` (Task 1).
- Produces:
  - `enum class Base { HAPPY, CONTENT, BORED, SAD, ASLEEP, MUNCH }`
  - `enum class Reaction(val ms: Long) { PET(2600), YAWN(3600), DIZZY(2000), ANGRY(6000), CALMED(4000), PEEK(3200), BOO(2600) }`
  - `data class Pose(val base: Base, val reaction: Reaction?, val reactionStart: Long, val lookX: Int, val lookY: Int, val lean: Int, val level: Int)`
  - `class PetBrain(initial: MoodState, sleep: () -> SleepWindow, zone: ZoneId = ZoneId.systemDefault(), random: Random = Random.Default)`:
    - events: `onLongPress(now: Long, active: Boolean)`, `onShake(magnitude: Float, now: Long, active: Boolean)`, `onGravity(x: Float, y: Float, z: Float, now: Long, active: Boolean)`, `onCharging(on: Boolean, level: Int, now: Long)`
    - `tick(now: Long, active: Boolean)`
    - queries: `mood(now: Long): Int`, `base(now: Long): Base`, `reaction(now: Long): Reaction?`, `pose(now: Long, size: Int): Pose`
    - persistence: `val dirty: Boolean`, `snapshot(now: Long): MoodState` (clears dirty)

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

class PetBrainTest {
    private val t0 = Instant.parse("2026-10-05T12:00:00Z").toEpochMilli()
    private val night = Instant.parse("2026-10-05T23:30:00Z").toEpochMilli()
    private val alwaysBoo = object : Random() { override fun nextBits(bitCount: Int) = 0 }
    private val neverBoo = object : Random() { override fun nextBits(bitCount: Int) = -1 ushr (32 - bitCount) }   // nextInt(5) = 2
    private fun brain(mood: Double, at: Long = t0, random: Random = neverBoo) =
        PetBrain(MoodState(mood, at), { SleepWindow(23, 7) }, ZoneOffset.UTC, random)

    @Test
    fun baseFollowsSleepChargingAndMood() {
        assertEquals(Base.HAPPY, brain(80.0).base(t0)); assertEquals(Base.CONTENT, brain(50.0).base(t0))
        assertEquals(Base.BORED, brain(20.0).base(t0)); assertEquals(Base.SAD, brain(5.0).base(t0))
        assertEquals(Base.ASLEEP, brain(80.0, night).base(night))
        val b = brain(80.0); b.onCharging(true, 40, t0); assertEquals(Base.MUNCH, b.base(t0))
    }

    @Test
    fun petCooldown() {
        val b = brain(50.0)
        b.onLongPress(t0, true); assertEquals(Reaction.PET, b.reaction(t0)); assertEquals(65, b.mood(t0))
        b.onLongPress(t0 + 10_000, true); assertEquals(Reaction.PET, b.reaction(t0 + 10_000)); assertEquals(65, b.mood(t0 + 10_000))
        b.onLongPress(t0 + 31_000, true); assertEquals(80, b.mood(t0 + 31_000))
        assertNull(b.reaction(t0 + 31_000 + 2600))
    }

    @Test
    fun yawnAtNightKeepsHimAwakeAMinute() {
        val b = brain(50.0, night)
        b.onLongPress(night, true)
        assertEquals(Reaction.YAWN, b.reaction(night)); assertEquals(55, b.mood(night))
        assertEquals(Base.CONTENT, b.base(night + 4_000))
        assertEquals(Base.ASLEEP, b.base(night + 61_000))
    }

    @Test
    fun shakesDizzyThenAngryThenCalmed() {
        val b = brain(50.0)
        b.onShake(5f, t0, true); assertNull(b.reaction(t0))                         // not a big shake
        b.onShake(15f, t0, true); assertEquals(Reaction.DIZZY, b.reaction(t0)); assertEquals(47, b.mood(t0))
        b.onShake(15f, t0 + 1_000, true); assertEquals(47, b.mood(t0 + 1_000))     // loss cooldown
        b.onShake(15f, t0 + 2_000, true); assertEquals(Reaction.ANGRY, b.reaction(t0 + 2_000)); assertEquals(42, b.mood(t0 + 2_000))
        b.onLongPress(t0 + 3_000, true); assertEquals(Reaction.CALMED, b.reaction(t0 + 3_000)); assertEquals(52, b.mood(t0 + 3_000))
    }

    @Test
    fun onlyCalmedReplacesAngry() {
        val b = brain(50.0)
        repeat(3) { b.onShake(15f, t0 + it * 100L, true) }
        assertEquals(Reaction.ANGRY, b.reaction(t0 + 300))
        b.onGravity(0f, 0f, 9.8f, t0 + 400, true); b.onGravity(0f, 0f, 9.8f, t0 + 2_500, true)
        b.onGravity(0f, 0f, -9.8f, t0 + 2_600, true); b.onGravity(0f, 0f, -9.8f, t0 + 3_200, true)   // a peek would fire here
        assertEquals(Reaction.ANGRY, b.reaction(t0 + 3_200))
    }

    @Test
    fun peekOnlyOncePerFaceDown() {
        val b = brain(50.0)
        b.onGravity(0f, 0f, 9.8f, t0, true); b.onGravity(0f, 0f, 9.8f, t0 + 2_500, true)
        b.onGravity(0f, 0f, -9.8f, t0 + 2_600, true); assertNull(b.reaction(t0 + 2_600))
        b.onGravity(0f, 0f, -9.8f, t0 + 3_200, true); assertEquals(Reaction.PEEK, b.reaction(t0 + 3_200)); assertEquals(55, b.mood(t0 + 3_200))
        b.onGravity(0f, 0f, -9.8f, t0 + 9_000, true); assertNull(b.reaction(t0 + 9_000))            // still face-down: no repeat
    }

    @Test
    fun quickFlipDoesNotPeek() {
        val b = brain(50.0)
        b.onGravity(0f, 0f, 9.8f, t0, true); b.onGravity(0f, 0f, 9.8f, t0 + 500, true)
        b.onGravity(0f, 0f, -9.8f, t0 + 600, true); b.onGravity(0f, 0f, -9.8f, t0 + 1_300, true)
        assertNull(b.reaction(t0 + 1_300))
    }

    @Test
    fun chargingAddsAPointPerMinuteWhileBound() {
        val b = brain(50.0)
        b.onCharging(true, 40, t0)
        b.tick(t0 + 150_000, true)
        assertEquals(52, b.mood(t0 + 150_000))       // +2 minutes, −0.42 decay
        assertEquals(40, b.pose(t0 + 150_000, 25).level)
    }

    @Test
    fun aodEventsChangeMoodOnly() {
        val b = brain(50.0)
        b.onLongPress(t0, active = false)
        assertNull(b.reaction(t0)); assertEquals(65, b.mood(t0))
        b.onShake(15f, t0 + 100, false); assertNull(b.reaction(t0 + 100))
    }

    @Test
    fun booOnlyWhenHappyActiveAndRarely() {
        val b = brain(90.0, random = alwaysBoo)
        b.tick(t0, false); assertNull(b.reaction(t0))                               // AOD: no boo
        b.tick(t0 + 1, true); assertEquals(Reaction.BOO, b.reaction(t0 + 1))
        b.tick(t0 + 120_000, true); assertNull(b.reaction(t0 + 120_000))            // within 10 min
        b.tick(t0 + 601_000, true); assertEquals(Reaction.BOO, b.reaction(t0 + 601_000))
        val c = brain(50.0, random = alwaysBoo); c.tick(t0, true); assertNull(c.reaction(t0))   // not happy
    }

    @Test
    fun tiltLooksAndLeans() {
        val b = brain(50.0)
        b.onGravity(9.8f, -9.8f, 0f, t0, true)
        val p = b.pose(t0, 25)
        assertEquals(1, p.lookX); assertEquals(-1, p.lookY); assertEquals(1, p.lean)
        assertEquals(0, b.pose(t0, 13).lookY)
        b.onLongPress(t0, true)
        assertEquals(0, b.pose(t0, 25).lookX)                                     // no tilt while reacting
    }

    @Test
    fun snapshotClearsDirty() {
        val b = brain(50.0)
        assertFalse(b.dirty)
        b.onLongPress(t0, true); assertTrue(b.dirty)
        val s = b.snapshot(t0)
        assertEquals(65.0, s.mood, 1e-9); assertEquals(t0, s.at); assertFalse(b.dirty)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.pet.PetBrainTest'`
Expected: compilation FAIL, because `PetBrain`, `Base` and `Reaction` are unresolved.

- [ ] **Step 3: Write the implementation**

```kotlin
package app.backlit.pet

import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

enum class Base { HAPPY, CONTENT, BORED, SAD, ASLEEP, MUNCH }

enum class Reaction(val ms: Long) { PET(2600), YAWN(3600), DIZZY(2000), ANGRY(6000), CALMED(4000), PEEK(3200), BOO(2600) }

data class Pose(
    val base: Base,
    val reaction: Reaction?,
    val reactionStart: Long,
    val lookX: Int,
    val lookY: Int,
    val lean: Int,
    val level: Int,
)

/**
 * The pet's state machine. Pure: callers pass wall-clock time and whether the toy is ACTIVE (screen on) —
 * events in AOD only change mood, never start a reaction.
 */
class PetBrain(
    initial: MoodState,
    private val sleep: () -> SleepWindow,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val random: Random = Random.Default,
) {
    private var mood = PetMood.clamp(initial.mood)
    private var moodAt = initial.at
    var dirty = false
        private set

    private var reaction: Reaction? = null
    private var reactionStart = 0L
    private var lastPetGain = Long.MIN_VALUE / 2
    private var lastShakeLoss = Long.MIN_VALUE / 2
    private var lastDizzy = Long.MIN_VALUE / 2
    private val bigShakes = ArrayDeque<Long>()
    private var awakeUntil = 0L
    private var charging = false
    private var level = 0
    private var chargeAt = 0L
    private var chargeAccum = 0L
    private var faceDownSince: Long? = null
    private var notDownSince: Long? = null
    private var peekArmed = false
    private var gx = 0f
    private var gy = 0f
    private var lastBoo = Long.MIN_VALUE / 2
    private var nextBooCheck = Long.MIN_VALUE / 2

    private fun current(now: Long) = PetMood.decay(MoodState(mood, moodAt), now, sleep(), zone)

    private fun change(now: Long, delta: Double) {
        mood = PetMood.clamp(current(now) + delta)
        moodAt = now
        dirty = true
    }

    fun mood(now: Long): Int = current(now).roundToInt()

    fun snapshot(now: Long): MoodState {
        mood = current(now)
        moodAt = now
        dirty = false
        return MoodState(mood, now)
    }

    private fun asleep(now: Long) = sleep().contains(now, zone) && now >= awakeUntil

    fun base(now: Long): Base {
        val m = mood(now)
        return when {
            asleep(now) -> Base.ASLEEP
            charging -> Base.MUNCH
            m >= 70 -> Base.HAPPY
            m >= 40 -> Base.CONTENT
            m >= 15 -> Base.BORED
            else -> Base.SAD
        }
    }

    fun reaction(now: Long): Reaction? {
        val r = reaction ?: return null
        return if (now - reactionStart < r.ms) r else null
    }

    private fun start(r: Reaction, now: Long, active: Boolean) {
        if (!active) return
        val cur = reaction(now)
        if (cur == Reaction.ANGRY && r != Reaction.CALMED && r != Reaction.ANGRY) return
        reaction = r
        reactionStart = now
    }

    fun onLongPress(now: Long, active: Boolean) {
        if (reaction(now) == Reaction.ANGRY) {
            start(Reaction.CALMED, now, active)
            change(now, 10.0)
            return
        }
        if (asleep(now)) {
            awakeUntil = now + 60_000
            start(Reaction.YAWN, now, active)
            change(now, 5.0)
            return
        }
        start(Reaction.PET, now, active)
        if (now - lastPetGain >= 30_000) {
            lastPetGain = now
            change(now, 15.0)
        }
    }

    fun onShake(magnitude: Float, now: Long, active: Boolean) {
        if (magnitude < 12f) return
        bigShakes.addLast(now)
        while (bigShakes.isNotEmpty() && now - bigShakes.first() > 10_000) bigShakes.removeFirst()
        if (bigShakes.size >= 3) {
            bigShakes.clear()
            start(Reaction.ANGRY, now, active)
            change(now, -5.0)
            return
        }
        if (now - lastDizzy >= 1_500) {
            lastDizzy = now
            start(Reaction.DIZZY, now, active)
        }
        if (now - lastShakeLoss >= 10_000) {
            lastShakeLoss = now
            change(now, -3.0)
        }
    }

    fun onGravity(x: Float, y: Float, z: Float, now: Long, active: Boolean) {
        gx = x
        gy = y
        if (z <= -7f) {
            notDownSince = null
            val since = faceDownSince ?: now.also { faceDownSince = it }
            if (peekArmed && now - since >= 500) {
                peekArmed = false
                start(Reaction.PEEK, now, active)
                change(now, 5.0)
            }
        } else {
            faceDownSince = null
            val since = notDownSince ?: now.also { notDownSince = it }
            if (now - since >= 2_000) peekArmed = true
        }
    }

    fun onCharging(on: Boolean, level: Int, now: Long) {
        this.level = level.coerceIn(0, 100)
        if (on && !charging) chargeAt = now
        if (!on && charging) chargeAccum = 0
        charging = on
    }

    fun tick(now: Long, active: Boolean) {
        if (charging) {
            chargeAccum += (now - chargeAt).coerceAtLeast(0)
            chargeAt = now
            val minutes = chargeAccum / 60_000
            if (minutes > 0) {
                chargeAccum -= minutes * 60_000
                change(now, minutes.toDouble())
            }
        }
        if (active && reaction(now) == null && base(now) == Base.HAPPY && now - lastBoo >= 600_000 && now >= nextBooCheck) {
            nextBooCheck = now + 60_000
            if (random.nextInt(5) == 0) {
                lastBoo = now
                start(Reaction.BOO, now, active)
            }
        }
    }

    fun pose(now: Long, size: Int): Pose {
        val r = reaction(now)
        val still = r != null
        val lx = if (still) 0 else (gx / 9.8f).roundToInt().coerceIn(-1, 1)
        val ly = if (still || size < 25) 0 else (gy / 9.8f).roundToInt().coerceIn(-1, 1)
        val lean = if (still || abs(gx) <= 4.9f) 0 else if (gx > 0) 1 else -1
        return Pose(base(now), r, reactionStart, lx, ly, lean, level)
    }
}
```

Note: the tilt sign (`gx > 0` leans right) is a raw-axis mapping. If the device check shows the eyes moving the wrong way, flip the signs in `pose()`, update `tiltLooksAndLeans`, and record a ruling.

- [ ] **Step 4: Run the test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.pet.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/pet/PetBrain.kt app/src/test/java/app/backlit/pet/PetBrainTest.kt
git commit -m "feat(pet): pet state machine (mood, reactions, sleep, tilt, peekaboo, boo)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: GhostArt

**Files:**
- Create: `app/src/main/java/app/backlit/pet/GhostArt.kt`
- Test: `app/src/test/java/app/backlit/pet/GhostArtTest.kt`

**Interfaces:**
- Consumes: `Base`, `Reaction`, `Pose` (Task 2), and `ChargeKit.circlePoints/line/dot/set` (existing in `app.backlit.render.charge`).
- Produces: `object GhostArt` with `fun frame(size: Int, pose: Pose, now: Long): PixelGrid` and `fun still(size: Int, pose: Pose, minuteOfHour: Int): PixelGrid`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostArtTest {
    private fun pose(base: Base = Base.CONTENT, r: Reaction? = null, level: Int = 50) = Pose(base, r, 0L, 0, 0, 0, level)

    @Test
    fun everyPoseRendersInsideTheMask() {
        for (size in listOf(25, 13)) {
            for (b in Base.entries) for (t in 0L..6000L step 200) check(GhostArt.frame(size, pose(b), t))
            for (r in Reaction.entries) for (t in 0L..r.ms step 100) check(GhostArt.frame(size, pose(r = r), t))
            for (b in Base.entries) for (m in 0..3) check(GhostArt.still(size, pose(b), m))
        }
    }

    private fun check(g: app.backlit.render.PixelGrid) {
        assertTrue(g.litCount() > 0)                                  // peekaboo's first frame is mostly below the panel
        for (y in 0 until g.size) for (x in 0 until g.size) if (g[x, y] > 0) assertTrue(g.hasLed(x, y))
    }

    @Test
    fun idleStillIsSymmetricAboveTheHem() {
        val g = GhostArt.still(25, pose(Base.CONTENT), 0)
        for (y in 0..19) for (x in 0 until 25) assertEquals("x=$x y=$y", g[x, y], g[24 - x, y])
        val s = GhostArt.still(13, pose(Base.CONTENT), 0)
        for (y in 0..9) for (x in 0 until 13) assertEquals("13 x=$x y=$y", s[x, y], s[12 - x, y])
    }

    @Test
    fun chargingStillFillsWithTheLevel() {
        fun dim(level: Int) = GhostArt.still(25, pose(Base.MUNCH, level = level), 0).raw().count { it in 70..80 }   // 0.3 → 77
        assertTrue(dim(30) < dim(62)); assertTrue(dim(62) < dim(95))
    }

    @Test
    fun contentEyesAreOpenAndBodyOutlineIsLit() {
        val g = GhostArt.frame(25, pose(Base.CONTENT), 2199)         // t ≈ 700π: float 0, not blinking (blink is t % 3200 < 160)
        for ((x, y) in listOf(9 to 10, 10 to 12, 14 to 10, 15 to 12)) assertEquals(255, g[x, y])
        assertTrue(g[12, 4] >= 200)                                   // dome top (12, 11−7)
        assertTrue(g[5, 15] >= 200 && g[19, 15] >= 200)               // sides
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `.superpowers/runtests.sh --tests 'app.backlit.pet.GhostArtTest'`
Expected: compilation FAIL, because `GhostArt` is unresolved.

- [ ] **Step 3: Write the implementation.** This is a line-for-line port of `docs/superpowers/mockups/2026-10-05-pet-ghost-faces.html` and `-aod.html` (option A).

```kotlin
package app.backlit.pet

import app.backlit.render.PixelGrid
import app.backlit.render.charge.ChargeKit
import app.backlit.render.charge.ChargeKit.dot
import app.backlit.render.charge.ChargeKit.set
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** The ghost pet, ported from the approved mockup. Brightness 0..1 like the mockup; ChargeKit maps it to 0..255. */
object GhostArt {
    private val HEART = listOf("101", "111", "010")
    private val Z = listOf("111", "010", "111")
    private val BOLT = listOf("01", "11", "10")
    private val LOOKS = listOf(0 to 0, 1 to 0, 0 to 1, -1 to 0)

    private data class Body(val dx: Int = 0, val dy: Int = 0, val b: Double = 0.85, val puff: Boolean = false, val hemSpeed: Long = 200, val droop: Boolean = false)

    fun frame(size: Int, pose: Pose, now: Long): PixelGrid {
        val g = PixelGrid(size)
        val r = pose.reaction
        if (r != null) {
            val t = (now - pose.reactionStart).coerceAtLeast(0)
            when (r) {
                Reaction.PET -> pet(g, t)
                Reaction.YAWN -> yawn(g, t)
                Reaction.DIZZY -> dizzy(g, t)
                Reaction.ANGRY -> angry(g, t)
                Reaction.CALMED -> calmed(g, t)
                Reaction.PEEK -> peek(g, t)
                Reaction.BOO -> boo(g, t)
            }
            return g
        }
        val t = now.coerceAtLeast(0)
        when (pose.base) {
            Base.HAPPY -> happy(g, t, pose.lean)
            Base.CONTENT -> content(g, t, pose)
            Base.BORED -> bored(g, t)
            Base.SAD -> sad(g, t)
            Base.ASLEEP -> sleep(g, t)
            Base.MUNCH -> munch(g, t, pose.lean)
        }
        return g
    }

    fun still(size: Int, pose: Pose, minuteOfHour: Int): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        when (pose.base) {
            Base.ASLEEP -> {
                body(g, 0, Body(b = 0.55, hemSpeed = 0)); eyes(g, "closed"); mouth(g, "small")
                if (big) bm(g, Z, 19, 3, 0.8) else g.p(10, 1, 0.8)
            }
            Base.MUNCH -> {
                body(g, 0, Body(hemSpeed = 0))
                val lv = pose.level / 100.0
                if (big) {
                    val fillTop = (20 - 9 * lv).px()
                    for (y in max(fillTop, 9)..20) for (x in 6..18) g.p(x, y, 0.3)
                } else {
                    val fillTop = (10 - 5 * lv).px()
                    for (y in fillTop..10) for (x in 3..9) g.p(x, y, 0.3)
                }
                eyes(g, "happy"); mouth(g, "smile")
            }
            else -> {
                body(g, 0, Body(hemSpeed = 0))
                val (lx, ly) = LOOKS[((minuteOfHour % 4) + 4) % 4]
                eyes(g, if (pose.base == Base.SAD) "sad" else "open", px = lx, py = if (big) ly else 0)
                mouth(g, when (pose.base) { Base.HAPPY -> "wide"; Base.BORED -> "flat"; Base.SAD -> "frown"; else -> "small" })
            }
        }
        return g
    }

    // ── resting faces ──

    private fun happy(g: PixelGrid, t: Long, lean: Int) {
        val big = g.size >= 25
        val c = t % 2600
        val hop = if (c < 500) (-sin(c / 500.0 * PI) * (if (big) 2 else 1)).px() else float(t, big)
        body(g, t, Body(dx = lean, dy = hop, hemSpeed = 120)); eyes(g, "happy", dx = lean, dy = hop); mouth(g, "wide", dx = lean, dy = hop); blush(g, dx = lean, dy = hop)
    }

    private fun content(g: PixelGrid, t: Long, pose: Pose) {
        val dy = float(t, g.size >= 25)
        body(g, t, Body(dx = pose.lean, dy = dy))
        eyes(g, if (t % 3200 < 160) "blink" else "open", dx = pose.lean, dy = dy, px = pose.lookX, py = pose.lookY)
        mouth(g, "small", dx = pose.lean, dy = dy)
    }

    private fun bored(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val dy = float(t, big, 0.5)
        val yawning = t % 7000 > 5800
        body(g, t, Body(dy = dy, hemSpeed = 450))
        eyes(g, if (yawning) "closed" else "half", dy = dy, px = if (big) 1 else 0)
        mouth(g, if (yawning) "O" else "flat", dy = dy)
    }

    private fun sad(g: PixelGrid, t: Long) {
        val sigh = t % 5200 > 4300
        val dy = if (sigh) 1 else 0
        body(g, t, Body(dy = dy, hemSpeed = 0, b = 0.7, droop = true)); eyes(g, "sad", dy = dy); mouth(g, "frown", dy = dy)
        val tr = (t % 2400) / 2400.0
        if (g.size >= 25) g.dot(15.0, 13 + tr * 4, 0.6 * (1 - tr)) else g.dot(9.0, 7 + tr * 3, 0.6 * (1 - tr))
    }

    private fun sleep(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val br = 0.5 + 0.25 * (0.5 + 0.5 * sin(t / 900.0))
        val dy = float(t, big, 0.5)
        body(g, t, Body(dy = dy, b = br, hemSpeed = 600)); eyes(g, "closed", dy = dy); mouth(g, "small", dy = dy)
        for (i in 0..1) {
            val ph = ((t / 2400.0) + i * 0.5) % 1.0
            if (big) bm(g, Z, 18 + (ph * 3).px(), (5 - ph * 5).px(), 1 - ph) else g.p(10 + ph.px(), (2 - ph * 2).px(), 1 - ph)
        }
    }

    private fun munch(g: PixelGrid, t: Long, lean: Int) {
        val big = g.size >= 25
        val c = t % 2200
        val dy = float(t, big)
        body(g, t, Body(dx = lean, dy = dy, hemSpeed = 120))
        eyes(g, if (c > 1000) "happy" else "open", dx = lean, dy = dy)
        if (c < 1000) {
            val x = (if (big) 23.0 else 12.0) - c / 1000.0 * (if (big) 9 else 5)
            if (big) bm(g, BOLT, x.px(), 14 + dy, 1.0) else g.p(x.px(), 8 + dy, 1.0)
            mouth(g, "O", dx = lean, dy = dy)
        } else {
            mouth(g, if ((c / 180) % 2 == 1L) "chomp" else "flat", dx = lean, dy = dy)
            if (big) { g.dot(16 + (c / 60.0) % 3, 17.0 + dy, 0.4); g.dot(8 - (c / 80.0) % 2, 17.0 + dy, 0.35) }
        }
    }

    // ── reactions ──

    private fun pet(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val dy = float(t, big)
        body(g, t, Body(dy = dy, hemSpeed = 100)); eyes(g, "happy", dy = dy); mouth(g, "wide", dy = dy); blush(g, dy = dy, v = 0.6)
        hearts(g, t, if (big) 3 else 2)
    }

    private fun dizzy(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val dx = (sin(t / 110.0) * (if (big) 1.4 else 1.0)).px()
        val dy = float(t, big)
        body(g, t, Body(dx = dx, dy = dy, hemSpeed = 60)); eyes(g, "swirl", dx = dx, dy = dy, t = t); mouth(g, "zig", dx = dx, dy = dy)
    }

    private fun angry(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val sh = if ((t / 90) % 2 == 1L && t % 1500 < 500) 1 else 0
        body(g, t, Body(dx = sh, puff = true, hemSpeed = 70)); eyes(g, "angry", dx = sh); mouth(g, "zig", dx = sh)
        for (i in 0..1) {
            val ph = ((t / 900.0) + i * 0.5) % 1.0
            val side = if (i == 1) 1 else -1
            if (big) {
                val x = 12 + side * (8 + ph * 3)
                val y = 4 - ph * 4
                g.dot(x, y, 1 - ph); g.dot(x + side, y, 0.7 * (1 - ph)); g.dot(x, y - 1, 0.6 * (1 - ph))
            } else {
                g.dot(6 + side * (5 + ph * 1.5), 1 - ph * 2, 1 - ph)
            }
        }
    }

    private fun calmed(g: PixelGrid, t: Long) {
        if (t % 4000 < 1600) {
            body(g, t, Body(hemSpeed = 300)); eyes(g, "closed"); mouth(g, "small")
            if (g.size >= 25) { g.s(8, 9); g.s(9, 9); g.s(15, 9); g.s(16, 9) }
        } else {
            pet(g, t)
        }
    }

    private fun peek(g: PixelGrid, t: Long) {
        val big = g.size >= 25
        val c = t % 3200
        val off = if (c < 500) ((1 - c / 500.0) * (if (big) 14 else 8)).px() else 0
        body(g, t, Body(dy = off, hemSpeed = 80)); eyes(g, if (c < 500) "open" else "wide", dy = off); mouth(g, if (c < 500) "small" else "O", dy = off)
        if (c > 500 && (c / 250) % 2 == 1L) {
            if (big) { for (y in 3..6) g.p(21, y, 1.0); g.p(21, 8, 1.0) } else { g.p(11, 3, 1.0); g.p(11, 5, 1.0) }
        }
    }

    private fun yawn(g: PixelGrid, t: Long) {
        val c = t % 3600
        val dy = float(t, g.size >= 25, 0.5)
        body(g, t, Body(dy = dy, hemSpeed = 500))
        if (c < 1800) { eyes(g, "closed", dy = dy); mouth(g, "O", dy = dy) } else { eyes(g, "half", dy = dy); mouth(g, "small", dy = dy) }
    }

    private fun boo(g: PixelGrid, t: Long) {
        val c = t % 2600
        body(g, t, Body(puff = c < 900, hemSpeed = 60)); eyes(g, "wide"); mouth(g, "O")
    }

    // ── parts ──

    private fun body(g: PixelGrid, t: Long, o: Body) {
        val step = if (o.hemSpeed > 0) (t / o.hemSpeed).toInt() else 0
        if (g.size >= 25) {
            val r = if (o.puff) 8 else 7
            val l = 12 - r
            val rr = 12 + r
            val top = 11
            ChargeKit.circlePoints(12 + o.dx, top + o.dy, r).forEach { (x, y) -> if (y <= top + o.dy) g.p(x, y, o.b) }
            val bot = 19 + if (o.droop) 1 else 0
            ChargeKit.line(g, (l + o.dx).toDouble(), (top + o.dy).toDouble(), (l + o.dx).toDouble(), (bot + o.dy).toDouble(), o.b)
            ChargeKit.line(g, (rr + o.dx).toDouble(), (top + o.dy).toDouble(), (rr + o.dx).toDouble(), (bot + o.dy).toDouble(), o.b)
            for (x in l..rr) { val w = if ((x + step) % 4 < 2) 0 else 1; g.p(x + o.dx, bot + 1 + o.dy + w, o.b) }
        } else {
            val r = if (o.puff) 5 else 4
            val l = 6 - r
            val rr = 6 + r
            val top = 6
            ChargeKit.circlePoints(6 + o.dx, top + o.dy, r).forEach { (x, y) -> if (y <= top + o.dy) g.p(x, y, o.b) }
            ChargeKit.line(g, (l + o.dx).toDouble(), (top + o.dy).toDouble(), (l + o.dx).toDouble(), (10 + o.dy).toDouble(), o.b)
            ChargeKit.line(g, (rr + o.dx).toDouble(), (top + o.dy).toDouble(), (rr + o.dx).toDouble(), (10 + o.dy).toDouble(), o.b)
            for (x in l..rr) { val w = if ((x + step) % 2 != 0) 1 else 0; g.p(x + o.dx, 11 + o.dy - w, o.b) }
        }
    }

    private fun eyes(g: PixelGrid, kind: String, dx: Int = 0, dy: Int = 0, px: Int = 0, py: Int = 0, t: Long = 0) {
        if (g.size >= 25) {
            val ey = 10 + dy
            for (ex in listOf(9 + dx, 14 + dx)) {
                val left = ex < 12 + dx
                when (kind) {
                    "open" -> for (a in 0..1) for (c in 0..2) g.s(ex + a + px, ey + c + py)
                    "blink", "closed" -> { g.s(ex, ey + 2); g.s(ex + 1, ey + 2) }
                    "half" -> { g.s(ex + px, ey + 2); g.s(ex + 1 + px, ey + 2); g.s(ex + px, ey + 1); g.s(ex + 1 + px, ey + 1) }
                    "happy" -> { g.s(ex - 1, ey + 2); g.s(ex, ey + 1); g.s(ex + 1, ey + 1); g.s(ex + 2, ey + 2) }
                    "sad" -> if (left) { g.s(ex + 1, ey + 1); g.s(ex, ey + 2); g.s(ex - 1, ey + 2, 0.5) } else { g.s(ex, ey + 1); g.s(ex + 1, ey + 2); g.s(ex + 2, ey + 2, 0.5) }
                    "swirl" -> {
                        val ph = ((t / 110) % 6).toInt()
                        listOf(0 to 0, 1 to 0, 1 to 1, 1 to 2, 0 to 2, 0 to 1).forEachIndexed { k, (a, c) -> g.s(ex + a, ey + c, if (k == ph) 0.15 else 1.0) }
                    }
                    "angry" -> for (a in 0..1) for (c in 1..2) g.s(ex + a, ey + c)
                    "wide" -> for (a in -1..2) for (c in -1..2) if (!((a == -1 || a == 2) && (c == -1 || c == 2))) g.s(ex + a, ey + c)
                }
            }
            if (kind == "angry") { g.s(8 + dx, 9 + dy); g.s(9 + dx, 9 + dy); g.s(10 + dx, 10 + dy); g.s(16 + dx, 9 + dy); g.s(15 + dx, 9 + dy); g.s(14 + dx, 10 + dy) }
        } else {
            val ey = 6 + dy
            for (ex in listOf(4 + dx, 8 + dx)) {
                val left = ex < 6 + dx
                when (kind) {
                    "open", "angry", "wide" -> g.s(ex + px, ey + py)
                    "blink", "closed", "half" -> g.s(ex, ey, 0.45)
                    "happy" -> { g.s(ex - 1, ey); g.s(ex, ey - 1); g.s(ex + 1, ey) }
                    "sad" -> { g.s(ex, ey); g.s(ex + if (left) -1 else 1, ey + 1, 0.6) }
                    "swirl" -> {
                        val ph = ((t / 120) % 4).toInt()
                        val (ddx, ddy) = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)[ph]
                        g.s(ex, ey, 0.4); g.s(ex + ddx, ey + ddy)
                    }
                }
            }
            if (kind == "angry") { g.s(5 + dx, 5 + dy); g.s(7 + dx, 5 + dy) }
        }
    }

    private val MOUTH25 = mapOf(
        "small" to listOf(12 to 15), "smile" to listOf(11 to 15, 12 to 16, 13 to 15),
        "wide" to listOf(10 to 15, 11 to 16, 12 to 16, 13 to 16, 14 to 15), "flat" to listOf(11 to 15, 12 to 15, 13 to 15),
        "frown" to listOf(11 to 16, 12 to 15, 13 to 16), "O" to listOf(11 to 15, 12 to 14, 13 to 15, 11 to 16, 13 to 16, 12 to 17),
        "zig" to listOf(10 to 16, 11 to 15, 12 to 16, 13 to 15, 14 to 16), "chomp" to listOf(11 to 15, 12 to 15, 13 to 15, 11 to 16, 13 to 16),
    )
    private val MOUTH13 = mapOf(
        "small" to listOf(6 to 8), "smile" to listOf(5 to 8, 6 to 9, 7 to 8), "wide" to listOf(4 to 8, 5 to 9, 6 to 9, 7 to 9, 8 to 8),
        "flat" to listOf(5 to 8, 6 to 8, 7 to 8), "frown" to listOf(5 to 9, 6 to 8, 7 to 9), "O" to listOf(6 to 8, 5 to 9, 7 to 9),
        "zig" to listOf(5 to 9, 6 to 8, 7 to 9), "chomp" to listOf(5 to 8, 6 to 8, 7 to 8),
    )

    private fun mouth(g: PixelGrid, kind: String, dx: Int = 0, dy: Int = 0) =
        (if (g.size >= 25) MOUTH25 else MOUTH13).getValue(kind).forEach { (x, y) -> g.s(x + dx, y + dy) }

    private fun blush(g: PixelGrid, dx: Int = 0, dy: Int = 0, v: Double = 0.35) {
        if (g.size >= 25) { g.p(7 + dx, 14 + dy, v); g.p(17 + dx, 14 + dy, v) } else { g.p(3 + dx, 8 + dy, v); g.p(9 + dx, 8 + dy, v) }
    }

    private fun hearts(g: PixelGrid, t: Long, count: Int) {
        val big = g.size >= 25
        for (i in 0 until count) {
            val ph = ((t / 1300.0) + i.toDouble() / count) % 1.0
            val hx = if (big) (if (i % 2 == 1) 20 else 2) else (if (i % 2 == 1) 11 else 1)
            val hy = (if (big) 11.0 else 6.0) - ph * (if (big) 9 else 5)
            if (big) bm(g, HEART, hx, hy.px(), 1 - ph * 0.7) else g.p(hx, hy.px(), 1 - ph * 0.7)
        }
    }

    private fun float(t: Long, big: Boolean, amp: Double = 1.0): Int = (sin(t / 700.0) * (if (big) 1.0 else 0.6) * amp).px()

    private fun bm(g: PixelGrid, rows: List<String>, x0: Int, y0: Int, v: Double) =
        rows.forEachIndexed { j, r -> r.forEachIndexed { i, ch -> if (ch == '1') g.p(x0 + i, y0 + j, v) } }

    private fun PixelGrid.p(x: Int, y: Int, v: Double) = dot(x.toDouble(), y.toDouble(), v)
    private fun PixelGrid.s(x: Int, y: Int, v: Double = 1.0) = set(x.toDouble(), y.toDouble(), v)
}
```

Port note: the mockup's charging fill computes `fillTop = round(bot − (bot − top) × lv)` with bot = 20 and top = 11, which is `(20 − 9 × lv).px()`. Its `Math.round` equals `.px()` (round half up).

- [ ] **Step 4: Run the test to verify it passes**

Run: `.superpowers/runtests.sh --tests 'app.backlit.pet.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/pet/GhostArt.kt app/src/test/java/app/backlit/pet/GhostArtTest.kt
git commit -m "feat(pet): ghost art for every mood, reaction and AOD still

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Pet preview ids and settings

**Files:**
- Create: `app/src/main/java/app/backlit/pet/PetPreviewAnimation.kt`
- Modify: `app/src/main/java/app/backlit/alerts/AlertsRuntime.kt` (`animationFor`)
- Modify: `app/src/main/java/app/backlit/data/Settings.kt`, `SettingsRepo.kt`
- Modify: `app/build.gradle.kts` (`buildConfig = true`)
- Test: `app/src/test/java/app/backlit/pet/PetPreviewAnimationTest.kt`, `app/src/test/java/app/backlit/data/SettingsRepoTest.kt`

**Interfaces:**
- Consumes: `GhostArt`, `Base`, `Reaction`, `Pose` (Tasks 2–3).
- Produces:
  - `class PetPreviewAnimation(base: Base?, reaction: Reaction?) : GlyphAnimation`, with companion `fun parse(id: String): PetPreviewAnimation?` and `fun idFor(base: Base): String`
  - `Settings.petName: String = "Boo"`, `petMood: Float = 70f`, `petMoodAt: Long = 0L`, `petSleepStart: Int = 23`, `petSleepEnd: Int = 7`, `petToyEverBound: Boolean = false`
  - `SettingsRepo.cleanPetName(s: String): String`

- [ ] **Step 1: Write the failing tests**

`PetPreviewAnimationTest.kt`:

```kotlin
package app.backlit.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PetPreviewAnimationTest {
    @Test
    fun parsesBasesAndReactions() {
        val h = PetPreviewAnimation.parse("pet:happy")!!
        assertEquals("pet:happy", h.id); assertEquals(3000L, h.loopMs)
        assertEquals(GhostArt.frame(25, Pose(Base.HAPPY, null, 0, 0, 0, 0, 62), 400), h.frame(25, 400))
        val d = PetPreviewAnimation.parse("pet:dizzy")!!
        assertEquals(2000L, d.loopMs)
        assertEquals(GhostArt.frame(13, Pose(Base.CONTENT, Reaction.DIZZY, 0, 0, 0, 0, 62), 300), d.frame(13, 300))
        assertEquals("pet:munch", PetPreviewAnimation.idFor(Base.MUNCH))
        assertNull(PetPreviewAnimation.parse("pet:nope")); assertNull(PetPreviewAnimation.parse("charge:moon:still"))
    }
}
```

Add this to `SettingsRepoTest.roundTripsEveryField` after `canvasDrawingId = "import:q", canvasToyEverBound = true,`:

```kotlin
            petName = "Casper", petMood = 42.5f, petMoodAt = 123_456L, petSleepStart = 22, petSleepEnd = 6, petToyEverBound = true,
```

Add these tests:

```kotlin
    @Test
    fun petDefaults() = runBlocking {
        val s = repo().settings.first()
        assertEquals("Boo", s.petName); assertEquals(70f, s.petMood); assertEquals(0L, s.petMoodAt)
        assertEquals(23, s.petSleepStart); assertEquals(7, s.petSleepEnd); assertEquals(false, s.petToyEverBound)
    }

    @Test
    fun petNameIsCleaned() {
        assertEquals("Boo", SettingsRepo.cleanPetName("   "))
        assertEquals("Spooky Ghost", SettingsRepo.cleanPetName("  Spooky Ghost  "))
        assertEquals("ABCDEFGHIJKL", SettingsRepo.cleanPetName("ABCDEFGHIJKLMNOP"))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.pet.PetPreviewAnimationTest' --tests 'app.backlit.data.SettingsRepoTest'`
Expected: compilation FAIL, because `PetPreviewAnimation` is unresolved and there's no parameter `petName`.

- [ ] **Step 3: Write the implementation**

`PetPreviewAnimation.kt`:

```kotlin
package app.backlit.pet

import app.backlit.anim.GlyphAnimation
import app.backlit.render.PixelGrid

/** A pet pose as a GlyphAnimation, for "Show on Glyph" via the Alerts preview path. Ids: pet:<base|reaction>. */
class PetPreviewAnimation(private val base: Base?, private val reaction: Reaction?) : GlyphAnimation {
    override val id: String = "pet:" + (reaction?.name ?: base?.name ?: Base.CONTENT.name).lowercase()
    override val name: String = "Pet"
    override val loopMs: Long = reaction?.ms ?: 3000L

    override fun frame(size: Int, tMs: Long): PixelGrid =
        GhostArt.frame(size, Pose(base ?: Base.CONTENT, reaction, 0, 0, 0, 0, 62), tMs)

    companion object {
        fun idFor(base: Base) = "pet:" + base.name.lowercase()

        fun parse(id: String): PetPreviewAnimation? {
            if (!id.startsWith("pet:")) return null
            val key = id.removePrefix("pet:")
            Reaction.entries.firstOrNull { it.name.lowercase() == key }?.let { return PetPreviewAnimation(null, it) }
            Base.entries.firstOrNull { it.name.lowercase() == key }?.let { return PetPreviewAnimation(it, null) }
            return null
        }
    }
}
```

In `AlertsRuntime.animationFor`, add the pet parser after the charge parser:

```kotlin
            ?: ChargePreviewAnimation.parse(alert.animationId)
            ?: PetPreviewAnimation.parse(alert.animationId)
```

Then add `import app.backlit.pet.PetPreviewAnimation`.

In `Settings.kt`, add these after `canvasToyEverBound`:

```kotlin
    val petName: String = "Boo",
    val petMood: Float = 70f,
    val petMoodAt: Long = 0L,
    val petSleepStart: Int = 23,
    val petSleepEnd: Int = 7,
    val petToyEverBound: Boolean = false,
```

In `SettingsRepo.kt`, add these keys after `CANVAS_BOUND`. Also add `import androidx.datastore.preferences.core.floatPreferencesKey`.

```kotlin
        private val PET_NAME = stringPreferencesKey("pet_name")
        private val PET_MOOD = floatPreferencesKey("pet_mood")
        private val PET_MOOD_AT = longPreferencesKey("pet_mood_at")
        private val PET_SLEEP_START = intPreferencesKey("pet_sleep_start")
        private val PET_SLEEP_END = intPreferencesKey("pet_sleep_end")
        private val PET_BOUND = booleanPreferencesKey("pet_toy_ever_bound")

        fun cleanPetName(s: String): String = s.trim().take(12).trim().ifBlank { "Boo" }
```

Add these to `toSettings()` after `canvasToyEverBound = ...,`:

```kotlin
                petName = cleanPetName(this[PET_NAME] ?: d.petName),
                petMood = (this[PET_MOOD] ?: d.petMood).coerceIn(0f, 100f),
                petMoodAt = this[PET_MOOD_AT] ?: d.petMoodAt,
                petSleepStart = (this[PET_SLEEP_START] ?: d.petSleepStart).coerceIn(0, 23),
                petSleepEnd = (this[PET_SLEEP_END] ?: d.petSleepEnd).coerceIn(0, 23),
                petToyEverBound = this[PET_BOUND] ?: d.petToyEverBound,
```

Add these to `write()` after `this[CANVAS_BOUND] = ...`:

```kotlin
            this[PET_NAME] = s.petName
            this[PET_MOOD] = s.petMood
            this[PET_MOOD_AT] = s.petMoodAt
            this[PET_SLEEP_START] = s.petSleepStart
            this[PET_SLEEP_END] = s.petSleepEnd
            this[PET_BOUND] = s.petToyEverBound
```

In `app/build.gradle.kts`, change `buildFeatures { compose = true }` to `buildFeatures { compose = true; buildConfig = true }`.

- [ ] **Step 4: Run tests and build**

Run: `.superpowers/runtests.sh -q 2>&1 | tail -1 && source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK`
Expected: `N tests, 0 failures, 0 errors`, then `BUILD_OK`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/pet/PetPreviewAnimation.kt app/src/test/java/app/backlit/pet/PetPreviewAnimationTest.kt app/src/main/java/app/backlit/alerts/AlertsRuntime.kt app/src/main/java/app/backlit/data/ app/src/test/java/app/backlit/data/SettingsRepoTest.kt app/build.gradle.kts
git commit -m "feat(pet): preview ids, pet settings and BuildConfig

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: PetToyService

**Files:**
- Create: `app/src/main/java/app/backlit/glyph/PetToyService.kt`
- Create: `app/src/main/res/drawable/ic_pet_preview.xml`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes:
  - `PetBrain`, `MoodState`, `SleepWindow`, `GhostArt`, `Base` (Tasks 1–3)
  - `Settings.pet*` (Task 4)
  - the existing toy plumbing: `GlyphOutput`, `FramePacer`, `FrameEncoder`, `ModeTracker` (with `msUntilActive`), `ToyPresence`, `AlertsRuntime`
- Produces: the toy service `app.backlit.glyph.PetToyService`.

This is Android glue with no JVM unit test. The behaviour is all in `PetBrain` and `GhostArt`, and is checked on the device in Task 7.

- [ ] **Step 1: Write the service**

```kotlin
package app.backlit.glyph

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
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
import app.backlit.pet.GhostArt
import app.backlit.pet.MoodState
import app.backlit.pet.PetBrain
import app.backlit.pet.SleepWindow
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
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
import java.time.Instant
import java.time.ZoneId
import kotlin.math.sqrt

/** The ghost pet toy: animates while ACTIVE (with motion sensors), still pose in AOD. */
class PetToyService : Service() {

    private var scope: CoroutineScope? = null
    private var renderJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private var settings = Settings()
    private var alerts: AlertsRuntime? = null
    private var brain: PetBrain? = null
    private var sensors: SensorManager? = null
    private var sensorsOn = false
    private var lastSave = 0L
    private val gravity = FloatArray(3)

    private fun now() = System.currentTimeMillis()

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> { brain?.onLongPress(now(), active = !isAod()); kick() }
                GlyphToy.EVENT_AOD -> { modes.onAodEvent(now()); kick() }
            }
        }
    }
    private val messenger = Messenger(handler)
    private val rekick = Runnable { kick() }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val b = brain ?: return
            for (i in 0..2) gravity[i] = 0.8f * gravity[i] + 0.2f * e.values[i]
            val lx = e.values[0] - gravity[0]
            val ly = e.values[1] - gravity[1]
            val lz = e.values[2] - gravity[2]
            val t = now()
            val active = !isAod()
            b.onGravity(gravity[0], gravity[1], gravity[2], t, active)
            b.onShake(sqrt(lx * lx + ly * ly + lz * lz), t, active)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = applyBattery(intent)
    }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        if (profile == DeviceProfile.UNSUPPORTED) return messenger.binder

        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "pet toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        ToyPresence.enter()
        val rt = AlertsRuntime.get(this).also { alerts = it }
        rt.toyChanged()
        sensors = getSystemService(SensorManager::class.java)
        s.launch {
            settings = repo.settings.first()
            val start = MoodState(settings.petMood.toDouble(), settings.petMoodAt)
            brain = PetBrain(start, { SleepWindow(settings.petSleepStart, settings.petSleepEnd) }, ZoneId.systemDefault())
            applyBattery(registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED))
            output = GlyphOutput(this@PetToyService, profile) { kick() }.also { it.connect() }
            repo.update { if (it.petToyEverBound) it else it.copy(petToyEverBound = true) }
            launch { repo.settings.collect { settings = it; kick() } }
            launch { rt.bus.collect { kick() } }
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(rekick)
        setSensors(false)
        runCatching { unregisterReceiver(batteryReceiver) }
        save(force = true)
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

    private fun applyBattery(i: Intent?) {
        i ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        brain?.onCharging(i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0, level * 100 / scale, now())
    }

    private fun isAod() = modes.mode(now()) == Mode.AOD

    private fun setSensors(on: Boolean) {
        val sm = sensors ?: return
        if (on == sensorsOn) return
        sensorsOn = on
        if (on) sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        else sm.unregisterListener(sensorListener)
    }

    /** Persist the fractional mood: when changed (≤ once / 10 s), every 5 min, and on unbind. */
    private fun save(force: Boolean = false) {
        val b = brain ?: return
        val t = now()
        if (!force && !(b.dirty && t - lastSave >= 10_000) && t - lastSave < 300_000) return
        lastSave = t
        val snap = b.snapshot(t)
        val write: suspend () -> Unit = { repo.update { it.copy(petMood = snap.mood.toFloat(), petMoodAt = snap.at) } }
        val sc = scope
        if (sc != null && !force) sc.launch { write() }
        else CoroutineScope(Dispatchers.IO).launch { write() }
    }

    private fun kick() {
        val s = scope ?: return
        if (brain == null || renderJob?.isActive == true) return
        handler.removeCallbacks(rekick)
        renderJob = s.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val b = brain ?: break
                val t = now()
                val aod = isAod()
                setSensors(!aod)
                b.tick(t, active = !aod)
                save()
                val alert = alerts?.bus?.value
                val grid = runCatching {
                    when {
                        alert != null -> alerts!!.animationFor(alert).frame(profile.size, AlertsRuntime.now() - alert.startedAt)
                        aod -> GhostArt.still(profile.size, b.pose(t, profile.size), Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).minute)
                        else -> GhostArt.frame(profile.size, b.pose(t, profile.size), t)
                    }
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = alert == null && aod))
                if (aod && alert == null) {
                    modes.msUntilActive(t)?.let { handler.postDelayed(rekick, it + 100) }
                    break
                }
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    private companion object {
        const val TAG = "BacklitPet"
        const val FRAME_MS = 50L
    }
}
```

- [ ] **Step 2: Add the manifest entry, strings and toy image**

In `strings.xml`, after `canvas_toy_summary`:

```xml
    <string name="pet_toy_name">Backlit Pet</string>
    <string name="pet_toy_summary">A little ghost that lives on your Glyph. Long press to pet him.</string>
```

In `AndroidManifest.xml`, right after the closing `</service>` of `.glyph.CanvasToyService`:

```xml
        <service
            android:name=".glyph.PetToyService"
            android:exported="true"
            tools:ignore="ExportedService">
            <intent-filter>
                <action android:name="com.nothing.glyph.TOY" />
            </intent-filter>
            <meta-data
                android:name="com.nothing.glyph.toy.name"
                android:resource="@string/pet_toy_name" />
            <meta-data
                android:name="com.nothing.glyph.toy.image"
                android:resource="@drawable/ic_pet_preview" />
            <meta-data
                android:name="com.nothing.glyph.toy.summary"
                android:resource="@string/pet_toy_summary" />
            <meta-data
                android:name="com.nothing.glyph.toy.longpress"
                android:value="1" />
            <meta-data
                android:name="com.nothing.glyph.toy.aod_support"
                android:value="1" />
        </service>
```

`res/drawable/ic_pet_preview.xml` is a ghost outline with two eyes:

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
        android:strokeLineJoin="round"
        android:pathData="M20,80L20,44a28,28 0,0 1,56 0L76,80l-9,-7l-9,7l-10,-7l-10,7l-9,-7z" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M34,40h8v12h-8zM54,40h8v12h-8z" />
</vector>
```

- [ ] **Step 3: Build and run the full suite**

Run: `source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK && .superpowers/runtests.sh -q 2>&1 | tail -1`
Expected: `BUILD_OK`, then `N tests, 0 failures, 0 errors`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/backlit/glyph/PetToyService.kt app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/main/res/drawable/ic_pet_preview.xml
git commit -m "feat(pet): Backlit Pet toy service with motion sensors and AOD stills

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: PET tab and scrolling tab row

**Files:**
- Create: `app/src/main/java/app/backlit/ui/PetScreen.kt`
- Modify: `app/src/main/java/app/backlit/ui/HomeScreen.kt`

**Interfaces:**
- Consumes:
  - `PetBrain`, `MoodState`, `SleepWindow`, `GhostArt`, `Base` (Tasks 1–3)
  - `PetPreviewAnimation.idFor` (Task 4)
  - `Settings.pet*` and `SettingsRepo.cleanPetName` (Task 4)
  - `BuildConfig.DEBUG` (Task 4)
  - `AlertsRuntime.preview`
- Produces: `@Composable fun PetTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit)`

This is UI. It's gated by the build, and checked on the device in Task 7.

- [ ] **Step 1: Write `PetScreen.kt`**

```kotlin
package app.backlit.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.BuildConfig
import app.backlit.alerts.AlertsRuntime
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.glyph.DeviceProfile
import app.backlit.pet.Base
import app.backlit.pet.GhostArt
import app.backlit.pet.MoodState
import app.backlit.pet.PetBrain
import app.backlit.pet.PetPreviewAnimation
import app.backlit.pet.SleepWindow
import kotlinx.coroutines.delay
import java.time.ZoneId

@Composable
fun PetTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val sleep = SleepWindow(settings.petSleepStart, settings.petSleepEnd)

    // A local preview brain from the stored mood; taps pet it locally (no saved mood change).
    val brain = remember(settings.petMood, settings.petMoodAt, settings.petSleepStart, settings.petSleepEnd) {
        PetBrain(MoodState(settings.petMood.toDouble(), settings.petMoodAt), { sleep }, ZoneId.systemDefault())
    }
    LaunchedEffect(brain) {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), android.content.Context.RECEIVER_NOT_EXPORTED)
        if (i != null) {
            val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            brain.onCharging(i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0, lv, System.currentTimeMillis())
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(50); now = System.currentTimeMillis() } }

    if (profile != DeviceProfile.UNSUPPORTED && !settings.petToyEverBound) {
        Notice("Turn on Backlit Pet in Glyph Toys (Settings → Glyph Interface → Glyph Toys). He snacks while you charge with him on the Glyph.")
    }

    val pose = brain.pose(now, size)
    MatrixPreview(GhostArt.frame(size, pose, now), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clickable { brain.onLongPress(System.currentTimeMillis(), true) })

    val mood = brain.mood(now)
    val state = when (pose.base) {
        Base.HAPPY -> "HAPPY"; Base.CONTENT -> "CONTENT"; Base.BORED -> "BORED"
        Base.SAD -> "SAD"; Base.ASLEEP -> "ASLEEP"; Base.MUNCH -> "SNACKING"
    }
    Text("${settings.petName.uppercase()} IS $state", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
    Row(
        Modifier.padding(vertical = 8.dp).clickable(enabled = BuildConfig.DEBUG) {
            val next = when { mood >= 70 -> 55f; mood >= 40 -> 25f; mood >= 15 -> 5f; else -> 90f }
            onUpdate { it.copy(petMood = next, petMoodAt = System.currentTimeMillis()) }
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(10) { i -> Box(Modifier.size(10.dp).background(if (i < (mood + 5) / 10) BacklitColors.White else BacklitColors.LedOff, CircleShape)) }
    }
    Text("Tap him to pet him here. Long press the Glyph button to pet him on the back.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)

    Text("NAME", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    var name by remember(settings.petName) { mutableStateOf(settings.petName) }
    OutlinedTextField(
        value = name,
        onValueChange = { v -> name = v.take(12); onUpdate { it.copy(petName = SettingsRepo.cleanPetName(name)) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text("SLEEP HOURS", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    HourStepper("FROM", settings.petSleepStart) { h -> onUpdate { it.copy(petSleepStart = h) } }
    HourStepper("TO", settings.petSleepEnd) { h -> onUpdate { it.copy(petSleepEnd = h) } }
    Text("He sleeps (and his mood doesn't drop) between these hours.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)

    SquareChip("SHOW ON GLYPH", true, { runtime.preview(PetPreviewAnimation.idFor(Base.HAPPY), 3000L) }, Modifier.fillMaxWidth().padding(vertical = 12.dp))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.weight(1f))
        SquareChip("−", false, { onChange((hour + 23) % 24) })
        Text("%02d:00".format(hour), style = MaterialTheme.typography.titleMedium)
        SquareChip("+", false, { onChange((hour + 1) % 24) })
    }
}
```

- [ ] **Step 2: Make the tab row scroll and add PET**

In `HomeScreen.kt`, replace the tab chip row with a horizontally scrolling row of natural-width chips, and add the PET branch:

```kotlin
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("CLOCK", "MUSIC", "ALERTS", "CHARGE", "STUDIO", "PET").forEachIndexed { i, label ->
                SquareChip(label, selected = tab == i, onClick = { onTab(i) })
            }
        }

        when (tab) {
            0 -> ClockTab(settings, profile, onUpdate, onNavigate)
            1 -> MusicTab(settings, profile, onUpdate)
            2 -> AlertsTab(profile, onEdit)
            3 -> ChargeTab(settings, profile, onUpdate)
            4 -> StudioTab(settings, profile, onUpdate, onEdit)
            else -> PetTab(settings, profile, onUpdate)
        }
```

Add the import `androidx.compose.foundation.horizontalScroll`. `rememberScrollState` is already imported.

- [ ] **Step 3: Build and run the full suite**

Run: `source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && echo BUILD_OK && .superpowers/runtests.sh -q 2>&1 | tail -1`
Expected: `BUILD_OK`, then `N tests, 0 failures, 0 errors`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/backlit/ui/PetScreen.kt app/src/main/java/app/backlit/ui/HomeScreen.kt
git commit -m "feat(pet): PET tab with live preview, mood meter, name and sleep hours

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: On-device checks and docs

**Files:**
- Modify: `docs/testing/device-checklist.md`, `README.md`

- [ ] **Step 1: Install**

Run: `source .superpowers/env.sh && adb devices && ./gradlew -q :app:installDebug && adb logcat -c`
Expected: the device is listed and the install succeeds.

- [ ] **Step 2: Run the spec §6 device checklist with the user and record each result.** Read `adb logcat -d -s BacklitPet:V AndroidRuntime:E` after each step.

1. PET tab: the ghost is live, and the headline and meter show. In this debug build, tapping the meter cycles the faces: HAPPY, CONTENT, BORED, SAD.
2. Turn on Backlit Pet in Glyph Toys. The resting face matches the PET tab.
3. Long press the Glyph button: hearts, and the meter rises. Shake once: dizzy. Shake hard 3 times: angry, with steam. Long press while angry: calmed.
4. Tilt the phone: the eyes follow and the body leans. **Record whether the direction feels right.** If it's mirrored, flip the signs per the Task 2 note and record a ruling.
5. Hold the phone face-up for 3 s, then turn it face-down (matrix up): peekaboo with "!".
6. While charging: munch. In AOD while charging: the fills-up still.
7. Set the sleep hours to include now: ASLEEP with z's. A long press gives a yawn, then sleep again after about a minute.
8. AOD idle still (the eyes change each minute) and the AOD sleep still.
9. SHOW ON GLYPH with the Glyph idle plays a happy hop.
10. Turn the toy off and back on: the mood persists (the meter is unchanged in the PET tab).

- [ ] **Step 3: Update the docs**

Append this to `docs/testing/device-checklist.md`:

```markdown
## Glyph Pet (Phone (3))

- [ ] PET tab: live ghost, headline + mood meter; debug meter tap cycles faces
- [ ] Toy shows the resting face for the mood
- [ ] Long press → hearts (+mood); shake → dizzy; 3 hard shakes → angry; long press while angry → calmed
- [ ] Tilt moves eyes / leans body (direction correct)
- [ ] Face-up ≥2 s then face-down → peekaboo !
- [ ] Charging → munch; AOD while charging → fills-up still
- [ ] Sleep hours → asleep with z's; long press → yawn, back to sleep after ~1 min
- [ ] AOD idle still (eyes change per minute) and AOD sleep still
- [ ] SHOW ON GLYPH plays a happy hop
- [ ] Mood persists after toggling the toy off/on
```

In `README.md`, add a section after the Studio + Canvas features:

```markdown
### Backlit Pet (Glyph Toy)
- A little ghost with a mood meter. Long-press to pet him; shake him and he gets dizzy (or angry!);
  tilt and his eyes follow; turn the phone face-down for a peekaboo; he munches while charging and
  sleeps at night. Mood decays slowly while he's awake, never while he sleeps, and he never dies.
- AOD shows a still pose, filling up like a battery while charging. The PET tab shows him live, with
  his name, mood and sleep hours.
```

Add a row to the docs table:

```markdown
| Glyph Pet | [`2026-10-05-backlit-pet-design.md`](docs/superpowers/specs/2026-10-05-backlit-pet-design.md) | [`2026-10-05-backlit-pet.md`](docs/superpowers/plans/2026-10-05-backlit-pet.md) |
```

- [ ] **Step 4: Commit**

```bash
git add docs/testing/device-checklist.md README.md
git commit -m "docs: Glyph Pet device checklist and README

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
