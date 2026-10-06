# Backlit — UI Redesign Part 2 (Toy Pages, Studio, Alerts) — Design

**Date:** 2026-10-06
**Status:** Draft for review
**Builds on:** part 1 (PR #13) and its spec `2026-10-06-backlit-ui-redesign-design.md`. That spec's §4 table and §5 design
system still apply. This document adds the decisions made for part 2.

## 1. Goal

Finish the redesign:
- every toy page gets the approved page shape (`docs/superpowers/mockups/2026-10-06-toy-page.html`)
- Studio becomes a **gallery** (mockup `2026-10-06-studio-alerts.html`, Studio A)
- Alerts becomes **segmented** (same mockup, Alerts B)

Toy, Glyph, alert and drawing behaviour does not change. Only the screens and their organisation do.

### Success criteria
- **Every toy page** uses one scaffold: header, big live preview, name, pager dots, status line, settings, and a fixed bottom
  bar. Content never sits under the bar.
- **Every setting** that exists today is still reachable from its toy page, Studio or Alerts.
- **Studio** is a 2-column gallery with a per-drawing action sheet and a NEW / IMPORT bottom bar.
- **Alerts** has CONTACTS | DEVICES | ANIMATIONS segments, at most one needs-attention card, sheets for editing a rule, and
  a per-segment bottom action.
- **Only the settled pager page animates.** Home previews pause when the app is not visible.
- **No new permissions.** All existing tests stay green.

### Out of scope
- New toy features.
- A light theme.
- Tablet layouts.
- Changing the drawing editor's own layout (it stays as approved in the Studio spec, restyled only by the theme tokens).

## 2. Toy pages

### Scaffold (`ToyPageScaffold`)
The page has three layers:
- **Header** (`ToyHeader`).
- **Scrollable column:**
  - `HeroPreview`: a `MatrixPreview` at 62 % of the width, centred
  - the name (`headlineSmall`, centred)
  - `PagerDots`
  - the status line (`labelSmall`, Dim, centred)
  - the content
- **`BottomActionBar`:** pinned to the bottom of the page, on black, with a 1 dp top line and 12/16/18 dp padding. The scroll
  column's bottom padding equals the bar's measured height plus 16 dp.

### Per toy
| Toy | Status (`ToyStatus`) | Content | Bottom (`ToyAction`) |
|---|---|---|---|
| Clock | `ANALOG` / `DAY RING` | face `ChipRow`; second hand row (Analog); time format row and sun times row → Location (Day ring) | `OPEN GLYPH TOYS` |
| Music | `LISTENING`, `NEEDS PERMISSION` or `PHONE (3) ONLY` | style `ChipRow` (Mirror / Peaks); sensitivity `ChipRow` (Low / Med / High); permission row when not granted | `OPEN GLYPH TOYS` |
| Charge | `62 % · MOON` (live battery % and style label) | style row → `OptionSheet` with live previews of the built-ins plus imports; done-target stepper row (− 85 % +; 50–100 in steps of 5, as today); plug-in animation row → sheet; done animation row → sheet | `SHOW ON GLYPH` (plug-in moment) |
| Canvas | drawing name, or `NO DRAWING YET` | drawing row → `OptionSheet` with `GalleryCard`s of your drawings; `OPEN STUDIO` row | `SHOW ON GLYPH` (the drawing); with no drawing: `+ NEW DRAWING` |
| Pet | `BOO IS HAPPY · MOOD 72`, then the 10-dot meter under it | pet row → `OptionSheet` (6 live tiles); name field; sleep hours rows; how mood works (expand) | `SHOW ON GLYPH` (happy) |
| Sand | `READY · 5 MIN` / `RUNNING · 3:12 LEFT` / `PAUSED · 3:12 LEFT` / `TIME'S UP` | times `ChipRow` plus `EDIT` (add or remove); when time's up `ChipRow`; ring exactly on time row; how it works (expand) | `SHOW ON GLYPH` (running) |
| Badge | the live scroll text | message rows (tap to show, ↑ ↓, EDIT); `+ ADD MESSAGE` row; the editor opens in an `OptionSheet` | `SHOW ON GLYPH` (current message) |

**`ToyAction` rules:**
- Not set up on a supported phone → `TURN ON IN GLYPH TOYS` (red dot). This opens Glyph Toys, or Setup if that screen is missing.
- Unsupported phone (no Glyph Matrix) → no bottom button (`null`), except Canvas with no drawing, which keeps `+ NEW DRAWING`.
  Previews can't play there, and Glyph Toys doesn't exist.
- Otherwise → the action in the table.

**Removed:** the per-toy "Turn on … in Glyph Toys" notices.

## 3. Studio (gallery)
- **Header:** `PageHeader("STUDIO")`, then "N DRAWINGS · TAP ONE FOR OPTIONS".
- **Gallery:** `GalleryCard`s in 2 columns. Each card shows:
  - a live looping preview of the drawing
  - the name
  - "N FRAMES" (or "1 FRAME")
  - the drawing on the Canvas toy has a white outline and "● ON CANVAS"
- **Tap a card → `OptionSheet`** with the preview at the top and these actions:
  - EDIT
  - SHOW ON CANVAS (hidden when it's already on the Canvas)
  - SHOW ON GLYPH
  - SHARE
  - RENAME (inline field + SAVE)
  - DELETE (confirm step inside the sheet: "Contacts, devices or charging that use it go back to their default.")
- **Empty state:** "Draw your own pictures and animations for the Glyph. Use them for calls, devices, charging, or on the
  Canvas toy."
- **Bottom bar:** `+ NEW DRAWING` (primary) and `IMPORT` (secondary, the Glyph Museum file picker as today). Import results
  show as a one-line message under the header.

## 4. Alerts (segmented)
- **Header:** `PageHeader("ALERTS")`, then a `SegmentedControl` with CONTACTS | DEVICES | ANIMATIONS. The chosen segment is
  saved per screen instance (`rememberSaveable`).
- **`AttentionCard`:** at most one, above the segments, from `AlertsAttention.pick(...)`:
  1. notification access missing, when any contact exists or the CONTACTS segment is open → "Allow notification access so
     calls light the Glyph" → opens the system notification access screen (as today)
  2. Nearby devices permission missing while devices exist → "Allow Nearby devices so Backlit can notice your devices" →
     requests the permission (as today)
  3. otherwise nothing
- **CONTACTS segment:**
  - **Rows:** an initials circle, the name, the animation name, and a live mini preview on the right.
  - **Tap a row → sheet:** an animation picker grid (built-ins, imports and drawings, with a live tile each); PREVIEW ON GLYPH; REMOVE.
  - **Bottom:** `+ ADD CONTACT` (system contact picker, as today).
  - **"About alerts" row:** expands the disclosure text, the Nothing ringtone-lights note, and the always-on tip.
- **DEVICES segment (Bluetooth connect alerts):** each row is a paired Bluetooth device you chose (earbuds, car, watch…).
  Its animation plays on the Glyph when that device connects, exactly as today (`BluetoothAlertReceiver`, unchanged).
  - **Rows:** a device glyph circle, the device name, the animation name, and a live mini preview.
  - **Tap a row → sheet:** the same animation picker; PREVIEW ON GLYPH; REMOVE.
  - **Bottom:** `+ ADD DEVICE`, which opens a sheet listing the paired Bluetooth devices (asking for Nearby devices first if
    needed), or "No paired Bluetooth devices found."
  - **Segment hint line:** "Plays when the device connects."
- **ANIMATIONS segment:**
  - **Gallery:** a 3-column gallery of every animation, each a live tile with its name.
  - **Tap:** preview on the Glyph.
  - **Imported or drawn tiles:** a sheet with DELETE. Drawings say "Edit in Studio" and link there.
  - **Bottom:** `IMPORT FROM GLYPH MUSEUM`.

## 5. Components (`ui/components/`)
- `HeroPreview(grid)`
- `ChipRow(options: List<Pair<String,String>>, selected: String, onSelect)`: pills; the selected one is filled white with black text
- `SegmentedControl(options, selected, onSelect)`: the same pill look in a single row
- `BottomActionBar(primary: ActionSpec, secondary: ActionSpec? = null)`, with `ActionSpec(label, style: PRIMARY / OUTLINE / ATTENTION, onClick)`
- `OptionSheet(title, onDismiss, content)`: Material3 `ModalBottomSheet`, black container, top handle in `Line`
- `SheetAction(label, danger = false, onClick)`
- `GalleryCard(grid, name, subtitle, highlighted, onClick)`
- `AttentionCard(text, onClick)`
- `ToyPageScaffold(onBack, setUp, onTurnOn, hero, name, index, count, status, content, action)`

`SquareChip`, `DashedDivider` and `Notice` are removed once nothing uses them. `SettingRow` stays, restyled with a 1 dp
`Line` separator.

## 6. Architecture
- **Pure, with JVM tests:**
  - `ui/toys/ToyStatus.kt`: `ToyStatus.line(id, s, now, zone, inputs: StatusInputs)` gives the status string per §2.
    `StatusInputs(battery: Int?, micGranted: Boolean, musicSupported: Boolean, petBase: String?, petMood: Int?, hasDrawing: Boolean,
    drawingName: String?)` carries the values that need Android or the pet brain; the caller fills them in.
  - `ui/toys/ToyAction.kt`: `ToyAction.of(id, setUp, supported, hasDrawing): Action` (`ShowOnGlyph(previewId)`,
    `OpenGlyphToys`, `TurnOn`, `NewDrawing`) or `null` (no bar), plus `previewIdFor(id, s, now, zone): String?`. Canvas
    plays the drawing through `previewAnimation` and has no id.
  - `ui/alerts/AlertsAttention.kt`: `AlertsAttention.pick(notificationAccess, btGranted, contacts, devices, segment)`
    returns `NotificationAccess`, `Nearby` or `null`.
- **Screens:**
  - `ui/toys/<Toy>Toy.kt`, one per toy: the hero grid function, the content composable, and any sheets. Logic moves out of
    `*Screen.kt`, which are deleted.
  - `ToyPagerScreen` uses `ToyPageScaffold`.
  - `StudioScreen` and `AlertsScreen` are rewritten (the old `StudioTab` / `AlertsTab` are removed). Their runtime calls,
    pickers and importers are reused as they are.
- **Animation:** pages and Home use a shared `rememberTicker(periodMs, active)`. It stops when `active` is false (pager page
  not settled) and when the lifecycle is below STARTED.
- **Part 1 polish folded in:**
  - ⚙ and ← get 48 dp touch targets
  - unsupported phones show `PREVIEW` instead of `TURN ON`
  - the Sand preview animation is cached
  - the Home canvas drawing is loaded off the main thread
  - unused imports are removed

## 7. Testing
- **`ToyStatus`:**
  - each toy's line, for example Sand in every phase, Pet mood words, Charge with and without a battery level, Clock faces,
    Music states, Badge text, and Canvas with and without a drawing
- **`ToyAction`:**
  - set up / not set up / unsupported for every toy (unsupported → `null`, except Canvas with no drawing)
  - Canvas with no drawing → `NewDrawing`
  - Clock and Music → `OpenGlyphToys`
  - preview ids match the existing ones (pet happy, sand running, the badge current message, the charge plug-in)
- **`AlertsAttention`:** the priority order, and nothing when everything is granted.
- **All existing tests stay green.** No ToyPreviews golden change is expected.
- **Device checklist:**
  - each toy page (hero, status, settings, bottom bar, never covered)
  - swiping (only the visible page animates)
  - Studio: gallery, sheet actions, NEW and IMPORT, the empty state
  - Alerts: segments, the attention card, rule sheets, add contact or device, animations gallery and import
  - Home previews pause in the background
  - the 4a Pro: no Music, 13×13 heroes
