# Backlit — Alerts: Important Callers & Bluetooth Devices — Design

**Date:** 2026-10-04
**Status:** Draft for review
**Builds on:** Part 1 (clocks) and Part 2 (music), both merged.
**Replaces in the roadmap:** AI reactions (Part 4) are parked. This "Alerts" part comes first.

## 1. Goal

Play an animation on the Glyph Matrix when:
1. **an important contact calls.** The user picks contacts and gives each one its own animation.
   Calls from anyone else do nothing.
2. **a chosen Bluetooth device connects.** The user picks paired devices and gives each one its own
   animation. Other devices do nothing.

Animations come from **6 built-ins** or are **imported from Glyph Museum** (JSON export).

### Success criteria
- With the Glyph idle (screen on or off), an important caller's animation loops on the matrix while
  the phone rings and stops when the call is answered, declined or missed.
- With a Backlit toy showing (Clock or Music, including Clock as the AOD toy), the same alert
  plays inside that toy, then the toy resumes.
- A chosen Bluetooth device connecting plays its animation once (~3 s).
- A real Glyph Museum JSON export imports and plays correctly.
- No crash or stuck matrix in any failure path. No network. Caller names are never stored except
  the ones the user picked.

### Out of scope
AI reactions, OTP (Part 3), all-calls/all-devices defaults, matching by phone number, GIF/video
import, editing animations, cloud or Glyph Museum online browsing.

## 2. Verified platform facts (spike on Phone (3), 2026-10-04)

- `GlyphMatrixManager.setAppMatrixFrame(int[])` / `closeAppMatrix()` **work outside a toy**, from a
  broadcast receiver, with the Glyph idle and the **screen on or off**.
- **When any toy is showing, app-matrix frames are not shown.** The toy wins, as the SDK docs say.
  So when one of Backlit's own toys is active, the alert must be rendered by that toy.
- `GlyphMatrixManager` is a per-process singleton. The toy and the app-matrix player must never use
  it at the same time.
- **Phone (4a) Pro app-matrix behaviour is unverified** (no device). It is designed for, and
  previewable in the app, but unconfirmed.

## 3. Animations

### 3.1 Model (`anim/`, pure Kotlin)
```
interface GlyphAnimation {
    val id: String                       // "builtin:heart" … or "import:<uuid>"
    val name: String
    val loopMs: Long                     // length of one loop
    fun frame(size: Int, tMs: Long): PixelGrid   // size 25 or 13; t wraps by loopMs
}
```
- **Built-ins** are procedural with hand-placed bitmaps where shapes matter. They must look right at
  both 25 and 13. The approved reference is the brainstorm mockup `event-animations-v7.html`:
  - **Heartbeat (`builtin:heart`):** loop 1200 ms. The big bitmap shows during 0–12% and 24–36% of
    the loop at 255, otherwise the small bitmap at 153. Bitmaps, centred:
    - 25 small (11×9): `.XXX...XXX.` `XXXXX.XXXXX` `XXXXXXXXXXX` `XXXXXXXXXXX` `.XXXXXXXXX.` `..XXXXXXX..` `...XXXXX...` `....XXX....` `.....X.....`
    - 25 big (15×13): `..XXXX...XXXX..` `.XXXXXX.XXXXXX.` `XXXXXXXXXXXXXXX`×4 `.XXXXXXXXXXXXX.` `..XXXXXXXXXXX..` `...XXXXXXXXX...` `....XXXXXXX....` `.....XXXXX.....` `......XXX......` `.......X.......`
    - 13 small (7×6): `.XX.XX.` `XXXXXXX` `XXXXXXX` `.XXXXX.` `..XXX..` `...X...`
    - 13 big (9×8): `.XX...XX.` `XXXX.XXXX` `XXXXXXXXX` `XXXXXXXXX` `.XXXXXXX.` `..XXXXX..` `...XXX...` `....X....`
  - **Smiley wink (`builtin:smiley`):** loop 2400 ms; it winks during 55–80% of the loop. The outline
    is every pixel with |distance − R| < 0.5 at 140 (R = 8.5 on 25, R = 5 on 13). The face pixels are
    at 255:
    - 25: left eye (9..10, 9..10). Right eye (14..15, 9..10), or when winking (13..16, 10).
      Smile `(9,14)(10,15)(11,15)(12,15)(13,15)(14,15)(15,14)`. Wide smile adds `(8,13)(16,13)`.
    - 13: eyes `(4,4)` and `(8,4)`, or when winking `(7,5)(8,5)(9,5)`.
      Smile `(4,7)(5,8)(6,8)(7,8)(8,7)`. Wide smile adds `(3,6)(9,6)`.
  - **Ringing (`builtin:ring`):**
    - 25: loop 1000 ms. A 5×9 phone outline at the centre shakes ±1 px during the first half.
      Three arcs (±0.6 rad) move from r = 4 to r = 12 on both sides and fade with radius.
    - 13: loop 1200 ms, hand-placed. Phone `(5..7,4)(5,5..7)(7,5..7)(5..7,8)` shakes ±1 px during
      0–33%. Inner arcs `(3,5..7)(9,5..7)` show from 33% (255 until 66%, then 115). Outer arcs
      `(2,4)(1,5..7)(2,8)(10,4)(11,5..7)(10,8)` show from 66%.
  - **Burst (`builtin:burst`):** loop 1100 ms. Rays (16 on 25, 8 on 13) fly out to R (12 / 6) with
    a short trail, fading with progress. The centre flashes during the first 15% (3×3 on 25, 1 px on 13).
  - **Link (`builtin:link`):** loop 1600 ms. Two dots (3×3 on 25, 1 px on 13) fly in from the
    edges with an ease-out over the first 50%. The left x is
    `edge + floor(e × (meet − edge))` and the right x is **exactly `(size − 1) − left`** (they must
    meet at the centre). Then an expanding flash ring runs from 50% to 80%.
  - **Bounce (`builtin:bounce`):** loop 2200 ms. A ball (3×3 on 25, 2×2 on 13) bounces three times
    with decreasing height (9, 5.5, 2.5 / 5, 3, 1.5), moving left to right and squashing on
    landings, above a dim ground line (row 21 / 11).
- **Imported** animations (`FrameAnimation`) are a list of `PixelGrid` frames plus per-frame
  durations, prepared for both sizes (§3.2).

### 3.2 Glyph Museum import (`anim/MuseumFormat`, pure Kotlin)
- Format (Glyph Museum / Glyph Matrix Editor JSON export):
  `{"v": 1|4, "frames": [{"d": <ms, optional>, "p": [<0..255> × LEDs]}]}`.
  - `v = 1` means Phone (3): 489 values. `v = 4` means Phone (4a) Pro: 137 values.
  - Values are listed row by row, only for LEDs inside the round mask. Row widths are
    `[7,11,15,17,19,21,21,23,23,25,25,25,25,25,25,25,23,23,21,21,19,17,15,11,7]` (25) and
    `[5,9,11,11,13,13,13,13,13,11,11,9,5]` (13), centred. These are identical to `PixelGrid.hasLed`.
  - Missing `d` defaults to 100 ms. `d` is clamped to 20–5000 ms.
- **Validation:** valid JSON. `v` is 1 or 4. 1 to 600 frames. Each `p` has exactly the expected
  count. Values are clamped to 0–255. Anything else is rejected with a single user-facing error:
  "This file isn't a Glyph Museum animation."
- **Size conversion:** the other grid size is derived by area-average resampling, so 25→13 and
  13→25 are both available. The UI says "Made for Phone (3), scaled for 4a Pro" (or the reverse).
- **Storage:** the normalised JSON is saved to `filesDir/animations/<uuid>.json`. Index entries
  `{id, name, sourceV}` are kept in the settings store. The name comes from the file name, or is
  "Imported N".
- Format knowledge is from reading the GPL editor's source. **No code is copied.**

## 4. Rules and alert logic (`alerts/`, pure Kotlin)

```
data class ContactRule(val name: String, val animationId: String)
data class DeviceRule(val address: String, val name: String, val animationId: String)
sealed interface AlertEvent { CallRinging(callerName), CallEnded, DeviceConnected(address, atMs) }
data class ActiveAlert(val animationId: String, val kind: CALL|DEVICE, val startedAt: Long, val endsAt: Long?)
```
- **Contact match:** normalise both names (trim, collapse whitespace, lowercase, strip invisible
  bidi marks) and compare for equality. No partial matches.
- **`AlertCoordinator`** is a pure state machine over events + time:
  - `CallRinging` for a matching contact → CALL alert. It loops until `CallEnded`, capped at 60 s.
    Non-matching callers are ignored.
  - `DeviceConnected` for a matching device → DEVICE alert, `endsAt = start + 3000`, unless a CALL
    alert is active (the call wins) or the same device fired in the last 30 s (cooldown).
  - A new CALL replaces a DEVICE alert immediately.
  - `CallEnded` ends a CALL alert. `tick(now)` ends expired alerts.
- **Persistence:** rules and the import index are stored as JSON strings in the existing DataStore
  (kotlinx-serialization). Unknown animation ids fall back to `builtin:heart` for contacts and
  `builtin:link` for devices.

## 5. Playing on the matrix

- **`AlertBus`** is an in-process `StateFlow<ActiveAlert?>` owned by the coordinator.
- **`ToyPresence`** is an in-process `StateFlow<Boolean>`. `ClockToyService` and `MusicToyService`
  set it to true while bound and false on unbind.
- **Toy path:** while an alert is active, the bound Backlit toy renders
  `animation.frame(size, now − startedAt)` instead of its normal frame, using the 50 ms paced
  loop. The Clock toy temporarily switches to per-frame ticking. When the alert ends, the toy
  resumes its normal rendering.
- **App-matrix path (`AppMatrixPlayer`):** while an alert is active **and no Backlit toy is
  present**, it connects `GlyphMatrixManager` (registering the device), pushes frames with
  `setAppMatrixFrame` on a `FramePacer` 50 ms grid, and calls `closeAppMatrix()` + `unInit()` when
  the alert ends (within 0.5 s) or a toy appears.
- Frames are encoded with `FrameEncoder` at the user's brightness (not AOD-dimmed).
- If another app's toy is showing, app-matrix frames simply don't appear. No retry storms: one
  connect attempt per alert, plus the Part 1 retry policy.

## 6. Triggers (Android)

- **Calls: `CallAlertListener : NotificationListenerService`.**
  - `onNotificationPosted`: immediately ignore anything that isn't `category == CALL` with
    `EXTRA_CALL_TYPE == CALL_TYPE_INCOMING` (API 31+). For incoming calls, read `EXTRA_TITLE`
    (the caller name as shown by the Phone app) → `CallRinging(name)`.
  - `onNotificationRemoved` for that notification key, or a later update whose call type isn't
    incoming (answered) → `CallEnded`.
  - Notification contents are never stored or logged.
  - The listener process stays alive while access is enabled, so the app-matrix player can run
    for the whole ring.
- **Bluetooth: `BluetoothAlertReceiver`** (manifest receiver for
  `BluetoothDevice.ACTION_ACL_CONNECTED`, which is exempt from implicit-broadcast limits). Needs
  `BLUETOOTH_CONNECT`. It reads the device address and name → `DeviceConnected`, and uses
  `goAsync()` to keep the process alive for the ~3 s animation.

## 7. App UI — ALERTS tab

Home tabs become **CLOCK | MUSIC | ALERTS** (same raw / dot-matrix style).
- **Status hints** (top, only when relevant):
  - "Turn on notification access to detect calls →" opens `ACTION_NOTIFICATION_LISTENER_SETTINGS`,
    after a disclosure screen.
  - "Allow Nearby devices for Bluetooth alerts" shows when a device rule exists without the
    permission.
  - "Tip: set Backlit Clock as your always-on toy so alerts always show."
- **IMPORTANT CONTACTS:** rows show `name · ANIMATION`. Tap opens the animation picker (live
  previews + PREVIEW ON MATRIX + REMOVE). **+ ADD CONTACT** uses `ActivityResultContracts.PickContact`
  (no `READ_CONTACTS`). Only the display name is read from the returned URI, then the animation
  picker opens.
- **BLUETOOTH DEVICES:** the same layout. **+ ADD DEVICE** lists bonded devices (name + type) after
  `BLUETOOTH_CONNECT` is granted.
- **ANIMATIONS:** a grid of the 6 built-ins and imports with live previews (at the device's size).
  **IMPORT FROM GLYPH MUSEUM** uses `OpenDocument(application/json)`, and imports can be deleted
  (rules using a deleted animation fall back to the default). An intent filter (`ACTION_SEND`,
  `application/json`) lets Glyph Museum share files straight to Backlit.
- **PREVIEW ON MATRIX** plays the animation through `AlertBus` for 3 s, like a device alert.
- **Notification disclosure text (exact):** "Backlit only looks at incoming-call notifications from
  your Phone app, to check the caller's name against your important contacts. Nothing else is
  read, stored or sent."

## 8. Permissions, privacy, Play

- New: `BIND_NOTIFICATION_LISTENER_SERVICE` (service), `BLUETOOTH_CONNECT`.
  `READ_CONTACTS`, call log and phone state are **not** needed.
- Privacy policy, About text and the Data safety line gain a paragraph: the caller name is compared
  on the device against the user's chosen contacts, and nothing is stored or shared. The Bluetooth
  device name and address are stored only for devices the user picked.
- Still no network.

## 9. Error handling

| Situation | Behavior |
|---|---|
| Notification access off | No call alerts. Hint in the ALERTS tab. |
| `BLUETOOTH_CONNECT` denied | No device alerts. Hint in the tab. Device picker unavailable. |
| Bad import file | Rejected with the single error message. Nothing saved. |
| Import made for the other phone | Resampled, with a note. |
| Another app's toy active | The alert doesn't show (platform limit). No retries. |
| SDK connect fails | Part 1 retry policy, then give up quietly. |
| Call ends mid-animation | `closeAppMatrix` within 0.5 s, or the toy resumes. |
| Flaky BT reconnects | 30 s cooldown per device. |
| Animation id missing (deleted import) | Default animation (heart for contacts, link for devices). |

## 10. Testing

- **Unit (JVM):**
  - The built-ins at 25 and 13:
    - heart bitmaps are exact and symmetric
    - the smiley outline is a pure distance band and symmetric
    - the 13×13 Ringing pixels match §3.1
    - Link dots are mirror-symmetric and meet at the centre at 50%
    - Burst is symmetric
    - Bounce stays inside the mask
    - every frame is non-empty
  - `MuseumFormat`:
    - a v1 file and a v4 file parse
    - a missing `d` defaults to 100
    - clamping works
    - wrong counts, bad `v`, empty frames and non-JSON input are rejected
    - round-trip back to JSON
    - 25↔13 resampling keeps a centred dot centred
  - Contact name normalisation.
  - `AlertCoordinator`: match/no-match, the call loops until ended, the 60 s cap, the device alert
    ends at 3 s, the call beats the device, the 30 s cooldown, a call replaces a device alert.
  - Rules + index JSON round-trip, with unknown ids falling back.
- **Device checklist (Phone (3)):**
  - an important caller with the Glyph idle (screen on and off)
  - an important caller while Backlit Clock is showing, and while it's the AOD toy
  - a non-important caller does nothing
  - answer / decline / missed each stop the animation
  - a BT device connects, and the cooldown works
  - a real Glyph Museum export imports and plays
  - the PREVIEW button
  - the permissions-denied paths
