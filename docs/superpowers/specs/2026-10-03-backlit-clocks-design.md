# Backlit — Part 1: Core + Clock Toy — Design

**Date:** 2026-10-03
**Status:** Draft for review

## 1. Context and goals

Backlit is an Android app for Nothing phones with a Glyph Matrix. It competes with
"404+ : Glyph Matrix Maxxing" and "Glyph Museum", and sets itself apart with **better design and
usability**. It will be published on the Google Play Store.

The full product is split into four parts, each with its own spec → plan → build cycle:

1. **Core + Clock toy (this spec)**
2. Music visualizer
3. OTP on Glyph
4. AI reactions (ChatGPT / Gemini / Claude)

Part 1 builds the rendering engine, the app's look and feel, one polished Glyph Toy and the Play
Store release. Parts 2–4 build on top of it.

### Success criteria

- The "Backlit Clock" toy appears in Nothing's Glyph Toy manager on a Phone (3) and works when
  turned on: it ticks every second, handles long press and AOD, and the faces match the
  approved mockups.
- On a Phone (4a) Pro, the same toy works in AOD mode on the 13×13 grid, updating once a minute.
- The in-app preview shows exactly the same pixels as the physical matrix.
- The release build passes Play Store review with "no data collected".

### Out of scope for Part 1

Character or licensed art (e.g. Spider-Man, Batman — rejected for IP reasons), a user canvas, an
automator, a music visualizer, OTP, AI reactions, monetization (no ads, no IAP), analytics, and
the binary clock face (rejected after the mockup review).

## 2. Hardware and SDK facts

From the GlyphMatrix Developer Kit (`glyph-matrix-sdk-2.0.aar`):

| Device | SDK constant | Grid | Glyph Touch | Toy modes |
|---|---|---|---|---|
| Phone (3) | `Glyph.DEVICE_23112` | 25×25 | Yes | Active + AOD |
| Phone (4a) Pro | `Glyph.DEVICE_25111p` | 13×13 | No | AOD only |

- Toys are Android `Service`s with an intent filter for `com.nothing.glyph.TOY`. The manifest
  needs the `com.nothing.ketchum.permission.ENABLE` permission and the metadata
  `com.nothing.glyph.toy.name`, `com.nothing.glyph.toy.image`, plus
  `com.nothing.glyph.toy.longpress = 1` and `com.nothing.glyph.toy.aod_support = 1`.
- `GlyphMatrixManager`: `init(callback)`, `register(device)`, `setMatrixFrame(int[])`, `unInit()`.
- Events reach the service through a `Messenger`: `GlyphToy.EVENT_CHANGE` (long press) and
  `GlyphToy.EVENT_AOD` (once a minute).
- Only one toy runs at a time, and an active toy overrides app-pushed frames. All matrix output
  for this app goes through the toy.
- The grid size at runtime comes from `Common.getDeviceMatrixLength()`.
- The system toy manager can be opened with
  `com.nothing.thirdparty/.matrix.toys.manager.ToysManagerActivity` (newer system versions only).
- No emulator support: matrix behavior can only be checked on real hardware. The developer has
  a **Phone (3)**. The Phone (4a) Pro is checked through the 13×13 in-app preview.

## 3. Architecture

A single Android app module in **Kotlin + Jetpack Compose**, with packages kept strictly
separate:

```
app/src/main/java/app/backlit/
├─ render/            Pure Kotlin. No Android or SDK imports.
│   ├─ PixelGrid       size×size brightness array (0–255) + round mask; equality for tests
│   ├─ Draw            dot, anti-aliased line (Wu), arc/ring, disc, crescent
│   ├─ PixelFont       3×5 digits (shared by both grids)
│   ├─ FaceContext     time, grid size, mode (ACTIVE/AOD), options, sun times
│   └─ faces/
│       ├─ Face        interface: id, render(ctx): PixelGrid, needsSecondTicks(ctx)
│       ├─ AnalogFace
│       └─ DayRingFace
├─ glyph/             The only package that imports the Nothing SDK.
│   ├─ DeviceProfile   PHONE_3 (25, touch) / PHONE_4A_PRO (13, AOD-only) / UNSUPPORTED
│   ├─ GlyphOutput     wraps GlyphMatrixManager: connect, register, push(PixelGrid), close
│   └─ ClockToyService the single Glyph Toy
├─ data/
│   ├─ SettingsRepo    DataStore: faceId, secondHand, brightness, use24h, locationMode, city
│   ├─ SunTimes        on-device NOAA sunrise/sunset calculation
│   ├─ LocationSource  LocationManager approximate location (no Play Services)
│   └─ Cities          bundled offline list of major cities (name, lat, lon), GeoNames CC-BY
└─ ui/                Compose, "raw / dot-matrix" theme
    ├─ HomeScreen, SetupScreen, LocationScreen, AboutScreen
    └─ MatrixPreview   draws a PixelGrid as round LEDs
```

**Rules:**
- `render/` never imports Android. It holds most of the logic and is fully unit-testable.
- `glyph/` is the only code that touches the SDK. If the SDK changes, only this package changes.
- The preview and the device are both driven by `Face.render()`, so they cannot drift apart.

## 4. The toy at runtime

### Lifecycle
- **`onBind`**: `DeviceProfile.detect()` → `GlyphOutput.connect()` → `register(device)` → load
  settings → start the ticker → return the `Messenger` binder.
- **Ticker**: a coroutine that lines up with each wall-clock second. Each tick builds a
  `FaceContext`, calls `face.render()`, and pushes the frame only if it differs from the last
  pushed frame. When `face.needsSecondTicks(ctx)` is false, the ticker lines up with minute
  boundaries instead.
- **`onUnbind`**: cancel the ticker, `GlyphOutput.close()` (`unInit`). There is no foreground
  service, no wake lock and no background work.

### Update frequency

| Face | Phone (3), active | AOD (both devices) |
|---|---|---|
| Analog | every second if the second hand is on, otherwise every minute | every minute (`EVENT_AOD`), no second hand |
| Day ring | every minute | every minute |

### Events
- **Long press (`EVENT_CHANGE`)**: switch to the next face and save it to `SettingsRepo`.
- **AOD (`EVENT_AOD`)**: render one frame in AOD mode (no second hand, AOD brightness = 60% of
  the user's brightness).
- **Settings change**: the service watches the `SettingsRepo` flow and redraws immediately.
- **`ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED`**: redraw immediately, and recalculate sun
  times on a timezone change.

### Device behavior
- **Phone (3)**: active + AOD, 25×25.
- **Phone (4a) Pro**: AOD path only, 13×13, with its own hand-tuned layouts (not a scaled-down
  25×25 frame).
- **Unsupported**: the service does nothing and returns quietly.

## 5. Faces

All coordinates use a centered round mask: pixel `(x, y)` is lit only when its center lies inside
the inscribed circle.

### 5.1 Analog
- **Ticks**: 12 rim ticks on 25×25 (12/3/6/9 at brightness 200, others at 70). On 13×13 only
  12/3/6/9 are drawn.
- **Hands**: anti-aliased (Wu) lines from the center. Minute hand: length 9.5 (25) / 4.5 (13),
  brightness 170. Hour hand: length 6 (25) / 3 (13), brightness 255, drawn on top. Center pixel
  at 255.
- **The minute hand moves only on whole minutes** (angle = `m × 6°`), so the anti-aliased edges
  don't shimmer between minutes. The hour hand moves smoothly with the minutes
  (`(h%12 + m/60) × 30°`).
- **Second hand**: a single dot at brightness 255 on the rim at `s × 6°`. 25×25 active mode only,
  **on by default**, and can be turned off in settings.

### 5.2 Day ring
- **Rim** = a 24-hour dial: midnight at the bottom, sunrise side on the left, noon at the top,
  sunset side on the right (clockwise). Rim pixels whose time falls between today's sunrise and
  sunset are at brightness 110 (90 on 13×13). Night pixels are at 22 (18 on 13×13).
- **Sun/moon marker** on the rim at the current time: a small bright disc during the day and a
  crescent at night (25×25). A single 255 pixel on 13×13.
- **Time**:
  - 25×25: `HH:MM` in the 3×5 font, centered (x=4, y=10), brightness 230, colon at x+8.
  - 13×13 (approved layout B): HH on top (y=1) and MM below (y=7) in the 3×5 font at x=3 and
    x=7. HH at 255, MM at 170. The ring is left out across the top and bottom (x 2–10 at y ≤ 1
    and y ≥ 11) to make room for the digits.
- 12/24h follows the setting.

### 5.3 Brightness
Every face renders at its design brightness. `GlyphOutput` scales by the user's brightness
setting (default 80%), and by a further 60% in AOD, just before pushing.

## 6. Sun times and location

- `SunTimes.compute(date, lat, lon, zone)` uses the NOAA solar position algorithm and returns
  sunrise and sunset, or polar day/night flags (all day → the whole rim is bright, all night →
  the whole rim is dim).
- **Location modes** (`locationMode` setting):
  1. **Approximate**: `ACCESS_COARSE_LOCATION` only, asked once when Day ring is first chosen.
     Read with `LocationManager` (network/passive provider). Saved, and refreshed at most once a
     day when the app is open.
  2. **City**: chosen from the bundled offline city list.
  3. **Fixed**: 06:00–18:00. This is the default, and it's what's used when permission is
     denied.
- Sun times are cached per date. The toy service never asks for location itself. It only reads
  the saved coordinates.
- There are no network calls anywhere in the app.

## 7. App UI ("raw / dot-matrix" style)

- **Theme**: pure black background (`#000`), white text, one red accent (`#D71921`) for the
  "live" dot. Headings use **Doto** (dot-matrix font, OFL, bundled), body text uses
  **Space Grotesk** (OFL, bundled). Dashed dividers, square chips. Dark only.
- **HomeScreen**: title "BACKLIT", a "● Live on matrix · <device>" status line, a large
  `MatrixPreview` that updates every second, face chips (ANALOG / DAY RING), then settings for the
  chosen face (second hand, brightness, 24h, location for Day ring), and a "Glyph Toy setup →"
  row.
- **SetupScreen**: shown on first launch and whenever the toy isn't turned on. It explains the
  steps and opens `ToysManagerActivity` if the intent can be resolved; otherwise it shows the
  manual steps.
- **LocationScreen**: three options (Approximate / City / Fixed), plus city search over the
  bundled list.
- **AboutScreen**: version, "Backlit collects no data", open-source licenses (including the
  GeoNames attribution).
- **Unsupported device**: the app still opens and shows the preview, with a banner saying "This
  phone has no Glyph Matrix".

## 8. Error handling

| Situation | Behavior |
|---|---|
| Not a Nothing matrix phone | `DeviceProfile.UNSUPPORTED`. The toy does nothing and the app shows a banner. |
| The toy manager intent can't be resolved | The setup screen shows manual steps. |
| `GlyphOutput.connect` fails or disconnects | Retry 3 times with backoff (1s, 2s, 4s), then stop quietly and log. Never throw out of the service. |
| Location denied or unavailable | Fall back to the saved coordinates, then to Fixed mode. |
| Render throws | Catch in the ticker, log, and push a blank frame. Never crash the service. |

## 9. Testing

- **Unit tests (JVM)** for `render/`: exact-pixel snapshot tests for each face × grid × mode at
  fixed times (00:00, 03:15, 09:41, 12:00, 12:59, 18:30, 23:59), as ASCII-art golden strings
  stored next to the tests. Plus specific checks: the minute hand doesn't change within a
  minute; the second dot only appears in ACTIVE mode on 25×25 with the setting on; the 13×13 ring
  has its top and bottom gaps.
- **Unit tests** for `SunTimes`, against published sunrise/sunset times for 3+ cities (one near
  the equator, one at mid latitude, one with polar days), within ±2 minutes.
- **Unit tests** for the ticker's scheduling logic, using a fake clock.
- **Manual on-device checklist (Phone (3))**: enable the toy in the manager, short press to cycle
  in and out, long press to change face, AOD frames, change the timezone, change settings while
  the toy is showing, reboot persistence, check for battery drain over 1 hour.

## 10. Release

- `applicationId`: `app.backlit` (to confirm before the first upload: it can't be changed
  later). `minSdk 34`, `targetSdk` = latest stable.
- Permissions: `com.nothing.ketchum.permission.ENABLE`, `ACCESS_COARSE_LOCATION`.
- Play listing: "Backlit – Glyph Matrix Clocks & Toys". Free, no ads, no IAP. Data safety: no
  data collected or shared. Screenshots include photos of the physical matrix.
- Signed release AAB with R8 enabled, plus a keep rule for the SDK classes.
- Before release, search the Play Store and trademark databases for the name "Backlit".
