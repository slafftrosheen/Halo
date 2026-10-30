# Issue 57 focused acceptance — 2026-10-30

Issue: https://github.com/slafftrosheen/Halo/issues/57

Status: **KEEP_OPEN**. This checkpoint fills specific gaps; no broad regression suite was rerun.

## Production memory correction and Forget

**PASS — native explicit review/Keep, correction, Forget and local production recall/revalidation.**
The sole QA fact was created with native Remember information → Fact → Review → Keep.
The existing memory opt-in remained on. No canonical rows were inserted by instrumentation.

Canonical ID: `97400eb6-9186-4222-96ad-369b4a313bbc`, FACT/GLOBAL.
Original: `HALO-QA-20261029 test token is copper.`
Native correction: `HALO-QA-20261029 test token is silver.`

The installed read-only `MemoryContextCheckpointPhysicalTest#verifyProductionRecallCheckpoint`
used the real `ManualDependencyGraph.memoryContextSource.recall` and `dereference` with the same
run ID `issue57-memory-20261030` at three separate checkpoints:

| Checkpoint | Result |
| --- | --- |
| ORIGINAL | Exact copper fact recalled; dereference succeeds; original hit retained for the later checks. |
| CORRECTED | Retained copper hit rejected; fresh recall returns silver under the same canonical ID with a new version; current silver dereference succeeds. |
| FORGOTTEN | Retained copper and silver hits both rejected; fresh recall contains neither the ID nor the QA prefix. |

Native Forget confirmation removed only this QA fact. The unfiltered memory screen then showed
zero saved records and zero suggestions. This is **local production retrieval evidence**, not model
influence, conversational answer quality, a generated suggestion, remote delivery or spoken acceptance.
The explicit manual candidate/review path was exercised; automatic suggestion generation was not.

Machine reports and single-method runner logs: [evidence directory](issue57/2026-10-30).
Each method completed `OK (1 test)`. No provider invocation or memory usage marking was performed.

## Chain B: close the saved-copy reopen gap

**PASS — native reopen of the saved Studio copy after intervening process restarts.**
Workspace opened the actual `HALO-QA-20261029-STUDIO-COPY` project; Studio displayed all changes
saved. Canonical readback confirms revision 5 and byte-exact committed/session JSON from the
earlier Save copy → background edit → Undo → Redo → Save checkpoint. The original project's
revision and committed/session hashes still match its pre-edit baseline.

This completes the previously deferred copy reopen check. Reuse the actual native Workspace
PNG → Canvas → Open source and edit/export/restart evidence in
[canvas-edit-source-v14-result.json](issue62/2026-10-29/unattended/canvas-edit-source-v14-result.json).
The original image bytes and note were preserved. A copied export's source IDs in prose remain
prose; they are not represented as a structured live reference. The background edit is not claimed
to change the exported pixels because the existing opaque image covers it.

Exact new readback: [studio-copy-reopened-result.json](issue57/2026-10-30/studio-copy-reopened-result.json).

## Build, device and restoration

- Main source: `eee81b805698a70058cf4f9e775d703caa52d9bb`.
- Installed main APK SHA-256: `5fd833042484d3560fc30625f6de59faa7deb3b3e9e43e114bb1db7e5872cca8`.
- Test source: `1253093d8ba5a6c9ba007159e9614fa8227c70da`.
- Test APK SHA-256: `b5c8fafcf596b895ac5c5449faf0c6ba3c1821fac11855c5eebe3eca83579c48`.
- Only `:app:assembleDebugAndroidTest` was built (5m29s); only the test APK was installed.
- Device: `f01fe673`, Redmi `23124RA7EO`, Android 15.
- Original automatic rotation `1`, user rotation `0`, font scale `1.0` restored and read back.
  Other appearance, mode, model and consent settings were not changed.

## Remaining issue gates

The native selected-word check reproduced a Chain A defect: the editor opened the setup wizard
with only `captureSource=<noteId>`, bypassing `NoteEditorViewModel.startStudy` and losing its
anchor. No anchored session was created. The focused fix carries a saved ID/revision/block/range
reference through the existing setup navigation and ViewModel, resolves the selected preview,
persists the anchor with the learner's setup choices, and revalidates it before the transaction.
The original reproduction was **FAIL**. The corrected source's two focused regression cases now
**PASS** (zero failures/errors/skips): editor selection → serialized setup reference → selected
preview → saved anchor and learner choices; source changed during setup → no new lesson or
whole-note/general-knowledge fallback. Only that class ran. The first assembly caught a Kotlin
cross-module smart-cast compilation error; it was corrected before the successful gate.

The focused gate and main APK assembly completed in 6m16s (380.1 seconds including orchestration),
with zero frozen-source drift. Main APK `6669346563e75e4f5a65309ee7d1502c6624324a277d900e6b50480d77c469d1`
was installed and its device hash verified. The APK was assembled before the evidence commit;
[the exact changed-source hashes](issue57/2026-10-30/selection-fix-source-hashes.json) identify its
source independently of the base commit's BuildConfig metadata.

**PASS — physical selected-passage handoff and restart.** Native long press selected only `Copper`
in the existing QA note. The selection toolbar's Study action opened the four-step setup, where
Practice and explicit phone execution were chosen. Saved session `6dcb8501-d113-4e17-9d59-54f272df52be`
retains note `e38b5c57-7f62-42e6-bd79-cda25c86b396`, revision `1`, block
`efcf898c-36a4-4cae-88ad-0b68a431ef70`, offsets `[0,6)`. The actual source inspector showed only
`Copper`. After force-stop and launch, the saved lesson reopened from the native library; exact
session fields and selected excerpt matched. Open original reached the correct untouched note.
No inference, new generated note, speech or unsupported PDF/Ink operation was invoked.

Readback and native XML: [selected-passage-restart-result.json](issue57/2026-10-30/selected-passage-restart-result.json).
Reproduce the focused host regression with
`gradlew.bat :app:testDebugUnitTest --tests com.halo.app.data.workspace.SelectedPassageSetupRegressionTest`.

Chain A's exact saved-note selection → Study → restart portion is now qualified. Its full
selected-source → newly saved linked note/output chain is not established by this narrower check;
the existing whole-note export handoff is separate evidence. The complete negative
matrix, three real UI/typed/spoken routes and correlated Private→cloud zero-outbound case remain
separate gates. Unsupported PDF extraction and physical Ink/stylus remain explicitly bounded.
These results do not establish full issue closure.

## Continuation: linked output, replay, conflict and offline

**PASS — native linked output and a new Study from that output.** Save study notes created note
`6f115dcb-f77a-4c90-8967-ce169acb9cc8`, revision 1, with a real REFERENCE block to the original
source and canonical `object_provenance` linking the exact anchored Study session. Force-stop,
native resume and repeating export reused the same note, revision and body hash. An intentional
native edit of this QA note then produced revision 2; exporting again refused to overwrite it
and displayed the localized conflict notice. Its edit and original creation receipt were preserved.
[Readback](issue57/2026-10-30/selected-output-replay-conflict-result.json).

The edited note then entered native Study setup as a new source. New session
`f1dd8094-0a67-4743-a3ae-b018cb8f81b7` references that note at its actual current revision 2;
it does not misattribute the original session or label the historical export receipt as revision 2.
After an explicit verified process stop, native resume and Open original reached the same note
with the same body hash. This verifies the saved-source/output/session chain. A generated explanation
and the spoken route are separate requirements, not inferred from metadata export.
[Readback](issue57/2026-10-30/exported-note-study-link-result.json).

**PASS — offline local source access.** With Android reporting `Active default network: none`,
the native source inspector still showed exactly `Copper` and Open original reached the correct
full original note. Original Wi-Fi off/mobile data on settings were restored in `finally` and read
back. No provider generation or offline voice claim.
[Result](issue57/2026-10-30/offline-source-result.json).

## Private denial deadlock: reproduced, fixed and physically verified

The first correlated negative check exposed an actual lock inversion, not a model timeout.
`PrivacyModeStore.acquire` held its authority lock while the denied-request audit called provider
discovery. Another thread held the provider registry lock while reading the processing profile.
Both blocked. The initial run was stopped; the bounded diagnostic run failed after 30 seconds.
[Thread proof](issue57/2026-10-30/private-lock-thread-proof.log) and
[failure](issue57/2026-10-30/private-before-fix-timeout.log) are retained.

`PrivateDeniedAuditLockTest` reproduced the lock ownership failure before the production change.
The fix finalizes denial under the same authority lock, then enriches its BLOCKED receipt outside
that lock. Permission checks, STARTED receipt handling and granted lease registration/revalidation
retain their original synchronization. A later mode change cannot turn that denial into a grant.

**PASS — focused regression and real selected-source negative case.** The lock regression and the
two directly affected Workspace-category and queued-revision dispatch tests passed (3 total,
zero failures/errors/skips). No generic suite or lint was rerun. The app and test APKs were built,
installed and fingerprinted. The same cold-production-graph physical method now finishes in 2.269s:
native Private active; real saved source/revision/range resolves locally to `Copper`; remote object
permission denies; an explicit adverse attempt through the production OpenRouter provider and
dispatch policy records BLOCKED. Its observer-instrumented client reports zero DNS lookups,
connections, request headers and body bytes. The independent DNS containment would fail the test
if reached; it is not used as proof of policy denial. The actual BLOCKED receipt is correlated by ID.

The native control restored original Power mode; both complete mode profile contents and sharing
acknowledgment match their baseline (intent revision counters may advance). No credentials,
permissions, source rows or Working Set selections were changed by the harness.
[Result](issue57/2026-10-30/private-lock-fix-result.json),
[provider observation](issue57/2026-10-30/selected-source-private-result.json),
[receipt](issue57/2026-10-30/private-blocked-receipt.json).

Installed main APK: `be6b200503bef3192d97d50beb81d7c36d21ab517fb15ff292e7009fdd882b4d`.
Installed test APK: `387db24c935088da234e61345c5cf80f88a85c0315068ae1fb0becb032398e98`.
[Frozen source hashes](issue57/2026-10-30/private-lock-fix-source-hashes.json) identify the changed
source independently of the base-commit BuildConfig metadata. Device remains `f01fe673`, Redmi
23124RA7EO, Android 15.

The Russian export also exposed an English selected-range label. Rendered export labels now use
the existing EN/RU/LV/PL Study copy owner while preserving exact offsets and canonical locator
metadata. The two focused copy cases passed before the separate lock fix; that copy source did not
change afterward. Existing edited documents are not silently rewritten.

Remaining acceptance is still reported separately: actual grounded generation and representative
typed/spoken routes are not established by these persistence/denial checks. No full closure yet.

The subsequent [actual adult Study journey](ISSUE-61-ACCEPTANCE-2026-10-30.md) adds three real
typed provider replies, three natively reviewed/kept questions and a saved summary with a live
source reference. Restart readback preserves the exact passage/revision, replies, card states
and note/provenance. Strict source-only content failed on an extra fact in the first reply;
that result is retained separately from the passing persistence checks. Spoken routes remain
deferred; this is not full issue closure.
