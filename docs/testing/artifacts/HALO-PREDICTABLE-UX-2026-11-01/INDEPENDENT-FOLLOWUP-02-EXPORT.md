# Independent external-export follow-up

Status: HISTORICAL REPORT — independent observations at the source snapshot described below.

Date: 1 November 2026. Scope: integrated external export transport/session/UI, Graph/AppShell wiring, direct Notes export, incoming ACTION_VIEW and WorkspaceContinuity OPEN routes. Also spot-checked the source fixes for F01–F10. Layered guidance remains in progress and was excluded.

Method: read-only source review by `/root/ux_ui_review`, with independent data-owner and UI/entry assessments. No code edits, Gradle execution, device/UI actions or fresh score. References identify the inspected uncommitted snapshot; root is editing concurrently.

## Actionable findings

Paths below are relative to `app/src/main/java/com/halo/app/` unless stated otherwise.

| ID / priority | Trigger, evidence and correction |
|---|---|
| E01 / **P1** | **Picker callbacks are not bound to the request that launched them.** `data/workspace/WorkspaceExternalExportSession.kt:81–97` receives only a URI and applies it to whichever session is now PICKER/CANCELLED. A late result can revive a cancelled request or be attributed to a replacement request. A concrete UI precursor exists at `ui/workspace/WorkspaceExternalExportUi.kt:105–113`: rapid second Choose activation makes `picking()` reject busy, but its catch calls `picked(null)`, cancelling the first live picker. Persist a transfer/attempt identity, correlate launch/result with it, and never cancel another attempt because launch admission failed. Test double activation, late callbacks after cancellation/replacement, and restored results. |
| E02 / **P2** | **A valid restored result can be dropped before journal loading finishes.** The host starts async `owner.load()` at `WorkspaceExternalExportUi.kt:45–49`, independently of ActivityResult callback registration. `WorkspaceExternalExportSession.kt:84` returns immediately if state is null, without loading under the same lock. On process recreation, RESULT_OK can arrive first, disappear, and then the restored journal becomes CANCELLED. Load the journal before resolving callback admission under the same mutex; preserve the pending attempt identity. |
| E03 / **P2** | **Cancelling a new location choice erases uncertainty about an earlier write.** `WorkspaceExternalExportSession.kt:77` retains the old destination while clearing its outcome; `:85–88` changes the whole session to CANCELLED. Trigger: bytes written, readback unavailable → UNKNOWN → Choose another location → cancel. `strings_external_export.xml` then says “Halo did not write a copy” although the earlier target can contain complete or partial output. Keep the preceding receipt/outcome independently of the new picker attempt; describe cancellation as no new write while retaining the earlier uncertainty and inspection path. |
| E04 / **P2** | **Invalid provider results can strand the export in PICKER.** `WorkspaceExternalExportSession.kt:91` requires a content URI inside the launched callback coroutine without catching that validation failure. A file URI or malformed provider result throws before a terminal state is persisted, leaving busy controls disabled. Reject invalid results into a controlled, dismissible outcome before opening output. Test zero writes and a recoverable session. |
| E05 / **P2** | **Open exported copy can re-enter Halo instead of an external viewer.** `ui/workspace/WorkspaceExternalExportUi.kt:92–96` sends unrestricted ACTION_VIEW, while the new manifest registers Halo for the same content MIME types. If Halo is default/selected, `MainActivity.kt:379–393` imports the export again and opens another local copy. Resolve an external viewer excluding Halo, with an honest unavailable state. Recheck current loaded adult authority immediately before dispatch; the current UI callback relies on composition-time `permitted`. |
| E06 / **P2** | **The incoming-file toast declares opening before delivery is established.** `MainActivity.kt:391–393` selects “Opened a local copy” immediately after `WorkspaceContinuity.requestOpen()`. That owner only enqueues a request; `WorkspaceHandoffRequests` can still mark it rejected/expired and route creation can fail. Report the confirmed local-copy result and pending opening separately, for example “Local copy kept; opening…”. Only claim opening after the appropriate host result. |

E01's wrong-request scenario is an owner-boundary defect: request A → cancellation/replacement → request B picker → delayed A result. Android's normal one-result behavior does not make an uncorrelated owner safe, especially with the current failed-second-launch cancellation path and restored callbacks. The correction should address both layers together.

## Supported safeguards and integration

- Transport reads through the canonical current-version owner, requires an owned local copy and hash, validates the source bytes before touching output, and reads the chosen destination back before VERIFIED.
- Recovery verification is read-only; it does not replay a write. Interrupted write/verify journal entries become unknown after restoration.
- The production transport/session permission closure requires loaded Kids preferences and adult mode, and writing rechecks that permission during chunks.
- Direct Notes export flushes first and supplies the actual revision to the existing text exporter. Its AppShell callback rechecks loaded adult authority. The exporter revalidates the source at local-output publication.
- OPEN routing uses supported canonical MIME/type metadata for text/PDF/image families and retains `navigateSafely()` at the shell boundary. No additional confirmed child navigation bypass was found in this bounded pass.
- Output-location actions are integrated into Notes, Studio and file-tool output presentation. The outstanding issues concern lifecycle/result identity and truthful recovery, not a missing transport implementation.

## F01–F10 source spot-check

| Previous finding | Current source response |
|---|---|
| F01 — failed Notes observation overwrite | Observer now guards local-vs-committed edit ownership, source dirtiness and FAILED state. |
| F02 — older-base recovery ignored | Exact recovery blocks/title/source text now reopen as the local side of an explicit conflict. |
| F03 — oversized escaped Study recovery | Encoded payload length is validated before storage replacement; overflow regression test is present. |
| F04 — stale Studio retry loop | Retry carries its original expected project and revalidates its revision before invoking the block. |
| F05 — interrupted Studio declared failed | Interrupted copy no longer prepends the definite operation-failure sentence. |
| F06 — no Studio open Retry | Open/observation failures now supply a re-open retry and clear prior mutation retry admission. |
| F07 — unmeasured Home/Kids orbit | Actual orbit branch is now measured for placement. |
| F08 — invented 880dp fixture budget | Hardcoded 880dp supply is removed. |
| F09 — unused return item identity | NotebookScreen now consumes the last-opened identity. |
| F10 — inactive caption/invisible activity | Ring/caption is now a merged action target and separate activity wording is visible in source. |

These changes address the reported mechanisms on inspection. They are not blanket acceptance of new rendered layouts, every race or actual device behavior.

## Evidence limit

The latest completed `recovery-shell-host.log` inspected here reports **82 tests, one failure**: `NoteStructuredRecoveryTest.failedStructuredWritesStayOwnedByEditorAcrossExternalObservation` timed out. Root identified its use of legacy `updateBody` and is correcting it to dispatch through the structured owner. Until that run passes, this review does not call the host gate green. Export tests cover exact readback, source/permission denial, corrupted output, read-only recheck and basic cancellation/journal recovery; they do not yet cover E01–E04.

No external provider picker, outside-app viewer, real lifecycle interruption, child session or assistive operation was exercised. No new numerical usability/visual score is assigned. Questions skipped: scope and implementation authority were explicit; findings were returned to root as they were confirmed.
