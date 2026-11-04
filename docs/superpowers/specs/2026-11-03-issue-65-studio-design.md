# Issue 65 Studio design

Authority: [issue snapshot](../../studio/ISSUE-65-SOURCE.md). User requests complete implementation in coherent coding batches, retaining one build/test worker.

Evolve the existing revisioned StudioDocument, StudioSession and Room owner. Design, Paint and AI are presentation modes of the same project; switching mode changes no document/revision/history. Keep originals, exact source versions, shared typed/STT operation paths, current Kids/privacy/Power gates and provider consent.

## Approach and decisions

Use the current typed command language, extend its closed schemas and render the same portable document for previews/export. Replacing Studio with another editor or storing a separate paint bitmap would break revision/source continuity. These are implementer decisions under the user's complete-implementation instruction.

Layers retain existing image/ink/text/rectangle/ellipse roles; add real groups, lines and deterministic procedural textures. Groups use an acyclic bounded parent hierarchy and preserve global document coordinates. Group transforms affect descendants atomically; ancestor visibility/locking applies to descendants. Add text typography, image crop, blend modes and editable bounded effect stacks. Rectangular masks remain explicitly rectangular clips; do not advertise segmentation masks or full vector path editing. Presets are local data, never promised downloadable packs.

Default Studio orbit contains Add, Layers, Tools, AI with distinct existing Halo icons. Full commands remain in All actions. The compact mode selector lives in the Zone utility band; the upper work area contains only the artboard/selection/temporary work status. Modal inspectors are contextual, scrollable and large-text safe. No persistent desktop toolbar.

The artboard supports hit selection, clear selection, object drag, resize/rotation handles, pinch zoom, two-finger pan/rotation and fit/reset. Commit once at gesture end through the exact observed revision. Zoom/pan are viewport state only. Selected locked objects remain selectable and inspectable; editing requires explicit unlock. Accessible move/resize/rotate/fit controls provide alternatives to precision gestures.

Paint uses the existing portable Ink engine and Android pressure/stylus input. Brush presets change real layer colour/width/opacity; eraser edits strokes, selection uses existing identity/version context. Fill supports solid layers, selected-layer colour and a bounded connected raster fill published as an independent transparent managed image layer. Eyedropper samples the bounded rendered composition. Drawing remains one project. Both direct Paint and its temporary inspector use the canonical compositor; one host-owned input lease drains through the application scope and retains recovery across navigation. Empty Paint entry is transient until the first completed stroke. Save/Undo drain and resume at the confirmed revision. Spatial effects/group buffers use a fixed logical 640-edge raster across preview/export.

Effects are deterministic bounded image operations, enabled/disabled/reordered independently. Tone, spatial, texture and lighting effects operate on one temporary layer bitmap at a time, serialized off main with cancellation and explicit limits. UI previews use downsampling; export retains bounded dimensions. Procedural paper/canvas/concrete/fabric/dust/scratch/noise textures need no external assets. Seeded effects reproduce across save/reopen/export. Source checks still run before and after decode and before output persistence. Published Compose bitmaps are not recycled while visible.

AI uses installed image-model capabilities and existing review/spend/privacy owner. Context explains the requested edit and destination. Keep produces a new project revision/copy or layer with preserved original and pinned provenance; unsupported remove/mask/relight operations are never presented as guaranteed specialized capabilities. No automatic network dispatch on opening Studio or switching modes.

## Evidence

Write production and regression sources for all batches before the one-worker focused gate. Preserve legacy schema-1 compatibility while schema-2 carries new fields; reject unsupported schemas/unknown fields instead of empty documents. Exercise hierarchy/locking/CAS/history, render output/effects/crop/blends, gesture mapping, mode invariance, orbit vocabulary, provider boundaries and archive roundtrip. Build and native device/STT/provider evidence remain separate and are recorded honestly. The host has restarted twice during #64 validation; do not infer those partial runs are passes.
