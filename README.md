# Backlit

**Clocks, music and alerts for the Glyph Matrix on the back of Nothing phones.**

Backlit is an Android app for the **Nothing Phone (3)** (25×25 Glyph Matrix) and **Nothing Phone (4a) Pro**
(13×13). It adds two Glyph Toys you cycle to with the Glyph Button, plus event animations that light up
the matrix when an important contact calls or one of your Bluetooth devices connects. The companion app
uses a raw, dot-matrix look (pure black, Doto headings, one red accent) and shows a live preview of
exactly what the LEDs will show.

Free, no ads, no analytics, no network access.

---

## Contents

- [Features](#features)
- [Supported devices](#supported-devices)
- [Building from source](#building-from-source)
- [Running on a phone](#running-on-a-phone)
- [Project structure](#project-structure)
- [Architecture](#architecture)
- [Permissions and privacy](#permissions-and-privacy)
- [Testing](#testing)
- [Glyph Museum import format](#glyph-museum-import-format)
- [Platform limits](#platform-limits)
- [Docs, specs and plans](#docs-specs-and-plans)
- [Roadmap](#roadmap)

---

## Features

### Backlit Clock (Glyph Toy)
- **Analog:** anti-aliased hands. The minute hand moves on whole minutes so it never shimmers, and a
  second-hand dot runs around the rim (Phone (3), optional).
- **Day ring:** the rim is a 24-hour dial. Daylight hours are bright and night hours dim, with the sun
  or moon at the current time and the time in the middle. Sunrise and sunset are calculated on the
  phone from a fixed 06–18 day, your approximate location, or a city picked from a bundled offline list.
- Long-press the Glyph Button to switch face. AOD (always-on) supported, including on the Phone (4a) Pro.

### Backlit Music (Glyph Toy, Phone (3))
- Reacts to whatever is playing on the phone (Spotify, YouTube Music, …) using the system output mix.
- Styles: **Mirror** (mirrored bars, bass in the centre) and **Peaks** (mirrored bars with falling
  peak dots). Long-press to switch.
- A breathing centre line when nothing plays, and a livelier fallback line without the audio permission.
- Steady 20 fps on a fixed frame grid (the SDK push itself takes ~15 ms).

### Alerts
- **Important contacts:** pick contacts and give each its own animation. It plays as their call
  starts, and again after a **missed call** (10 s, then a 5 s reminder every minute, up to 10, until
  you clear the missed-call notification).
- **Bluetooth devices:** pick paired devices (earbuds, watch, car…). Their animation plays for 3 s
  when they connect, with a 30 s cooldown for flaky reconnects.
- **Animations:** 6 built-ins hand-tuned for both grid sizes (Heartbeat, Smiley wink, Ringing, Burst,
  Link, Bounce), plus **Glyph Museum JSON import** by file picker or *Share → Backlit*. Imports are
  scaled automatically between 25×25 and 13×13. Tap any animation to preview it on the real matrix.
- When the Glyph is idle, alerts are drawn with the SDK's app-matrix API. When a Backlit toy is
  showing, the toy plays the alert itself and then resumes.

---

## Supported devices

| Device | Grid | Clock | Music | Alerts |
|---|---|---|---|---|
| Nothing Phone (3) | 25×25 | ✅ active + AOD | ✅ | ✅ (verified on device) |
| Nothing Phone (4a) Pro | 13×13 | ✅ AOD only | ❌ (AOD-only device) | designed, **not yet verified on hardware** |
| Other phones | — | preview only in the app | — | — |

`minSdk 34`, `targetSdk`/`compileSdk 37`. Developed and tested on a Phone (3) (model A024) running Android 16.

---

## Building from source

### 1. Toolchain

You need a JDK (17+) and the Android SDK with platform 37. Android Studio is the easy route. On macOS
from the command line:

```bash
brew install openjdk@21 gradle
```

```bash
brew install --cask android-commandlinetools
```

```bash
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
```

Point Gradle at the SDK with a `local.properties` file in the repo root (it's gitignored):

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

and make sure `JAVA_HOME` / `ANDROID_HOME` are set in your shell.

### 2. Nothing Glyph Matrix SDK

The SDK is **not committed**, because Nothing's EULA doesn't allow redistributing it. Download
`glyph-matrix-sdk-2.0.aar` from
[Nothing-Developer-Programme/GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
and put it at:

```
libs/glyph-matrix-sdk-2.0.aar
```

### 3. Build

```bash
./gradlew :app:assembleDebug
```

Release bundle (R8-minified; signed only if `keystore.properties` exists, see below):

```bash
./gradlew :app:bundleRelease
```

### Release signing

Create an upload key (keep the `.jks` outside the repo and back it up):

```bash
keytool -genkeypair -v -keystore ~/keys/backlit-upload.jks -alias backlit -keyalg RSA -keysize 4096 -validity 10000
```

Then add a gitignored `keystore.properties` at the repo root:

```properties
storeFile=/Users/<you>/keys/backlit-upload.jks
storePassword=<password>
keyAlias=backlit
keyPassword=<password>
```

---

## Running on a phone

1. Enable **Developer options** (Settings → About phone → tap *Build number* 7 times), then turn on
   **USB debugging**, connect the phone, and allow the computer.
2. Install the app:

   ```bash
   ./gradlew :app:installDebug
   ```

3. On the phone: **Settings → Glyph Interface → Glyph Toys**, then switch on **Backlit Clock** and
   **Backlit Music**. The app's Setup screen links straight there.
4. Press the Glyph Button to cycle to a toy, and long-press to change face or style.
5. For alerts, open the app → **ALERTS**. Add contacts and devices, then grant notification access
   and *Nearby devices* when asked.

Useful while developing:

```bash
adb logcat -s BacklitToy BacklitGlyph BacklitMusic BacklitAudio BacklitAlerts
```

```bash
adb exec-out screencap -p > screen.png
```

Debug builds also contain debug-only audio probes (`app/src/debug`) that were used for the
screen-off feasibility spike. They aren't part of release builds.

---

## Project structure

```
app/src/main/java/app/backlit/
├── render/          Pure Kotlin drawing (no Android imports)
│   ├── PixelGrid.kt         size×size brightness grid (0–255) with the round LED mask
│   ├── Draw.kt, PixelFont.kt  anti-aliased lines, discs, 3×5 digits
│   ├── faces/               AnalogFace, DayRingFace, Face registry
│   └── viz/                 MirrorBars, MirrorPeaks, IdleLine, VizStyles
├── anim/            Pure Kotlin event animations
│   ├── GlyphAnimation.kt    interface, bitmap + midpoint-circle helpers
│   ├── Heartbeat, SmileyWink, Ringing, Burst, Link, Bounce, BuiltInAnimations
│   └── MuseumFormat.kt      Glyph Museum JSON parse / export / 25↔13 resampling
├── audio/           Music analysis
│   ├── SpectrumAnalyzer, MusicState, MusicEngine, DemoAudio, AudioFrame   (pure)
│   └── OutputVisualizer, MusicActivity, AudioPermission                   (Android)
├── alerts/          Important callers & Bluetooth
│   ├── Rules, NameMatch, CallNotificationTracker, AlertCoordinator, AnimationLibrary   (pure)
│   └── AlertStore, AlertsRuntime, ToyPresence, CallAlertListener,
│       BluetoothAlertReceiver, ImportHelper                                            (Android)
├── data/            Settings (DataStore), sunrise/sunset maths, location, offline cities
├── glyph/           The only package that talks to the Nothing SDK
│   ├── GlyphOutput.kt       connect/register/push with retry; toy or app-matrix mode
│   ├── FrameEncoder.kt      0–255 design brightness → SDK 0–2047 (with a minimum visible level)
│   ├── FramePacer.kt        fixed-rate frame scheduling
│   ├── ClockToyService, MusicToyService   the two Glyph Toys
│   └── AppMatrixPlayer.kt   plays alerts when no Backlit toy is showing
├── ui/              Jetpack Compose screens (Home tabs CLOCK | MUSIC | ALERTS, Setup, Location, About)
└── MainActivity.kt
app/src/test/        JVM unit tests mirroring the packages above
app/src/debug/       debug-only spike probes
libs/                put the Nothing SDK .aar here (gitignored)
scripts/build_cities.py   builds the bundled offline city list from GeoNames
docs/                specs, plans, privacy policy, Play listing, device checklist
```

---

## Architecture

- **Pure core, thin edges.** Everything that decides *what* the matrix shows (faces, visualizer
  styles, animations, audio analysis, alert logic, Glyph Museum parsing) is plain Kotlin with no
  Android imports, and is unit-tested on the JVM. Android and SDK code is kept to thin wrappers.
- **One frame type.** Everything renders into a `PixelGrid` of design brightness 0–255. Only
  `FrameEncoder` converts to the SDK's 0–2047 scale, lifting any lit pixel to a minimum visible
  level because the LEDs read as "off" near the bottom of their range.
- **Preview equals hardware.** The in-app `MatrixPreview` draws the same `PixelGrid` the toy pushes,
  so what you see in the app is what the back of the phone shows.
- **Who owns the matrix.**
  - A Glyph Toy only draws while it is selected.
  - Alerts use the app-matrix API when the Glyph is idle.
  - When a Backlit toy is showing, alerts go through an in-process `AlertsRuntime` `StateFlow`
    and the toy renders them.
  - `ToyPresence` makes sure the toy and the app-matrix player never use the per-process
    `GlyphMatrixManager` at the same time.
- **Nothing runs when you aren't looking.** The toys start work on `onBind` and release
  everything (audio, SDK, coroutines) on `onUnbind`. There are no foreground services and no wakelocks.

---

## Permissions and privacy

| Permission | Why | When it's asked |
|---|---|---|
| `com.nothing.ketchum.permission.ENABLE` | drive the Glyph Matrix | install |
| `ACCESS_COARSE_LOCATION` | Day ring sunrise/sunset (rounded to ~1 km) | only if you choose *Approximate location* |
| `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS` | read the sound the phone is already playing, for the Music toy | when you tap *Allow* in the Music tab |
| Notification access | see incoming/missed call notifications to match important contacts | when you add an important contact |
| `BLUETOOTH_CONNECT` (Nearby devices) | notice chosen Bluetooth devices connecting | when you add a device |

- No network calls, analytics, ads or accounts.
- Audio is analysed in real time and never recorded or stored.
- Call notifications are checked for the caller's name and then ignored. Only the contacts and
  devices you pick are saved, on the phone.
- No contacts, call-log or phone-state permissions. Contacts are picked with Android's contact picker.
- Full text: [`docs/privacy-policy.md`](docs/privacy-policy.md).

---

## Testing

Unit tests (JVM, ~150 tests):

```bash
./gradlew :app:testDebugUnitTest
```

They cover pixel-exact face rendering, sunrise/sunset maths, the spectrum analyzer and music state
machine, every visualizer style, all built-in animations at both grid sizes, Glyph Museum
parsing/validation/resampling, contact-name matching, call-notification tracking, alert timing
(cooldowns, missed-call reminders, priorities) and settings/rules persistence.

On-device checks are listed in [`docs/testing/device-checklist.md`](docs/testing/device-checklist.md).

---

## Glyph Museum import format

Backlit imports the JSON that [Glyph Museum](https://play.google.com/store/apps/details?id=com.pauwma.glyphmuseum)
and the Glyph Matrix Editor export:

```json
{ "v": 1, "frames": [ { "d": 100, "p": [0, 255, 128, …] } ] }
```

- `v`: `1` = Phone (3), 489 values per frame. `4` = Phone (4a) Pro, 137 values per frame.
- `p`: brightness 0–255 for each physical LED, row by row, only positions inside the round mask.
  Row widths are `7,11,15,17,19,21,21,23,23,25,25,25,25,25,25,25,23,23,21,21,19,17,15,11,7` (25×25)
  and `5,9,11,11,13,13,13,13,13,11,11,9,5` (13×13).
- `d`: frame duration in ms (default 100, clamped to 20–5000). Up to 600 frames per file.

Backlit learned the format by reading the open-source editor; no code was copied.

---

## Platform limits

- **Only the selected toy is shown.** Another app's toy hides Backlit's alerts. Using Backlit Clock
  as your always-on toy avoids this.
- **Nothing's ringtone Glyph wins during calls.** Nothing OS plays its own ringtone pattern a moment
  into every incoming call, above any app, and there's no user setting to turn it off. Backlit plays
  the contact's animation as the call starts, and again for missed calls.
- **The Music toy needs the Phone (3).** The (4a) Pro only runs always-on toys, which update once a minute.
- **Phone (4a) Pro hasn't been tested on real hardware yet.** Its 13×13 layouts are designed and
  previewable in the app.

---

## Docs, specs and plans

| Part | Spec | Plan |
|---|---|---|
| Core + Clock toy | [`2026-10-03-backlit-clocks-design.md`](docs/superpowers/specs/2026-10-03-backlit-clocks-design.md) | [`2026-10-03-backlit-clocks.md`](docs/superpowers/plans/2026-10-03-backlit-clocks.md) |
| Music toy | [`2026-10-03-backlit-music-design.md`](docs/superpowers/specs/2026-10-03-backlit-music-design.md) | [`2026-10-03-backlit-music.md`](docs/superpowers/plans/2026-10-03-backlit-music.md) |
| Alerts | [`2026-10-04-backlit-alerts-design.md`](docs/superpowers/specs/2026-10-04-backlit-alerts-design.md) | [`2026-10-04-backlit-alerts.md`](docs/superpowers/plans/2026-10-04-backlit-alerts.md) |

Also: [Play listing draft](docs/release/play-listing.md) · [Privacy policy](docs/privacy-policy.md) ·
[Device checklist](docs/testing/device-checklist.md).

---

## Roadmap

- **OTP on Glyph:** show verification codes on the matrix (feasibility check on Android 16 in progress).
- **AI reactions:** animations while you talk to ChatGPT, Gemini or Claude (parked, design started).
- **Play Store release:** signing key, store listing and screenshots.

---

## Credits

- Fonts: [Doto](https://fonts.google.com/specimen/Doto) and [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk), SIL Open Font License 1.1.
- City data: [GeoNames](https://www.geonames.org/), CC BY 4.0.
- Glyph Matrix SDK: Nothing Technology Ltd. (not redistributed).
- Glyph Museum format: [Glyph Matrix Editor](https://github.com/pauwma/GlyphMatrixEditor) by pauwma (format only).
