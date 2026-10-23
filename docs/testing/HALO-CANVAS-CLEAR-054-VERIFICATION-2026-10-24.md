# Canvas clear functional parity — #28 / #54

## Feature and canonical owner

`halo_canvas_clear_sketch(canvasId, version, nodeId)` exposes the saved outcome of the editor's Clear + Save through WorkspaceCanvasActions and NotebookCanvasRepository. It is a semantic edit of a saved local sketch, not physical pen simulation. The existing ToolRegistry, discovery, CognitiveSession, policy and action journal remain execution/approval owners.

The closed schema takes exact bounded IDs and observed board version. The executor checks active board and revision inside the same Room transaction as the repository operation. Only a local SKETCH with strictly decodable known Ink is accepted. Linked notes/files and PDF/image annotations are excluded. Aspect, card identity/geometry and links remain intact. Empty known sketches return `changed:false` without increasing revision; changed sketches return durable board/card IDs, `changed:true` and the saved version. Stale/repeated old commands reject; they never refresh a version and replay.

## Contract and policy

ToolOperability v1: workspace.write, EDIT, NotebookCanvasRepository, exact `version`, JSON receipt IDs `canvasId`/`nodeId`, required saved `version`/`changed`. Registered-feature review is exercised by WorkspaceOperabilityTest and the isolated native Workspace fixture. The destructive descriptor mandates ALWAYS approval and NEVER_REPLAY, including an ALWAYS_ALLOW user policy and previously approved scope. Discovery/Workspace native skill, per-tool destructive risk in both runtime and bundled manifest, merged risk floor and remote Workspace content categorization are covered. No new Android permission or automatic cloud transfer. Clear sketch/Clear already have EN/RU/LV/PL catalogue entries; approval presentation contains no technical card ID.

An open editor cannot save a different-content stale original over the cleared sketch, and its older recovery draft cannot be attached to that changed source. Recovery data remains owned by the existing exact-receipt logic. This is content-conflict protection, not a new per-edit revision protocol or a claim covering every process-death/ABA scenario. No new draft store, migration or undo owner. The action is destructive and offers no new automatic undo; preview and explicit approval precede it.

## Source and evidence

Base main `3013a7ab5d33ea9a799f7e9558a0e9b4bc7694b0`, fresh fetch divergence0/0 before edits. All implementation and regression sources completed before first grouped focused gate. Read-only review found no actionable issue. [Source identities and evidence](artifacts/HALO-CANVAS-CLEAR-054-2026-10-24/).

The first grouped gate passed in **10m53s**: **33 tests / six classes**, zero failures/errors/skips, plus both APK assemblies. Runtime Workspace provenance/skill ownership and approval contract:6; app Canvas actions4, operability6, bundled manifests5, editor persistence12. All new/changed source and regressions preceded the gate. The initial test APK was built but not installed.

Three test-only additions then strengthened existing phone fixtures with unique file-backed database close/reopen and corrected the Polish tool-use prompt's accidental English fallback. English/Latvian/Russian prompt bytes remain unchanged; unknown languages reject. Follow-up source review is clear. The role campaign already selects `#qualifyInstalledRoles` explicitly, retaining its exact one-test contract; the prompt regression is invoked separately. Test-only assembly passed in **1m11s** and production compilation was UP-TO-DATE. The production APK hash remained unchanged.

Installed on physical phone `f01fe673` / 23124RA7EO / API35 with exact installed hash readback:
- app `8c7466231c3f3e52d72177cadd052ca56908c3b3a1e9676709663642688fdc41` (289394779 bytes)
- final test `961bf53eb76e00b0fb9a64ed3d986106a21387321517bcf9d53b528e4e81f198`

**Five native Workspace cases passed in14.385s**, including canonical Canvas clear/readback/stale rejection, disk-backed file and continuity readback, and RU200% dialogs. **One separate native tool-prompt regression passed in0.015s**. MainActivity resumed after launch. Raw runner output, install/readback identities, both source phases and focused XMLs are adjacent. These isolated fixtures use synthetic files and generated database names with guarded cleanup; user Workspace data, role/model selections and OS preferences are untouched.

File fixtures cover TXT/Markdown/CSV create/edit/literal export/import and saved document/file/Canvas/Study material readback after database reopen; JPEG rotation/dimensions, persisted PDF vector annotations and exported raster page images; original PNG/PDF bytes retained. Continuity reconstructs Studio/Canvas/Study/media/search readers over the same persisted saved IDs, rejects a trashed source, and confirms no implicit Memory promotion. Database close/reopen is not OS process kill or a complete lifecycle matrix. Study **material resolution** is verified; saved Study-session restart is not claimed.

Three real typed/spoken cross-tool workflows and permitted Power remain NOT_RUN for this operation. Native owner fixtures do not establish those requirements; #54 remains OPEN. Physical pen, accessibility/tablet and broad #28 recovery qualification remain separate. No CI/Actions.
