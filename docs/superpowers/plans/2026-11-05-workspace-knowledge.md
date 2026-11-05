# Workspace Navigation and Knowledge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Implement inline; a fresh reviewer checks the full change before completion. User authorization is “Implement this design”; no additional plan approval is needed.

**Goal:** Make Workspace navigation predictable, rich-note editing accessible and selected personal/reference data usable by Halo through bounded canonical operations.

**Architecture:** Extend ShellPageAction presentation and the existing Notebook/Organizer owners. Store library generations and their derived FTS index in AppDatabase, with CollectorSource the sole access-policy authority. Use existing governed network transport and ToolRegistry; keep Runtime orchestration stable.

**Tech Stack:** Kotlin, Android Compose, Room/FTS4, WorkManager/coroutines, commons-compress, Jsoup, existing Runtime schemas and source leases.

**Spec:** `docs/superpowers/specs/2026-11-05-workspace-knowledge-design.md`

## Global Constraints

- Finish the entire production/regression coding batch before building/running tests; one core/Gradle worker/test fork, existing 16 GiB swap.
- Runtime result ceiling remains 4,000 UTF-16 characters including JSON escaping; complete revision-pinned pages/chunks, no private-data fallback bypass.
- One import worker; 256 MiB compressed input/job, 1 GiB expanded/job, 2 GiB managed storage including staging/index, 256 KiB/entry, 512 MiB free reserve.
- Preserve existing notes, records, model, widgets, full/Kids switch and protected adult unlock. No destructive migration, arbitrary AI file paths, new CI or paid image generation.
- EN/RU/LV/PL copy/discovery; minimum targets 48dp, 56dp Kids; adaptive primary controls and keyboard-visible insertion.

## Review Focus

- Changing selection/family/source approval during a paged read or import must stop disclosure/publication.
- Malformed XML, quoted CSV and compressed input with unknown size must remain bounded and recoverable.
- Literal documents must remain literal while rich-note creation and empty-block style conversion become accessible.
- Menu recomposition must preserve presentation without accepting an obsolete callback lease.
- Long escaped content must reassemble completely through the real Registry without invalid JSON or mixed generations.

### Task 1: Explicit shell presentation and hierarchy

Files: `ui/presence/ShellPageActions.kt`, new `ui/zone5/WorkspaceActionPresentation.kt`, `WorkspaceToolMenus.kt`, `HaloZone5OrbLauncher.kt`, page action declarations and `ui/everyday/OrganizerScreen.kt`/`OrganizerSectionContent.kt`.

Interfaces: `ShellActionPresentation(role, group, order, primary)` is presentation only; `ShellPageProjection.invoke(id)` remains the sole live page dispatcher. `WorkspaceActionPresentation.forAction(id)` is an exhaustive map of existing action IDs, never a command registry.

- [x] Author menu coverage/unique-placement/stale-lease and 200% layout regressions; include every existing page family.
- [x] Add metadata, explicit action mapping, direct single-entry groups, primary adaptive rows and secondary-only default sheets. Keep one global Workspace chooser and persistent controls.
- [x] Map Organizer to Today/Plan/Time/Research and contextual Settings; retain original routes/record semantics, secondary Inbox/Routines/Agenda filters and true disabled reasons.

### Task 2: Canonical rich-note entry and keyboard editing

Files: `ui/screens/notebook/NotebookScreen.kt`, `NoteEditorScreen.kt`, `NoteEditorViewModel.kt`, Workspace tool list and shell routes; tests under `ui/screens/notebook`/`ui/zone5`.

Interfaces: existing `NotebookViewModel.createNote()` creates rich notes; existing `NoteEditorViewModel.changeBlockType`/`insertBlock` own mutation. Shared insertion callback targets the selected/current empty block and keeps source validation.

- [x] Author rich creation/heading/Enter/paragraph and literal-file preservation regressions, including an open IME and selected text.
- [x] Route HaloNotes to Notes library by default; expose plain-file tools explicitly. Direct Insert opens its picker; compact Insert/Style/Undo remains above the keyboard, without duplicate menus.
- [x] Keep autosave/error/caret behavior and existing typed/voice note operations intact; do not invent a second editor/store.

### Task 3: Multilingual capability discovery

Files: `runtime/cognition/.../tools/ToolDiscoveryService.kt`, `ToolExposurePlanner.kt`, turn/context assembly call sites; host descriptor metadata; new/extended discovery regressions.

Interfaces: `ToolDiscoveryService.catalogueSummary()` returns bounded availability-filtered domain coverage; `search(query,limit)` accepts Unicode and exact IDs. Planner receives the same catalogue provider for initial/continuation/recovery paths.

- [x] Author Cyrillic/accented/mixed-ID discovery and domain-coverage/exposure regressions.
- [x] Implement Unicode/Locale.ROOT normalization, host-owned translated operation keywords and balanced catalogue selection.
- [x] Integrate summaries without inserting private content/counts into ungated prompts or weakening exposure policy.

### Task 4: Complete bounded Organizer/Source readback

Files: new `di/tools/EverydayToolReadback.kt`, `EverydayToolExecutor.kt`, `EverydayToolContracts.kt`, `EverydayResultContracts.kt`, `OrganizerOperationTools.kt`/contracts, registered descriptors and tests.

Interfaces: revision-pinned `view`, `offset`/cursor and `limit` arguments on existing read tools. Encoded responses include exact IDs/revisions, completion/next offset and at most five summaries or a bounded text chunk. Query pagination binds the authority and query/source-set identity.

- [x] Author real Registry maximum escaping/reassembly/stale/revocation regressions before changing readback.
- [x] Replace oversized unpaged read views, maintain compatible defaults and update closed result schemas/versioned operability.
- [x] Keep existing write receipts truthful and bounded, including commitment/research results; expose actual blocked/empty/stale outcomes.

### Task 5: Durable reference libraries and parsers

Files: new `everyday/knowledge/` models/parser/limits, `data/knowledge/` Room entities/DAO/repository, `data/AppDatabase.kt`, collector-kind/codec/UI policy branches and tests.

Interfaces: `ReferenceLibraryRepository` owns `preview`, `confirm`, `cancel`, `resume`, `query`, `entry` and generation publication; all policy reads come from CollectorSource. `ReferenceInput` opens a scoped SAF handle. `ReferenceEntry` carries stable ID/title/text/format/language/URL/revision/license/dates. `ReferenceImportState` is durable and observable.

- [x] Author streaming XML/MedlinePlus/JSONL/CSV, ZIP restrictions, bounded decompression, corrupt input, cancel/resume/dedup and Room migration/FTS tests.
- [x] Implement staged generations, one import worker, real byte/storage/free-space accounting, checkpoints and atomic publication. Old ready content survives failed updates.
- [x] Add local-library Source kind, derived Unicode FTS4 index, source removal cleanup and family/privacy read/commit guards.

### Task 6: Library UI, canonical AI operations and online research

Files: new `ui/everyday/ReferenceLibrariesScreen.kt`/view model, new `di/tools/ReferenceLibraryTools.kt`/contracts, `ManualDependencyGraph.kt`, shell routes, capability manifests, `ResearchSources.kt` and governed research transport owner.

Interfaces: one repository serves UI and registered catalogue/query/read/import lifecycle operations. File selection returns a scoped handle, never an arbitrary path. Online article selection pins page/revision and stores dated sections. Starter catalogue offers MedlinePlus summaries, an explicit small CDC preparedness manifest and selected Wikipedia articles; downloads require explicit Power review.

- [x] Author typed/voice-parity/schema/selection-revocation and Local-zero-network tests, plus Wikipedia revision/section parsing and transfer failure cases.
- [x] Add preview/confirm/progress/cancel/reselect/resume UI, entry reader/citations and Source management integration.
- [x] Register real operations and operability metadata, extend remote classification/child policy explicitly, preserve untrusted-data boundaries.
- [x] Implement the three usable optional starter choices and full selected Wikipedia article/section retrieval through bounded governed transport. Preserve honest snippet/document distinctions, original language/rights/date and failure states.

### Task 7: Full coding review and one validation gate

Files: changed regression sources, localized resources/catalogue, AI-controllable verification report and this plan's ledger.

- [x] Complete source-level coverage checks, localization generation and a fresh whole-change review; resolve important findings in one coherent fix batch.
- [x] Run `halo-gradle :runtime:cognition:test :app:testDebugUnitTest` with related filters/tasks resolved from settings, one worker/fork. Expected: all selected tests pass; archive source identity, counts and failures honestly.
- [x] Run `halo-gradle :app:assembleDebug` after the coding batch; expected: signed APK with current source identity. Fix any actual compiler/test failures; no unrelated repeat runs.
- [ ] Install `-r` and verify APK hash. Exercise normal/200%/landscape/keyboard/theme navigation, rich note durability, successful/cancelled local library import/search/citations, family switch and AI retrieval using owned fixtures; restore phone settings.
- [x] Publish code and sanitized evidence under existing push authorization; record source/native/spoken/cloud limits separately. No issue closure, merge or paid generation.

Validation outcome (5 November): production source `a873f44ca10f` is compiled, signature-verified and pushed. Gate 04 broad run: 1094/1097 pass; its three remaining failures are corrected and all 34 affected/family/image checks pass in Gate 05. Full JUnit and focused results are retained separately. Native installation and physical-phone acceptance remain unchecked because no phone is detected. See `docs/testing/WORKSPACE-KNOWLEDGE-2026-11-05.md` for exact scope and APK identity. No paid generation.
