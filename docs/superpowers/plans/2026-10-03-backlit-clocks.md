# Backlit Part 1 (Core + Clock Toy) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Backlit, an Android app whose single Glyph Toy shows an Analog or Day-ring clock on the Nothing Phone (3) (25×25) and Phone (4a) Pro (13×13), with a dot-matrix styled companion app, ready for the Play Store.

**Architecture:** A pure-Kotlin `render/` package draws faces into a `PixelGrid` (0–255 per LED). The thin `glyph/` package converts frames to the SDK's 0–2047 scale and owns the Glyph Toy service. `data/` holds DataStore settings, on-device sunrise/sunset maths and location. `ui/` is Jetpack Compose and renders the same `PixelGrid` as a live preview.

**Tech Stack:** Kotlin, Android Gradle Plugin 9.4 (built-in Kotlin), Jetpack Compose (Material 3), AndroidX DataStore Preferences, kotlinx-coroutines, JUnit 4, Nothing GlyphMatrix SDK `glyph-matrix-sdk-2.0.aar`.

**Spec:** `docs/superpowers/specs/2026-10-03-backlit-clocks-design.md`

## Global Constraints

- Package / namespace / applicationId: `app.backlit`.
- `minSdk 34` (the SDK AAR itself needs ≥ 33), `compileSdk 37`, `targetSdk 37`, Java/Kotlin target 17.
- `render/` must never import `android.*`, `androidx.*` or `com.nothing.*`.
- Only files in `app/src/main/java/app/backlit/glyph/` may import `com.nothing.ketchum.*`.
- No network calls anywhere. No analytics. No ads. No IAP.
- Permissions: only `com.nothing.ketchum.permission.ENABLE` and `android.permission.ACCESS_COARSE_LOCATION`.
- Manifest keeps `<meta-data android:name="NothingKey" android:value="test"/>` (no Nothing API key is needed on Android 16+; the meta-data is kept for compatibility, per Nothing's Glyph kit README).
- The SDK `.aar` is **never committed** (Nothing's EULA forbids redistribution). It lives at `libs/glyph-matrix-sdk-2.0.aar` and `libs/*.aar` is gitignored.
- Faces render at "design brightness" 0–255. Only `FrameEncoder` converts to the SDK range (0 = off, otherwise `MIN_LIT..2047`).
- Matrix size comes from the device: Phone (3) = 25, Phone (4a) Pro = 13. LED mask: `hypot(x - c, y - c) <= size / 2.0` with `c = (size - 1) / 2.0`.
- Visual style: pure black `#000000`, white text, single red accent `#D71921`, headings in **Doto** (weight 900), body text in **Space Grotesk**, dark only.
- Commit after each task with a `feat:`/`test:`/`chore:`/`docs:` message ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Midnight rollover while the toy is bound:** sun times must move to the new date at 00:00 without rebinding. Pinned by `DayLightResolverTest.recomputesWhenDateChanges` (Task 6).
2. **Timezone change / DST jump while bound:** the clock must show the new local time immediately and sun times must be recomputed for the new zone. Pinned by `DayLightResolverTest.recomputesWhenZoneChanges` (Task 6) and the receiver in Task 8.
3. **Sunset after local midnight or polar day/night** (high latitudes, odd zones): the ring must not invert or go blank. Pinned by `DayLightTest.wrapsPastMidnight` (Task 3) and `SunTimesTest` polar cases (Task 5).
4. **Low brightness setting making dim pixels invisible on real LEDs:** any non-zero design pixel must stay at or above `MIN_LIT`. Pinned by `FrameEncoderTest.nonZeroNeverBelowMinLit` (Task 6).
5. **12-hour mode at midnight/noon:** shows `12`, not `00`. Pinned by `DayRingFaceTest.twelveHourMidnightShowsTwelve` (Task 5).

---

## File Structure

```
.gitignore                               (modify: add libs/*.aar, keystore.properties, *.jks)
settings.gradle.kts                      Gradle settings
build.gradle.kts                         root plugins
gradle/libs.versions.toml                version catalog
gradle.properties                        JVM args, AndroidX flag
libs/README.md                           how to obtain the SDK AAR
app/build.gradle.kts                     app module
app/proguard-rules.pro                   R8 keep rules for the SDK
app/src/main/AndroidManifest.xml
app/src/main/assets/cities.tsv           bundled offline city list (Task 11)
app/src/main/res/font/doto.ttf, space_grotesk.ttf
app/src/main/res/drawable/ic_toy_preview.xml, ic_launcher_foreground.xml
app/src/main/res/mipmap-anydpi/ic_launcher.xml
app/src/main/res/values/strings.xml, colors.xml, themes.xml
app/src/main/java/app/backlit/
  render/PixelGrid.kt                    grid + LED mask + ASCII dump
  render/Draw.kt                         Wu line, disc, crescent, px rounding
  render/PixelFont.kt                    3×5 digits and text drawing
  render/DayLight.kt                     sunrise/sunset window in minutes
  render/FaceContext.kt                  Mode, FaceOptions, FaceContext
  render/faces/Face.kt                   Face interface + Faces registry
  render/faces/AnalogFace.kt
  render/faces/DayRingFace.kt
  data/SunTimes.kt                       NOAA sunrise/sunset
  data/Settings.kt                       Settings + LocationMode (pure)
  data/DayLightResolver.kt               settings + date + zone → DayLight (cached)
  data/SettingsRepo.kt                   DataStore persistence
  data/Cities.kt                         TSV parse + search (pure) + asset load
  data/LocationSource.kt                 coarse location via LocationManager
  data/LocationRefresher.kt              daily refresh helper
  glyph/TickSchedule.kt                  pure tick delay maths
  glyph/FrameEncoder.kt                  PixelGrid → SDK IntArray (pure)
  glyph/ModeTracker.kt                   ACTIVE vs AOD decision (pure)
  glyph/DeviceProfile.kt                 Phone (3) / (4a) Pro / unsupported
  glyph/GlyphOutput.kt                   GlyphMatrixManager wrapper with retry
  glyph/ClockToyService.kt               the Glyph Toy
  glyph/ToysManager.kt                   intent to Nothing's toy manager
  ui/Theme.kt                            colours, fonts, MaterialTheme
  ui/Components.kt                       DashedDivider, SquareChip, SettingRow, ScreenHeader
  ui/MatrixPreview.kt                    PixelGrid as round LEDs
  ui/HomeScreen.kt
  ui/SetupScreen.kt
  ui/LocationScreen.kt
  ui/AboutScreen.kt
  MainActivity.kt                        screen switching
app/src/test/java/app/backlit/...        JVM unit tests mirroring the above
docs/release/play-listing.md, docs/privacy-policy.md
```

---

### Task 0: Toolchain and SDK (performed by the human, assisted)

This machine has **no JDK, no Android SDK, no Gradle**. These steps download software and accept licences, so the human runs or approves each one. Agents must not run downloads or accept licences on their own.

**Files:**
- Create: `libs/README.md`
- Modify: `.gitignore`

- [ ] **Step 1 (human): Install Android Studio and Gradle**

```bash
brew install --cask android-studio
```

```bash
brew install gradle
```

Open Android Studio once and finish the setup wizard (Standard install). This installs the Android SDK at `~/Library/Android/sdk`, platform-tools (`adb`) and accepts the SDK licences. Then in Android Studio → Settings → Languages & Frameworks → Android SDK → SDK Platforms, tick **Android API 37** and apply.

- [ ] **Step 2 (human): Point the shell at the SDK and Studio's bundled JDK**

Append to `~/.zshrc`:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

Then open a new terminal and verify:

```bash
java -version && adb version && gradle --version
```

Expected: Java 17 or newer, an `Android Debug Bridge` version line, a Gradle 9.x version.

- [ ] **Step 3 (human approves download): Fetch the Glyph Matrix SDK into `libs/`**

```bash
mkdir -p libs && curl -L -o libs/glyph-matrix-sdk-2.0.aar https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/raw/main/glyph-matrix-sdk-2.0.aar
```

Verify it is a zip (an AAR) and not an HTML error page:

```bash
unzip -l libs/glyph-matrix-sdk-2.0.aar | head
```

Expected: a listing containing `classes.jar` and `AndroidManifest.xml`.

- [ ] **Step 4 (human approves download): Fetch the two OFL fonts**

```bash
mkdir -p app/src/main/res/font && curl -L -o app/src/main/res/font/doto.ttf "https://github.com/google/fonts/raw/main/ofl/doto/Doto%5BROND%2Cwght%5D.ttf" && curl -L -o app/src/main/res/font/space_grotesk.ttf "https://github.com/google/fonts/raw/main/ofl/spacegrotesk/SpaceGrotesk%5Bwght%5D.ttf"
```

Verify both are real font files:

```bash
file app/src/main/res/font/*.ttf
```

Expected: both report `TrueType Font data`.

- [ ] **Step 5: Write `libs/README.md`**

```markdown
# libs/

`glyph-matrix-sdk-2.0.aar` goes here. It is **not** committed: Nothing's EULA does not allow
redistributing it. Download it from
https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
(file `glyph-matrix-sdk-2.0.aar`) and place it in this folder before building.
```

- [ ] **Step 6: Extend `.gitignore`**

Append these lines to the existing `.gitignore`:

```
libs/*.aar
keystore.properties
*.jks
*.keystore
captures/
```

- [ ] **Step 7: Commit**

```bash
git add .gitignore libs/README.md app/src/main/res/font/doto.ttf app/src/main/res/font/space_grotesk.ttf
git commit -m "chore: toolchain notes, SDK placeholder and bundled OFL fonts

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 1: Gradle project skeleton + PixelGrid

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/app/backlit/render/PixelGrid.kt`
- Test: `app/src/test/java/app/backlit/render/PixelGridTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `class PixelGrid(val size: Int)` with `operator fun get(x: Int, y: Int): Int`, `fun hasLed(x: Int, y: Int): Boolean`, `fun plot(x: Int, y: Int, b: Int)` (keeps the brighter value), `fun put(x: Int, y: Int, b: Int)` (overwrites), `fun raw(): IntArray` (row-major copy), `fun toAscii(): String`, `fun litCount(): Int`, `val center: Double`, value equality.
  - `companion object { fun ledCount(size: Int): Int }`

- [ ] **Step 1: Create the Gradle files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Backlit"
include(":app")
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.4.0"
kotlin = "2.2.20"
compileSdk = "37"
minSdk = "34"
targetSdk = "37"
composeBom = "2025.09.00"
activityCompose = "1.11.0"
lifecycle = "2.9.4"
datastore = "1.1.7"
coroutines = "1.10.2"
junit = "4.13.2"

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

`app/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.backlit"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.backlit"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    // Nothing Glyph Matrix SDK. Not committed (EULA); see libs/README.md.
    implementation(files("../libs/glyph-matrix-sdk-2.0.aar"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
```

`app/proguard-rules.pro`:

```
# Nothing Glyph Matrix SDK: keep everything; it is called through its public API and binder.
-keep class com.nothing.ketchum.** { *; }
-dontwarn com.nothing.ketchum.**
```

`app/src/main/AndroidManifest.xml` (minimal for now; Tasks 8 and 9 extend it):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true">

        <meta-data
            android:name="NothingKey"
            android:value="test" />

    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Backlit</string>
</resources>
```

- [ ] **Step 2: Generate the Gradle wrapper and check the build resolves**

```bash
gradle wrapper && ./gradlew :app:help
```

Expected: `BUILD SUCCESSFUL`. If Gradle reports that the Kotlin/Compose plugin version is incompatible with AGP 9.4.0, set `kotlin` in `libs.versions.toml` to the version the error message names and rerun. If a library version does not resolve, change it to the newest stable version listed on https://developer.android.com/jetpack/androidx/versions and rerun. Note any version you changed in the commit message.

- [ ] **Step 3: Write the failing test**

`app/src/test/java/app/backlit/render/PixelGridTest.kt`:

```kotlin
package app.backlit.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelGridTest {

    @Test
    fun ledCountsMatchTheRealPanels() {
        // Phone (4a) Pro allocation diagram has 137 LEDs on the 13×13 grid.
        assertEquals(137, PixelGrid.ledCount(13))
        // Disc of radius 12.5 on a 25×25 grid.
        assertEquals(489, PixelGrid.ledCount(25))
    }

    @Test
    fun cornersHaveNoLedCentreAndEdgesDo() {
        val g = PixelGrid(25)
        assertFalse(g.hasLed(0, 0))
        assertTrue(g.hasLed(12, 0))
        assertTrue(g.hasLed(0, 12))
        assertTrue(g.hasLed(12, 12))
        assertFalse(g.hasLed(-1, 12))
        assertFalse(g.hasLed(25, 12))
    }

    @Test
    fun plotKeepsBrighterValueAndClamps() {
        val g = PixelGrid(13)
        g.plot(6, 6, 100)
        g.plot(6, 6, 50)
        assertEquals(100, g[6, 6])
        g.plot(6, 6, 999)
        assertEquals(255, g[6, 6])
    }

    @Test
    fun putOverwritesIncludingZero() {
        val g = PixelGrid(13)
        g.plot(6, 6, 200)
        g.put(6, 6, 0)
        assertEquals(0, g[6, 6])
    }

    @Test
    fun writesOutsideTheMaskAreIgnored() {
        val g = PixelGrid(13)
        g.plot(0, 0, 255)
        g.put(0, 0, 255)
        g.plot(-3, 40, 255)
        assertEquals(0, g[0, 0])
        assertEquals(0, g.litCount())
    }

    @Test
    fun rawIsRowMajorCopy() {
        val g = PixelGrid(13)
        g.plot(3, 2, 77)
        val raw = g.raw()
        assertEquals(169, raw.size)
        assertEquals(77, raw[2 * 13 + 3])
        raw[2 * 13 + 3] = 0
        assertEquals(77, g[3, 2])
    }

    @Test
    fun valueEquality() {
        val a = PixelGrid(13).apply { plot(6, 6, 10) }
        val b = PixelGrid(13).apply { plot(6, 6, 10) }
        val c = PixelGrid(13).apply { plot(6, 6, 11) }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun asciiUsesBrightnessBands() {
        val g = PixelGrid(13)
        g.plot(6, 6, 255)
        g.plot(5, 6, 100)
        g.plot(4, 6, 20)
        val row6 = g.toAscii().lines()[6]
        assertEquals('#', row6[6])
        assertEquals('+', row6[5])
        assertEquals('-', row6[4])
        assertEquals('.', row6[3])
        assertEquals(' ', g.toAscii().lines()[0][0])
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.render.PixelGridTest"
```

Expected: compilation FAIL, `Unresolved reference 'PixelGrid'`.

- [ ] **Step 5: Implement `PixelGrid`**

`app/src/main/java/app/backlit/render/PixelGrid.kt`:

```kotlin
package app.backlit.render

import kotlin.math.hypot

/**
 * One frame for a round Glyph Matrix: `size × size` design brightness values (0–255).
 * Positions outside the round LED mask can never be lit.
 */
class PixelGrid(val size: Int) {

    private val px = IntArray(size * size)

    /** Centre coordinate, e.g. 12.0 on 25×25 and 6.0 on 13×13. */
    val center: Double = (size - 1) / 2.0

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size

    /** True when the panel has a physical LED at (x, y). */
    fun hasLed(x: Int, y: Int): Boolean =
        inBounds(x, y) && hypot(x - center, y - center) <= size / 2.0

    operator fun get(x: Int, y: Int): Int = if (inBounds(x, y)) px[y * size + x] else 0

    /** Lighten: keeps the brighter of the current and new value. */
    fun plot(x: Int, y: Int, b: Int) {
        if (!hasLed(x, y)) return
        val i = y * size + x
        val v = b.coerceIn(0, 255)
        if (v > px[i]) px[i] = v
    }

    /** Overwrite, including with 0 (used to carve space, e.g. behind digits). */
    fun put(x: Int, y: Int, b: Int) {
        if (!hasLed(x, y)) return
        px[y * size + x] = b.coerceIn(0, 255)
    }

    /** Row-major copy of all values. */
    fun raw(): IntArray = px.copyOf()

    fun litCount(): Int = px.count { it > 0 }

    /** Debug view: ' ' no LED, '.' off, '-' 1–84, '+' 85–169, '#' 170–255. */
    fun toAscii(): String = buildString {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val v = this@PixelGrid[x, y]
                append(
                    when {
                        !hasLed(x, y) -> ' '
                        v == 0 -> '.'
                        v < 85 -> '-'
                        v < 170 -> '+'
                        else -> '#'
                    }
                )
            }
            if (y < size - 1) append('\n')
        }
    }

    override fun equals(other: Any?): Boolean =
        other is PixelGrid && other.size == size && other.px.contentEquals(px)

    override fun hashCode(): Int = 31 * size + px.contentHashCode()

    override fun toString(): String = "PixelGrid($size)\n${toAscii()}"

    companion object {
        fun ledCount(size: Int): Int {
            val g = PixelGrid(size)
            return (0 until size).sumOf { y -> (0 until size).count { x -> g.hasLed(x, y) } }
        }
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.render.PixelGridTest"
```

Expected: PASS (8 tests). If `ledCount(25)` differs from 489, print `PixelGrid.ledCount(25)` and check it by hand against the mask formula; the 13×13 value 137 is fixed by Nothing's diagram and must not change.

- [ ] **Step 7: Check the whole app compiles with the SDK**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat app/
git commit -m "feat: Gradle skeleton and PixelGrid with round LED mask

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Drawing primitives and pixel font

**Files:**
- Create: `app/src/main/java/app/backlit/render/Draw.kt`, `app/src/main/java/app/backlit/render/PixelFont.kt`
- Test: `app/src/test/java/app/backlit/render/DrawTest.kt`, `app/src/test/java/app/backlit/render/PixelFontTest.kt`

**Interfaces:**
- Consumes: `PixelGrid` (Task 1).
- Produces:
  - `fun Double.px(): Int` — round half up, robust to float noise.
  - `object Draw { fun wuLine(g: PixelGrid, x0: Double, y0: Double, x1: Double, y1: Double, b: Int); fun disc(g: PixelGrid, cx: Double, cy: Double, r: Double, b: Int); fun crescent(g: PixelGrid, cx: Double, cy: Double, b: Int) }`
  - `object PixelFont { const val WIDTH = 3; const val HEIGHT = 5; fun digit(g: PixelGrid, d: Int, x: Int, y: Int, b: Int); fun text(g: PixelGrid, s: String, x: Int, y: Int, b: Int) }` — `text` lays out digits 3 wide with a 1-pixel gap. `':'` takes 1 column, lit at rows 1 and 3, followed by a 1-pixel gap. So `"15:07"` is 17 pixels wide.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/backlit/render/DrawTest.kt`:

```kotlin
package app.backlit.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawTest {

    @Test
    fun pxRoundsHalfUpAndAbsorbsFloatNoise() {
        assertEquals(3, 2.5.px())
        assertEquals(2, 2.4999.px())
        assertEquals(12, (12.0 + 6e-17).px())
        assertEquals(15, 14.499999999999998.px())
        assertEquals(-1, (-1.2).px())
    }

    @Test
    fun verticalLineIsOnePixelWideAtFullBrightness() {
        val g = PixelGrid(25)
        Draw.wuLine(g, 12.0, 12.0, 12.0, 2.5, 170)
        for (y in 3..12) assertEquals("row $y", 170, g[12, y])
        assertEquals(0, g[12, 2])
        assertEquals(0, g[11, 6])
        assertEquals(0, g[13, 6])
    }

    @Test
    fun horizontalLineIsOnePixelWide() {
        val g = PixelGrid(25)
        Draw.wuLine(g, 12.0, 12.0, 18.0, 12.0, 255)
        for (x in 12..18) assertEquals(255, g[x, 12])
        assertEquals(0, g[12, 11])
        assertEquals(0, g[12, 13])
    }

    @Test
    fun diagonalLineSplitsBrightnessBetweenNeighbours() {
        val g = PixelGrid(25)
        // Slope 0.5: at x = 13 the ideal y is 12.5, shared equally by rows 12 and 13.
        Draw.wuLine(g, 12.0, 12.0, 16.0, 14.0, 200)
        assertEquals(100, g[13, 12])
        assertEquals(100, g[13, 13])
        assertEquals(200, g[14, 13])
    }

    @Test
    fun discOfRadiusOneAndAHalfIsThreeByThree() {
        val g = PixelGrid(25)
        Draw.disc(g, 12.0, 12.0, 1.5, 255)
        for (x in 11..13) for (y in 11..13) assertEquals(255, g[x, y])
        assertEquals(9, g.litCount())
    }

    @Test
    fun crescentIsLitOnTheLeftAndDarkOnTheUpperRight() {
        val g = PixelGrid(25)
        Draw.crescent(g, 12.0, 12.0, 170)
        assertEquals(170, g[10, 12])
        assertEquals(0, g[13, 11])
        assertTrue(g.litCount() in 4..12)
    }
}
```

`app/src/test/java/app/backlit/render/PixelFontTest.kt`:

```kotlin
package app.backlit.render

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelFontTest {

    private fun rows(g: PixelGrid, x: Int, y: Int): List<String> =
        (0 until 5).map { r -> (0 until 3).joinToString("") { c -> if (g[x + c, y + r] > 0) "1" else "0" } }

    @Test
    fun everyDigitMatchesItsBitmap() {
        val expected = mapOf(
            0 to listOf("111", "101", "101", "101", "111"),
            1 to listOf("010", "110", "010", "010", "111"),
            2 to listOf("111", "001", "111", "100", "111"),
            3 to listOf("111", "001", "111", "001", "111"),
            4 to listOf("101", "101", "111", "001", "001"),
            5 to listOf("111", "100", "111", "001", "111"),
            6 to listOf("111", "100", "111", "101", "111"),
            7 to listOf("111", "001", "001", "001", "001"),
            8 to listOf("111", "101", "111", "101", "111"),
            9 to listOf("111", "101", "111", "001", "111"),
        )
        for ((d, bitmap) in expected) {
            val g = PixelGrid(25)
            PixelFont.digit(g, d, 10, 10, 200)
            assertEquals("digit $d", bitmap, rows(g, 10, 10))
        }
    }

    @Test
    fun textLaysOutDigitsAndColon() {
        val g = PixelGrid(25)
        PixelFont.text(g, "15:07", 4, 10, 230)
        assertEquals(230, g[5, 10])   // '1' top middle
        assertEquals(230, g[8, 10])   // '5' top left
        assertEquals(230, g[12, 11])  // colon upper dot
        assertEquals(230, g[12, 13])  // colon lower dot
        assertEquals(0, g[12, 12])
        assertEquals(230, g[14, 10])  // '0' top left
        assertEquals(230, g[20, 10])  // '7' top right
        assertEquals(0, g[21, 10])
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.render.DrawTest" --tests "app.backlit.render.PixelFontTest"
```

Expected: compilation FAIL, unresolved `Draw`, `px`, `PixelFont`.

- [ ] **Step 3: Implement `Draw.kt`**

```kotlin
package app.backlit.render

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Round half up. The epsilon keeps 14.4999…98 from JVM trig on the same side as 14.5. */
fun Double.px(): Int = floor(this + 0.5 + 1e-9).toInt()

object Draw {

    /** Xiaolin Wu anti-aliased line; brightness is split between the two nearest pixels. */
    fun wuLine(g: PixelGrid, x0: Double, y0: Double, x1: Double, y1: Double, b: Int) {
        var ax = x0; var ay = y0; var bx = x1; var by = y1
        val steep = abs(by - ay) > abs(bx - ax)
        if (steep) {
            var t = ax; ax = ay; ay = t
            t = bx; bx = by; by = t
        }
        if (ax > bx) {
            var t = ax; ax = bx; bx = t
            t = ay; ay = by; by = t
        }
        val dx = bx - ax
        val gradient = if (dx == 0.0) 0.0 else (by - ay) / dx
        for (x in ax.px()..bx.px()) {
            val y = ay + gradient * (x - ax)
            val yi = floor(y + 1e-9).toInt()
            val frac = (y - yi).coerceAtLeast(0.0)
            plotWeighted(g, steep, x, yi, b * (1 - frac))
            plotWeighted(g, steep, x, yi + 1, b * frac)
        }
    }

    private fun plotWeighted(g: PixelGrid, steep: Boolean, a: Int, c: Int, v: Double) {
        val iv = v.roundToInt()
        if (iv <= 0) return
        if (steep) g.plot(c, a, iv) else g.plot(a, c, iv)
    }

    /** Filled disc centred on the nearest pixel to (cx, cy). */
    fun disc(g: PixelGrid, cx: Double, cy: Double, r: Double, b: Int) {
        val x0 = cx.px(); val y0 = cy.px()
        val limit = r * r + 0.5
        val reach = r.toInt() + 1
        for (dx in -reach..reach) for (dy in -reach..reach) {
            if (dx * dx + dy * dy <= limit) g.plot(x0 + dx, y0 + dy, b)
        }
    }

    /** Small crescent moon: a radius-2 disc with its upper-right bitten out. Never erases. */
    fun crescent(g: PixelGrid, cx: Double, cy: Double, b: Int) {
        val x0 = cx.px(); val y0 = cy.px()
        for (dx in -3..3) for (dy in -3..3) {
            val inMoon = dx * dx + dy * dy <= 4.5
            val inBite = (dx - 1) * (dx - 1) + (dy + 1) * (dy + 1) <= 4.84
            if (inMoon && !inBite) g.plot(x0 + dx, y0 + dy, b)
        }
    }
}
```

- [ ] **Step 4: Implement `PixelFont.kt`**

```kotlin
package app.backlit.render

/** 3×5 digits, shared by the 25×25 and 13×13 faces. */
object PixelFont {
    const val WIDTH = 3
    const val HEIGHT = 5

    private val DIGITS = arrayOf(
        arrayOf("111", "101", "101", "101", "111"),
        arrayOf("010", "110", "010", "010", "111"),
        arrayOf("111", "001", "111", "100", "111"),
        arrayOf("111", "001", "111", "001", "111"),
        arrayOf("101", "101", "111", "001", "001"),
        arrayOf("111", "100", "111", "001", "111"),
        arrayOf("111", "100", "111", "101", "111"),
        arrayOf("111", "001", "001", "001", "001"),
        arrayOf("111", "101", "111", "101", "111"),
        arrayOf("111", "101", "111", "001", "111"),
    )

    fun digit(g: PixelGrid, d: Int, x: Int, y: Int, b: Int) {
        val rows = DIGITS[d.coerceIn(0, 9)]
        for (r in 0 until HEIGHT) for (c in 0 until WIDTH) {
            if (rows[r][c] == '1') g.plot(x + c, y + r, b)
        }
    }

    /** Digits are 3 wide + 1 gap; ':' is 1 wide (dots on rows 1 and 3) + 1 gap. */
    fun text(g: PixelGrid, s: String, x: Int, y: Int, b: Int) {
        var cx = x
        for (ch in s) {
            when {
                ch.isDigit() -> { digit(g, ch - '0', cx, y, b); cx += WIDTH + 1 }
                ch == ':' -> { g.plot(cx, y + 1, b); g.plot(cx, y + 3, b); cx += 2 }
                else -> cx += WIDTH + 1
            }
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.render.DrawTest" --tests "app.backlit.render.PixelFontTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/backlit/render/Draw.kt app/src/main/java/app/backlit/render/PixelFont.kt app/src/test/java/app/backlit/render/DrawTest.kt app/src/test/java/app/backlit/render/PixelFontTest.kt
git commit -m "feat: anti-aliased line, disc, crescent and 3x5 pixel font

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Face model and Analog face

**Files:**
- Create: `app/src/main/java/app/backlit/render/DayLight.kt`, `app/src/main/java/app/backlit/render/FaceContext.kt`, `app/src/main/java/app/backlit/render/faces/Face.kt`, `app/src/main/java/app/backlit/render/faces/AnalogFace.kt`
- Test: `app/src/test/java/app/backlit/render/DayLightTest.kt`, `app/src/test/java/app/backlit/render/faces/AnalogFaceTest.kt`

**Interfaces:**
- Consumes: `PixelGrid`, `Draw`, `px()` (Tasks 1–2).
- Produces:
  - `data class DayLight(val riseMinute: Int, val setMinute: Int) { fun isDay(minuteOfDay: Double): Boolean; companion object { val FIXED: DayLight /*360,1080*/; val ALL_DAY: DayLight /*0,1440*/; val ALL_NIGHT: DayLight /*0,0*/ } }`
  - `enum class Mode { ACTIVE, AOD }`
  - `data class FaceOptions(val secondHand: Boolean = true, val use24h: Boolean = true)`
  - `data class FaceContext(val hour: Int, val minute: Int, val second: Int, val size: Int, val mode: Mode, val options: FaceOptions = FaceOptions(), val dayLight: DayLight = DayLight.FIXED) { val minuteOfDay: Double }`
  - `interface Face { val id: String; val label: String; fun render(ctx: FaceContext): PixelGrid; fun needsSecondTicks(ctx: FaceContext): Boolean }`
  - `object Faces { val all: List<Face>; fun byId(id: String): Face; fun next(id: String): Face }` (DayRingFace is added to `all` in Task 5)
  - `object AnalogFace : Face` with `id = "analog"`, `label = "ANALOG"`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/backlit/render/DayLightTest.kt`:

```kotlin
package app.backlit.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayLightTest {

    @Test
    fun fixedIsSixToEighteen() {
        assertFalse(DayLight.FIXED.isDay(359.0))
        assertTrue(DayLight.FIXED.isDay(360.0))
        assertTrue(DayLight.FIXED.isDay(1079.9))
        assertFalse(DayLight.FIXED.isDay(1080.0))
    }

    @Test
    fun polarExtremes() {
        assertTrue(DayLight.ALL_DAY.isDay(0.0))
        assertTrue(DayLight.ALL_DAY.isDay(1439.0))
        assertFalse(DayLight.ALL_NIGHT.isDay(720.0))
    }

    @Test
    fun wrapsPastMidnight() {
        // Sunrise 03:00, sunset 00:30 the next day (high latitude summer).
        val d = DayLight(180, 30)
        assertTrue(d.isDay(10.0))
        assertFalse(d.isDay(100.0))
        assertTrue(d.isDay(200.0))
        assertTrue(d.isDay(1400.0))
    }
}
```

`app/src/test/java/app/backlit/render/faces/AnalogFaceTest.kt`:

```kotlin
package app.backlit.render.faces

import app.backlit.render.FaceContext
import app.backlit.render.FaceOptions
import app.backlit.render.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalogFaceTest {

    private fun ctx(h: Int, m: Int, s: Int = 0, size: Int = 25, mode: Mode = Mode.ACTIVE, sec: Boolean = false) =
        FaceContext(h, m, s, size, mode, FaceOptions(secondHand = sec))

    @Test
    fun noonOn25StacksHourOverMinute() {
        val g = AnalogFace.render(ctx(12, 0))
        for (y in 6..12) assertEquals("hour row $y", 255, g[12, y])
        for (y in 3..5) assertEquals("minute row $y", 170, g[12, y])
        assertEquals(200, g[12, 0])      // 12 o'clock major tick
        assertEquals(0, g[12, 2])
    }

    @Test
    fun threeOClockHourPointsRight() {
        val g = AnalogFace.render(ctx(3, 0))
        for (x in 12..18) assertEquals("hour col $x", 255, g[x, 12])
        for (y in 3..11) assertEquals("minute row $y", 170, g[12, y])
        assertEquals(200, g[24, 12])     // 3 o'clock tick
    }

    @Test
    fun minorTicksOnlyOn25() {
        val big = AnalogFace.render(ctx(6, 30))
        assertEquals(70, big[18, 2])     // 1 o'clock tick on 25×25
        val small = AnalogFace.render(ctx(6, 30, size = 13, mode = Mode.AOD))
        assertEquals(0, small[9, 1])     // no 1 o'clock tick on 13×13
        assertEquals(200, small[6, 0])
        assertEquals(200, small[12, 6])
    }

    @Test
    fun noonOn13() {
        val g = AnalogFace.render(ctx(12, 0, size = 13, mode = Mode.AOD))
        assertEquals(170, g[6, 2])
        for (y in 3..6) assertEquals(255, g[6, y])
    }

    @Test
    fun minuteHandIsStillWithinAMinute() {
        val a = AnalogFace.render(ctx(10, 41, 5))
        val b = AnalogFace.render(ctx(10, 41, 55))
        assertEquals(a, b)
    }

    @Test
    fun secondDotOnlyWhenActiveOn25AndEnabled() {
        val on = AnalogFace.render(ctx(12, 0, 0, sec = true))
        assertEquals(255, on[12, 0])
        val at15 = AnalogFace.render(ctx(12, 0, 15, sec = true))
        assertEquals(255, at15[24, 12])
        val aod = AnalogFace.render(ctx(12, 0, 15, mode = Mode.AOD, sec = true))
        assertEquals(AnalogFace.render(ctx(12, 0, 15, mode = Mode.AOD, sec = false)), aod)
        val off = AnalogFace.render(ctx(12, 0, 15, sec = false))
        assertNotEquals(at15, off)
    }

    @Test
    fun needsSecondTicks() {
        assertTrue(AnalogFace.needsSecondTicks(ctx(1, 1, sec = true)))
        assertFalse(AnalogFace.needsSecondTicks(ctx(1, 1, sec = false)))
        assertFalse(AnalogFace.needsSecondTicks(ctx(1, 1, mode = Mode.AOD, sec = true)))
        assertFalse(AnalogFace.needsSecondTicks(ctx(1, 1, size = 13, sec = true)))
    }

    @Test
    fun registryCyclesAndFallsBack() {
        assertEquals(AnalogFace, Faces.byId("analog"))
        assertEquals(AnalogFace, Faces.byId("does-not-exist"))
        assertEquals(Faces.all[(Faces.all.indexOf(AnalogFace) + 1) % Faces.all.size], Faces.next("analog"))
        assertEquals(Faces.all.first(), Faces.next(Faces.all.last().id))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.render.DayLightTest" --tests "app.backlit.render.faces.AnalogFaceTest"
```

Expected: compilation FAIL, unresolved `DayLight`, `FaceContext`, `AnalogFace`.

- [ ] **Step 3: Implement the model**

`app/src/main/java/app/backlit/render/DayLight.kt`:

```kotlin
package app.backlit.render

/** Daylight window in local minutes of the day [0, 1440]. Handles sunset after midnight. */
data class DayLight(val riseMinute: Int, val setMinute: Int) {

    fun isDay(minuteOfDay: Double): Boolean = when {
        riseMinute == setMinute -> false
        riseMinute < setMinute -> minuteOfDay >= riseMinute && minuteOfDay < setMinute
        else -> minuteOfDay >= riseMinute || minuteOfDay < setMinute
    }

    companion object {
        val FIXED = DayLight(360, 1080)
        val ALL_DAY = DayLight(0, 1440)
        val ALL_NIGHT = DayLight(0, 0)
    }
}
```

`app/src/main/java/app/backlit/render/FaceContext.kt`:

```kotlin
package app.backlit.render

enum class Mode { ACTIVE, AOD }

data class FaceOptions(
    val secondHand: Boolean = true,
    val use24h: Boolean = true,
)

data class FaceContext(
    val hour: Int,
    val minute: Int,
    val second: Int,
    val size: Int,
    val mode: Mode,
    val options: FaceOptions = FaceOptions(),
    val dayLight: DayLight = DayLight.FIXED,
) {
    val minuteOfDay: Double get() = hour * 60.0 + minute
    val isLarge: Boolean get() = size >= 25
}
```

`app/src/main/java/app/backlit/render/faces/Face.kt`:

```kotlin
package app.backlit.render.faces

import app.backlit.render.FaceContext
import app.backlit.render.PixelGrid

interface Face {
    val id: String
    val label: String
    fun render(ctx: FaceContext): PixelGrid
    fun needsSecondTicks(ctx: FaceContext): Boolean
}

object Faces {
    val all: List<Face> = listOf(AnalogFace)

    fun byId(id: String): Face = all.firstOrNull { it.id == id } ?: all.first()

    fun next(id: String): Face {
        val i = all.indexOfFirst { it.id == id }
        return all[(i + 1).mod(all.size)]
    }
}
```

`app/src/main/java/app/backlit/render/faces/AnalogFace.kt`:

```kotlin
package app.backlit.render.faces

import app.backlit.render.Draw
import app.backlit.render.FaceContext
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.cos
import kotlin.math.sin

object AnalogFace : Face {
    override val id = "analog"
    override val label = "ANALOG"

    private const val MAJOR_TICK = 200
    private const val MINOR_TICK = 70
    private const val MINUTE_HAND = 170
    private const val HOUR_HAND = 255

    override fun render(ctx: FaceContext): PixelGrid {
        val g = PixelGrid(ctx.size)
        val c = g.center
        val rim = c
        val large = ctx.isLarge

        for (k in 0 until 12) {
            val major = k % 3 == 0
            if (!large && !major) continue
            val a = Math.toRadians(k * 30.0 - 90.0)
            g.plot((c + cos(a) * rim).px(), (c + sin(a) * rim).px(), if (major) MAJOR_TICK else MINOR_TICK)
        }

        // Minute hand moves only on whole minutes so its anti-aliased edges never shimmer.
        val ma = Math.toRadians(ctx.minute * 6.0 - 90.0)
        val ml = if (large) 9.5 else 4.5
        Draw.wuLine(g, c, c, c + cos(ma) * ml, c + sin(ma) * ml, MINUTE_HAND)

        val ha = Math.toRadians(((ctx.hour % 12) + ctx.minute / 60.0) * 30.0 - 90.0)
        val hl = if (large) 6.0 else 3.0
        Draw.wuLine(g, c, c, c + cos(ha) * hl, c + sin(ha) * hl, HOUR_HAND)

        g.plot(c.px(), c.px(), HOUR_HAND)

        if (needsSecondTicks(ctx)) {
            val sa = Math.toRadians(ctx.second * 6.0 - 90.0)
            g.put((c + cos(sa) * rim).px(), (c + sin(sa) * rim).px(), 255)
        }
        return g
    }

    override fun needsSecondTicks(ctx: FaceContext): Boolean =
        ctx.isLarge && ctx.mode == Mode.ACTIVE && ctx.options.secondHand
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.render.DayLightTest" --tests "app.backlit.render.faces.AnalogFaceTest"
```

Expected: PASS. If a pixel assertion is off by one, print `AnalogFace.render(...).toAscii()` in the failing test to see the frame, and fix the face (not the test) unless the test's coordinate arithmetic is wrong.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/render/ app/src/test/java/app/backlit/render/
git commit -m "feat: face model, daylight window and analog face

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Sunrise / sunset maths

**Files:**
- Create: `app/src/main/java/app/backlit/data/SunTimes.kt`
- Test: `app/src/test/java/app/backlit/data/SunTimesTest.kt`

**Interfaces:**
- Consumes: `DayLight` (Task 3).
- Produces: `object SunTimes { fun compute(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): DayLight }`. Returns local minutes of the day, `DayLight.ALL_DAY` when the sun never sets, and `DayLight.ALL_NIGHT` when it never rises.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.data

import app.backlit.render.DayLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class SunTimesTest {

    private fun assertNear(label: String, expectedHhMm: String, actualMinute: Int) {
        val (h, m) = expectedHhMm.split(":").map { it.toInt() }
        val expected = h * 60 + m
        assertTrue("$label expected $expectedHhMm got ${actualMinute / 60}:${actualMinute % 60}",
            abs(expected - actualMinute) <= 3)
    }

    @Test
    fun londonSummerSolstice() {
        val d = SunTimes.compute(LocalDate.of(2026, 6, 21), 51.5074, -0.1278, ZoneId.of("Europe/London"))
        assertNear("rise", "04:43", d.riseMinute)
        assertNear("set", "21:21", d.setMinute)
    }

    @Test
    fun newYorkWinterSolstice() {
        val d = SunTimes.compute(LocalDate.of(2026, 12, 21), 40.7128, -74.0060, ZoneId.of("America/New_York"))
        assertNear("rise", "07:16", d.riseMinute)
        assertNear("set", "16:32", d.setMinute)
    }

    @Test
    fun singaporeEquinoxFarFromUtc() {
        val d = SunTimes.compute(LocalDate.of(2026, 3, 20), 1.3521, 103.8198, ZoneId.of("Asia/Singapore"))
        assertNear("rise", "07:09", d.riseMinute)
        assertNear("set", "19:15", d.setMinute)
    }

    @Test
    fun tromsoPolarDayAndNight() {
        val zone = ZoneId.of("Europe/Oslo")
        assertEquals(DayLight.ALL_DAY, SunTimes.compute(LocalDate.of(2026, 6, 21), 69.6492, 18.9553, zone))
        assertEquals(DayLight.ALL_NIGHT, SunTimes.compute(LocalDate.of(2026, 12, 21), 69.6492, 18.9553, zone))
    }
}
```

If a city's reference time looks wrong to you, check it on timeanddate.com for that exact date before changing either the code or the test.

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.data.SunTimesTest"
```

Expected: compilation FAIL, unresolved `SunTimes`.

- [ ] **Step 3: Implement `SunTimes`** (the NOAA / "Almanac for Computers" sunrise equation, accurate to about 1–2 minutes)

```kotlin
package app.backlit.data

import app.backlit.render.DayLight
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

object SunTimes {

    /** Official zenith for sunrise/sunset, including refraction and the sun's radius. */
    private const val ZENITH = 90.833

    private sealed interface Event {
        data class At(val utcHours: Double) : Event
        data object NeverRises : Event
        data object NeverSets : Event
    }

    fun compute(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): DayLight {
        val rise = event(date, lat, lon, rising = true)
        val set = event(date, lat, lon, rising = false)
        if (rise is Event.NeverRises || set is Event.NeverRises) return DayLight.ALL_NIGHT
        if (rise is Event.NeverSets || set is Event.NeverSets) return DayLight.ALL_DAY
        return DayLight(
            localMinute(date, (rise as Event.At).utcHours, zone),
            localMinute(date, (set as Event.At).utcHours, zone),
        )
    }

    private fun event(date: LocalDate, lat: Double, lon: Double, rising: Boolean): Event {
        val n = date.dayOfYear
        val lngHour = lon / 15.0
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        val l = norm360(m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634)
        var ra = norm360(atanD(0.91764 * tanD(l)))
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sinD(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosD(ZENITH) - sinDec * sinD(lat)) / (cosDec * cosD(lat))
        if (cosH > 1) return Event.NeverRises
        if (cosH < -1) return Event.NeverSets
        val h = (if (rising) 360.0 - acosD(cosH) else acosD(cosH)) / 15.0
        val localMeanTime = h + ra - 0.06571 * t - 6.622
        return Event.At(norm24(localMeanTime - lngHour))
    }

    private fun localMinute(date: LocalDate, utcHours: Double, zone: ZoneId): Int {
        val instant = date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds((utcHours * 3600).roundToLong())
        val t = instant.atZone(zone).toLocalTime()
        return t.hour * 60 + t.minute
    }

    private fun sinD(d: Double) = sin(Math.toRadians(d))
    private fun cosD(d: Double) = cos(Math.toRadians(d))
    private fun tanD(d: Double) = tan(Math.toRadians(d))
    private fun atanD(x: Double) = Math.toDegrees(atan(x))
    private fun acosD(x: Double) = Math.toDegrees(acos(x))
    private fun norm360(d: Double) = ((d % 360.0) + 360.0) % 360.0
    private fun norm24(h: Double) = ((h % 24.0) + 24.0) % 24.0
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.data.SunTimesTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/data/SunTimes.kt app/src/test/java/app/backlit/data/SunTimesTest.kt
git commit -m "feat: on-device NOAA sunrise and sunset calculation

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Day-ring face

**Files:**
- Create: `app/src/main/java/app/backlit/render/faces/DayRingFace.kt`
- Modify: `app/src/main/java/app/backlit/render/faces/Face.kt` (add to `Faces.all`)
- Test: `app/src/test/java/app/backlit/render/faces/DayRingFaceTest.kt`

**Interfaces:**
- Consumes: `PixelGrid`, `Draw`, `PixelFont`, `px()`, `FaceContext`, `DayLight`, `Face` (Tasks 1–3).
- Produces: `object DayRingFace : Face` with `id = "dayring"`, `label = "DAY RING"`, plus `fun hourText(hour: Int, use24h: Boolean): String` (two digits; 12-hour mode maps 0 → "12" and 13 → "01"). `Faces.all` becomes `listOf(AnalogFace, DayRingFace)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.render.faces

import app.backlit.render.DayLight
import app.backlit.render.FaceContext
import app.backlit.render.FaceOptions
import app.backlit.render.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DayRingFaceTest {

    private fun ctx(h: Int, m: Int, size: Int = 25, use24h: Boolean = true, day: DayLight = DayLight.FIXED) =
        FaceContext(h, m, 0, size, if (size == 13) Mode.AOD else Mode.ACTIVE, FaceOptions(use24h = use24h), day)

    @Test
    fun rimIsBrightByDayAndDimByNight25() {
        val g = DayRingFace.render(ctx(15, 0))
        assertEquals(110, g[12, 0])   // noon (top) is daylight
        assertEquals(22, g[12, 24])   // midnight (bottom) is night
        assertEquals(110, g[3, 4])    // about 08:46 on the rim — daylight
    }

    @Test
    fun timeDigits24h() {
        val g = DayRingFace.render(ctx(15, 7))
        assertEquals(230, g[5, 10])   // '1'
        assertEquals(0, g[4, 10])
        assertEquals(230, g[12, 11])  // colon
        assertEquals(230, g[12, 13])
        assertEquals(0, g[12, 12])
    }

    @Test
    fun timeDigits12h() {
        val g = DayRingFace.render(ctx(15, 7, use24h = false))
        assertEquals(230, g[4, 10])   // '0' of "03"
    }

    @Test
    fun twelveHourMidnightShowsTwelve() {
        assertEquals("12", DayRingFace.hourText(0, use24h = false))
        assertEquals("12", DayRingFace.hourText(12, use24h = false))
        assertEquals("01", DayRingFace.hourText(13, use24h = false))
        assertEquals("00", DayRingFace.hourText(0, use24h = true))
        assertEquals("23", DayRingFace.hourText(23, use24h = true))
    }

    @Test
    fun sunMarkerIsBrightDisc() {
        // 15:00 → marker up-right of centre at about (19.4, 4.6) on radius 10.5.
        val g = DayRingFace.render(ctx(15, 0))
        assertEquals(255, g[19, 5])
    }

    @Test
    fun allNightRingIsDim() {
        val g = DayRingFace.render(ctx(3, 0, day = DayLight.ALL_NIGHT))
        assertEquals(22, g[12, 0])
        assertEquals(22, g[3, 4])
    }

    @Test
    fun small13HasTopAndBottomGapsAndStackedDigits() {
        val g = DayRingFace.render(ctx(15, 7, size = 13))
        assertEquals(0, g[6, 0])      // top gap
        assertEquals(0, g[6, 12])     // bottom gap
        assertEquals(90, g[1, 3])     // left-upper ring, about 08:04 — daylight
        assertEquals(255, g[4, 1])    // '1' of HH, top row
        assertEquals(255, g[7, 1])    // '5' of HH
        assertEquals(170, g[3, 7])    // '0' of MM, dimmer
        assertEquals(170, g[7, 7])    // '7' of MM
        assertEquals(255, g[10, 2])   // marker at 15:00
    }

    @Test
    fun neverNeedsSecondTicks() {
        assertFalse(DayRingFace.needsSecondTicks(ctx(15, 0)))
    }

    @Test
    fun registryIncludesBothFaces() {
        assertEquals(listOf("analog", "dayring"), Faces.all.map { it.id })
        assertEquals(AnalogFace, Faces.next("dayring"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.render.faces.DayRingFaceTest"
```

Expected: compilation FAIL, unresolved `DayRingFace`.

- [ ] **Step 3: Implement `DayRingFace`**

```kotlin
package app.backlit.render.faces

import app.backlit.render.Draw
import app.backlit.render.FaceContext
import app.backlit.render.PixelFont
import app.backlit.render.PixelGrid
import app.backlit.render.px
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The rim is a 24-hour dial: midnight at the bottom, 06:00 left, noon top, 18:00 right.
 * Daylight hours are bright, night hours dim, the sun or moon sits on the rim, the time in the middle.
 */
object DayRingFace : Face {
    override val id = "dayring"
    override val label = "DAY RING"

    private const val BAND = 0.6

    fun hourText(hour: Int, use24h: Boolean): String {
        val h = if (use24h) hour else (hour % 12).let { if (it == 0) 12 else it }
        return h.toString().padStart(2, '0')
    }

    override fun render(ctx: FaceContext): PixelGrid {
        val g = PixelGrid(ctx.size)
        val large = ctx.isLarge
        val c = g.center
        val ringR = if (large) 11.5 else 5.6
        val dayB = if (large) 110 else 90
        val nightB = if (large) 22 else 18

        for (y in 0 until ctx.size) for (x in 0 until ctx.size) {
            if (!g.hasLed(x, y)) continue
            if (abs(hypot(x - c, y - c) - ringR) > BAND) continue
            if (!large && x in 2..10 && (y <= 1 || y >= 11)) continue   // room for the stacked digits
            var a = atan2(y - c, x - c) - PI / 2
            a = (a + 4 * PI) % (2 * PI)
            val minute = a / (2 * PI) * 1440.0
            g.plot(x, y, if (ctx.dayLight.isDay(minute)) dayB else nightB)
        }

        val hh = hourText(ctx.hour, ctx.options.use24h)
        val mm = ctx.minute.toString().padStart(2, '0')
        if (large) {
            PixelFont.text(g, "$hh:$mm", 4, 10, 230)
        } else {
            for (y in 1..11) for (x in 3..9) g.put(x, y, 0)
            PixelFont.digit(g, hh[0] - '0', 3, 1, 255)
            PixelFont.digit(g, hh[1] - '0', 7, 1, 255)
            PixelFont.digit(g, mm[0] - '0', 3, 7, 170)
            PixelFont.digit(g, mm[1] - '0', 7, 7, 170)
        }

        val markerR = if (large) ringR - 1 else ringR
        val ma = PI / 2 + ctx.minuteOfDay / 1440.0 * 2 * PI
        val mx = c + cos(ma) * markerR
        val my = c + sin(ma) * markerR
        val isDay = ctx.dayLight.isDay(ctx.minuteOfDay)
        when {
            !large -> g.put(mx.px(), my.px(), 255)
            isDay -> Draw.disc(g, mx, my, 1.5, 255)
            else -> Draw.crescent(g, mx, my, 170)
        }
        return g
    }

    override fun needsSecondTicks(ctx: FaceContext): Boolean = false
}
```

- [ ] **Step 4: Register the face**

In `app/src/main/java/app/backlit/render/faces/Face.kt` change:

```kotlin
    val all: List<Face> = listOf(AnalogFace)
```

to:

```kotlin
    val all: List<Face> = listOf(AnalogFace, DayRingFace)
```

- [ ] **Step 5: Run the whole render suite**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.render.*"
```

Expected: PASS (including `AnalogFaceTest.registryCyclesAndFallsBack`, which now cycles through two faces).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/backlit/render/faces/ app/src/test/java/app/backlit/render/faces/DayRingFaceTest.kt
git commit -m "feat: day-ring face with 13x13 stacked layout

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Pure runtime logic (settings model, daylight resolver, ticks, encoder, mode)

**Files:**
- Create: `app/src/main/java/app/backlit/data/Settings.kt`, `app/src/main/java/app/backlit/data/DayLightResolver.kt`, `app/src/main/java/app/backlit/glyph/TickSchedule.kt`, `app/src/main/java/app/backlit/glyph/FrameEncoder.kt`, `app/src/main/java/app/backlit/glyph/ModeTracker.kt`
- Test: `app/src/test/java/app/backlit/data/DayLightResolverTest.kt`, `app/src/test/java/app/backlit/glyph/TickScheduleTest.kt`, `app/src/test/java/app/backlit/glyph/FrameEncoderTest.kt`, `app/src/test/java/app/backlit/glyph/ModeTrackerTest.kt`

**Interfaces:**
- Consumes: `DayLight`, `SunTimes`, `PixelGrid`, `Mode`, `FaceOptions` (Tasks 1–4).
- Produces:
  - `enum class LocationMode { FIXED, APPROXIMATE, CITY }`
  - `data class Settings(val faceId: String = "analog", val secondHand: Boolean = true, val brightness: Int = 80, val use24h: Boolean = true, val locationMode: LocationMode = LocationMode.FIXED, val lat: Double? = null, val lon: Double? = null, val placeName: String? = null, val locationUpdatedAt: Long = 0L, val toyEverBound: Boolean = false) { val faceOptions: FaceOptions }`
  - `class DayLightResolver(compute: (LocalDate, Double, Double, ZoneId) -> DayLight = SunTimes::compute) { fun resolve(settings: Settings, date: LocalDate, zone: ZoneId): DayLight }`
  - `object TickSchedule { fun delayToNextTick(nowMillis: Long, perSecond: Boolean): Long }` (lands 20 ms after the boundary)
  - `object FrameEncoder { const val SDK_MAX = 2047; var minLit: Int /* default 300 */; const val AOD_FACTOR = 0.6; fun encode(grid: PixelGrid, brightnessPercent: Int, aod: Boolean): IntArray }`
  - `class ModeTracker(aodOnly: Boolean) { fun onAodEvent(nowMillis: Long); fun mode(nowMillis: Long): Mode }` (AOD if aodOnly, or if an `EVENT_AOD` arrived in the last 70 s)

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/app/backlit/data/DayLightResolverTest.kt`:

```kotlin
package app.backlit.data

import app.backlit.render.DayLight
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayLightResolverTest {

    private var calls = 0
    private val fake = { _: LocalDate, _: Double, _: Double, _: ZoneId -> calls++; DayLight(300 + calls, 1100) }
    private val located = Settings(locationMode = LocationMode.CITY, lat = 51.5, lon = -0.1)
    private val day1 = LocalDate.of(2026, 10, 3)
    private val london = ZoneId.of("Europe/London")

    @Test
    fun fixedModeIgnoresCoordinates() {
        val r = DayLightResolver(fake)
        assertEquals(DayLight.FIXED, r.resolve(located.copy(locationMode = LocationMode.FIXED), day1, london))
        assertEquals(0, calls)
    }

    @Test
    fun missingCoordinatesFallBackToFixed() {
        val r = DayLightResolver(fake)
        assertEquals(DayLight.FIXED, r.resolve(Settings(locationMode = LocationMode.APPROXIMATE), day1, london))
    }

    @Test
    fun cachesWithinTheSameDay() {
        val r = DayLightResolver(fake)
        r.resolve(located, day1, london)
        r.resolve(located, day1, london)
        assertEquals(1, calls)
    }

    @Test
    fun recomputesWhenDateChanges() {
        val r = DayLightResolver(fake)
        val a = r.resolve(located, day1, london)
        val b = r.resolve(located, day1.plusDays(1), london)
        assertEquals(2, calls)
        assertEquals(301, a.riseMinute)
        assertEquals(302, b.riseMinute)
    }

    @Test
    fun recomputesWhenZoneChanges() {
        val r = DayLightResolver(fake)
        r.resolve(located, day1, london)
        r.resolve(located, day1, ZoneId.of("Asia/Kolkata"))
        assertEquals(2, calls)
    }
}
```

`app/src/test/java/app/backlit/glyph/TickScheduleTest.kt`:

```kotlin
package app.backlit.glyph

import org.junit.Assert.assertEquals
import org.junit.Test

class TickScheduleTest {
    @Test
    fun perSecondLandsJustAfterNextSecond() {
        assertEquals(1020L, TickSchedule.delayToNextTick(5_000L, perSecond = true))
        assertEquals(270L, TickSchedule.delayToNextTick(5_750L, perSecond = true))
    }

    @Test
    fun perMinuteLandsJustAfterNextMinute() {
        assertEquals(60_020L, TickSchedule.delayToNextTick(120_000L, perSecond = false))
        assertEquals(1_020L, TickSchedule.delayToNextTick(179_000L, perSecond = false))
    }
}
```

`app/src/test/java/app/backlit/glyph/FrameEncoderTest.kt`:

```kotlin
package app.backlit.glyph

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameEncoderTest {

    private fun gridWith(vararg values: Pair<Int, Int>): PixelGrid =
        PixelGrid(13).apply { values.forEachIndexed { i, (_, v) -> plot(3 + i, 6, v) } }

    @Test
    fun zeroStaysOffAndFullIsSdkMax() {
        val g = PixelGrid(13).apply { plot(6, 6, 255) }
        val out = FrameEncoder.encode(g, brightnessPercent = 100, aod = false)
        assertEquals(169, out.size)
        assertEquals(2047, out[6 * 13 + 6])
        assertEquals(0, out[0])
        assertEquals(0, out[6 * 13 + 5])
    }

    @Test
    fun nonZeroNeverBelowMinLit() {
        val g = PixelGrid(13).apply { plot(6, 6, 1) }
        for (pct in listOf(10, 20, 50, 100)) for (aod in listOf(true, false)) {
            val v = FrameEncoder.encode(g, pct, aod)[6 * 13 + 6]
            assertTrue("pct=$pct aod=$aod v=$v", v >= FrameEncoder.minLit)
        }
    }

    @Test
    fun aodIsDimmerThanActive() {
        val g = PixelGrid(13).apply { plot(6, 6, 200) }
        val active = FrameEncoder.encode(g, 80, aod = false)[6 * 13 + 6]
        val aod = FrameEncoder.encode(g, 80, aod = true)[6 * 13 + 6]
        assertTrue(aod < active)
    }

    @Test
    fun monotonicInDesignBrightness() {
        val g = gridWith(0 to 20, 0 to 90, 0 to 170, 0 to 255)
        val out = FrameEncoder.encode(g, 80, aod = false)
        val row = (3..6).map { out[6 * 13 + it] }
        assertEquals(row.sorted(), row)
        assertTrue(row.toSet().size == 4)
    }

    @Test
    fun brightnessIsClampedToTenToHundred() {
        val g = PixelGrid(13).apply { plot(6, 6, 255) }
        assertEquals(FrameEncoder.encode(g, 100, false)[84], FrameEncoder.encode(g, 400, false)[84])
        assertEquals(FrameEncoder.encode(g, 10, false)[84], FrameEncoder.encode(g, -5, false)[84])
    }
}
```

`app/src/test/java/app/backlit/glyph/ModeTrackerTest.kt`:

```kotlin
package app.backlit.glyph

import app.backlit.render.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class ModeTrackerTest {
    @Test
    fun aodOnlyDevicesAreAlwaysAod() {
        assertEquals(Mode.AOD, ModeTracker(aodOnly = true).mode(0L))
    }

    @Test
    fun activeUntilAnAodEventThenBackAfterSeventySeconds() {
        val t = ModeTracker(aodOnly = false)
        assertEquals(Mode.ACTIVE, t.mode(1_000L))
        t.onAodEvent(10_000L)
        assertEquals(Mode.AOD, t.mode(10_001L))
        assertEquals(Mode.AOD, t.mode(79_999L))
        assertEquals(Mode.ACTIVE, t.mode(80_001L))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.data.DayLightResolverTest" --tests "app.backlit.glyph.*"
```

Expected: compilation FAIL on the unresolved new types.

- [ ] **Step 3: Implement the five files**

`app/src/main/java/app/backlit/data/Settings.kt`:

```kotlin
package app.backlit.data

import app.backlit.render.FaceOptions

enum class LocationMode { FIXED, APPROXIMATE, CITY }

data class Settings(
    val faceId: String = "analog",
    val secondHand: Boolean = true,
    val brightness: Int = 80,
    val use24h: Boolean = true,
    val locationMode: LocationMode = LocationMode.FIXED,
    val lat: Double? = null,
    val lon: Double? = null,
    val placeName: String? = null,
    val locationUpdatedAt: Long = 0L,
    val toyEverBound: Boolean = false,
) {
    val faceOptions: FaceOptions get() = FaceOptions(secondHand = secondHand, use24h = use24h)
}
```

`app/src/main/java/app/backlit/data/DayLightResolver.kt`:

```kotlin
package app.backlit.data

import app.backlit.render.DayLight
import java.time.LocalDate
import java.time.ZoneId

/** Settings + date + zone → today's daylight window, cached until any input changes. */
class DayLightResolver(
    private val compute: (LocalDate, Double, Double, ZoneId) -> DayLight = SunTimes::compute,
) {
    private data class Key(val date: LocalDate, val lat: Double, val lon: Double, val zone: ZoneId)

    private var key: Key? = null
    private var cached: DayLight = DayLight.FIXED

    fun resolve(settings: Settings, date: LocalDate, zone: ZoneId): DayLight {
        val lat = settings.lat
        val lon = settings.lon
        if (settings.locationMode == LocationMode.FIXED || lat == null || lon == null) return DayLight.FIXED
        val k = Key(date, lat, lon, zone)
        if (k != key) {
            cached = compute(date, lat, lon, zone)
            key = k
        }
        return cached
    }
}
```

`app/src/main/java/app/backlit/glyph/TickSchedule.kt`:

```kotlin
package app.backlit.glyph

object TickSchedule {
    private const val LANDING_MS = 20L

    /** Milliseconds until just after the next wall-clock second (or minute). */
    fun delayToNextTick(nowMillis: Long, perSecond: Boolean): Long {
        val period = if (perSecond) 1_000L else 60_000L
        return period - (nowMillis % period) + LANDING_MS
    }
}
```

`app/src/main/java/app/backlit/glyph/FrameEncoder.kt`:

```kotlin
package app.backlit.glyph

import app.backlit.render.PixelGrid
import kotlin.math.roundToInt

/**
 * Design brightness (0–255) → the SDK's raw frame values (0–2047).
 * LEDs near the bottom of their range read as off, so every lit pixel is lifted to at least [minLit].
 * [minLit] is calibrated on a real Phone (3) in Task 12.
 */
object FrameEncoder {
    const val SDK_MAX = 2047
    const val AOD_FACTOR = 0.6
    var minLit: Int = 300

    fun encode(grid: PixelGrid, brightnessPercent: Int, aod: Boolean): IntArray {
        val scale = brightnessPercent.coerceIn(10, 100) / 100.0 * (if (aod) AOD_FACTOR else 1.0)
        val raw = grid.raw()
        return IntArray(raw.size) { i ->
            val v = raw[i]
            if (v == 0) 0
            else (minLit + (SDK_MAX - minLit) * (v / 255.0) * scale).roundToInt().coerceIn(minLit, SDK_MAX)
        }
    }
}
```

`app/src/main/java/app/backlit/glyph/ModeTracker.kt`:

```kotlin
package app.backlit.glyph

import app.backlit.render.Mode

/**
 * The SDK does not say whether a toy is showing in the carousel or as the always-on toy.
 * EVENT_AOD arrives once a minute only in AOD, so a recent EVENT_AOD means AOD.
 */
class ModeTracker(private val aodOnly: Boolean) {
    private var lastAodAt: Long = -1L

    fun onAodEvent(nowMillis: Long) {
        lastAodAt = nowMillis
    }

    fun mode(nowMillis: Long): Mode = when {
        aodOnly -> Mode.AOD
        lastAodAt >= 0 && nowMillis - lastAodAt < 70_000L -> Mode.AOD
        else -> Mode.ACTIVE
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.data.DayLightResolverTest" --tests "app.backlit.glyph.*"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/data/ app/src/main/java/app/backlit/glyph/ app/src/test/java/app/backlit/data/DayLightResolverTest.kt app/src/test/java/app/backlit/glyph/
git commit -m "feat: settings model, daylight resolver, tick schedule, frame encoder, mode tracker

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Settings persistence (DataStore)

**Files:**
- Create: `app/src/main/java/app/backlit/data/SettingsRepo.kt`
- Test: `app/src/test/java/app/backlit/data/SettingsRepoTest.kt`

**Interfaces:**
- Consumes: `Settings`, `LocationMode` (Task 6).
- Produces:
  - `val Context.settingsDataStore: DataStore<Preferences>` (single file `settings`)
  - `class SettingsRepo(store: DataStore<Preferences>) { val settings: Flow<Settings>; suspend fun update(transform: (Settings) -> Settings); companion object { fun get(context: Context): SettingsRepo } }`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.backlit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepoTest {

    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun repo() = SettingsRepo(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { tmp.root.resolve("s.preferences_pb") })
    )

    @After fun tearDown() = scope.cancel()

    @Test
    fun defaultsWhenEmpty() = runBlocking {
        assertEquals(Settings(), repo().settings.first())
    }

    @Test
    fun roundTripsEveryField() = runBlocking {
        val r = repo()
        val s = Settings(
            faceId = "dayring", secondHand = false, brightness = 40, use24h = false,
            locationMode = LocationMode.CITY, lat = 12.97, lon = 77.59, placeName = "Bengaluru, IN",
            locationUpdatedAt = 123L, toyEverBound = true,
        )
        r.update { s }
        assertEquals(s, r.settings.first())
    }

    @Test
    fun clearingCoordinatesRemovesThem() = runBlocking {
        val r = repo()
        r.update { it.copy(lat = 1.0, lon = 2.0) }
        r.update { it.copy(lat = null, lon = null) }
        val s = r.settings.first()
        assertNull(s.lat)
        assertNull(s.lon)
    }

    @Test
    fun unknownLocationModeFallsBackToFixed() = runBlocking {
        val r = repo()
        r.update { it.copy(locationMode = LocationMode.APPROXIMATE) }
        assertEquals(LocationMode.APPROXIMATE, r.settings.first().locationMode)
        assertEquals(LocationMode.FIXED, SettingsRepo.parseMode("NOT_A_MODE"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.data.SettingsRepoTest"
```

Expected: compilation FAIL, unresolved `SettingsRepo`.

- [ ] **Step 3: Implement `SettingsRepo`**

```kotlin
package app.backlit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepo(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    companion object {
        private val FACE = stringPreferencesKey("face_id")
        private val SECOND = booleanPreferencesKey("second_hand")
        private val BRIGHTNESS = intPreferencesKey("brightness")
        private val USE24 = booleanPreferencesKey("use_24h")
        private val LOC_MODE = stringPreferencesKey("location_mode")
        private val LAT = doublePreferencesKey("lat")
        private val LON = doublePreferencesKey("lon")
        private val PLACE = stringPreferencesKey("place_name")
        private val LOC_AT = longPreferencesKey("location_updated_at")
        private val BOUND = booleanPreferencesKey("toy_ever_bound")

        fun get(context: Context): SettingsRepo = SettingsRepo(context.applicationContext.settingsDataStore)

        fun parseMode(s: String?): LocationMode =
            LocationMode.entries.firstOrNull { it.name == s } ?: LocationMode.FIXED

        private fun Preferences.toSettings(): Settings {
            val d = Settings()
            return Settings(
                faceId = this[FACE] ?: d.faceId,
                secondHand = this[SECOND] ?: d.secondHand,
                brightness = this[BRIGHTNESS] ?: d.brightness,
                use24h = this[USE24] ?: d.use24h,
                locationMode = parseMode(this[LOC_MODE]),
                lat = this[LAT],
                lon = this[LON],
                placeName = this[PLACE],
                locationUpdatedAt = this[LOC_AT] ?: d.locationUpdatedAt,
                toyEverBound = this[BOUND] ?: d.toyEverBound,
            )
        }

        private fun MutablePreferences.write(s: Settings) {
            this[FACE] = s.faceId
            this[SECOND] = s.secondHand
            this[BRIGHTNESS] = s.brightness
            this[USE24] = s.use24h
            this[LOC_MODE] = s.locationMode.name
            if (s.lat != null) this[LAT] = s.lat else remove(LAT)
            if (s.lon != null) this[LON] = s.lon else remove(LON)
            if (s.placeName != null) this[PLACE] = s.placeName else remove(PLACE)
            this[LOC_AT] = s.locationUpdatedAt
            this[BOUND] = s.toyEverBound
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.data.SettingsRepoTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/data/SettingsRepo.kt app/src/test/java/app/backlit/data/SettingsRepoTest.kt
git commit -m "feat: DataStore-backed settings repository

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Glyph Toy service

**Files:**
- Create: `app/src/main/java/app/backlit/glyph/DeviceProfile.kt`, `app/src/main/java/app/backlit/glyph/GlyphOutput.kt`, `app/src/main/java/app/backlit/glyph/ClockToyService.kt`, `app/src/main/res/drawable/ic_toy_preview.xml`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `Faces`, `FaceContext`, `Mode`, `PixelGrid` (Tasks 3, 5); `SettingsRepo`, `Settings`, `DayLightResolver` (Tasks 6–7); `TickSchedule`, `FrameEncoder`, `ModeTracker` (Task 6).
- Produces:
  - `enum class DeviceProfile(val size: Int, val aodOnly: Boolean, val label: String) { PHONE_3, PHONE_4A_PRO, UNSUPPORTED; val sdkDevice: String?; companion object { fun detect(): DeviceProfile } }`
  - `class GlyphOutput(context: Context, profile: DeviceProfile, onReady: () -> Unit) { fun connect(); fun push(frame: IntArray); fun close() }`
  - `class ClockToyService : Service` declared in the manifest as a Glyph Toy.

The SDK cannot run on the JVM or an emulator. This task is verified by compiling it and running it on the Phone (3).

- [ ] **Step 1: Implement `DeviceProfile`**

```kotlin
package app.backlit.glyph

import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph

enum class DeviceProfile(val size: Int, val aodOnly: Boolean, val label: String) {
    PHONE_3(25, aodOnly = false, label = "PHONE (3)"),
    PHONE_4A_PRO(13, aodOnly = true, label = "PHONE (4A) PRO"),
    UNSUPPORTED(25, aodOnly = false, label = "PREVIEW");

    val sdkDevice: String?
        get() = when (this) {
            PHONE_3 -> Glyph.DEVICE_23112
            PHONE_4A_PRO -> Glyph.DEVICE_25111p
            UNSUPPORTED -> null
        }

    companion object {
        fun detect(): DeviceProfile = runCatching {
            when {
                Common.is23112() -> PHONE_3
                Common.is25111p() -> PHONE_4A_PRO
                else -> UNSUPPORTED
            }
        }.getOrDefault(UNSUPPORTED)
    }
}
```

- [ ] **Step 2: Implement `GlyphOutput`** (connect, retry 1 s / 2 s / 4 s, skip duplicate frames, never throw)

```kotlin
package app.backlit.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nothing.ketchum.GlyphMatrixManager

class GlyphOutput(
    private val context: Context,
    private val profile: DeviceProfile,
    private val onReady: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var manager: GlyphMatrixManager? = null
    private var ready = false
    private var closed = false
    private var attempt = 0
    private var last: IntArray? = null

    fun connect() {
        val device = profile.sdkDevice ?: return
        closed = false
        runCatching {
            val m = GlyphMatrixManager.getInstance(context.applicationContext)
            manager = m
            m.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName?) {
                    if (closed) return
                    runCatching { m.register(device) }
                        .onSuccess {
                            ready = true
                            attempt = 0
                            last = null
                            onReady()
                        }
                        .onFailure { Log.w(TAG, "register failed", it); retry() }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    ready = false
                    last = null
                    if (!closed) retry()
                }
            })
        }.onFailure { Log.w(TAG, "init failed", it); retry() }
    }

    private fun retry() {
        if (closed || attempt >= RETRY_DELAYS_MS.size) {
            Log.w(TAG, "giving up after $attempt retries")
            return
        }
        val delay = RETRY_DELAYS_MS[attempt++]
        handler.postDelayed({
            runCatching { manager?.unInit() }
            connect()
        }, delay)
    }

    fun push(frame: IntArray) {
        if (!ready) return
        if (last?.contentEquals(frame) == true) return
        runCatching { manager?.setMatrixFrame(frame) }
            .onSuccess { last = frame }
            .onFailure { Log.w(TAG, "setMatrixFrame failed", it) }
    }

    fun close() {
        closed = true
        ready = false
        last = null
        handler.removeCallbacksAndMessages(null)
        runCatching { manager?.unInit() }
        manager = null
    }

    private companion object {
        const val TAG = "BacklitGlyph"
        val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L)
    }
}
```

- [ ] **Step 3: Implement `ClockToyService`**

```kotlin
package app.backlit.glyph

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import app.backlit.data.DayLightResolver
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.render.FaceContext
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
import app.backlit.render.faces.Faces
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

class ClockToyService : Service() {

    private var scope: CoroutineScope? = null
    private var tickJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private val resolver = DayLightResolver()
    private var settings = Settings()

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> scope?.launch {
                    repo.update { it.copy(faceId = Faces.next(it.faceId).id) }
                }
                GlyphToy.EVENT_AOD -> {
                    modes.onAodEvent(System.currentTimeMillis())
                    draw()
                }
            }
        }
    }
    private val messenger = Messenger(handler)

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            draw()
            restartTicker()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s

        if (profile != DeviceProfile.UNSUPPORTED) {
            output = GlyphOutput(this, profile) { draw(); restartTicker() }.also { it.connect() }
            s.launch {
                repo.update { if (it.toyEverBound) it else it.copy(toyEverBound = true) }
            }
            s.launch {
                repo.settings.collect {
                    settings = it
                    draw()
                    restartTicker()
                }
            }
            registerReceiver(timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }, Context.RECEIVER_NOT_EXPORTED)
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { unregisterReceiver(timeReceiver) }
        tickJob?.cancel()
        scope?.cancel()
        scope = null
        output?.close()
        output = null
        return false
    }

    private fun context(now: LocalDateTime = LocalDateTime.now()): FaceContext = FaceContext(
        hour = now.hour,
        minute = now.minute,
        second = now.second,
        size = profile.size,
        mode = modes.mode(System.currentTimeMillis()),
        options = settings.faceOptions,
        dayLight = resolver.resolve(settings, now.toLocalDate(), ZoneId.systemDefault()),
    )

    private fun draw() {
        val out = output ?: return
        val ctx = context()
        val face = Faces.byId(settings.faceId)
        val grid = runCatching { face.render(ctx) }
            .getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
        out.push(FrameEncoder.encode(grid, settings.brightness, aod = ctx.mode == Mode.AOD))
    }

    private fun restartTicker() {
        val s = scope ?: return
        tickJob?.cancel()
        tickJob = s.launch {
            while (isActive) {
                val perSecond = Faces.byId(settings.faceId).needsSecondTicks(context())
                delay(TickSchedule.delayToNextTick(System.currentTimeMillis(), perSecond))
                draw()
            }
        }
    }

    private companion object {
        const val TAG = "BacklitToy"
    }
}
```

- [ ] **Step 4: Add the toy preview drawable**

`app/src/main/res/drawable/ic_toy_preview.xml`:

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
        android:strokeWidth="7"
        android:pathData="M48,48L48,24M48,48L64,58" />
</vector>
```

- [ ] **Step 5: Register the toy in the manifest and add strings**

Replace `app/src/main/AndroidManifest.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="com.nothing.ketchum.permission.ENABLE" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true">

        <meta-data
            android:name="NothingKey"
            android:value="test" />

        <service
            android:name=".glyph.ClockToyService"
            android:exported="true"
            tools:ignore="ExportedService">
            <intent-filter>
                <action android:name="com.nothing.glyph.TOY" />
            </intent-filter>
            <meta-data
                android:name="com.nothing.glyph.toy.name"
                android:resource="@string/toy_name" />
            <meta-data
                android:name="com.nothing.glyph.toy.image"
                android:resource="@drawable/ic_toy_preview" />
            <meta-data
                android:name="com.nothing.glyph.toy.summary"
                android:resource="@string/toy_summary" />
            <meta-data
                android:name="com.nothing.glyph.toy.longpress"
                android:value="1" />
            <meta-data
                android:name="com.nothing.glyph.toy.aod_support"
                android:value="1" />
        </service>

    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Backlit</string>
    <string name="toy_name">Backlit Clock</string>
    <string name="toy_summary">Analog and day-ring clocks. Long press to change face.</string>
</resources>
```

- [ ] **Step 6: Build and run all unit tests**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If the compiler rejects a Nothing SDK symbol (for example `Common.is23112` or `GlyphToy.EVENT_AOD`), list the AAR's classes with `unzip -p libs/glyph-matrix-sdk-2.0.aar classes.jar > /tmp/g.jar && unzip -l /tmp/g.jar`, then inspect the class with `javap -cp /tmp/g.jar com.nothing.ketchum.Common`, and use the real name.

- [ ] **Step 7 (human, Phone (3) connected with USB debugging): Install and smoke-test the toy**

```bash
./gradlew :app:installDebug
```

Then on the phone: Settings → Glyph Interface → Glyph Toys (the exact path may differ slightly by OS version; record the real one for Task 10's setup text). Enable **Backlit Clock**, press the Glyph Button until it shows, and check: the analog face appears; the second dot moves; a long press switches to Day ring and back. Watch the logs while doing this:

```bash
adb logcat -s BacklitGlyph BacklitToy
```

Expected: no `render failed` or `giving up` lines.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/backlit/glyph/ app/src/main/AndroidManifest.xml app/src/main/res/
git commit -m "feat: Backlit Clock Glyph Toy service with retrying SDK output

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Theme, live preview and Home screen

**Files:**
- Create: `app/src/main/java/app/backlit/ui/Theme.kt`, `app/src/main/java/app/backlit/ui/Components.kt`, `app/src/main/java/app/backlit/ui/MatrixPreview.kt`, `app/src/main/java/app/backlit/ui/HomeScreen.kt`, `app/src/main/java/app/backlit/MainActivity.kt`, `app/src/main/res/values/themes.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/res/mipmap-anydpi/ic_launcher.xml`, `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `PixelGrid`, `Faces`, `FaceContext`, `Mode` (render); `Settings`, `SettingsRepo`, `DayLightResolver`, `LocationMode` (data); `DeviceProfile`, `TickSchedule` (glyph).
- Produces:
  - `object BacklitColors { Black, White, Dim, Line, Red, LedOff }`, `val Doto: FontFamily`, `val SpaceGrotesk: FontFamily`, `@Composable fun BacklitTheme(content: @Composable () -> Unit)`
  - `@Composable fun DashedDivider()`, `@Composable fun SquareChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)`, `@Composable fun SettingRow(label: String, value: String, onClick: (() -> Unit)? = null)`, `@Composable fun ScreenHeader(title: String, onBack: (() -> Unit)?)`
  - `@Composable fun MatrixPreview(grid: PixelGrid, modifier: Modifier = Modifier)`
  - `enum class Screen { HOME, SETUP, LOCATION, ABOUT }`
  - `@Composable fun HomeScreen(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, onNavigate: (Screen) -> Unit)`
  - `MainActivity` hosting the screens (Setup/Location/About are placeholders until Tasks 10–11)

- [ ] **Step 1: Theme**

`app/src/main/java/app/backlit/ui/Theme.kt`:

```kotlin
package app.backlit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.backlit.R

object BacklitColors {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Dim = Color(0xFF888888)
    val Line = Color(0xFF333333)
    val Red = Color(0xFFD71921)
    val LedOff = Color(0xFF1C1C1C)
}

@OptIn(ExperimentalTextApi::class)
val Doto = FontFamily(
    Font(R.font.doto, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

@OptIn(ExperimentalTextApi::class)
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.space_grotesk, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontFamily = Doto, fontWeight = FontWeight.Black, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = SpaceGrotesk, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = SpaceGrotesk, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = SpaceGrotesk, fontSize = 11.sp, letterSpacing = 2.sp),
)

@Composable
fun BacklitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BacklitColors.Black,
            surface = BacklitColors.Black,
            primary = BacklitColors.White,
            onPrimary = BacklitColors.Black,
            onBackground = BacklitColors.White,
            onSurface = BacklitColors.White,
        ),
        typography = typography,
        content = content,
    )
}
```

- [ ] **Step 2: Shared components**

`app/src/main/java/app/backlit/ui/Components.kt`:

```kotlin
package app.backlit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DashedDivider() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = BacklitColors.Line,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
    }
}

@Composable
fun SquareChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .border(1.dp, if (selected) BacklitColors.White else BacklitColors.Line, shape)
            .background(if (selected) BacklitColors.White else BacklitColors.Black, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) BacklitColors.Black else BacklitColors.White,
        )
    }
}

@Composable
fun SettingRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    DashedDivider()
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 14.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Text("←", style = MaterialTheme.typography.displaySmall, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
        }
        Text(title, style = MaterialTheme.typography.displaySmall)
    }
}
```

- [ ] **Step 3: Matrix preview**

`app/src/main/java/app/backlit/ui/MatrixPreview.kt`:

```kotlin
package app.backlit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import app.backlit.render.PixelGrid

/** Draws a frame exactly as the panel shows it: one round LED per position. */
@Composable
fun MatrixPreview(grid: PixelGrid, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val cell = size.width / grid.size
        for (y in 0 until grid.size) for (x in 0 until grid.size) {
            if (!grid.hasLed(x, y)) continue
            val v = grid[x, y]
            val color = if (v == 0) BacklitColors.LedOff else BacklitColors.White.copy(alpha = 0.1f + 0.9f * v / 255f)
            drawCircle(color, radius = cell * 0.38f, center = Offset(x * cell + cell / 2, y * cell + cell / 2))
        }
    }
}
```

- [ ] **Step 4: Home screen**

`app/src/main/java/app/backlit/ui/HomeScreen.kt`:

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
        SettingRow("Time format", if (settings.use24h) "24H" else "12H") {
            onUpdate { it.copy(use24h = !it.use24h) }
        }
        if (face.id == DayRingFace.id) {
            val where = when (settings.locationMode) {
                LocationMode.FIXED -> "06–18"
                else -> settings.placeName ?: "—"
            }
            SettingRow("Sun times", "$where →") { onNavigate(Screen.LOCATION) }
        }
        BrightnessRow(settings.brightness) { pct -> onUpdate { it.copy(brightness = pct) } }
        SettingRow("Glyph Toy setup", "→") { onNavigate(Screen.SETUP) }
        SettingRow("About", "→") { onNavigate(Screen.ABOUT) }
        DashedDivider()
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun BrightnessRow(value: Int, onChange: (Int) -> Unit) {
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

- [ ] **Step 5: MainActivity with placeholder screens**

`app/src/main/java/app/backlit/MainActivity.kt`:

```kotlin
package app.backlit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.glyph.DeviceProfile
import app.backlit.ui.BacklitColors
import app.backlit.ui.BacklitTheme
import app.backlit.ui.HomeScreen
import app.backlit.ui.Screen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = SettingsRepo.get(this)
        val profile = DeviceProfile.detect()

        setContent {
            BacklitTheme {
                val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
                var screen by rememberSaveable { mutableStateOf<Screen?>(null) }
                val scope = rememberCoroutineScope()
                val update: ((Settings) -> Settings) -> Unit = { t -> scope.launch { repo.update(t) } }

                Box(Modifier.fillMaxSize().background(BacklitColors.Black).safeDrawingPadding()) {
                    val s = settings ?: return@Box
                    LaunchedEffect(Unit) {
                        if (screen == null) {
                            screen = if (s.toyEverBound || profile == DeviceProfile.UNSUPPORTED) Screen.HOME else Screen.SETUP
                        }
                    }
                    BackHandler(enabled = screen != Screen.HOME && screen != null) { screen = Screen.HOME }
                    when (screen) {
                        null, Screen.HOME -> HomeScreen(s, profile, update) { screen = it }
                        else -> Text("${screen} — coming in Tasks 10–11", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Launcher icon, theme, manifest activity**

`app/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="black">#FF000000</color>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.Backlit" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/black</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml` (dot ring with hands, centred in the 108-unit adaptive safe zone):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="4"
        android:pathData="M54,30a24,24 0,1 1,0 48a24,24 0,1 1,0 -48" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFFFF"
        android:strokeLineCap="round"
        android:strokeWidth="5"
        android:pathData="M54,54L54,40M54,54L63,60" />
    <path
        android:fillColor="#FFD71921"
        android:pathData="M54,31.5a2.5,2.5 0,1 1,0 5a2.5,2.5 0,1 1,0 -5" />
</vector>
```

`app/src/main/res/mipmap-anydpi/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/black" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

In `app/src/main/AndroidManifest.xml`, change the `<application ...>` opening tag to:

```xml
    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Backlit">
```

and add the activity just before `<service`:

```xml
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
```

- [ ] **Step 7: Build, test, run**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

Human, on the Phone (3):

```bash
./gradlew :app:installDebug && adb shell am start -n app.backlit/.MainActivity
```

Check: black UI with the dot-matrix title; the preview ticks every second; tapping `25×25 / 13×13` switches the preview; the face chips change the face both in the preview **and on the physical matrix** while the toy is showing; Second hand / Time format / Brightness change both too.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/backlit/ui/ app/src/main/java/app/backlit/MainActivity.kt app/src/main/res/ app/src/main/AndroidManifest.xml
git commit -m "feat: dot-matrix theme, live matrix preview and home screen

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Setup and About screens

**Files:**
- Create: `app/src/main/java/app/backlit/glyph/ToysManager.kt`, `app/src/main/java/app/backlit/ui/SetupScreen.kt`, `app/src/main/java/app/backlit/ui/AboutScreen.kt`
- Modify: `app/src/main/java/app/backlit/MainActivity.kt`, `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ScreenHeader`, `SettingRow`, `DashedDivider`, `SquareChip`, `BacklitColors` (Task 9).
- Produces:
  - `object ToysManager { fun canOpen(context: Context): Boolean; fun open(context: Context): Boolean }`
  - `@Composable fun SetupScreen(onDone: () -> Unit)`
  - `@Composable fun AboutScreen(onBack: () -> Unit)`

- [ ] **Step 1: `ToysManager`**

```kotlin
package app.backlit.glyph

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Nothing's own Glyph Toys manager (newer system versions only). */
object ToysManager {
    private fun intent() = Intent()
        .setComponent(ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canOpen(context: Context): Boolean = intent().resolveActivity(context.packageManager) != null

    fun open(context: Context): Boolean = try {
        context.startActivity(intent())
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
```

Add package visibility for that app in `AndroidManifest.xml`, directly inside `<manifest>` after the `uses-permission` line:

```xml
    <queries>
        <package android:name="com.nothing.thirdparty" />
    </queries>
```

- [ ] **Step 2: `SetupScreen`** (use the menu path recorded in Task 8 Step 7 if it differs from the text below)

```kotlin
package app.backlit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.glyph.ToysManager

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val canOpen = remember { ToysManager.canOpen(context) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("SET UP", onBack = onDone)
        Text("Turn on the Backlit Clock toy:", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        listOf(
            "01" to "Open Settings → Glyph Interface → Glyph Toys.",
            "02" to "Find \"Backlit Clock\" and switch it on.",
            "03" to "Phone (3): press the Glyph Button on the back until the clock shows. Long press to change face.",
            "04" to "Phone (4a) Pro: choose Backlit Clock as the always-on toy.",
        ).forEach { (n, step) ->
            DashedDivider()
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(n, style = MaterialTheme.typography.titleMedium)
                Text(step, style = MaterialTheme.typography.bodyLarge)
            }
        }
        DashedDivider()
        Spacer(Modifier.height(20.dp))
        if (canOpen) {
            SquareChip("OPEN GLYPH TOYS", selected = true, onClick = { ToysManager.open(context) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        SquareChip("DONE", selected = false, onClick = onDone, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
    }
}
```

- [ ] **Step 3: `AboutScreen`**

```kotlin
package app.backlit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("ABOUT", onBack = onBack)
        SettingRow("Version", version)
        DashedDivider()
        Text(
            "Backlit collects no data. Everything, including your location if you share it, stays on your phone. " +
                "The app makes no network requests.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 14.dp),
        )
        DashedDivider()
        Text("LICENSES", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        listOf(
            "Doto — SIL Open Font License 1.1",
            "Space Grotesk — SIL Open Font License 1.1",
            "City data — GeoNames (geonames.org), CC BY 4.0",
            "Glyph Matrix SDK — Nothing Technology Ltd.",
        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp)) }
        Spacer(Modifier.height(32.dp))
    }
}
```

- [ ] **Step 4: Wire the screens into `MainActivity`**

Replace the `when (screen)` block in `MainActivity.kt` with:

```kotlin
                    when (screen) {
                        null, Screen.HOME -> HomeScreen(s, profile, update) { screen = it }
                        Screen.SETUP -> SetupScreen(onDone = { screen = Screen.HOME })
                        Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.HOME })
                        Screen.LOCATION -> Text("LOCATION — coming in Task 11", modifier = Modifier.padding(16.dp))
                    }
```

and add the imports:

```kotlin
import app.backlit.ui.AboutScreen
import app.backlit.ui.SetupScreen
```

- [ ] **Step 5: Build, test, run**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Human, on the Phone (3): `./gradlew :app:installDebug`, then check that "OPEN GLYPH TOYS" opens Nothing's toy manager, DONE and the system back gesture return Home, and About shows the version and licences.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/backlit/ app/src/main/AndroidManifest.xml
git commit -m "feat: setup and about screens with toy manager shortcut

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Location for sun times (approximate, city, fixed)

**Files:**
- Create: `app/src/main/java/app/backlit/data/Cities.kt`, `app/src/main/java/app/backlit/data/LocationSource.kt`, `app/src/main/java/app/backlit/data/LocationRefresher.kt`, `app/src/main/java/app/backlit/ui/LocationScreen.kt`, `app/src/main/assets/cities.tsv`, `scripts/build_cities.py`
- Modify: `app/src/main/java/app/backlit/MainActivity.kt`, `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/app/backlit/data/CitiesTest.kt`

**Interfaces:**
- Consumes: `Settings`, `LocationMode`, `SettingsRepo` (Tasks 6–7); UI components (Task 9).
- Produces:
  - `data class City(val name: String, val country: String, val lat: Double, val lon: Double) { val display: String }`
  - `object Cities { fun parse(lines: Sequence<String>): List<City>; fun search(all: List<City>, query: String, limit: Int = 20): List<City>; fun load(assets: AssetManager): List<City> }`
  - `class LocationSource(context: Context) { fun hasPermission(): Boolean; suspend fun current(): Coords? }`, with `data class Coords(val lat: Double, val lon: Double)` and `fun roundCoord(v: Double): Double` (2 decimal places, about 1 km)
  - `object LocationRefresher { suspend fun refreshIfStale(context: Context, repo: SettingsRepo, settings: Settings, nowMillis: Long) }`
  - `@Composable fun LocationScreen(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit, onBack: () -> Unit)`

- [ ] **Step 1 (human approves download): Build the bundled city list from GeoNames**

`scripts/build_cities.py`:

```python
"""Builds app/src/main/assets/cities.tsv from GeoNames cities15000.txt (CC BY 4.0).

Usage: python3 scripts/build_cities.py /path/to/cities15000.txt
Keeps cities with population >= 300,000. Output columns: name, country code, lat, lon.
"""
import sys

MIN_POP = 300_000
src = sys.argv[1]
rows = []
with open(src, encoding="utf-8") as f:
    for line in f:
        p = line.rstrip("\n").split("\t")
        name, lat, lon, country, pop = p[1], float(p[4]), float(p[5]), p[8], int(p[14] or 0)
        if pop >= MIN_POP:
            rows.append((pop, name, country, round(lat, 4), round(lon, 4)))
rows.sort(reverse=True)
with open("app/src/main/assets/cities.tsv", "w", encoding="utf-8") as out:
    for _, name, country, lat, lon in rows:
        out.write(f"{name}\t{country}\t{lat}\t{lon}\n")
print(f"wrote {len(rows)} cities")
```

Run it after downloading (human approves the download):

```bash
mkdir -p app/src/main/assets && curl -L -o /tmp/cities15000.zip https://download.geonames.org/export/dump/cities15000.zip && unzip -o /tmp/cities15000.zip -d /tmp && python3 scripts/build_cities.py /tmp/cities15000.txt
```

Expected: `wrote N cities` with N roughly between 1,000 and 2,000, and the asset file is under 100 KB (`ls -lh app/src/main/assets/cities.tsv`).

- [ ] **Step 2: Write the failing test**

`app/src/test/java/app/backlit/data/CitiesTest.kt`:

```kotlin
package app.backlit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitiesTest {

    private val sample = sequenceOf(
        "Tokyo\tJP\t35.6895\t139.6917",
        "Bengaluru\tIN\t12.9719\t77.5937",
        "Berlin\tDE\t52.5244\t13.4105",
        "Albany\tUS\t42.6526\t-73.7562",
        "broken line",
        "Nowhere\tXX\tnot-a-number\t1.0",
    )

    @Test
    fun parseSkipsMalformedLines() {
        val all = Cities.parse(sample)
        assertEquals(4, all.size)
        assertEquals(City("Bengaluru", "IN", 12.9719, 77.5937), all[1])
        assertEquals("Bengaluru, IN", all[1].display)
    }

    @Test
    fun searchIgnoresCase() {
        val all = Cities.parse(sample)
        assertEquals(listOf("Albany"), Cities.search(all, "ALB").map { it.name })
        assertEquals(setOf("Berlin", "Bengaluru"), Cities.search(all, "be").map { it.name }.toSet())
    }

    @Test
    fun prefixMatchesComeBeforeContainsMatches() {
        val all = Cities.parse(sample)
        val r = Cities.search(all, "al")
        assertEquals("Albany", r.first().name)          // prefix
        assertTrue(r.any { it.name == "Bengaluru" })     // contains "al"
    }

    @Test
    fun blankQueryReturnsNothingAndLimitApplies() {
        val all = Cities.parse(sample)
        assertTrue(Cities.search(all, "  ").isEmpty())
        assertEquals(1, Cities.search(all, "e", limit = 1).size)
    }

    @Test
    fun coordinatesAreRoundedToAboutOneKilometre() {
        assertEquals(12.97, roundCoord(12.97194), 1e-9)
        assertEquals(-73.76, roundCoord(-73.7562), 1e-9)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.data.CitiesTest"
```

Expected: compilation FAIL, unresolved `Cities`, `City`, `roundCoord`.

- [ ] **Step 4: Implement `Cities.kt` and `LocationSource.kt`**

`app/src/main/java/app/backlit/data/Cities.kt`:

```kotlin
package app.backlit.data

import android.content.res.AssetManager

data class City(val name: String, val country: String, val lat: Double, val lon: Double) {
    val display: String get() = "$name, $country"
}

object Cities {

    fun parse(lines: Sequence<String>): List<City> = lines.mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 4) return@mapNotNull null
        val lat = p[2].toDoubleOrNull() ?: return@mapNotNull null
        val lon = p[3].toDoubleOrNull() ?: return@mapNotNull null
        City(p[0], p[1], lat, lon)
    }.toList()

    /** Case-insensitive; names starting with the query first, then names containing it. */
    fun search(all: List<City>, query: String, limit: Int = 20): List<City> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val prefix = all.filter { it.name.lowercase().startsWith(q) }
        val contains = all.filter { !it.name.lowercase().startsWith(q) && it.name.lowercase().contains(q) }
        return (prefix + contains).take(limit)
    }

    fun load(assets: AssetManager): List<City> =
        assets.open("cities.tsv").bufferedReader().useLines { parse(it) }
}
```

`app/src/main/java/app/backlit/data/LocationSource.kt`:

```kotlin
package app.backlit.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.round

data class Coords(val lat: Double, val lon: Double)

/** Two decimals ≈ 1 km: plenty for sunrise, and we never store anything finer. */
fun roundCoord(v: Double): Double = round(v * 100.0) / 100.0

class LocationSource(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(): Coords? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val recent = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.time >= dayAgo }
            .maxByOrNull { it.time }
        val loc: Location? = recent ?: withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                val started = runCatching {
                    lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, signal, context.mainExecutor) { cont.resume(it) }
                }
                if (started.isFailure) cont.resume(null)
            }
        }
        return loc?.let { Coords(roundCoord(it.latitude), roundCoord(it.longitude)) }
    }
}
```

`app/src/main/java/app/backlit/data/LocationRefresher.kt`:

```kotlin
package app.backlit.data

import android.content.Context

object LocationRefresher {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Refreshes approximate coordinates at most once a day, only while the app is open. */
    suspend fun refreshIfStale(context: Context, repo: SettingsRepo, settings: Settings, nowMillis: Long) {
        if (settings.locationMode != LocationMode.APPROXIMATE) return
        if (nowMillis - settings.locationUpdatedAt < DAY_MS) return
        val src = LocationSource(context)
        if (!src.hasPermission()) return
        val c = src.current() ?: return
        repo.update { it.copy(lat = c.lat, lon = c.lon, locationUpdatedAt = nowMillis) }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests "app.backlit.data.CitiesTest"
```

Expected: PASS (5 tests).

- [ ] **Step 6: `LocationScreen`**

```kotlin
package app.backlit.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.data.Cities
import app.backlit.data.LocationMode
import app.backlit.data.LocationSource
import app.backlit.data.Settings
import kotlinx.coroutines.launch

@Composable
fun LocationScreen(settings: Settings, onUpdate: ((Settings) -> Settings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember { LocationSource(context) }
    val cities = remember { runCatching { Cities.load(context.assets) }.getOrDefault(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var showCities by remember { mutableStateOf(settings.locationMode == LocationMode.CITY) }

    fun useApproximate() {
        status = "LOCATING…"
        scope.launch {
            val c = source.current()
            if (c == null) {
                status = "COULDN'T GET A LOCATION — TRY A CITY"
            } else {
                onUpdate {
                    it.copy(locationMode = LocationMode.APPROXIMATE, lat = c.lat, lon = c.lon,
                        placeName = "APPROX.", locationUpdatedAt = System.currentTimeMillis())
                }
                status = null
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useApproximate() else status = "PERMISSION DENIED — USING FIXED HOURS"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("SUN TIMES", onBack = onBack)
        Text("The day ring lights up the hours between sunrise and sunset.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))

        SettingRow("Fixed 06:00–18:00", if (settings.locationMode == LocationMode.FIXED) "●" else "") {
            onUpdate { it.copy(locationMode = LocationMode.FIXED) }
            showCities = false
        }
        SettingRow("Approximate location", if (settings.locationMode == LocationMode.APPROXIMATE) "●" else "") {
            showCities = false
            if (source.hasPermission()) useApproximate() else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        SettingRow(
            "Choose a city",
            if (settings.locationMode == LocationMode.CITY) (settings.placeName ?: "●") else "",
        ) { showCities = true }
        DashedDivider()

        status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = BacklitColors.Red, modifier = Modifier.padding(vertical = 10.dp)) }

        if (showCities) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search cities") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BacklitColors.White,
                    unfocusedBorderColor = BacklitColors.Line,
                    cursorColor = BacklitColors.White,
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            Cities.search(cities, query).forEach { city ->
                Text(
                    city.display,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUpdate {
                                it.copy(locationMode = LocationMode.CITY, lat = city.lat, lon = city.lon, placeName = city.display)
                            }
                            showCities = false
                            query = ""
                        }
                        .padding(vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
```

- [ ] **Step 7: Permission, wiring and the daily refresh**

In `AndroidManifest.xml`, add after the Glyph permission:

```xml
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

In `MainActivity.kt`, replace the `Screen.LOCATION ->` branch with:

```kotlin
                        Screen.LOCATION -> LocationScreen(s, update, onBack = { screen = Screen.HOME })
```

add, directly after the existing `LaunchedEffect(Unit) { ... }` block inside the `Box`:

```kotlin
                    LaunchedEffect(s.locationMode) {
                        LocationRefresher.refreshIfStale(this@MainActivity, repo, s, System.currentTimeMillis())
                    }
```

and the imports:

```kotlin
import app.backlit.data.LocationRefresher
import app.backlit.ui.LocationScreen
```

- [ ] **Step 8: Build, test, run**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Human, on the Phone (3): pick Day ring → Sun times. (a) Choose a city, e.g. "Reykjavik" → the ring's bright arc visibly changes. (b) Approximate → the permission prompt shows only "approximate"; allow → the place reads "APPROX.". (c) Deny on a fresh install (`adb shell pm clear app.backlit`) → the red "PERMISSION DENIED" line appears and the ring stays 06–18.

- [ ] **Step 9: Commit**

```bash
git add scripts/build_cities.py app/src/main/assets/cities.tsv app/src/main/java/app/backlit/ app/src/main/AndroidManifest.xml app/src/test/java/app/backlit/data/CitiesTest.kt
git commit -m "feat: sun-time location via approximate location, offline city list or fixed hours

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Device calibration and manual checklist (human + agent)

**Files:**
- Modify: `app/src/main/java/app/backlit/glyph/FrameEncoder.kt` (calibrated `minLit`), and optionally the face brightness constants
- Create: `docs/testing/device-checklist.md`

**Interfaces:**
- Consumes: everything above.
- Produces: a calibrated `FrameEncoder.minLit` and a filled-in checklist.

- [ ] **Step 1: Write the checklist**

`docs/testing/device-checklist.md`:

```markdown
# Backlit — Phone (3) device checklist

Date: ______  Build: ______  Nothing OS: ______

## Brightness calibration
- [ ] At brightness 20%, Day ring at night: the dim night arc is just visible in a dark room.
      If not, raise FrameEncoder.minLit by 100 and retry. Final minLit: ____
- [ ] At brightness 100%, Analog: hour hand clearly brighter than minute hand.

## Toy behaviour
- [ ] Enable Backlit Clock in Glyph Toys; it appears with the clock icon and summary.
- [ ] Short press cycles to the toy and away again with no frozen frame left behind.
- [ ] Long press switches Analog ↔ Day ring; the app's chips follow.
- [ ] Analog second dot moves once per second; minute hand never flickers.
- [ ] As the AOD toy: the frame updates each minute, with no second dot and dimmer.
- [ ] Change the time zone in system settings while the toy shows: the time updates immediately.
- [ ] Change face/brightness in the app while the toy shows: the matrix updates within a second.
- [ ] Reboot: the chosen face and settings persist.
- [ ] Battery: 1 hour with the toy as AOD; the Backlit battery use is negligible in Settings → Battery.

## App
- [ ] First launch opens Setup; after the toy has run once, launch opens Home.
- [ ] 13×13 preview toggle shows the stacked day-ring digits with ring gaps top/bottom.
- [ ] Location: city, approximate (approximate-only prompt), deny path.
- [ ] About shows version and licenses.
```

- [ ] **Step 2 (human): Run the checklist on the Phone (3)**

Install with `./gradlew :app:installDebug` and work through every box. To try a different `minLit` value, edit the default in `FrameEncoder.kt`, reinstall, and recheck. Report the final value and any failing box.

- [ ] **Step 3: Apply the calibrated value and re-run tests**

Set `var minLit: Int = <calibrated>` in `FrameEncoder.kt`, then:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS (the encoder tests are written against `FrameEncoder.minLit`, not a literal).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/backlit/glyph/FrameEncoder.kt docs/testing/device-checklist.md
git commit -m "chore: calibrate minimum LED brightness on Phone (3) and record device checklist

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: Release build and Play Store material

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `docs/release/play-listing.md`, `docs/privacy-policy.md`

**Interfaces:**
- Consumes: the finished app.
- Produces: a signed `app-release.aab` and the store texts.

- [ ] **Step 1: Signing config read from an untracked `keystore.properties`**

Add at the top of `app/build.gradle.kts`, after the imports:

```kotlin
import java.util.Properties

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { !keystoreProps.getProperty(it).isNullOrBlank() }
```

Inside `android { ... }`, before `buildTypes`:

```kotlin
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
```

and inside `buildTypes { release { ... } }` add:

```kotlin
            signingConfig = signingConfigs.findByName("release")
```

- [ ] **Step 2 (human): Create the upload key**

The human runs this and chooses the passwords. Agents must never type or see them. Keep the `.jks` file outside the repo and back it up.

```bash
keytool -genkeypair -v -keystore ~/keys/backlit-upload.jks -alias backlit -keyalg RSA -keysize 4096 -validity 10000
```

Then create `keystore.properties` at the repo root (it is gitignored):

```properties
storeFile=/Users/<you>/keys/backlit-upload.jks
storePassword=<your password>
keyAlias=backlit
keyPassword=<your password>
```

- [ ] **Step 3: Privacy policy and listing text**

`docs/privacy-policy.md`:

```markdown
# Backlit — Privacy Policy

_Last updated: 2026-10-03_

Backlit does not collect, store on any server, or share any personal data.

- **Location:** if you choose "Approximate location" for the Day ring clock, Backlit reads your
  approximate location on your phone, rounds it to about 1 km, and stores it only on your phone to
  calculate sunrise and sunset. It is never sent anywhere. You can switch to a city or fixed hours
  at any time.
- **No network access:** Backlit makes no network requests, has no analytics and no ads.
- **Uninstalling** Backlit deletes all of its data from your phone.

Contact: venusaiyalamanchili@gmail.com
```

`docs/release/play-listing.md`:

```markdown
# Play listing — Backlit

**App name:** Backlit – Glyph Matrix Clocks & Toys
**Short description (≤80):** Beautiful clocks for the Glyph Matrix on Nothing Phone (3) and (4a) Pro.
**Category:** Personalization · **Price:** Free · **Ads:** No · **In-app purchases:** No

**Full description:**
Backlit turns the Glyph Matrix on the back of your Nothing phone into a clock that looks like it
belongs there.

• ANALOG — smooth anti-aliased hands, a rim second hand on Phone (3)
• DAY RING — a 24-hour dial that lights the hours between your real sunrise and sunset
• Live preview in the app, pixel-for-pixel what the matrix shows
• Long press the Glyph Button to switch faces
• Always-on (AOD) support, including Phone (4a) Pro
• No ads, no tracking, no data collected

Supported: Nothing Phone (3), Nothing Phone (4a) Pro.

**Data safety form:** No data collected. No data shared. (Location is processed on device only.)
**Privacy policy URL:** GitHub URL of docs/privacy-policy.md (or a GitHub Pages copy)
**Screenshots:** 2–4 app screenshots + 2 photos of the physical matrix (Analog, Day ring).
```

- [ ] **Step 4: Build the release bundle**

```bash
./gradlew :app:testDebugUnitTest :app:bundleRelease
```

Expected: `BUILD SUCCESSFUL`, and `app/build/outputs/bundle/release/app-release.aab` exists. Then smoke-test the R8-minified build on the Phone (3):

```bash
./gradlew :app:installRelease
```

Check: the toy still appears and draws. If it doesn't, R8 stripped SDK classes, so recheck `proguard-rules.pro`.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts docs/privacy-policy.md docs/release/play-listing.md
git commit -m "chore: release signing from untracked keystore.properties, privacy policy and listing

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 6 (human): Before the first Play upload**

Confirm the `applicationId` is `app.backlit` (it can't be changed after the first upload). Search the Play Store and a trademark database for "Backlit". Upload the AAB to an internal testing track first.
