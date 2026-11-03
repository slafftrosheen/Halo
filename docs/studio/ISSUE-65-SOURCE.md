Source: https://github.com/slafftrosheen/Halo/issues/65
Updated at source: 2026-11-03T08:06:21Z
Status: authoritative issue snapshot; execution evidence tracked separately.

# HaloStudio — unified Design + Paint creative workbench

## Product direction

HaloStudio should become Halo's first-class creative instrument: **one coherent product combining the strengths of Canva and Krita**, rather than two disconnected editors.

The target is not a clone of either product. The goal is a compact, local-first, touch-first creative environment that lets a user:

- compose layouts and designs quickly
- draw and paint freely
- work with layers, text, shapes and images
- add textures, lighting and visual effects
- experiment without destroying the original
- use Halo/AI as a creative collaborator
- move naturally between manual design and AI-assisted work
- keep the whole experience inside Halo's Zone 5.0 interaction language

HaloStudio must feel like a finished creative application, not a bare canvas with a collection of buttons.

## Preserve the existing working foundation

The current Studio implementation already contains meaningful behavior and should be evolved rather than replaced. Preserve and consolidate the existing concepts and flows where valid:

- revisioned Studio projects
- shared project/session state
- layers
- drawing/ink
- text
- shapes
- image import
- camera capture
- transforms
- background editing
- undo/redo
- project history / versions
- save / save-as
- PNG/JPEG export
- AI draft/review boundary
- output handoff
- Canvas integration
- source/version continuity
- recovery/error ownership
- existing runtime/page-action bindings

Do not create a second incompatible Studio storage model.

## One Studio, multiple creative work modes

Do **not** split Design and Paint into separate top-level Workspace instruments.

Use one document/project model and one shared layer stack, with different work modes exposing different controls.

### 1. Design mode — Canva-inspired

Focused on fast visual composition.

Primary workflows:

- posters
- cards
- social graphics
- simple presentations/artboards
- labels
- flyers
- moodboards
- simple product visuals
- collages
- quick image compositions

Expected capabilities:

- text boxes
- rich typography controls
- shapes
- lines
- frames
- image placement
- crop
- alignment
- distribution
- snapping/guides
- grouping
- locking
- duplicate
- arrange/front/back
- opacity
- basic blending
- reusable styles
- page/artboard presets
- background fills/images
- templates/presets where real assets exist

The interaction should be fast and obvious rather than exposing desktop-DTP complexity.

### 2. Paint mode — Krita-inspired

Focused on direct drawing, painting and image manipulation.

Expected capabilities:

- freehand brush
- eraser
- brush size/opacity
- brush presets
- colour picker
- eyedropper
- fill
- selection
- transform
- crop
- layer opacity
- layer locking
- blend modes
- masks where supported
- pressure/stylus support when the Android input stack exposes it
- zoom/pan/rotate canvas gestures
- undo/redo optimized for rapid creative work

Paint mode uses the **same layers and project** as Design mode.

A user should be able to place text and images in Design mode, switch to Paint, draw over them, return to Design, reposition elements, and continue without exporting/importing between modes.

### 3. AI mode / Halo creative assistance

AI is a collaborator inside the same project, not a separate generator screen and not the default workflow.

AI actions should be scoped and reversible.

Examples:

- generate a new image layer
- propose a background
- create a texture layer
- remove or isolate an object
- create a mask
- propose colour grading
- relight a composition
- extend/crop-fill an image where the configured provider supports it
- propose layout alternatives
- create text/style suggestions
- generate decorative elements
- summarize requested edits into a previewable operation plan

AI output should preferably become:

- a new layer
- a mask
- an effect/adjustment layer
- a new revision/version
- or another explicit non-destructive proposal

Never silently overwrite the user's only copy.

Existing AI review/consent behavior should remain the base contract and be expanded rather than bypassed.

The actual available AI operations must depend truthfully on configured local/cloud capabilities. Unsupported operations must not appear as if they will work.

## Unified layer model

The layer stack is the core of Studio.

Support/plan a coherent set of layer roles such as:

- raster/image
- ink/paint
- text
- shape/vector-like primitive
- group
- mask
- adjustment/effect
- generated/AI result

Where full vector editing is not yet supported, do not pretend a rasterized primitive is a full vector object. Keep the model truthful.

Layer operations should include:

- reorder
- show/hide
- lock/unlock
- rename
- duplicate
- delete
- group/ungroup
- opacity
- blend mode where supported
- mask attachment
- effects
- selection identity
- per-layer properties

## Non-destructive effects and creative styling

Studio should support playful experimentation without flattening/destructively baking every edit.

Prefer an effect/adjustment stack or effect layers where architecture permits.

Target effects include:

### Colour / tone
- brightness
- contrast
- exposure
- saturation
- temperature
- tint
- highlights/shadows
- grayscale
- duotone/tint
- curves/levels later if justified

### Image effects
- blur
- sharpen
- grain/noise
- vignette
- posterize
- pixelate
- emboss/relief-style effects where performant
- edge treatment

### Light / atmosphere
- glow
- drop shadow
- inner shadow
- soft light bloom
- sun/lens flare-style overlays
- light leaks
- haze
- gradient lighting
- reflection/highlight overlays

These should be implemented as real, toggleable/editable operations or composited layers, not as decorative UI promises.

### Texture system
Allow textures to be added as layers/effects:

- paper
- canvas
- concrete
- film grain
- dust
- scratches
- fabric
- noise
- subtle material patterns

Texture assets can later be extended through creative packs, but the layer/effect contract should exist independently of downloadable packs.

## Creative packs / presets

HaloStudio may later consume creative packs containing:

- brushes
- textures
- templates
- palettes
- fonts where licensing permits
- decorative elements
- effect presets
- layout presets

These are **content extensions inside HaloStudio**, not new top-level Workspace tools.

Do not couple this issue to the future downloadable tool-pack architecture. Define stable internal preset/asset contracts so such a system can be added later.

## Halo Zone 5.0 — critical UX requirement

This is a primary goal of the issue.

The current Studio experience must no longer feel like "just a canvas with tools attached."

HaloStudio should be one of the clearest demonstrations of the Halo Zone 5.0 UX language.

### Working-area rule

In normal portrait:

- upper ~75% = artboard/canvas only
- lower ~25% = Halo Zone
- no permanent desktop-style toolbar stealing the creative work area
- no duplicated global navigation around the Orb

Temporary inspectors/pickers may overlay or slide from the Zone when invoked, but the default creative surface remains clean.

### Primary Orb satellite groups

Keep the first-level tool vocabulary intentionally small and understandable:

1. **Add**
2. **Layers**
3. **Tools**
4. **AI**

Each satellite must have:

- a unique meaningful icon
- readable label
- selected/active state
- disabled reason where applicable
- Halo Zone motion/state feedback

Do not expose 15–20 anonymous satellites at once.

### Add

Contextual creation/import actions:

- Image
- Camera
- Text
- Shape
- Draw/paint layer
- Group
- Texture
- Background
- future template/asset insertion

### Layers

Open a polished layer manager showing:

- thumbnail/type icon
- name
- visibility
- lock state
- selection
- hierarchy/groups
- effects/masks indicators

Reordering should be touch-friendly.

### Tools

Changes according to selected mode and object.

Examples:

Design:
- move
- crop
- align
- arrange
- duplicate
- transform
- style
- colour
- opacity

Paint:
- brush
- eraser
- picker
- fill
- selection
- transform
- brush settings

Selected image:
- crop
- transform
- adjustments
- effects
- remove/isolate when AI/provider supports it

Selected text:
- font
- size
- weight
- alignment
- spacing
- colour
- effects

### AI

Open contextual Halo creative operations relevant to the current project/selection.

The AI panel should explain what will be changed and where the result will land (new layer, mask, version, etc.).

## Studio mode switching

Design / Paint / AI are modes within the same Studio.

Mode switching should be immediately visible but compact. It can live in the Zone identity/utility row or inside Tools if space is constrained.

Requirements:

- switching modes must not mutate the document
- current selection persists when compatible
- layer stack remains identical
- undo history/revision continuity remains valid
- mode-specific controls swap without creating separate project copies

## Visual language

Studio must look and feel like Halo, not Material defaults with a canvas in the middle.

Use:

- existing Zone 5.0 shapes/tokens
- active Halo accent/theme
- large understandable icons
- labels
- calm surfaces
- contextual hints
- proper empty states
- real previews
- subtle motion
- tactile selection feedback
- dark/light parity
- reduced-motion parity

The visual direction should feel **creative, warm, calm and inviting**, while retaining Halo's technical precision.

Avoid:

- tiny desktop-editor icons
- unlabeled icon rows
- permanently visible toolbars
- dense nested menus with no hierarchy
- fake controls for future features

## Touch / gesture model

Minimum expected gestures:

- pinch zoom
- two-finger pan where appropriate
- canvas/artboard pan
- object drag
- resize handles
- rotate where supported
- tap to select
- tap empty area to clear selection
- long press for object/layer context where useful

Stylus/pressure should be supported when exposed by Android and should degrade cleanly to touch.

Gestures must not conflict with Halo Zone navigation.

## Project/version truth

Preserve revision ownership.

Every destructive-looking operation must either:

- be undoable
- be represented as a new revision
- or require explicit confirmation

Autosave/draft behavior should be explicit and truthful.

AI proposals must never be confused with committed user edits.

## Canvas relationship

HaloCanvas and HaloStudio remain separate instruments.

- **HaloStudio** creates/edits visual media and compositions.
- **HaloCanvas** organizes ideas, references, relationships and cross-file visual thinking.

Studio may insert/export results into Canvas.
Canvas may open referenced visual objects in Studio.
Do not merge the two products into one overloaded editor.

## Performance constraints

Studio must remain usable on normal Android hardware.

Requirements:

- bounded bitmap sizes
- downsample previews
- avoid decoding full-resolution assets unnecessarily
- cache thumbnails
- background heavy processing
- cancellation
- progress states based on real work
- memory pressure handling
- recovery after process death where existing architecture permits

Effects should prefer GPU/Compose/Android image primitives when appropriate, but architecture should remain testable and deterministic.

## Implementation approach

Prefer evolution in large coherent coding batches rather than replacing the current stack.

Suggested order:

### Batch A — document/layer consolidation
- formalize layer roles
- selection
- groups
- effect metadata
- masks contract
- project persistence/version compatibility

### Batch B — Zone 5.0 Studio shell
- four primary satellites
- mode switch
- contextual action ownership
- layer manager
- object inspectors
- clean 75/25 artboard

### Batch C — Design mode
- text/shapes/images
- alignment/arrange
- crop/transform
- styling

### Batch D — Paint mode
- brush/eraser
- brush presets/settings
- picker/fill
- selection
- stylus pressure where available

### Batch E — effects/textures
- adjustment/effect stack
- light/atmosphere overlays
- texture layers
- non-destructive preview

### Batch F — AI creative layer
- contextual operations
- proposal/review
- new-layer/version outputs
- provider capability gating

### Batch G — polish
- accessibility
- large text
- landscape
- reduced motion
- process death/recovery
- performance
- screenshots/device acceptance

Do not rebuild after every tiny edit; code substantial batches first, then run targeted verification.

## Acceptance criteria

- [ ] HaloStudio remains one instrument with shared Design/Paint/AI modes.
- [ ] Existing projects/revisions remain compatible or have an explicit migration path.
- [ ] Design and Paint operate on the same layer stack.
- [ ] Upper work area remains primarily canvas/artboard under Zone 5.0.
- [ ] Halo Zone provides four clear first-level groups: Add, Layers, Tools, AI.
- [ ] Satellites have unique icons and readable labels.
- [ ] No permanent desktop-style editing toolbar pollutes the upper workspace.
- [ ] Layer manager supports selection, reorder, visibility and lock state.
- [ ] Text, image, shape and ink layers are usable through normal touch workflows.
- [ ] Non-destructive effects/adjustments are supported by a real editable model.
- [ ] Texture and lighting effects can be added/removed without flattening the whole project.
- [ ] AI output is reviewable and lands in a new layer/version/mask/effect rather than silently replacing source work.
- [ ] Unsupported AI/provider features are clearly unavailable rather than mocked.
- [ ] Undo/redo and project revision semantics remain truthful.
- [ ] PNG/JPEG export remains functional and is extended only when real support exists.
- [ ] Canvas handoff remains functional without merging Canvas and Studio responsibilities.
- [ ] Dark/light/reduced-motion/accessibility states are handled.
- [ ] Core interactions are touch-friendly and do not require desktop precision.
- [ ] Performance/memory behavior is bounded on Android.
- [ ] Final device UI looks like a complete Halo creative environment, not a bare canvas.

## Explicitly out of scope

- desktop-class Photoshop parity
- full professional vector editor parity
- full desktop Krita brush engine parity in the first pass
- pretending unsupported file formats are round-trip editable
- separate top-level "Designer" and "Painter" Workspace tools
- downloadable marketplace/tool-pack architecture itself

Those can be added incrementally after the unified Studio foundation is solid.
