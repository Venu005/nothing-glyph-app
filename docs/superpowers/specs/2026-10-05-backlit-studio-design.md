# Backlit — Pixel Studio + Canvas Toy — Design

**Date:** 2026-10-05
**Status:** Draft for review
**Builds on:** Clocks, Music, Alerts and Charge, all merged. It reuses the Alerts animation library and the Glyph Museum format.
**Roadmap:** after this comes the next-event countdown (calendar). OTP and AI reactions are parked.

## 1. Goal

An in-app **pixel studio** for drawing still images and frame-by-frame animations for the Glyph Matrix,
plus a **Backlit Canvas** Glyph toy that shows one of your drawings. Drawings are first-class animations:
they appear in every Backlit animation picker (important contacts, Bluetooth devices, Charge plug-in/done),
they can be shared as Glyph Museum JSON, and Glyph Museum files can be imported as editable drawings.

### Success criteria
- Drawing with a finger on the Phone (3) feels direct: pen, erase, line, circle, fill, text, mirror,
  undo/redo, shift and clear all work on the canvas.
- An animation of up to 24 frames, with speed and per-frame hold, plays the same in the editor, on "Show on Glyph",
  in alerts, in Charge and in the Canvas toy.
- A saved drawing appears at once in the ALERTS and CHARGE pickers.
- Backlit Canvas shows the chosen drawing, long press cycles through drawings, and AOD shows the first frame.
- A real Glyph Museum JSON imports into STUDIO as an editable drawing.
- Share as JSON produces a file that Backlit's own importer, and Glyph Museum's format, accepts.
- No new permissions and no network.

### Out of scope
- Editing at both sizes by hand. You draw at your phone's size and the other size is auto-scaled.
- A per-pixel brightness slider or more than 3 shades.
- Copy/paste between drawings, layers, auto-rotating playlists in the toy, and cloud or gallery browsing.

## 2. Visual source of truth

`docs/superpowers/mockups/2026-10-05-studio-layout.html`, layout **A · Stacked** (approved). Top to bottom it has:
- a header (← BACK · name · ▶ PLAY)
- the round canvas
- the tool row PEN | ERASE | LINE | ○ | FILL | TEXT
- the shade row BRIGHT | MED | DIM | MIRROR
- the edit row ↶ | ↷ | ← | ↑ | ↓ | → | CLEAR
- FRAMES · n / 24 with a thumbnail strip and +
- SPEED · n FPS · HOLD FRAME ×1 ×2 ×3 ×4
- SAVE | SHOW ON GLYPH

The styling is the existing Backlit raw dot-matrix look: SquareChip, Doto titles, BacklitColors, and MatrixPreview-style LEDs.

## 3. Screens and flow

### 3.1 STUDIO tab
- **Tabs:** CLOCK | MUSIC | ALERTS | CHARGE | STUDIO.
- A setup hint shows until the Canvas toy has bound once: "Turn on Backlit Canvas in Glyph Toys
  (Settings → Glyph Interface → Glyph Toys)."
- **MY DRAWINGS:** a 3-column grid of live mini previews with names. The drawing the Canvas toy shows is marked "● ON CANVAS".
- Tapping a drawing expands an action row: **EDIT · USE ON CANVAS · SHARE · RENAME · DELETE**. Delete asks to confirm.
- Below the grid are **+ NEW DRAWING** and **IMPORT FROM GLYPH MUSEUM**.
- If there are no drawings yet, a short line says what the studio is for.

### 3.2 Editor (full screen, `Screen.EDITOR`)
- It opens with a new blank drawing or an existing drawing's id.
- **Back** with unsaved changes shows a dialog: "Discard changes?" with DISCARD and KEEP EDITING.
- **SAVE:** a new drawing first asks for a name (an inline text field). The editor stays open after saving.
- **▶ PLAY / ■ STOP** loops the animation in place. Editing is disabled while it plays.
- **SHOW ON GLYPH** saves nothing. It previews the current, possibly unsaved, frames on the matrix through the Alerts
  preview path for max(one loop, 3000 ms) and at most 10 s. Because unsaved frames have no library id,
  `AlertsRuntime.previewAnimation(anim: GlyphAnimation, durationMs)` holds the animation under the reserved id
  `preview:studio` (resolved first in `animationFor`) and dispatches a preview for that id.

### 3.3 Other places
- ALERTS animation pickers, the ALERTS ANIMATIONS library and the CHARGE custom-animation pickers list drawings with
  the imports. Labels say "DRAWING" or "IMPORT" under the name.
- Each import in the ALERTS ANIMATIONS library gets **EDIT IN STUDIO**, which creates an editable copy as a new drawing,
  then opens the editor. The original import is unchanged.
- **Deleting** a drawing anywhere removes it from the library. Rules that used it fall back to the default as they do
  for deleted imports today (Alerts uses the built-in default, Charge uses the style's own animation, and Canvas uses
  the first drawing).

## 4. Editor behaviour

- **Canvas size** is `DeviceProfile.size` (25 on Phone (3) and on unsupported phones, 13 on (4a) Pro).
  Only pixels that exist on the round panel (`PixelGrid.hasLed`) can be painted. All tools ignore the others.
- **Shades:** index 0 = off, 1 = DIM (70), 2 = MED (150), 3 = BRIGHT (255). The brush is one of 1..3 and starts at BRIGHT.
- **Tools** (one active at a time):
  - **PEN:** tap or drag. Each touched cell gets the brush shade. A fast drag fills the gaps between successive
    touch points with a line, so strokes are continuous.
  - **ERASE:** like PEN, but sets 0.
  - **LINE:** drag from start to end. A live preview shows while dragging and commits on release. It uses the same
    line rasteriser as PEN gap-filling (sample every 0.5 px, round half up).
  - **CIRCLE (○):** the drag start is the centre and the distance sets the integer radius (rounded). It draws a
    midpoint circle outline (gap-free and symmetric), with a live preview, and commits on release.
  - **FILL:** a 4-way flood fill from the touched cell. It replaces the connected region of the touched cell's shade
    with the brush shade, within the panel only. It does nothing if the shades are equal.
  - **TEXT:** a dialog takes up to 6 characters from `0-9 A-Z ! ? . - : + ♥`, uppercased. It renders in the 3×5 font
    (`PixelFontText`, 1 px gap, ♥ = 5 px wide), centred on the canvas, in the brush shade. It commits as one undo step.
    You position it with the shift arrows afterwards.
- **MIRROR:** a toggle. When on, a red dashed centre line shows and every pixel a tool writes also writes its mirror
  `(size-1-x, y)`. Fill fills the mirrored seed too.
- **Edit row:**
  - **↶ / ↷:** undo and redo, 50 snapshots. A snapshot is the whole document (all frames, holds and fps), so frame
    operations undo too.
  - **← ↑ ↓ →:** shift the current frame by 1 px. Pixels pushed out of the grid are lost and new cells are 0. After
    shifting, cells that land outside the panel mask are cleared.
  - **CLEAR:** clears the current frame.
- **Frames:**
  - The strip shows thumbnails at their own size, with the hold shown as "×2". Tap a thumbnail to select it.
  - **+** inserts a copy of the current frame after it and selects the copy.
  - A long press on a thumbnail opens a small menu: DELETE · MOVE LEFT · MOVE RIGHT. Delete is disabled with 1 frame,
    and + is disabled at 24 frames.
- **Onion skin:** when the current frame isn't the first, the previous frame's lit cells show at about 12 % in the
  editor view only, never saved or sent.
- **Timing:** SPEED runs from 2 to 20 fps (default 8). HOLD for the selected frame is ×1–×4 (default ×1). A frame
  lasts `hold × round(1000 / fps)` ms.

## 5. Storage (Approach A: drawings are Glyph Museum entries in the existing library)

- `AnimIndexEntry` gains `kind: String = "import"` (`"import"` or `"drawing"`) and `fps: Int = 0`. The defaults keep
  existing stored JSON valid.
- A drawing is stored exactly like an import:
  - It uses an `import:<uuid>` id and its file is the normalised Glyph Museum JSON written by `MuseumFormat.toJson`.
  - The index entry has `kind="drawing"`, `fps`, `name` and `sourceV` (1 for a 25×25 drawing, 4 for 13×13).
  - So `animationFor`, `importedAnimation`, previews, pickers and deletion work unchanged.
- **Encode (Drawing → ImportedAnimation):** the shade index maps to 0/70/150/255 in a `PixelGrid` of the drawing's size.
  The other size is made with `MuseumFormat.resample`. Duration = `hold × round(1000 / fps)`.
- **Decode (ImportedAnimation + entry → Drawing):**
  - Take the frames at the entry's source size (25 if `sourceV == 1`, else 13).
  - Map each LED to the nearest shade: 0 when v < 35, 1 when v < 110, 2 when v < 203, else 3. These are the midpoints
    between 0, 70, 150 and 255.
  - fps = the entry's `fps` if > 0. Otherwise it's derived (see §6).
  - hold = clamp(round(duration / round(1000 / fps)), 1, 4).
- **Drawing size vs device size:** a drawing whose size differs from the device (for example a shared 13×13 drawing
  opened on Phone (3)) is resampled to the device size when opened in the editor, and saved at the device size.

### `AlertsRuntime` additions
- `drawings: Flow<List<AnimIndexEntry>>` filters `config.imports` to `kind == "drawing"`.
- `saveDrawing(d: Drawing, id: String?): String` writes the file and adds or updates the index entry. It returns the id
  and is main-safe (file IO on `Dispatchers.IO`).
- `loadDrawing(id: String): Drawing?`
- `importAsDrawing(json: String, name: String, deviceSize: Int): DrawingImport` returns a sealed result:
  `Ok(id, truncated: Boolean, simplified: Boolean)` or `Invalid`.
- `copyImportToDrawing(importId: String, deviceSize: Int): String?`
- `previewAnimation(anim: GlyphAnimation, durationMs: Long)`: see §3.2.

## 6. Glyph Museum import into STUDIO

- STUDIO's **IMPORT FROM GLYPH MUSEUM** uses the same document picker and size limit (4 MB) as ALERTS
  (`importFromUri` refactored so the read part is shared).
- The JSON is parsed with `MuseumFormat.parse` and resampled to the device size, then:
  - Frames beyond 24 are dropped (`truncated = true`).
  - Each LED is rounded to the nearest shade. If any LED wasn't exactly 0/70/150/255, `simplified = true`.
  - fps = clamp(round(1000 / min(duration)), 2, 20), and holds are derived as in §5.
  - The new drawing is named from the file name.
- The message shown is "Imported "<name>" as a drawing", plus " · first 24 frames kept" and/or " · brightness
  simplified to 3 shades".
- **EDIT IN STUDIO** on an ALERTS import does the same from the stored import, creating a new drawing named
  "<name> (edit)".

## 7. Backlit Canvas toy (glyph/CanvasToyService)

- The structure is a copy of `ChargeToyService` / `ClockToyService`:
  - `DeviceProfile` and `ModeTracker` (including `msUntilActive` re-kick)
  - `GlyphOutput`, the settings flow, and the alert bus with `ToyPresence`
- **What it shows:** the drawing with id `settings.canvasDrawingId`. If that's empty or missing, it shows the first
  drawing (by index order). With no drawings, it shows the hint pattern: a small dotted pencil drawn in code at both sizes.
- **ACTIVE:**
  - A multi-frame drawing loops via `FramePacer(50)`.
  - A single-frame drawing is pushed once.
  - The loop stops for single frames, as in the Charge toy's still handling.
- **AOD:** the first frame, redrawn on each EVENT_AOD.
- **Long press (EVENT_CHANGE):** sets `canvasDrawingId` to the next drawing's id, wrapping around. With none, it does nothing.
- **Alerts:** while `bus.value != null`, render the alert (same as the other toys).
- **Manifest:** a `<service>` with `toy.name` "Backlit Canvas", a summary, `toy.image` `ic_canvas_preview`,
  `longpress=1` and `aod_support=1`.
- **Settings** (Settings/SettingsRepo): `canvasDrawingId: String = ""` and `canvasToyEverBound: Boolean = false`.

## 8. Sharing

- **SHARE** writes `cacheDir/share/<safe-name>.json` (normalised Glyph Museum JSON) and fires `ACTION_SEND` with
  type `application/json` through a `FileProvider` (`${applicationId}.share`, path `cache/share/`), with
  `FLAG_GRANT_READ_URI_PERMISSION`.
- Backlit's existing *Share → Backlit* import still works on these files.

## 9. Architecture summary (pure vs Android)

The pure units (JVM unit-tested, no android imports) live in `studio/`:

| Unit | Responsibility |
|---|---|
| `Drawing` | name, size, fps, frames: List<Frame>; Frame = shades: ByteArray(size×size) of 0..3, hold 1..4 |
| `EditorState` | immutable document + current frame + history; returns a new state for each operation (pen, erase, line, circle, fill, text, shift, clear, add/delete/move frame, hold, fps, undo, redo); mirror is a parameter |
| `Raster` | line samples, midpoint circle points, flood fill, mask check |
| `PixelFontText` | 3×5 glyphs for `0-9 A-Z ! ? . - : + ♥`; text width/render into a shade grid |
| `DrawingCodec` | Drawing ↔ ImportedAnimation (encode/decode, nearest-shade, fps/hold derivation, truncation) |

Android/UI: `AlertsRuntime` additions, `ImportHelper` refactor, `CanvasToyService`, `ui/StudioScreen.kt`,
`ui/EditorScreen.kt`, `MainActivity` (EDITOR route + editing id), `HomeScreen` (5th tab), manifest (toy + FileProvider),
`res/xml/share_paths.xml`, strings, and `ic_canvas_preview`.

## 10. Edge cases

- An empty drawing (no lit cells) can be saved. It shows blank wherever it's used.
- A name is trimmed and capped at 24 characters. A blank name becomes "Drawing N", where N is one more than the number
  of drawings.
- Deleting the Canvas drawing makes the toy fall back to the first drawing, or to the hint.
- A drawing that's saved while it's used by a rule takes effect the next time it plays. `AnimationLibrary`'s cache entry
  for that id must be replaced when the drawing is saved.
- Process death while editing loses unsaved changes. That's accepted, and Back still warns within a session.
- A corrupt drawing file makes `loadDrawing` return null. The editor shows "Couldn't open this drawing" and returns to STUDIO.

## 11. Testing

- **EditorState and Raster:**
  - pen writes the brush shade and ignores off-panel cells
  - mirror writes `(size-1-x, y)`
  - line endpoints are lit and the line is 8-connected
  - a circle of r=8 at 25 is gap-free with its 4 cardinal points
  - fill is bounded by other shades and never leaks outside the mask
  - shift drops edge pixels and clears off-mask cells
  - clear
  - add/delete/move frame, with the 1 and 24 limits
  - hold 1..4 clamped, fps 2..20 clamped
  - undo/redo restores exact documents, history is capped at 50, and a new edit clears redo
- **PixelFontText:** glyph widths, "HI" renders 7 px wide, and unknown characters are skipped.
- **DrawingCodec:**
  - encode→decode round trip is identical (shades, holds, fps)
  - nearest-shade thresholds (34→0, 35→1, 109→1, 110→2, 202→2, 203→3)
  - 30 frames are truncated to 24 with the flag
  - fps derivation from durations [100, 300] gives 10 fps with holds [1, 3]
  - `simplified` is set only when an LED isn't on a shade value
- **Settings:** defaults for the new fields.
- **AnimIndexEntry:** old JSON without `kind`/`fps` decodes with the defaults.
- **Device checklist (Phone (3)):**
  - draw with each tool by finger, and check mirror
  - an animation plays in the editor and on Show on Glyph
  - save → the drawing appears in the ALERTS contact picker and in CHARGE
  - the Canvas toy shows it, long press cycles, and AOD shows frame 1
  - share opens the share sheet, and the shared file re-imports
  - a real Glyph Museum file imports as a drawing, and EDIT IN STUDIO works on an ALERTS import
