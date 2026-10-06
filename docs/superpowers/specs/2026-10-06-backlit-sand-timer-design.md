# Backlit — Tilt Sand Timer — Design

**Date:** 2026-10-06
**Status:** Draft for review
**Roadmap:** this comes after the multiple pets (PR #10) and before the message badge.

## 1. Goal

**Backlit Sand** is a Glyph toy that is a real hourglass. Live sand follows gravity as you tilt the phone, and you flip
it over to time things: a focus session, tea or a short break. It aims to be equally useful and playful.

### Success criteria
- **Classic hourglass look** at 25×25 and 13×13, matching the approved mockups.
- **Flipping works like a real hourglass.** The bulb on top holds the time left, so flipping mid-run swaps the time left
  and the time already run.
- **Laying it on its side pauses it.** Laying it face-down on a desk keeps it running, with the sand falling to the bottom of the matrix.
- **Long press** cycles the presets. While the timer runs, it first shows the minutes left, so it never wipes a running timer by accident.
- **When time is up,** the "Flip me" moment plays, and the done alert fires even if another toy is showing or
  the screen is asleep. It is on time with "Ring exactly on time" switched on, and otherwise within about a minute.
- **TIMER tab** with a live preview, presets, the done alert setting and an optional "Ring exactly on time" switch.
- **No new runtime permission prompts.** Battery use is like the Pet toy: the accelerometer only runs while the toy is
  showing.

### Out of scope
- More than one timer at a time, stopwatches and lap times.
- Sounds other than the system notification chime.
- Statistics or history of focus sessions.
- Notifications in the shade.

## 2. Visual source of truth
- **Look:** style A, the classic hourglass, in `docs/superpowers/mockups/2026-10-06-sand-styles.html`. Port its
  `build` (style `"a"` only), `newSim` and `step` rules line for line.
  - The glass interior is set by half-widths per row distance from the centre row: `[gate,0,1,2,3,4,5,6,6,6,5]` at 25×25
    and `[gate,0,1,2,3,3]` at 13×13. The neck gate is the centre cell.
  - Wall cells are the in-mask cells 8-adjacent to the interior or the gate.
  - The sand load is `floor(0.72 × min(top bulb cells, bottom bulb cells))`.
  - Brightness: wall 0.16, resting grain 0.7, a grain that moved this step 1.0. Everything maps 0..1 → 0..255 as in the earlier toys.
- **Moments:** `docs/superpowers/mockups/2026-10-06-sand-moments.html`.
  - The long-press number view: the hourglass is dimmed to ×0.25, with the number on a cleared window. The 5×7 font is used at
    25×25 (the existing `PixelFont5x7` digits) and the 3×5 font at 13×13.
  - The refill (700 ms) and the Ready breathing glass (wall 0.16–0.28, a 500 ms sine). One change from the mockup:
    the refill fills the bulb that is **down**, not the top one. READY looks like a real hourglass waiting to be turned over,
    so the flip that starts it is a real flip (see §3).
  - The done moment is option **C, "Flip me"**: a 1200 ms eased 180° spin of the done picture, then the glass blinks
    twice (1300–2300 ms, 250 ms on/off).
  - The always-on stills use `layout(n, frac, stream)`. The Done still has a brighter wall at 0.35.

## 3. Behaviour

### States
| State | What you see | Clock |
|---|---|---|
| READY | the bulb that is down is full, glass breathing ("flip to start") | not running |
| RUNNING | live sand | counting down; the time left is the sand in the bulb on top |
| PAUSED | sand settled to the side | frozen; time left is stored |
| DONE | bottom bulb full; after "Flip me", the glass breathes slowly | 0 |

### Gravity and orientation
- **Gravity** is the accelerometer's in-plane vector `(gx, gy)` in matrix coordinates. **Up** (+1) means the top bulb is up,
  which is when gy > 0.2 after normalising. **Down** (−1) means gy < −0.2. Anything in between is **side**.
- **Flat:** when the in-plane magnitude is under 3 m/s² (phone face-down or face-up), the matrix is read upright:
  the top bulb holds the time left and sand falls to the bottom edge. This is a change of view (`upSide` becomes +1
  with the time unchanged), not a flip. To start or flip, the phone is turned upside down in the hand.
  *Changed after the device test (2026-10-06): keeping the last direction made sand pour upwards on a desk.*
- **Side debounce:** "side" must hold for 500 ms before the timer pauses.

### The flip rule
The timer stores the duration `D`, which bulb is "full side up" (`fullSide`, ±1) and `endAt`.
- **From READY:** the sand rests in the bulb that is down (the current definite orientation, or the last one). A flip to the
  opposite definite orientation starts the timer: that full bulb is now on top, `fullSide` is set to it, and `endAt = now + D`.
  Going to side and back to the same orientation does nothing.
- **Mid-run flip:** with time left `L`, after the flip the time left is `D − L`, and `endAt = now + (D − L)`. If `D − L` is 0,
  it goes to DONE.
- **From DONE:** a flip starts the full `D` again.
- **Side:** goes to PAUSED, storing `L`. Returning to the same orientation resumes with `L`. Returning to the opposite one counts as a flip,
  so the time left becomes `D − L`.

### Long press (EVENT_CHANGE)
- **READY or DONE:** move to the next preset. Show the number for 1500 ms, refill for 700 ms, then READY.
- **RUNNING or PAUSED:** the first press shows the minutes left, rounded up, for 1500 ms. The state doesn't change. A second press
  within 2000 ms of the first moves to the next preset and resets to READY.
- **Presets:** the default is `[1, 3, 5, 10, 25]` minutes. The selected preset is stored. Each preset is 1..99, with no duplicates and at least one.

### Sand vs time
- The time is the truth. The neck gate releases grains at `total / D` grains per ms of running time.
- About every second the gate rate is recomputed from the grains resting in the up bulb (the gate cell is not counted)
  against `should = ceil(total × L / D)`. If there are more grains than that, the rate is `base + extra / 1000` grains/ms.
  If there are fewer, the rate is 0 until the clock catches up. If they match, the rate is `base`. This way the sand finishes
  with the clock.
- **On (re)entering the toy** (bind, back from an alert or after AOD), the grid is rebuilt with `SandLayout` for the current
  fraction. The stream isn't shown in the rebuilt still.

### Time's up
- **On the toy:** "Flip me" plays. Then the true DONE picture (sand in the bulb at the bottom) stays, with the glass breathing
  at 0.16–0.24 on a 1500 ms sine, until a flip or a long press.
- **Done alert setting** (`sandAlert`):
  - `GLYPH`: nothing beyond the Glyph.
  - `VIBRATE` (the default): the pattern 0, 400, 200, 400, 200, 600 ms.
  - `CHIME`: the vibration plus the default notification sound, and only the vibration when the ringer is silent or on vibrate.
- **Away from the toy:** `SandAlarm` fires at `endAt` and gives the same vibration or chime. If no toy is showing, it also plays "Flip me"
  on the matrix through `AppMatrixPlayer`, the same path as the call alerts.

### Always-on
- On EVENT_AOD (once a minute) the toy draws `SandArt.still(size, state, frac)`. RUNNING shows a two-dot stream in the neck,
  and DONE shows the brighter wall.
- **4a Pro (AOD only):** at each AOD tick the toy reads the accelerometer once (register, take one sample, unregister) to
  apply flips and side. That means flips there are picked up within a minute. No continuous sensor runs.

## 4. Architecture

### Pure core (`sand/`, JVM tests)
| Unit | Responsibility |
|---|---|
| `HourglassShape` | `forSize(n)`: `kind` (OUT/IN/WALL/GATE) and `side` (+1 top / −1 bottom / 0 gate) per cell, and `total` |
| `SandSim` | `SandSim(shape, random)`: `grid`, `moved`, `step(gx, gy, dtMs)`, `gateRate`, `countOn(side)` and `load(layout)`. It ports the mockup rules: candidate moves with dot > 0.38 sorted by dot, grains processed furthest along gravity first, no crossing bulbs except through the gate, no diagonal corner cutting between two non-open cells, and a gate budget capped at 2 |
| `SandLayout` | `layout(shape, fracTop, stream)`: the deterministic fill from the mockup's `sc1`/`sc2` scores |
| `TimerState` | an immutable data class with transitions `orientation(o, now)`, `longPress(now)`, `tick(now)`, `presetCycle` and `restore`. It holds `phase`, `durationMs`, `fullSide`, `endAt`, `pausedLeftMs`, `presetIndex`, `numberUntil` and `lastPressAt`. `timeLeft(now)` and `fractionTop(now)` are derived from it |
| `SandArt` | `frame(shape, sim, view, now)` covers READY breathing, RUNNING, PAUSED, the number overlay, the refill, "Flip me" and the DONE breathing; `still(shape, state, now)` covers always-on |
| `SandPreviewAnimation` | the app and toy-picker preview (`sand:ready`, `sand:running`, `sand:done`), following `PetPreviewAnimation` |

### Android
- **`glyph/SandToyService`:** follows `PetToyService`. It uses `DeviceProfile`, `ModeTracker`, `GlyphOutput`, `FramePacer(50)` and
  `kick()`, plus `ToyPresence` and the `AlertsRuntime` bus.
  - The accelerometer runs only while ACTIVE (or as a single AOD sample on the 4a Pro).
  - Each `TimerState` change is saved to Settings and calls `SandAlarm.schedule/cancel`.
  - The loop runs at 50 ms steps while RUNNING, during moments and during breathing. It holds still otherwise.
- **`sand/SandAlarm` + `SandAlarmReceiver`:**
  - Uses `AlarmManager.setExactAndAllowWhileIdle` when `canScheduleExactAlarms()` and the user turned on `sandExact`.
    Otherwise it uses `setAndAllowWhileIdle`.
  - The receiver checks the saved state is still RUNNING with `endAt ≤ now + 1 s`, marks it DONE, then gives the alert.
  - The alarm is re-armed on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` (or fires right away if `endAt` has passed).
- **Manifest:**
  - New permissions: `VIBRATE`, `SCHEDULE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED`. All three are install-time or special access, with no runtime prompt.
  - The toy service meta-data: `toy.name` "Backlit Sand", `toy.summary`, `toy.longpress` "Pick a time", `toy.aod_support`
    and a `toy.image` from `ToyPreviewXml`.
- **`ui/SandScreen`:** the TIMER tab, added after PET on `HomeScreen`. It contains:
  - a live preview, with "Show on Glyph" playing `sand:running`
  - preset chips: tap to remove (not the last one), "+" to add a number from 1 to 99
  - the done alert as a three-way segmented control (Glyph only / Vibrate / Vibrate + chime)
  - the "Ring exactly on time" switch, which opens `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` if access is not granted
  - a how-it-works card covering flip, side, face-down and long press

### Settings
| Field | Default | Notes |
|---|---|---|
| `sandPresets` | `[1,3,5,10,25]` | stored as a JSON string, cleaned on read |
| `sandAlert` | `"vibrate"` | `glyph` / `vibrate` / `chime`, unknown → vibrate |
| `sandExact` | `false` | |
| `sandTimer` | `""` | `TimerState` as JSON (including the selected preset index), so the timer survives restarts. Empty means READY at 5 min |
| `sandToyEverBound` | `false` | as for the other toys |

## 5. Edge cases
- **App killed or reboot mid-timer:** the state comes back from `sandTimer`. The alarm is re-armed by the boot receiver, and DONE is resolved at
  once if the time has passed.
- **Paused** keeps `pausedLeftMs`, so it never runs out while on its side. No alarm is set while paused.
- **Wobbles:** "side" needs 500 ms. Flips need a definite orientation, and the flat zone keeps the last direction.
- **Call alerts** take the matrix as usual. The clock keeps running, and the grid is rebuilt from `SandLayout` when the toy is back.
- **Presets changed while running:** the running timer keeps its duration, and the new list applies at the next cycle.
- **Exact alarm access revoked:** fall back to the inexact alarm without crashing, and the switch shows off.

## 6. Testing
- **SandSim:**
  - the grain count is conserved over 10,000 steps at random angles
  - grains stay on IN or GATE cells
  - grains never cross bulbs except through the gate
  - at sideways gravity, nothing crosses the gate
  - a full bulb drains within ±5% of `D` at both sizes with the rate correction on
- **TimerState:**
  - start from READY in either orientation
  - flip mid-run (`L → D − L`)
  - flip from DONE
  - side pause and resume, including the opposite resume counting as a flip
  - the 500 ms side debounce
  - the flat zone
  - long press in each phase, including the 2 s double press
  - the preset cycle wraps
  - restore round-trip
- **SandLayout:** the grain count equals `round(total × frac)` on top plus the rest below, the stream adds 2 cells, and the layout is balanced
  left-right to within one grain per bulb without the stream.
- **SandArt:**
  - all views at both sizes are inside the mask with lit pixels
  - "Flip me" at 0 ms equals the DONE picture and at ≥1200 ms equals it rotated 180°
  - the number view shows the digits
- **Settings:** defaults, preset cleaning (range, duplicates, non-empty), an unknown alert falls back to vibrate, and the JSON round-trip.
- **ToyPreviewsTest:** a new golden for the Backlit Sand picker image.
- **Device checklist:**
  - flip to start, flip mid-run, on its side, face-down
  - long press (both cases)
  - done with each alert type
  - done while on another toy and with the screen off
  - the exact switch
  - a reboot mid-timer
  - the 4a Pro AOD still and flip within a minute
