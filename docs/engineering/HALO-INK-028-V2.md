# HALO-INK-028 — bounded Ink v2 and shared Android pen editing

Date: 2026-10-21. Base: `0513fb53d30583948c3e1d019896fbd7bbce82b4`.
Issue: #28. **SOURCE IMPLEMENTATION; NOT COMPILED, NOT TEST-RUN, NOT DEVICE-QUALIFIED.**
This work is independent of the unmerged #40 search/media/cost package.

## Implemented paths

- Portable `InkPoint`, `InkStroke`, `InkDocument`, strict `InkCodec` and transactional `InkEditor` live under `sdk/runtime-sdk/.../ink`; no Android or vendor types enter that boundary.
- `host-android/.../ink/AndroidInkInput` handles pointer IDs, batched historical samples, advertised pressure ranges, optional tilt/orientation, hover, physical eraser and primary stylus button held at contact start. Finger and primary-button mouse remain input options.
- A stylus preempts an unfinished finger contact; additional fingers never join a pen stroke. Platform `ACTION_CANCEL` and API 33+ `FLAG_CANCELED` discard the entire active draw/erase transaction. A bounded proximity grace avoids immediate palm input without globally disabling touch. Hardware-specific palm quality is not established by this policy.
- One `CanvasInkSurface` now owns input, variable-width midpoint-smoothed rendering, explicit Draw / whole-stroke Erase / Move view modes, 1–4x zoom, fit/reset, bounded undo/redo, keyboard Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y, focus and accessibility labels.
- `CanvasSketchPadDialog` and `CanvasSourceInkDialog` share that surface. The background page and ink use the same fit/pan/zoom transform; pointer positions use its inverse. Tablet dialogs can expand to 880dp, with scrollable controls on small/landscape screens. Page rendering remains bounded and read-only.
- Existing `onDraft` callbacks retain completed edits through the established Room-backed drawing-draft owner. Source-page changes first validate the existing aggregate markup and draft envelopes. Cancel retains its existing owner behavior: discard the recovery draft, leave saved source content untouched. An interrupted contact itself is deliberately not persisted.
- New data is visible through sketch previews, file overlays and existing repository validation. The old XY decoding API remains a read-only projection for existing consumers such as local OCR; editable surfaces retain full v2 documents. Plain-text search excludes all `sketch:` / `halo-markup:` versions, not just v1.
- Fifteen new strings are present in EN/RU/LV. No proprietary tablet SDK, inference provider, Bluetooth integration, new dependency, permission, network flow or Room schema change.

## Format and migration contract

New writes use `sketch:v3|<aspectMilli>|<tool>@<strokeId>@<points>|...`.
The reader remains backward-compatible with `sketch:v1|...` and
`sketch:v2|<aspectMilli>|<tool>@<points>|...`.

Tools have stable wire values: `L` legacy, `F` finger, `S` stylus, `M` mouse.
Normal points are `x,y,timeMs,pressure,tilt,orientation,buttons`, separated by `;`.
Coordinates and pressure are integer thousandths, time is relative milliseconds,
angles are milliradians; `_` means an unavailable axis, distinct from measured zero.
Legacy `L` points retain compact `x,y` pairs; assigning extra metadata to a legacy
point is rejected rather than silently discarding it. Whole-stroke erasure stores
the resulting vector document, not a background-colored overlay or an event log.

Each v3 stroke carries a bounded ASCII ID that remains stable when its points are
edited. Decoding older formats assigns deterministic per-document IDs, so their
references remain stable after a v3 save. The strict reader recognizes v1, v2
and v3. Malformed, oversized, unknown-version or unknown-tool data is unreadable,
not an editable blank sheet. Opening or saving an unchanged legacy drawing
preserves its original string; the first real edit writes v3 without discarding
old points.
When old geometry has no recorded aspect, the first edit binds it to the editing
rectangle; undo restores the exact pre-edit document. No database migration runs.

## Quotas and failure behavior

| Boundary | Limit |
| --- | --- |
| Strokes per document | 256 (legacy v1 input remains 64) |
| Samples per stroke | 2,048 (legacy v1 input remains 256) |
| Total samples per document | 16,384 |
| Encoded document | 262,144 characters |
| Contact duration field | One hour; relative timestamps must be nondecreasing |
| Undo history | At most 16 entries; bounded retained point references |
| Existing source annotation envelope | 16 pages / 1,500,000 characters, unchanged |
| Existing drawing draft envelope | 3,000,000 characters, unchanged |

All decoder splitting is bounded. A full v1 drawing stays compact after v2
conversion. The wire limit remains deliberately modest because the existing host
also uses saved state; this is not a guarantee for arbitrary Android Binder state
sizes. At point/stroke capacity, input stops admitting samples and shows a visible
limit message; already accepted points may finish as the current stroke. A wire
or aggregate-envelope overflow rejects the entire last edit and keeps the prior
draft. No silent payload truncation is used. History is session-local; process
recreation restores completed drawing data, not the full undo stack or viewport.

## Verification evidence and limits

Authored **37 new source cases**: 25 portable/codec/history/draft cases, 10 synthetic
Android adapter instrumentation cases, and 2 additional search-exclusion cases.
The pre-existing three search cases are retained. Coverage includes v1/v2,
metadata, full legacy capacity, corrupt/future input, monotonic timestamps,
point/history bounds, swept erasure, rollback, undo/redo, source markup, drafts,
pointer-index reordering, physical eraser type, barrel button, hover, mouse
secondary-button rejection, batched samples and pressure normalization.

Performed before the v3 follow-up: source/call-site review against pinned base,
old-format consumer search, EN/RU/LV XML parsing/key parity/reference checks,
whitespace checks, content hashes and changed-path review. The portable runtime
SDK test suite passed for the original v2 implementation.

**2026-10-22 follow-up:** v3 stable stroke IDs, backward-compatible v1/v2 reads,
selection geometry and exact source identity/revision resolution were added. The
full `:sdk:runtime-sdk:test` suite passed, as did focused app tests for InkV2,
CanvasSketchCodec and WorkspaceLocalContentSearch. The search test was rerun after
adding explicit v3 payload coverage; it passed. The first app run hit a corrupt
local Gradle cache entry, then passed with `--no-build-cache`. No CI/Actions,
emulator install, hardware drawing or physical pen acceptance is claimed here.
No independent reviewer agent was available.

## Keep #28 open until owner acceptance

1. Compile and run the added tests when authorized; install the resulting APK.
2. Verify an actual compatible pen's pressure, tilt, hover, physical eraser/button
   and palm behavior. Check finger/mouse fallback and device disconnect/reconnect.
3. Check low-RAM/large-drawing latency, 200% text, small landscape screens, tablet
   orientation, draw/erase cancellation and accessibility navigation.
4. Reopen saved v1/v2 drawings and multipage annotations; kill/recreate the app
   between completed strokes, while drawing, and during a pending draft write.
   Confirm database errors do not discard prior saved content.
5. Verify PDF/image preview alignment and original-file integrity under zoom,
   rotation, page changes and source-file version changes.

Source completion cannot satisfy the physical acceptance items in #28. No claim
is made that Huion or another named pen device has been qualified. Ink selection,
AI interpretation and cross-device synchronization remain #29 / #30, not this patch.

## Platform references reviewed

- Android advanced stylus features: https://developer.android.com/develop/ui/compose/touch-input/stylus-input/advanced-stylus-features
- MotionEvent (tool types, axes, history and cancellation): https://developer.android.com/reference/android/view/MotionEvent.html

The adapter follows these platform contracts; documentation is not device evidence.
