# Halo Zone 5 — batch 4 instruments

Base: `70ee2a113915ea3a046d48a7fc21b92218169624`. Work branch: `codex/halo-zone5-batch4`. Host checks only, as requested. Verification below is being completed; this report does not establish physical or provider acceptance.

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

PENDING final host gate and review. Initial characterization ran 20 distinct checks: 14 expected RED assertions and six passing invariants, zero errors/skips. New assertions failed for old action ordering/assets, missing visible Back, large-text identity loss, hidden Memory tabs, extra Memory settings satellite and failed-search/retry recovery. See the committed RED summaries in the artifact directory.

| Evidence layer | Disposition |
|---|---|
| Source/document/owner comparison | Implemented for this batch |
| Focused and broader host regressions, registered-feature review, APK assembly | PENDING |
| Native-renderer Compose captures | PENDING inspection; never phone screenshots |
| Phone install, physical gestures/TalkBack, actual STT-final/spoken requests, remote provider/Power acceptance | NOT_RUN — host only requested |

For all workflows, typed-on-device, spoken-on-device, typed permitted Power and spoken permitted Power remain NOT_RUN. Host fixtures establish only their tested callback, policy, canonical operation or Room readback; display alone does not establish mutation. No issue is closed or hardware capability promoted.

Reproduce with [verify-host.ps1](artifacts/HALO-ZONE5-BATCH4-2026-10-30/verify-host.ps1). Native captures use synthetic public fixture content only. Final source/APK identities and per-suite results will be recorded alongside this report.

## Decisions and limits

1. The supplied approved handoff and explicit continuation are the specification; host-only reactive-theme instructions govern. Cost if wrong: phone, speech and provider acceptance remains unverified.
2. Migrate the five named instruments and their embedded workflows. Cost if wrong: unrelated roots remain in later batches.
3. Implement inline with one fresh whole-branch review; merge/push are already authorized. Use a native Windows ledger and preserve ignored scratch after the earlier blocked recursive cleanup. Cost: scratch remains local and ignored.
4. Keep Studio/Study's dedicated semantic owners: the current-page attachment binding intentionally excludes them. Cost if wrong: those continue through existing structured source/export/handoff paths.

Final review findings, their disposition and exact host evidence: PENDING.
