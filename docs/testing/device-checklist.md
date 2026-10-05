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
- [ ] Setup step 01 menu path matches the real Settings path on this OS version (fix the text if not).
- [ ] "OPEN GLYPH TOYS" opens Nothing's toy manager.
- [ ] 13×13 preview toggle shows the stacked day-ring digits with ring gaps top/bottom.
- [ ] Location: city, approximate (approximate-only prompt), deny path.
- [ ] About shows version and licenses.

## Music toy (Part 2)
- [x] Backlit Music appears in Glyph Toys with the bars icon.
- [x] Mirror and Peaks each move in time with a song (Spotify).
- [x] Works with the screen off (phone face down, toy shown via the Glyph Button).
- [x] Long press switches Mirror ↔ Peaks; the app's chips follow.
- [x] A 2–3 s gap between tracks does not drop to the idle line.
- [x] Pause: bars fall for ~1 s, then the breathing line.
- [x] Permission revoked (`adb shell pm revoke app.backlit android.permission.RECORD_AUDIO`): toy shows the livelier fallback line while music plays; no crash.
- [x] After leaving the toy (and after leaving the app from the Music tab), `dumpsys media.audio_flinger` shows no Backlit Visualizer client.
- [x] Steady 20 fps (200 frames per 10.0 s in the `avg frame` log); push cost ~15 ms is SDK-side.
- [x] Music tab: demo preview, disclosure + ALLOW, LIVE label with real music, sensitivity cycling.


## Alerts
- [x] Dialer's incoming-call notification is recognised (log: "incoming call notification", no name).
- [x] Important caller: animation plays as the call starts; Nothing OS ringtone Glyph takes over after ~1 s (platform limit, documented in app).
- [x] Missed call from important contact: 10 s animation, then reminders (log: "missed call notification").
- [x] Missed-call reminders fire with the screen off (checked over 3 min; deep Doze over longer idle may delay later ones).
- [ ] Missed-call reminders stop when the notification is dismissed.
- [ ] Important caller while Backlit Clock shows (carousel / AOD).
- [ ] Non-important caller: nothing.
- [x] Chosen BT device connects: animation plays.
- [ ] BT reconnect within 30 s: nothing (covered by AlertCoordinatorTest).
- [x] Real Glyph Museum export (v4, 76 frames) imports via file picker and plays on the Phone (3), upscaled.
- [ ] Share → Backlit import from Glyph Museum.
- [ ] Bad file: "This file isn't a Glyph Museum animation."
- [x] Tap-to-preview plays on the matrix with the Glyph idle.
- [ ] Notification access off / Nearby devices denied: hints shown, no crash.

## Charge toy (Phone (3))

- [x] Toy listed as "Backlit Charge"; still level shows; long press cycles Sprout → Buddy → Big number → Moon
- [x] Plug in: Nothing's own battery animation takes the matrix for about 3 s (no off switch found, Phone (3), 2026-10-04). Backlit's plug-in animation replays when the toy returns (about 10 s), ending with the %
- [x] The charging loop shows the % for 2 s every 10 s (Sprout, Buddy, Moon)
- [x] Unplug → still
- [x] Done plays once when the level crosses "Done at" (halo seen); 100 → 99 → 100 jitter doesn't replay it
- [x] AOD toy: still level only, no animation loop
- [x] Alert preview from ALERTS plays inside the Charge toy, then the toy resumes
- [x] Imported plug-in animation plays instead of the style's own
- [x] CHARGE tab previews all four moments
- Tip: `adb shell dumpsys battery unplug` / `set level N` / `set ac 1`, then `dumpsys battery reset`, fakes charging events

## Pixel Studio + Canvas toy (Phone (3))

- [x] Tools by finger: pen (continuous on fast drags), erase, line, circle, fill, text; mirror; undo/redo; shift; clear
- [x] Frames: + copies, hold ×2, speed; ▶ PLAY in editor; SHOW ON GLYPH on the matrix
- [x] Save asks for a name; drawing appears in STUDIO, ALERTS pickers and CHARGE pickers
- [x] Backlit Canvas toy shows the chosen drawing; long press cycles; AOD shows frame 1
- [x] Deleting the Canvas drawing falls back to another drawing / the pencil hint
- [x] SHARE opens the share sheet; the shared file re-imports
- [x] Import from Glyph Museum into STUDIO; EDIT IN STUDIO on an ALERTS import
- [x] Discard prompt on unsaved Back; STUDIO tab kept after leaving the editor

## Glyph Pet (Phone (3))

- [x] PET tab: live ghost, headline + mood meter; debug meter tap cycles faces
- [x] Toy shows the resting face for the mood
- [x] Long press → hearts (+mood); shake → dizzy; 3 hard shakes → angry; long press while angry → calmed
- [x] Tilt moves eyes / leans body (direction correct)
- [x] Face-up ≥2 s then face-down → peekaboo !
- [x] Charging → munch; AOD while charging → fills-up still
- [x] Sleep hours → asleep with z's; long press → yawn, back to sleep after ~1 min
- [x] AOD idle still (eyes change per minute) and AOD sleep still
- [x] SHOW ON GLYPH plays a happy hop
- [x] Mood persists after toggling the toy off/on

- [x] Glyph Toys picker shows Nothing-style dot-matrix images for all five Backlit toys
