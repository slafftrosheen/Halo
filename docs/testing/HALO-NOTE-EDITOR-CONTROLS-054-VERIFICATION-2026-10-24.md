# #54 Note editor controls

Base42eb5aec9362e7a0fabdd7ab2588afad11fae9b0 fetched main0/0 clean. Source, desktop and isolated native owners verified. Keep #54 OPEN. No CI/Actions.

## Feature and canonical owners

The editor already supports saved block commands and lifecycle changes, while the assistant could only create/search/read notes. WorkspaceNoteActions adds18 operations via the existing WorkspaceToolExecutor/ToolRegistry: structured get/read_block, rename, insert/edit/move/indent/split/merge/duplicate/delete blocks, insert/relink reference, Markdown replacement, archive/trash/restore and remove-from-inbox. Content uses the existing NotebookDocumentRepository/DocumentReducer revision owner; lifecycle uses NotebookUseCases. Graph injects the actual UI owners. No new writer, recovery store, undo history, runtime, provider or approval owner.

Actual registration carries ToolOperabilityv1, workspace.read/write, closed typed schemas, bounded JSON receipts, exact saved IDs/revisions/content hashes and discovery. Home Notebook references meaningful operations. Runtime/bundled Workspace skill per-tool risks and result data provenance match. Presentation reuses Notebook/Save/Delete labels; no resource translation changes or rendered UI proof are claimed.

## Typed operation contract

All operations resolve an exact note ID inside a Room transaction. The same editor initialization gives legacy body-only notes stable blocks without changing their committed revision. Specialized halo-file/PDF/receipt CODE documents remain with their specialized owners. A current note identity hashes all saved block content/IDs/order/format/references and header/revision/lifecycle/Inbox state and sorted attachment metadata. Every write checks it inside the transaction that applies the canonical command. Later read pages require the first observed identity. Reference changes also verify the exact active target through the existing WorkspaceContextStore; targets are linked, never copied or edited.

A durable editor draft stops assistant writes without acknowledging/discarding/replacing the draft; Notebook recovery is the required next step. Uncommitted active-editor changes remain subject to the editor's own revision conflict/recovery path. There is no automatic refresh-and-replay. Writes are NEVER_REPLAY; reads SAFE. Block deletion, whole replacement and Trash retain DESTRUCTIVE/ALWAYS, including always-allow routine policy; ordinary allowed edits retain USER_POLICY. Existing capability/Kids/Private/Power/permissions remain authoritative. Saved/source text stays untrusted data; no model output is permission or code execution.

Block previews are paged eight at a time/256characters each; exact text uses2000character pages, including JSON escaping bounds. Responses fit24000characters. New block IDs are bounded100 with count/truncation fields and subsequent saved block pages. Insertion/duplicate/split check the existing2000block limit before mutation; replacement checks codec sourceBlockCount before reducer normalization. Unsupported Markdown requires explicit literal-fallback acceptance; overflow is always refused. New reference targets use versioned reference operations. Split refuses a surrogate-pair boundary. Omitted insertion anchor appends, matching the editor; merge preserves the editor's newline; final-block deletion is refused, matching its retained-last-block rule.

Permanent purge is not newly exposed: the existing purge's managed-file cleanup lacks an atomic durable effect receipt. Editor-local undo/redo needs its active history; no durable assistant undo is invented. These and complete rich selection/proposal/export workflows remain part of the wider parity audit. Original #54 acceptance is not reduced to this saved-editor slice.

## Source review and regressions

All final production, desktop and native fixture sources precede the final focused gate. Read-only review found pre-normalization replacement overflow, sole-block deletion truth and omitted-anchor description mismatches; corrected before building, with regressions. Capacity metadata is additive to the existing codec; the assistant independently refuses overflow even with literal fallback. This does not claim every older codec consumer has been changed to enforce it.

New desktop cases cover actual registration/policy/schema/bundled risk parity, legacy initialization, exact revisions and UI conflicts, partial edits/formatting, ordering/indent bounds, split/merge/duplicate/delete semantics, reference source/version protection, Markdown fallback/refusal, lifecycle/Inbox, recovery drafts, concurrent writes, transaction failure rollback, foreign IDs/missing anchors, malformed arguments, specialized documents, hostile text/result bounds, paging, capacity and surrogate boundaries. Runtime covers sole Workspace ownership/risk and remote Workspace provenance for all18operations. Existing canonical document/repository/Home/operability checks accompany the targeted gate.

## Three functional workflows

| Workflow | Typed on device | Spoken on device | Typed permitted Power | Spoken permitted Power | Durable real-user readback |
|---|---|---|---|---|---|
| Find exact note → rename/edit block → inspect saved revision | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Link exact Workspace source → reorder/format blocks → inspect source retention | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Archive/restore or approve Trash → verify retained saved content | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

The isolated native fixture uses a UUID Room database/preferences and actual registered routine policy/semantic owners. It edits saved content, inserts an exact reference, renames, refuses a stale edit and pending-draft mutation, then recreates Room and verifies saved block/reference/header/recovery/source data. It uses synthetic data and no global notes, mic or provider request. Database recreation is not OS process death; semantic ToolCalls are not actual keyboard/STT-final/Power workflows. Destructive effects remain covered by desktop policy/owner cases, not this native routine fixture.

## Verification results

First gate terminal FAIL4m56s after9 runtime cases passed: one source compile error used a nonexistent rejection enum. Corrected to existing RESOURCE_UNAVAILABLE. A final audit also found oversized legacy initialization could truncate before a read; new preflight refuses it before migration, and a regression verifies exact legacy note/zero blocks are unchanged. Final read-only review clear. The next gate was deliberately cancelled before packaging to finish the attachment-content guard: old Trash requests now refuse changed attachment metadata, with no note-revision assumption. Reference targets are restricted before access to the same active kinds supported by WorkspaceContinuity. Final app cases PASS59/5classes (new22, document repository8, note repository5, operability7, Home catalogue17), zero failures/errors/skips. Final gate PASS8m29s/511tasks/27executed, both APKs assembled. All15 captured compiled input hashes match. Native run72349aec8f474fea839dccdb0ab0327d PASS1/1 in0.988s on f01fe673/23124RA7EO/API35. Saved block/header/reference/source and uncommitted recovery survived Room recreation; stale edit and recovery overwrite were refused. UUID fixture data cleaned; MainActivity resumed. Runtime source identities are unchanged since the9 passing cases; no unrelated rerun is needed.

Installed app SHA25671b9435ebb475aedcaabc5a95b3dbec3a2e777b04d216bb041034fca7f380114 (288516147bytes); test SHAb1c9149b8a1548cafd53259e0413acb25ab44c8b64b41d3abbecfdee924ebd5a (2122214bytes). Both installed readbacks match. [Bounded raw evidence/source identities](artifacts/NOTE-EDITOR-CONTROLS-054-2026-10-24). No OS process death, actual keyboard/STT-final/Power or rendered-screen acceptance is claimed. #54 remains OPEN for its original full acceptance and remaining operability work.
