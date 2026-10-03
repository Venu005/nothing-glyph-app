# Backlit Part 2 (Music Visualizer Toy) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a second Glyph Toy, "Backlit Music", that animates the Phone (3) Glyph Matrix in time with whatever music is playing (styles Mirror / Peaks / Wave), with a breathing idle line, plus a Music tab in the app.

**Architecture:** `android.media.audiofx.Visualizer(0)` FFT bytes, polled every 50 ms on the main thread → pure-Kotlin `SpectrumAnalyzer` (8 bands with auto-gain) → `MusicState` (IDLE/LIVE/FALLBACK) → pure-Kotlin stateful styles in `render/viz` → `PixelGrid` → Part 1's `FrameEncoder` + `GlyphOutput`. A pure `MusicEngine` ties analyzer, state and styles together and is shared by the toy service and the in-app preview.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX DataStore, kotlinx-coroutines, JUnit 4, Android `Visualizer`/`AudioManager`, Nothing GlyphMatrix SDK (already integrated in Part 1).

**Spec:** `docs/superpowers/specs/2026-10-03-backlit-music-design.md` (builds on `docs/superpowers/specs/2026-10-03-backlit-clocks-design.md`)

## Global Constraints

- Work on branch `feat/backlit-part2`. Package root `app.backlit`.
- Before any Gradle command in a fresh shell: `source .superpowers/env.sh` (sets JAVA_HOME / ANDROID_HOME; the Bash tool doesn't load `~/.zshrc`). Unit tests with a summary line: `.superpowers/runtests.sh [gradle args]`.
- `render/` (including `render/viz`) never imports `android.*`, `androidx.*` or `com.nothing.*`.
- `audio/` is the only package that imports `android.media.*`. Pure files in `audio/` (`AudioFrame`, `SpectrumAnalyzer`, `MusicState`, `DemoAudio`, `MusicEngine`) import no Android at all.
- Only `glyph/` imports `com.nothing.ketchum.*`.
- No network calls, no recording or storage of audio, no analytics.
- New permissions: `android.permission.RECORD_AUDIO`, `android.permission.MODIFY_AUDIO_SETTINGS`.
- The Music toy runs only on `DeviceProfile.PHONE_3` (25×25). It declares `com.nothing.glyph.toy.aod_support = 0`.
- Frame loop period 50 ms (20 fps). Style ids: `mirror`, `peaks`, `wave`. Labels `MIRROR`, `PEAKS`, `WAVE`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Short silence between tracks (2–4 s) while LIVE** must not drop to the idle line or fallback. Pinned by `MusicStateTest.shortSilenceDuringLiveStaysLive` (Task 3).
2. **Audio permission revoked while the toy runs** (`getFft` fails → `null`) must lead to FALLBACK after 5 s, not a crash or a frozen frame. Pinned by `MusicEngineTest.missingFftWhileMusicPlaysFallsBack` (Task 6).
3. **Very loud or clipped tracks at HIGH sensitivity** must never push a band above 1.0. Pinned by `SpectrumAnalyzerTest.outputIsClampedAtHighSensitivity` (Task 2).
4. **Rapid long presses changing style mid-song** must swap styles cleanly and keep rendering. Pinned by `MusicEngineTest.switchingStyleMidSongKeepsRendering` (Task 6).
5. **Music paused** must make the bars fall smoothly for ~1 s before the idle line, not snap. Pinned by `MusicEngineTest.pauseDecaysThenShowsIdleLine` (Task 6).

---

## File Structure

```
app/src/debug/AndroidManifest.xml                         (Task 1) debug-only probe activity + audio permissions
app/src/debug/java/app/backlit/debug/AudioProbeActivity.kt (Task 1) spike: logs Visualizer levels, screen on/off
app/src/main/java/app/backlit/audio/
  AudioFrame.kt          8 bands + level + kick
  SpectrumAnalyzer.kt    FFT bytes → AudioFrame (auto-gain, sensitivity, kick)
  MusicState.kt          IDLE / LIVE / FALLBACK transitions, decay window, retry timer
  DemoAudio.kt           synthetic frames for the in-app preview
  MusicEngine.kt         analyzer + state + styles + idle line → PixelGrid
  OutputVisualizer.kt    android Visualizer(0) wrapper
  MusicActivity.kt       AudioManager.isMusicActive wrapper
  AudioPermission.kt     RECORD_AUDIO check
app/src/main/java/app/backlit/render/viz/
  VizStyle.kt            interface + ColumnLevels (shared column model)
  MirrorBars.kt  MirrorPeaks.kt  ScrollWave.kt  IdleLine.kt
  VizStyles.kt           registry
app/src/main/java/app/backlit/glyph/MusicToyService.kt
app/src/main/java/app/backlit/ui/MusicScreen.kt           MusicTab composable
app/src/main/java/app/backlit/ui/HomeScreen.kt            (rewrite) CLOCK | MUSIC tabs
app/src/main/java/app/backlit/data/Settings.kt            (+ musicStyle, musicSensitivity, Sensitivity)
app/src/main/java/app/backlit/data/SettingsRepo.kt        (+ two keys)
app/src/main/java/app/backlit/ui/SetupScreen.kt           (+ Music step)
app/src/main/java/app/backlit/ui/AboutScreen.kt           (+ microphone sentence)
app/src/main/AndroidManifest.xml                          (+ permissions, Music toy service)
app/src/main/res/drawable/ic_music_preview.xml, res/values/strings.xml
docs/privacy-policy.md, docs/release/play-listing.md, docs/testing/device-checklist.md
tests under app/src/test/java/app/backlit/{audio,render/viz,data}/
```

---

### Task 1: Spike — does `Visualizer(0)` hear music with the screen off? (go / no-go)

**Files:**
- Create: `app/src/debug/AndroidManifest.xml`, `app/src/debug/java/app/backlit/debug/AudioProbeActivity.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: a decision recorded in the ledger. `GO` means continue with Task 2. `NO-GO` means **stop the plan** and report back to the human with the logs (options: microphone source, or the no-permission animation).

- [ ] **Step 1: Debug-only manifest**

`app/src/debug/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <application>
        <activity
            android:name=".debug.AudioProbeActivity"
            android:exported="true" />
    </application>
</manifest>
```

- [ ] **Step 2: Probe activity**

`app/src/debug/java/app/backlit/debug/AudioProbeActivity.kt`:

```kotlin
package app.backlit.debug

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import kotlin.math.hypot

/** Debug-only spike: logs the output-mix Visualizer level every 100 ms for 2 minutes. */
class AudioProbeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var viz: Visualizer? = null
    private var ticks = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "grant RECORD_AUDIO first: adb shell pm grant app.backlit android.permission.RECORD_AUDIO")
            finish(); return
        }
        val v = runCatching {
            Visualizer(0).apply {
                setEnabled(false)
                setCaptureSize(Visualizer.getCaptureSizeRange()[1])
                setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
                setEnabled(true)
            }
        }.onFailure { Log.e(TAG, "Visualizer(0) failed", it) }.getOrNull()
        if (v == null) { finish(); return }
        viz = v
        val buf = ByteArray(v.captureSize)
        val pm = getSystemService(PowerManager::class.java)
        val am = getSystemService(AudioManager::class.java)
        handler.post(object : Runnable {
            override fun run() {
                val rc = v.getFft(buf)
                var sum = 0.0
                for (k in 1 until buf.size / 2) sum += hypot(buf[2 * k].toDouble(), buf[2 * k + 1].toDouble())
                Log.i(TAG, "rc=$rc level=%.2f screenOn=${pm.isInteractive} music=${am.isMusicActive} rate=${v.samplingRate}"
                    .format(sum / (buf.size / 2)))
                if (++ticks < 1200) handler.postDelayed(this, 100) else finish()
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { viz?.release() }
        super.onDestroy()
    }

    private companion object { const val TAG = "BacklitProbe" }
}
```

- [ ] **Step 3: Build and install**

```bash
source .superpowers/env.sh && ./gradlew :app:installDebug && adb shell pm grant app.backlit android.permission.RECORD_AUDIO
```

Expected: `Installed on 1 device.` and no error from `pm grant`. If no device is attached, stop and ask the human to connect the Phone (3) with USB debugging.

- [ ] **Step 4 (human + agent): Measure with the screen on, then off**

The human starts a song in Spotify or YouTube Music at normal volume. The agent runs:

```bash
source .superpowers/env.sh && adb logcat -c && adb shell am start -n app.backlit/.debug.AudioProbeActivity
```

After ~10 s, the human presses the power button to lock the phone and waits ~30 s, then unlocks. The agent then collects the log:

```bash
adb logcat -d -s BacklitProbe:V > /tmp/probe.log; grep -c "screenOn=true" /tmp/probe.log; grep "screenOn=false" /tmp/probe.log | head -5; grep "screenOn=false" /tmp/probe.log | awk -F'level=' '{split($2,a," "); s+=a[1]; n++} END {print "avg level screen-off:", s/n}'
```

Expected for **GO**: `rc=0` lines with `music=true`, and an average level clearly above 0 (for example > 1.0) for both `screenOn=true` and `screenOn=false`. **NO-GO** means the levels are ~0 while `screenOn=false` and `music=true`, or `Visualizer(0) failed`.

- [ ] **Step 5: Record the decision and commit**

Append `Task 1: GO|NO-GO — avg level screen-on X / screen-off Y, device A024` to the ledger. On NO-GO, stop here and report to the human. On GO:

```bash
git add app/src/debug
git commit -m "chore: debug-only Visualizer probe for the music toy spike

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: AudioFrame + SpectrumAnalyzer

**Files:**
- Create: `app/src/main/java/app/backlit/audio/AudioFrame.kt`, `app/src/main/java/app/backlit/audio/SpectrumAnalyzer.kt`
- Test: `app/src/test/java/app/backlit/audio/SpectrumAnalyzerTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `class AudioFrame(val bands: FloatArray, val level: Float, val kick: Float) { companion object { const val BANDS = 8; val SILENT: AudioFrame } }`
  - `class SpectrumAnalyzer { fun analyze(fft: ByteArray, samplingRateHz: Int, dtMs: Long, gain: Float): AudioFrame }`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumAnalyzerTest {

    private val sr = 44100
    private val n = 1024   // bin width ≈ 43 Hz

    private fun bin(k: Int, amp: Int) = ByteArray(n).also { it[2 * k] = amp.toByte() }
    private fun flat(amp: Int) = ByteArray(n).also { for (k in 1 until n / 2) it[2 * k] = amp.toByte() }

    @Test
    fun bassToneFillsBandZero() {
        val f = SpectrumAnalyzer().analyze(bin(1, 100), sr, 50, 1f)   // 43 Hz
        assertEquals(1f, f.bands[0], 1e-4f)
        for (i in 1 until 8) assertEquals("band $i", 0f, f.bands[i], 1e-6f)
    }

    @Test
    fun trebleToneLightsBandSeven() {
        val f = SpectrumAnalyzer().analyze(bin(348, 100), sr, 50, 1f)  // ≈ 15 kHz
        assertTrue(f.bands[7] > 0.3f)
        for (i in 0 until 7) assertEquals("band $i", 0f, f.bands[i], 1e-6f)
    }

    @Test
    fun silenceGivesZeros() {
        val f = SpectrumAnalyzer().analyze(ByteArray(n), sr, 50, 1f)
        assertEquals(0f, f.level, 0f)
        assertTrue(f.bands.all { it == 0f })
        assertEquals(0f, f.kick, 0f)
    }

    @Test
    fun quietAndLoudNormaliseToTheSameShape() {
        val loud = SpectrumAnalyzer(); val quiet = SpectrumAnalyzer()
        var a = AudioFrame.SILENT; var b = AudioFrame.SILENT
        repeat(20) { a = loud.analyze(flat(80), sr, 50, 1f); b = quiet.analyze(flat(20), sr, 50, 1f) }
        for (i in 0 until 8) assertEquals("band $i", a.bands[i], b.bands[i], 0.05f)
    }

    @Test
    fun sensitivityScalesOutput() {
        fun after(gain: Float): Float {
            val s = SpectrumAnalyzer()
            s.analyze(flat(100), sr, 50, gain)
            return s.analyze(flat(50), sr, 50, gain).bands[3]
        }
        assertEquals(0.508f, after(1.0f), 0.02f)
        assertEquals(0.712f, after(1.4f), 0.02f)
        assertEquals(0.356f, after(0.7f), 0.02f)
    }

    @Test
    fun outputIsClampedAtHighSensitivity() {
        val s = SpectrumAnalyzer()
        repeat(10) { val f = s.analyze(flat(127), sr, 50, 1.4f); assertTrue(f.bands.all { it in 0f..1f }); assertTrue(f.level <= 1f) }
    }

    @Test
    fun kickFiresOnBassOnset() {
        val s = SpectrumAnalyzer()
        var f = AudioFrame.SILENT
        repeat(60) { f = s.analyze(bin(1, 10), sr, 50, 1f) }   // 3 s of steady bass: the running average catches up
        assertTrue("steady bass decays kick, was ${f.kick}", f.kick < 0.05f)
        f = s.analyze(bin(1, 80), sr, 50, 1f)
        assertEquals(1f, f.kick, 0f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
.superpowers/runtests.sh --tests "app.backlit.audio.SpectrumAnalyzerTest" 2>&1 | grep -E "^e:" | head -3
```

Expected: `Unresolved reference 'SpectrumAnalyzer'` / `'AudioFrame'`.

- [ ] **Step 3: Implement**

`app/src/main/java/app/backlit/audio/AudioFrame.kt`:

```kotlin
package app.backlit.audio

/** One analysed moment of audio: 8 bands bass→treble, overall level and a kick-drum pulse, all 0..1. */
class AudioFrame(val bands: FloatArray, val level: Float, val kick: Float) {
    init { require(bands.size == BANDS) { "expected $BANDS bands" } }

    companion object {
        const val BANDS = 8
        val SILENT = AudioFrame(FloatArray(BANDS), 0f, 0f)
    }
}
```

`app/src/main/java/app/backlit/audio/SpectrumAnalyzer.kt`:

```kotlin
package app.backlit.audio

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max

/**
 * Visualizer FFT bytes → [AudioFrame].
 * Layout: [Re0, Re(n/2), Re1, Im1, Re2, Im2, …]; bin k sits at k × samplingRate / n Hz.
 * Each band is normalised against its own running peak (rises instantly, decays over ~3 s).
 */
class SpectrumAnalyzer {
    private val peaks = DoubleArray(AudioFrame.BANDS) { PEAK_FLOOR }
    private var bassAvg = 0.0
    private var kick = 0.0

    fun analyze(fft: ByteArray, samplingRateHz: Int, dtMs: Long, gain: Float): AudioFrame {
        val n = fft.size
        if (n < 4 || samplingRateHz <= 0) return AudioFrame.SILENT
        val sums = DoubleArray(AudioFrame.BANDS)
        val counts = IntArray(AudioFrame.BANDS)
        var maxMag = 0.0
        for (k in 1 until n / 2) {
            val mag = hypot(fft[2 * k].toDouble(), fft[2 * k + 1].toDouble())
            if (mag > maxMag) maxMag = mag
            val b = bandOf(k.toDouble() * samplingRateHz / n)
            if (b < 0) continue
            sums[b] += mag
            counts[b]++
        }
        if (maxMag < SILENCE) {
            kick = 0.0
            return AudioFrame.SILENT
        }
        val decay = exp(-dtMs / PEAK_DECAY_MS)
        val bands = FloatArray(AudioFrame.BANDS)
        for (i in 0 until AudioFrame.BANDS) {
            val raw = if (counts[i] == 0) 0.0 else sums[i] / counts[i]
            peaks[i] = max(raw, max(PEAK_FLOOR, peaks[i] * decay))
            bands[i] = (raw / peaks[i] * gain).coerceIn(0.0, 1.0).toFloat()
        }
        val raw0 = if (counts[0] == 0) 0.0 else sums[0] / counts[0]
        kick = if (raw0 > ONSET_RATIO * bassAvg && raw0 > ONSET_MIN) 1.0 else kick * 0.8
        bassAvg += (raw0 - bassAvg) * (dtMs / 1000.0).coerceAtMost(1.0)
        return AudioFrame(bands, bands.average().toFloat(), kick.toFloat())
    }

    private companion object {
        val EDGES = doubleArrayOf(20.0, 60.0, 150.0, 400.0, 1000.0, 2500.0, 6000.0, 12000.0, 20000.0)
        const val PEAK_FLOOR = 1.0
        const val PEAK_DECAY_MS = 3000.0
        const val SILENCE = 2.0
        const val ONSET_RATIO = 1.5
        const val ONSET_MIN = 4.0

        fun bandOf(hz: Double): Int {
            for (i in 0 until AudioFrame.BANDS) if (hz >= EDGES[i] && hz < EDGES[i + 1]) return i
            return -1
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
.superpowers/runtests.sh --tests "app.backlit.audio.SpectrumAnalyzerTest" 2>&1 | tail -1
```

Expected: `7 tests, 0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/audio/AudioFrame.kt app/src/main/java/app/backlit/audio/SpectrumAnalyzer.kt app/src/test/java/app/backlit/audio/SpectrumAnalyzerTest.kt
git commit -m "feat: spectrum analyzer with auto-gain, sensitivity and kick detection

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: MusicState

**Files:**
- Create: `app/src/main/java/app/backlit/audio/MusicState.kt`
- Test: `app/src/test/java/app/backlit/audio/MusicStateTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum class MusicPhase { IDLE, LIVE, FALLBACK }`; `class MusicState { val phase: MusicPhase; fun update(nowMs: Long, musicActive: Boolean, hasVisualizer: Boolean, level: Float): MusicPhase; fun inDecay(nowMs: Long): Boolean; fun shouldRetryVisualizer(nowMs: Long): Boolean }` (constants: silence→fallback 5000 ms, decay 1000 ms, retry 10000 ms).

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicStateTest {

    @Test
    fun startsIdle() = assertEquals(MusicPhase.IDLE, MusicState().phase)

    @Test
    fun musicWithAudioGoesLive() {
        assertEquals(MusicPhase.LIVE, MusicState().update(0, true, true, 0.4f))
    }

    @Test
    fun stoppingMusicGoesIdleWithOneSecondDecay() {
        val s = MusicState()
        s.update(0, true, true, 0.4f)
        assertEquals(MusicPhase.IDLE, s.update(100, false, true, 0f))
        assertTrue(s.inDecay(100))
        assertTrue(s.inDecay(1099))
        assertFalse(s.inDecay(1100))
    }

    @Test
    fun musicWithoutVisualizerIsFallback() {
        assertEquals(MusicPhase.FALLBACK, MusicState().update(0, true, false, 0f))
    }

    @Test
    fun shortSilenceDuringLiveStaysLive() {
        val s = MusicState()
        s.update(0, true, true, 0.5f)
        assertEquals(MusicPhase.LIVE, s.update(1000, true, true, 0f))
        assertEquals(MusicPhase.LIVE, s.update(4000, true, true, 0f))
        assertEquals(MusicPhase.LIVE, s.update(5500, true, true, 0.3f))
    }

    @Test
    fun fiveSecondsOfSilenceWhileMusicPlaysIsFallback() {
        val s = MusicState()
        s.update(0, true, true, 0.5f)
        s.update(1000, true, true, 0f)
        assertEquals(MusicPhase.LIVE, s.update(5999, true, true, 0f))
        assertEquals(MusicPhase.FALLBACK, s.update(6000, true, true, 0f))
    }

    @Test
    fun fallbackRecoversWhenAudioReturns() {
        val s = MusicState()
        s.update(0, true, false, 0f)
        assertEquals(MusicPhase.LIVE, s.update(500, true, true, 0.2f))
    }

    @Test
    fun retriesVisualizerEveryTenSecondsInFallback() {
        val s = MusicState()
        s.update(1000, true, false, 0f)
        assertFalse(s.shouldRetryVisualizer(10_999))
        assertTrue(s.shouldRetryVisualizer(11_000))
        assertFalse(s.shouldRetryVisualizer(11_001))
        assertTrue(s.shouldRetryVisualizer(21_000))
    }

    @Test
    fun noRetryOutsideFallback() {
        val s = MusicState()
        s.update(0, true, true, 0.5f)
        assertFalse(s.shouldRetryVisualizer(60_000))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
.superpowers/runtests.sh --tests "app.backlit.audio.MusicStateTest" 2>&1 | grep -E "^e:" | head -2
```

Expected: `Unresolved reference 'MusicState'`.

- [ ] **Step 3: Implement**

```kotlin
package app.backlit.audio

enum class MusicPhase { IDLE, LIVE, FALLBACK }

/** Decides what the Music toy shows: idle line, live visualizer, or the fallback line. */
class MusicState {
    var phase: MusicPhase = MusicPhase.IDLE
        private set

    private var silentSince = -1L
    private var idleSince = -1L
    private var lastRetryAt = 0L

    fun update(nowMs: Long, musicActive: Boolean, hasVisualizer: Boolean, level: Float): MusicPhase {
        when {
            !musicActive -> {
                if (phase == MusicPhase.LIVE) idleSince = nowMs
                go(MusicPhase.IDLE, nowMs)
                silentSince = -1L
            }
            !hasVisualizer -> go(MusicPhase.FALLBACK, nowMs)
            level > 0f -> {
                silentSince = -1L
                go(MusicPhase.LIVE, nowMs)
            }
            else -> {
                if (silentSince < 0) silentSince = nowMs
                if (nowMs - silentSince >= SILENCE_TO_FALLBACK_MS) go(MusicPhase.FALLBACK, nowMs)
            }
        }
        return phase
    }

    /** True for 1 s after music stops, so the style can let its bars fall before the idle line. */
    fun inDecay(nowMs: Long): Boolean =
        phase == MusicPhase.IDLE && idleSince >= 0 && nowMs - idleSince < DECAY_MS

    fun shouldRetryVisualizer(nowMs: Long): Boolean {
        if (phase != MusicPhase.FALLBACK || nowMs - lastRetryAt < RETRY_MS) return false
        lastRetryAt = nowMs
        return true
    }

    private fun go(next: MusicPhase, nowMs: Long) {
        if (next == MusicPhase.FALLBACK && phase != MusicPhase.FALLBACK) lastRetryAt = nowMs
        phase = next
    }

    private companion object {
        const val SILENCE_TO_FALLBACK_MS = 5_000L
        const val DECAY_MS = 1_000L
        const val RETRY_MS = 10_000L
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
.superpowers/runtests.sh --tests "app.backlit.audio.MusicStateTest" 2>&1 | tail -1
```

Expected: `9 tests, 0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/audio/MusicState.kt app/src/test/java/app/backlit/audio/MusicStateTest.kt
git commit -m "feat: music state machine (idle, live, fallback, decay, retry)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Column model, MirrorBars, IdleLine, registry

**Files:**
- Create: `app/src/main/java/app/backlit/render/viz/VizStyle.kt`, `app/src/main/java/app/backlit/render/viz/MirrorBars.kt`, `app/src/main/java/app/backlit/render/viz/IdleLine.kt`, `app/src/main/java/app/backlit/render/viz/VizStyles.kt`
- Test: `app/src/test/java/app/backlit/render/viz/MirrorBarsTest.kt`, `app/src/test/java/app/backlit/render/viz/IdleLineTest.kt`, `app/src/test/java/app/backlit/render/viz/VizStylesTest.kt`

**Interfaces:**
- Consumes: `PixelGrid`, `px()` (Part 1); `AudioFrame` (Task 2).
- Produces:
  - `interface VizStyle { val id: String; fun update(frame: AudioFrame, dtMs: Long); fun render(): PixelGrid }`
  - `class ColumnLevels(size: Int) { val levels: FloatArray; fun update(frame: AudioFrame); fun height(x: Int): Int }`
  - `class MirrorBars(size: Int) : VizStyle` (id `mirror`)
  - `class IdleLine(size: Int) { fun update(dtMs: Long, fallback: Boolean); fun brightness(): Int; fun render(): PixelGrid }`
  - `object VizStyles { val ids: List<String>; fun normalize(id: String): String; fun label(id: String): String; fun next(id: String): String; fun create(id: String, size: Int): VizStyle }`. In this task, `create` maps `peaks` and `wave` to `MirrorBars` too. Task 5 replaces them.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/backlit/render/viz/MirrorBarsTest.kt`:

```kotlin
package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorBarsTest {

    private fun bass(v: Float = 1f) = AudioFrame(FloatArray(8).also { it[0] = v }, v / 8, 0f)

    @Test
    fun zeroFrameLightsOnlyTheCentreRow() {
        val s = MirrorBars(25)
        s.update(AudioFrame.SILENT, 50)
        val g = s.render()
        assertEquals(25, g.litCount())
        for (x in 0 until 25) assertEquals(140, g[x, 12])
    }

    @Test
    fun symmetricAboutTheCentreRow() {
        val s = MirrorBars(25)
        s.update(AudioFrame(FloatArray(8) { 0.2f + it * 0.1f }, 0.5f, 0f), 50)
        val g = s.render()
        for (x in 0 until 25) for (k in 1..12) assertEquals("x=$x k=$k", g[x, 12 - k], g[x, 12 + k])
    }

    @Test
    fun bassMakesTheCentreColumnTallest() {
        val s = MirrorBars(25)
        s.update(bass(), 50)
        val cols = ColumnLevels(25).also { it.update(bass()) }
        assertEquals(12, cols.height(12))
        for (x in 0 until 25) assertTrue(cols.height(x) <= cols.height(12))
        val g = s.render()
        assertEquals(255, g[12, 0])
        assertEquals(255, g[12, 24])
    }

    @Test
    fun barsFallGraduallyNotInstantly() {
        val c = ColumnLevels(25)
        c.update(bass())
        c.update(AudioFrame.SILENT)
        assertEquals(0.7f, c.levels[12], 1e-4f)
    }
}
```

`app/src/test/java/app/backlit/render/viz/IdleLineTest.kt`:

```kotlin
package app.backlit.render.viz

import org.junit.Assert.assertEquals
import org.junit.Test

class IdleLineTest {

    @Test
    fun onlyTheCentreRowIsLit() {
        val g = IdleLine(25).render()
        assertEquals(25, g.litCount())
        for (x in 0 until 25) assertEquals(100, g[x, 12])
    }

    @Test
    fun breathesOverFourSeconds() {
        val l = IdleLine(25)
        assertEquals(100, l.brightness())
        l.update(1000, fallback = false)
        assertEquals(140, l.brightness())
        l.update(2000, fallback = false)
        assertEquals(60, l.brightness())
    }

    @Test
    fun fallbackIsFasterAndBrighter() {
        val l = IdleLine(25)
        l.update(500, fallback = true)
        assertEquals(180, l.brightness())
    }
}
```

`app/src/test/java/app/backlit/render/viz/VizStylesTest.kt`:

```kotlin
package app.backlit.render.viz

import org.junit.Assert.assertEquals
import org.junit.Test

class VizStylesTest {
    @Test
    fun idsLabelsAndCycling() {
        assertEquals(listOf("mirror", "peaks", "wave"), VizStyles.ids)
        assertEquals("mirror", VizStyles.normalize("nonsense"))
        assertEquals("PEAKS", VizStyles.label("peaks"))
        assertEquals("peaks", VizStyles.next("mirror"))
        assertEquals("mirror", VizStyles.next("wave"))
        assertEquals("mirror", VizStyles.create("unknown", 25).id)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
.superpowers/runtests.sh --tests "app.backlit.render.viz.*" 2>&1 | grep -E "^e:" | head -3
```

Expected: unresolved `MirrorBars`, `ColumnLevels`, `IdleLine`, `VizStyles`.

- [ ] **Step 3: Implement**

`app/src/main/java/app/backlit/render/viz/VizStyle.kt`:

```kotlin
package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

/** A stateful music visualizer drawn on a size×size grid. */
interface VizStyle {
    val id: String
    fun update(frame: AudioFrame, dtMs: Long)
    fun render(): PixelGrid
}

/**
 * Per-column level 0..1: centre column = band 0 (bass), edges = band 7 (treble), mirrored,
 * tapering towards the edges. Rises instantly, falls as old × 0.7 + new × 0.3.
 */
class ColumnLevels(private val size: Int) {
    val levels = FloatArray(size)

    fun update(frame: AudioFrame) {
        val c = (size - 1) / 2.0
        val last = AudioFrame.BANDS - 1
        for (x in 0 until size) {
            val d = if (c == 0.0) 0.0 else abs(x - c) / c
            val f = d * last
            val i = floor(f).toInt().coerceIn(0, last)
            val fr = f - i
            val j = min(last, i + 1)
            val v = ((frame.bands[i] * (1 - fr) + frame.bands[j] * fr) * (1 - 0.45 * d)).toFloat()
            levels[x] = if (v > levels[x]) v else levels[x] * 0.7f + v * 0.3f
        }
    }

    /** Bar half-height in pixels: 0..(size/2 − 0.5), e.g. 0..12 on 25×25. */
    fun height(x: Int): Int = (levels[x] * (size / 2.0 - 0.5)).px()
}
```

`app/src/main/java/app/backlit/render/viz/MirrorBars.kt`:

```kotlin
package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import app.backlit.render.PixelGrid

/** Style E: one column per LED, mirrored up and down from the centre row. */
class MirrorBars(private val size: Int) : VizStyle {
    override val id = "mirror"
    private val cols = ColumnLevels(size)

    override fun update(frame: AudioFrame, dtMs: Long) = cols.update(frame)

    override fun render(): PixelGrid {
        val g = PixelGrid(size)
        val c = (size - 1) / 2
        for (x in 0 until size) {
            g.plot(x, c, CENTRE)
            for (k in 1..cols.height(x)) {
                g.plot(x, c - k, BAR)
                g.plot(x, c + k, BAR)
            }
        }
        return g
    }

    private companion object {
        const val CENTRE = 140
        const val BAR = 255
    }
}
```

`app/src/main/java/app/backlit/render/viz/IdleLine.kt`:

```kotlin
package app.backlit.render.viz

import app.backlit.render.PixelGrid
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Breathing centre row: 60–140 over 4 s when idle, 80–180 over 2 s in fallback. */
class IdleLine(private val size: Int) {
    private var t = 0L
    private var fallback = false

    fun update(dtMs: Long, fallback: Boolean) {
        t += dtMs
        this.fallback = fallback
    }

    fun brightness(): Int {
        val lo = if (fallback) 80 else 60
        val hi = if (fallback) 180 else 140
        val period = if (fallback) 2_000L else 4_000L
        val s = sin(2 * PI * (t % period) / period)
        return (lo + (hi - lo) * (0.5 + 0.5 * s)).roundToInt()
    }

    fun render(): PixelGrid {
        val g = PixelGrid(size)
        val c = (size - 1) / 2
        val b = brightness()
        for (x in 0 until size) g.plot(x, c, b)
        return g
    }
}
```

`app/src/main/java/app/backlit/render/viz/VizStyles.kt`:

```kotlin
package app.backlit.render.viz

object VizStyles {
    val ids = listOf("mirror", "peaks", "wave")
    private val labels = mapOf("mirror" to "MIRROR", "peaks" to "PEAKS", "wave" to "WAVE")

    fun normalize(id: String): String = if (id in ids) id else ids.first()

    fun label(id: String): String = labels.getValue(normalize(id))

    fun next(id: String): String = ids[(ids.indexOf(normalize(id)) + 1) % ids.size]

    fun create(id: String, size: Int): VizStyle = when (normalize(id)) {
        else -> MirrorBars(size)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
.superpowers/runtests.sh --tests "app.backlit.render.viz.*" 2>&1 | tail -1
```

Expected: `8 tests, 0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/render/viz/ app/src/test/java/app/backlit/render/viz/
git commit -m "feat: mirror-bars visualizer, breathing idle line and style registry

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: MirrorPeaks and ScrollWave

**Files:**
- Create: `app/src/main/java/app/backlit/render/viz/MirrorPeaks.kt`, `app/src/main/java/app/backlit/render/viz/ScrollWave.kt`
- Modify: `app/src/main/java/app/backlit/render/viz/VizStyles.kt` (`create`)
- Test: `app/src/test/java/app/backlit/render/viz/MirrorPeaksTest.kt`, `app/src/test/java/app/backlit/render/viz/ScrollWaveTest.kt`, and a new line in `VizStylesTest`

**Interfaces:**
- Consumes: `ColumnLevels`, `VizStyle` (Task 4); `AudioFrame` (Task 2); `PixelGrid`.
- Produces: `class MirrorPeaks(size: Int) : VizStyle` (id `peaks`, `internal fun peakHeight(x: Int): Int`); `class ScrollWave(size: Int) : VizStyle` (id `wave`, `internal fun samplesNewestFirst(): List<Float>`). `VizStyles.create` returns all three classes.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/backlit/render/viz/MirrorPeaksTest.kt`:

```kotlin
package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorPeaksTest {

    private val bass = AudioFrame(FloatArray(8).also { it[0] = 1f }, 0.125f, 0f)

    @Test
    fun peakDotSitsJustBeyondTheBar() {
        val s = MirrorPeaks(25)
        s.update(AudioFrame(FloatArray(8).also { it[0] = 0.5f }, 0.06f, 0f), 50)   // centre bar h = 6
        val g = s.render()
        assertEquals(6, s.peakHeight(12))
        assertEquals(255, g[12, 12 - 7])
        assertEquals(255, g[12, 12 + 7])
        assertEquals(200, g[12, 12 - 6])     // bar tip
    }

    @Test
    fun peakHoldsThenFallsOnePixelPer200ms() {
        val s = MirrorPeaks(25)
        s.update(bass, 50)
        assertEquals(12, s.peakHeight(12))
        repeat(4) { s.update(AudioFrame.SILENT, 50) }   // t = 200 ms
        assertEquals(12, s.peakHeight(12))
        s.update(AudioFrame.SILENT, 50)                  // t = 250 ms
        assertEquals(11, s.peakHeight(12))
        repeat(4) { s.update(AudioFrame.SILENT, 50) }   // t = 450 ms
        assertEquals(10, s.peakHeight(12))
    }
}
```

`app/src/test/java/app/backlit/render/viz/ScrollWaveTest.kt`:

```kotlin
package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollWaveTest {

    private fun lv(v: Float) = AudioFrame(FloatArray(8), v, 0f)

    @Test
    fun newSamplesEnterOnTheRightEvery70ms() {
        val s = ScrollWave(25)
        s.update(lv(0.5f), 70)
        s.update(lv(1.0f), 70)
        assertEquals(listOf(1.0f, 0.5f), s.samplesNewestFirst())
        s.update(lv(0f), 140)
        assertEquals(listOf(0f, 0f, 1.0f, 0.5f), s.samplesNewestFirst())
        s.update(lv(0.3f), 30)
        assertEquals(4, s.samplesNewestFirst().size)
    }

    @Test
    fun historyIsCappedAtGridWidth() {
        val s = ScrollWave(25)
        repeat(40) { s.update(lv(0.2f), 70) }
        assertEquals(25, s.samplesNewestFirst().size)
    }

    @Test
    fun newestColumnIsBrightest() {
        val s = ScrollWave(25)
        s.update(lv(0.5f), 70)
        s.update(lv(1.0f), 70)
        val g = s.render()
        assertEquals(255, g[24, 12])
        assertTrue(g[23, 12] in 1..254)
        assertEquals(0, g[22, 12])
    }
}
```

Add to `VizStylesTest.idsLabelsAndCycling` (before the closing brace):

```kotlin
        assertEquals("peaks", VizStyles.create("peaks", 25).id)
        assertEquals("wave", VizStyles.create("wave", 25).id)
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
.superpowers/runtests.sh --tests "app.backlit.render.viz.*" 2>&1 | grep -E "^e:|FAILED" | head -4
```

Expected: unresolved `MirrorPeaks` / `ScrollWave`.

- [ ] **Step 3: Implement**

`app/src/main/java/app/backlit/render/viz/MirrorPeaks.kt`:

```kotlin
package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import app.backlit.render.PixelGrid
import kotlin.math.max
import kotlin.math.roundToInt

/** Style F: mirror bars with peak dots that hold 250 ms, then fall one pixel per 200 ms. */
class MirrorPeaks(private val size: Int) : VizStyle {
    override val id = "peaks"
    private val cols = ColumnLevels(size)
    private val peak = IntArray(size)
    private val timer = LongArray(size)

    override fun update(frame: AudioFrame, dtMs: Long) {
        cols.update(frame)
        for (x in 0 until size) {
            val h = cols.height(x)
            if (h >= peak[x]) {
                peak[x] = h
                timer[x] = 0
            } else {
                timer[x] += dtMs
                if (timer[x] >= HOLD_MS) {
                    peak[x] = max(h, peak[x] - 1)
                    timer[x] = HOLD_MS - FALL_MS
                }
            }
        }
    }

    internal fun peakHeight(x: Int): Int = peak[x]

    override fun render(): PixelGrid {
        val g = PixelGrid(size)
        val c = (size - 1) / 2
        for (x in 0 until size) {
            val h = cols.height(x)
            g.plot(x, c, BAR_LOW)
            for (k in 1..h) {
                val b = (BAR_LOW + (BAR_HIGH - BAR_LOW) * k / h.toDouble()).roundToInt()
                g.plot(x, c - k, b)
                g.plot(x, c + k, b)
            }
            if (peak[x] > 0) {
                g.plot(x, c - peak[x] - 1, PEAK)
                g.plot(x, c + peak[x] + 1, PEAK)
            }
        }
        return g
    }

    private companion object {
        const val HOLD_MS = 250L
        const val FALL_MS = 200L
        const val BAR_LOW = 90
        const val BAR_HIGH = 200
        const val PEAK = 255
    }
}
```

`app/src/main/java/app/backlit/render/viz/ScrollWave.kt`:

```kotlin
package app.backlit.render.viz

import app.backlit.audio.AudioFrame
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.roundToInt

/** Style G: loudness history scrolling right → left, one sample every 70 ms, fading with age. */
class ScrollWave(private val size: Int) : VizStyle {
    override val id = "wave"
    private val samples = ArrayDeque<Float>()   // newest first
    private var acc = 0L

    override fun update(frame: AudioFrame, dtMs: Long) {
        acc += dtMs
        while (acc >= STEP_MS) {
            acc -= STEP_MS
            samples.addFirst(frame.level)
            if (samples.size > size) samples.removeLast()
        }
    }

    internal fun samplesNewestFirst(): List<Float> = samples.toList()

    override fun render(): PixelGrid {
        val g = PixelGrid(size)
        val c = (size - 1) / 2
        samples.forEachIndexed { i, s ->
            val x = size - 1 - i
            val b = (NEWEST - (NEWEST - OLDEST) * i / (size - 1).toDouble()).roundToInt()
            val h = (s * (size / 2.0 - 0.5)).px()
            g.plot(x, c, b)
            for (k in 1..h) {
                g.plot(x, c - k, b)
                g.plot(x, c + k, b)
            }
        }
        return g
    }

    private companion object {
        const val STEP_MS = 70L
        const val NEWEST = 255
        const val OLDEST = 90
    }
}
```

In `VizStyles.kt`, replace:

```kotlin
    fun create(id: String, size: Int): VizStyle = when (normalize(id)) {
        else -> MirrorBars(size)
    }
```

with:

```kotlin
    fun create(id: String, size: Int): VizStyle = when (normalize(id)) {
        "peaks" -> MirrorPeaks(size)
        "wave" -> ScrollWave(size)
        else -> MirrorBars(size)
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
.superpowers/runtests.sh --tests "app.backlit.render.viz.*" 2>&1 | tail -1
```

Expected: `13 tests, 0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/render/viz/ app/src/test/java/app/backlit/render/viz/
git commit -m "feat: mirror-peaks and scrolling-waveform visualizer styles

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: DemoAudio + MusicEngine

**Files:**
- Create: `app/src/main/java/app/backlit/audio/DemoAudio.kt`, `app/src/main/java/app/backlit/audio/MusicEngine.kt`
- Test: `app/src/test/java/app/backlit/audio/DemoAudioTest.kt`, `app/src/test/java/app/backlit/audio/MusicEngineTest.kt`

**Interfaces:**
- Consumes: `SpectrumAnalyzer`, `AudioFrame` (Task 2); `MusicState`, `MusicPhase` (Task 3); `VizStyles`, `VizStyle`, `IdleLine` (Tasks 4–5); `PixelGrid`.
- Produces:
  - `object DemoAudio { fun frame(tMs: Long): AudioFrame }`
  - `class MusicEngine(size: Int = 25) { val styleId: String; val phase: MusicPhase; fun setStyle(id: String); fun tick(nowMs: Long, dtMs: Long, musicActive: Boolean, fft: ByteArray?, samplingRateHz: Int, gain: Float, hasVisualizer: Boolean): PixelGrid; fun tickDemo(dtMs: Long, frame: AudioFrame): PixelGrid; fun shouldRetryVisualizer(nowMs: Long): Boolean }`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/backlit/audio/DemoAudioTest.kt`:

```kotlin
package app.backlit.audio

import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAudioTest {
    @Test
    fun beatsAndStaysInRange() {
        assertTrue(DemoAudio.frame(0).kick > 0.99f)
        assertTrue(DemoAudio.frame(400).kick < 0.1f)
        var t = 0L
        while (t < 10_000) {
            val f = DemoAudio.frame(t)
            assertTrue(f.bands.all { it in 0f..1f })
            assertTrue(f.level > 0f)
            t += 37
        }
    }
}
```

`app/src/test/java/app/backlit/audio/MusicEngineTest.kt`:

```kotlin
package app.backlit.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicEngineTest {

    private val sr = 44100
    private fun bassFft() = ByteArray(1024).also { it[2] = 100 }   // bin 1 ≈ 43 Hz

    private fun onlyCentreRow(g: app.backlit.render.PixelGrid): Boolean =
        g.litCount() == 25 && (0 until 25).all { g[it, 12] > 0 }

    @Test
    fun noMusicShowsTheIdleLine() {
        val e = MusicEngine()
        val g = e.tick(50, 50, musicActive = false, fft = null, samplingRateHz = sr, gain = 1f, hasVisualizer = false)
        assertEquals(MusicPhase.IDLE, e.phase)
        assertTrue(onlyCentreRow(g))
    }

    @Test
    fun musicWithAudioDrawsBars() {
        val e = MusicEngine()
        val g = e.tick(50, 50, true, bassFft(), sr, 1f, true)
        assertEquals(MusicPhase.LIVE, e.phase)
        assertTrue(g[12, 11] > 0)
        assertTrue(g[12, 1] > 0)
    }

    @Test
    fun musicWithoutVisualizerShowsFallbackLine() {
        val e = MusicEngine()
        val g = e.tick(50, 50, true, null, sr, 1f, false)
        assertEquals(MusicPhase.FALLBACK, e.phase)
        assertTrue(onlyCentreRow(g))
    }

    @Test
    fun missingFftWhileMusicPlaysFallsBack() {
        val e = MusicEngine()
        e.tick(0, 50, true, bassFft(), sr, 1f, true)
        var t = 0L
        while (t < 5_100) { t += 50; e.tick(t, 50, true, null, sr, 1f, true) }   // silence began at t = 50
        assertEquals(MusicPhase.FALLBACK, e.phase)
    }

    @Test
    fun pauseDecaysThenShowsIdleLine() {
        val e = MusicEngine()
        e.tick(0, 50, true, bassFft(), sr, 1f, true)
        val falling = e.tick(50, 50, false, null, sr, 1f, true)
        assertTrue("bars still visible while decaying", falling[12, 11] > 0)
        var g = falling
        var t = 50L
        while (t < 1_200) { t += 50; g = e.tick(t, 50, false, null, sr, 1f, true) }
        assertTrue(onlyCentreRow(g))
    }

    @Test
    fun switchingStyleMidSongKeepsRendering() {
        val e = MusicEngine()
        e.tick(0, 50, true, bassFft(), sr, 1f, true)
        for (id in listOf("peaks", "wave", "mirror", "peaks")) {
            e.setStyle(id)
            assertEquals(id, e.styleId)
            val g = e.tick(100, 70, true, bassFft(), sr, 1f, true)
            assertTrue(g.litCount() > 0)
        }
    }

    @Test
    fun demoTickRendersTheCurrentStyle() {
        val e = MusicEngine()
        val g = e.tickDemo(50, DemoAudio.frame(0))
        assertTrue(g[12, 11] > 0)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
.superpowers/runtests.sh --tests "app.backlit.audio.DemoAudioTest" --tests "app.backlit.audio.MusicEngineTest" 2>&1 | grep -E "^e:" | head -3
```

Expected: unresolved `DemoAudio` / `MusicEngine`.

- [ ] **Step 3: Implement**

`app/src/main/java/app/backlit/audio/DemoAudio.kt`:

```kotlin
package app.backlit.audio

import kotlin.math.exp
import kotlin.math.sin

/** Deterministic fake music (120 BPM kick, snare on the off-beat bar, wobbling mids/highs). */
object DemoAudio {
    fun frame(tMs: Long): AudioFrame {
        val kick = exp(-((tMs % 500) / 500.0) * 7)
        val snare = if ((tMs / 500) % 2 == 1L) exp(-((tMs % 500) / 500.0) * 9) else 0.1
        fun wob(period: Double, shift: Double) = 0.5 + 0.5 * sin(tMs / period + shift)
        val raw = doubleArrayOf(
            kick, kick * 0.85, 0.3 + 0.3 * wob(700.0, 0.0), 0.3 + 0.5 * snare,
            0.25 + 0.35 * wob(430.0, 1.0), 0.15 + 0.6 * snare, 0.2 + 0.3 * wob(170.0, 2.0), 0.15 + 0.3 * wob(110.0, 3.0),
        )
        val bands = FloatArray(AudioFrame.BANDS) { raw[it].coerceIn(0.0, 1.0).toFloat() }
        return AudioFrame(bands, bands.average().toFloat(), kick.toFloat())
    }
}
```

`app/src/main/java/app/backlit/audio/MusicEngine.kt`:

```kotlin
package app.backlit.audio

import app.backlit.render.PixelGrid
import app.backlit.render.viz.IdleLine
import app.backlit.render.viz.VizStyle
import app.backlit.render.viz.VizStyles

/** Pure glue shared by the toy and the in-app preview: FFT → analysis → state → style → frame. */
class MusicEngine(private val size: Int = 25) {
    private val analyzer = SpectrumAnalyzer()
    private val state = MusicState()
    private val idle = IdleLine(size)
    private var style: VizStyle = VizStyles.create(VizStyles.ids.first(), size)

    val styleId: String get() = style.id
    val phase: MusicPhase get() = state.phase

    fun setStyle(id: String) {
        val wanted = VizStyles.normalize(id)
        if (wanted != style.id) style = VizStyles.create(wanted, size)
    }

    fun tick(
        nowMs: Long,
        dtMs: Long,
        musicActive: Boolean,
        fft: ByteArray?,
        samplingRateHz: Int,
        gain: Float,
        hasVisualizer: Boolean,
    ): PixelGrid {
        val frame = if (fft != null) analyzer.analyze(fft, samplingRateHz, dtMs, gain) else AudioFrame.SILENT
        val phase = state.update(nowMs, musicActive, hasVisualizer, frame.level)
        return when {
            phase == MusicPhase.LIVE -> { style.update(frame, dtMs); style.render() }
            state.inDecay(nowMs) -> { style.update(AudioFrame.SILENT, dtMs); style.render() }
            else -> { idle.update(dtMs, fallback = phase == MusicPhase.FALLBACK); idle.render() }
        }
    }

    fun tickDemo(dtMs: Long, frame: AudioFrame): PixelGrid {
        style.update(frame, dtMs)
        return style.render()
    }

    fun shouldRetryVisualizer(nowMs: Long): Boolean = state.shouldRetryVisualizer(nowMs)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
.superpowers/runtests.sh --tests "app.backlit.audio.*" 2>&1 | tail -1
```

Expected: `24 tests, 0 failures, 0 errors` (7 analyzer + 9 state + 1 demo + 7 engine).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/audio/DemoAudio.kt app/src/main/java/app/backlit/audio/MusicEngine.kt app/src/test/java/app/backlit/audio/DemoAudioTest.kt app/src/test/java/app/backlit/audio/MusicEngineTest.kt
git commit -m "feat: music engine tying analysis, state and styles; demo audio

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Music settings

**Files:**
- Modify: `app/src/main/java/app/backlit/data/Settings.kt`, `app/src/main/java/app/backlit/data/SettingsRepo.kt`, `app/src/test/java/app/backlit/data/SettingsRepoTest.kt`

**Interfaces:**
- Consumes: Part 1 `Settings`, `SettingsRepo`.
- Produces: `enum class Sensitivity(val gain: Float) { LOW(0.7f), MED(1.0f), HIGH(1.4f) }`; `Settings.musicStyle: String = "mirror"`, `Settings.musicSensitivity: Sensitivity = Sensitivity.MED`; `SettingsRepo.parseSensitivity(s: String?): Sensitivity`.

- [ ] **Step 1: Extend the tests (failing)**

In `SettingsRepoTest.roundTripsEveryField`, replace:

```kotlin
            locationUpdatedAt = 123L, toyEverBound = true,
        )
```

with:

```kotlin
            locationUpdatedAt = 123L, toyEverBound = true,
            musicStyle = "wave", musicSensitivity = Sensitivity.HIGH,
        )
```

and add this test before the class's closing brace:

```kotlin
    @Test
    fun unknownSensitivityFallsBackToMed() {
        assertEquals(Sensitivity.MED, SettingsRepo.parseSensitivity("LOUD"))
        assertEquals(Sensitivity.LOW, SettingsRepo.parseSensitivity("LOW"))
        assertEquals("mirror", Settings().musicStyle)
    }
```

```bash
.superpowers/runtests.sh --tests "app.backlit.data.SettingsRepoTest" 2>&1 | grep -E "^e:" | head -2
```

Expected: unresolved `musicStyle` / `Sensitivity`.

- [ ] **Step 2: Implement**

In `Settings.kt`, replace:

```kotlin
    val toyEverBound: Boolean = false,
) {
```

with:

```kotlin
    val toyEverBound: Boolean = false,
    val musicStyle: String = "mirror",
    val musicSensitivity: Sensitivity = Sensitivity.MED,
) {
```

and add below `enum class LocationMode { FIXED, APPROXIMATE, CITY }`:

```kotlin

enum class Sensitivity(val gain: Float) { LOW(0.7f), MED(1.0f), HIGH(1.4f) }
```

In `SettingsRepo.kt`:
- after `private val BOUND = booleanPreferencesKey("toy_ever_bound")` add:

```kotlin
        private val MUSIC_STYLE = stringPreferencesKey("music_style")
        private val MUSIC_SENS = stringPreferencesKey("music_sensitivity")
```

- after the `parseMode` function add:

```kotlin

        fun parseSensitivity(s: String?): Sensitivity =
            Sensitivity.entries.firstOrNull { it.name == s } ?: Sensitivity.MED
```

- in `toSettings()`, replace `toyEverBound = this[BOUND] ?: d.toyEverBound,` with:

```kotlin
                toyEverBound = this[BOUND] ?: d.toyEverBound,
                musicStyle = this[MUSIC_STYLE] ?: d.musicStyle,
                musicSensitivity = parseSensitivity(this[MUSIC_SENS]),
```

- in `write()`, replace `this[BOUND] = s.toyEverBound` with:

```kotlin
            this[BOUND] = s.toyEverBound
            this[MUSIC_STYLE] = s.musicStyle
            this[MUSIC_SENS] = s.musicSensitivity.name
```

- [ ] **Step 3: Run tests**

```bash
.superpowers/runtests.sh 2>&1 | tail -1
```

Expected: all tests pass (Part 1's 65 + the Part 2 tests so far + 1).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/backlit/data/ app/src/test/java/app/backlit/data/SettingsRepoTest.kt
git commit -m "feat: persist music style and sensitivity

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Android audio wrappers + MusicToyService

**Files:**
- Create: `app/src/main/java/app/backlit/audio/OutputVisualizer.kt`, `app/src/main/java/app/backlit/audio/MusicActivity.kt`, `app/src/main/java/app/backlit/audio/AudioPermission.kt`, `app/src/main/java/app/backlit/glyph/MusicToyService.kt`, `app/src/main/res/drawable/ic_music_preview.xml`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `MusicEngine` (Task 6); `Settings.musicStyle`/`musicSensitivity` (Task 7); `VizStyles.next` (Task 4); Part 1 `GlyphOutput`, `FrameEncoder`, `DeviceProfile`, `SettingsRepo`, `PixelGrid`.
- Produces:
  - `class OutputVisualizer { val isActive: Boolean; val samplingRateHz: Int; fun start(): Boolean; fun readFft(): ByteArray?; fun release() }`
  - `class MusicActivity(context: Context) { fun isPlaying(): Boolean }`
  - `fun hasAudioPermission(context: Context): Boolean`
  - The `MusicToyService` Glyph Toy.

This task can't be unit-tested (Android audio APIs and the SDK). It's verified by building and running on the phone.

- [ ] **Step 1: Audio wrappers**

`app/src/main/java/app/backlit/audio/AudioPermission.kt`:

```kotlin
package app.backlit.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

fun hasAudioPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
```

`app/src/main/java/app/backlit/audio/MusicActivity.kt`:

```kotlin
package app.backlit.audio

import android.content.Context
import android.media.AudioManager

/** Whether any app is playing music. Needs no permission. */
class MusicActivity(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun isPlaying(): Boolean = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
}
```

`app/src/main/java/app/backlit/audio/OutputVisualizer.kt`:

```kotlin
package app.backlit.audio

import android.media.audiofx.Visualizer
import android.util.Log

/** The system output mix (session 0) as FFT bytes. Polled; never throws. */
class OutputVisualizer {
    private var viz: Visualizer? = null
    private var buffer = ByteArray(0)

    var samplingRateHz: Int = 44_100
        private set

    val isActive: Boolean get() = viz != null

    fun start(): Boolean = runCatching {
        val v = Visualizer(0)
        v.setEnabled(false)
        v.setCaptureSize(Visualizer.getCaptureSizeRange()[1])
        v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
        v.setEnabled(true)
        samplingRateHz = v.samplingRate / 1000
        buffer = ByteArray(v.captureSize)
        viz = v
    }.onFailure {
        Log.w(TAG, "Visualizer(0) unavailable", it)
        release()
    }.isSuccess

    fun readFft(): ByteArray? {
        val v = viz ?: return null
        val rc = runCatching { v.getFft(buffer) }.getOrDefault(Visualizer.ERROR)
        return if (rc == Visualizer.SUCCESS) buffer else null
    }

    fun release() {
        runCatching { viz?.setEnabled(false) }
        runCatching { viz?.release() }
        viz = null
    }

    private companion object { const val TAG = "BacklitAudio" }
}
```

- [ ] **Step 2: MusicToyService**

`app/src/main/java/app/backlit/glyph/MusicToyService.kt`:

```kotlin
package app.backlit.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import app.backlit.audio.MusicActivity
import app.backlit.audio.MusicEngine
import app.backlit.audio.OutputVisualizer
import app.backlit.audio.hasAudioPermission
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.render.PixelGrid
import app.backlit.render.viz.VizStyles
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicToyService : Service() {

    private var scope: CoroutineScope? = null
    private var loopJob: Job? = null
    private var output: GlyphOutput? = null
    private val engine = MusicEngine(SIZE)
    private val visualizer = OutputVisualizer()
    private lateinit var music: MusicActivity
    private lateinit var repo: SettingsRepo
    private var settings = Settings()
    private var frames = 0
    private var frameNanos = 0L

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            if (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA) == GlyphToy.EVENT_CHANGE) {
                scope?.launch { repo.update { it.copy(musicStyle = VizStyles.next(it.musicStyle)) } }
            }
        }
    }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder {
        val profile = DeviceProfile.detect()
        repo = SettingsRepo.get(this)
        music = MusicActivity(this)
        if (profile != DeviceProfile.PHONE_3) return messenger.binder

        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "music toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        output = GlyphOutput(this, profile) { startLoop() }.also { it.connect() }
        s.launch { repo.update { if (it.toyEverBound) it else it.copy(toyEverBound = true) } }
        s.launch {
            repo.settings.collect {
                settings = it
                engine.setStyle(it.musicStyle)
            }
        }
        if (hasAudioPermission(this)) visualizer.start()
        return messenger.binder
    }

    private fun startLoop() {
        val s = scope ?: return
        loopJob?.cancel()
        loopJob = s.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(FRAME_MS)
                val now = SystemClock.elapsedRealtime()
                val dt = now - last
                last = now
                if (engine.shouldRetryVisualizer(now) && hasAudioPermission(this@MusicToyService)) {
                    visualizer.release()
                    visualizer.start()
                }
                val t0 = SystemClock.elapsedRealtimeNanos()
                val grid = runCatching {
                    engine.tick(
                        now, dt, music.isPlaying(), visualizer.readFft(), visualizer.samplingRateHz,
                        settings.musicSensitivity.gain, visualizer.isActive,
                    )
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(SIZE) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = false))
                frameNanos += SystemClock.elapsedRealtimeNanos() - t0
                if (++frames == 200) {
                    Log.d(TAG, "avg frame %.2f ms, phase=%s".format(frameNanos / 200 / 1e6, engine.phase))
                    frames = 0
                    frameNanos = 0
                }
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        loopJob?.cancel()
        scope?.cancel()
        scope = null
        visualizer.release()
        output?.close()
        output = null
        return false
    }

    private companion object {
        const val TAG = "BacklitMusic"
        const val SIZE = 25
        const val FRAME_MS = 50L
    }
}
```

- [ ] **Step 3: Preview drawable, strings, manifest**

`app/src/main/res/drawable/ic_music_preview.xml`:

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
        android:pathData="M48,6a42,42 0,1 1,0 84a42,42 0,1 1,0 -84" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF"
        android:strokeLineCap="round"
        android:strokeWidth="6"
        android:pathData="M24,42L24,54M34,34L34,62M44,24L44,72M54,30L54,66M64,38L64,58M74,44L74,52" />
</vector>
```

In `strings.xml`, add before `</resources>`:

```xml
    <string name="music_toy_name">Backlit Music</string>
    <string name="music_toy_summary">Reacts to the music playing on your phone. Long press to change style.</string>
```

In `AndroidManifest.xml`:
- after `<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />` add:

```xml
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

- replace the final `        </service>\n\n    </application>` with:

```xml
        </service>

        <service
            android:name=".glyph.MusicToyService"
            android:exported="true"
            tools:ignore="ExportedService">
            <intent-filter>
                <action android:name="com.nothing.glyph.TOY" />
            </intent-filter>
            <meta-data
                android:name="com.nothing.glyph.toy.name"
                android:resource="@string/music_toy_name" />
            <meta-data
                android:name="com.nothing.glyph.toy.image"
                android:resource="@drawable/ic_music_preview" />
            <meta-data
                android:name="com.nothing.glyph.toy.summary"
                android:resource="@string/music_toy_summary" />
            <meta-data
                android:name="com.nothing.glyph.toy.longpress"
                android:value="1" />
            <meta-data
                android:name="com.nothing.glyph.toy.aod_support"
                android:value="0" />
        </service>

    </application>
```

- [ ] **Step 4: Build and test**

```bash
source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && .superpowers/runtests.sh -q 2>&1 | tail -1
```

Expected: no compile errors, and all tests pass.

- [ ] **Step 5 (human + agent, Phone (3) connected): Smoke test the toy**

```bash
source .superpowers/env.sh && ./gradlew -q :app:installDebug && adb shell pm grant app.backlit android.permission.RECORD_AUDIO && adb logcat -c
```

The human enables **Backlit Music** in Glyph Toys, plays a song, and cycles to the toy. Check that the bars move with the music, that a long press cycles Mirror → Peaks → Wave, and that pausing gives the breathing line. Then the agent runs:

```bash
adb logcat -d -s BacklitMusic:V BacklitAudio:V BacklitGlyph:V | tail -20
```

Expected: `avg frame` lines under ~5 ms with `phase=LIVE` while music plays. No `render failed` lines.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/backlit/audio/ app/src/main/java/app/backlit/glyph/MusicToyService.kt app/src/main/res/ app/src/main/AndroidManifest.xml
git commit -m "feat: Backlit Music Glyph Toy driven by the output-mix visualizer

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Music tab, setup step, privacy texts

**Files:**
- Create: `app/src/main/java/app/backlit/ui/MusicScreen.kt`
- Rewrite: `app/src/main/java/app/backlit/ui/HomeScreen.kt`
- Modify: `app/src/main/java/app/backlit/ui/SetupScreen.kt`, `app/src/main/java/app/backlit/ui/AboutScreen.kt`, `docs/privacy-policy.md`, `docs/release/play-listing.md`

**Interfaces:**
- Consumes: `MusicEngine`, `DemoAudio`, `OutputVisualizer`, `MusicActivity`, `hasAudioPermission` (Tasks 6, 8); `VizStyles` (Task 4); `Settings`, `Sensitivity` (Task 7); Part 1 UI components (`SquareChip`, `SettingRow`, `DashedDivider`, `MatrixPreview`, `BacklitColors`).
- Produces:
  - `@Composable fun MusicTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit)`
  - `internal @Composable fun BrightnessRow(value: Int, onChange: (Int) -> Unit)` (moved from private to internal)
  - `HomeScreen` with the same signature as Part 1 and CLOCK | MUSIC tabs.

- [ ] **Step 1: Rewrite `HomeScreen.kt`**

The header (title, status line, setup banner) stays at the top, then the tabs. The existing clock content moves unchanged into `ClockTab`.

```kotlin
package app.backlit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.backlit.data.DayLightResolver
import app.backlit.data.LocationMode
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.glyph.TickSchedule
import app.backlit.render.FaceContext
import app.backlit.render.Mode
import app.backlit.render.faces.DayRingFace
import app.backlit.render.faces.Faces
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId

enum class Screen { HOME, SETUP, LOCATION, ABOUT }

@Composable
fun HomeScreen(
    settings: Settings,
    profile: DeviceProfile,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Text("BACKLIT", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
            val supported = profile != DeviceProfile.UNSUPPORTED
            Box(Modifier.size(7.dp).background(if (supported) BacklitColors.Red else BacklitColors.Dim, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                if (supported) "LIVE ON MATRIX · ${profile.label}" else "THIS PHONE HAS NO GLYPH MATRIX · PREVIEW ONLY",
                style = MaterialTheme.typography.labelSmall,
                color = BacklitColors.Dim,
            )
        }

        if (profile != DeviceProfile.UNSUPPORTED && !settings.toyEverBound) {
            Box(
                Modifier.fillMaxWidth().border(1.dp, BacklitColors.Red).clickable { onNavigate(Screen.SETUP) }.padding(12.dp),
            ) {
                Text("TOY NOT SET UP YET — TAP TO SET UP →", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareChip("CLOCK", selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.weight(1f))
            SquareChip("MUSIC", selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.weight(1f))
        }

        if (tab == 0) ClockTab(settings, profile, onUpdate, onNavigate) else MusicTab(settings, profile, onUpdate)

        SettingRow("Glyph Toy setup", "→") { onNavigate(Screen.SETUP) }
        SettingRow("About", "→") { onNavigate(Screen.ABOUT) }
        DashedDivider()
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ClockTab(
    settings: Settings,
    profile: DeviceProfile,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(TickSchedule.delayToNextTick(System.currentTimeMillis(), perSecond = true))
            value = LocalDateTime.now()
        }
    }
    var previewSize by rememberSaveable { mutableIntStateOf(profile.size) }
    val resolver = remember { DayLightResolver() }
    val face = Faces.byId(settings.faceId)
    val ctx = FaceContext(
        hour = now.hour, minute = now.minute, second = now.second,
        size = previewSize,
        mode = if (previewSize == 13) Mode.AOD else Mode.ACTIVE,
        options = settings.faceOptions,
        dayLight = resolver.resolve(settings, now.toLocalDate(), ZoneId.systemDefault()),
    )
    val grid = face.render(ctx)

    MatrixPreview(grid, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            listOf(25, 13).joinToString("   ") { if (it == previewSize) "[${it}×$it]" else "${it}×$it" },
            style = MaterialTheme.typography.labelSmall,
            color = BacklitColors.Dim,
            modifier = Modifier.clickable { previewSize = if (previewSize == 25) 13 else 25 }.padding(8.dp),
        )
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Faces.all.forEach { f ->
            SquareChip(f.label, selected = f.id == face.id, onClick = { onUpdate { it.copy(faceId = f.id) } }, modifier = Modifier.weight(1f))
        }
    }

    if (face.id == "analog") {
        SettingRow("Second hand", if (settings.secondHand) "ON" else "OFF") {
            onUpdate { it.copy(secondHand = !it.secondHand) }
        }
    }
    if (face.id == DayRingFace.id) {
        // Only faces that show digits have a time format; the analog face has none.
        SettingRow("Time format", if (settings.use24h) "24H" else "12H") {
            onUpdate { it.copy(use24h = !it.use24h) }
        }
        val where = when (settings.locationMode) {
            LocationMode.FIXED -> "06–18"
            else -> settings.placeName ?: "—"
        }
        SettingRow("Sun times", "$where →") { onNavigate(Screen.LOCATION) }
    }
    BrightnessRow(settings.brightness) { pct -> onUpdate { it.copy(brightness = pct) } }
}

@Composable
internal fun BrightnessRow(value: Int, onChange: (Int) -> Unit) {
    var local by remember(value) { mutableStateOf(value.toFloat()) }
    DashedDivider()
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Brightness", style = MaterialTheme.typography.bodyLarge)
            Text("${local.toInt()}%", style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.toInt()) },
            valueRange = 20f..100f,
            steps = 7,
            colors = SliderDefaults.colors(
                thumbColor = BacklitColors.White,
                activeTrackColor = BacklitColors.White,
                inactiveTrackColor = BacklitColors.Line,
            ),
        )
    }
}
```

- [ ] **Step 2: Create `MusicScreen.kt`**

```kotlin
package app.backlit.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.audio.DemoAudio
import app.backlit.audio.MusicActivity
import app.backlit.audio.MusicEngine
import app.backlit.audio.OutputVisualizer
import app.backlit.audio.hasAudioPermission
import app.backlit.data.Sensitivity
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.render.PixelGrid
import app.backlit.render.viz.VizStyles
import kotlinx.coroutines.delay

@Composable
fun MusicTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }

    val current by rememberUpdatedState(settings)
    val engine = remember { MusicEngine(25) }
    val music = remember { MusicActivity(context) }
    val viz = remember(granted) { OutputVisualizer().also { if (granted) it.start() } }
    DisposableEffect(viz) { onDispose { viz.release() } }
    var grid by remember { mutableStateOf(PixelGrid(25)) }
    var live by remember { mutableStateOf(false) }

    LaunchedEffect(settings.musicStyle) { engine.setStyle(settings.musicStyle) }
    LaunchedEffect(viz) {
        val start = SystemClock.elapsedRealtime()
        var last = start
        while (true) {
            delay(50)
            val now = SystemClock.elapsedRealtime()
            val dt = now - last
            last = now
            live = viz.isActive && music.isPlaying()
            grid = if (live) {
                engine.tick(now, dt, true, viz.readFft(), viz.samplingRateHz, current.musicSensitivity.gain, true)
            } else {
                engine.tickDemo(dt, DemoAudio.frame(now - start))
            }
        }
    }

    MatrixPreview(grid, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
    Text(
        if (live) "LIVE · REACTING TO YOUR MUSIC" else "DEMO AUDIO",
        style = MaterialTheme.typography.labelSmall,
        color = BacklitColors.Dim,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    )

    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VizStyles.ids.forEach { id ->
            SquareChip(
                VizStyles.label(id),
                selected = VizStyles.normalize(settings.musicStyle) == id,
                onClick = { onUpdate { it.copy(musicStyle = id) } },
                modifier = Modifier.weight(1f),
            )
        }
    }

    when {
        profile != DeviceProfile.PHONE_3 -> Notice("The Music toy needs the Phone (3). The (4a) Pro only supports always-on toys.")
        !granted && !denied -> {
            Notice(
                "Let Backlit react to your music. Android files this under the microphone permission, but Backlit " +
                    "only reads the sound your phone is already playing. Nothing is recorded, stored, or sent anywhere.",
            )
            SquareChip("ALLOW", selected = true, onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        !granted -> {
            Notice("Music reactions are off. The toy will show a calm line instead.")
            SquareChip(
                "OPEN SETTINGS",
                selected = false,
                onClick = {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    SettingRow("Sensitivity", settings.musicSensitivity.name) {
        onUpdate { it.copy(musicSensitivity = Sensitivity.entries[(it.musicSensitivity.ordinal + 1) % Sensitivity.entries.size]) }
    }
    BrightnessRow(settings.brightness) { pct -> onUpdate { it.copy(brightness = pct) } }
}

@Composable
private fun Notice(text: String) {
    Column(Modifier.fillMaxWidth().border(1.dp, BacklitColors.Line).padding(12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(8.dp))
}
```

- [ ] **Step 3: Setup step, About text, privacy policy, listing**

In `SetupScreen.kt`, replace:

```kotlin
            "04" to "Phone (4a) Pro: choose Backlit Clock as the always-on toy.",
```

with:

```kotlin
            "04" to "Phone (3): also switch on \"Backlit Music\" to see your music on the back.",
            "05" to "Phone (4a) Pro: choose Backlit Clock as the always-on toy.",
```

In `AboutScreen.kt`, replace:

```kotlin
            "Backlit collects no data. Everything, including your location if you share it, stays on your phone. " +
                "The app makes no network requests.",
```

with:

```kotlin
            "Backlit collects no data. Everything, including your location if you share it, stays on your phone. " +
                "Music is analysed on the phone in real time and is never recorded, stored or shared. " +
                "The app makes no network requests.",
```

In `docs/privacy-policy.md`, add after the **Location** bullet:

```markdown
- **Microphone permission (music reactions):** Android requires this permission to read the
  audio your phone is playing. Backlit uses it only while the Backlit Music toy or the app's Music
  tab is showing, analyses the sound on the phone in real time to animate the Glyph Matrix, and
  never records, stores or shares any audio.
```

In `docs/release/play-listing.md`, add after the `• DAY RING …` line:

```markdown
• MUSIC — a second toy that moves with the song playing on your phone (Mirror, Peaks, Wave)
```

and replace the Data safety line with:

```markdown
**Data safety form:** No data collected. No data shared. (Location and audio are processed on device only; audio is never recorded.)
```

- [ ] **Step 4: Build and test**

```bash
source .superpowers/env.sh && ./gradlew -q :app:assembleDebug && .superpowers/runtests.sh -q 2>&1 | tail -1
```

Expected: no compile errors, and all tests pass.

- [ ] **Step 5 (Phone (3) connected): Check the app on the device**

```bash
source .superpowers/env.sh && ./gradlew -q :app:installDebug && adb shell pm revoke app.backlit android.permission.RECORD_AUDIO; adb shell am force-stop app.backlit; adb shell am start -n app.backlit/.MainActivity
```

Check with screenshots (`adb exec-out screencap -p > /tmp/m.png`, then read the image): the MUSIC tab shows DEMO AUDIO bars and the disclosure + ALLOW. Tapping ALLOW shows the system prompt. Once allowed, with music playing, the label reads LIVE and the preview follows the music. The chips change style both in the app and on the matrix. Sensitivity cycles LOW → MED → HIGH.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/backlit/ui/ docs/privacy-policy.md docs/release/play-listing.md
git commit -m "feat: music tab with live preview, permission disclosure and privacy texts

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Device checklist for Part 2

**Files:**
- Modify: `docs/testing/device-checklist.md`

**Interfaces:**
- Consumes: everything above.
- Produces: a completed Music section of the checklist.

- [ ] **Step 1: Append the Music section**

Append to `docs/testing/device-checklist.md`:

```markdown

## Music toy (Part 2)
- [ ] Backlit Music appears in Glyph Toys with the bars icon.
- [ ] Mirror, Peaks and Wave each move in time with a song from Spotify and from YouTube Music.
- [ ] Works with the screen off (phone face down, toy shown via the Glyph Button).
- [ ] Long press cycles Mirror → Peaks → Wave; the app's chips follow.
- [ ] A 2–3 s gap between tracks does not drop to the idle line.
- [ ] Pause: bars fall for ~1 s, then the breathing line.
- [ ] Permission revoked (`adb shell pm revoke app.backlit android.permission.RECORD_AUDIO`): toy shows the livelier fallback line while music plays; no crash.
- [ ] After leaving the toy, `adb shell dumpsys media.audio_flinger | grep -i visualizer` shows no Backlit visualizer.
- [ ] `avg frame` log stays under ~5 ms; no visible stutter.
```

- [ ] **Step 2 (human + agent): Run the checklist** on the Phone (3) and tick each box. Fix any failure with a failing test first where it's testable, or a ledgered ruling where it isn't.

- [ ] **Step 3: Commit**

```bash
git add docs/testing/device-checklist.md
git commit -m "docs: music toy device checklist

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
