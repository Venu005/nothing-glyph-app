# Backlit — Message Badge — Design

**Date:** 2026-10-06
**Status:** Draft for review
**Roadmap:** follows the sand timer (PR #11); "Blow out the candles" comes next.

## 1. Goal

**Backlit Badge** is a Glyph toy that works as a status sign for the people around you. You lay the phone face-down
and the matrix shows a pixel icon with a scrolling message, such as "IN A MEETING", "BACK IN 4:12" or "DO NOT DISTURB".
Messages can be smart: a countdown, or "until" a time of day.

### Success criteria
- **Style A (icon + ticker)** at 25×25 and 13×13, matching the approved mockups. At 13×13 the icon and the text
  never overlap.
- **Long press** moves to the next message, and the new icon flashes once.
- **Countdown and "until" messages update live.** When they run out, the wording changes ("BACK SOON", or the time is
  dropped) and the message keeps showing.
- **Always-on** shows one still a minute: the icon plus a short word, the minutes left, or the time.
- **A BADGE tab** to pick, add, edit, reorder and delete messages.
- **No new permissions**, and no sensors.

### Out of scope
- Automatic status from the calendar, calls or Do Not Disturb.
- Custom drawn icons. A Studio drawing as an icon is a later idea.
- Lowercase letters, emoji or other scripts.
- Scheduling messages by time of day.

## 2. Visual source of truth
- **Layout:** style A in `docs/superpowers/mockups/2026-10-06-badge-styles.html`.
  - **25×25:** the 9×9 icon at x = (25 − 9) / 2, y = 3, brightness 0.85. The text uses the 5×7 font on rows
    14–20, scrolling right to left at 1 px per 55 ms. One loop is `textWidth + 25 + 4` px.
  - **13×13:** the layout from `docs/superpowers/mockups/2026-10-06-badge-icons.html`. The 5×5 icon is at
    x = 4, y = 1. The text uses the 3×5 font on rows 7–11, scrolling at 1 px per 80 ms. One loop is
    `textWidth + 13 + 4` px.
  - Text brightness is 1.0. Brightness maps 0..1 → 0..255, as in the earlier toys.
- **5×7 letters:** the `F7` table in the badge-styles mockup, with digits from the existing `PixelFont5x7`.
  **3×5:** the Studio's `PixelFontText`.
- **Always-on still:**
  - **25×25:** the icon at y = 4, and the short text in the 5×7 font centred at y = 15.
  - **13×13:** the icon at y = 1, and the short text in the 3×5 font, at most 3 characters, centred at y = 7.
- **Change flash:** for 600 ms after a message becomes current, the icon blinks at full brightness (on at 0–150 ms and
  300–450 ms, at 0.85 otherwise). The ticker restarts from the right edge.
- **Icons:** these tables are the source of truth. "X" is lit.

| id | 9×9 (25×25) | 5×5 (13×13) |
|---|---|---|
| `laptop` | `.XXXXXXX.` `.X.....X.` `.X.....X.` `.X.....X.` `.XXXXXXX.` `XXXXXXXXX` | `.XXX.` `.X.X.` `.XXX.` `XXXXX` |
| `coffee` | `..X..X...` `...X..X..` `.........` `XXXXXXX..` `XXXXXXXXX` `XXXXXXX.X` `XXXXXXXXX` `.XXXXX...` | `.X.X.` `X.X..` `XXXX.` `XXXXX` `.XX..` |
| `phone` | `XXX......` `XXXX.....` `XXX......` `.XX......` `.XX......` `.XXX.....` `..XXX.XXX` `...XXXXXX` `.....XXX.` | `XX...` `X....` `X....` `XX.XX` `.XXX.` |
| `moon` | `..XXXX...` `.XXX.....` `XXX......` `XXX......` `XXX......` `XXX......` `XXXX...XX` `.XXXXXXX.` `..XXXXX..` | `.XX..` `XX...` `X....` `XX..X` `.XXX.` |
| `heart` | `.XX...XX.` `XXXX.XXXX` `XXXXXXXXX` `XXXXXXXXX` `.XXXXXXX.` `..XXXXX..` `...XXX...` `....X....` | `XX.XX` `XXXXX` `XXXXX` `.XXX.` `..X..` |
| `car` | `..XXXXX..` `.X.....X.` `X.......X` `XXXXXXXXX` `X.XXXXX.X` `XXXXXXXXX` `XX.....XX` | `.XXX.` `XXXXX` `X.X.X` `XXXXX` `X...X` |
| `headphones` | `..XXXXX..` `.X.....X.` `X.......X` `X.......X` `XX.....XX` `XXX...XXX` `XXX...XXX` `.X.....X.` | `.XXX.` `X...X` `X...X` `XX.XX` `XX.XX` |
| `food` | `X.X.X..X.` `X.X.X.XX.` `X.X.X.XX.` `XXXXX.XX.` `.XXX..XX.` `..X....X.` `..X....X.` `..X....X.` `..X....X.` | `X.X.X` `X.X.X` `XXX.X` `.X..X` `.X..X` |

  Icons shorter than 9 (or 5) rows are vertically centred in their 9×9 (or 5×5) box. The new icons (car, headphones and
  food) get a look on the device before merge.

## 3. Behaviour

### Messages
- A message has:
  - `text`: up to 32 characters, uppercase A–Z, 0–9, space and `: ! ? . ' -`. Other characters are dropped and
    lowercase is uppercased.
  - `icon`: one of the 8 ids.
  - `kind`: PLAIN, COUNTDOWN or UNTIL.
  - `minutes`: 1..180, for COUNTDOWN.
  - `untilMinuteOfDay`: 0..1439, for UNTIL.
- **Starter list:**

| text | icon | kind |
|---|---|---|
| IN A MEETING | laptop | PLAIN |
| BACK IN | coffee | COUNTDOWN 5 |
| ON A CALL | phone | PLAIN |
| DO NOT DISTURB | moon | PLAIN |
| THANK YOU | heart | PLAIN |

- A list holds 1..8 messages. One is current (`badgeActive`), and `badgeActiveSince` is the wall-clock ms when it
  became current.

### What is shown (`BadgeText`)
- **PLAIN:** `text`.
- **COUNTDOWN:** let `left = minutes·60 s − (now − activeSince)`.
  - While `left > 0`, show `"$text m:ss"` with seconds rounded up (for example "BACK IN 4:59"). From 1 hour up it is
    `h:mm:ss`.
  - When it reaches 0, show `"$text"` with a trailing " IN" dropped, followed by " SOON". So "BACK IN" becomes
    "BACK SOON", and any other text gets " SOON" added (for example "LUNCH" becomes "LUNCH SOON").
- **UNTIL:** the end is the next time `untilMinuteOfDay` comes round after `activeSince`. If it is the same minute or
  earlier, that means tomorrow.
  - Before the end, show `"$text UNTIL 3PM"` (12-hour, "3:30PM" when the minutes aren't zero) or `"$text UNTIL 15:00"`
    (24-hour), following the Clock's `use24h` setting.
  - After the end, show `text`.
- **Short text for the always-on still:**
  - COUNTDOWN running: `"${ceil(left/60)}M"`. It caps at "99M"; from 100 minutes it shows "${ceil(left/3600)}H".
  - COUNTDOWN expired: "SOON".
  - UNTIL running: "3PM" (or "15" for 24-hour, the hour only).
  - Otherwise: the first word of `text`.
  - At 13×13 the short text is cut to 3 characters.
- **Empty text:** the icon is shown alone and the ticker is blank.

### Glyph button
- **Long press:** `badgeActive = (badgeActive + 1) % size` and `badgeActiveSince = now`, then the change flash plays.

### Always-on
- On EVENT_AOD (once a minute), draw `BadgeArt.still`. The minutes left and the expiry are recomputed each minute.

## 4. Architecture

### Pure core (`badge/`, JVM tests)
| Unit | Responsibility |
|---|---|
| `BadgeMessage` | `@Serializable` data class, `Kind`, `clean()`, `STARTERS`, and companion list helpers `cleanList` / `decodeList` / `encodeList` (1..8, cleaned messages, bad JSON → starters) plus the index helpers `activeIndex` / `moveActive` / `afterDelete` |
| `BadgeText` | `scroll(msg, activeSince, now, use24h, zone)` and `short(msg, activeSince, now, use24h, zone)` |
| `BadgeFont` | 5×7 glyphs (A–Z and punctuation, with digits from `PixelFont5x7`), `width(text)` and `draw(grid, text, x, y, b)`. 13×13 uses `PixelFontText` |
| `BadgeIcons` | `ICONS: Map<String, Pair<List<String>, List<String>>>`, `draw(grid, id, x, y, b)` and `ids` |
| `BadgeArt` | `frame(size, msg, text, tMs, sinceChangeMs)` and `still(size, msg, short)` |
| `BadgePreviewAnimation` | `badge:<icon>:<text>` scrolls that text with that icon in a loop (one full scroll). Show on Glyph sends the current message's icon and its current scroll text. The picker image is the `laptop` icon with "IN A MEETING" at t = 1200 ms |

### Android
- **`glyph/BadgeToyService`:** follows `CanvasToyService` / `PetToyService`.
  - It uses `DeviceProfile`, `ModeTracker`, `GlyphOutput`, `FramePacer(50)`, `kick()`, `ToyPresence` and the `AlertsRuntime` bus.
  - EVENT_CHANGE runs the long press.
  - The loop runs at 50 ms while ACTIVE. In AOD it draws one still and stops.
- **Settings:**
  - `badgeMessages` (JSON, default `""` = starters)
  - `badgeActive` (0, clamped)
  - `badgeActiveSince` (0 = treat as "now" when first shown)
  - `badgeToyEverBound` (false)
- **Manifest:** a GlyphToy service, `toy.name` "Backlit Badge", a summary, `toy.longpress`, `toy.aod_support`, and a
  `toy.image` from `ToyPreviewXml` (golden in `ToyPreviewsTest`).
- **`ui/BadgeScreen`:** the BADGE tab, after TIMER. It contains:
  - a live preview of the current message (at the device size)
  - the message list (icon, text, kind summary); tap to make one current; ↑ ↓ to reorder; edit; delete (not the last one)
  - "+ ADD MESSAGE" (up to 8)
  - the editor: a text field (uppercased and cleaned as you type, with a 32-character counter); an icon row of 8 tiles,
    each drawn as a live dot icon; PLAIN / COUNTDOWN / UNTIL chips; a minutes stepper (−5, −1, +1, +5) or an
    hour/minute stepper; SAVE / CANCEL
  - a hint line ("Long press the Glyph button to switch messages") and Show on Glyph

## 5. Edge cases
- **Blank or all-invalid text:** the message is kept with its icon and an empty ticker.
- **Deleting the current message:** the next one (wrapping round) becomes current, and `badgeActiveSince = now`.
- **Reordering** keeps the same message current.
- **Editing the current message** restarts its countdown or until (`badgeActiveSince = now`).
- **UNTIL picked after its time:** it means tomorrow.
- **The clock changes** (time zone or manual): the until end is recomputed from `activeSince` and the zone each frame.
- **Alerts** take over the matrix as usual.

## 6. Testing
- **BadgeMessage:** cleaning (case, allowed set, 32 maximum, `minutes` 1..180, `untilMinuteOfDay` 0..1439), the JSON
  round trip, the starters, and the list cleaning (empty → starters, more than 8 → first 8).
- **BadgeText:**
  - countdown at 0 s, mid-way and at 1 s left, then "BACK SOON", and `h:mm:ss` for an hour or more
  - until before and after the end, including after midnight
  - 12-hour and 24-hour formats, including ":30"
  - the short forms (with "99M" / "2H" caps), and the first word for PLAIN
- **BadgeFont / BadgeIcons:** every allowed character has a glyph at both sizes, and every icon fits its box and the
  mask at its draw position.
- **BadgeArt:**
  - every icon with a long message at both sizes stays inside the mask with lit pixels
  - at 13×13 icon pixels only appear on rows 1–5 and text pixels only on rows 7–11
  - the ticker frames differ over time
  - the flash is brighter than the steady icon
  - the still shows both icon and text
- **Settings:** defaults, list round trip, index clamping.
- **ToyPreviewsTest:** the golden `ic_badge_preview`.
- **Device checklist:**
  - the picker image
  - each starter message
  - long press cycles with the flash
  - a countdown in real time to BACK SOON
  - an until message across its time
  - adding, editing, reordering and deleting in the tab
  - the always-on still
  - the 4a Pro still
  - the new icons look right
