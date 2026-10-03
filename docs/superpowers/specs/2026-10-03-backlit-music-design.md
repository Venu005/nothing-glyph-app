# Backlit — Part 2: Music Visualizer Toy — Design

**Date:** 2026-10-03
**Status:** Draft for review
**Builds on:** `docs/superpowers/specs/2026-10-03-backlit-clocks-design.md` (Part 1, merged)

## 1. Goal

Add a second Glyph Toy, **Backlit Music**. While music plays on the phone, the Glyph Matrix shows
a visualizer that reacts to the song. When nothing plays, it shows a softly breathing centre line.

### Success criteria
- On a Phone (3), with music playing from any app (Spotify, YouTube Music, …), the matrix moves
  in time with the song in all three styles.
- A long press on the Glyph Button cycles the styles. The app's Music tab shows the same frames
  as a live preview.
- Without the audio permission, or if the system blocks capture, the toy still works and shows the
  breathing line. It never crashes or goes blank.
- Nothing runs while the toy isn't showing. No recording, storage or network.

### Out of scope
Phone (4a) Pro support (AOD-only device, see §2), the clock switching automatically to the
visualizer, microphone/room-sound mode, screen-capture audio, extra styles A–D from the mockups
(sunburst, pulse rings, EQ bars, waveform line), and track info.

## 2. Platform facts and constraints

- **Audio source:** `android.media.audiofx.Visualizer` on audio session **0** (the global output
  mix). It needs `RECORD_AUDIO` (a runtime permission) and `MODIFY_AUDIO_SETTINGS` (granted
  automatically). FFT is captured at `Visualizer.getMaxCaptureRate()` (typically 20 Hz) with
  capture size `getCaptureSizeRange()[1]` (typically 1024).
  - FFT byte layout: `[Re0, Re(n/2), Re1, Im1, Re2, Im2, …]`. Bin k is at frequency
    `k × samplingRate / n`, where `getSamplingRate()` is in mHz.
- **Known risk: audio offload.** With the screen off, Android may play music through an offload
  path that the output-mix Visualizer can't see, so it receives silence. **Plan task 1 is a spike on
  the user's Phone (3)** that measures this. If it receives silence with the screen off, stop and
  re-decide (microphone or the no-permission animation) before building further.
- **Music detection without permission:** `AudioManager.isMusicActive()`.
- **The Glyph Matrix shows only the selected toy.** The Music toy is a separate toy that the user
  cycles to with the Glyph Button.
- **Phone (4a) Pro runs AOD toys only** (one update per minute), so a visualizer can't run there.
  The Music toy declares `com.nothing.glyph.toy.aod_support = 0`. On a 4a Pro the service does
  nothing, and the app's Music tab explains why.
- **Frame rate:** target 20 fps (it matches the Visualizer capture rate). The spike measures the real
  `setMatrixFrame` cost on the device.

## 3. Architecture

New code follows Part 1's boundaries:

```
app/src/main/java/app/backlit/
├─ audio/                    Only package that touches Android audio APIs
│   ├─ SpectrumAnalyzer      pure Kotlin: FFT bytes → AudioFrame (unit-tested)
│   ├─ AudioFrame            data: bands FloatArray(8) 0..1, level 0..1, kick 0..1
│   ├─ MusicState            pure Kotlin state machine (IDLE / LIVE / FALLBACK), unit-tested
│   ├─ OutputVisualizer      wraps android.media.audiofx.Visualizer(0); emits FFT bytes
│   ├─ MusicActivity         AudioManager.isMusicActive() wrapper
│   └─ DemoAudio             pure Kotlin synthetic AudioFrames for the in-app preview
├─ render/viz/               pure Kotlin, stateful visualizer styles
│   ├─ VizStyle              interface: id, label, update(frame, dtMs), render(size): PixelGrid
│   ├─ MirrorBars            style E
│   ├─ MirrorPeaks           style F
│   ├─ ScrollWave            style G
│   ├─ IdleLine              breathing centre line (idle + fallback)
│   └─ VizStyles             registry: all, byId, next (same pattern as Faces)
├─ glyph/MusicToyService     second Glyph Toy
└─ ui/MusicScreen            Music tab
```

`render/viz` must not import `android.*`. `audio/` is the only package importing
`android.media.*`.

## 4. Audio analysis (`SpectrumAnalyzer`)

- Input: FFT bytes and the sampling rate in Hz. Output: `AudioFrame`.
- Magnitude per bin = `hypot(re, im)`. Bins are grouped into **8 log-spaced bands** with edges
  `20, 60, 150, 400, 1000, 2500, 6000, 12000, 20000` Hz. The band value is the mean magnitude of its
  bins (0 if a band has no bins).
- **Auto-gain:** each band is divided by a running peak that rises immediately and decays with a
  ~3 s time constant (floor 1.0 so silence stays at 0). Then it's clamped to 0..1.
- **Sensitivity** multiplies before clamping: LOW 0.7, MED 1.0, HIGH 1.4.
- `level` = mean of the bands. `kick` = 1.0 when band 0 exceeds 1.5× its ~1 s running average
  (onset), decaying ×0.8 per frame.
- **Silence:** if every raw magnitude is below a small floor, output all zeros.

## 5. Visualizer styles (`render/viz`)

All styles share the column model: each column x gets a level 0..1 interpolated from the bands.
The centre column is band 0 (bass) and the edge columns are band 7 (treble), mirrored left/right.
Levels taper towards the edges (× (1 − 0.45·d), where d is the 0..1 distance from the centre).
Column levels rise instantly and fall as `old × 0.7 + new × 0.3` per update.

- **E, MirrorBars (`mirror`):** the centre row is lit at 140. Each column lights `h = round(level ×
  (size/2 − 0.5))` pixels above and below the centre at 255.
- **F, MirrorPeaks (`peaks`):** like E, but the bar pixels go from 90 near the centre to 200 at the
  tips. A peak dot at 255 sits one pixel beyond the bar. Peaks hold for 250 ms, then drop one pixel
  every 200 ms.
- **G, ScrollWave (`wave`):** keeps a history of `level` samples, one new sample every 70 ms. The
  newest sample is on the right edge and older ones shift left. Each column is a mirrored bar of
  that sample's height, with brightness fading from 255 on the right to 90 on the left.
- **IdleLine:** the centre row, whose brightness breathes between 60 and 140 over a 4 s sine cycle.
  In FALLBACK (music playing but no audio data) it breathes faster (2 s) and between 80 and 180.

The LED round mask from `PixelGrid` applies automatically. The styles are written for 25×25. Small
sizes still render correctly (tested at 13 for robustness), even though the toy doesn't run on 13×13.

## 6. Runtime (`MusicToyService`)

- **`onBind`:** detect the device. If it isn't a Phone (3), return a binder and do nothing. Otherwise
  connect `GlyphOutput`, collect settings, and start `OutputVisualizer` if `RECORD_AUDIO` is
  granted. Start the frame loop.
- **Frame loop (main thread, every 50 ms):** read `MusicActivity` and the latest FFT, run
  `SpectrumAnalyzer`, update `MusicState`, then:
  - IDLE or FALLBACK → `IdleLine.update/render`
  - LIVE → the current style `update(frame, dt)` / `render(25)`

  Encode with `FrameEncoder` (user brightness, `aod = false`) and push. Unchanged frames are skipped
  by `GlyphOutput`.
- **`MusicState` transitions:**
  - `!musicActive` → IDLE. On entering IDLE from LIVE, the style keeps updating with zero frames
    for 1 s so the bars fall smoothly.
  - `musicActive` and audio frames have a level above zero → LIVE
  - `musicActive` and no visualizer, or all-zero frames for 5 s → FALLBACK. While in FALLBACK the
    visualizer is re-created every 10 s.
- **Long press (`EVENT_CHANGE`):** `musicStyle = VizStyles.next(musicStyle)`, saved to settings.
- **`onUnbind`:** release the Visualizer, cancel the loop and scope, close `GlyphOutput`.
- **Crash guard:** the same as Part 1. `CoroutineExceptionHandler`, render wrapped in `runCatching`
  (blank frame on failure), and Visualizer construction and calls wrapped in `runCatching`, falling
  back to FALLBACK.

## 7. Settings

These new fields are added to `Settings` and persisted by `SettingsRepo`:
- `musicStyle: String = "mirror"` (one of `mirror`, `peaks`, `wave`)
- `musicSensitivity: Sensitivity = MED` (`LOW`, `MED`, `HIGH`, stored by name, unknown → MED)

## 8. App UI

- **Home** gets top tabs **CLOCK | MUSIC** (dot-matrix style, square chips). The CLOCK tab is the
  existing Home content.
- **MUSIC tab:**
  - A live `MatrixPreview` of the 25×25 frame. It uses real audio when `RECORD_AUDIO` is granted
    and the app is in the foreground, and `DemoAudio` otherwise.
  - Style chips **MIRROR · PEAKS · WAVE**, plus a Sensitivity row (LOW / MED / HIGH, tap to cycle)
    and the shared Brightness row.
  - Permission not granted → a disclosure panel with the exact text: "Let Backlit react to your
    music. Android files this under the microphone permission, but Backlit only reads the sound
    your phone is already playing. Nothing is recorded, stored, or sent anywhere." and an **ALLOW**
    button that launches the runtime request.
  - Denied → "Music reactions are off. The toy will show a calm line instead." plus an **OPEN
    SETTINGS** button that opens the app's system settings page.
  - Device is not a Phone (3) → "The Music toy needs the Phone (3). The (4a) Pro only supports
    always-on toys." The preview still runs on demo audio.
- **Setup screen:** add a step: "Phone (3): also switch on \"Backlit Music\" in Glyph Toys."
- **About / privacy policy:** add a microphone paragraph. Audio is analysed on the phone, in real
  time, only while the Music toy or Music tab is showing. It is never recorded, stored or shared.

## 9. Manifest

- Permissions added: `android.permission.RECORD_AUDIO` and
  `android.permission.MODIFY_AUDIO_SETTINGS`.
- Second toy service `.glyph.MusicToyService` with action `com.nothing.glyph.TOY`, name
  "Backlit Music", summary "Reacts to the music playing on your phone. Long press to change style.",
  its own preview drawable, `longpress = 1`, `aod_support = 0`.

## 10. Error handling

| Situation | Behavior |
|---|---|
| No `RECORD_AUDIO` | No Visualizer. IDLE or FALLBACK only. |
| `Visualizer(0)` throws or `setEnabled` fails | Log it, go to FALLBACK, retry every 10 s. |
| Visualizer delivers only silence while music plays | FALLBACK after 5 s, retry every 10 s. |
| SDK disconnect | `GlyphOutput` retry (Part 1 behavior). |
| Not a Phone (3) | The service does nothing. The app explains why. |
| Render throws | Blank frame and a log entry. The loop continues. |

## 11. Testing

- **Unit tests (JVM):**
  - `SpectrumAnalyzer`: a synthetic FFT with energy at 50 Hz peaks band 0, at 15 kHz peaks band 7;
    all-zero input gives zero frames; quiet and loud versions of the same spectrum normalise to
    similar values after warm-up; sensitivity scales the output; the kick fires on a band-0 onset.
  - `MirrorBars`: vertically symmetric about the centre row; the centre column is the tallest for a
    bass-only frame; a zero frame lights only the centre row.
  - `MirrorPeaks`: the peak sits above the bar; it holds 250 ms, then falls.
  - `ScrollWave`: a new sample enters at the right and shifts left by one column per 70 ms.
  - `IdleLine`: lights only the centre row; brightness changes over time; fallback is brighter.
  - `MusicState`: every transition in §6, using a fake clock.
  - `VizStyles`: byId fallback and next cycling.
  - `SettingsRepo`: round-trip of the new fields; an unknown sensitivity falls back to MED.
- **Spike (plan task 1, on the Phone (3)):** a debug-only probe that logs Visualizer level and
  `setMatrixFrame` duration with the screen on and off while music plays. It decides go/no-go.
- **Device checklist:** reacts in all styles with Spotify and YouTube Music, long press cycles,
  pause falls back to idle, permission denial path, leaving the toy stops all work (checked with
  `dumpsys`), no visible stutter.
