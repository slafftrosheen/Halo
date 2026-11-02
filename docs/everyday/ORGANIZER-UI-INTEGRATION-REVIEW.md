# Organizer UI integration review

Status: Independent bounded integration review; the four prior residuals and retained task end-time defect are addressed in the corrected frozen source. Matching corrected host tests, native geometry, participant and overall issue-63 acceptance are not established.

Reviewed 1 November 2026: `ui/everyday/OrganizerDraft.kt`, `OrganizerViewModel.kt`, `OrganizerEditor.kt`, `OrganizerScreen.kt`, `EverydayComponents.kt`, and the add-only import path in `RoomOrganizerRepository`, `OrganizerModels` and `OrganizerIcs`. Supporting reads checked the existing recurrence, delivery, Workspace export and Material3 owners. Source changed during this review; the dispositions below explicitly identify corrections inspected after the first findings. Line numbers refer to that inspected working tree, not a frozen acceptance manifest.

The reviewed code uses the existing organizer repository and its record-version comparisons, retains an editor base in saved state, keeps imported alarms inert, imports records and overrides atomically, and separates saved-item wording from scheduling status. These are useful foundations. The following concrete issues remain within this batch; unfinished routes, translations, other pages, tools, widgets and backup are not counted as regressions here. The reviewer made no production/test edits, ran no builds or tests and performed no phone operations.

## Findings and source disposition

### OU01 — P1: occurrence editing must preserve the exact occurrence meaning

**Partly addressed; timing residual open.** The initial `OrganizerViewModel.edit/save` constructed a new REPLACED override, losing a completed occurrence's disposition, imported source-URL override and replacement time-zone semantics. The inspected correction at `OrganizerViewModel.kt:115–127,140–143` loads the canonical master and existing override, keeps the master version, and copies the existing override when saving. `OrganizerDraft.kt:11,52,64` also persists the original override. Completion and source-URL preservation are addressed at source level.

However, `edit` still rebuilds the replacement start/end from effective occurrence instants in the current device zone, then assigns the original authored zone. For a floating Europe/Riga replacement on 29 March 2026, 03:30–05:30 has one hour of authored elapsed duration. In America/New_York the effective occurrence is 03:30–04:30. A title-only edit reconstructs Riga 03:30–04:30; both resolve to the same instant at the DST gap and validation rejects the previously valid event. Related first-time overrides can change the original series duration at a different DST boundary.

Preserve the exact existing replacement time when its fields have not changed. For a new override or an intentional time edit, explicitly retain or review the chosen elapsed/calendar duration rather than deriving an authored end from an unrelated device zone.

Acceptance: edit only the title of an imported moved occurrence with a source URL, a completed recurring task occurrence, and floating timed replacements across different device/author DST rules; restore the draft before saving. Verify completion, original occurrence identity, URL, authored timing, duration and master version remain intact. An external writer must still cause a conflict.

### OU02 — P1: metadata edits must not rewrite recurrence limits or reset history

**Addressed in inspected source; tests pending.** Initially `OrganizerDraft` reduced exact UNTIL to a date and rebuilt end-of-day on every save. A title-only edit could extend an imported series or trigger the repository's recurrence-conflict path. `OrganizerDraft.kt:36–40` now retains the exact original UNTIL when the displayed date and authored zone have not changed.

The reset switch promises to reset individual changes *if the series schedule changes*. The original repository cleared overrides whenever the switch was checked. `RoomOrganizerRepository.kt:42` now additionally requires an actual time/repeat change. This closes the reported silent-history-loss mechanism at source level.

Acceptance: title-only save of a timed UNTIL ending before the final day's occurrence keeps the exact bound and occurrence set. Checking Reset during a title-only edit keeps all overrides. An actual schedule change without Reset conflicts; the same reviewed change with Reset clears exactly that series' overrides.

### OU03 — P1: Keep editing does not restore a sheet already dismissed by Material3

**Partly addressed; sheet-state residual open.** Initial editor dismissal directly cleared the saved draft. `OrganizerEditor.kt:28–30,120–123` now asks before discard and preserves the draft, but `EverydayComponents.kt:16–20` still uses the default `ModalBottomSheet` state. Material3 hides/settles the sheet before invoking `onDismissRequest`; Keep editing only clears the confirmation flag. The editor remains composed with the same hidden state and is not explicitly shown again. Ignoring dismiss while busy has the same hidden-editor risk if the action then fails.

This control flow was checked against the locally cached Material3 1.4.0 primary source: `ModalBottomSheet.kt:151–165,181` hides before calling the callback; its automatic show effect at line 213 is keyed by the sheet-state object. No UI execution is claimed.

Own the sheet state: prevent a dirty/busy transition to Hidden until the decision is resolved, or explicitly show the same editor after Keep editing. Preserve entered text and the original version.

Acceptance: text entry followed by scrim tap, swipe dismissal and system Back each offers Discard/Keep editing; Keep editing returns to the visible, editable same draft. Repeat during a suspended save that subsequently fails. Explicit Discard clears the saved draft, and configuration/process restoration retains an undecided edit.

### OU04 — P1: calendar export has no durable, source-bound external-copy outcome

**Open.** `OrganizerScreen.kt:66–67,89` launches an unbound CreateDocument picker. `OrganizerViewModel.kt:202–207` then reads all current events/overrides after the picker returns, writes directly to the destination, and reports success after stream close. There is no reviewed source snapshot/version, immutable picker identity, verification read, persisted write phase or read-only recovery. A changed calendar can be included without the original selection being retained; provider partial writes or interruption leave a file whose state the UI cannot explain or verify.

Reuse the existing `data/workspace/WorkspaceExternalExport*` outcome discipline or provide the equivalent organizer owner: prepare immutable reviewed bytes/identity before handoff, fence the returned picker result, persist destination and uncertain phase, distinguish verified copy from written-but-unverified/unknown, and recover by checking rather than replaying the write. Preserve cancellation truth.

Acceptance: edit/remove/add events while the picker is open; deliver an old picker result; cancel; deny read-back; fail after partial write; recreate/kill the owner during write. Each resulting notice must match the actual bytes and reviewed source, and uncertainty must not silently trigger another write.

### OU05 — P2: URI-scoped import identities lead to duplicates with no export recovery

**Open.** `OrganizerViewModel.kt:187–193` passes the selected file URI as `calendarId`. `OrganizerIcs.kt:75–76` derives the local identity from calendarId plus UID, while `RoomOrganizerRepository.kt:266–267` skips only matching local IDs. The same calendar imported from a copied/renamed/provider-different URI therefore adds a second local version of each UID. The form/result describes existing IDs as kept without explaining this namespace boundary.

The existing ICS exporter correctly rejects duplicate effective UIDs, but this page always exports all events (`OrganizerViewModel.kt:202–205`) and catches its actionable explanation as a generic validation error. It offers neither separate calendar/export selection nor the guard's suggested recovery. The user can end up unable to export any calendar without deleting a valid copy.

Make manual import target identity stable and visible, or explicitly detect/review existing UIDs according to the chosen calendar namespace. Preserve legitimate source-separated records; do not silently overwrite them. Add a real selectable export scope and retain the duplicate-UID recovery explanation if multiple versions remain.

Acceptance: import twice from the same URI, the same bytes from a different URI, and two intentionally different calendars sharing a UID; include existing items in Trash. No overwrite/resurrection occurs. The result accurately identifies added/kept versions, and the user can export an unambiguous selected calendar without deleting another.

### OU06 — P1: adult operation authority is checked only before suspension

**Open.** `OrganizerViewModel.kt:92–101` checks Kids once before invoking a suspend action. Save can suspend on linked-note reads, import can suspend on file IO, export can suspend on repository reads/byte generation, and mutation methods themselves suspend. A switch to Kids after that check does not invalidate the admitted operation before it writes or publishes. The screen hides adult controls and clears drafts, but that does not cancel/fence the active mutation or external write.

Capture the live parent/entry authority for the operation and revalidate it at the actual repository transaction or external-write boundary; cancel pending adult work on authority loss and prevent a late completion from republishing an adult preview. Keep committed outcomes truthful if revocation occurs after an irreversible effect.

Acceptance: barrier-test a profile change while each of linked-note validation, import IO, transaction admission and export preparation is suspended. Resume the barrier and verify there is no newly admitted adult mutation/write, no leaked preview and no false success. Include restored picker results under Kids.

### OU07 — P2: delivery history and timer labels lose the actual outcome

**Open.** `OrganizerViewModel.kt:80` retains only receipts matching the current record version. `RoomOrganizerRepository.dismissDelivery` deliberately saves DISMISSED on the delivered version, then increments the record version for a one-shot alarm/reminder/timer (`:210–215`). The completed item's real dismissal therefore disappears from the page; it falls back to the generic scheduling-preparation/no-future-alert text (`OrganizerScreen.kt:165`). A metadata edit similarly hides consumed historical outcomes.

Also, the timer detail at `OrganizerScreen.kt:123–125` calls any active timer with nonnull startedAt Running, including remaining=0 with FIRED, FAILED, NOTIFICATIONS_BLOCKED or MISSED. The delivery owner intentionally keeps these outcomes separate from task status.

Show current scheduling separately from explicitly version-labelled terminal history. Derive timer presentation from remaining time plus its current receipt: counting down, due/alerting, blocked/failed/missed, snoozed or finished as supported; do not imply a positive countdown is still running at zero.

Acceptance: timer reaches zero with allowed, blocked, failed and late delivery; dismiss a timer and a one-shot reminder; edit a delivered repeating item's title. The correct terminal outcome remains visible without representing an old schedule as current.

### OU08 — P2: editor relationships depend on truncated or unrelated search results

**Open.** `OrganizerViewModel.kt:81–82` takes only the first 200 active notes before the editor's Find a note filter. `OrganizerEditor.kt:105–112` then labels any linked note missing from that window unavailable, even if it exists and is active. Valid notes outside the window cannot be found. Parent choices at `OrganizerEditor.kt:99` and subtask rows at `OrganizerScreen.kt:145` use `state.records`, which has already been filtered by the outer page search (`OrganizerViewModel.kt:85`); a search matching the selected parent can hide its subtasks or the editor's current parent relationship.

In addition, `OrganizerViewModel.kt:135–137` requires every existing linked note to remain active before any save, including occurrence edits whose link controls are hidden. Moving an old linked note to Trash can therefore block an unrelated occurrence title edit with no in-form repair, although the organizer still retains the record and its reference.

Use separate canonical relationship/detail data from page search results. Resolve linked/current parent IDs directly, and apply note search in the repository before a clearly bounded/paginated result limit. Do not describe an unqueried record as unavailable. Validate newly chosen links without making an unchanged unavailable reference an unexplained obstacle to an unrelated edit, or expose a clear, deliberate repair.

Acceptance: more than 200 notes with the linked note beyond the first page, search for that note, an outer query matching only a parent, a child whose current parent is not a page-search match, and an occurrence edited after its linked note moves to Trash. Existing relationships remain visible, findable and unchanged unless deliberately edited; unavailable references have an actionable recovery.

### OU09 — P2: Agenda date rejects ordinary partial typing

**Open.** `OrganizerScreen.kt:204` immediately parses every keystroke and updates the controlled field only when the entire date is valid. Erasing a digit or clearing the field produces no accepted state, so the previous complete date is immediately restored. The visible editable field cannot support ordinary erase/retype input even though the date picker works.

Keep a local/saved raw input draft with validation and commit a valid date deliberately, or use a read-only date display with an explicit picker. Preserve the visible date and field error during incomplete input.

Acceptance: select-all, erase, type a valid replacement date one character at a time, enter an invalid date and then correct it, and use the picker after partial input. No unexpected navigation/date change or input reversal occurs.

### OU10 — P2: child-visible controls do not consistently retain 56dp targets

**Open.** The page correctly gives item rows/Open item a 56dp child minimum, but shared filter chips (`OrganizerScreen.kt:199–200`), date choice (`EverydayComponents.kt:43`) and Previous/Today/Next (`OrganizerScreen.kt:205–208`) retain their default smaller targets. Other child-visible Back/agenda controls use default TextButton sizing. Material defaults do not meet the project's explicit 56dp child contract.

Pass the live child profile into shared controls or apply the child minimum locally to every exposed action, including date-dialog actions. Preserve wrapping and avoid growing hidden adult controls into the child projection.

Acceptance: measured child semantics/touch bounds for all actionable controls at normal and 200% text in EN/RU/LV/PL; keyboard/landscape and long labels remain reachable. This is a source finding; no geometry measurement has yet been performed for these new pages.

### OU11 — P2: a failed refresh can be reported as a failed durable action

**Open.** `OrganizerViewModel.kt:96–100` places `action(); refresh()` in one catch block. A durable save/import/delete can complete, then a subsequent read fail; the UI reports that the action could not finish and offers retry even though the change has already committed. Actions without a distinct success notice are especially ambiguous. Generic exception mapping also suppresses specific recovery conditions such as pending schedule cancellation before purge or duplicate calendar UIDs.

Record the mutation outcome before attempting projection refresh. Report a refresh failure as a read/display problem with a read-only retry, retaining the committed receipt and selected identity. Map known domain failures to purposeful recovery messages rather than treating all IllegalArgumentException cases as date/zone mistakes.

Acceptance: inject a read failure immediately after successful save, import, delete and timer action; verify the committed outcome remains known, no mutation is replayed by Retry, and recovery refreshes the same identity. Verify purge-pending and duplicate-UID export explain their actual recovery.

## Evidence and next boundary

All findings above are source-derived. OU02 and the stated portions of OU01/OU03 have inspected corrective source, **not matching test evidence**. Existing organizer foundation/delivery host results concern their recorded earlier source and do not validate these new forms, import transaction or picker flows. No numerical UX/UI score is assigned to this unfinished batch.

After the parent freezes the corrected integration, bind its focused behavioral tests, restored-state tests, child/large-text renders and later native/device results to that exact source. Overall issue-63 completion still requires its other workstreams and documented acceptance; human participant/assistive outcomes remain separate.

## First integrated-page source follow-up

Status: Independent bounded source follow-up of the frozen `everyday-pages-core-source.json` batch, captured at 21:38:13 UTC on 1 November 2026. The parent's first host run is pending. No build, test execution, device operation, production edit or numerical score was performed by this reviewer. Localization is explicitly outside this boundary.

The follow-up also read Sources/SourceSettings/CollectorContent, EverydayBrief, WorldClock/OfflineCityPicker, EverydayRoutes, the new AppShell/Screen/Orb wiring and the relevant repository authority hooks. The following table supersedes the earlier source dispositions; it does not turn inspected regression source into passing evidence.

| Finding | Current source disposition |
|---|---|
| OU01 | **Source-addressed.** `OrganizerDraft.forOccurrence:62–77` preserves an exact replacement or derives a new authored-zone start/end using the original elapsed/all-day duration. The canonical master version, override completion and URL are retained. `EverydayEditingTest:44–65` covers restored floating replacements and first overrides across travel; not executed by reviewer. |
| OU02 | **Source-addressed, unchanged from preceding follow-up.** Exact UNTIL and conditional override reset are covered by new regression source at `EverydayEditingTest:33–42`. |
| OU03 | **Source-addressed.** `EverydaySheet` now owns its state and vetoes Hidden while forwarding the dismissal request, so Keep editing does not depend on reopening an already-hidden sheet. Dirty/Busy gesture behavior still needs rendered execution. |
| OU04 | **Main external-write mechanism addressed; preparation/review residual below.** Selected event versions produce a managed Workspace file; `WorkspaceExportButton` reuses the existing verified, journaled external-copy owner. A fixed file prevents later organizer changes from silently changing picker bytes. |
| OU05 | **Source-addressed.** Manual imports use `halo:manual-import`; the transaction additionally deduplicates retained UIDs including Trash. Export now has explicit event/version selection, a 200-item UI cap and duplicate-UID guidance. New regression source covers copied input and Trash without resurrection. |
| OU06 | **Organizer/collector/clock/settings transaction mechanisms addressed; export-manifest residual below.** A monotonic Kids action epoch survives profile round trips. `EverydayWriteAuthority` propagates its checker across suspension; repository mutation hooks revalidate it. New tests exercise a mid-import rollback and revoked epoch. This does not yet establish a passing full authority matrix. |
| OU07 | **Source-addressed.** Receipts are retained, earlier revisions are labelled, and only current ringing receipts offer actions. Zero timer presentation now consumes actual receipt state. |
| OU08 | **Partly addressed.** All active note candidates are loaded before searching; chosen links are sorted first; parent choices use independent canonical rows plus a search; occurrence edits no longer validate hidden note controls. **Subtask detail rows still use the outer search-filtered `state.records` at `OrganizerScreen.kt:157`.** A query matching the parent but not its children continues to hide its subtasks. Use a separate full child projection, including completed children as appropriate. |
| OU09 | **Source-addressed.** Agenda uses a saved raw `dayInput` while committing parseable dates; partial typing no longer reverts immediately. |
| OU10 | **Partly addressed.** Main organizer chips and day navigation now have 56dp bounds. Child-visible helper/dialog actions still use defaults: `EverydayDateField` Apply/Cancel, `OfflineCityPicker.kt:38,47` Retry/Cancel, and `EverydayBriefScreen.kt:85,102,125` Retry/Open organizer/Freshness and sources. The standalone Back variants also remain 48dp/default. Apply the child target rule through the shared surfaces and measure them. |
| OU11 | **Post-commit refresh distinction addressed; typed recovery residual remains.** `OrganizerViewModel.kt:110–111` now reports a completed action followed by a failed read separately. Known domain conditions still collapse at `:114–116`: pending cancellation before purge and duplicate-UID export are not date/time errors. Preserve their specific recovery in the relevant form. |

### OU04/OU06 residual — P1: export manifest is committed outside validated file publication

`OrganizerViewModel.prepareCalendarExport:209–224` creates a real Notebook note containing the event manifest at line 216 before `createManagedFile` performs its source-version/authority validation at lines 219–221. `NotebookRepository.createNote:55–74` has no Everyday authority hook. File validation failure, profile revocation, cancellation or an event revision change can leave this extra note even though the export operation reports failure; it is outside the managed-file publication transaction and cleanup.

Create the manifest and file under one owner transaction with the reviewed event/authority validation, or retain an explicit partial-result record and safe compensation. Do not leave a failed attempt as an unexplained ordinary note. Barrier-test revocation/version conflict before manifest insertion and before file publication, and verify all durable artifacts and the reported outcome agree.

The export review also retains old `id@version` entries in `exportVersions` while rendering only current keys (`OrganizerScreen.kt:275–284`). After a selected event changes or disappears, Save can stay enabled although no visible row is selected; the rejected old selection cannot be individually deselected. Show/reconcile stale selections and provide a clear re-review action before preparation. Test a changed and deleted selection without dismissing the entire review.

### OU12 — P2: deferred refresh receipt is discarded by the page

`SourcesViewModel.kt:69–74` calls `refreshDetailed` but keeps only a generic notice. The coordinator intentionally returns an ephemeral deferred attempt/nextAllowedAt at `CollectorCoordinator.kt:102`; cooldown/quota denial need not replace the repository's previous successful attempt. The screen observes only that repository state, so it can instruct the user to check status below while still displaying the prior CURRENT/success result and old next-allowed time.

Retain the returned receipt with its source/version and display the actual deferred reason and eligible time without changing successful retrieval/verification timestamps. Clear it on a new relevant action or source change. Test cooldown, shared-endpoint Retry-After, quota denial and blocked refresh after a successful cached result; no false new success or timestamp should appear.

### OU13 — P2: Add feed reopens into the prior success event

`SourcesScreen.kt:194` dismisses AddFeedSheet whenever the persistent notice equals the add-success text. That notice remains after the first successful addition, so opening Add feed again launches the effect with the old notice and immediately closes the new form. Use a consumed operation ID/event or reset the form's completion state when opening; do not use durable display copy as an event identity. Acceptance: add two feeds consecutively on the same page, including a failed second save and restoration.

The Add feed form at `:195,226` also dismisses entered URL/title immediately on scrim/Back/Cancel, unlike the corrected organizer and SourceSettings editors. Preserve a draft or use the same explicit discard decision for meaningful entered content. The earlier source-settings base/version preservation does not cover this separate form.

### OU14 — P2: brief retry and restoration lose the requested document

`EverydayBriefViewModel.watch:37–44` clears `document` while reading an ID; `retry:73` then builds a new ON_DEMAND brief whenever that read failed. A transient historical read failure thus changes the document and creates new history instead of retrying the requested saved brief. The currently opened history ID is also only held by the observation job; the ViewModel takes no SavedStateHandle, so process restoration returns to the original route/default recent brief.

Persist the requested/current document ID independently of the loaded value. Retry that read, retaining an honest missing state if the exact brief was pruned; keep Build new brief a distinct explicit action. Test transient error then Retry, missing historical ID, and process restoration after selecting history.

### OU15 — P1: existing Kids sharing can become unreachable for revocation

`EverydayBriefScreen.kt:187` exposes only `records.take(100)` in the priorities/Kids-sharing form. The settings sets persist selected IDs independently. After more records are created or reordered, an already-shared record can fall outside that first 100 and remain shared while its toggle is no longer reachable; there is no search, selected-only view or clear-sharing control.

Always render or otherwise expose every currently shared/pinned ID, including an explicit unavailable record entry, and offer searchable/paginated candidates. The 100-selected limit must not mean first-100-records-only management. Test a shared record beyond the first 100, an unavailable previously shared ID, and an external order change; the parent can inspect and revoke each current grant without deleting unrelated records.

### OU16 — P2: proactive notice opens whichever brief is latest

`AndroidAttentionHost.kt:88` uses the same PendingIntent request code 8110 and bare `screen/brief` for every brief notice, dropping the document ID already present in `request.id`. `EverydayBriefViewModel.kt:28–31` resolves that bare route to a recent document or builds a new one. Opening a morning notice after generating an evening/on-demand brief therefore does not open the notified saved document.

Carry a validated document identifier in a distinct pending-intent identity and route it through a narrow typed MainActivity entry path. The current generic handler (`MainActivity.kt:330–340`) accepts only catalog keys, so appending an arbitrary query string alone is insufficient. Test two outstanding notices, later history creation, process restart and a pruned notified document; each tap opens its own exact brief or an honest unavailable state.

### OU17 — P2: Changes view misstates an empty comparison

`EverydayBriefScreen.kt:99–103` maps a successful CHANGES comparison to changed facts, then calls an empty result “No selected information is available” and suggests choosing sources. A valid baseline with unchanged selected information has a different outcome: nothing changed. Changed rows also omit ADDED/UPDATED distinction, while the removed count at `:119–120` says “local plan” for all removed fact kinds, including public-source items.

Render the comparison outcome and change kinds explicitly, with an unchanged-since-baseline state and source-appropriate removal wording. Test zero changes with a valid baseline, added/updated facts, only removals, and missing comparison sources; do not conflate empty comparison, empty data and unavailable baseline.

### OU18 — P2: saved calendar reader drops cancellation and occurrence context

`CollectorContent.kt:80–91` renders only master records with their original start, title and details. It does not disclose `status=CANCELLED`, recurrence or saved exceptions. `EverydayRoutes.kt:33–39` preserves article identity but routes a calendar-occurrence brief fact to the whole source with no occurrence identity. A moved/cancelled series can consequently be presented as an ordinary original-date event, and a brief link loses the particular occurrence the user selected.

At minimum label master series/cancelled status truthfully and expose their saved occurrence changes. For a selected calendar fact, resolve and show the exact current saved occurrence or an unavailable/changed outcome, rather than silently substituting the master. Test a cancelled series, a moved occurrence with its own title/details/URL, and a refresh that removes the selected occurrence.

### OU19 — P2: weather timestamp and displayed zone disagree

`CollectorContent.kt:20,54–55` formats weather `observedAt` in the phone zone, then displays the weather city's zone underneath. For a Tokyo forecast viewed in Riga this visually associates a Riga clock time with Asia/Tokyo. Format that forecast timestamp in the selected place's zone, or explicitly label the phone-zone time. Test different phone/city zones and date boundaries; daily date labels must remain in the same intended city calendar.

### OU20 — P2: reminder dialog validates a new snapshot instead of the reviewed article

`SourcesScreen.kt:169–182` retains an `ArticleFact` for its displayed title/publisher but takes `snapshot.contentRevision` from the live `current` source at Save. If a background refresh replaces the article with the same ID, the dialog still shows the old content while `SourcesViewModel.remindArticle:103–109` validates the new revision and creates a reminder from the new article.

Capture source ID, source version/content revision and article identity together when opening the reminder form. If the source changes, require an explicit refreshed review; do not silently retarget the saved reminder. Test a same-ID title/URL update while the dialog is open and a removed/deselected source. No source content is fabricated, but the user's reviewed context must match the committed result.

No additional concrete world-clock storage/CAS defect was found in this bounded pass. Its native geometry, child picker targets, standalone routes and rendered recovery remain unverified. This addendum records source dispositions and actionable issues only; it is not acceptance of the ongoing host run or broader issue-63 scope.

### First integration build boundary and subsequent edits

The reviewer read `everyday-pages-core-results.json`: **BUILD FAILED in 1m 50s; JUnit NOT_RUN; 111/111 source files matched at archive**. Production compilation stopped at missing `ToolZoneHeader` imports in the four page files. No passing result is inferred from older XML or from the new test source.

The parent ended that freeze and archived the result before applying imports and further review corrections. A later reviewer comparison found current page files different from that archive; these are subsequent edits, **not an archive-integrity failure**. A direct read of the archived/current page differences showed import repairs and the beginnings of stale-export selection, subtask projection, deferred-refresh receipt and per-add completion corrections. The parent also reports atomic managed-copy publication and exact brief retry changes in progress. These post-archive corrections await their next coherent source review/verification boundary; the open findings above describe the inspected first integration snapshot and should not be mistaken for a claim that the evolving latest source is unchanged.

## Second frozen source review

Status: Independent bounded source re-review; matching tests and native geometry pending. No overall issue-63 or numerical UX/UI acceptance is claimed.

Boundary: `everyday-pages-review-source.json`, captured **2026-11-01T23:53:40.310952+02:00**, with its source ZIP. The reviewer independently checked **127/127 current source hashes matching, zero mismatches**, before the re-review. The parent's build/test session was still running. The reviewer read `EverydayEditingTest`, `EverydayCalendarExportTest` and `EverydaySourceReviewTest` as source only and performed no build, test execution, device action or implementation edit.

The following supersedes prior open source dispositions for this snapshot:

| Findings | Bounded source disposition |
|---|---|
| OU01–OU03 | Addressed for the reported occurrence, recurrence and sheet-dismissal mechanisms; prior corrections remain intact. |
| OU04–OU06 | Addressed for the reported export/import/mutation mechanisms. `createManagedFileWithPreparedSource` validates before preparing the manifest, after preparing it and before committing, within the same Room transaction as the managed-file rows. Failure cleanup preserves only referenced bytes. The organizer supplies authority and exact event-version checks. Stale export selections now show a clear-selection/re-review state and disable publication. Stable manual namespace/UID deduplication and Kids-epoch mutation fences remain present. |
| OU07–OU09 | Addressed for their reported mechanisms. Receipt history and zero-timer status are retained; `subtasks` is now derived from full scoped rows independently of page search; note/parent choices and incomplete date input retain their earlier fixes. |
| OU10 | The named app-owned controls now have 56dp bounds, and the shared sheet provides a 56dp Material minimum. This is source disposition only: internal platform/Material controls, dialog reflow, keyboard reachability and actual child target geometry still require rendered/native measurement. |
| OU11 | Addressed for the organizer's reported post-commit/read distinction and known recovery reasons. `ScheduleCancellationPending` and `DuplicateCalendarUids` now receive specific explanations. The brief auxiliary retry consequence is recorded below under OU14. |
| OU12 | Addressed in source. Manual refresh stores its returned transient receipt; the source page uses it only for the same source/version and invalidates it when the observed canonical row changes. Admitted=false no longer becomes new success. |
| OU13 | Ordinary repeated-add and dirty-dismissal mechanisms addressed; restoration/commit residual below remains. Add feed now distinguishes a new completion sequence and asks before discarding meaningful fields. |
| OU14 | Exact requested brief ID is saved independently of the loaded document and used by Retry. Auxiliary-read recovery remains open below. |
| OU15 | Addressed in source. All chosen/shared IDs remain listed, unavailable IDs can be revoked, candidates are searched before the limit, and all organizer sharing can be cleared deliberately. |
| OU16 | Typed ID validation, distinct PendingIntent data and MainActivity/claim admission are addressed. The live singleTop navigation boundary remains open below. |
| OU17–OU19 | Addressed for the reported mechanisms. Changes now distinguishes unchanged/partly unavailable comparisons and added/updated/removed results. Calendar selection carries original occurrence identity, content revision and zone, rejects changed/cancelled selection, and displays series/override context. Weather timestamps use the selected city's zone. |
| OU20 | Immutable article/source/version/content review is captured on opening and validated inside the same Room transaction as reminder creation. A mute-scope residual remains below. |

### OU13 residual — P2: restored Add feed can repeat an already committed creation

`SourcesScreen.kt:197–208` restores the form fields and its outer `adding` flag but initializes `openingSequence` with plain `remember`. `SourcesViewModel.kt:100–105` generates a new random source ID on every Save and keeps completion sequence only in memory. If the activity is recreated after the source commits and its sequence increments but before the dismissal effect consumes completion, the restored form captures that new sequence as its baseline. It remains open with the already-saved values; another Save creates another source. A process interruption after commit has the same underlying missing operation identity.

Retain a saved draft/operation identity and its consumed completion boundary. Re-resolve the same source after uncertain completion rather than retry creation with another UUID. Acceptance: suspend completion delivery after commit, recreate the page, then restore/continue; exactly one source exists and the form shows the actual saved/unfinished outcome. Include both rotation and process restoration.

### OU14 residual — P2: Retry does not repair failed history/settings reads

`EverydayBriefViewModel.kt:64–69` loads history, settings and organizer-sharing candidates separately from the visible document. If this auxiliary read fails, `retry:75` only calls `open(requestedId)`, which starts the document observer. It never retries `refreshAuxiliary`. A successfully reopened document can therefore clear the error while history remains falsely empty or the enabled Brief settings action has no settings object and opens no form.

Retry the same requested document **and** the failed auxiliary projection as read-only work, preserving any already-loaded document. Keep auxiliary error/loading distinct from a known empty history. Acceptance: fail `recent` or another auxiliary read after a document loads, then Retry; the same document remains selected and history/settings/sharing controls recover without generating a brief or mutating settings.

### OU16 residual — P1: singleTop reuses the old brief/organizer ViewModel

`AppShell.kt:216–219` uses `launchSingleTop=true` for these explicit destinations. The brief ViewModel at `:633–635` is keyed only by `entry.id`; its route argument is consumed only in the constructor. Android Navigation **2.10.1** keeps that entry ID and ViewModelStore when replacing a singleTop entry's arguments. Thus tapping a different exact brief notice while the brief page is already on top reuses the old `requestedId` and can still display the wrong brief despite the now-correct PendingIntent. The organizer route has the same constructor-only `initialId/selectedId` pattern.

This was verified from the locally cached primary sources: `NavControllerImpl.kt:1234–1279` replaces the top entry via `NavBackStackEntry.create(oldEntry, newArgs)`; `NavBackStackEntry.kt:251–267` explicitly retains the old ID and ViewModelStore provider. No navigation execution is claimed. The current PendingIntent regression verifies distinct intents and route claiming, but stops before the actual NavHost/VM boundary.

Deliver a deliberate new target to the live owner or create a distinct explicit-target back-stack entry; preserve any dirty organizer draft for a deliberate decision/Back return. The parent has proposed distinct entries after the active run is archived. Acceptance: morning→evening notice with the brief destination already topmost; re-enter the original ID after selecting another history item inside the page; open a second organizer record while the first has a dirty draft, then Back. Assert actual loaded IDs and preserved edits through saved-state restoration.

### OU20 residual — P2: an unchanged muted source cannot create an explicit reminder

`ArticleReminderReview.kt:5–10` rejects `state.source.muted`. Yet `SourceSettings.kt:52–58` defines mute as exclusion from briefs/proactive suggestions, and `CollectorContent.kt:33` still exposes Remind me about this article for the saved reader. Opening an unchanged, already-muted feed and saving that explicit reminder always produces the “article changed” recovery; reopening cannot fix it. This is a new restriction introduced by the review guard, not a source-identity mismatch.

Keep the stated mute scope consistent: allow this explicit manual operation when its reviewed identity and otherwise-required authority remain valid, or expose a deliberate truthful unavailable capability instead of an impossible changed-article recovery. Acceptance: open an already-muted selected feed and create a reviewed manual reminder; separately change source version/content while the form is open and verify that stale review is still rejected. Brief/proactive exclusion must remain unchanged.

No other concrete residual was identified in this bounded re-review of OU01–OU20. That statement does not establish complete issue-63 implementation, successful compilation/tests, native layout/accessibility or participant outcomes. The four residuals above remain open until the parent applies and verifies their corrections after the current frozen run.

## Corrected frozen source review — 2 November 2026

This narrow follow-up supersedes the four open source dispositions above; the earlier findings and failed evidence remain recorded. The reviewed boundary is `docs/testing/artifacts/HALO-EVERYDAY-63-2026-11-01/everyday-pages-corrected-source.json`, captured at `2026-11-01T22:03:44.053594+00:00`, with its corresponding source ZIP. The reviewer independently compared all **130 current files with the manifest: 130 matched, zero mismatches**. The parent reports build session 34847 running; no result from that run is claimed here. Review activity was limited to source, test-source and artifact reads, plus this document update.

### Previous run retained as failed evidence

The independently read `everyday-pages-review-results.json` records **BUILD FAILED in 5m 17s: 101 tests in 14 suites, 99 passing, 2 failures, zero errors/skips**, with **127 frozen source files matched**. Production and test compilation succeeded. `EverydayEditingTest.floatingDstReplacementSurvivesTitleEditAndDraftRestoration` exposed a real retained-metadata defect: a TASK replacement's `end=05:30` became `null`. `ShellOrbCatalogTest.kidsMenuCannotExposeAdultDestinationsAsUsable` still expected only Home/Study and omitted the deliberately scoped organizer, clocks and brief destinations. The former requires the source correction reviewed below; the latter is an explicit catalog-expectation update. This archive is not a passing integration boundary and is not evidence for the newer 130-file correction.

### Narrow correction disposition

| Finding | Current source disposition and limit |
| --- | --- |
| OU13 — restored Add feed creation | **Addressed in source.** `SourcesScreen.kt:197–208` saves the draft UUID and dismisses only for the matching completed source ID. `SourcesViewModel.kt:19–23,100–107` restores and persists that completion ID. `RoomCollectorRepository.kt:31–38` resolves the exact already-committed draft within a transaction, returns it without rewriting the source/snapshot/attempt, and rejects a conflicting saved definition. An uncertain completion can therefore retry the same ID without making a second source or overwriting an independent edit. `EverydayEditingTest.kt:103–114` adds fresh-repository replay, distinct-draft and conflict checks; execution of this corrected batch remains pending. |
| OU14 — auxiliary-read Retry | **Addressed in source.** `EverydayBriefViewModel.kt:25–41,70–81` makes Retry rerun bootstrap, exact-document observation and the history/settings/scoped-record projection. The saved `requestedId` remains the first choice, so recovering those reads does not substitute a new brief. A failed auxiliary read still produces an error instead of silently establishing empty history. No injected auxiliary-failure execution is claimed in this source review. |
| OU16 — warm explicit target identity | **Addressed in source.** `AppShell.kt:216–219` calls `navigateToInstrument`; `HaloNavigationRoutes.kt:38–45` disables singleTop for organizer, brief and saved-source routes. Their entry-keyed owners consequently receive a distinct entry while the earlier entry/draft remains available on Back. `EverydayEntryNavigationTest` uses a real NavHost to check different and repeated topmost brief targets after internal history selection, and organizer saved-state retention through navigation/restoration. These tests assert entry identity, arguments and saved state; they do not themselves exercise a complete live page/phone notification journey. |
| OU20 — muted manual article reminder | **Addressed in source.** `ArticleReminderReview.kt:5–10` no longer treats mute as a manual-reminder prohibition. Source ID/version, enabled/selected state, content revision and exact article equality still guard the same transaction. `EverydaySourceReviewTest.kt:33` now permits an otherwise unchanged muted source; stale version/content remains rejected. This does not alter mute's brief/proactive scope. |
| OU01 — retained TASK end exposed by the prior test | **Addressed in source.** `OrganizerDraft.kt:29–35` computes `hasEnd` from an EVENT or the actual existing override/master time and uses it when reconstructing a record. `OrganizerEditor.kt:49–62` exposes the corresponding end controls and adjusts the exclusive all-day end where necessary. The original override and end fields remain in the saved draft and `forOccurrence` projection. Thus an existing TASK end is both visible and retained on a title-only/restored edit, rather than silently discarded because its kind is not EVENT. The previously failing DST replacement test remains the relevant pending regression. |

The additional `EverydayFormsRenderingTest` source covers an editor edit/discard-decline/restoration path, RU/LV/PL at 200% with actual text-layout scale assertions, unavailable/late Kids-sharing revocation and a child date input's 56dp actions. Those are useful targeted assertions; they have not been executed by this reviewer and no renders or native geometry are accepted from their presence alone.

No further concrete source residual was found in these five narrowly re-reviewed mechanisms. This closes their **source-review findings**, not their pending verification, all other issue-63 workstreams, real-device acceptance, human assistive-technology use or participant usability measures. No overall implementation or UX/UI score is assigned.
