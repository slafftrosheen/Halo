# #28: [Roadmap][Halo Ink 1/3] Ink v2 — pressure-aware stylus, eraser, palm rejection and resilient Canvas sketches

Original: https://github.com/slafftrosheen/Halo/issues/28 · updated 2026-10-24T06:20:30Z. Full source is retained in source-issues.json.

No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.

### I28-P01: Legacy/offline

Prepare v1 sketches and a new blank board. Disable network and do not enable any model. Reopen, draw with finger and mouse, save/reopen, erase the final stroke, undo/redo, and Cancel an edit.

**Pass evidence:** Original v1 geometry survives untouched saves; edited geometry and empty existing sketches persist correctly; Cancel preserves the saved object. No provider, Bluetooth or proprietary-driver dependency.

Status: NOT_TESTED. Evidence: pending.

### I28-P02: Physical pen

On an actual pressure pen/tablet, draw light/heavy strokes, vary supported tilt, hover, use eraser end and barrel button, place a palm, then pan/zoom/select with touch. Repeat after pen detach/reattach.

**Pass evidence:** Pressure and eraser have their documented meaning; unsupported axes fall back; no palm stroke, connected-pointer jump, accidental erase or blocked accessibility gesture. Record exact hardware/OS/driver.

Status: NOT_TESTED. Evidence: pending.

### I28-P03: Recovery/capacity

Create completed and incomplete contacts. Rotate, background/foreground, terminate/restart the app, and restore a completed draft. Attempt a large drawing up to each quota; import corrupt/future input in the isolated fixture.

**Pass evidence:** Completed local recovery survives; incomplete contact is discarded; exact draft receipt prevents stale resurrection; bounded history/payload/points and truthful limit notice; no silent loss or crash.

Status: NOT_TESTED. Evidence: pending.

### I28-P04: Tablet/accessibility

Repeat phone/tablet portrait/landscape, 100%/200% font, TalkBack, keyboard undo/redo and reduced motion in EN/LV/RU/PL. Run a 30-minute drawing session and record frame/memory trends.

**Pass evidence:** Workspace and controls stay usable, focus/order/labels work, geometry and transforms stay stable, memory remains bounded. Synthetic MotionEvents are separate evidence from the physical pen.

Status: NOT_TESTED. Evidence: pending.

## Requirement-by-requirement review

- [ ] **I28-R01** (original line 9, Historical baseline before the 2026-10-21 source implementation): - `CanvasSketchEditor.kt` recorded drag positions using Compose `detectDragGestures`, with constant rendered stroke width.
- [ ] **I28-R02** (original line 10, Historical baseline before the 2026-10-21 source implementation): - `CanvasSketchCodec.kt` persisted `sketch:v1|` with normalized integer X/Y only (64 strokes, 256 points/stroke); existing notes and sketches must keep working.
- [ ] **I28-R03** (original line 11, Historical baseline before the 2026-10-21 source implementation): - `CanvasDrawingDraft.kt` and file markups provided a starting place for safe draft/recovery integration.
- [ ] **I28-R04** (original line 12, Historical baseline before the 2026-10-21 source implementation): - Tablet model and input-hardware compatibility remain **unverified**.
- [ ] **I28-R05** (original line 15, Scope): - [ ] Introduce a portable, bounded **InkStroke / InkPoint v2** domain model (normalized position, timestamp, pressure; optional tilt, orientation, tool type/eraser, buttons). Keep Android classes at the host boundary.
- [ ] **I28-R06** (original line 16, Scope): - [ ] Accept Android stylus `MotionEvent` data, including historical samples, pressure/tilt, hover and explicit eraser tool/buttons where available, while keeping smooth finger/mouse fallback.
- [ ] **I28-R07** (original line 17, Scope): - [ ] Perform palm rejection and tool arbitration without globally disabling touch or accessibility; distinguish drawing vs panning/zooming/selection.
- [ ] **I28-R08** (original line 18, Scope): - [ ] Render pressure-aware ink with appropriate smoothing, variable widths and stable transforms across tablet/phone density, orientation and zoom.
- [ ] **I28-R09** (original line 19, Scope): - [ ] Support erase (stroke and/or segment with well-defined semantics), undo/redo, predictable save/cancel, drafts/recovery after interruption.
- [ ] **I28-R10** (original line 20, Scope): - [ ] Increase reasonable bounded capacity without excessive allocations or serialized payloads; enforce quotas and safe handling of corrupted input.
- [ ] **I28-R11** (original line 21, Scope): - [ ] Define a versioned `Ink v2` codec and **v1 read compatibility**; no destructive migration and no loss of original sketch content.
- [ ] **I28-R12** (original line 22, Scope): - [ ] Adapt Canvas/PDF annotation surfaces for tablets: enlarged workspace, orientation, accessibility, keyboard/pen controls. Add EN/LV/RU text where UI changes require it.
- [ ] **I28-R13** (original line 23, Scope): - [ ] Keep the input abstraction vendor-neutral; Huion compatibility is an acceptance scenario, not a dependency.
- [ ] **I28-R14** (original line 26, Acceptance criteria): - Finger sketches behave at least as they do today on phone-sized devices.
- [ ] **I28-R15** (original line 27, Acceptance criteria): - Existing `sketch:v1` entries render/edit/save without loss. New v2 data round-trips with pressure/timestamps/eraser semantics intact.
- [ ] **I28-R16** (original line 28, Acceptance criteria): - Stylus pressure and eraser work on an actual compatible pen device; unsupported attributes degrade correctly.
- [ ] **I28-R17** (original line 29, Acceptance criteria): - Long sessions, rotation, process death/draft restoration, stylus disconnect/reconnect, palm contact and large drawings have bounded, tested behavior.
- [ ] **I28-R18** (original line 30, Acceptance criteria): - Unit tests cover codec/versioning, geometry, normalization, quotas, undo/redo, and corruption; Android instrumentation tests cover pointer type/pressure/eraser and pen/finger fallback.
- [ ] **I28-R19** (original line 31, Acceptance criteria): - No model/provider, Bluetooth, or proprietary-driver requirement to draw and save.
