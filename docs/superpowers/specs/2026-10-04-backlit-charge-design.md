# Backlit — Charge Toy — Design

**Date:** 2026-10-04
**Status:** Draft for review
**Builds on:** Part 1 (clocks), Part 2 (music) and Alerts, all merged.
**Roadmap context:** app notification glyphs were dropped because Nothing's own are good. After Charge
come the pixel studio and then the next-event countdown. OTP and AI reactions are parked.

## 1. Goal

A new Glyph toy, **Backlit Charge**: a battery toy that is useful all day and comes alive while charging.
It offers four styles (Sprout, Buddy, Big number, Moon) and can use Glyph Museum imports for the
plug-in and done moments.

### Success criteria
- When selected and not charging, the toy shows the current battery level in the chosen style (still).
- When plugged in while the toy is showing, the plug-in animation plays (about 5 s, ending with the %).
  A gentle charging loop then runs until unplugged.
- When the level crosses the user's target (50–100 %, default 100 %), the done moment plays once per
  charge session.
- In AOD, the still level is refreshed once a minute.
- Long press on the Glyph button cycles the style. Alerts interrupt the toy as they do for Clock.
- No background work: battery updates are only received while the toy is bound. No new permissions.

### Out of scope
- Running when the toy is not selected. Explicitly rejected: no notification-listener reuse, no
  foreground service, no JobScheduler.
- Fast-charge detection, wireless/wired differences, health stats, sounds.
- Editing animations. That belongs to the later pixel studio.

## 2. Visual source of truth

`docs/superpowers/mockups/2026-10-04-charge-styles.html` holds the approved mockup (round 3, final).
Port its geometry and timings exactly. The user approved it after these fixes:
- Buddy's outline is a midpoint circle at 25×25 and a hand-placed 9×9 circle at 13×13.
- Big number at 13×13 uses a 2 px gap between digits, has no bolt and no rim, and has one pulsing dot under the number.
- The 25×25 rim dot glides over an ordered, continuous ring.

Brightness in the mockup is 0..1. In Kotlin it maps to design brightness 0..255 on `PixelGrid`
(`b * 255`, rounded), with `FrameEncoder` as usual.

## 3. Behaviour

### 3.1 Moments
| Moment | When | Duration |
|---|---|---|
| `STILL` | Not charging, and in AOD at any time | static; redrawn on level change and on each EVENT_AOD |
| `PLUG_IN` | Plug event seen while the toy is bound and ACTIVE | 5000 ms, then `CHARGING` |
| `CHARGING` | Charging, after plug-in (or charging when the toy binds) | loops until unplugged |
| `DONE` | Level goes from `< target` to `>= target` while charging, once per session | 3500 ms, then `CHARGING` |

- **Session:** starts at a plug event (or at bind time when already charging) and ends at unplug.
  `donePlayed` resets when a new session starts.
- **Bind while already charging:** go straight to `CHARGING` with no plug-in. If the level is already `>= target`,
  mark `donePlayed = true`, because done only plays on a crossing.
- **Unplug during any moment:** go to `STILL` at once.
- **Plug-in vs done:** if the target is crossed during `PLUG_IN`, `DONE` is queued and starts when
  `PLUG_IN` ends.
- **Target changed mid-session:** applies to future crossings only. If the new target is `<= level`,
  the change does not fire done.
- **Level jitter:** a crossing requires the previous reported level `< target` and the new one `>= target`.
  After done has played this session, later dips and re-crossings do not replay it.
- **AOD:** the toy always renders `STILL` (no animations) and redraws on EVENT_AOD. Plug and unplug are still
  tracked, so the session state is right when the toy returns to ACTIVE. A plug event in AOD starts the
  session in `CHARGING` without a plug-in animation. A crossing in AOD marks `donePlayed` and does not play. On the (4a) Pro (`aodOnly`)
  this means the toy is effectively always `STILL`.

### 3.2 Custom animations (Glyph Museum)
- `chargePlugInAnim` and `chargeDoneAnim` are each `""` (use the style's own) or an imported-animation id
  (`import:<uuid>`, resolved through the existing `AnimationLibrary` / `AlertStore` imports).
- An imported plug-in animation plays for 5000 ms and has no % reveal. An imported done animation plays
  for 3500 ms. Imports loop if shorter and are cut if longer.
- If an id no longer resolves, for example because the import was deleted, the style's own animation is used.
  `AlertsRuntime` gains `importedAnimation(id): GlyphAnimation?`, which looks only at imports and does not fall back to a
  built-in alert animation.
- `STILL` and `CHARGING` always use the chosen style, because imports cannot show a level.

### 3.3 Interaction with Alerts
Same contract as `ClockToyService`:
- `ToyPresence.enter/leave`, `AlertsRuntime.toyChanged()`.
- Collect the alert bus. While an alert is active, render the alert instead (50 ms pacer).
- When the alert ends, resume. If a moment's time ran out during the alert, continue with the next
  state. `DONE` is still played after the alert if it was pending.

## 4. Styles (render/charge)

```kotlin
interface ChargeStyle {
    val id: String; val label: String
    fun still(size: Int, level: Int): PixelGrid                    // level 0..100
    fun plugIn(size: Int, level: Int, tMs: Long): PixelGrid         // 0..5000; ends with % reveal
    fun charging(size: Int, level: Int, tMs: Long): PixelGrid       // looping, tMs since loop start
    fun done(size: Int, tMs: Long): PixelGrid                       // 0..3500
}
object ChargeStyles { val all: List<ChargeStyle>; fun byId(id: String): ChargeStyle; fun next(id: String): ChargeStyle }
```

Default style: `moon`. Order is Sprout, Buddy, Big number, Moon. Long press uses `next`.

**Shared % reveal.** Used by the Sprout, Buddy and Moon plug-in animations. From t=3800 ms, dim the scene to 20 %
over 250 ms. Then clear a box 1 px larger than the text and draw the % in 3×5 digits (`PixelFont`), centred.
Big number has no reveal because the number is always shown.

**Still = the last frame of plug-in without the reveal.** That means the plant at full eased height, Buddy filled to the level,
the moon at phase = level, and the number at the level. For Sprout, Buddy and Moon, still has no motion (t frozen).

| Style | still / plug-in | charging loop | done |
|---|---|---|---|
| **Sprout** | Pot. A stem grows to `level` × maxH (25: 12 rows from y=18; 13: 6 rows from y=9) over 3000 ms easeInOut. Leaves alternate every 3 rows (25) or 2 rows (13). A bud sits on top. | The top 40 % of the stem sways by ±1 px (sin t/900). The leaf tips twinkle. | A flower blooms over 1200 ms (8 petals at 25, 4 at 13). Pollen sparkles then drift off. |
| **Buddy** | Circle outline (25: midpoint r=8 at (12,13); 13: hand-placed 9×9 at x2..10, y3..11). The interior fills from the bottom to the level (0.35). 2×2 eyes (25) or 1 px eyes (13) and a small smile. An antenna on top. | The antenna tip pulses (|sin t/350|). The eyes blink for 140 ms every 2600 ms. | Buddy hops for 900 ms, with ^ ^ eyes and a wide smile. Hearts float up (2 at 25, 1 px at 13). |
| **Big number** | 25: 5×7 digits centred at (12,13), a 3×5 bolt at (11,3). 13: 3×5 digits centred at (6,6) with a 2 px gap (1 px for "100"), one dot at (6,10). Plug-in counts up over 1800 ms (easeOut). | 25: the bolt pulses (0.35..1, sin t/420). One dot with an 8-px fading tail circles the ordered midpoint ring r=11 (one step every 75 ms). 13: the dot under the number pulses. | 25: "100" blinks (220 ms) for 1300 ms, then a full dim ring plus a thick check mark draws in over 500 ms. 13: the full ring r=5 blinks, then the check mark. |
| **Moon** | Disc (25: r=8.6; 13: r=4.6). The lit side is the pixels with `dx > w·(1−2f)`, with f = level/100, so the lit area equals the level. The unlit side is at 0.07. Five craters at 25. Fixed stars (6 at 25, 3 at 13). Phase eases over 3200 ms. | The stars twinkle slowly (sin t/900, offset per star). | Full moon, with a halo ring at r+1.3 (±0.4, sin t/500) fading in over 900 ms. |

Every frame must keep lit pixels inside the round mask (`PixelGrid` already clips).

**New 5×7 font:** `render/PixelFont5x7` holds digits 0–9, ported from the mockup's `F5`. Big number at 25
is its only user.

## 5. Session logic (charge/ChargeSession — pure)

```kotlin
data class Battery(val plugged: Boolean, val level: Int)            // level 0..100
enum class Moment { STILL, PLUG_IN, CHARGING, DONE }
data class Show(val moment: Moment, val startedAt: Long)

class ChargeSession(target: () -> Int) {
    fun onBind(b: Battery, now: Long)
    fun onBattery(b: Battery, now: Long, active: Boolean)   // from ACTION_BATTERY_CHANGED; active = not AOD
    fun tick(now: Long)                    // advances PLUG_IN→(DONE|CHARGING), DONE→CHARGING
    val show: Show
    val level: Int
    fun nextWakeAt(): Long?               // end of PLUG_IN/DONE, null otherwise
}
```

All the rules in §3.1 live here and are unit-tested. The service only translates Android events and draws.

## 6. Toy service (glyph/ChargeToyService)

- The structure is a copy of `ClockToyService`: `DeviceProfile`, `ModeTracker`, `GlyphOutput`, the settings flow,
  the alert bus, and `ToyPresence`.
- Register a receiver for `Intent.ACTION_BATTERY_CHANGED` (`RECEIVER_NOT_EXPORTED`) in `onBind`, and
  unregister it in `onUnbind`. The sticky intent gives the initial state for `onBind`. Plugged means
  `EXTRA_PLUGGED != 0`. The level is `EXTRA_LEVEL * 100 / EXTRA_SCALE`.
- **Render loop:**
  - In ACTIVE with moment ≠ STILL, use a `FramePacer(50)` loop.
  - In STILL, push one frame on each change: level, style, settings or AOD event.
  - In AOD, always use still.
- EVENT_CHANGE sets `chargeStyle = ChargeStyles.next(...)`.
- The manifest gets a new `<service>` with action `com.nothing.glyph.TOY` and the same meta-data keys as the Clock toy:
  `toy.name` "Backlit Charge", `toy.summary`, `toy.image`, `toy.longpress=1` and `toy.aod_support=1`. The
  application-level `NothingKey` meta-data is already present.
- **Toy preview image:** a new vector drawable showing the Moon at about 62 %.

## 7. Settings

These are added to `Settings` / `SettingsRepo` (DataStore):

| Field | Default | Notes |
|---|---|---|
| `chargeStyle` | `"moon"` | unknown ids fall back to the default |
| `chargeTarget` | `100` | 50..100, step 5, clamped on read |
| `chargePlugInAnim` | `""` | `""` or `import:<id>` |
| `chargeDoneAnim` | `""` | `""` or `import:<id>` |
| `chargeToyEverBound` | `false` | set the first time the Charge toy binds; hides the setup hint |

## 8. App UI (ui/ChargeScreen.kt, new CHARGE tab)

- **Tabs:** CLOCK | MUSIC | ALERTS | CHARGE.
- **Live `MatrixPreview`:**
  - It renders the selected style at the device's size (25 if unsupported).
  - Segmented buttons switch it between Still, Plug-in, Charging and Done, using a demo level of 62 %.
- **"Show on Glyph" button:** plays the previewed moment on the real matrix through the existing Alerts preview path, so
  it behaves exactly like the Alerts previews. It plays through the app matrix when the Glyph is idle, and it is rendered
  by whichever Backlit toy is showing. It is not visible under other apps' toys.
  - **ID scheme:** `charge:<styleId>:<moment>`.
  - **Resolving:** `AlertsRuntime.animationFor` resolves it to a `ChargePreviewAnimation` (a `GlyphAnimation` wrapping the style
    moment at a demo level of 62 %).
  - **Duration:** `AlertCoordinator.preview` gains an optional `durationMs`. It is 5000 for plug-in, 3500 for done and 3000
    for still and charging.
- **Style picker:** four cards, each with a small still preview.
- **"Done at" slider:** 50–100 %, step 5. The help text says it plays once per charge when you reach the level, and if
  your phone limits charging (battery protection), it should be set at or below that limit.
- **Custom animations:** "Plug-in animation" and "Done animation" rows. Each is a picker for Style default, the
  imported animations, or **Import from Glyph Museum…**, which reuses `ImportHelper` and the Alerts import flow.
  Imports are shared with Alerts, and deleting one there falls back to the default here.
- **Setup hint card** until the Charge toy has bound once (`chargeToyEverBound`): "Turn on Backlit Charge in Glyph Toys".
- **Nothing on-charge note:** only added if the device check (§9) shows Nothing's On Charge Animation hides ours.
  It explains how to turn Nothing's one off. Backlit never changes system settings.

## 9. Device checks (Phone (3))

1. The toy shows the still level, and long press cycles the styles.
2. Plug in while the toy is showing: does the plug-in animation show, or does Nothing's On Charge Animation cover it?
   Record the result. If it's covered, add the §8 note and re-test with Nothing's turned off.
3. The charging loop runs, and unplugging returns to still.
4. Done: set the target just above the current level and charge until it's crossed. Done plays once.
5. AOD toy: the still level refreshes, with no animations.
6. A call alert from an important contact interrupts, then the toy resumes.
7. An imported plug-in animation and an imported done animation play, and a deleted import falls back.

## 10. Testing

- **Styles:** for each style × {25, 13} × levels {0, 5, 50, 62, 99, 100}, the frames render without exceptions and
  lit pixels stay inside the mask.
  - **Still:** the lit pixel count rises with the level (Sprout, Buddy, Moon). Moon's lit count ≈ level × disc area (±10 %).
  - **Buddy:** the outline is left-right symmetric at both sizes.
  - **Big number:** the 25 rim ring is 8-connected and closed. At 13, a 2-digit number has a ≥2-column gap.
- **ChargeSession:**
  - Plug → PLUG_IN → CHARGING.
  - A crossing during CHARGING gives DONE once.
  - A crossing during PLUG_IN queues DONE.
  - Unplug → STILL from every moment.
  - Bind while charging above the target means no DONE.
  - Jitter around the target means one DONE.
  - A target lowered below the level means no DONE.
  - Replug starts a new session, so DONE can play again.
- **Settings:** defaults are used, and the target is clamped.
