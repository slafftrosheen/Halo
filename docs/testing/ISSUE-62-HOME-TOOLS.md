# Issue 62 — Home and nested-tool source review

Latest delivery status and exact source/APK/test evidence: [integration ledger](ISSUE-62-INTERFACE-DELIVERY-2026-10-25.md). Checkpoint-specific sections below are historical review records; they do not override that ledger or establish final phone acceptance.

Date: 2026-10-25. Baseline: `d5fe5943c8c3e689c49b8f7513823f6e50054556`; reviewed the current uncommitted Interface 4 working changes. This is a source review, not physical acceptance or proof of complete assistant operability. No build, device test, speech request, provider operation, or stored user-data mutation was performed by this review.

## Implemented presentation changes

| Screen | Halo Zone projection | Preserved owner/action path |
| --- | --- | --- |
| Home | Composer, Chat/Screen popup; new/history/Automatic/clear/today contextual actions | Existing encrypted draft owner, submit/cancel callbacks, original history and mode dialogs, attachment pickers and Tools sheet |
| Note | Compact title/rename/back, read aloud, tools; outline/find/assist actions | Flush-before-leave, revision-bound note view model, existing AI/action sheets, original confirmations and editor controls |
| Canvas | Compact title/back/options; find/fit/tools satellites | Existing board owner, leave persistence, selection controls, source/import/export menus and intrinsic editing controls |
| Study setup | Compact title/back/current step; new/material/options actions | Existing setup state, cancellation/back sequence, source selection and learning options |
| Study lesson | Compact goal/back/overflow; sources/listen/save/finish/delete actions | Existing lesson/session, source guards, delete confirmation and reading-stop callback; duplicate page thinking Orb suppressed under shell |
| Studio | Compact title/back/save/overflow; export/layers/versions/save | Existing offline project owner and exact revision callbacks; all original overflow actions retained |
| Office Tools | Compact selected title/back; new/open/graphics/Study/attach actions | Existing file/text/PDF owners; PDF Study routes to underlying source; batch receipts cannot start Study |
| Memory | Compact title/current section/back; add/saved/suggestions/history/settings | Existing memory filters, search, explicit suggestion approval, scopes and semantic owners |

Original headers remain available to standalone hosts. Save, undo/redo, brush, selection, actual content fields, and other editing-intrinsic controls remain in working content where appropriate. This does not establish that every nested subdialog or user-facing screen in the entire application has been audited.

## Historical checkpoint09 integration record

This report describes integrated source and scoped evidence, not full device acceptance or all-actions parity. Production/tests are frozen during checkpoint09; this update changes this document only.

- **Checkpoint08 full app suite:** 2,135 tests; **2,134 passed, 1 failed**. The remaining failure was an AssistantTools fixture mismatch. Do not label the full suite passing. Checkpoint08 was **not installed** on the phone.
- **Checkpoint09:** affected Home/shell/presence and AssistantTools checks plus both APK builds are running against source fingerprint `d0227355b68b51f211799ac4ff40ec87cfc262d5ce873c4d7e01411e080ec768`. Results remain **PENDING**; no successful build/test result or installation is inferred.
- Earlier checkpoint03/04 results are historical checkpoints, superseded for current source status by the above; they do not establish later repair acceptance. No builds or tests were run by this agent during this documentation update.

## Physical findings and pending repairs

Installed build05 exposed **Home draft typing loss**: a single injected `abcde` became `cea`, and separately paced letters became `cdeb`. The same input produced exact text in Library and Note control fields. This isolates the observed failure to the Home path under that comparison; it is not dismissed as generic Android injection behavior. No test message was sent for these checks. These results qualify installed05 only, not uninstalled08 or pending09.

The integrated09 repair gives the composer a synchronous, ephemeral `TextFieldValue` echo, preserving caret/composition across delayed StateFlow/shell projection updates. Canonical draft revisions acknowledge edits; stale echoes cannot roll them back, while newer owner clears/replacements remain authoritative. Home Send reads the current canonical snapshot and retains the original exact conversation/revision/literal admission. No new persistence owner, automatic send or synthetic revision is introduced. **Fix09 native typing/IME/lifecycle acceptance is PENDING** until the new APK is built, installed and checked. Authored burst-key and revision/caret regression tests are part of the pending affected gate; they do not prove the physical repair alone.

The user reported a **speech hang**. Installed05 showed `RESOURCE_ACQUIRE` with local recognition deferred because another heavyweight native model was busy. Actual speech acceptance is **FAILED/DEFERRED**, and the user is asleep: no further human voice prompts or voice-success claims. The integrated Home presentation repair scopes the current-task hero to canonical current-conversation runs while Bell/Activity retain global tasks. It replaces this specific resource-busy notice with actionable typing/wait copy and a Type instead focus action, preserving canonical failure diagnostics and avoiding an automatic speech retry. These are UI repairs, not a speech runtime/resource-scheduler fix.

## Home and projected-control repairs

- Home manual upward scrolling releases LIVE following; new output/recovery does not force a jump. Explicit submit/Return to current re-arms following. The submit epoch is transient `remember(draftKey)`, preventing restoration of an old positive epoch from overriding saved scroll lockout.
- Home composer, attachment/Tools actions and compact Chat/Screen popup use the shared utility projection. At fontScale >= 1.4 the shell composer uses its existing full-width field with actions on a separate row; standalone short-screen behavior stays with its original layout. Expanded text needs physical vertical-space/IME acceptance.
- `ShellUtilityProjection` captures the original composition locals. `RenderShellUtilityBand` restores them and keys content by navigation entry/current object. Page lifecycle, saveable/view-model owners and menu anchors consequently remain associated with the owning page; no callback executor is introduced.
- Studio/Office eligibility uses exact current navigation entry and route family, while their authoritative active selection supplies current object/version. Path-bound Note/Canvas retain strict route-object matching. Explicit Office context uses the active source, not an obsolete initial query object.
- Page-local dialogs, destructive confirmations, read-aloud callbacks and intrinsic Save/Undo/brush/editing controls remain with their original owners. Moving a control is not proof of natural-language execution parity.

## Kids fixes applied

The shell now allows only the existing `note`, `canvas`, and `study` child projections in Kids, retaining exact navigation-entry and route/object matching. These pages had hidden their local headers when the shell owned the Orb; accepting their projections restores their existing manual back/title/menu controls. Settings, Memory, Studio and Office root access are not added, and existing parent challenges and cloud/permission predicates remain.

Kids Study fallback now carries no adult Home catalogue request and dispatches directly through `navigateSafely("study")`. Kids Home does not collect the adult Home catalogue; the old request path was dead. Exact Study was already an allowed Kids route. Current-screen metadata remains unavailable in Kids; explicit source attachment and typed-message actions do not gain child authorization. Child voice-target continuity, native parent challenges and physical child-tool usability remain unverified.

## Current-screen semantic metadata

`CurrentScreenContext`, `BindCurrentScreenContext` and `halo_current_screen` publish/read the same accepted current-page projection under a resumed navigation-entry lease. Metadata includes route/suite/tool/current object, canonical sourceVersion when genuinely known, selected identifiers, enabled presentation action IDs and explicitly mapped canonical tools. UI action IDs are not executable semantic tools, and discovery does not grant their authority.

The output now includes **selectionCount** and **selectionTruncated**. At most 100 selected identifiers are returned; the full selection is privately copied for comparison. A change after identifier 100 invalidates the snapshot even when returned IDs/count are unchanged. Count, subset and truncation consistency are validated. Canvas and Notebook pass full owner selections; no subset is claimed to be the entire selection. Tests cover 130/150-item selection, same-count tail change, false truncation claims and absence of text/title content.

Unknown sourceVersion remains null. Studio supplies its real project revision; Office supplies canonical Workspace sourceVersion only where supported. UI hashes, titles, Note revisions and Study timestamps are not invented as Workspace versions. Metadata contains no document title, selected text, bytes or implicit attachment. Explicit `CurrentPageContextBinding` source selection remains a separate user action through its existing owner.

Remote metadata reads are now permitted only through the existing acknowledged **CLOUD** mode and both existing Screen/Workspace disclosure flags, with repeated host checks and the existing runtime/provider gates. Foreground/grown-up admission remains mandatory. This adds no new consent, mode transition, automatic read/upload or source-content grant. Authorization can be revoked before return; no metadata is returned after failed revalidation. Actual permitted remote-provider and spoken acceptance remain separate from source tests.

## Library typed view and guarded organization

The integrated `NotebookLibraryTools` and `NotebookOrganizationTools` are separate HostTools executors, preserving `ToolExecutionContext` dispatch. Each descriptor declares its actual capability, typed closed schema, versioned operability and replay/approval policy. They use the live `NotebookViewModel` and existing `NotebookOrganizationRepository`/Room DAO, not a second list engine, persistence store or generic UI callback registry.

`halo_library_view` exposes requested and rendered query/filter/sort, collection identifiers, explicit selection and bounded visible-row metadata. The owner's debounced resolved query is separate from raw input: pending search is reported unsettled rather than labeling old hits fresh. `halo_library_set_view` changes exactly one existing view setting through the actual VM: query, non-navigation filter, sort, collection filter or explicit visible selection. The view lease requires the exact resumed root Notebook entry, no blocking modal/menu overlay (suite carousels keep the visible Library owner), foreground grown-up local scope, a settled fresh snapshot and supported visible selections. State changes consume admission; stale entry/selection and replay are rejected. MEMORY navigation is not treated as an ordinary filter operation.

`halo_library_organization` explicitly captures organization state for a visible object and/or existing collection. `halo_library_organize` implements individual desired favorite/membership values and collection creation/deletion. Explicit target IDs must match the one-use organization token. Exact private source row, favorite row, collection list and relevant membership rows are compared in the same existing Room transaction as the write and readback. No title/content is returned from that private comparison. Missing or trashed source targets are rejected. Deleting a collection removes collection metadata/memberships, not its source objects.

Persistent organization writes require canonical **ALWAYS** approval, **NEVER_REPLAY**, and a nonblank runtime interaction ID. Immediately before the write, the current view/entry/access is revalidated. After suspension, exact entry/requested-view revision and foreground/Kids access are checked again before disclosing receipt IDs. Revocation after an actual effect or a post-dispatch exception yields a content-free **UNKNOWN** completion; it is not reported as a rejection/no effect. Ordinary coroutine cancellation follows existing runtime handling. These Library queries remain local-only.

Authored tests cover the actual VM/Room owner, pending search, selection and entry changes, stale/replayed admission, persisted favorite readback, concurrent source/favorite/membership changes, missing/trashed targets, collection deletion retaining source objects, no-interaction denial, descriptor capability/approval/replay requirements and a real persisted effect followed by revoked readback. The broader checkpoint08 app run reached 2,134 passes with one AssistantTools fixture failure; exact individual test attribution should use its saved reports. Latest Home presentation/editor changes and affected regressions remain **PENDING checkpoint09**. No native or spoken acceptance is inferred from those source tests.

## Remaining Library semantic gaps and nearest owners

The new Library adapters explicitly reject mixed batch lifecycle, Undo and purge operations; these remain implementation gaps, not justified parity exceptions. They do not claim to cover every menu, import/export or creation operation.

| Unsupported operation in the new Library adapter | Existing owner to preserve for subsequent guarded integration |
| --- | --- |
| Mixed selection Context | `NotebookViewModel.applyToSelection(CONTEXT)` and `WorkspaceUseCases.select`; explicit context admission/budget must remain, not organization SQL |
| Mixed Archive/Trash/Restore | `NotebookViewModel.applyToSelection` and per-object `WorkspaceUseCases.archive/trash/restore`; conversations additionally require `RoomConversationStore.trashSavedConversation/restoreSavedConversation` with their expected-version/admission paths |
| Undo last Trash batch | `NotebookViewModel.undoLastTrash`, its exact successful Trash ID set and original per-object restore owners; do not infer Undo from arbitrary current selection |
| Permanent note deletion | `NotebookUseCases.purgeNote/purgeStatus` and the existing note purge repository/receipt owner, including source/recovery/prior-receipt versions and attachment cleanup truth; never direct organization-table deletion |

Original UI confirmations and conversation-active-state protections must survive any future typed adapter. Per-object operations need current source versions and exact selection admission; do not implement a mixed SQL lifecycle shortcut. A collection operation does not authorize a document lifecycle change.

## Remaining acceptance

**NOT QUALIFIED / pending current-build acceptance:** phone portrait/landscape, large text, IME, typing integrity and back handling; Home manual-follow behavior over real streaming/recovery; nested menu/dialog anchors across object/route replacement; Kids child controls and parent challenge; actual typed runtime approvals; real STT-final spoken execution (user-reported hang, FAILED/DEFERRED); permitted remote-provider metadata read; exhaustive action parity and visual acceptance. No issue closure claim is supported by this document alone.

## Delivery recovery update

Checkpoint12 full app regression passed **2,150/2,150**, zero failures/errors/skips, with both APKs built. The main action list now uses only the current page actions; fixed destinations and standalone/Mini compatibility retain their original owners. Duplicate Note/Canvas utility actions are removed only when the identical enabled carousel action is available; compact/large-font headers and unique read-aloud controls remain. Library search has a persistent accessible input label. Saved-chat fixture collectors stop before database teardown, preserving every original version/data assertion.

An existing read-only headless Android17/API37 emulator is being used for independent silent checks. Setup permissions, provider keys, model downloads and device audio were skipped through actual optional UI. It exposed inherited scroll between setup stages; the only production change after full12 keys the saveable scroll state by setup stage while preserving form composition/drafts. Checkpoint13 recompiles both APKs; emulator verification is pending. This is not a fresh phone or human speech qualification. The owner confirmed the phone is unavailable for now.

Final coding checkpoint: full12 passed2,150 cases with zero failures/errors/skips;13 rebuilt both APKs after only the setup scroll-state key change. The owner then deferred further tests. Temporary emulator stopped; final phone/native/human acceptance remains pending. See the authoritative delivery ledger and machine-readable manifest for exact source/APK attribution.
