# Backlit Message Badge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship "Backlit Badge", a Glyph toy that shows a status sign (a pixel icon plus scrolling text, with live countdown and until-time messages), together with a BADGE tab for managing up to 8 messages.

**Architecture:** A pure Kotlin core in `app.backlit.badge` (`BadgeMessage`, `BadgeText`, `BadgeFont`, `BadgeIcons`, `BadgeArt`, `BadgePreviewAnimation`), each with JVM tests. `BadgeToyService` follows `CanvasToyService`: no sensors, a 50 ms loop while ACTIVE, and one still per minute in AOD. Messages are stored as a JSON string in Settings.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore with kotlinx-serialization, the Nothing GlyphMatrix SDK, and JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-06-backlit-message-badge-design.md`. The mockups are `docs/superpowers/mockups/2026-10-06-badge-styles.html` (style A) and `docs/superpowers/mockups/2026-10-06-badge-icons.html` (the 13×13 layout and the solid moon).

## Global Constraints
- **Tooling:** run `source .superpowers/env.sh` before Gradle. Run tests with `.superpowers/runtests.sh [--tests '<pattern>']`; its last line is `N tests, F failures, E errors`.
- **Brightness:** 0..1 maps to 0..255 with `(v * 255).px()`. Icons are 0.85 (217), text is 1.0 (255), and the flash is 1.0.
- **Layout:**
  - 25×25: the 9×9 icon at x = 8, y = 3; 5×7 text on rows 14–20; 1 px of scroll per 55 ms.
  - 13×13: the 5×5 icon at x = 4, y = 1; 3×5 text on rows 7–11; 1 px of scroll per 80 ms.
  - One ticker loop is `textWidth + size + 4` px, and the text enters from x = size.
- **Always-on still:**
  - 25×25: the icon at y = 4, and the short text centred at y = 15.
  - 13×13: the icon at y = 1, and the short text cut to 3 characters, centred at y = 7.
- **Flash:** lasts 600 ms after the message changes. It is bright from 0–150 ms and from 300–450 ms.
- **Text:** uppercase A–Z, 0–9, space and `: ! ? . ' -`, at most 32 characters. A list holds 1..8 messages. `minutes` is 1..180 and `untilMinuteOfDay` is 0..1439.
- **No new permissions.**
- **Commits:** every commit ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never commit the Nothing SDK aar, and never change system settings on the phone.

## Review Focus
1. **Editing or deleting the current message from the app while the toy is showing.** The toy should switch cleanly: no crash, no out-of-range index, and a flash for the new message. Pinned by `afterDeletePicksTheNext` and `activeIndexClamps` (Task 1).
2. **Very long text and many messages** (32 characters × 8 messages). The ticker must loop fully and never draw outside the mask. Pinned by `everyIconAndLongTextStayInsideAtBothSizes` (Task 4).
3. **An "until" message picked after its time, and messages that cross midnight.** These should mean tomorrow, never "already over". Pinned by `untilPickedAfterItsTimeMeansTomorrow` (Task 2).
4. **A countdown of an hour or more.** It must show `h:mm:ss`, and the short form must switch to hours above 99 minutes. Pinned by `longCountdownsUseHours` (Task 2).
5. **Corrupt or old stored JSON.** It should fall back to the starter list with no crash. Pinned by `badJsonFallsBackToStarters` (Task 1).

---

## File Structure
| File | Responsibility |
|---|---|
| `app/src/main/java/app/backlit/badge/BadgeMessage.kt` | the message model, cleaning, the starter list, list JSON, and the index helpers |
| `app/src/main/java/app/backlit/badge/BadgeText.kt` | live scroll text and short text (countdown, until, soon) |
| `app/src/main/java/app/backlit/badge/BadgeFont.kt` | 5×7 (25×25) and 3×5 (13×13) text: width, draw, supports |
| `app/src/main/java/app/backlit/badge/BadgeIcons.kt` | the 8 icons at 9×9 and 5×5 |
| `app/src/main/java/app/backlit/badge/BadgeArt.kt` | ticker frame, flash, always-on still, and icon tiles for the UI |
| `app/src/main/java/app/backlit/badge/BadgePreviewAnimation.kt` | `badge:<icon>:<text>` GlyphAnimation |
| `app/src/main/java/app/backlit/glyph/BadgeToyService.kt` | the toy |
| `app/src/main/java/app/backlit/ui/BadgeScreen.kt` | the BADGE tab and the editor |
| Modify: `studio/PixelFontText.kt` (public `glyph`), `data/Settings.kt`, `data/SettingsRepo.kt`, `alerts/AlertsRuntime.kt`, `ui/HomeScreen.kt`, `AndroidManifest.xml`, `res/values/strings.xml`, `test/.../render/ToyPreviewsTest.kt`, `README.md`, `docs/testing/device-checklist.md` | |

---

### Task 1: Message model

**Files:**
- Create: `app/src/main/java/app/backlit/badge/BadgeMessage.kt`
- Create: `app/src/main/java/app/backlit/badge/BadgeIcons.kt` (ids only for now; the pixels come in Task 3)
- Test: `app/src/test/java/app/backlit/badge/BadgeMessageTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class BadgeMessage(text: String = "", icon: String = "heart", kind: Kind = Kind.PLAIN, minutes: Int = 5, untilMinuteOfDay: Int = 900)` with `enum class Kind { PLAIN, COUNTDOWN, UNTIL }` and `clean()`
  - In the companion:
    - constants: `MAX_TEXT`, `MAX_MESSAGES`, `ALLOWED: Set<Char>`, `STARTERS`
    - list handling: `cleanText(s)`, `cleanList(list)`, `decodeList(s)`, `encodeList(list)`
    - index helpers: `activeIndex(stored, size)`, `moveActive(active, from, to)`, `afterDelete(active, deleted, sizeBefore)`
  - `BadgeIcons.ids: List<String>`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/app/backlit/badge/BadgeMessageTest.kt
package app.backlit.badge

import app.backlit.badge.BadgeMessage.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class BadgeMessageTest {
    @Test
    fun textIsUppercasedAndLimitedToTheFont() {
        assertEquals("BACK IN 5", BadgeMessage("back in 5 😀 #").clean().text)
        assertEquals("IN ", BadgeMessage.cleanText("in "))           // a trailing space survives while typing
        assertEquals("A B", BadgeMessage.cleanText("a    b"))
        assertEquals("IT'S OK? YES! 3:30 - .", BadgeMessage("it's ok? yes! 3:30 - .").clean().text)
        assertEquals(32, BadgeMessage.cleanText("A".repeat(40)).length)
    }

    @Test
    fun cleanClampsNumbersAndUnknownIcons() {
        val m = BadgeMessage("x", "nope", Kind.COUNTDOWN, minutes = 500, untilMinuteOfDay = 5000).clean()
        assertEquals("heart", m.icon)
        assertEquals(180, m.minutes)
        assertEquals(1439, m.untilMinuteOfDay)
        assertEquals(1, BadgeMessage(minutes = 0).clean().minutes)
        assertEquals(0, BadgeMessage(untilMinuteOfDay = -5).clean().untilMinuteOfDay)
    }

    @Test
    fun startersMatchTheSpec() {
        val s = BadgeMessage.STARTERS
        assertEquals(listOf("IN A MEETING", "BACK IN", "ON A CALL", "DO NOT DISTURB", "THANK YOU"), s.map { it.text })
        assertEquals(listOf("laptop", "coffee", "phone", "moon", "heart"), s.map { it.icon })
        assertEquals(Kind.COUNTDOWN, s[1].kind)
        assertEquals(5, s[1].minutes)
        assertEquals(8, BadgeIcons.ids.size)
    }

    @Test
    fun listRoundTripsThroughJson() {
        val list = BadgeMessage.STARTERS + BadgeMessage("LUNCH", "food", Kind.UNTIL, untilMinuteOfDay = 13 * 60 + 30)
        assertEquals(list, BadgeMessage.decodeList(BadgeMessage.encodeList(list)))
    }

    @Test
    fun badJsonFallsBackToStarters() {
        assertEquals(BadgeMessage.STARTERS, BadgeMessage.decodeList(""))
        assertEquals(BadgeMessage.STARTERS, BadgeMessage.decodeList("{not json"))
        assertEquals(BadgeMessage.STARTERS, BadgeMessage.decodeList("[]"))
        val ten = List(10) { BadgeMessage("M$it", "heart") }
        assertEquals(8, BadgeMessage.decodeList(BadgeMessage.encodeList(ten)).size)
    }

    @Test
    fun activeIndexClamps() {
        assertEquals(4, BadgeMessage.activeIndex(9, 5))
        assertEquals(0, BadgeMessage.activeIndex(-1, 5))
        assertEquals(2, BadgeMessage.activeIndex(2, 5))
    }

    @Test
    fun movingKeepsTheSameMessageCurrent() {
        assertEquals(1, BadgeMessage.moveActive(active = 2, from = 2, to = 1))
        assertEquals(2, BadgeMessage.moveActive(active = 1, from = 2, to = 1))
        assertEquals(0, BadgeMessage.moveActive(active = 0, from = 2, to = 1))
    }

    @Test
    fun afterDeletePicksTheNext() {
        assertEquals(1, BadgeMessage.afterDelete(active = 2, deleted = 1, sizeBefore = 5))
        assertEquals(2, BadgeMessage.afterDelete(active = 2, deleted = 2, sizeBefore = 5))   // the next one slides into 2
        assertEquals(0, BadgeMessage.afterDelete(active = 4, deleted = 4, sizeBefore = 5))   // wraps
        assertEquals(1, BadgeMessage.afterDelete(active = 1, deleted = 3, sizeBefore = 5))
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.badge.*'`
Expected: compilation FAIL with `Unresolved reference: BadgeMessage`.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/app/backlit/badge/BadgeIcons.kt  (ids now; pixels in Task 3)
package app.backlit.badge

object BadgeIcons {
    val ids: List<String> = listOf("laptop", "coffee", "phone", "moon", "heart", "car", "headphones", "food")
}
```

```kotlin
// app/src/main/java/app/backlit/badge/BadgeMessage.kt
package app.backlit.badge

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One status message: text (font characters only), an icon, and plain / countdown / until-a-time. */
@Serializable
data class BadgeMessage(
    val text: String = "",
    val icon: String = "heart",
    val kind: Kind = Kind.PLAIN,
    val minutes: Int = 5,
    val untilMinuteOfDay: Int = 15 * 60,
) {
    @Serializable
    enum class Kind { PLAIN, COUNTDOWN, UNTIL }

    fun clean(): BadgeMessage = copy(
        text = cleanText(text).trim(),
        icon = if (icon in BadgeIcons.ids) icon else "heart",
        minutes = minutes.coerceIn(1, 180),
        untilMinuteOfDay = untilMinuteOfDay.coerceIn(0, 1439),
    )

    companion object {
        const val MAX_TEXT = 32
        const val MAX_MESSAGES = 8
        val ALLOWED: Set<Char> = (('A'..'Z') + ('0'..'9') + listOf(' ', ':', '!', '?', '.', '\'', '-')).toSet()

        val STARTERS = listOf(
            BadgeMessage("IN A MEETING", "laptop"),
            BadgeMessage("BACK IN", "coffee", Kind.COUNTDOWN, minutes = 5),
            BadgeMessage("ON A CALL", "phone"),
            BadgeMessage("DO NOT DISTURB", "moon"),
            BadgeMessage("THANK YOU", "heart"),
        )

        private val json = Json { ignoreUnknownKeys = true }

        /** For typing: uppercase, font characters only, single spaces, no leading space, 32 max (a trailing space stays). */
        fun cleanText(s: String): String =
            s.uppercase().filter { it in ALLOWED }.replace(Regex(" {2,}"), " ").trimStart().take(MAX_TEXT)

        fun cleanList(list: List<BadgeMessage>): List<BadgeMessage> =
            list.map { it.clean() }.take(MAX_MESSAGES).ifEmpty { STARTERS }

        fun decodeList(s: String): List<BadgeMessage> =
            if (s.isBlank()) STARTERS
            else runCatching { cleanList(json.decodeFromString<List<BadgeMessage>>(s)) }.getOrDefault(STARTERS)

        fun encodeList(list: List<BadgeMessage>): String = json.encodeToString(list)

        fun activeIndex(stored: Int, size: Int): Int = stored.coerceIn(0, (size - 1).coerceAtLeast(0))

        /** The current message's index after moving the message at [from] to [to] (a swap of neighbours). */
        fun moveActive(active: Int, from: Int, to: Int): Int = when (active) {
            from -> to
            to -> from
            else -> active
        }

        /** The current index after deleting [deleted]: the next message takes over if the current one went. */
        fun afterDelete(active: Int, deleted: Int, sizeBefore: Int): Int = when {
            deleted < active -> active - 1
            deleted > active -> active
            deleted >= sizeBefore - 1 -> 0
            else -> deleted
        }
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.badge.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/badge app/src/test/java/app/backlit/badge
git commit -m "feat(badge): message model, starters and list helpers

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Live text (countdown, until, soon)

**Files:**
- Create: `app/src/main/java/app/backlit/badge/BadgeText.kt`
- Test: `app/src/test/java/app/backlit/badge/BadgeTextTest.kt`

**Interfaces:**
- Consumes: `BadgeMessage` (Task 1).
- Produces:
  - `BadgeText.scroll(m, since, now, use24h, zone): String`
  - `BadgeText.short(m, since, now, use24h, zone): String`
  - `BadgeText.timeLabel(minuteOfDay, use24h): String`
  - `BadgeText.untilEnd(m, since, zone): Long`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/app/backlit/badge/BadgeTextTest.kt
package app.backlit.badge

import app.backlit.badge.BadgeMessage.Kind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BadgeTextTest {
    private val zone = ZoneOffset.UTC
    private val ten = ZonedDateTime.of(2026, 10, 6, 10, 0, 0, 0, zone).toInstant().toEpochMilli()   // 10:00
    private val h = 3_600_000L
    private val back = BadgeMessage.STARTERS[1]
    private val meeting = BadgeMessage("IN A MEETING", "laptop", Kind.UNTIL, untilMinuteOfDay = 15 * 60)
    private fun scroll(m: BadgeMessage, now: Long, since: Long = ten, h24: Boolean = false) = BadgeText.scroll(m, since, now, h24, zone)
    private fun short(m: BadgeMessage, now: Long, since: Long = ten, h24: Boolean = false) = BadgeText.short(m, since, now, h24, zone)

    @Test
    fun countdownCountsThenSaysSoon() {
        assertEquals("BACK IN 5:00", scroll(back, ten))
        assertEquals("BACK IN 4:59", scroll(back, ten + 1000))
        assertEquals("BACK IN 0:01", scroll(back, ten + 299_001))
        assertEquals("BACK SOON", scroll(back, ten + 300_000))
        assertEquals("LUNCH SOON", scroll(BadgeMessage("LUNCH", "food", Kind.COUNTDOWN, minutes = 1), ten + 60_000))
        assertEquals("SOON", scroll(BadgeMessage("", "food", Kind.COUNTDOWN, minutes = 1), ten + 60_000))
    }

    @Test
    fun longCountdownsUseHours() {
        assertEquals("BACK IN 1:30:00", scroll(back.copy(minutes = 90), ten))
        assertEquals("99M", short(back.copy(minutes = 99), ten))
        assertEquals("3H", short(back.copy(minutes = 150), ten))
        assertEquals("5M", short(back, ten))
        assertEquals("1M", short(back, ten + 299_001))
        assertEquals("SOON", short(back, ten + 300_000))
    }

    @Test
    fun untilShowsTheTimeThenDropsIt() {
        assertEquals("IN A MEETING UNTIL 3PM", scroll(meeting, ten))
        assertEquals("IN A MEETING UNTIL 15:00", scroll(meeting, ten, h24 = true))
        assertEquals("IN A MEETING UNTIL 3PM", scroll(meeting, ten + 5 * h - 1))
        assertEquals("IN A MEETING", scroll(meeting, ten + 5 * h))
        assertEquals("3PM", short(meeting, ten))
        assertEquals("15", short(meeting, ten, h24 = true))
        assertEquals("IN", short(meeting, ten + 5 * h))
    }

    @Test
    fun untilPickedAfterItsTimeMeansTomorrow() {
        val four = ten + 6 * h                                    // picked at 16:00, until 15:00
        assertEquals("IN A MEETING UNTIL 3PM", scroll(meeting, four, since = four))
        assertEquals("IN A MEETING UNTIL 3PM", scroll(meeting, four + 22 * h, since = four))   // 14:00 tomorrow
        assertEquals("IN A MEETING", scroll(meeting, four + 23 * h, since = four))            // 15:00 tomorrow
        val midnight = meeting.copy(untilMinuteOfDay = 30)        // 00:30, picked at 10:00 → after midnight
        assertEquals("IN A MEETING UNTIL 12:30AM", scroll(midnight, ten + 14 * h))
        assertEquals("IN A MEETING", scroll(midnight, ten + 14 * h + 30 * 60_000))
    }

    @Test
    fun timeLabels() {
        assertEquals("3:30PM", BadgeText.timeLabel(15 * 60 + 30, false))
        assertEquals("12AM", BadgeText.timeLabel(0, false))
        assertEquals("12PM", BadgeText.timeLabel(12 * 60, false))
        assertEquals("9:05", BadgeText.timeLabel(9 * 60 + 5, true))
    }

    @Test
    fun plainShowsTextAndFirstWord() {
        val m = BadgeMessage("DO NOT DISTURB", "moon")
        assertEquals("DO NOT DISTURB", scroll(m, ten))
        assertEquals("DO", short(m, ten))
        assertEquals("", short(BadgeMessage("", "moon"), ten))
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.badge.BadgeTextTest'`
Expected: compilation FAIL with `Unresolved reference: BadgeText`.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/app/backlit/badge/BadgeText.kt
package app.backlit.badge

import app.backlit.badge.BadgeMessage.Kind
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** What a message says right now: live countdowns, "until" times, and the short word for always-on. */
object BadgeText {
    private const val MIN = 60_000L
    private const val HOUR = 3_600_000L

    fun scroll(m: BadgeMessage, since: Long, now: Long, use24h: Boolean, zone: ZoneId): String = when (m.kind) {
        Kind.PLAIN -> m.text
        Kind.COUNTDOWN -> {
            val left = left(m, since, now)
            if (left > 0) join(m.text, clock(left)) else soon(m.text)
        }
        Kind.UNTIL -> if (now < untilEnd(m, since, zone)) join(m.text, "UNTIL " + timeLabel(m.untilMinuteOfDay, use24h)) else m.text
    }

    fun short(m: BadgeMessage, since: Long, now: Long, use24h: Boolean, zone: ZoneId): String = when (m.kind) {
        Kind.PLAIN -> firstWord(m.text)
        Kind.COUNTDOWN -> {
            val left = left(m, since, now)
            val mins = ceilDiv(left, MIN)
            when {
                left <= 0 -> "SOON"
                mins <= 99 -> "${mins}M"
                else -> "${ceilDiv(left, HOUR)}H"
            }
        }
        Kind.UNTIL -> if (now < untilEnd(m, since, zone)) shortTime(m.untilMinuteOfDay, use24h) else firstWord(m.text)
    }

    /** The next time [BadgeMessage.untilMinuteOfDay] comes round after [since] (the same minute or earlier → tomorrow). */
    fun untilEnd(m: BadgeMessage, since: Long, zone: ZoneId): Long {
        val start = Instant.ofEpochMilli(since).atZone(zone)
        var end = start.toLocalDate().atTime(LocalTime.of(m.untilMinuteOfDay / 60, m.untilMinuteOfDay % 60)).atZone(zone)
        if (!end.isAfter(start)) end = end.plusDays(1)
        return end.toInstant().toEpochMilli()
    }

    fun timeLabel(minuteOfDay: Int, use24h: Boolean): String {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        if (use24h) return String.format(Locale.US, "%d:%02d", h, m)
        val h12 = if (h % 12 == 0) 12 else h % 12
        val ap = if (h < 12) "AM" else "PM"
        return if (m == 0) "$h12$ap" else String.format(Locale.US, "%d:%02d%s", h12, m, ap)
    }

    private fun shortTime(minuteOfDay: Int, use24h: Boolean): String {
        val h = minuteOfDay / 60
        if (use24h) return "$h"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12" + if (h < 12) "AM" else "PM"
    }

    private fun left(m: BadgeMessage, since: Long, now: Long): Long = m.minutes * MIN - (now - since)

    private fun clock(ms: Long): String {
        val s = ceilDiv(ms, 1000)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /** "BACK IN" → "BACK SOON"; anything else gets " SOON". */
    private fun soon(text: String): String {
        val t = text.trim()
        val base = if (t == "IN") "" else t.removeSuffix(" IN")
        return join(base, "SOON")
    }

    private fun join(a: String, b: String): String = listOf(a.trim(), b).filter { it.isNotBlank() }.joinToString(" ")

    private fun firstWord(text: String): String = text.trim().substringBefore(' ')

    private fun ceilDiv(a: Long, b: Long): Long = if (a <= 0) 0 else (a + b - 1) / b
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.badge.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/badge/BadgeText.kt app/src/test/java/app/backlit/badge/BadgeTextTest.kt
git commit -m "feat(badge): live countdown and until text

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Font and icons

**Files:**
- Create: `app/src/main/java/app/backlit/badge/BadgeFont.kt`
- Modify: `app/src/main/java/app/backlit/badge/BadgeIcons.kt` (add the pixels)
- Modify: `app/src/main/java/app/backlit/studio/PixelFontText.kt` (add a public `glyph`)
- Test: `app/src/test/java/app/backlit/badge/BadgeFontIconsTest.kt`

**Interfaces:**
- Consumes: `BadgeMessage.ALLOWED` (Task 1), `PixelFont5x7.digit`, `PixelGrid`.
- Produces:
  - `PixelFontText.glyph(c: Char): List<String>?`
  - `BadgeFont.height(size)`, `BadgeFont.width(size, text)`, `BadgeFont.supports(size, c)`, `BadgeFont.draw(g, text, x, y, b)`
  - `BadgeIcons.box(size)`, `BadgeIcons.rows(id, big: Boolean)`, `BadgeIcons.draw(g, id, x, y, b, big = g.size >= 25)`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/app/backlit/badge/BadgeFontIconsTest.kt
package app.backlit.badge

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeFontIconsTest {
    @Test
    fun everyAllowedCharacterHasAGlyphAtBothSizes() {
        for (c in BadgeMessage.ALLOWED) {
            assertTrue("25 '$c'", BadgeFont.supports(25, c))
            assertTrue("13 '$c'", BadgeFont.supports(13, c))
        }
    }

    @Test
    fun widthsAndHeights() {
        assertEquals(11, BadgeFont.width(25, "AB"))
        assertEquals(7, BadgeFont.width(13, "AB"))
        assertEquals(7, BadgeFont.height(25))
        assertEquals(5, BadgeFont.height(13))
        assertEquals(0, BadgeFont.width(25, ""))
    }

    @Test
    fun drawPutsTheLettersWhereAsked() {
        val g = PixelGrid(25)
        BadgeFont.draw(g, "T", 10, 14, 255)
        for (x in 10..14) assertEquals(255, g[x, 14])                 // the T's top bar
        assertEquals(255, g[12, 20])
        val h = PixelGrid(13)
        BadgeFont.draw(h, "T", 5, 7, 255)
        for (x in 5..7) assertEquals(255, h[x, 7])
    }

    @Test
    fun iconsFitTheirBoxesAndTheMask() {
        for (id in BadgeIcons.ids) for ((size, y) in listOf(25 to 3, 13 to 1)) {
            val big = size >= 25
            val rows = BadgeIcons.rows(id, big)
            val box = BadgeIcons.box(size)
            assertTrue("$id rows", rows.size <= box)
            assertTrue("$id width", rows.all { it.length == box })
            val g = PixelGrid(size)
            BadgeIcons.draw(g, id, (size - box) / 2, y, 255)
            assertEquals("$id at $size fits the mask", rows.sumOf { r -> r.count { it == 'X' } }, g.litCount())
        }
    }
}
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.badge.BadgeFontIconsTest'`
Expected: compilation FAIL with `Unresolved reference: BadgeFont`.

- [ ] **Step 3: Implement**

In `PixelFontText.kt`, add this after `fun clean(...)`:

```kotlin
    /** The rows of [c], or null when the font has no such character. */
    fun glyph(c: Char): List<String>? = GLYPHS[c]
```

```kotlin
// app/src/main/java/app/backlit/badge/BadgeFont.kt
package app.backlit.badge

import app.backlit.render.PixelFont5x7
import app.backlit.render.PixelGrid
import app.backlit.studio.PixelFontText

/** Badge text: 5×7 capitals on 25×25 (digits from PixelFont5x7), the Studio's 3×5 font on 13×13. 1 px gaps. */
object BadgeFont {
    private val DIGIT = List(7) { "00000" }   // width placeholder; digits are drawn by PixelFont5x7

    private val F7: Map<Char, List<String>> = mapOf(
        'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        'B' to listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
        'C' to listOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
        'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
        'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
        'F' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
        'G' to listOf("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
        'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
        'I' to listOf("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
        'J' to listOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
        'K' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
        'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
        'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
        'N' to listOf("10001", "10001", "11001", "10101", "10011", "10001", "10001"),
        'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
        'Q' to listOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
        'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
        'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
        'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
        'V' to listOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
        'W' to listOf("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
        'X' to listOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
        'Y' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
        'Z' to listOf("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
        ':' to listOf("0", "1", "1", "0", "1", "1", "0"),
        '!' to listOf("1", "1", "1", "1", "1", "0", "1"),
        '?' to listOf("01110", "10001", "00001", "00010", "00100", "00000", "00100"),
        '.' to listOf("0", "0", "0", "0", "0", "0", "1"),
        '-' to listOf("000", "000", "000", "111", "000", "000", "000"),
        ' ' to listOf("000", "000", "000", "000", "000", "000", "000"),
        '\'' to listOf("1", "1", "0", "0", "0", "0", "0"),
    )

    private val EXTRA3: Map<Char, List<String>> = mapOf(
        ' ' to listOf("00", "00", "00", "00", "00"),
        '\'' to listOf("1", "1", "0", "0", "0"),
    )

    fun height(size: Int): Int = if (size >= 25) PixelFont5x7.HEIGHT else PixelFontText.HEIGHT

    private fun glyph(size: Int, c: Char): List<String>? =
        if (size >= 25) (if (c.isDigit()) DIGIT else F7[c]) else EXTRA3[c] ?: PixelFontText.glyph(c)

    fun supports(size: Int, c: Char): Boolean = glyph(size, c) != null

    fun width(size: Int, text: String): Int {
        val gs = text.mapNotNull { glyph(size, it) }
        return if (gs.isEmpty()) 0 else gs.sumOf { it[0].length } + gs.size - 1
    }

    /** Draws [text] with its top-left at (x, y); the grid's size picks the font. Off-panel pixels are dropped. */
    fun draw(g: PixelGrid, text: String, x: Int, y: Int, b: Int) {
        var cx = x
        for (c in text) {
            val rows = glyph(g.size, c) ?: continue
            if (g.size >= 25 && c.isDigit()) PixelFont5x7.digit(g, c - '0', cx, y, b)
            else for (r in rows.indices) for (col in rows[r].indices) if (rows[r][col] == '1') g.plot(cx + col, y + r, b)
            cx += rows[0].length + 1
        }
    }
}
```

```kotlin
// app/src/main/java/app/backlit/badge/BadgeIcons.kt
package app.backlit.badge

import app.backlit.render.PixelGrid

/** The 8 badge icons (spec §2 table): 9×9 for 25×25, 5×5 for 13×13. Short icons sit vertically centred in the box. */
object BadgeIcons {
    val ids: List<String> = listOf("laptop", "coffee", "phone", "moon", "heart", "car", "headphones", "food")

    private val BIG: Map<String, List<String>> = mapOf(
        "laptop" to listOf(".XXXXXXX.", ".X.....X.", ".X.....X.", ".X.....X.", ".XXXXXXX.", "XXXXXXXXX"),
        "coffee" to listOf("..X..X...", "...X..X..", ".........", "XXXXXXX..", "XXXXXXXXX", "XXXXXXX.X", "XXXXXXXXX", ".XXXXX..."),
        "phone" to listOf("XXX......", "XXXX.....", "XXX......", ".XX......", ".XX......", ".XXX.....", "..XXX.XXX", "...XXXXXX", ".....XXX."),
        "moon" to listOf("..XXXX...", ".XXX.....", "XXX......", "XXX......", "XXX......", "XXX......", "XXXX...XX", ".XXXXXXX.", "..XXXXX.."),
        "heart" to listOf(".XX...XX.", "XXXX.XXXX", "XXXXXXXXX", "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...", "....X...."),
        "car" to listOf("..XXXXX..", ".X.....X.", "X.......X", "XXXXXXXXX", "X.XXXXX.X", "XXXXXXXXX", "XX.....XX"),
        "headphones" to listOf("..XXXXX..", ".X.....X.", "X.......X", "X.......X", "XX.....XX", "XXX...XXX", "XXX...XXX", ".X.....X."),
        "food" to listOf("X.X.X..X.", "X.X.X.XX.", "X.X.X.XX.", "XXXXX.XX.", ".XXX..XX.", "..X....X.", "..X....X.", "..X....X.", "..X....X."),
    )

    private val SMALL: Map<String, List<String>> = mapOf(
        "laptop" to listOf(".XXX.", ".X.X.", ".XXX.", "XXXXX"),
        "coffee" to listOf(".X.X.", "X.X..", "XXXX.", "XXXXX", ".XX.."),
        "phone" to listOf("XX...", "X....", "X....", "XX.XX", ".XXX."),
        "moon" to listOf(".XX..", "XX...", "X....", "XX..X", ".XXX."),
        "heart" to listOf("XX.XX", "XXXXX", "XXXXX", ".XXX.", "..X.."),
        "car" to listOf(".XXX.", "XXXXX", "X.X.X", "XXXXX", "X...X"),
        "headphones" to listOf(".XXX.", "X...X", "X...X", "XX.XX", "XX.XX"),
        "food" to listOf("X.X.X", "X.X.X", "XXX.X", ".X..X", ".X..X"),
    )

    fun box(size: Int): Int = if (size >= 25) 9 else 5

    fun rows(id: String, big: Boolean): List<String> = (if (big) BIG else SMALL)[id] ?: (if (big) BIG else SMALL).getValue("heart")

    fun draw(g: PixelGrid, id: String, x: Int, y: Int, b: Int, big: Boolean = g.size >= 25) {
        val rows = rows(id, big)
        val dy = ((if (big) 9 else 5) - rows.size) / 2
        for (r in rows.indices) for (c in rows[r].indices) if (rows[r][c] == 'X') g.plot(x + c, y + dy + r, b)
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.badge.*'`
Expected: PASS. If `iconsFitTheirBoxesAndTheMask` fails for an icon, a corner pixel is outside the round mask. Move the icon's draw position as the spec allows, or trim that corner pixel. Then record the change as a ruling.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/badge app/src/test/java/app/backlit/badge app/src/main/java/app/backlit/studio/PixelFontText.kt
git commit -m "feat(badge): 5x7/3x5 badge font and the 8 icons

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Badge art and preview

**Files:**
- Create: `app/src/main/java/app/backlit/badge/BadgeArt.kt`
- Create: `app/src/main/java/app/backlit/badge/BadgePreviewAnimation.kt`
- Modify: `app/src/main/java/app/backlit/alerts/AlertsRuntime.kt` (`animationFor`)
- Test: `app/src/test/java/app/backlit/badge/BadgeArtTest.kt`

**Interfaces:**
- Consumes: Tasks 1–3, `GlyphAnimation`, `px()`.
- Produces:
  - `BadgeArt.frame(size, msg, text, tMs, sinceChangeMs): PixelGrid`
  - `BadgeArt.still(size, msg, short): PixelGrid`
  - `BadgeArt.tile(id): PixelGrid` (a 13×13 grid with the 9×9 icon at 2,2, used by the UI)
  - `BadgeArt.FLASH_MS = 600`
  - `BadgePreviewAnimation.idFor(icon, text)` and `BadgePreviewAnimation.parse(id)`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/app/backlit/badge/BadgeArtTest.kt
package app.backlit.badge

import app.backlit.render.PixelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeArtTest {
    private fun litRows(g: PixelGrid) = (0 until g.size).filter { y -> (0 until g.size).any { x -> g[x, y] > 0 } }.toSet()
    private val long = "ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123"

    @Test
    fun everyIconAndLongTextStayInsideAtBothSizes() {
        for (id in BadgeIcons.ids) for (size in listOf(25, 13)) {
            val m = BadgeMessage(long, id)
            for (t in 0L..20_000L step 700) {
                val g = BadgeArt.frame(size, m, long, t, BadgeArt.FLASH_MS)
                assertTrue("$id $size $t", g.litCount() > 0)
                for (y in 0 until size) for (x in 0 until size) if (g[x, y] > 0) assertTrue(g.hasLed(x, y))
            }
        }
    }

    @Test
    fun at13TheIconAndTextNeverShareARow() {
        for (id in BadgeIcons.ids) {
            val m = BadgeMessage("IN A MEETING", id)
            val icon = litRows(BadgeArt.frame(13, m, "", 0, BadgeArt.FLASH_MS))
            assertTrue("$id icon rows $icon", icon.all { it in 1..5 })
            for (t in 0L..6_000L step 160) {
                val rows = litRows(BadgeArt.frame(13, m, "IN A MEETING", t, BadgeArt.FLASH_MS))
                assertTrue("$id t=$t rows $rows", rows.all { it in 1..5 || it in 7..11 })
            }
        }
        val rows25 = litRows(BadgeArt.frame(25, BadgeMessage("HI", "heart"), "HI", 600, BadgeArt.FLASH_MS))
        assertTrue(rows25.all { it in 3..11 || it in 14..20 })
    }

    @Test
    fun theTickerMoves() {
        val m = BadgeMessage("ON A CALL", "phone")
        assertNotEquals(BadgeArt.frame(25, m, "ON A CALL", 0, 9999).raw().toList(), BadgeArt.frame(25, m, "ON A CALL", 550, 9999).raw().toList())
        assertNotEquals(BadgeArt.frame(13, m, "ON A CALL", 0, 9999).raw().toList(), BadgeArt.frame(13, m, "ON A CALL", 800, 9999).raw().toList())
    }

    @Test
    fun theChangeFlashIsBrighterThanTheSteadyIcon() {
        val m = BadgeMessage("", "heart")
        val flash = BadgeArt.frame(25, m, "", 50, 50)
        val steady = BadgeArt.frame(25, m, "", 50, BadgeArt.FLASH_MS)
        val mid = BadgeArt.frame(25, m, "", 200, 200)
        assertEquals(255, flash[12, 6])
        assertEquals(217, steady[12, 6])
        assertEquals(217, mid[12, 6])
    }

    @Test
    fun stillShowsIconAndShortText() {
        val m = BadgeMessage("BACK IN", "coffee")
        val s = BadgeArt.still(25, m, "4M")
        val rows = litRows(s)
        assertTrue(rows.any { it in 4..12 })
        assertTrue(rows.any { it in 15..21 })
        assertEquals(BadgeArt.still(13, m, "ABC").raw().toList(), BadgeArt.still(13, m, "ABCD").raw().toList())
    }

    @Test
    fun tilesHoldTheWholeBigIcon() {
        for (id in BadgeIcons.ids) {
            assertEquals(id, BadgeIcons.rows(id, true).sumOf { r -> r.count { it == 'X' } }, BadgeArt.tile(id).litCount())
        }
    }

    @Test
    fun previewsParse() {
        val a = BadgePreviewAnimation.parse(BadgePreviewAnimation.idFor("coffee", "BACK IN 4:59"))
        assertNotNull(a)
        assertTrue(a!!.frame(25, 1200).litCount() > 0)
        assertNull(BadgePreviewAnimation.parse("badge:nope:X"))
        assertNull(BadgePreviewAnimation.parse("pet:happy"))
    }
}
```

Two of these values need explaining. The heart's 9×9 row 3 is `XXXXXXXXX`. It is drawn at y = 3 + (9 − 8) / 2 = 3, so its row 3 is grid row 6, and x = 12 is inside it. The steady icon is 0.85 × 255 = 216.75, which rounds to 217.

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.badge.BadgeArtTest'`
Expected: compilation FAIL with `Unresolved reference: BadgeArt`.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/app/backlit/badge/BadgeArt.kt
package app.backlit.badge

import app.backlit.render.PixelGrid
import app.backlit.render.px

/** Style A: icon on top, ticker underneath (mockups 2026-10-06-badge-styles / badge-icons). */
object BadgeArt {
    const val FLASH_MS = 600L
    private const val ICON = 0.85

    private fun b(v: Double): Int = (v * 255).px()

    private fun iconX(size: Int): Int = (size - BadgeIcons.box(size)) / 2

    /** [tMs]: time since the ticker started; [sinceChangeMs]: time since this message became current (the flash). */
    fun frame(size: Int, msg: BadgeMessage, text: String, tMs: Long, sinceChangeMs: Long): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        val flash = sinceChangeMs in 0 until 150 || sinceChangeMs in 300 until 450
        BadgeIcons.draw(g, msg.icon, iconX(size), if (big) 3 else 1, b(if (flash) 1.0 else ICON))
        if (text.isNotEmpty()) {
            val span = BadgeFont.width(size, text) + size + 4
            val step = if (big) 55L else 80L
            val off = ((tMs.coerceAtLeast(0) / step) % span).toInt()
            BadgeFont.draw(g, text, size - off, if (big) 14 else 7, 255)
        }
        return g
    }

    /** Always-on: the icon and a short word (cut to 3 characters on 13×13). */
    fun still(size: Int, msg: BadgeMessage, short: String): PixelGrid {
        val g = PixelGrid(size)
        val big = size >= 25
        BadgeIcons.draw(g, msg.icon, iconX(size), if (big) 4 else 1, b(ICON))
        val s = if (big) short else short.take(3)
        BadgeFont.draw(g, s, (size - BadgeFont.width(size, s)) / 2, if (big) 15 else 7, 255)
        return g
    }

    /** A UI tile: the 9×9 icon on a 13×13 dot grid (corners stay inside the round mask). */
    fun tile(id: String): PixelGrid = PixelGrid(13).also { BadgeIcons.draw(it, id, 2, 2, 255, big = true) }
}
```

```kotlin
// app/src/main/java/app/backlit/badge/BadgePreviewAnimation.kt
package app.backlit.badge

import app.backlit.anim.GlyphAnimation
import app.backlit.render.PixelGrid

/** A badge as a GlyphAnimation: "Show on Glyph" and the Glyph Toys picker image. Id: badge:<icon>:<text>. */
class BadgePreviewAnimation private constructor(private val icon: String, private val text: String) : GlyphAnimation {
    override val id: String = idFor(icon, text)
    override val name: String = "Badge"
    override val loopMs: Long = (BadgeFont.width(25, text) + 25 + 4) * 55L

    override fun frame(size: Int, tMs: Long): PixelGrid =
        BadgeArt.frame(size, BadgeMessage(text, icon), text, tMs, BadgeArt.FLASH_MS)

    companion object {
        fun idFor(icon: String, text: String) = "badge:$icon:$text"

        fun parse(id: String): BadgePreviewAnimation? {
            if (!id.startsWith("badge:")) return null
            val parts = id.split(':', limit = 3)
            if (parts.size < 3 || parts[1] !in BadgeIcons.ids) return null
            return BadgePreviewAnimation(parts[1], BadgeMessage.cleanText(parts[2]).trim())
        }
    }
}
```

In `alerts/AlertsRuntime.kt`, add `import app.backlit.badge.BadgePreviewAnimation`, and add this line in `animationFor` after the `SandPreviewAnimation.parse(...)` line:

```kotlin
            ?: BadgePreviewAnimation.parse(alert.animationId)
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.badge.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/badge app/src/test/java/app/backlit/badge app/src/main/java/app/backlit/alerts/AlertsRuntime.kt
git commit -m "feat(badge): icon + ticker art, flash, always-on still, previews

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Badge settings

**Files:**
- Modify: `app/src/main/java/app/backlit/data/Settings.kt`
- Modify: `app/src/main/java/app/backlit/data/SettingsRepo.kt`
- Test: `app/src/test/java/app/backlit/data/SettingsRepoTest.kt`

**Interfaces:**
- Produces: the `Settings` fields `badgeMessages: String = ""`, `badgeActive: Int = 0`, `badgeActiveSince: Long = 0L` and `badgeToyEverBound: Boolean = false`. The list and index are interpreted by `BadgeMessage.decodeList` and `activeIndex`.

- [ ] **Step 1: Write the failing tests**

In `roundTripsEveryField`, add this after the `sandTimer = ...` line:

```kotlin
            badgeMessages = "[{\"text\":\"HI\"}]", badgeActive = 3, badgeActiveSince = 777L, badgeToyEverBound = true,
```

Append this test:

```kotlin
    @Test
    fun badgeDefaults() = runBlocking {
        val s = repo().settings.first()
        assertEquals("", s.badgeMessages); assertEquals(0, s.badgeActive)
        assertEquals(0L, s.badgeActiveSince); assertEquals(false, s.badgeToyEverBound)
    }
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.superpowers/runtests.sh --tests 'app.backlit.data.SettingsRepoTest'`
Expected: compilation FAIL with `No parameter with name 'badgeMessages'`.

- [ ] **Step 3: Implement**

In `Settings.kt`, add these after `val sandToyEverBound: Boolean = false,`:

```kotlin
    val badgeMessages: String = "",
    val badgeActive: Int = 0,
    val badgeActiveSince: Long = 0L,
    val badgeToyEverBound: Boolean = false,
```

In `SettingsRepo.kt`, add these keys after `SAND_BOUND`:

```kotlin
        private val BADGE_MESSAGES = stringPreferencesKey("badge_messages")
        private val BADGE_ACTIVE = intPreferencesKey("badge_active")
        private val BADGE_ACTIVE_SINCE = longPreferencesKey("badge_active_since")
        private val BADGE_BOUND = booleanPreferencesKey("badge_toy_ever_bound")
```

In `toSettings()`, add these after `sandToyEverBound = ...,`:

```kotlin
                badgeMessages = this[BADGE_MESSAGES] ?: d.badgeMessages,
                badgeActive = (this[BADGE_ACTIVE] ?: d.badgeActive).coerceAtLeast(0),
                badgeActiveSince = this[BADGE_ACTIVE_SINCE] ?: d.badgeActiveSince,
                badgeToyEverBound = this[BADGE_BOUND] ?: d.badgeToyEverBound,
```

In `write()`, add these after `this[SAND_BOUND] = ...`:

```kotlin
            this[BADGE_MESSAGES] = s.badgeMessages
            this[BADGE_ACTIVE] = s.badgeActive
            this[BADGE_ACTIVE_SINCE] = s.badgeActiveSince
            this[BADGE_BOUND] = s.badgeToyEverBound
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `.superpowers/runtests.sh --tests 'app.backlit.data.SettingsRepoTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/data app/src/test/java/app/backlit/data
git commit -m "feat(badge): badge settings (messages, current, since)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The Backlit Badge toy

**Files:**
- Create: `app/src/main/java/app/backlit/glyph/BadgeToyService.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Create (generated): `app/src/main/res/drawable/ic_badge_preview.xml`
- Modify: `app/src/test/java/app/backlit/render/ToyPreviewsTest.kt`

**Interfaces:**
- Consumes: Tasks 1–5, `DeviceProfile`, `ModeTracker`, `GlyphOutput`, `FramePacer`, `FrameEncoder`, `ToyPresence`, `AlertsRuntime`.

- [ ] **Step 1: Add the failing golden test for the picker image**

In `ToyPreviewsTest`, add these imports: `app.backlit.badge.BadgeArt` and `app.backlit.badge.BadgeMessage`. Then add this entry to `previews`:

```kotlin
        "ic_badge_preview" to BadgeArt.frame(25, BadgeMessage("IN A MEETING", "laptop"), "IN A MEETING", 1200, BadgeArt.FLASH_MS),
```

Run: `.superpowers/runtests.sh --tests 'app.backlit.render.ToyPreviewsTest'`
Expected: FAIL with `FileNotFoundException` for `ic_badge_preview.xml`.

Generate the image with `source .superpowers/env.sh && UPDATE_PREVIEWS=1 ./gradlew --no-daemon :app:testDebugUnitTest --tests 'app.backlit.render.ToyPreviewsTest'`, then run the plain test again. Expected: PASS.

- [ ] **Step 2: Add the strings and register the service in the manifest**

In `strings.xml`, add these after `sand_toy_summary`:

```xml
    <string name="badge_toy_name">Backlit Badge</string>
    <string name="badge_toy_summary">A status sign for when your phone is face-down. Long press to switch messages.</string>
```

In `AndroidManifest.xml`, add this after the `SandToyService` service block:

```xml
        <service
            android:name=".glyph.BadgeToyService"
            android:exported="true"
            tools:ignore="ExportedService">
            <intent-filter>
                <action android:name="com.nothing.glyph.TOY" />
            </intent-filter>
            <meta-data
                android:name="com.nothing.glyph.toy.name"
                android:resource="@string/badge_toy_name" />
            <meta-data
                android:name="com.nothing.glyph.toy.image"
                android:resource="@drawable/ic_badge_preview" />
            <meta-data
                android:name="com.nothing.glyph.toy.summary"
                android:resource="@string/badge_toy_summary" />
            <meta-data
                android:name="com.nothing.glyph.toy.longpress"
                android:value="1" />
            <meta-data
                android:name="com.nothing.glyph.toy.aod_support"
                android:value="1" />
        </service>
```

- [ ] **Step 3: Write the service** (modelled on `CanvasToyService`)

```kotlin
// app/src/main/java/app/backlit/glyph/BadgeToyService.kt
package app.backlit.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import app.backlit.alerts.AlertsRuntime
import app.backlit.alerts.ToyPresence
import app.backlit.badge.BadgeArt
import app.backlit.badge.BadgeMessage
import app.backlit.badge.BadgeText
import app.backlit.data.Settings
import app.backlit.data.SettingsRepo
import app.backlit.render.Mode
import app.backlit.render.PixelGrid
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
import java.time.ZoneId

/** A status sign: the current message's icon with its text scrolling underneath; long press switches messages. */
class BadgeToyService : Service() {

    private var scope: CoroutineScope? = null
    private var renderJob: Job? = null
    private var output: GlyphOutput? = null
    private lateinit var profile: DeviceProfile
    private lateinit var modes: ModeTracker
    private lateinit var repo: SettingsRepo
    private var settings = Settings()
    private var alerts: AlertsRuntime? = null

    private var listJson: String? = null
    private var messages: List<BadgeMessage> = BadgeMessage.STARTERS
    private var shownKey: Pair<Int, Long>? = null
    private var tickerFrom = 0L
    private var flashFrom = Long.MIN_VALUE / 2

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> next()
                GlyphToy.EVENT_AOD -> { modes.onAodEvent(System.currentTimeMillis()); kick() }
            }
        }
    }
    private val messenger = Messenger(handler)
    private val rekick = Runnable { kick() }

    override fun onBind(intent: Intent?): IBinder {
        profile = DeviceProfile.detect()
        modes = ModeTracker(profile.aodOnly)
        repo = SettingsRepo.get(this)
        if (profile == DeviceProfile.UNSUPPORTED) return messenger.binder

        val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "badge toy coroutine failed", e) }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)
        scope = s
        ToyPresence.enter()
        val rt = AlertsRuntime.get(this).also { alerts = it }
        rt.toyChanged()
        output = GlyphOutput(this, profile) { kick() }.also { it.connect() }
        s.launch {
            repo.update {
                var u = it
                if (!u.badgeToyEverBound) u = u.copy(badgeToyEverBound = true)
                if (u.badgeActiveSince == 0L) u = u.copy(badgeActiveSince = System.currentTimeMillis())
                u
            }
        }
        s.launch { repo.settings.collect { settings = it; kick() } }
        s.launch { rt.bus.collect { kick() } }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(rekick)
        renderJob?.cancel()
        renderJob = null
        scope?.cancel()
        scope = null
        output?.close()
        output = null
        if (alerts != null) {
            ToyPresence.leave()
            alerts?.toyChanged()
            alerts = null
        }
        return false
    }

    /** Long press: the next message becomes current, and its countdown / until starts now. */
    private fun next() {
        val s = scope ?: return
        s.launch {
            repo.update { cur ->
                val list = BadgeMessage.decodeList(cur.badgeMessages)
                val i = BadgeMessage.activeIndex(cur.badgeActive, list.size)
                cur.copy(badgeActive = (i + 1) % list.size, badgeActiveSince = System.currentTimeMillis())
            }
        }
    }

    private fun isAod() = modes.mode(System.currentTimeMillis()) == Mode.AOD

    private fun current(): Pair<Int, BadgeMessage> {
        if (settings.badgeMessages != listJson) {
            listJson = settings.badgeMessages
            messages = BadgeMessage.decodeList(settings.badgeMessages)
        }
        val i = BadgeMessage.activeIndex(settings.badgeActive, messages.size)
        return i to messages[i]
    }

    private fun kick() {
        val s = scope ?: return
        if (renderJob?.isActive == true) return
        handler.removeCallbacks(rekick)
        renderJob = s.launch {
            val pacer = FramePacer(FRAME_MS)
            var wait = 0L
            while (isActive) {
                delay(wait)
                val now = AlertsRuntime.now()
                val wall = System.currentTimeMillis()
                val alert = alerts?.bus?.value
                val aod = isAod()
                val (i, msg) = current()
                val since = settings.badgeActiveSince.takeIf { it > 0 } ?: wall
                val key = i to since
                if (key != shownKey) {
                    if (shownKey != null) flashFrom = now   // a change while showing flashes; the first frame doesn't
                    shownKey = key
                    tickerFrom = now
                }
                val zone = ZoneId.systemDefault()
                val grid = runCatching {
                    when {
                        alert != null -> alerts!!.animationFor(alert).frame(profile.size, now - alert.startedAt)
                        aod -> BadgeArt.still(profile.size, msg, BadgeText.short(msg, since, wall, settings.use24h, zone))
                        else -> BadgeArt.frame(profile.size, msg, BadgeText.scroll(msg, since, wall, settings.use24h, zone), now - tickerFrom, now - flashFrom)
                    }
                }.getOrElse { Log.e(TAG, "render failed", it); PixelGrid(profile.size) }
                output?.push(FrameEncoder.encode(grid, settings.brightness, aod = alert == null && aod))
                if (aod && alert == null) {
                    modes.msUntilActive(System.currentTimeMillis())?.let { handler.postDelayed(rekick, it + 100) }
                    break
                }
                wait = pacer.delayBeforeNext(AlertsRuntime.now())
            }
        }
    }

    private companion object {
        const val TAG = "BacklitBadge"
        const val FRAME_MS = 50L
    }
}
```

- [ ] **Step 4: Build and run all the tests**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon :app:assembleDebug && .superpowers/runtests.sh`
Expected: `BUILD SUCCESSFUL`, and the last line shows `0 failures, 0 errors`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/backlit/glyph/BadgeToyService.kt app/src/main/AndroidManifest.xml app/src/main/res app/src/test/java/app/backlit/render/ToyPreviewsTest.kt
git commit -m "feat(badge): Backlit Badge Glyph toy

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: BADGE tab

**Files:**
- Create: `app/src/main/java/app/backlit/ui/BadgeScreen.kt`
- Modify: `app/src/main/java/app/backlit/ui/HomeScreen.kt`

**Interfaces:**
- Consumes:
  - `BadgeMessage` (`decodeList`, `encodeList`, `cleanList`, `cleanText`, `activeIndex`, `moveActive`, `afterDelete`, `MAX_MESSAGES`, `MAX_TEXT`)
  - `BadgeText.scroll` / `timeLabel`
  - `BadgeArt.frame` / `tile`
  - `BadgeIcons.ids`
  - `BadgePreviewAnimation.idFor`
  - `MatrixPreview`, `SquareChip`, `SettingRow`, `Notice`, `BacklitColors`
- Produces: `BadgeTab(settings, profile, onUpdate)`.

- [ ] **Step 1: Write the screen**

```kotlin
// app/src/main/java/app/backlit/ui/BadgeScreen.kt
package app.backlit.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.backlit.alerts.AlertsRuntime
import app.backlit.badge.BadgeArt
import app.backlit.badge.BadgeIcons
import app.backlit.badge.BadgeMessage
import app.backlit.badge.BadgeMessage.Kind
import app.backlit.badge.BadgePreviewAnimation
import app.backlit.badge.BadgeText
import app.backlit.data.Settings
import app.backlit.glyph.DeviceProfile
import kotlinx.coroutines.delay
import java.time.ZoneId

@Composable
fun BadgeTab(settings: Settings, profile: DeviceProfile, onUpdate: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    val runtime = remember { AlertsRuntime.get(context) }
    val size = if (profile == DeviceProfile.PHONE_4A_PRO) 13 else 25
    val messages = BadgeMessage.decodeList(settings.badgeMessages)
    val active = BadgeMessage.activeIndex(settings.badgeActive, messages.size)
    val current = messages[active]
    val opened = remember { System.currentTimeMillis() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(50); now = System.currentTimeMillis() } }
    val since = settings.badgeActiveSince.takeIf { it > 0 } ?: opened
    val zone = ZoneId.systemDefault()
    val text = BadgeText.scroll(current, since, now, settings.use24h, zone)

    fun save(list: List<BadgeMessage>, newActive: Int, restart: Boolean) = onUpdate {
        val clean = BadgeMessage.cleanList(list)
        it.copy(
            badgeMessages = BadgeMessage.encodeList(clean),
            badgeActive = BadgeMessage.activeIndex(newActive, clean.size),
            badgeActiveSince = if (restart) System.currentTimeMillis() else it.badgeActiveSince,
        )
    }

    if (profile != DeviceProfile.UNSUPPORTED && !settings.badgeToyEverBound) {
        Notice("Turn on Backlit Badge in Glyph Toys (Settings → Glyph Interface → Glyph Toys), then lay your phone face-down.")
    }

    MatrixPreview(BadgeArt.frame(size, current, text, now - opened, BadgeArt.FLASH_MS), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
    Text(text.ifBlank { "(icon only)" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))

    var editing by remember { mutableStateOf<Int?>(null) }   // index being edited, or -1 for a new message
    Text("MESSAGES", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
    messages.forEachIndexed { i, m ->
        if (editing == i) {
            BadgeEditor(m, settings.use24h, canDelete = messages.size > 1,
                onSave = { e -> save(messages.toMutableList().also { it[i] = e }, active, restart = i == active); editing = null },
                onDelete = { save(messages.filterIndexed { k, _ -> k != i }, BadgeMessage.afterDelete(active, i, messages.size), restart = i == active); editing = null },
                onCancel = { editing = null })
        } else {
            Row(
                Modifier.fillMaxWidth().border(1.dp, if (i == active) BacklitColors.White else BacklitColors.Line)
                    .clickable { save(messages, i, restart = true) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MatrixPreview(BadgeArt.tile(m.icon), Modifier.size(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.text.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
                    Text(kindLabel(m, settings.use24h), style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim)
                }
                SquareChip("↑", false, {
                    if (i > 0) save(messages.toMutableList().also { it[i] = messages[i - 1]; it[i - 1] = m }, BadgeMessage.moveActive(active, i, i - 1), restart = false)
                })
                SquareChip("↓", false, {
                    if (i < messages.size - 1) save(messages.toMutableList().also { it[i] = messages[i + 1]; it[i + 1] = m }, BadgeMessage.moveActive(active, i, i + 1), restart = false)
                })
                SquareChip("EDIT", false, { editing = i })
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    if (editing == -1) {
        BadgeEditor(BadgeMessage("", "heart"), settings.use24h, canDelete = false,
            onSave = { e -> save(messages + e, messages.size, restart = true); editing = null },
            onDelete = {}, onCancel = { editing = null })
    } else if (messages.size < BadgeMessage.MAX_MESSAGES) {
        SquareChip("+ ADD MESSAGE", false, { editing = -1 }, Modifier.fillMaxWidth())
    }

    Text("Tap a message to show it. Long press the Glyph button to switch messages on the back.",
        style = MaterialTheme.typography.labelSmall, color = BacklitColors.Dim, modifier = Modifier.padding(top = 10.dp))
    SquareChip("SHOW ON GLYPH", true, { runtime.preview(BadgePreviewAnimation.idFor(current.icon, text), 6000L) },
        Modifier.fillMaxWidth().padding(vertical = 12.dp))
    Spacer(Modifier.height(8.dp))
}

private fun kindLabel(m: BadgeMessage, use24h: Boolean): String = when (m.kind) {
    Kind.PLAIN -> "PLAIN"
    Kind.COUNTDOWN -> "COUNTDOWN · ${m.minutes} MIN"
    Kind.UNTIL -> "UNTIL " + BadgeText.timeLabel(m.untilMinuteOfDay, use24h)
}

@Composable
private fun BadgeEditor(
    initial: BadgeMessage,
    use24h: Boolean,
    canDelete: Boolean,
    onSave: (BadgeMessage) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.text) }
    var icon by remember { mutableStateOf(initial.icon) }
    var kind by remember { mutableStateOf(initial.kind) }
    var minutes by remember { mutableIntStateOf(initial.minutes) }
    var until by remember { mutableIntStateOf(initial.untilMinuteOfDay) }

    Column(Modifier.fillMaxWidth().border(1.dp, BacklitColors.White).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = BadgeMessage.cleanText(it) },
            placeholder = { Text("MESSAGE") },
            supportingText = { Text("${text.length} / ${BadgeMessage.MAX_TEXT}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BadgeIcons.ids.forEach { id ->
                Box(Modifier.weight(1f).border(1.dp, if (id == icon) BacklitColors.White else BacklitColors.Line).clickable { icon = id }.padding(2.dp)) {
                    MatrixPreview(BadgeArt.tile(id), Modifier.fillMaxWidth())
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Kind.PLAIN to "PLAIN", Kind.COUNTDOWN to "COUNTDOWN", Kind.UNTIL to "UNTIL").forEach { (k, label) ->
                SquareChip(label, kind == k, { kind = k }, Modifier.weight(1f))
            }
        }
        when (kind) {
            Kind.COUNTDOWN -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SquareChip("−5", false, { minutes = (minutes - 5).coerceAtLeast(1) })
                SquareChip("−1", false, { minutes = (minutes - 1).coerceAtLeast(1) })
                Text("$minutes MIN", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SquareChip("+1", false, { minutes = (minutes + 1).coerceAtMost(180) })
                SquareChip("+5", false, { minutes = (minutes + 5).coerceAtMost(180) })
            }
            Kind.UNTIL -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SquareChip("H−", false, { until = (until - 60 + 1440) % 1440 })
                SquareChip("H+", false, { until = (until + 60) % 1440 })
                Text(BadgeText.timeLabel(until, use24h), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SquareChip("M−", false, { until = (until - 5 + 1440) % 1440 })
                SquareChip("M+", false, { until = (until + 5) % 1440 })
            }
            Kind.PLAIN -> Unit
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SquareChip("SAVE", true, { onSave(BadgeMessage(text, icon, kind, minutes, until).clean()) }, Modifier.weight(1f))
            SquareChip("CANCEL", false, onCancel, Modifier.weight(1f))
            if (canDelete) SquareChip("DELETE", false, onDelete, Modifier.weight(1f))
        }
    }
}
```

- [ ] **Step 2: Add the tab to `HomeScreen`**

In `HomeScreen.kt`, change the tab list to `listOf("CLOCK", "MUSIC", "ALERTS", "CHARGE", "STUDIO", "PET", "TIMER", "BADGE")`. Then change the tail of `when (tab)` from `else -> SandTab(settings, profile, onUpdate)` to:

```kotlin
            6 -> SandTab(settings, profile, onUpdate)
            else -> BadgeTab(settings, profile, onUpdate)
```

- [ ] **Step 3: Build and run all the tests**

Run: `source .superpowers/env.sh && ./gradlew --no-daemon :app:assembleDebug && .superpowers/runtests.sh`
Expected: `BUILD SUCCESSFUL`, and the last line shows `0 failures, 0 errors`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/backlit/ui/BadgeScreen.kt app/src/main/java/app/backlit/ui/HomeScreen.kt
git commit -m "feat(badge): BADGE tab with message list and editor

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Docs and on-device check

**Files:**
- Modify: `README.md` (Features, Project structure, Roadmap)
- Modify: `docs/testing/device-checklist.md`

- [ ] **Step 1: README**

Add this after the "Backlit Sand (Glyph Toy)" section:

```markdown
### Backlit Badge (Glyph Toy)
A status sign for when your phone is face-down: a pixel icon with your message scrolling underneath.
- **Messages:** up to 8, each with one of 8 icons (laptop, coffee, phone, moon, heart, car, headphones, food).
  Starters: IN A MEETING, BACK IN 5, ON A CALL, DO NOT DISTURB, THANK YOU.
- **Smart messages:** a countdown ("BACK IN 4:59" → "BACK SOON") or until a time ("IN A MEETING UNTIL 3PM").
- **Long press** the Glyph button to switch messages; the icon flashes on change.
- **Always-on:** the icon with a short word, the minutes left or the time, once a minute.
- **BADGE tab:** pick, add, edit, reorder and delete messages.
```

Add a project-structure line after the `sand/` lines:

```
├── badge/           Message badge: BadgeMessage, BadgeText (countdown/until), BadgeFont, BadgeIcons,
│                    BadgeArt, BadgePreviewAnimation (pure)
```

In the Roadmap, remove the "Message badge" line.

- [ ] **Step 2: Device checklist** (append to `docs/testing/device-checklist.md`)

```markdown
## Backlit Badge
- [ ] Glyph Toys picker shows "Backlit Badge" with the laptop + text image
- [ ] Each starter message: icon on top, text scrolls underneath; nothing overlaps on 13×13
- [ ] Long press switches to the next message; the icon flashes
- [ ] BACK IN 5 counts down in real time and becomes BACK SOON
- [ ] An UNTIL message shows the time, and drops it once the time passes
- [ ] BADGE tab: tap to pick, add, edit (text, icon, kind), reorder, delete; the Glyph follows
- [ ] Always-on still shows the icon + short word / minutes left
- [ ] 4a Pro: always-on still with a 5×5 icon and 3-letter text
- [ ] The new icons (car, headphones, food) look right
```

- [ ] **Step 3: Run the full suite, build and install**

Run: `source .superpowers/env.sh && .superpowers/runtests.sh && ./gradlew --no-daemon :app:installDebug`
Expected: `0 failures, 0 errors`, and `Installed on 1 device.`

- [ ] **Step 4: Commit**

```bash
git add README.md docs/testing/device-checklist.md
git commit -m "docs: Backlit Badge README and device checklist

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
