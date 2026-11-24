# halo 13.0 · Batch 4: the making tools (Canvas, Studio, Notes, Translate, Scan)

Checked against repo `slafftrosheen/Halo` at **99aac2f** (23 Nov 2026). This is a design and developer spec, not a build, and nothing has been tested on a device.

The new logic files compile against the repo's own ink classes (`InkDocument`, `InkLimits`) in a standalone Kotlin JVM project, and **6 of 6 tests pass**. They have not been compiled inside the Android app, and no screens are built.

Boards: 155 Canvas · 156 drawing without walls · 157 Studio · 158 Notes · 159 Translate + Scan · 160 icons, motion and files.
This batch builds on batch 1 (depth levels, plain words, meaning colours). Red still means only "a source failed".

---

## 1. What the repo has

- **Canvas** (`ui/screens/notebook/Canvas*`, `data/notebook`): `CanvasNodeType` has 12 values (TEXT, NOTE, IMAGE, FILE, SKETCH, INK_SELECTION, GROUP, TAG, STICKY, SHAPE, FRAME, COMMENT), plus edges. `CanvasFramePreset` offers FREE, A4, 16:9 and Phone. `CanvasOrganizationProposal` already proposes groups, and `CanvasUndoSnapshot` gives one undo step.
- **Ink limits**:
  - `CanvasSketchCodec` (sketch:v1, legacy) allows **64 strokes** of 256 points.
  - The SDK's `InkLimits` (v2–v4) allows **256 strokes**, 2,048 points per stroke and **16,384 points** per sketch.
  - When a sketch is full, `InkEditor.begin()` sets `limitReached`, and `CanvasInkSurface` shows `R.string.ink_capacity` in `colorScheme.error`: "Drawing limit reached. Lift the pen; undo or erase strokes to make room."
- **Studio** (`ui/workspace/Studio*`, `data/studio`):
  - Presets: BLANK, SQUARE, POSTCARD and PORTRAIT. Up to 32 layers.
  - Shape joins: WELD, TRIM and INTERSECT. Fills: solid, linear and radial. Symmetry: vertical, horizontal and radial.
  - Export as SVG, DXF, PDF or .halo.
  - Smudge stops every **12 strokes** with "Apply these strokes before adding more."
- **Notes**: `NoteBlockType` has 11 values, plus fonts, page settings, comments, tracked changes (`NoteChange`) and read-back. **Notes has no drawing block.**
- **Translate** (`HaloTranslateScreen`): type, talk, camera, phrasebook and offline packs, with a conversation flag.
- **Scan** (`HaloScanScreen`): about 12 instruments, including edges, filter, rotate and reorder, sign, fill a form, magnifier, read aloud, translate and save PDF. It already has an Easy mode.
- **Languages:** Translate and Scan share `scanLanguages`, 8 in all: en, lv, pl, ru, de, es, fr and uk.

13.0 adds **no new storage**.

## 2. Canvas (board 155) · `ui/canvas/v13/CanvasPlain.kt`

- **Four plain starts:** Blank, Mind map, Moodboard and Compare.
- **One bottom bar with five verbs:**

| Verb | Makes |
|---|---|
| Write | TEXT · NOTE · STICKY |
| Draw | SKETCH · INK_SELECTION |
| Picture | IMAGE · FILE |
| Shape | SHAPE (FRAME under More, Pro) |
| Connect | edges |

  Group, Tag and Comment live in the long-press menu.
- **Tidy up** is offered only with 6 or more loose cards, at most once per board per day, and never at Simple. It shows the proposed groups first. Nothing moves until you tap "Tidy it", and one undo puts everything back.
- **Find on this board:** a search plus a small map, with "Go there", "Fit everything" and "Next frame".
- **Show it:** frames become swipeable pages, which you can save as PDF or send to another phone.
- **By depth:**
  - **Simple:** Write, Draw and Picture.
  - **Standard:** all five verbs, plus Tidy up and Find.
  - **Pro:** also frames, review pins, image tools, PDF pages and the minimap.
  - **Kids:** always Simple.

## 3. Drawing without walls (board 156) · `ui/canvas/v13/InkSheets.kt`

**The 64-line wall comes down, and so does the 256-line one.** The safe limits stay *per sheet*, so saving, sync, OCR and remote ink keep their budgets. What goes is the wall in the interface.

- **Fullness:** each sheet measures how full it is, by whichever limit is closer (strokes or points).
- **At 90% full**, the next sheet is prepared quietly in the background.
- **When a stroke no longer fits**, it lands on the new sheet. There is no message and no red.
- **How sheets are stored:**
  - Each sheet is an ordinary **SKETCH node**.
  - All sheets of one drawing sit in one **GROUP**, joined by edges labelled `continues`.
- **Size:** up to **32 sheets** per drawing. From 24 sheets, a calm apricot card suggests starting a new drawing. At 32, it asks for one.
- **Editing across sheets:** undo crosses sheets, and Erase works on all of them.
- **Sheet tools:** "See all as one", "Hide a sheet" and "Flatten to picture".
- **Old 64-line sketches** (sketch:v1) open as v4 documents the first time they are edited. The original text is kept until the save succeeds.
- **`R.string.ink_capacity` is never shown in 13.0.** It also broke the colour rule, because it used the error colour.

**Drawings in Notes** (`ui/notes/v13/NoteDrawing.kt`):
- A drawing is a `REFERENCE` block with `refObjectId = "canvas-sketch:<canvasId>/<nodeId>"`. It is the same drawing as on Canvas, so edits show in both.
- Notes that don't belong to a board use a hidden "Note drawings" board.

**Handwriting into text** wraps `CanvasSketchOcr`:
- It runs on the phone.
- Words it isn't sure of are marked in apricot.
- "Add as text" puts the text into the note. Nothing is replaced automatically.

## 4. Studio (board 157) · `ui/studio/v13/StudioPlain.kt`

- **"What are you making?"** offers five starts: A square picture, A postcard, A tall picture, Blank, and Start from a photo.
- **Five tool groups:** Draw, Shapes, Text, Photo and Colour. One brush bar holds Brush, Eraser, Smudge, Pick colour and Layers.
- **Plain names:**

| Repo | Shown |
|---|---|
| Layers | Things on this picture |
| WELD · TRIM · INTERSECT | Join · Cut out · Keep the overlap |
| SOLID · LINEAR · RADIAL fill | One colour · Fade across · Fade from the middle |
| VERTICAL · HORIZONTAL · RADIAL symmetry | Mirror left–right · Mirror top–bottom · Kaleidoscope |
| SVG · DXF · PDF · HALO | Shapes file · Cutter file (Pro) · PDF · halo project |

- **Smudge without stopping:** `SmudgeQueue` applies every 12 strokes in the background, because the engine still needs batches of 1–12. Undo still takes back one stroke at a time.
- **By depth:**
  - **Simple:** Draw, Text, Photo and Colour.
  - **Standard:** adds shapes, the layers list, smudge and symmetry.
  - **Pro:** adds path points, pixel selection, combining shapes, DXF and colour numbers.
- **Kids:** big brushes, no file export, and pictures saved automatically to their gallery.

## 5. Notes (board 158) · `ui/notes/v13/NoteDrawing.kt`

- **Five buttons:** Title, List, Tick list, Drawing and More.
- **Plain names for the 11 block types:**
  - Title · Heading · Small heading
  - List · Numbered list · Tick list
  - Quote · Line · Code (Pro)
  - Link to something
- **Shapes to start from:** Blank, Shopping list, Meeting, Recipe, Diary and Class notes. You can also start by saying "Note: …".
- **Tick line to Planner:** the task is shown first, and it stays linked both ways. Tick it in either place.
- **Suggesting:** tracked changes (`NoteChange`) in plain words, with "Accept all" or "Go one by one". Pro only.
- **Read it to me:** follows along on screen, with Slower and the brand voice.

## 6. Translate + Scan (board 159) · `ui/translate/v13/FaceToFace.kt`, `ui/scan/v13/ScanNext.kt`

**Translate**
- "Hold to talk" comes first. Type, Camera and Face to face are one tap away. The phrasebook becomes "Saved phrases" under More.
- **Face to face:** the phone lies flat between two people, and the far half is turned upside down. Each half shows its own language and has its own "Hold to talk", and only one side listens at a time.
- **Offline:** "No internet needed" appears on a language once its pack is on the phone.
- **Kids:** talk and camera only, with no online look-up.

**Scan**
- Just point the phone. The edges are found and the photo is taken when it's sharp. Many pages and the magnifier are one tap away.
- After the photo, halo offers **at most 5 actions that fit the page:**

| Page | Offers |
|---|---|
| Text | Read it to me · Translate · Save as a note · PDF · Copy |
| Form (2+ labels ending in a colon, the existing form rule) | Fill · Sign · PDF · Read |
| Receipt | Save to Inbox · Add the date to Planner · PDF |
| Object | "What is this?" (in Kids, only with a grown-up-approved source) |

- Edges, filter and rotate move under "Fix the page".
- **Forms:** halo fills only what you type or say. It never guesses a date or a number.

## 7. Batch 3 fix found while testing

`ui/planner/v13/NaturalAdd.kt` used `\b` in its regexes. In Java and Kotlin, `\b` only recognises Latin letters, so Russian "в 10", "с мамой" and "завтра" never matched, and the batch 3 test `russianTomorrow` failed.
- **Fix:** add the `(?U)` / `(?iU)` flags.
- **New test:** `russianTitleAndFamily`.
- **Result:** the planner tests now pass 6 of 6. The fixed file and test are in this pack.

## 8. QA checklist

- [ ] Draw 300+ strokes in one sketch: no message, a sheet chip appears, undo crosses back to sheet 1.
- [ ] Open an old sketch:v1 board, edit it, kill the app mid-save: the original is intact.
- [ ] Sync a 3-sheet drawing to a second device: the order is kept and the group stays together.
- [ ] Smudge 40 strokes without a stop; undo 13 times.
- [ ] Tidy up: preview, Tidy it, undo, and the board is identical.
- [ ] Face to face with TalkBack: each half announces its own language.
- [ ] Scan a receipt, a form and a page of text: the offers match the table above.
- [ ] Kids mode: no frames, no file export, no online look-up.
- [ ] No new string uses the error colour.
- [ ] A Russian native-speaker review of strings_making_13.xml.

## 9. Files

| Path | What |
|---|---|
| ui/canvas/v13/InkSheets.kt | Fullness, prepare, place, v1 upgrade |
| ui/canvas/v13/CanvasPlain.kt | Starts, verbs, depth gates, Tidy up rule |
| ui/notes/v13/NoteDrawing.kt | Drawing refs, plain block names, bar |
| ui/studio/v13/StudioPlain.kt | Starts, plain names, SmudgeQueue |
| ui/translate/v13/FaceToFace.kt | Two sides, turns, languages |
| ui/scan/v13/ScanNext.kt | Page kind and offers |
| test/…/canvas/v13/MakingToolsV13Test.kt | 6 tests, passing |
| ui/planner/v13/NaturalAdd.kt + PlannerV13Test.kt | Batch 3 fix, 6 tests passing |
| assets/creative/creative-13.json | All rules above as data |
| res/values(-ru)/strings_making_13.xml | 79 strings, EN + RU |
| assets/halo/artwork/ic, res/drawable | 26 icons |
| res/raw/halo13_motion_* | 5 Lottie animations × dark/light |
| docs/design/halo-13/source | Board, icon and motion sources |
