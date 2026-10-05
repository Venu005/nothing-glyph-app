# Backlit — Multiple Pets — Design

**Date:** 2026-10-06
**Status:** Draft for review
**Builds on:** Glyph Pet (ghost), merged in PR #7 and #8. Spec `2026-10-05-backlit-pet-design.md`.
**Roadmap:** the tilt sand timer comes next.

## 1. Goal

The Backlit Pet toy offers **six pets**: Ghost (Boo, existing), Frog, Penguin, Axolotl, Owl and Robot.
The user chooses a pet in the PET tab. All pets share one mood, sleep hours and behaviour (`PetBrain` is
unchanged), and each pet has its own default name, art and signature move.

### Success criteria
- **PET tab** has "CHOOSE YOUR PET" with six live mini previews. Tapping one switches the app preview and the
  Glyph toy at once.
- **Every new pet** renders every base mood, reaction and AOD still at 25×25 and 13×13, matching the approved
  mockup.
- **Mood carries over** when switching pets. Names are per pet, with defaults
  Boo / Ribbit / Waddles / Lotl / Hoot / Bolt, and each can be renamed.
- **The ghost** looks pixel-identical to today.
- **Existing users** keep their ghost and Boo's name after the upgrade.
- No new permissions. Battery behaviour is unchanged.

### Out of scope
- Per-pet moods or stats.
- Unlocking pets.
- Per-pet toy picker icons (the picker keeps the ghost image).
- Pets drawn in the Studio.

## 2. Visual source of truth
- **New pets:** `docs/superpowers/mockups/2026-10-06-pets-full.html` (approved), with one row per pet covering
  Happy, Content, Bored, Sad, Asleep, Pet, Dizzy, Angry, Calmed, Munch, Peekaboo, Yawn, Signature, AOD idle,
  AOD bored, AOD asleep and AOD charging. Port its rigs, `eyes`/`mouth`/`cheeks`/`hearts`/`zz`/`steam` helpers
  and `STATES` line for line. The brightness constants are `O = 0.9`, `F = 0.22` and `L = 0.5`, with the 0..1 → 0..255
  mapping as in the earlier toys.
- **Ghost:** unchanged (`GhostArt`, from the 2026-10-05 mockups).

## 3. Behaviour
- `PetBrain`, `PetMood`, `PetInsight`, the reactions table, the timings and the AOD rules are **unchanged**.
- **Signature move:** the existing `Reaction.BOO` (2600 ms, HAPPY and ACTIVE only, at most once per 10 min)
  is each pet's signature move. The ghost does its Boo!, and the frog catches a fly with its tongue, the
  penguin belly-slides, the axolotl blows bubbles, the owl swivels its head and the robot glitches.
  Each signature is timed to fit in 2600 ms, so the penguin's slide (3000 ms in the mockup) is shortened to 2600 ms.
  The enum name stays `BOO` to avoid churn.
- **Munch:**
  - The frog catches the bolt with its tongue.
  - The robot plugs a cable in from the right with a sparking plug.
  - The other pets use the shared chomp, where a bolt flies to the mouth.
- **Tilt:** `Pose.lookX/lookY` drive the open eyes in CONTENT, as for the ghost.
- **AOD idle:** each minute the eyes look a different way, cycling (0,0) → (1,0) → (0,1) → (−1,0) by
  minute-of-hour, as for the ghost. This replaces the mockup's fixed `lx: 1`.
- **AOD charging fill:** the body interior (the cells drawn at fill brightness F) is lit at 0.42 from the
  bottom up to the battery level, and cleared above it.

## 4. Architecture

### Pure core (`pet/`)
| Unit | Responsibility |
|---|---|
| `PetKind` | `enum PetKind(id, defaultName) { GHOST("ghost","Boo"), FROG("frog","Ribbit"), PENGUIN("penguin","Waddles"), AXOLOTL("axolotl","Lotl"), OWL("owl","Hoot"), ROBOT("robot","Bolt") }` with `byId(id)` (unknown → GHOST) |
| `PetArt` | `object PetArt { fun frame(kind, size, pose, now): PixelGrid; fun still(kind, size, pose, minuteOfHour): PixelGrid }`. GHOST delegates to `GhostArt` unchanged; the others go through `RigArt` |
| `RigArt` | the shared expression system: eye kinds (open, blink, closed, half, happy, sad, swirl, angry, wide), mouth kinds (smile, wide, flat, frown, O, zig, chomp, small), cheeks, hearts, z's, steam, the "!", the bolt, AOD fill, and the mood/reaction/AOD choreography (`STATES` from the mockup) |
| `Rig` | interface: `eyes25/eyes13: EyeSpec(cx: List<Int>, cy, w, h, mode: HOLE/BRIGHT)`, `mouth25/mouth13: MouthSpec(x, y, scale)`, `cheeks25/cheeks13`, `top25/top13`, `body(g, BodyOpts)`, an optional `mouth(g, kind, opts)` override (penguin and owl beaks), `signature(g, t)`, and an optional `munch(g, t, opts)` |
| `rigs/FrogRig`, `PenguinRig`, `AxolotlRig`, `OwlRig`, `RobotRig` | each pet's body and extras, ported from the mockup |
| `BodyOpts` | `dx, dy, puff, b (edge brightness), t, gill (FAST/NORMAL/SLOW/STILL/DROOP), flap, tip (antenna brightness), footLift` |

`PetPreviewAnimation` gains the kind: `pet:<kind>:<state>`. The old `pet:<state>` ids still parse as the ghost.

### Settings
| Field | Default | Notes |
|---|---|---|
| `petKind` | `"ghost"` | unknown ids read as `"ghost"` |
| `petNames` | `{}` | a map from pet id to name, stored as a JSON string. A missing entry means the pet's default name |

- **Migration:** the existing `petName` value (default "Boo") is the ghost's name. It is read into
  `petNames["ghost"]` when that entry is missing.
- `SettingsRepo.petNameFor(settings, kind)` returns the name to show.
- `cleanPetName` and `petNameToSave` apply per kind, with that kind's default name as the blank fallback.

### Toy and UI
- `PetToyService` renders `PetArt.frame/still(PetKind.byId(settings.petKind), …)`. Nothing else in the toy changes.
- **PET tab:**
  - A "CHOOSE YOUR PET" row of six 1-cm tiles. Each is a live mini preview of the pet's current base pose, with the
    name underneath. The selected tile has a white border.
  - The headline, the hint line, the how-it-works card title, the name field and Show on Glyph all use the
    selected pet and its name.
  - Show on Glyph plays `pet:<kind>:happy`.

## 5. Testing
- **Every pet** × every Base × every Reaction × both sizes × t in 0..6000 renders without exceptions, has lit pixels,
  and stays inside the mask. AOD stills are covered for every Base and minute 0..3.
- **The ghost is unchanged:** `PetArt.frame(GHOST, …)` equals `GhostArt.frame(…)` for a sample of poses and times, and
  the existing `ToyPreviewsTest` golden stays green.
- **AOD fill grows:** for each new pet, the count of 0.42 cells rises from level 30 to 62 to 95.
- **Symmetry:** at minute 0 (look 0,0), the AOD idle at 25×25 is exactly left-right symmetric for the Penguin,
  Axolotl, Owl and Robot. The Frog is exempt, because its pupils share a gaze (cells 6–7 and 16–17) as approved.
- **PetKind:** `byId` falls back to GHOST, and the defaults match.
- **Settings:** the defaults; the migration from `petName` to `petNames["ghost"]`; per-pet name cleaning; and an
  unknown `petKind` reads as ghost.
- **PetPreviewAnimation:** `pet:frog:happy` and the legacy `pet:happy` both parse.
- **Device checklist:**
  - switch each pet in the PET tab and see the Glyph follow
  - each pet's happy, pet, dizzy, angry, munch, peekaboo and signature
  - AOD idle and charging fill for two pets
  - mood is kept when switching
  - names per pet
