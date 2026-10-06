# Backlit

**Clocks, music, a pet ghost, your own drawings and alerts for the Glyph Matrix on the back of Nothing phones.**

Backlit is an Android app for the **Nothing Phone (3)** (25×25 Glyph Matrix) and **Nothing Phone (4a) Pro**
(13×13). It adds five Glyph Toys you cycle to with the Glyph Button (Clock, Music, Charge, Canvas and Pet),
a pixel studio for drawing your own animations, and event animations that light up the matrix when an
important contact calls or one of your Bluetooth devices connects. The companion app
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

The app opens on a grid of live toy cards; tap one for its page and swipe between toys. The icon is the
"Diamond ring" logo (`render/LogoGeometry.kt`), also used for the themed icon and the Play Store icon (`docs/store/icon-512.png`).

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

### Backlit Charge (Glyph Toy)
- A battery toy with four styles: **Sprout** (a plant that grows), **Buddy** (a little blob that fills
  up), **Big number** and **Moon** (the lit phase is your level). Long-press to switch.
- Shows your level all day. Plug in for a plug-in animation, then a gentle charging loop that flashes
  the % every 10 s, and a **done** moment once you reach your chosen level (50–100 %).
- Plug-in and done can use Glyph Museum imports instead. AOD (always-on) shows the still level.

### Backlit Studio + Canvas (Glyph Toy)
- Draw your own pictures and animations at your phone's matrix size, in 3 shades: pen, erase, line,
  circle, fill, text, mirror, undo/redo and shift, with up to 24 frames, a speed setting and per-frame hold.
- Drawings appear everywhere Backlit picks an animation (contacts, devices, charging), can be shared as
  Glyph Museum JSON, and Glyph Museum files import as editable drawings.
- **Backlit Canvas** shows a drawing on the back; long-press for the next one. AOD shows its first frame.

### Backlit Pet (Glyph Toy)
- **Six pets to choose from** in the PET tab: Ghost (Boo), Frog (Ribbit), Penguin (Waddles), Axolotl (Lotl),
  Owl (Hoot) and Robot (Bolt). They share one mood; each has its own name and signature move (tongue and fly,
  belly-slide, bubbles, head swivel, glitch).
- **Reactions:**
  - **Long-press:** you pet him and hearts float up.
  - **Shake:** he gets dizzy. Shake him hard three times and he gets angry and steams; a long press calms him down.
  - **Tilt:** his eyes follow.
  - **Face-down:** turn the phone face-down for a peekaboo.
  - **Charging:** he munches.
  - **Night:** he sleeps, and a long press makes him yawn.
- **Mood (0–100):** petting, peekaboo, calming him and charging raise it. It slowly drops while he's
  awake and ignored, about −10 an hour, so he gets **bored** after a few hours (half-lidded side-eye,
  yawns) and then **sad** (droopy eyes, a tear). It never drops while he sleeps, and he never dies.
  Mood is worked out from timestamps, so nothing runs in the background.
- **AOD:** still poses that update once a minute. You see his mood face, asleep with a "z" at night,
  or **filling up like a battery** while charging.
- **PET tab:** a live preview, "BOO IS HAPPY" with a 10-dot meter, "MOOD 63 / 100 · Gets bored in ~2 h",
  a collapsible "how his mood works" card, his name and sleep hours, and Show on Glyph.

### Backlit Sand (Glyph Toy)
A real hourglass. On the Phone (3), live sand pours as you tilt the phone.
- **Flip** the phone over to start. Flip it mid-way and the sand runs back, so the time left becomes the time run.
- **On its side** pauses it, and **face-down** on a desk keeps it running.
- **Long press** the Glyph button to pick 1, 3, 5, 10 or 25 minutes (editable in the TIMER tab). While it runs, the first press shows the minutes left.
- **Time's up:** the hourglass spins itself over ("Flip me"), with a vibration (or Glyph only, or with a chime). This works even when another toy is showing or the screen is off.
- **Always-on:** a still that updates every minute. On the 4a Pro, flips are picked up within a minute.
- **TIMER tab:** what the timer is doing right now, your times, what happens when time's up, and an optional "Ring exactly on time" switch.

### Backlit Badge (Glyph Toy)
A status sign for when your phone is face-down: a pixel icon with your message scrolling underneath.
- **Messages:** up to 8, each with one of 8 icons (laptop, coffee, phone, moon, heart, car, headphones, food).
  Starters: IN A MEETING, BACK IN 5, ON A CALL, DO NOT DISTURB, THANK YOU.
- **Smart messages:** a countdown ("BACK IN 4:59" → "BACK SOON") or until a time ("IN A MEETING UNTIL 3PM").
- **Long press** the Glyph button to switch messages; the icon flashes on change.
- **Always-on:** the icon with a short word, the minutes left or the time, once a minute.
- **BADGE tab:** pick, add, edit, reorder and delete messages.

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

| Device | Grid | Clock · Charge · Canvas · Pet | Music | Studio | Alerts |
|---|---|---|---|---|---|
| Nothing Phone (3) | 25×25 | ✅ active + AOD (verified on device) | ✅ | ✅ | ✅ (verified on device) |
| Nothing Phone (4a) Pro | 13×13 | ✅ AOD only (designed, **not yet verified on hardware**) | ❌ (AOD-only device) | ✅ draws at 13×13 | designed, **not yet verified on hardware** |
| Other phones | — | preview only in the app | — | preview only | — |

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
│   ├── viz/                 MirrorBars, MirrorPeaks, IdleLine, VizStyles
│   ├── charge/              Sprout, Buddy, Big number, Moon charge styles + ChargeKit helpers
│   └── ToyPreviewXml.kt     Nothing-style Glyph Toys picker images, generated from real frames
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
├── charge/          ChargeSession state machine, ChargePreviewAnimation (pure)
├── studio/          Pixel studio: Drawing, EditorState (undo), Raster, text font, DrawingCodec,
│                    Canvas toy helpers (pure)
├── pet/             Glyph Pet: PetMood (timestamp decay), PetBrain (state machine), GhostArt,
│                    PetInsight (hints), PetPreviewAnimation (pure)
├── sand/            Sand timer: HourglassShape, SandSim (grain physics), TimerState (flip/side/long press),
│                    SandArt, SandPreviewAnimation (pure); SandAlarm + receiver (Android)
├── badge/           Message badge: BadgeMessage, BadgeText (countdown/until), BadgeFont, BadgeIcons,
│                    BadgeArt, BadgePreviewAnimation (pure)
├── data/            Settings (DataStore), sunrise/sunset maths, location, offline cities
├── glyph/           The only package that talks to the Nothing SDK
│   ├── GlyphOutput.kt       connect/register/push with retry; toy or app-matrix mode
│   ├── FrameEncoder.kt      0–255 design brightness → SDK 0–2047 (with a minimum visible level)
│   ├── FramePacer.kt        fixed-rate frame scheduling
│   ├── ClockToyService, MusicToyService, ChargeToyService,
│   │   CanvasToyService, PetToyService     the five Glyph Toys
│   ├── PlugWatcher.kt       remembers plug-in time so Charge can replay after Nothing's animation
│   └── AppMatrixPlayer.kt   plays alerts when no Backlit toy is showing
├── ui/              Jetpack Compose screens: Welcome, Home grid, toy pager, Studio + pixel editor, Alerts,
│   │                Settings, Privacy, Setup, Location, About
│   ├── home/        ToyCatalog (order, set-up status), ToyThumbs (live card previews)   (pure)
│   ├── nav/         Route (screens, Back, save/restore)   (pure)
│   ├── components/  Nothing-style components: BacklitLogo, ToyCard, ToolCard, ToyHeader, PagerDots…
│   └── toys/        ToyPagerScreen, ClockTab
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
  everything (audio, sensors, SDK, coroutines) on `onUnbind`. There are no foreground services and no wakelocks.
  The pet's accelerometer runs only while his toy is showing with the screen on. Moods and charge
  sessions are worked out from timestamps instead of background timers.

---

## Permissions and privacy

| Permission | Why | When it's asked |
|---|---|---|
| `com.nothing.ketchum.permission.ENABLE` | drive the Glyph Matrix | install |
| `ACCESS_COARSE_LOCATION` | Day ring sunrise/sunset (rounded to ~1 km) | only if you choose *Approximate location* |
| `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS` | read the sound the phone is already playing, for the Music toy | when you tap *Allow* in the Music tab |
| Notification access | see incoming/missed call notifications to match important contacts | when you add an important contact |
| `BLUETOOTH_CONNECT` (Nearby devices) | notice chosen Bluetooth devices connecting | when you add a device |
| `VIBRATE` | the sand timer's done buzz | install (no prompt) |
| `SCHEDULE_EXACT_ALARM` (Alarms & reminders) | ring the sand timer exactly on time while the phone sleeps | only if you turn on *Ring exactly on time* |
| `RECEIVE_BOOT_COMPLETED` | keep a running sand timer's alarm after a restart | install (no prompt) |

The pet's motion sensing (shake, tilt, face-down) and the sand timer's tilt use the accelerometer, which needs no permission.
Sharing a drawing uses Android's share sheet through a private FileProvider.

- No network calls, analytics, ads or accounts.
- Audio is analysed in real time and never recorded or stored.
- Call notifications are checked for the caller's name and then ignored. Only the contacts and
  devices you pick are saved, on the phone.
- No contacts, call-log or phone-state permissions. Contacts are picked with Android's contact picker.
- Full text: [`docs/privacy-policy.md`](docs/privacy-policy.md).

---

## Testing

Unit tests (JVM, ~265 tests):

```bash
./gradlew :app:testDebugUnitTest
```

They cover pixel-exact face rendering, sunrise/sunset maths, the spectrum analyzer and music state
machine, every visualizer style, all built-in animations at both grid sizes, Glyph Museum
parsing/validation/resampling, contact-name matching, call-notification tracking, alert timing
(cooldowns, missed-call reminders, priorities), charge sessions and styles, the pixel editor
(every tool, undo, frames) and drawing ↔ Glyph Museum round trips, the pet's mood decay (sleep
windows, multi-day gaps, frequent saves), reactions and ghost art, and settings/rules persistence. A
golden test keeps the Glyph Toys picker images in sync with the toys.

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
- **Nothing's charge animation plays first.** Plugging in shows Nothing's own battery Glyph for about
  3 s, with no setting to turn it off. Backlit Charge replays its plug-in animation when the matrix comes back.
- **AOD updates once a minute.** In always-on mode, toys (including the pet) show still poses that
  refresh about once a minute. Animations, and the pet's motion sensing, run while the screen is on.
- **The Music toy needs the Phone (3).** The (4a) Pro only runs always-on toys, which update once a minute.
- **Phone (4a) Pro hasn't been tested on real hardware yet.** Its 13×13 layouts are designed and
  previewable in the app.

---

## Docs, specs and plans

| Part | Spec | Plan |
|---|---|---|
| UI redesign + logo (part 1) | [`2026-10-06-backlit-ui-redesign-design.md`](docs/superpowers/specs/2026-10-06-backlit-ui-redesign-design.md) | [`2026-10-06-backlit-ui-redesign-part1.md`](docs/superpowers/plans/2026-10-06-backlit-ui-redesign-part1.md) |
| Core + Clock toy | [`2026-10-03-backlit-clocks-design.md`](docs/superpowers/specs/2026-10-03-backlit-clocks-design.md) | [`2026-10-03-backlit-clocks.md`](docs/superpowers/plans/2026-10-03-backlit-clocks.md) |
| Music toy | [`2026-10-03-backlit-music-design.md`](docs/superpowers/specs/2026-10-03-backlit-music-design.md) | [`2026-10-03-backlit-music.md`](docs/superpowers/plans/2026-10-03-backlit-music.md) |
| Alerts | [`2026-10-04-backlit-alerts-design.md`](docs/superpowers/specs/2026-10-04-backlit-alerts-design.md) | [`2026-10-04-backlit-alerts.md`](docs/superpowers/plans/2026-10-04-backlit-alerts.md) |
| Charge toy | [`2026-10-04-backlit-charge-design.md`](docs/superpowers/specs/2026-10-04-backlit-charge-design.md) | [`2026-10-04-backlit-charge.md`](docs/superpowers/plans/2026-10-04-backlit-charge.md) |
| Studio + Canvas | [`2026-10-05-backlit-studio-design.md`](docs/superpowers/specs/2026-10-05-backlit-studio-design.md) | [`2026-10-05-backlit-studio.md`](docs/superpowers/plans/2026-10-05-backlit-studio.md) |
| Glyph Pet | [`2026-10-05-backlit-pet-design.md`](docs/superpowers/specs/2026-10-05-backlit-pet-design.md) | [`2026-10-05-backlit-pet.md`](docs/superpowers/plans/2026-10-05-backlit-pet.md) |

Also: [Play listing draft](docs/release/play-listing.md) · [Privacy policy](docs/privacy-policy.md) ·
[Device checklist](docs/testing/device-checklist.md).

---

## Roadmap

- **Blow out the candles:** pixel birthday candles you blow out into the mic.
- **Next-event countdown:** your next calendar event on the matrix.
- **Later ideas:** animated weather, gentle reminders (water, stretch).
- **Parked:** OTP on Glyph, AI reactions (ChatGPT, Gemini, Claude).
- **Play Store release:** signing key, store listing and screenshots.

---

## Credits

- Fonts: [Doto](https://fonts.google.com/specimen/Doto) and [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk), SIL Open Font License 1.1.
- City data: [GeoNames](https://www.geonames.org/), CC BY 4.0.
- Glyph Matrix SDK: Nothing Technology Ltd. (not redistributed).
- Glyph Museum format: [Glyph Matrix Editor](https://github.com/pauwma/GlyphMatrixEditor) by pauwma (format only).
