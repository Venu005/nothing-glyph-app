# Backlit UI Redesign — Part 2 (Toy Pages, Studio, Alerts) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild every toy page on one scaffold (hero preview, status, settings, fixed bottom bar), turn Studio into a gallery with action sheets, and turn Alerts into a segmented screen (CONTACTS / DEVICES (Bluetooth connect alerts) / ANIMATIONS) with one attention card.

**Architecture:**
- **Pure units with JVM tests:**
  - `ToyStatus`: status lines.
  - `ToyAction`: bottom button and preview ids.
  - `AlertsAttention`: the single missing-permission card.
- **New Compose controls:**
  - `ChipRow`, `SegmentedControl`, `BottomActionBar`, `OptionSheet`, `SheetAction`, `GalleryCard`, `AttentionCard`, `PillButton`, `HeroPreview`
  - `ToyPageScaffold` and `rememberTicker`
- **Toy pages:** each toy gets a `XxxPage` that keeps its existing state logic and fills the scaffold.
- **Studio and Alerts:** rewritten on the same controls, reusing their runtime calls.

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`ModalBottomSheet`), `lifecycle-runtime-compose`, JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-06-backlit-ui-redesign-part2-design.md`. Part 1 spec §5 (design system) still applies. Mockups: `docs/superpowers/mockups/2026-10-06-toy-page.html` and `2026-10-06-studio-alerts.html` (Studio A, Alerts B).

## Global Constraints
- **Tooling:** `source .superpowers/env.sh`; `.superpowers/runtests.sh [--tests '<pattern>']`.
- **No behaviour changes** to toys, Glyph output, alerts, Bluetooth connect alerts, drawings or settings data. Every setting stays reachable.
- **Design tokens:** colours `BacklitColors` (Line `#333333`); type from `Theme.kt`.
  - Cards: 14 dp corners, 1 dp `Line` border.
  - Pills: fully rounded.
  - Selected chip: white fill, black text.
  - Primary button: white fill, black text.
  - Outline button: 1 dp `Line` border.
  - Attention: red (`#D71921`) 1 dp border with a red dot.
- **Layout and touch:**
  - Bottom bar: black, 1 dp top line, padding 16 dp (horizontal) / 12 dp (top) / 18 dp (bottom). The content's bottom padding is the bar's measured height plus 16 dp.
  - Hero preview: 62 % of the width, centred.
  - Touch targets: at least 48 dp for ← and ⚙.
- **Commits:** end every commit with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus
1. **The bottom bar covering content** on short pages, with long content, after an expandable row opens, and with the keyboard open (Pet name, Badge editor). Pinned by the scaffold's measured padding (Task 2) and the device checklist (Task 9).
2. **Off-screen pager pages still animating or reading the mic** (Music visualizer) while you're on another toy. Pinned by `chrome.active` gating in every page (Tasks 3–6).
3. **Sheet state across recomposition and rotation:**
   - A sheet open for a drawing that gets deleted.
   - A rule sheet open for a contact that gets removed.

   Sheets must close, never crash. Pinned by the sheets keying on ids looked up in the current list (Tasks 7–8).
4. **Permissions flow in Alerts:**
   - Notification access, granted then revoked.
   - Nearby devices, denied then granted.

   The attention card must reflect the live state on resume. Pinned by `AlertsAttentionTest` (Task 1) and the resume re-check (Task 8).
5. **4a Pro and unsupported phones:**
   - 13×13 heroes.
   - No Music page.
   - No bottom bar on unsupported phones, except Canvas "+ NEW DRAWING".

   Pinned by `ToyActionTest` (Task 1).

---

## File Structure
| File | Responsibility |
|---|---|
| `ui/toys/ToyStatus.kt`, `ui/toys/ToyAction.kt`, `ui/alerts/AlertsAttention.kt` | pure logic (tests) |
| `ui/components/Controls.kt` | `ChipRow`, `SegmentedControl`, `PillButton`, `BottomActionBar` and `ActionSpec`, `OptionSheet`, `SheetAction`, `GalleryCard`, `AttentionCard`, `HeroPreview`, `rememberTicker` |
| `ui/toys/ToyPageScaffold.kt` | `PageChrome` and `ToyPageScaffold` |
| `ui/toys/ClockPage.kt`, `MusicPage.kt`, `ChargePage.kt`, `CanvasPage.kt`, `PetPage.kt`, `SandPage.kt`, `BadgePage.kt` | one page per toy |
| `ui/StudioScreen.kt` (rewritten), `ui/AlertsScreen.kt` (rewritten) | tools |
| Modify | `ui/toys/ToyPagerScreen.kt`, `ui/ToolScreens.kt`, `ui/components/Nothing.kt`, `ui/HomeScreen.kt`, `MainActivity.kt` |
| Delete | `ui/toys/ClockToy.kt`, `ui/MusicScreen.kt` (keep `Notice`, which moves to `Controls.kt`), `ui/ChargeScreen.kt`, `ui/PetScreen.kt`, `ui/SandScreen.kt`, `ui/BadgeScreen.kt` |

---

### Task 1: Pure logic — status lines, bottom actions, Alerts attention

**Files:**
- Create: `app/src/main/java/app/backlit/ui/toys/ToyStatus.kt`, `app/src/main/java/app/backlit/ui/toys/ToyAction.kt`, `app/src/main/java/app/backlit/ui/alerts/AlertsAttention.kt`
- Test: `app/src/test/java/app/backlit/ui/toys/ToyStatusTest.kt`, `ToyActionTest.kt`, `app/src/test/java/app/backlit/ui/alerts/AlertsAttentionTest.kt`

**Interfaces:**
- Produces:
  - `data class StatusInputs(battery: Int? = null, micGranted: Boolean = true, musicSupported: Boolean = true, petBase: String? = null, petMood: Int? = null, drawingName: String? = null)`
  - `ToyStatus.line(id, s, now, zone, inputs): String`
  - `sealed interface ToyAction { data class ShowOnGlyph(val previewId: String?, val ms: Long); data object OpenGlyphToys; data object TurnOn; data object NewDrawing; companion object { fun of(id, setUp, supported, hasDrawing): ToyAction?; fun previewFor(id, s, now, zone): ShowOnGlyph } }`
  - `enum class AlertsSegment { CONTACTS, DEVICES, ANIMATIONS }`
  - `enum class Attention { NOTIFICATION_ACCESS, NEARBY }`
  - `AlertsAttention.pick(listenerOn, btGranted, contacts: Int, devices: Int, segment): Attention?`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/app/backlit/ui/toys/ToyStatusTest.kt
package app.backlit.ui.toys

import app.backlit.data.Settings
import app.backlit.sand.Phase
import app.backlit.sand.TimerState
import app.backlit.ui.home.ToyId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class ToyStatusTest {
    private val z = ZoneOffset.UTC
    private fun line(id: ToyId, s: Settings = Settings(), now: Long = 0, i: StatusInputs = StatusInputs()) = ToyStatus.line(id, s, now, z, i)

    @Test
    fun clockShowsTheFace() {
        assertEquals("ANALOG", line(ToyId.CLOCK))
        assertEquals("DAY RING", line(ToyId.CLOCK, Settings(faceId = "dayring")))
    }

    @Test
    fun musicStates() {
        assertEquals("LISTENING", line(ToyId.MUSIC))
        assertEquals("NEEDS PERMISSION", line(ToyId.MUSIC, i = StatusInputs(micGranted = false)))
        assertEquals("PHONE (3) ONLY", line(ToyId.MUSIC, i = StatusInputs(musicSupported = false)))
    }

    @Test
    fun chargeWithAndWithoutBattery() {
        assertEquals("62 % · MOON", line(ToyId.CHARGE, i = StatusInputs(battery = 62)))
        assertEquals("MOON", line(ToyId.CHARGE))
    }

    @Test
    fun canvasAndPet() {
        assertEquals("NO DRAWING YET", line(ToyId.CANVAS))
        assertEquals("HEART", line(ToyId.CANVAS, i = StatusInputs(drawingName = "Heart")))
        assertEquals("BOO IS HAPPY · MOOD 72", line(ToyId.PET, i = StatusInputs(petBase = "HAPPY", petMood = 72)))
        assertEquals("RIBBIT", line(ToyId.PET, Settings(petKind = "frog")))
    }

    @Test
    fun sandEveryPhase() {
        val m = TimerState.MIN
        assertEquals("READY · 5 MIN", line(ToyId.SAND))
        val run = TimerState(phase = Phase.RUNNING, durationMs = 5 * m, endAt = 192_000).encode()
        assertEquals("RUNNING · 3:12 LEFT", line(ToyId.SAND, Settings(sandTimer = run), now = 0))
        val paused = TimerState(phase = Phase.PAUSED, durationMs = 5 * m, leftMs = 192_000).encode()
        assertEquals("PAUSED · 3:12 LEFT", line(ToyId.SAND, Settings(sandTimer = paused)))
        assertEquals("TIME'S UP", line(ToyId.SAND, Settings(sandTimer = TimerState(phase = Phase.DONE).encode())))
    }

    @Test
    fun badgeShowsTheLiveText() {
        assertEquals("IN A MEETING", line(ToyId.BADGE, Settings(badgeActiveSince = 1)))
    }
}
```

```kotlin
// app/src/test/java/app/backlit/ui/toys/ToyActionTest.kt
package app.backlit.ui.toys

import app.backlit.data.Settings
import app.backlit.ui.home.ToyId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ToyActionTest {
    @Test
    fun notSetUpAlwaysAsksToTurnOn() {
        for (id in ToyId.entries) assertEquals(ToyAction.TurnOn, ToyAction.of(id, setUp = false, supported = true, hasDrawing = true))
    }

    @Test
    fun clockAndMusicOpenGlyphToysOthersShow() {
        assertEquals(ToyAction.OpenGlyphToys, ToyAction.of(ToyId.CLOCK, true, true, false))
        assertEquals(ToyAction.OpenGlyphToys, ToyAction.of(ToyId.MUSIC, true, true, false))
        for (id in listOf(ToyId.CHARGE, ToyId.PET, ToyId.SAND, ToyId.BADGE)) assertTrue(ToyAction.of(id, true, true, false) is ToyAction.ShowOnGlyph)
        assertTrue(ToyAction.of(ToyId.CANVAS, true, true, hasDrawing = true) is ToyAction.ShowOnGlyph)
        assertEquals(ToyAction.NewDrawing, ToyAction.of(ToyId.CANVAS, true, true, hasDrawing = false))
    }

    @Test
    fun unsupportedPhonesHaveNoBarExceptNewDrawing() {
        for (id in ToyId.entries.filter { it != ToyId.CANVAS }) assertNull(ToyAction.of(id, false, supported = false, hasDrawing = false))
        assertEquals(ToyAction.NewDrawing, ToyAction.of(ToyId.CANVAS, false, supported = false, hasDrawing = false))
        assertNull(ToyAction.of(ToyId.CANVAS, false, supported = false, hasDrawing = true))
    }

    @Test
    fun previewIdsMatchTheExistingOnes() {
        val z = ZoneOffset.UTC
        assertEquals("pet:happy", ToyAction.previewFor(ToyId.PET, Settings(), 0, z).previewId)
        assertEquals("pet:frog:happy", ToyAction.previewFor(ToyId.PET, Settings(petKind = "frog"), 0, z).previewId)
        assertEquals("sand:running", ToyAction.previewFor(ToyId.SAND, Settings(), 0, z).previewId)
        assertEquals("charge:moon:plug_in", ToyAction.previewFor(ToyId.CHARGE, Settings(), 0, z).previewId)
        assertEquals("badge:laptop:IN A MEETING", ToyAction.previewFor(ToyId.BADGE, Settings(badgeActiveSince = 1), 0, z).previewId)
        assertNull(ToyAction.previewFor(ToyId.CANVAS, Settings(), 0, z).previewId)
    }
}
```

```kotlin
// app/src/test/java/app/backlit/ui/alerts/AlertsAttentionTest.kt
package app.backlit.ui.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertsAttentionTest {
    @Test
    fun notificationAccessComesFirst() {
        assertEquals(Attention.NOTIFICATION_ACCESS, AlertsAttention.pick(false, false, contacts = 1, devices = 1, segment = AlertsSegment.DEVICES))
        assertEquals(Attention.NOTIFICATION_ACCESS, AlertsAttention.pick(false, true, contacts = 0, devices = 0, segment = AlertsSegment.CONTACTS))
    }

    @Test
    fun nearbyOnlyWithDevices() {
        assertEquals(Attention.NEARBY, AlertsAttention.pick(true, false, contacts = 0, devices = 2, segment = AlertsSegment.ANIMATIONS))
        assertNull(AlertsAttention.pick(true, false, contacts = 0, devices = 0, segment = AlertsSegment.DEVICES))
    }

    @Test
    fun nothingWhenAllGrantedOrNotNeeded() {
        assertNull(AlertsAttention.pick(true, true, 3, 3, AlertsSegment.CONTACTS))
        assertNull(AlertsAttention.pick(false, true, contacts = 0, devices = 0, segment = AlertsSegment.ANIMATIONS))
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.ui.toys.*' --tests 'app.backlit.ui.alerts.*'`
Expected: compilation FAIL with `Unresolved reference: ToyStatus`.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/app/backlit/ui/toys/ToyStatus.kt
package app.backlit.ui.toys

import app.backlit.badge.BadgeMessage
import app.backlit.badge.BadgeText
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.pet.PetKind
import app.backlit.render.charge.ChargeStyles
import app.backlit.render.faces.Faces
import app.backlit.sand.Phase
import app.backlit.sand.TimerState
import app.backlit.ui.home.ToyId
import java.time.ZoneId

/** Values a status line needs from Android or a live engine; the page fills them in. */
data class StatusInputs(
    val battery: Int? = null,
    val micGranted: Boolean = true,
    val musicSupported: Boolean = true,
    val petBase: String? = null,
    val petMood: Int? = null,
    val drawingName: String? = null,
)

/** The one-line status under each toy's name on its page. */
object ToyStatus {
    fun line(id: ToyId, s: Settings, now: Long, zone: ZoneId, i: StatusInputs): String = when (id) {
        ToyId.CLOCK -> Faces.byId(s.faceId).label.uppercase()
        ToyId.MUSIC -> when {
            !i.musicSupported -> "PHONE (3) ONLY"
            !i.micGranted -> "NEEDS PERMISSION"
            else -> "LISTENING"
        }
        ToyId.CHARGE -> listOfNotNull(i.battery?.let { "$it %" }, ChargeStyles.byId(s.chargeStyle).label.uppercase()).joinToString(" · ")
        ToyId.CANVAS -> i.drawingName?.uppercase() ?: "NO DRAWING YET"
        ToyId.PET -> {
            val name = SettingsRepo.petNameFor(s, PetKind.byId(s.petKind)).uppercase()
            name + (i.petBase?.let { " IS $it" } ?: "") + (i.petMood?.let { " · MOOD $it" } ?: "")
        }
        ToyId.SAND -> {
            val st = TimerState.decode(s.sandTimer).tick(now)
            when (st.phase) {
                Phase.READY -> "READY · ${st.durationMs / TimerState.MIN} MIN"
                Phase.RUNNING -> "RUNNING · ${TimerState.clock(st.timeLeft(now))} LEFT"
                Phase.PAUSED -> "PAUSED · ${TimerState.clock(st.timeLeft(now))} LEFT"
                Phase.DONE -> "TIME'S UP"
            }
        }
        ToyId.BADGE -> {
            val list = BadgeMessage.decodeList(s.badgeMessages)
            val m = list[BadgeMessage.activeIndex(s.badgeActive, list.size)]
            BadgeText.scroll(m, s.badgeActiveSince.takeIf { it > 0 } ?: now, now, s.use24h, zone)
        }
    }
}
```

```kotlin
// app/src/main/java/app/backlit/ui/toys/ToyAction.kt
package app.backlit.ui.toys

import app.backlit.badge.BadgeMessage
import app.backlit.badge.BadgePreviewAnimation
import app.backlit.badge.BadgeText
import app.backlit.charge.ChargePreviewAnimation
import app.backlit.charge.Moment
import app.backlit.data.Settings
import app.backlit.pet.Base
import app.backlit.pet.PetKind
import app.backlit.pet.PetPreviewAnimation
import app.backlit.render.charge.ChargeStyles
import app.backlit.sand.SandPreviewAnimation
import app.backlit.ui.home.ToyId
import java.time.ZoneId

/** The bottom-bar action on a toy page. */
sealed interface ToyAction {
    /** Play [previewId] on the Glyph for [ms] (null id: the page plays it itself, e.g. the Canvas drawing). */
    data class ShowOnGlyph(val previewId: String?, val ms: Long) : ToyAction
    data object OpenGlyphToys : ToyAction
    data object TurnOn : ToyAction
    data object NewDrawing : ToyAction

    companion object {
        /** null = no bar (unsupported phones, where nothing can play and Glyph Toys doesn't exist). */
        fun of(id: ToyId, setUp: Boolean, supported: Boolean, hasDrawing: Boolean): ToyAction? = when {
            !supported -> if (id == ToyId.CANVAS && !hasDrawing) NewDrawing else null
            !setUp -> TurnOn
            id == ToyId.CLOCK || id == ToyId.MUSIC -> OpenGlyphToys
            id == ToyId.CANVAS && !hasDrawing -> NewDrawing
            else -> ShowOnGlyph(null, 0)
        }

        fun previewFor(id: ToyId, s: Settings, now: Long, zone: ZoneId): ShowOnGlyph = when (id) {
            ToyId.CHARGE -> ShowOnGlyph(ChargePreviewAnimation.idFor(ChargeStyles.byId(s.chargeStyle).id, Moment.PLUG_IN), ChargePreviewAnimation.durationMs(Moment.PLUG_IN))
            ToyId.PET -> ShowOnGlyph(PetPreviewAnimation.idFor(PetKind.byId(s.petKind), Base.HAPPY), 3000L)
            ToyId.SAND -> ShowOnGlyph(SandPreviewAnimation.RUNNING_ID, 4000L)
            ToyId.BADGE -> {
                val list = BadgeMessage.decodeList(s.badgeMessages)
                val m = list[BadgeMessage.activeIndex(s.badgeActive, list.size)]
                val text = BadgeText.scroll(m, s.badgeActiveSince.takeIf { it > 0 } ?: now, now, s.use24h, zone)
                ShowOnGlyph(BadgePreviewAnimation.idFor(m.icon, text), 6000L)
            }
            else -> ShowOnGlyph(null, 4000L)
        }
    }
}
```

```kotlin
// app/src/main/java/app/backlit/ui/alerts/AlertsAttention.kt
package app.backlit.ui.alerts

enum class AlertsSegment(val label: String) { CONTACTS("CONTACTS"), DEVICES("DEVICES"), ANIMATIONS("ANIMATIONS") }

enum class Attention { NOTIFICATION_ACCESS, NEARBY }

/** At most one "needs attention" card on Alerts, most important first. */
object AlertsAttention {
    fun pick(listenerOn: Boolean, btGranted: Boolean, contacts: Int, devices: Int, segment: AlertsSegment): Attention? = when {
        !listenerOn && (contacts > 0 || segment == AlertsSegment.CONTACTS) -> Attention.NOTIFICATION_ACCESS
        !btGranted && devices > 0 -> Attention.NEARBY
        else -> null
    }
}
```

The Clock status needs the face labels to be "ANALOG" and "DAY RING". If `Faces.byId("dayring").label` is "DAY RING" this matches. If the labels differ, assert the actual labels uppercased and record a ruling: the status shows the face's own label.

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.ui.toys.*' --tests 'app.backlit.ui.alerts.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/ui/toys/ToyStatus.kt app/src/main/java/app/backlit/ui/toys/ToyAction.kt app/src/main/java/app/backlit/ui/alerts app/src/test/java/app/backlit/ui/toys app/src/test/java/app/backlit/ui/alerts
git commit -m "feat(ui): toy status lines, bottom actions and Alerts attention (pure)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Controls and the toy page scaffold

**Files:**
- Create: `app/src/main/java/app/backlit/ui/components/Controls.kt`, `app/src/main/java/app/backlit/ui/toys/ToyPageScaffold.kt`
- Modify: `app/src/main/java/app/backlit/ui/components/Nothing.kt`: 48 dp ← targets; `StatusPill(setUp, supported = true, onTurnOn)` shows `PREVIEW` when not supported.
- Modify: `app/src/main/java/app/backlit/ui/MusicScreen.kt`: delete `Notice` from here, since it moves to `Controls.kt`.

**Interfaces:**
- Produces:
  - `ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit)`
  - `SegmentedControl(options, selected, onSelect)`
  - `PillButton(label, onClick, modifier)`
  - `ActionSpec(label, style: ActionStyle, onClick)`, with `ActionStyle { PRIMARY, OUTLINE, ATTENTION }`
  - `BottomActionBar(primary: ActionSpec?, secondary: ActionSpec? = null, onHeight: (Int) -> Unit)`
  - `OptionSheet(title, onDismiss, content)`
  - `SheetAction(label, danger = false, onClick)`
  - `GalleryCard(grid, name, subtitle, highlighted, onClick, modifier)`
  - `AttentionCard(text, onClick)`
  - `HeroPreview(grid, onClick: (() -> Unit)? = null)`
  - `Notice(text)`
  - `rememberTicker(periodMs: Long, active: Boolean = true): Long`
  - `PageChrome(onBack, setUp, supported, onTurnOn, index, count, active)`
  - `ToyPageScaffold(chrome, name, hero, status, action: ToyAction?, onShow = {}, onNewDrawing = {}, heroClick = null, content)`

These are Compose components with no Compose test harness in the project, so they're verified by building, and on the device in Task 9.

- [ ] **Step 1: Write `Controls.kt`**

```kotlin
// app/src/main/java/app/backlit/ui/components/Controls.kt
package app.backlit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.backlit.render.PixelGrid
import app.backlit.ui.BacklitColors
import app.backlit.ui.MatrixPreview
import kotlinx.coroutines.delay

private val Pill = RoundedCornerShape(50)
private val Card = RoundedCornerShape(14.dp)

/** Wall-clock ms, refreshed every [periodMs] while [active] and the screen is at least STARTED. */
@Composable
fun rememberTicker(periodMs: Long, active: Boolean = true): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, periodMs) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(periodMs) }
        }
    }
    return now
}

@Composable
private fun PillText(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(Pill).background(if (selected) BacklitColors.White else BacklitColors.Black)
            .border(1.dp, if (selected) BacklitColors.White else BacklitColors.Line, Pill)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) BacklitColors.Black else BacklitColors.White, maxLines = 1)
    }
}

/** Single-choice pills; scrolls sideways when they don't fit. */
@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (id, label) -> PillText(label, id == selected, { onSelect(id) }) }
    }
}

/** Equal-width pills across the screen (Alerts segments). */
@Composable
fun SegmentedControl(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (id, label) -> PillText(label, id == selected, { onSelect(id) }, Modifier.weight(1f)) }
    }
}

@Composable
fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) = PillText(label, false, onClick, modifier)

enum class ActionStyle { PRIMARY, OUTLINE, ATTENTION }

data class ActionSpec(val label: String, val style: ActionStyle, val onClick: () -> Unit)

@Composable
private fun ActionButton(spec: ActionSpec, modifier: Modifier) {
    val bg = if (spec.style == ActionStyle.PRIMARY) BacklitColors.White else BacklitColors.Black
    val border = when (spec.style) { ActionStyle.PRIMARY -> BacklitColors.White; ActionStyle.OUTLINE -> BacklitColors.Line; ActionStyle.ATTENTION -> BacklitColors.Red }
    Row(
        modifier.clip(Pill).background(bg).border(1.dp, border, Pill).clickable(onClick = spec.onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spec.style == ActionStyle.ATTENTION) {
            Box(Modifier.size(6.dp).background(BacklitColors.Red, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(spec.label, style = MaterialTheme.typography.labelSmall, color = if (spec.style == ActionStyle.PRIMARY) BacklitColors.Black else BacklitColors.White)
    }
}

/** The fixed bar at the bottom of a page; reports its height so the page can pad its content above it. */
@Composable
fun BottomActionBar(primary: ActionSpec?, secondary: ActionSpec? = null, onHeight: (Int) -> Unit = {}, modifier: Modifier = Modifier) {
    if (primary == null && secondary == null) { onHeight(0); return }
    Column(modifier.fillMaxWidth().background(BacklitColors.Black).onSizeChanged { onHeight(it.height) }) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BacklitColors.Line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            secondary?.let { ActionButton(it, Modifier.weight(1f)) }
            primary?.let { ActionButton(it, Modifier.weight(2f)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BacklitColors.Black,
        contentColor = BacklitColors.White,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(width = 36.dp, height = 4.dp).background(BacklitColors.Line, Pill)) },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 10.dp))
            content()
        }
    }
}

@Composable
fun SheetAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (danger) BacklitColors.Red else BacklitColors.White, modifier = Modifier.padding(vertical = 14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(BacklitColors.Line))
    }
}

@Composable
fun GalleryCard(grid: PixelGrid, name: String, subtitle: String, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(Card).border(1.dp, if (highlighted) BacklitColors.White else BacklitColors.Line, Card).clickable(onClick = onClick).padding(8.dp)) {
        MatrixPreview(grid, Modifier.fillMaxWidth())
        Text(name.uppercase(), style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = if (highlighted) BacklitColors.White else BacklitColors.Dim)
    }
}

@Composable
fun AttentionCard(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(Card).border(1.dp, BacklitColors.Red, Card).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(BacklitColors.Red, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("→", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun HeroPreview(grid: PixelGrid, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
        val m = Modifier.fillMaxWidth(0.62f)
        MatrixPreview(grid, if (onClick != null) m.clickable(onClick = onClick) else m)
    }
}

/** A bordered note (kept for the few places that still need a paragraph, e.g. import results). */
@Composable
fun Notice(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Card).border(1.dp, BacklitColors.Line, Card).padding(12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CenterNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = BacklitColors.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
}
```

- [ ] **Step 2: Write the scaffold**

```kotlin
// app/src/main/java/app/backlit/ui/toys/ToyPageScaffold.kt
package app.backlit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.backlit.glyph.ToysManager
import app.backlit.render.PixelGrid
import app.backlit.ui.components.ActionSpec
import app.backlit.ui.components.ActionStyle
import app.backlit.ui.components.BottomActionBar
import app.backlit.ui.components.HeroPreview
import app.backlit.ui.components.PagerDots
import app.backlit.ui.components.ToyHeader
import app.backlit.ui.toys.ToyAction

/** What the pager tells each toy page. [active]: this page has settled on screen (only it animates). */
data class PageChrome(
    val onBack: () -> Unit,
    val setUp: Boolean,
    val supported: Boolean,
    val onTurnOn: () -> Unit,
    val index: Int,
    val count: Int,
    val active: Boolean,
)

/** The shape of every toy page: header, hero, name, dots, status, content, and a fixed bottom bar. */
@Composable
fun ToyPageScaffold(
    chrome: PageChrome,
    name: String,
    hero: PixelGrid,
    status: String,
    action: ToyAction?,
    onShow: () -> Unit = {},
    onNewDrawing: () -> Unit = {},
    heroClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var barPx by remember { mutableIntStateOf(0) }
    val barDp = with(LocalDensity.current) { barPx.toDp() }
    val spec = when (action) {
        null -> null
        ToyAction.TurnOn -> ActionSpec("TURN ON IN GLYPH TOYS", ActionStyle.ATTENTION, chrome.onTurnOn)
        ToyAction.OpenGlyphToys -> ActionSpec("OPEN GLYPH TOYS", ActionStyle.OUTLINE) { if (!ToysManager.open(context)) chrome.onTurnOn() }
        ToyAction.NewDrawing -> ActionSpec("+ NEW DRAWING", ActionStyle.PRIMARY, onNewDrawing)
        is ToyAction.ShowOnGlyph -> ActionSpec("SHOW ON GLYPH", ActionStyle.PRIMARY, onShow)
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = barDp + 16.dp)) {
            ToyHeader(chrome.onBack, chrome.setUp, chrome.supported, chrome.onTurnOn)
            HeroPreview(hero, heroClick)
            Text(name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            PagerDots(chrome.count, chrome.index)
            Text(status, style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            content()
        }
        BottomActionBar(spec, onHeight = { barPx = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
```

- [ ] **Step 3: Update `Nothing.kt`**

- **`StatusPill`:** change it to:

```kotlin
@Composable
fun StatusPill(setUp: Boolean, supported: Boolean = true, onTurnOn: (() -> Unit)? = null) {
    val label = when { !supported -> "PREVIEW"; setUp -> "● ON GLYPH"; else -> "○ TURN ON" }
    val m = if (supported && !setUp && onTurnOn != null) Modifier.clickable(onClick = onTurnOn) else Modifier
    Text(label, style = MaterialTheme.typography.labelSmall, color = if (supported && setUp) BacklitColors.White else BacklitColors.Dim, modifier = m)
}
```

- **`ToyHeader`:** change the signature to `ToyHeader(onBack: () -> Unit, setUp: Boolean, supported: Boolean, onTurnOn: () -> Unit)`, and pass `StatusPill(setUp, supported, onTurnOn)`.
- **`ToyCard`:** give it a `supported: Boolean = true` parameter and pass it to `StatusPill`.
- **48 dp tap targets:** give the "←" `Text` in `ToyHeader` and `PageHeader` `Modifier.minimumInteractiveComponentSize().clickable(onClick = onBack)` (import `androidx.compose.material3.minimumInteractiveComponentSize`).

In `MusicScreen.kt`, delete the `Notice` function so there's a single `Notice`. Add `import app.backlit.ui.components.Notice` to every file that uses `Notice(`; list them with `grep -ln "Notice(" app/src/main/java/app/backlit/ui`.

- [ ] **Step 4: Fix the callers, build and test**

`ToyPagerScreen` calls `ToyHeader(onBack, setUp, onTurnOn = …)`. Change it to `ToyHeader(onBack, ToyCatalog.isSetUp(settings, id), profile != DeviceProfile.UNSUPPORTED, onTurnOn = …)`. This is temporary; Task 3 replaces the pager body. In `HomeScreen`, pass `supported = profile != DeviceProfile.UNSUPPORTED` to each `ToyCard`, and give the ⚙ `Modifier.minimumInteractiveComponentSize()`.

Run: `source .superpowers/env.sh && ./gradlew --no-daemon -q :app:assembleDebug && .superpowers/runtests.sh`
Expected: the build succeeds, with `0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/ui
git commit -m "feat(ui): Nothing-style controls, bottom bar, sheets and the toy page scaffold

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Clock and Music pages, and the pager on the scaffold

**Files:**
- Create: `app/src/main/java/app/backlit/ui/toys/ClockPage.kt`, `MusicPage.kt`
- Modify: `app/src/main/java/app/backlit/ui/toys/ToyPagerScreen.kt`
- Delete: `app/src/main/java/app/backlit/ui/toys/ClockToy.kt`, `app/src/main/java/app/backlit/ui/MusicScreen.kt`

**Interfaces:**
- Produces:
  - `ClockPage(settings, profile, onUpdate, chrome, onOpenLocation)`
  - `MusicPage(settings, profile, onUpdate, chrome)`
  - A `ToyPagerScreen` that builds a `PageChrome` for each page.

- [ ] **Step 1: Write `ClockPage.kt`**

```kotlin
// app/src/main/java/app/backlit/ui/toys/ClockPage.kt
package app.backlit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.backlit.data.DayLightResolver
import app.backlit.data.LocationMode
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.render.FaceContext
import app.backlit.render.Mode
import app.backlit.render.faces.DayRingFace
import app.backlit.render.faces.Faces
import app.backlit.ui.components.ChipRow
import app.backlit.ui.components.Section
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.StatusInputs
import app.backlit.ui.toys.ToyAction
import app.backlit.ui.toys.ToyStatus
import java.time.Instant
import java.time.ZoneId

@Composable
fun ClockPage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome, onOpenLocation: () -> Unit) {
    val nowMs = rememberTicker(1000, chrome.active)
    val zone = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    var previewSize by rememberSaveable { mutableIntStateOf(profile.size) }
    val resolver = remember { DayLightResolver() }
    val face = Faces.byId(settings.faceId)
    val grid = face.render(
        FaceContext(
            hour = now.hour, minute = now.minute, second = now.second, size = previewSize,
            mode = if (previewSize == 13) Mode.AOD else Mode.ACTIVE, options = settings.faceOptions,
            dayLight = resolver.resolve(settings, now.toLocalDate(), zone),
        ),
    )
    ToyPageScaffold(
        chrome, "CLOCK", grid, ToyStatus.line(ToyId.CLOCK, settings, nowMs, zone, StatusInputs()),
        ToyAction.of(ToyId.CLOCK, chrome.setUp, chrome.supported, hasDrawing = false),
    ) {
        Section("FACE")
        ChipRow(Faces.all.map { it.id to it.label.uppercase() }, face.id) { id -> onUpdate { it.copy(faceId = id) } }
        Section("PREVIEW")
        ChipRow(listOf("25" to "25 × 25", "13" to "13 × 13 ALWAYS-ON"), previewSize.toString()) { previewSize = it.toInt() }
        if (face.id == "analog") {
            SettingRow("Second hand", if (settings.secondHand) "ON" else "OFF") { onUpdate { it.copy(secondHand = !it.secondHand) } }
        }
        if (face.id == DayRingFace.id) {
            SettingRow("Time format", if (settings.use24h) "24H" else "12H") { onUpdate { it.copy(use24h = !it.use24h) } }
            val where = when (settings.locationMode) { LocationMode.FIXED -> "06–18"; else -> settings.placeName ?: "—" }
            SettingRow("Sun times", "$where →") { onOpenLocation() }
        }
    }
}
```

- [ ] **Step 2: Write `MusicPage.kt`**

Move `MusicTab`'s state block unchanged into `MusicPage`. That block runs from `val context = LocalContext.current` to the end of the `LaunchedEffect(viz, resumed)` loop. Make three changes:
- `resumed` becomes `lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && chrome.active`, so the visualizer only runs on the settled page.
- `MusicEngine(25)` stays. The hero is the engine's `grid`.
- Keep `hasAudioPermission`, `OutputVisualizer`, `MusicActivity`, `DemoAudio` and the imports `MusicTab` used.

Then end `MusicPage` with:

```kotlin
    ToyPageScaffold(
        chrome, "MUSIC", grid,
        ToyStatus.line(ToyId.MUSIC, settings, 0, ZoneId.systemDefault(), StatusInputs(micGranted = granted, musicSupported = profile == DeviceProfile.PHONE_3)),
        ToyAction.of(ToyId.MUSIC, chrome.setUp, chrome.supported, hasDrawing = false),
    ) {
        Text(if (live) "LIVE · REACTING TO YOUR MUSIC" else "DEMO AUDIO", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        if (profile == DeviceProfile.PHONE_3 && !granted) {
            if (!denied) AttentionCard("Let Backlit react to your music. Android calls this the microphone permission, but Backlit only reads what your phone is already playing. Nothing is recorded.") {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            } else AttentionCard("Music reactions are off: the toy shows a calm line. Tap to open settings.") {
                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Section("STYLE")
        ChipRow(VizStyles.ids.map { it to VizStyles.label(it) }, VizStyles.normalize(settings.musicStyle)) { id -> onUpdate { it.copy(musicStyle = id) } }
        Section("SENSITIVITY")
        ChipRow(Sensitivity.entries.map { it.name to it.name }, settings.musicSensitivity.name) { n -> onUpdate { it.copy(musicSensitivity = Sensitivity.valueOf(n)) } }
    }
}
```

The signature is `@Composable fun MusicPage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome)`. It lives in package `app.backlit.ui`, with `AttentionCard`, `ChipRow`, `Section`, `StatusInputs`, `ToyAction`, `ToyStatus` and `ToyId` imported.

- [ ] **Step 3: Rewrite the `ToyPagerScreen` body**

Replace the pager body (everything inside `HorizontalPager { page -> … }`) with:

```kotlin
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { toys[it].key }) { page ->
        val id = toys[page]
        val chrome = PageChrome(
            onBack = onBack,
            setUp = ToyCatalog.isSetUp(settings, id),
            supported = profile != DeviceProfile.UNSUPPORTED,
            onTurnOn = { if (!ToysManager.open(context)) onOpen(Route.Setup(Route.Toy(id))) },
            index = page, count = toys.size,
            active = pager.settledPage == page,
        )
        when (id) {
            ToyId.CLOCK -> ClockPage(settings, profile, onUpdate, chrome) { onOpen(Route.Location(Route.Toy(ToyId.CLOCK))) }
            ToyId.MUSIC -> MusicPage(settings, profile, onUpdate, chrome)
            ToyId.CHARGE -> ChargeTab(settings, profile, onUpdate)
            ToyId.CANVAS -> StudioTab(settings, profile, onUpdate) { onOpen(Route.Editor(it, Route.Toy(ToyId.CANVAS))) }
            ToyId.PET -> PetTab(settings, profile, onUpdate)
            ToyId.SAND -> SandTab(settings, profile, onUpdate)
            ToyId.BADGE -> BadgeTab(settings, profile, onUpdate)
        }
    }
```

The Charge, Canvas, Pet, Sand and Badge pages are still the old tabs until Tasks 4–6, so they lose their header and dots for now. That's acceptable mid-branch: the branch only ships at the end. Delete `ClockToy.kt` and `MusicScreen.kt`.

- [ ] **Step 4: Build and run all the tests**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon -q :app:assembleDebug && .superpowers/runtests.sh`
Expected: the build succeeds, with `0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/app/backlit/ui
git commit -m "feat(ui): Clock and Music pages on the new scaffold

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Charge and Canvas pages

**Files:**
- Create: `app/src/main/java/app/backlit/ui/toys/ChargePage.kt`, `CanvasPage.kt`
- Modify: `ToyPagerScreen.kt` (wire them in)
- Delete: `app/src/main/java/app/backlit/ui/ChargeScreen.kt`

- [ ] **Step 1: Write `ChargePage.kt`**

```kotlin
// app/src/main/java/app/backlit/ui/toys/ChargePage.kt
package app.backlit.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.backlit.alerts.AlertConfig
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.importFromUri
import app.backlit.charge.ChargePreviewAnimation
import app.backlit.charge.Moment
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.glyph.DeviceProfile
import app.backlit.render.charge.ChargeStyles
import app.backlit.ui.components.ChipRow
import app.backlit.ui.components.GalleryCard
import app.backlit.ui.components.Notice
import app.backlit.ui.components.OptionSheet
import app.backlit.ui.components.PillButton
import app.backlit.ui.components.Section
import app.backlit.ui.components.SheetAction
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.StatusInputs
import app.backlit.ui.toys.ToyAction
import app.backlit.ui.toys.ToyStatus
import java.time.ZoneId

@Composable
fun ChargePage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val config by runtime.config.collectAsStateWithLifecycle(initialValue = AlertConfig())
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val style = ChargeStyles.byId(settings.chargeStyle)
    var moment by rememberSaveable { mutableStateOf(Moment.CHARGING) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }   // "style" | "plug" | "done"
    var message by remember { mutableStateOf<String?>(null) }
    val battery = remember {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), android.content.Context.RECEIVER_NOT_EXPORTED)?.let {
            it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        }?.takeIf { it >= 0 }
    }
    val now = rememberTicker(50, chrome.active)
    val t = now % ChargePreviewAnimation.durationMs(moment)
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) message = importFromUri(context, runtime, uri, size)
    }
    val names = remember(config.imports) { config.imports.associate { it.id to it.name } }
    val action = ToyAction.of(ToyId.CHARGE, chrome.setUp, chrome.supported, hasDrawing = false)

    ToyPageScaffold(
        chrome, "CHARGE", ChargePreviewAnimation(style, moment).frame(size, t),
        ToyStatus.line(ToyId.CHARGE, settings, now, ZoneId.systemDefault(), StatusInputs(battery = battery)), action,
        onShow = { runtime.preview(ChargePreviewAnimation.idFor(style.id, moment), ChargePreviewAnimation.durationMs(moment)) },
    ) {
        ChipRow(listOf(Moment.STILL to "STILL", Moment.PLUG_IN to "PLUG-IN", Moment.CHARGING to "CHARGING", Moment.DONE to "DONE").map { it.first.name to it.second }, moment.name) { moment = Moment.valueOf(it) }
        SettingRow("Style", "${style.label.uppercase()} →") { sheet = "style" }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Done at", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            PillButton("−", { onUpdate { it.copy(chargeTarget = SettingsRepo.clampTarget(it.chargeTarget - 5)) } })
            Text("${settings.chargeTarget} %", style = MaterialTheme.typography.titleMedium)
            PillButton("+", { onUpdate { it.copy(chargeTarget = SettingsRepo.clampTarget(it.chargeTarget + 5)) } })
        }
        Text("Plays once per charge when your battery reaches this level. If your phone stops charging early (battery protection), set it at or below that limit.",
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        Section("CUSTOM ANIMATIONS")
        SettingRow("Plug-in animation", "${(names[settings.chargePlugInAnim] ?: "Style default").uppercase()} →") { sheet = "plug" }
        SettingRow("Done animation", "${(names[settings.chargeDoneAnim] ?: "Style default").uppercase()} →") { sheet = "done" }
        SettingRow("Import from Glyph Museum", "→") { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        message?.let { Notice(it) }
        Text("Imported animations play instead of the style's own, but can't show your level.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
    }

    when (sheet) {
        "style" -> OptionSheet("STYLE", { sheet = null }) {
            ChargeStyles.all.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { s ->
                        GalleryCard(s.still(size, ChargePreviewAnimation.DEMO_LEVEL), s.label, if (s.id == style.id) "● CHOSEN" else "", s.id == style.id,
                            { onUpdate { it.copy(chargeStyle = s.id) }; sheet = null }, Modifier.weight(1f).padding(bottom = 8.dp))
                    }
                    if (pair.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                }
            }
        }
        "plug", "done" -> OptionSheet(if (sheet == "plug") "PLUG-IN ANIMATION" else "DONE ANIMATION", { sheet = null }) {
            val current = if (sheet == "plug") settings.chargePlugInAnim else settings.chargeDoneAnim
            (listOf("" to "Style default") + names.toList()).forEach { (id, name) ->
                val chosen = id == current || (id == "" && current !in names)
                SheetAction((if (chosen) "● " else "○ ") + name) {
                    onUpdate { if (sheet == "plug") it.copy(chargePlugInAnim = id) else it.copy(chargeDoneAnim = id) }
                    sheet = null
                }
            }
            if (names.isEmpty()) Text("No imported animations yet.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        }
    }
}
```

- [ ] **Step 2: Write `CanvasPage.kt`**

```kotlin
// app/src/main/java/app/backlit/ui/toys/CanvasPage.kt
package app.backlit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.backlit.alerts.AlertsRuntime
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import app.backlit.studio.CanvasHint
import app.backlit.ui.components.GalleryCard
import app.backlit.ui.components.OptionSheet
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.StatusInputs
import app.backlit.ui.toys.ToyAction
import app.backlit.ui.toys.ToyStatus
import java.time.ZoneId

@Composable
fun CanvasPage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome, onEdit: (String?) -> Unit, onOpenStudio: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val drawings by runtime.drawings.collectAsStateWithLifecycle(initialValue = emptyList())
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val chosen = CanvasHint.pick(settings.canvasDrawingId, drawings.map { it.id })
    val entry = drawings.firstOrNull { it.id == chosen }
    val anim = remember(entry) { entry?.let { runtime.library.load(it) } }
    var picking by rememberSaveable { mutableStateOf(false) }
    val now = rememberTicker(50, chrome.active)
    val action = ToyAction.of(ToyId.CANVAS, chrome.setUp, chrome.supported, hasDrawing = entry != null)

    ToyPageScaffold(
        chrome, "CANVAS", anim?.frame(size, now) ?: CanvasHint.frame(size),
        ToyStatus.line(ToyId.CANVAS, settings, now, ZoneId.systemDefault(), StatusInputs(drawingName = entry?.name)), action,
        onShow = { anim?.let { runtime.previewAnimation(it, it.loopMs.coerceIn(3000L, 10_000L)) } },
        onNewDrawing = { onEdit(null) },
    ) {
        SettingRow("Drawing", "${(entry?.name ?: "None").uppercase()} →") { picking = true }
        SettingRow("Open Studio", "→") { onOpenStudio() }
    }

    if (picking) OptionSheet("SHOW ON CANVAS", { picking = false }) {
        if (drawings.isEmpty()) CenterNote("No drawings yet. Make one in Studio.")
        drawings.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { d ->
                    val a = remember(d) { runtime.library.load(d) }
                    GalleryCard(a?.frame(size, now) ?: CanvasHint.frame(size), d.name, if (d.id == chosen) "● ON CANVAS" else "", d.id == chosen,
                        { onUpdate { it.copy(canvasDrawingId = d.id) }; picking = false }, Modifier.weight(1f).padding(bottom = 8.dp))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
```

`CenterNote` is in `app.backlit.ui.components`, so add `import app.backlit.ui.components.CenterNote`.

- [ ] **Step 3: Wire them into the pager, then delete `ChargeScreen.kt`**

In `ToyPagerScreen`, change these two branches:

```kotlin
            ToyId.CHARGE -> ChargePage(settings, profile, onUpdate, chrome)
            ToyId.CANVAS -> CanvasPage(settings, profile, onUpdate, chrome, onEdit = { onOpen(Route.Editor(it, Route.Toy(ToyId.CANVAS))) }, onOpenStudio = { onOpen(Route.Studio) })
```

- [ ] **Step 4: Build and run all the tests**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon -q :app:assembleDebug && .superpowers/runtests.sh`
Expected: the build succeeds, with `0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/app/backlit/ui
git commit -m "feat(ui): Charge and Canvas pages (style and drawing sheets)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Pet and Sand pages

**Files:**
- Create: `app/src/main/java/app/backlit/ui/toys/PetPage.kt`, `SandPage.kt`
- Modify: `ToyPagerScreen.kt`
- Delete: `app/src/main/java/app/backlit/ui/PetScreen.kt`, `app/src/main/java/app/backlit/ui/SandScreen.kt`

- [ ] **Step 1: Write `PetPage.kt`**

`PetPage` keeps `PetTab`'s brain logic unchanged. That's everything from `val sleep = …` to the battery `LaunchedEffect(brain)`. The only change is that `now` comes from `rememberTicker(50, chrome.active)`.

```kotlin
// app/src/main/java/app/backlit/ui/toys/PetPage.kt
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.backlit.pet.MoodState
import app.backlit.pet.PetArt
import app.backlit.pet.PetBrain
import app.backlit.pet.PetInsight
import app.backlit.pet.PetKind
import app.backlit.pet.PetPreviewAnimation
import app.backlit.pet.Pose
import app.backlit.pet.SleepWindow
import app.backlit.ui.components.GalleryCard
import app.backlit.ui.components.OptionSheet
import app.backlit.ui.components.PillButton
import app.backlit.ui.components.Section
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.StatusInputs
import app.backlit.ui.toys.ToyAction
import app.backlit.ui.toys.ToyStatus
import java.time.ZoneId

@Composable
fun PetPage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val sleep = SleepWindow(settings.petSleepStart, settings.petSleepEnd)
    val kind = PetKind.byId(settings.petKind)
    val petName = SettingsRepo.petNameFor(settings, kind)
    val zone = ZoneId.systemDefault()
    val brain = remember(settings.petMood, settings.petMoodAt, settings.petSleepStart, settings.petSleepEnd) {
        PetBrain(MoodState(settings.petMood.toDouble(), settings.petMoodAt), { sleep }, zone)
    }
    LaunchedEffect(brain) {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), android.content.Context.RECEIVER_NOT_EXPORTED)
        if (i != null) {
            val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            brain.onCharging(i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0, lv, System.currentTimeMillis())
        }
    }
    val now = rememberTicker(50, chrome.active)
    val pose = brain.pose(now, size)
    val mood = brain.mood(now)
    val word = when (pose.base) {
        Base.HAPPY -> "HAPPY"; Base.CONTENT -> "CONTENT"; Base.BORED -> "BORED"
        Base.SAD -> "SAD"; Base.ASLEEP -> "ASLEEP"; Base.MUNCH -> "SNACKING"
    }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var howOpen by rememberSaveable { mutableStateOf(false) }

    ToyPageScaffold(
        chrome, "PET", PetArt.frame(kind, size, pose, now),
        ToyStatus.line(ToyId.PET, settings, now, zone, StatusInputs(petBase = word, petMood = mood)),
        ToyAction.of(ToyId.PET, chrome.setUp, chrome.supported, hasDrawing = false),
        onShow = { runtime.preview(PetPreviewAnimation.idFor(kind, Base.HAPPY), 3000L) },
        heroClick = { brain.onLongPress(System.currentTimeMillis(), true) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = BuildConfig.DEBUG) {
                val next = when { mood >= 70 -> 55f; mood >= 40 -> 25f; mood >= 15 -> 5f; else -> 90f }
                onUpdate { it.copy(petMood = next, petMoodAt = System.currentTimeMillis()) }
            },
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(10) { i -> Box(Modifier.padding(horizontal = 3.dp).size(10.dp).background(if (i < (mood + 5) / 10) BacklitColors.White else BacklitColors.LedOff, CircleShape)) }
        }
        Text(PetInsight.hint(brain.moodExact(now), now, sleep, zone, charging = pose.base == Base.MUNCH, asleep = pose.base == Base.ASLEEP),
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
        SettingRow("Pet", "${kind.id.uppercase()} →") { choosing = true }
        Section("NAME")
        var name by remember(kind) { mutableStateOf(petName) }
        OutlinedTextField(
            value = name,
            onValueChange = { v ->
                name = v.take(12)
                SettingsRepo.petNameToSave(name)?.let { clean -> if (clean != petName) onUpdate { SettingsRepo.withPetName(it, kind, clean) } }
            },
            placeholder = { Text(kind.defaultName) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Section("SLEEP HOURS")
        HourRow("From", settings.petSleepStart) { h -> onUpdate { it.copy(petSleepStart = h) } }
        HourRow("To", settings.petSleepEnd) { h -> onUpdate { it.copy(petSleepEnd = h) } }
        SettingRow("How $petName's mood works", if (howOpen) "−" else "+") { howOpen = !howOpen }
        if (howOpen) {
            Text(
                listOf(
                    "↑ Long press to pet: +15 (once every 30 s)",
                    "↑ Charging with him on the Glyph: +1 a minute",
                    "↑ Peekaboo +5 · calming him down +10",
                    "↓ Awake and ignored: about −10 an hour",
                    "↓ Big shake −3 · getting angry −5",
                    "Asleep: no change. He never drops below 0.",
                    "70+ happy · 40+ content · 15+ bored · below 15 sad",
                ).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        Text("Tap him to pet him here. Long press the Glyph button to pet him on the back.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(top = 6.dp))
    }

    if (choosing) OptionSheet("CHOOSE YOUR PET", { choosing = false }) {
        PetKind.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    GalleryCard(PetArt.frame(k, size, Pose(brain.base(now), null, 0, 0, 0, 0, 62), now), SettingsRepo.petNameFor(settings, k), k.id.uppercase(), k == kind,
                        { onUpdate { it.copy(petKind = k.id) }; choosing = false }, Modifier.weight(1f).padding(bottom = 8.dp))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HourRow(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        PillButton("−", { onChange((hour + 23) % 24) })
        Text("%02d:00".format(hour), style = MaterialTheme.typography.titleMedium)
        PillButton("+", { onChange((hour + 1) % 24) })
    }
}
```

- [ ] **Step 2: Write `SandPage.kt`**

Take `SandTab`'s content and put it in the scaffold:

```kotlin
// app/src/main/java/app/backlit/ui/toys/SandPage.kt
package app.backlit.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.alerts.AlertsRuntime
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.glyph.DeviceProfile
import app.backlit.sand.HourglassShape
import app.backlit.sand.Phase
import app.backlit.sand.SandAlarm
import app.backlit.sand.SandArt
import app.backlit.sand.SandPreviewAnimation
import app.backlit.sand.TimerState
import app.backlit.ui.components.ChipRow
import app.backlit.ui.components.PillButton
import app.backlit.ui.components.Section
import app.backlit.ui.components.rememberTicker
import app.backlit.ui.home.ToyId
import app.backlit.ui.toys.StatusInputs
import app.backlit.ui.toys.ToyAction
import app.backlit.ui.toys.ToyStatus
import java.time.ZoneId

@Composable
fun SandPage(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, chrome: PageChrome) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val shape = HourglassShape.forSize(if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25)
    val now = rememberTicker(200, chrome.active)
    val st = TimerState.decode(settings.sandTimer).tick(now)
    val presets = settings.sandPresets
    val busy = st.phase == Phase.RUNNING || st.phase == Phase.PAUSED
    var editing by rememberSaveable { mutableStateOf(false) }
    var howOpen by rememberSaveable { mutableStateOf(false) }
    val canExact = context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    val exactOn = settings.sandExact && canExact

    ToyPageScaffold(
        chrome, "SAND", SandArt.still(shape, st, now),
        ToyStatus.line(ToyId.SAND, settings, now, ZoneId.systemDefault(), StatusInputs()),
        ToyAction.of(ToyId.SAND, chrome.setUp, chrome.supported, hasDrawing = false),
        onShow = { runtime.preview(SandPreviewAnimation.RUNNING_ID, 4000L) },
    ) {
        Section("TIMES")
        if (editing) {
            ChipRow(presets.map { it.toString() to "$it ×" }, "") { m -> if (presets.size > 1) onUpdate { it.copy(sandPresets = SettingsRepo.cleanPresets(it.sandPresets - m.toInt())) } }
            var add by remember { mutableIntStateOf(15) }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                PillButton("−", { add = (add - 1).coerceAtLeast(1) })
                Text("$add MIN", style = MaterialTheme.typography.titleMedium)
                PillButton("+", { add = (add + 1).coerceAtMost(99) })
                PillButton("ADD", { if (presets.size < 8) onUpdate { it.copy(sandPresets = SettingsRepo.cleanPresets(it.sandPresets + add)) } })
            }
        } else {
            val selected = if (busy) "" else presets.getOrNull(st.presetIndex.coerceIn(0, presets.size - 1))?.takeIf { it * TimerState.MIN == st.durationMs }?.toString() ?: ""
            ChipRow(presets.map { it.toString() to "$it MIN" }, selected) { m ->
                if (!busy) onUpdate { it.copy(sandTimer = st.select(presets.indexOf(m.toInt()), presets).encode()) }
            }
        }
        SettingRow("Edit times", if (editing) "DONE" else "→") { editing = !editing }
        Text(if (busy) "A timer is running. Long press the Glyph twice to change it." else "Tap a time, or long press the Glyph button.",
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        Section("WHEN TIME'S UP")
        ChipRow(listOf("glyph" to "GLYPH ONLY", "vibrate" to "VIBRATE", "chime" to "+ CHIME"), settings.sandAlert) { id -> onUpdate { it.copy(sandAlert = id) } }
        SettingRow("Ring exactly on time", if (exactOn) "ON" else "OFF") {
            if (exactOn) { onUpdate { it.copy(sandExact = false) }; SandAlarm.sync(context, st, false) }
            else {
                onUpdate { it.copy(sandExact = true) }
                if (canExact) SandAlarm.sync(context, st, true)
                else context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        SettingRow("How it works", if (howOpen) "−" else "+") { howOpen = !howOpen }
        if (howOpen) {
            Text(
                listOf(
                    "⟲ Flip the phone over to start, like a real hourglass",
                    "⟲ Flip it mid-way and the sand runs back: time left becomes time run",
                    "↔ Lay it on its side to pause",
                    "▭ Face-down on a desk keeps it running",
                    "● Long press: pick a time. While it runs, the first press shows the minutes left; press again within 2 s to change it",
                ).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}
```

- [ ] **Step 3: Wire them into the pager, then delete `PetScreen.kt` and `SandScreen.kt`**

```kotlin
            ToyId.PET -> PetPage(settings, profile, onUpdate, chrome)
            ToyId.SAND -> SandPage(settings, profile, onUpdate, chrome)
```

- [ ] **Step 4: Build and run all the tests**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon -q :app:assembleDebug && .superpowers/runtests.sh`
Expected: the build succeeds, with `0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/app/backlit/ui
git commit -m "feat(ui): Pet and Sand pages on the scaffold

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Badge page

**Files:**
- Create: `app/src/main/java/app/backlit/ui/toys/BadgePage.kt`
- Modify: `ToyPagerScreen.kt`
- Delete: `app/src/main/java/app/backlit/ui/BadgeScreen.kt`

- [ ] **Step 1: Write `BadgePage.kt`**

Move `BadgeScreen.kt` into `BadgePage.kt`. Keep `kindLabel` and `BadgeEditor` unchanged, and keep `save()`, `editing`, the `LaunchedEffect(settings.badgeMessages) { editing = null }` guard, `idle`, and the message rows with ↑ ↓ EDIT. Change four things:

1. Rename `BadgeTab` to `BadgePage(settings, profile, onUpdate, chrome: PageChrome)`, and replace its `now` ticker with `val now = rememberTicker(50, chrome.active)`.
2. Delete the `Notice("Turn on Backlit Badge …")`, the top `MatrixPreview`, the `Text(text.ifBlank …)` line and the final `SHOW ON GLYPH` chip. Wrap the remaining content in:

```kotlin
    ToyPageScaffold(
        chrome, "BADGE", BadgeArt.frame(size, current, text, now - opened, BadgeArt.FLASH_MS),
        text.ifBlank { "(ICON ONLY)" },
        ToyAction.of(ToyId.BADGE, chrome.setUp, chrome.supported, hasDrawing = false),
        onShow = { runtime.preview(BadgePreviewAnimation.idFor(current.icon, text), 6000L) },
    ) {
        Section("MESSAGES")
        // … the existing message rows (inline editor removed, see 3) and "+ ADD MESSAGE" …
        Text("Tap a message to show it. Long press the Glyph button to switch messages on the back.",
            style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(top = 10.dp))
    }
```

3. **The editor becomes a sheet.** Remove the inline `if (editing == i) BadgeEditor(…) else Row(…)` branching, so every message always renders as its row. After the scaffold, add:

```kotlin
    editing?.let { i ->
        val isNew = i == -1
        val start = if (isNew) BadgeMessage("", "heart") else messages.getOrNull(i)
        if (start == null) { editing = null } else OptionSheet(if (isNew) "NEW MESSAGE" else "EDIT MESSAGE", { editing = null }) {
            BadgeEditor(start, settings.use24h, canDelete = !isNew && messages.size > 1,
                onSave = { e -> if (isNew) save(messages + e, messages.size, restart = true) else save(messages.toMutableList().also { it[i] = e }, active, restart = i == active); editing = null },
                onDelete = { save(messages.filterIndexed { k, _ -> k != i }, BadgeMessage.afterDelete(active, i, messages.size), restart = i == active); editing = null },
                onCancel = { editing = null })
        }
    }
```

4. **"+ ADD MESSAGE"** becomes `SettingRow("+ Add message", "→") { if (idle) editing = -1 }`, shown while `messages.size < MAX_MESSAGES`. Replace the row chips `SquareChip("↑"…)`, `"↓"` and `"EDIT"` with `PillButton`s using the same lambdas.

- [ ] **Step 2: Wire it into the pager, then delete `BadgeScreen.kt`**

`ToyId.BADGE -> BadgePage(settings, profile, onUpdate, chrome)`.

- [ ] **Step 3: Build and run all the tests**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon -q :app:assembleDebug && .superpowers/runtests.sh`
Expected: the build succeeds, with `0 failures, 0 errors`.

- [ ] **Step 4: Commit**

```bash
git add -A app/src/main/java/app/backlit/ui
git commit -m "feat(ui): Badge page with the message editor in a sheet

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Studio gallery

**Files:**
- Rewrite: `app/src/main/java/app/backlit/ui/StudioScreen.kt` (replace `StudioTab` with the new `StudioScreen`; keep `LoopingPreview`, which the Alerts code may use)
- Modify: `app/src/main/java/app/backlit/ui/ToolScreens.kt` (remove the old `StudioScreen` wrapper)

**Interfaces:**
- Consumes: `AlertsRuntime` (`drawings`, `library.load`, `loadDrawing`, `saveDrawing`, `deleteImport`), `importDrawingFromUri`, `shareAnimation`, `CanvasHint`, and the controls.
- Produces: `StudioScreen(settings, profile, onUpdate, onEdit: (String?) -> Unit, onBack)`, with the same signature as part 1's wrapper.

- [ ] **Step 1: Write the new `StudioScreen`**

```kotlin
@Composable
fun StudioScreen(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit, onEdit: (String?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val drawings by runtime.drawings.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val onCanvas = CanvasHint.pick(settings.canvasDrawingId, drawings.map { it.id })
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val now = rememberTicker(50)
    var barPx by remember { mutableIntStateOf(0) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { message = importDrawingFromUri(context, runtime, uri, size) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = with(LocalDensity.current) { barPx.toDp() } + 16.dp)) {
            PageHeader("STUDIO", onBack)
            Text("${drawings.size} DRAWINGS · TAP ONE FOR OPTIONS", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
            message?.let { Notice(it) }
            if (drawings.isEmpty()) CenterNote("Draw your own pictures and animations for the Glyph. Use them for calls, devices, charging, or on the Canvas toy.")
            drawings.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { e ->
                        val anim = remember(e) { runtime.library.load(e) }
                        val frames = (anim as? ImportedAnimation)?.durations?.size ?: 1
                        GalleryCard(anim?.frame(size, now) ?: PixelGrid(size), e.name, if (e.id == onCanvas) "● ON CANVAS" else "$frames FRAME" + (if (frames == 1) "" else "S"),
                            e.id == onCanvas, { openId = e.id; renaming = false; confirmDelete = false }, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        BottomActionBar(
            primary = ActionSpec("+ NEW DRAWING", ActionStyle.PRIMARY) { onEdit(null) },
            secondary = ActionSpec("IMPORT", ActionStyle.OUTLINE) { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onHeight = { barPx = it }, modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // The sheet looks its drawing up in the live list, so a drawing deleted elsewhere closes it instead of crashing.
    val open = drawings.firstOrNull { it.id == openId }
    if (openId != null && open == null) openId = null
    if (open != null) OptionSheet(open.name.uppercase(), { openId = null }) {
        val anim = remember(open) { runtime.library.load(open) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { MatrixPreview(anim?.frame(size, now) ?: PixelGrid(size), Modifier.fillMaxWidth(0.5f)) }
        when {
            renaming -> {
                var name by remember(open.id) { mutableStateOf(open.name) }
                OutlinedTextField(value = name, onValueChange = { name = it.take(MAX_NAME) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                SheetAction("SAVE") {
                    scope.launch { runtime.loadDrawing(open.id, size)?.let { d -> runtime.saveDrawing(d.copy(name = name.trim().ifBlank { open.name }), open.id) } }
                    renaming = false
                }
            }
            confirmDelete -> {
                Text("Delete \"${open.name}\"? Contacts, devices or charging that use it go back to their default.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                SheetAction("DELETE", danger = true) { runtime.deleteImport(open.id); openId = null }
                SheetAction("CANCEL") { confirmDelete = false }
            }
            else -> {
                SheetAction("EDIT") { openId = null; onEdit(open.id) }
                if (open.id != onCanvas) SheetAction("SHOW ON CANVAS") { onUpdate { it.copy(canvasDrawingId = open.id) }; openId = null }
                SheetAction("SHOW ON GLYPH") { anim?.let { runtime.previewAnimation(it, it.loopMs.coerceIn(3000L, 10_000L)) } }
                SheetAction("SHARE") { (anim as ImportedAnimation?)?.let { shareAnimation(context, it, open.name) } }
                SheetAction("RENAME") { renaming = true }
                SheetAction("DELETE", danger = true) { confirmDelete = true }
            }
        }
    }
}
```

Imports, beyond the existing file's: `app.backlit.ui.components.*` (`ActionSpec`, `ActionStyle`, `BottomActionBar`, `CenterNote`, `GalleryCard`, `Notice`, `OptionSheet`, `PageHeader`, `SheetAction`, `rememberTicker`), plus `androidx.compose.foundation.layout.Box`, `androidx.compose.ui.platform.LocalDensity`, `androidx.compose.runtime.mutableIntStateOf` and `app.backlit.render.PixelGrid`.

Delete the old `StudioTab`, and delete the `StudioScreen` wrapper in `ToolScreens.kt`.

- [ ] **Step 2: Build and run all the tests**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon -q :app:assembleDebug && .superpowers/runtests.sh`
Expected: the build succeeds, with `0 failures, 0 errors`.

- [ ] **Step 3: Commit**

```bash
git add -A app/src/main/java/app/backlit/ui
git commit -m "feat(ui): Studio gallery with an action sheet and NEW / IMPORT bar

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Alerts (segmented, Bluetooth connect alerts in DEVICES)

**Files:**
- Rewrite: `app/src/main/java/app/backlit/ui/AlertsScreen.kt` (replace `AlertsTab` with `AlertsScreen`; keep `DISCLOSURE`, `readContactName`, `hasBtPermission`, `bondedDevices`, `nothingCallLightsOn`, `MiniPreview` and `nameOf`)
- Modify: `app/src/main/java/app/backlit/ui/ToolScreens.kt` (remove the old `AlertsScreen` wrapper; the file may become empty, in which case delete it)

**Interfaces:**
- Consumes: `AlertsRuntime` (`config`, `update`, `preview`, `library`, `deleteImport`, `copyImportToDrawing`), `importFromUri`, `AlertsAttention`, `AlertsSegment`, `NameMatch`, `ContactRule`, `DeviceRule`, `BuiltInAnimations`, `KIND_DRAWING`, and the controls.
- Produces: `AlertsScreen(profile, onEdit: (String?) -> Unit, onBack)`.

- [ ] **Step 1: Write the new `AlertsScreen`**

```kotlin
@Composable
fun AlertsScreen(profile: DeviceProfile, onEdit: (String?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val config by runtime.config.collectAsStateWithLifecycle(initialValue = AlertConfig())
    val scope = rememberCoroutineScope()
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var listenerOn by remember { mutableStateOf(true) }
    var btGranted by remember { mutableStateOf(true) }
    var callLights by remember { mutableStateOf(false) }
    LaunchedEffect(resumed) {
        if (resumed) {
            listenerOn = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            btGranted = hasBtPermission(context)
            callLights = nothingCallLightsOn(context)
        }
    }
    var segment by rememberSaveable { mutableStateOf(AlertsSegment.CONTACTS) }
    var ruleKey by rememberSaveable { mutableStateOf<String?>(null) }      // "c:<name>" or "d:<address>"
    var addingDevice by rememberSaveable { mutableStateOf(false) }
    var animId by rememberSaveable { mutableStateOf<String?>(null) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var barPx by remember { mutableIntStateOf(0) }
    val now = rememberTicker(50)
    val animations: List<GlyphAnimation> = remember(config.imports) { BuiltInAnimations.all + config.imports.mapNotNull { runtime.library.load(it) } }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        val name = uri?.let { readContactName(context, it) } ?: return@rememberLauncherForActivityResult
        scope.launch {
            runtime.update { c -> if (c.contacts.any { NameMatch.matches(it.name, name) }) c else c.copy(contacts = c.contacts + ContactRule(name, BuiltInAnimations.DEFAULT_CONTACT)) }
        }
        ruleKey = "c:$name"
    }
    val btPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> btGranted = ok; if (ok) addingDevice = true }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) message = importFromUri(context, runtime, uri, size) }

    val attention = AlertsAttention.pick(listenerOn, btGranted, config.contacts.size, config.devices.size, segment)
    val bar = when (segment) {
        AlertsSegment.CONTACTS -> ActionSpec("+ ADD CONTACT", ActionStyle.PRIMARY) { pickContact.launch(null) }
        AlertsSegment.DEVICES -> ActionSpec("+ ADD DEVICE", ActionStyle.PRIMARY) { if (btGranted) addingDevice = true else btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }
        AlertsSegment.ANIMATIONS -> ActionSpec("IMPORT FROM GLYPH MUSEUM", ActionStyle.PRIMARY) { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = with(LocalDensity.current) { barPx.toDp() } + 16.dp)) {
            PageHeader("ALERTS", onBack)
            when (attention) {
                Attention.NOTIFICATION_ACCESS -> AttentionCard("Allow notification access so calls light the Glyph") {
                    context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                Attention.NEARBY -> AttentionCard("Allow Nearby devices so Backlit can notice your devices connecting") { btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                null -> Unit
            }
            SegmentedControl(AlertsSegment.entries.map { it.name to it.label }, segment.name) { segment = AlertsSegment.valueOf(it) }
            message?.let { Notice(it) }
            when (segment) {
                AlertsSegment.CONTACTS -> {
                    if (config.contacts.isEmpty()) CenterNote("Add the people who matter. Their calls play an animation on the Glyph as they ring, and again if you miss them.")
                    config.contacts.forEach { r -> RuleListRow(initials(r.name), r.name, animations.nameOf(r.animationId), animations.firstOrNull { it.id == r.animationId }, size, now) { ruleKey = "c:${r.name}" } }
                }
                AlertsSegment.DEVICES -> {
                    Text("Plays when the device connects.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
                    if (config.devices.isEmpty()) CenterNote("Add a Bluetooth device — earbuds, car, watch — and its animation plays on the Glyph when it connects.")
                    config.devices.forEach { r -> RuleListRow("◖◗", r.name, animations.nameOf(r.animationId), animations.firstOrNull { it.id == r.animationId }, size, now) { ruleKey = "d:${r.address}" } }
                }
                AlertsSegment.ANIMATIONS -> {
                    Text("Tap to preview on the Glyph.", style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
                    animations.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { a ->
                                val own = a.id.startsWith("import:")
                                GalleryCard(a.frame(size, now), a.name, if (own) "TAP · ⋯" else "BUILT-IN", false, {
                                    runtime.preview(a.id)
                                    if (own) animId = a.id
                                }, Modifier.weight(1f))
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            SettingRow("About alerts", if (aboutOpen) "−" else "+") { aboutOpen = !aboutOpen }
            if (aboutOpen) {
                Text(
                    listOfNotNull(DISCLOSURE,
                        if (callLights) "Nothing's ringtone lights take over the matrix a moment into each call. Backlit plays your contact's animation as the call starts, and again if you miss it." else null,
                        "Tip: set Backlit Clock as your always-on toy so alerts always show.").joinToString("\n\n"),
                    style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
        BottomActionBar(bar, onHeight = { barPx = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // Rule sheet: looks the rule up in the live config, so a removed rule just closes the sheet.
    val contact = ruleKey?.takeIf { it.startsWith("c:") }?.let { k -> config.contacts.firstOrNull { "c:${it.name}" == k } }
    val device = ruleKey?.takeIf { it.startsWith("d:") }?.let { k -> config.devices.firstOrNull { "d:${it.address}" == k } }
    if (ruleKey != null && contact == null && device == null) ruleKey = null
    if (contact != null || device != null) {
        val title = contact?.name ?: device!!.name
        val selected = contact?.animationId ?: device!!.animationId
        OptionSheet(title.uppercase(), { ruleKey = null }) {
            animations.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { a ->
                        GalleryCard(a.frame(size, now), a.name, if (a.id == selected) "● CHOSEN" else "", a.id == selected, {
                            scope.launch {
                                runtime.update { c ->
                                    if (contact != null) c.copy(contacts = c.contacts.map { if (it == contact) it.copy(animationId = a.id) else it })
                                    else c.copy(devices = c.devices.map { if (it == device) it.copy(animationId = a.id) else it })
                                }
                            }
                        }, Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            SheetAction("PREVIEW ON GLYPH") { runtime.preview(selected) }
            SheetAction("REMOVE", danger = true) {
                scope.launch { runtime.update { c -> if (contact != null) c.copy(contacts = c.contacts - contact) else c.copy(devices = c.devices - device!!) } }
                ruleKey = null
            }
        }
    }

    if (addingDevice) OptionSheet("ADD A BLUETOOTH DEVICE", { addingDevice = false }) {
        val bonded = remember { bondedDevices(context) }.filter { (addr, _) -> config.devices.none { it.address.equals(addr, ignoreCase = true) } }
        if (bonded.isEmpty()) CenterNote("No paired Bluetooth devices found.")
        bonded.forEach { (addr, name) ->
            SheetAction(name) {
                scope.launch { runtime.update { c -> c.copy(devices = c.devices + DeviceRule(addr, name, BuiltInAnimations.DEFAULT_DEVICE)) } }
                addingDevice = false
                ruleKey = "d:$addr"
            }
        }
    }

    val ownAnim = animId?.let { id -> config.imports.firstOrNull { it.id == id } }
    if (animId != null && ownAnim == null) animId = null
    if (ownAnim != null) OptionSheet(ownAnim.name.uppercase(), { animId = null }) {
        val isDrawing = ownAnim.kind == KIND_DRAWING
        SheetAction(if (isDrawing) "EDIT IN STUDIO" else "COPY TO STUDIO AND EDIT") {
            if (isDrawing) onEdit(ownAnim.id) else scope.launch { runtime.copyImportToDrawing(ownAnim.id, size)?.let { onEdit(it) } }
            animId = null
        }
        SheetAction("DELETE", danger = true) { runtime.deleteImport(ownAnim.id); animId = null }
    }
}

@Composable
private fun RuleListRow(badge: String, name: String, animName: String, anim: GlyphAnimation?, size: Int, now: Long, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).border(1.dp, BacklitColors.Line, CircleShape), contentAlignment = Alignment.Center) { Text(badge, style = MaterialTheme.typography.labelSmall) }
        Column(Modifier.weight(1f)) {
            Text(name.uppercase(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(animName, style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
        }
        MatrixPreview(anim?.frame(size, now) ?: PixelGrid(size), Modifier.size(34.dp))
    }
}

private fun initials(name: String): String = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
```

Keep the file's existing helper functions. Delete the old `AlertsTab`, `SectionTitle`, `RuleRow` and `AnimationPicker`. `MiniPreview` can go too if nothing else uses it.

- [ ] **Step 2: Update `MainActivity`**

`MainActivity` already calls `AlertsScreen(profile, onEdit = …, onBack = up)` and `StudioScreen(...)`, so the signatures are unchanged. Delete `ToolScreens.kt` if it's now empty.

- [ ] **Step 3: Build and run all the tests**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon -q :app:assembleDebug && .superpowers/runtests.sh`
Expected: the build succeeds, with `0 failures, 0 errors`.

- [ ] **Step 4: Commit**

```bash
git add -A app/src/main/java/app/backlit/ui
git commit -m "feat(ui): segmented Alerts (contacts, Bluetooth devices, animations) with one attention card

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Home polish, cleanup, docs and on-device check

**Files:**
- Modify: `app/src/main/java/app/backlit/ui/HomeScreen.kt`, `app/src/main/java/app/backlit/ui/home/ToyThumbs.kt`
- Modify: `app/src/main/java/app/backlit/ui/Components.kt` (remove anything no longer used)
- Modify: `README.md`, `docs/testing/device-checklist.md`

- [ ] **Step 1: Home polish**

- **`HomeScreen`:** replace the `now` state and its `LaunchedEffect` loop with `val now = rememberTicker(50)`. That pauses while the app isn't visible.
- **Canvas thumbnail:** replace the `canvasAnim` `remember` with a background load:

```kotlin
    val canvasId = CanvasHint.pick(settings.canvasDrawingId, drawings.map { it.id })
    val canvasAnim by produceState<GlyphAnimation?>(null, canvasId, drawings) {
        value = withContext(Dispatchers.IO) { canvasId?.let { id -> runtime.library.importedOnly(id, drawings) } }
    }
```

- **`ToyThumbs`:** cache the sand animation with `private val sand by lazy { SandPreviewAnimation.parse(SandPreviewAnimation.RUNNING_ID)!! }`, and use `sand.frame(size, nowMs)`.
- **Imports:** remove the unused imports left over in `HomeScreen.kt`.

- [ ] **Step 2: Remove unused components**

Run `grep -rn "SquareChip(\|DashedDivider(\|ScreenHeader(" app/src/main/java`. Delete each function in `Components.kt` that has no remaining callers. The editor, setup and about screens may still use some of them; keep those.

- [ ] **Step 3: Docs**

- **README:**
  - Add to the "Project structure" `ui/` lines: `toys/` (`ToyPageScaffold`, one `XxxPage` per toy, `ToyStatus`, `ToyAction`), `alerts/` (`AlertsAttention`) and `components/Controls.kt`.
  - Under Features, add: "Studio is a gallery of your drawings. Alerts has three tabs: CONTACTS, DEVICES (Bluetooth connect alerts) and ANIMATIONS."
  - Add the part 2 spec and plan to the "Docs, specs and plans" table.
- **Device checklist:** append this section:

```markdown
## UI redesign — part 2
- [ ] Every toy page: big preview, name, dots, status line, settings, bottom button; nothing hides under the bar (scroll to the end, open "How … works")
- [ ] Swiping: only the visible page animates; Music only listens on its own page
- [ ] Charge: style sheet, done-at stepper, plug-in/done animation sheets, import
- [ ] Canvas: drawing sheet, Open Studio, + NEW DRAWING when there are none
- [ ] Pet: tap to pet, pet chooser sheet, name, sleep hours; keyboard doesn't hide the field
- [ ] Sand: times, edit, when time's up, exact switch
- [ ] Badge: tap to show, ↑ ↓, EDIT and + Add message open the editor sheet
- [ ] Studio: gallery, ON CANVAS outline, sheet actions (edit, canvas, glyph, share, rename, delete), NEW and IMPORT
- [ ] Alerts: attention card only when needed; CONTACTS add/edit/remove; DEVICES (Bluetooth) add from paired list, animation plays on connect; ANIMATIONS preview, delete, edit in Studio, import
- [ ] Home previews pause when the app is in the background
- [ ] 4a Pro: 13×13 heroes, no Music page
```

- [ ] **Step 4: Run the full suite, build and install**

Run: `source .superpowers/env.sh && .superpowers/runtests.sh && ./gradlew --no-daemon :app:installDebug`
Expected: `0 failures, 0 errors`, and `Installed on 1 device.`

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/java/app/backlit README.md docs/testing/device-checklist.md
git commit -m "feat(ui): Home ticker pauses in background; cleanup; part 2 docs

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
