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
