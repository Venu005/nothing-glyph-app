# Backlit — Glyph Pet (ghost) — Design

**Date:** 2026-10-05
**Status:** Draft for review
**Builds on:** Clocks, Music, Alerts, Charge, Studio + Canvas (all merged).
**Roadmap:** next come the Tilt sand timer, then the Message badge, Blow out the candles and the next-event countdown.

## 1. Goal

A **Backlit Pet** Glyph toy: a little ghost that lives on the matrix, with a light mood meter. It reacts to
the time of day, charging, long presses, shakes, tilt and being turned over. It is useful as an always-on
companion and playful while the screen is on. It never dies or runs away.

### Success criteria
- **Selecting the toy:** the ghost shows its resting face for the current mood and time.
- **Interactions** (while the screen is on):
  - a long press pets him
  - a shake makes him dizzy
  - 3 big shakes in 10 s make him angry
  - a long press while he is angry calms him
  - tilting moves his eyes and leans his body
  - turning the phone face-down (matrix up) gives a peekaboo
  - charging makes him munch
  - at night he sleeps
- **Mood** rises with attention and charging, and decays slowly while he is awake. It persists across toy restarts
  and reboots, and needs no background work.
- **AOD** shows a still pose that matches his state. Charging shows him filling up to the battery level.
- **The PET tab** shows him live, with the mood meter, his name and sleep hours, and lets you show him on the Glyph.
- No new permissions. Motion sensors run only while the toy is showing and the screen is on.

### Out of scope
- Other species, growing up, hunger stats, sickness or death.
- Notifications or reminders from the pet.
- Sound and haptics.
- Interactions while the toy isn't selected.

## 2. Visual source of truth

- `docs/superpowers/mockups/2026-10-05-pet-ghost-faces.html` holds every face and reaction (approved, except its AOD charging card).
- `docs/superpowers/mockups/2026-10-05-pet-ghost-aod.html` holds the approved AOD charging pose, **A · Fills up**.

Port the geometry, timings and brightness (0..1 → ×255) exactly, at 25×25 and 13×13, as in the Charge and Studio toys.

**Ghost body:**
- **25:** a dome that is the top half of a midpoint circle at (12,11), r=7 (r=8 when puffed). Sides at x=5 and x=19, from
  y=11 to y=19 (y=20 when drooping). A wavy hem on the next row alternates y and y+1 every 2 px, advancing one step per
  `hemSpeed` ms.
- **13:** a dome circle at (6,6), r=4 (r=5 when puffed). Sides at x=2 and x=10, y 6..10. A hem on y 10/11.
- **Eyes:** 2×3 at x 9 and 14, y 10 (25), or 1 px at x 4 and 8, y 6 (13).
- **Mouth:** around (12,15) at 25, or (6,8) at 13.

## 3. Behaviour

### 3.1 Mood (0..100, a fractional value, persisted)
- It starts at **70** for a new pet.
- **Gains:**
  - **pet** (long press) +15, at most once per 30 s (a press inside the cooldown still plays the reaction)
  - **peekaboo** +5
  - **charging** +1 per full minute charging *while the toy is bound*
  - **calmed** +10
  - all capped at 100
- **Losses:**
  - **decay** continuous, −1 per 6 awake minutes. It is fractional, so frequent saves don't lose progress.
  - **big shake** −3, at most once per 10 s
  - **angry** −5
  - all floored at 0
- **Asleep:** no decay during sleep hours, which default to 23:00–07:00 and are set in the app (whole hours,
  start ≠ end, and the window may wrap midnight).
- **No background ticking.** The stored state is `(mood, at = wall-clock ms)`. The current mood is
  `decay(mood, at, now, sleep window)`, which counts only the awake minutes between `at` and `now`. Every change stores a
  new `(mood, at)`. The toy also stores it at least every 5 min while bound, and on unbind.

### 3.2 Resting face (base)
The first match wins:
1. **ASLEEP:** now is inside the sleep hours and he isn't temporarily awake (§3.3, yawn).
2. **MUNCH:** charging (loops the munch animation in ACTIVE).
3. Otherwise by mood:
   - **HAPPY** 70–100
   - **CONTENT** 40–69
   - **BORED** 15–39
   - **SAD** 0–14

### 3.3 Reactions (one-shot, drawn over the base, which resumes afterwards)

| Reaction | Trigger | Length | Mood |
|---|---|---|---|
| PET | long press (awake, not angry) | 2600 ms | +15 (30 s cooldown) |
| YAWN | long press while ASLEEP. He stays awake (base by mood) for 60 s, then sleeps again | 3600 ms | +5 |
| DIZZY | a big shake (linear acceleration ≥ 12 m/s²), at most once per 1.5 s, when it doesn't trigger ANGRY | 2000 ms | −3 (10 s cooldown) |
| ANGRY | the 3rd big shake within 10 s | 6000 ms | −5 |
| CALMED | long press while ANGRY | 4000 ms | +10 |
| PEEK | turned face-down (gravity z ≤ −7 m/s² for 0.5 s) after ≥ 2 s face-up or upright | 3200 ms | +5 |
| BOO | while HAPPY in ACTIVE, at most once per 10 min, chance-based (seeded per bind) | 2600 ms | 0 |

- A new reaction replaces the current one, except that ANGRY can only be replaced by CALMED.
- Reactions never start in AOD. Events received in AOD only update mood and timestamps.

### 3.4 Tilt
- While ACTIVE and not reacting, gravity x/y (from a low-pass filter of the accelerometer) shifts the pupils by up to
  1 px on each axis (`round(gx/9.8)`, `round(gy/9.8)`, clamped to −1..1).
- The body leans 1 px horizontally when |gx| > 4.9.
- At 13×13 there is no vertical pupil shift.

### 3.5 AOD stills (redrawn on each EVENT_AOD)
- **ASLEEP:** the sleep still (dim body, closed eyes, one "z").
- **Charging:** **fills up**. The body interior fills from the bottom to the battery level (25: rows y 11..20,
  interior x 6..18; 13: rows y 5..10, x 3..9) at 0.3, with happy eyes and a smile.
- **Otherwise:** the idle still. The eyes look a different way each minute, cycling through (0,0) → (1,0) → (0,1) → (−1,0)
  by minute-of-hour mod 4, with the mood's mouth.
- On the (4a) Pro (`aodOnly`) the toy is always in this mode.

## 4. Architecture

### Pure core — `pet/` (no android imports; JVM-tested)

| Unit | Responsibility |
|---|---|
| `PetMood` | `data class MoodState(mood: Double, at: Long)`, `SleepWindow(startHour, endHour)` with `contains(epochMs, zone)`, and `decay(state, now, sleep, zone)` counting awake minutes; gain/loss helpers with caps and floors |
| `PetBrain` | state machine: `onLongPress(now)`, `onShake(magnitude, now)`, `onGravity(x, y, z, now)`, `onCharging(on, now)`, `tick(now)`, with `active: Boolean` per event; owns cooldowns, the shake window, face-down tracking, the temporary-awake window, the boo schedule (injected `Random`) and charging minutes. It exposes `pose(now): Pose` and `mood(now): Int`, and a `dirty` flag plus `snapshot(): MoodState` for persistence |
| `Pose` | `base: Base`, `reaction: Reaction?`, `reactionStart: Long`, `look: Pair<Int,Int>`, `lean: Int`, `level: Int` (battery, for AOD fill) |
| `GhostArt` | `frame(size, pose, now): PixelGrid` for ACTIVE and `still(size, pose, minuteOfHour): PixelGrid` for AOD; a line-for-line port of the mockup's body/eyes/mouth/blush/hearts/steam/z/bolt helpers and every state |
| `PetPreviewAnimation` | a `GlyphAnimation` for ids `pet:<reaction or base>`, so the PET tab can use the Alerts preview path ("Show on Glyph") |

### Toy — `glyph/PetToyService`
- Same structure as `ChargeToyService` / `CanvasToyService`: `DeviceProfile`, `ModeTracker` with the `msUntilActive` re-kick,
  `GlyphOutput`, the settings flow, the alert bus, `ToyPresence`, and a `FramePacer(50)` loop while ACTIVE (the pet always
  animates). AOD pushes one still per EVENT_AOD.
- **Sensors:** `SensorManager` `TYPE_ACCELEROMETER` at `SENSOR_DELAY_UI`.
  - Registered only while ACTIVE and bound, and unregistered in AOD and on unbind.
  - A low-pass filter (α = 0.8) gives gravity, and accel − gravity gives linear acceleration.
  - Feeds `onGravity` and `onShake`.
- **Battery:** an `ACTION_BATTERY_CHANGED` receiver while bound feeds `onCharging` and the level.
- EVENT_CHANGE (long press) → `onLongPress`.
- **Persistence:** on brain `dirty` (throttled to once per 10 s), every 5 min, and on unbind, write `petMood` and `petMoodAt`.
  On bind, load them.

### Settings (Settings/SettingsRepo)
| Field | Default |
|---|---|
| `petName` | "Boo" (≤ 12 chars, trimmed; blank → "Boo") |
| `petMood` | 70.0 (Float, so the fractional mood survives saves) |
| `petMoodAt` | 0 (0 means "new pet": treat as now) |
| `petSleepStart` / `petSleepEnd` | 23 / 7 |
| `petToyEverBound` | false |

### PET tab (`ui/PetScreen.kt`)
- **Tab row:** CLOCK | MUSIC | ALERTS | CHARGE | STUDIO | PET. The row scrolls horizontally, with chips at their natural width.
- **Setup hint** until the toy has bound once.
- **Live preview:** the ghost at the device size, driven by a local `PetBrain` view from the stored mood, the sleep hours
  and the phone's current charging state (from the sticky battery intent). It shows the resting face, and taps on
  the preview play PET locally (preview only, no mood change).
- **"<NAME> IS HAPPY / CONTENT / BORED / SAD / ASLEEP / SNACKING"** headline and a **10-dot mood meter**.
  In debug builds (`BuildConfig.DEBUG`, which needs `buildFeatures.buildConfig = true`), tapping the meter cycles the stored mood through 90 → 55 → 25 → 5, for testing.
  Release builds ignore the tap.
- **Name** text field.
- **Sleep hours:** start and end chips with − / + (whole hours).
- **SHOW ON GLYPH** plays a happy hop (`pet:happy`, 3000 ms) through `AlertsRuntime.preview`.

## 5. Edge cases
- `petMoodAt` in the future (clock changed): treat it as now, with no decay.
- Sleep window start == end: treated as no sleep.
- The toy bound at night in ACTIVE shows the ASLEEP animation. A long press gives YAWN plus 60 s awake.
- **Charging gain while unbound:** none, because nothing runs in the background. This is documented in the app as "He
  snacks while you charge with him on the Glyph."
- No accelerometer (unexpected): sensor features are silently skipped.
- **Alerts** interrupt the toy, as with the other toys. Reactions that run out during an alert are dropped. PET
  presses are not lost: they still apply mood.

## 6. Testing
- **PetMood:**
  - decay counts only awake minutes, including windows wrapping midnight and spans over several days
  - caps and floors
  - a future timestamp gives no decay
  - start == end means no sleep
- **PetBrain:**
  - base selection order
  - PET with cooldown (the reaction plays but no mood inside 30 s)
  - YAWN at night with the 60 s awake window
  - single shake → DIZZY
  - 3 big shakes in 10 s → ANGRY, and only CALMED replaces ANGRY
  - DIZZY cooldown
  - face-down after ≥ 2 s face-up → PEEK, but not when flipped quickly
  - charging minutes add mood while bound
  - nothing starts in AOD
  - BOO only while HAPPY and ACTIVE, at most once per 10 min with a seeded Random
  - tilt → look/lean clamps, and no vertical look at 13
- **GhostArt:**
  - every base and reaction renders at both sizes without exceptions and stays inside the mask
  - the AOD fill's lit interior count grows with the level
  - the body outline is left-right symmetric when there's no lean or reaction
- **Settings:** defaults and name clamping.
- **Device checklist (Phone (3)):**
  - each mood face, set through the debug meter tap (§4 PET tab)
  - pet, dizzy, angry and calmed
  - tilt
  - peekaboo by turning face-down
  - munch while charging
  - asleep at night (set sleep hours to include now), and yawn
  - the AOD idle, sleep and charging-fill stills
  - the PET tab preview, name, meter, sleep hours and Show on Glyph
  - mood persists after toggling the toy off and on
