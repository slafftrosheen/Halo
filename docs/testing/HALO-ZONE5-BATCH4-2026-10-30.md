# Halo Zone 5 — batch 4 instruments

Base: `70ee2a113915ea3a046d48a7fc21b92218169624`. Work branch: `codex/halo-zone5-batch4`. Host checks only, as requested. Verified source snapshot: `5c8361c8e3bd80326a8faf7dad75454771c0cc76`, embedded in the debug APK with `GIT_DIRTY=false`. This report does not establish physical or provider acceptance.

## Supplied-document comparison

Authority: the supplied pasted request, the original Zone 5 ZIP (SHA-256 `105831ba90909dd0e303590266117f3d0479a1b896f1987c08bf9a0404497404`), `docs/design/halo-zone5/DESIGN.md`, the pack's component contracts, AI_OPERABILITY and page-plan R004/R007–R009/R012–R015 plus E004/E018–E031/E037–E046. The three approved images cover batch 3; batch 4 follows the documented instrument contracts rather than inventing reference screenshots. The user's reactive-theme instruction overrides fixed mint/suite accents.

| Instrument | Implemented behavior |
|---|---|
| Notes / Office | Tool/document identity and true save state; Insert / Outline / Find; direct Undo/Redo and formatting. Insert captures the existing block identity and content snapshot. Literal text retains its real format and returns to Office for export. Office New / Open / Export uses the selected kind's actual text/Markdown, image, PDF-page or annotation workflow. Missing or unsupported sources cannot pretend to export. |
| Canvas / Studio | Canvas Add / Arrange / Fit; deterministic arrangement remains a preview, preserving selection and ink owners. Studio has visible Projects / Canvases, canonical blank Canvas creation, Add layer / Layers / Export, direct dirty Save and Undo/Redo, layer visibility, exact-revision picker returns, true Rename and Save as, and a saved-version preview before Restore. |
| Study | Landing New lesson; the setup exposes Material or Options only on the relevant step. Active lessons expose Sources / Practice / Read aloud or Stop reading. Opening practice changes only the local view. Back returns to tutor before leaving the session. Current exercise, source, reader and accepted-card owners stay authoritative. |
| Kids | Native semantic pictures and large labeled Back, Stop, Type, Help and parent-space controls. One scoped shell orb; no default adult satellite on Kids Home. Existing child allowlists, parent credentials, grants, lesson policies and stop/voice owners are preserved. |
| Memory | Visible Memories / Suggestions / Learning history tabs with independent scroll positions. One Remember default; Learning settings remains in All actions. Query/filter/retry can recover after index or stream failure, with loading/error/empty kept distinct. Detail actions bind at the root, survive scrolling, and use source/correction/confirmed Forget through existing owners. Stale forms retain their draft and disable saving/forgetting. |

Shared controls use supplied native assets and current Material roles, including after palette changes. Stored artwork, document pixels, layer colors, ink and semantic errors retain their represented meaning. EN/RU/LV Zone 5 resource sets have 55 matching keys. All 187 supplied native vectors byte-match their sources.

## Canonical owners and operability

This is presentation over the existing registered contracts, with no new tool schema, storage model, runtime/provider API or authority. `ShellPageOwner` carries immutable entry/object/selection identity and registered tool discovery; an orbital callback never grants a read, mutation, cloud, microphone or parental permission.

| Surface | Existing semantic operations and owner | Host exceptions / protected behavior |
|---|---|---|
| Notes | `NotebookDocumentRepository` and `NoteEditorViewModel`; `halo_note_get/read_block/insert_block/edit_block/replace_content/rename` | Android file picker and freehand input remain human host interactions; existing CAS, conflict, proposal and recovery rules apply. |
| Office | `WorkspaceFileTools`, document/file/image/PDF/annotation/batch owners; existing `halo_text_*`, `halo_csv_*`, image and PDF contracts | Export creates a managed output with a receipt and provenance. Picker/share activity belongs to Android; no DOCX/XLSX round-trip or native PDF text-editing claim. |
| Canvas / Studio | `NotebookCanvasRepository`, `StudioService`, `StudioProjectRepository`; `halo_canvas_*` / `halo_studio_*` | Preserve node/layer/source/page/crop IDs, revision admission, preview/cancel, ink selection and immutable outputs. Draw gestures and external camera/picker remain human host exceptions. |
| Study | `StudyUseCases`, repository, `ActiveStudyToolsOwner`, reader and session-action contracts | UI opening/appearance does not generate a turn, grade an answer or dispatch a provider. Execution choice, source anchors, current-exercise leases and narration remain with existing owners. |
| Kids | Existing scoped Kids voice/project/lesson owners and allowlists | No adult Home writer fallback; parent credential and explicit cloud grants stay protected. |
| Memory | `MemoryRepository`, `PersonalLearningRepository`, `WorkspaceUseCases`; `halo_search_memory/read_memory/remember/propose_memory/update_memory/forget_memory` | Suggestion review is not approval. Secrets/scope/evidence admission remain canonical. UI stale-form checks supplement existing identity-based correction/forget; this batch does not claim a new atomic memory CAS. Notes and source objects survive Forget. |

## Verification

**649/649 host checks PASS**, zero failures, errors or skipped tests; Android debug APK assembly PASS (`BUILD SUCCESSFUL`, exit 0). One fresh whole-branch review and its single fix pass are complete. Initial characterization ran 20 distinct checks: 14 expected RED assertions and six passing invariants, zero errors/skips. New assertions failed for old action ordering/assets, missing visible Back, large-text identity loss, hidden Memory tabs, extra Memory settings satellite and failed-search/retry recovery. See the committed RED summaries in the artifact directory.

| Evidence layer | Disposition |
|---|---|
| Source/document/owner comparison | Implemented for this batch |
| Focused and broader host regressions, registered-feature review, APK assembly | PASS — complete final 649-check invocation and APK assembly |
| Native-renderer Compose captures | 17 packaged captures inspected across the instruments and representative forms; never phone screenshots |
| Phone install, physical gestures/TalkBack, actual STT-final/spoken requests, remote provider/Power acceptance | NOT_RUN — host only requested |

For all workflows, typed-on-device, spoken-on-device, typed permitted Power and spoken permitted Power remain NOT_RUN. Host fixtures establish only their tested callback, policy, canonical operation or Room readback; display alone does not establish mutation. No issue is closed or hardware capability promoted.

Reproduce with [verify-host.ps1](artifacts/HALO-ZONE5-BATCH4-2026-10-30/verify-host.ps1). Native captures use synthetic public fixture content only. See [final results, source/APK hashes and capture manifest](artifacts/HALO-ZONE5-BATCH4-2026-10-30/final-summary.json). Raw source hashes identify the build worktree; Git object identity carries the same source into main despite checkout newline normalization.

## Decisions and limits

1. The supplied approved handoff and explicit continuation are the specification; host-only reactive-theme instructions govern. Cost if wrong: phone, speech and provider acceptance remains unverified.
2. Migrate the five named instruments and their embedded workflows. Cost if wrong: unrelated roots remain in later batches.
3. Implement inline with one fresh whole-branch review; merge/push are already authorized. Use a native Windows ledger and preserve ignored scratch after the earlier blocked recursive cleanup. Cost: scratch remains local and ignored.
4. Keep Studio/Study's dedicated semantic owners: the current-page attachment binding intentionally excludes them. Cost if wrong: those continue through existing structured source/export/handoff paths.

## Review and regression evidence

One fresh read-only whole-branch review of `70ee2a1..328785f` found two Important defects. Both were reproduced before correction and passed in the complete final gate:

- `studyPracticeReviewAndBackUseExclusiveViews`: Practice previously masked focused review. Review now leaves Practice, narration stops through its existing owner, and Back returns to the tutor without leaving or grading the session. Actual Room session and review-event readback remain unchanged.
- `groupingOffCenterCardsPreservesTheReviewedSelectionAndUndo` and `changingASourceWhileNamingASelectedGroupCannotCommitIt`: selected grouping previously opened blank spatial-group insertion. It now uses the existing scoped organization preview/approval, source fingerprints, durable group links and Undo. Off-center selected IDs are preserved; the unrelated card stays unchanged; source changes produce a visible refusal and no group/edges.

The same fix pass moved Memory forms and Canvas organization review onto the existing workspace dialog host, preserving user density, IME space and fixed controls. Native Memory correction saves through the canonical owner, retains the edited draft after an external change and refuses stale saving. These assertions were retained while correcting the test effect/VM clock and awaiting actual dialog attachment; no write, source or callback assertion was removed.

Initial broad validation ran 644 checks: 635 PASS, nine failures. Six old Study selectors matched both the new work-area heading and utility identity; their fixtures now scope the unchanged geometry/accessibility assertions to the utility. Search now waits for loaded results; full-list Back is scoped separately from the newly labeled utility Back. Native window failures were diagnosed rather than treated as passes. The penultimate full source ran 649 checks: 647 PASS and two organization-dialog window failures; using the existing workspace host resolved them. The **final complete invocation ran all 649 checks and passed**. The original RED and intermediate summaries remain as checkpoints, not final dispositions.

Registered-feature/ToolOperability checks include `HostFirstSkillAuditTest`, `AssistantToolsTest`, Workspace operability, typed Note/Canvas/file/Study actions, existing source/version/receipt/replay and persistence suites. The source change introduces no new callable capability or registry schema. Native coordinates and semantic input exercise host UI/owners; actual Android picker, credential, pen/touch, TalkBack, STT-final and provider paths remain the explicitly bounded exceptions / NOT_RUN layers above.

Minor deferred: the Office work heading is “Office Tools” while its legacy utility string still says “File tools”. Both identify the same existing instrument and route; this is a copy inconsistency.

Additional review rulings and costs:

- Physical touch, TalkBack, actual speech, provider and hardware acceptance are outside the explicit host-only request. Cost: those acceptance layers stay open.
- Source review and sampled renders do not establish exhaustive 200%/IME acceptance on every embedded surface. Cost: later physical/window qualification is still required.
- Existing Memory correction/Forget remain identity-based repository operations. UI snapshot/re-read checks supplement them; this batch does not introduce atomic Memory CAS. Cost: the existing concurrent-write limitation remains.
- Final host/APK status is supplied by the owning gate, not inferred from the reviewer. Cost: only recorded checks and exact artifacts establish this result.
- Unrelated roots remain their later batches. The reused host-owned worktree and ignored scratch remain in place. The user's merge/push instruction already supplies integration authorization; no approval menu is needed.

## Native-renderer captures

Production working-area screens use NavHost and isolated canonical Room owners with public synthetic content; Kids/header/CSV captures use production components. These are not full phone journeys. The shared shell/orb, palette transitions, action leases and layout tests run separately in the same final gate. EN/RU/LV, non-default Blue/Rose palettes, dark/light surfaces, 200% text and short landscape examples were inspected. Stored content/ink retains its actual colors.

| Surface | Captures |
|---|---|
| Notes / Office | [Notes](artifacts/HALO-ZONE5-BATCH4-2026-10-30/notes-dark-blue.png), [Office](artifacts/HALO-ZONE5-BATCH4-2026-10-30/office-dark-blue.png) |
| Canvas / Studio | [Canvas](artifacts/HALO-ZONE5-BATCH4-2026-10-30/canvas-light-rose.png), [Studio](artifacts/HALO-ZONE5-BATCH4-2026-10-30/studio-light-rose.png), [named group review](artifacts/HALO-ZONE5-BATCH4-2026-10-30/canvas-group-review.png), [stale-source refusal](artifacts/HALO-ZONE5-BATCH4-2026-10-30/canvas-group-stale.png) |
| Study | [Current lesson](artifacts/HALO-ZONE5-BATCH4-2026-10-30/study-light-rose.png) |
| Kids | [Choices / dock](artifacts/HALO-ZONE5-BATCH4-2026-10-30/kids-light-rose.png), [RU 200%](artifacts/HALO-ZONE5-BATCH4-2026-10-30/kids-ru-200.png), [LV 200%](artifacts/HALO-ZONE5-BATCH4-2026-10-30/kids-lv-200.png) |
| Memory | [Tabs / facts](artifacts/HALO-ZONE5-BATCH4-2026-10-30/memory-dark-blue.png), [detail](artifacts/HALO-ZONE5-BATCH4-2026-10-30/memory-detail-dark-blue.png), [correction](artifacts/HALO-ZONE5-BATCH4-2026-10-30/memory-correction-dark-blue.png) |
| Utility / table | [200% utility](artifacts/HALO-ZONE5-BATCH4-2026-10-30/utility-en-200.png), [CSV](artifacts/HALO-ZONE5-BATCH4-2026-10-30/office-csv-light-360.png), [RU short landscape 200%](artifacts/HALO-ZONE5-BATCH4-2026-10-30/office-csv-dark-ru-landscape-200.png), [LV 200%](artifacts/HALO-ZONE5-BATCH4-2026-10-30/office-csv-light-lv-412-200.png) |


## Verified debug artifact

Package `com.aistudio.braintool.xkqzps`; 293374551 bytes. SHA-256 `d07059d8a0cd55398b576c677782e4412a03780b1fc7925f8bb9d4ce3061b7a7`. The build output and `releases/halo-alpha-debug.apk` byte-match. No phone install was attempted.
