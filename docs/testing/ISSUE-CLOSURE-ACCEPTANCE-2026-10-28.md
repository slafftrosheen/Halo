# Issue closure acceptance continuation — 2026-10-28

Scope: finish actual acceptance gaps for issues 55–62. This is an incremental ledger; a passing row qualifies only its stated behavior. Baseline `7257b47069bcc91c993f2a78e6b4e86343f7def6`; fetch confirmed `origin/main...HEAD = 0 0` before edits, with prior uncommitted implementation retained.

## Installed baseline and physical checks

Physical Redmi 23124RA7EO, Android 15, authorized ADB serial `f01fe673`. Installed app SHA-256 independently read from base.apk: `f96e736d25650da0a06b1771b6aa0d7c04e0940574dab0dd18207d21ef50cf7b`. Local phone evidence is outside Git at `C:\Users\slavd\HaloEco\verification-2026-10-28\closure-pass`; captures may contain user content and must not be published wholesale.

| Actual production-app journey | Observed result | Status |
| --- | --- | --- |
| Home orbit history satellite → conversation history → Back | Tapping the actual satellite opened the history sheet. Back returned to the same chat. | PASS |
| Home explicit full action list | Actual list opened and exposed the current Home actions. | PASS |
| Home Chat → Screen | Dashboard rendered local clock/calendar/attention/continuation cards and current Screen actions. | PASS, inspected views |
| Screen edit → resize preview → Cancel | Persisted layout remained exactly revision 4 with its original cards. | PASS |
| Screen edit → taller clock → Apply | Revision 5 stored clock placement height 2 and adjusted downstream placement. | PASS |
| Force-stop → reopen Screen | Saved revision 5 read back unchanged; rendered after launch settled. Initial UI dump attempts lacked a hierarchy and were retained as harness failures. | PASS after retry |
| Screen settings → Undo after restart | Original card content restored under fresh revision 6. | PASS |
| Landscape Screen and accessible action list | Actual rotation 1 / 2400×1080 capture; four root destinations, bell, profile and full-list entry visible; list scroll exposed ambient entry. | PASS, inspected controls |
| Enter ambient from list in landscape → rotate portrait | Ambient opened in both orientations with explicit Exit. Portrait showed private placeholders instead of calendar/task details. | PASS, visible card masking |
| Ambient security and recording observation | Halo window had SECURE flag. RECORD_AUDIO operation showed no use since the earlier speech test. | PASS, scoped observation |
| Ambient Exit → bell Activities → Notifications → Close | Real history and empty notification view rendered; Close returned to Screen with its existing cards. | PASS |
| Unsent draft → landscape → Workspace → Home → force-stop/relaunch | Exact QA draft remained in the composer in all four readbacks; no send action performed. QA draft then cleared. | PASS |
| 360dp / 200% text | Home, Workspace, Settings and Devices inspected. All four fixed roots remained visible. Workspace search initially wrapped to four lines. On APK `52993c50…`, the one-line ellipsis reduced field height from 624 to 228 physical pixels, with the complete accessibility label retained. | Original FAIL; search fix physically PASS |
| Approximately 412dp / 200% text | Home, Settings and Devices captured on the same physical phone via temporary density override. This is responsive viewport evidence, not tablet/hardware qualification. | PASS, inspected controls |

Phone system animator setting was originally absent (`null`); a temporary reduced-motion counterfactual restored it by deleting the override. Temporary density override was reset and font scale restored to 1.0. Automatic rotation was restored once, then temporarily locked again for the later acceptance checks. **The phone disconnected before the final restoration: automatic rotation remains 0, user rotation 0; restore automatic rotation to 1 at the next authorized device session.** Existing Russian language, appearance, credentials, models and content were preserved. Screen Undo restored the original layout content using its legitimate new revision; no direct preference replacement was used.

## Idle rendering defect and measurement

Five-minute untouched ambient Screen sample: process CPU averaged **90.85% of one core**; six PSS snapshots were 283082, 289945, 298678, 310074, 307851 and 307853 KiB. This is a debug-build observation, not a release battery/energy benchmark or proof of a memory leak. The interval included a separately recorded 20-second Simpleperf profile; retain that profiling overhead boundary.

Simpleperf recorded **8317 samples, zero lost**. Inclusive rendering stacks included Lottie draw/progress and the Halo Zone backdrop coroutine. Source inspection found the full-size IDLE artwork loops forever, while the Halo Zone's independent backdrop remains animated in Screen. A 30.297-second reduced-motion counterfactual, after three seconds settling, measured **0.363% of one core**. Original setting was restored. The counterfactual changes all decorative animation; it does not independently assign cost to each animation.

Reproduction artifacts: `ambient-sample.json`, `ambient-gfx-final.txt`, `ambient.perf.data`, `ambient-simpleperf-children.txt`, `ambient-simpleperf-all.txt`, `ambient-reduced-motion-counterfactual.json`, `ambient-window.txt`.

The first correction makes full-size idle artwork play once and settle, and stops the separate Halo Zone backdrop clock on Screen. APK `52993c500e992b5051dde03e1b09e7af8ed89586505af004087da7b37aa3f5ce` was built, replacement-installed and matched by base.apk checksum. Its complete five-minute samples still measured **81.59%** of one core after instrumentation and **92.77%** after a clean process restart. This is **FAIL**, not a resource PASS. The second sample rules out dismissing the problem as instrumentation state alone.

The updated 15-second profile recorded **6170 samples, zero lost**. Lottie/backdrop stacks were absent above the report cutoff, but Material's indeterminate `LinearProgressIndicator` and `InfiniteTransition` remained. Source inspection identified the global interaction renderer mounted before AppShell's Scaffold: its passive progress card can remain covered by the working page while its frame clock keeps running. The Runtime's progress projection has no input request ID; actual approvals use separate confirmation/input content.

The subsequent source fix introduces `ShellInteractionSurface`: global passive `Progress` is not composed; other content, including actual input/approval dialogs, delegates to the existing renderer and typed response owner. It never dismisses, resolves, cancels or replays a run. Activity retains run progress and receipts. New rendered regressions cover absence of the hidden progress card and a transition to an approval that remains visible and emits only the explicitly selected Denied response.

The physical idle regression assertion failed on the pre-fix measurement before this source correction. **Final hidden-progress fix installation and physical CPU verification are DEFERRED_BY_USER** after the phone disconnected; the owner answered “Phone tests later.” No further ADB polling or reconnection requests are authorized by this deferral. Do not call the resource gate PASS from code or the reduced-motion counterfactual.

## Real free-provider action evidence

The actual Models UI identified `nvidia/nemotron-3.5-lightning:free` as the conversation default. Public metadata was checked at 17:16:50 UTC and again at 17:54:58 UTC: prompt/completion price 0, tools supported. Raw backing defaults also contained `openrouter/free`; those unscoped keys were not treated as the effective mode-specific route. The persisted executed run independently pinned the concrete Nemotron free model. No provider credentials were printed or changed.

- **PASS — actual typed note creation:** run `b2855288-d669-4afe-b37a-a61931fe7708` completed through real OpenRouter. Two malformed discovery calls were rejected as INVALID_ARGUMENTS before successful discovery and a CONFIRMED `halo_create_note` effect. Canonical readback found one active QA note, `1e543729-b284-4d79-8226-1f20f4ccc618`, revision 1, with the exact requested body. Rejected model arguments were not silently accepted.
- **FAIL — requested live note→Canvas reference:** Canvas `e145715f-9ba8-45db-9045-8abd3c877baa` persisted at revision 2, but node `84d031d7-fbd0-4335-b59a-0b478b3e045d` had type TEXT and null `refObjectId`. Its text merely contained the note ID. Assistant prose describing a link is not source-reference acceptance. The same failure class remains open under 55/57/58/62.
- **NOT_RUN — guided recovery through `halo_workspace_insert`:** the existing capability was inspected and supports exact note/file source insertion at current source/Canvas versions. The phone disconnected before the corrective request could be entered or sent. A new metadata check does not constitute an executed recovery.
- **Observed follow-up:** the sent literal remained in the visible composer after the live replies, while a later clean restart showed an empty composer. This needs an isolated UI/receipt/IME investigation before claiming complete submission-display acceptance; no speculative code change was made.

QA source objects and conversation remain available for continuation. No user object was overwritten or deleted. Raw database copies and phone captures remain outside Git. No whole cross-tool workflow PASS is claimed from the successful note step.

## Remaining closure boundaries

No issue is closed by this incremental report. Remaining issue-specific gates include real source-linked and protected cross-tool chains; workflow typed/spoken recipes and durable receipts; offline artifact/native voice qualification; Screen source-backed routine/Study continuation, lock/dock and requested layouts; complete adult/Kids learning; remaining screen/accessibility/context and sustained rendering acceptance. Prior evidence remains valid only for its exact scope. Human speech confirmation of “Open Workspace” remains one positive spoken navigation/audibility flow, not all spoken workflow coverage.

## Host and device regression boundaries

- Full app baseline rerun: **2206/2206 PASS**, zero failures/errors/skips, 19m11s. This rerun includes all seven corrected fixtures from the preceding verification report. XML results were copied before later focused runs.
- Idle-motion regression before correction: two expected assertion failures in the seven-case presence suite. These are retained in `idle-regression-red.log` / `idle-red-results`.
- First correction gate: **29/29 PASS**, zero failures/errors/skips across presence, Halo Zone, footer geometry and Library search ordering; both APK builds passed in 8m15s.
- Installed first-correction APK device run: **18 PASS / 3 harness failures** because the invocation used `haloQaRunId`/`haloQaSourceStamp` rather than required `runId`/`sourceStamp`. The three affected isolated Note/attachment/Study owner cases were rerun with correct arguments: **3/3 PASS**. There are 21 distinct passing cases across these attempts, not a single green initial 21-case run. The errors were invocation errors, not product fixes.
- Final hidden-progress source gate: **28/28 PASS**, zero failures/errors/skips across `ShellInteractionSurfaceTest`, `HaloApprovalRound2Test`, `ActivityReviewPresentationTest`, `PresenceArtworkContractTest`, `HaloZoneLayoutTest` and `ShellFooterViewportTest`. Both APK builds passed in **9m57s**. The installed phone has not received this final build; physical CPU/input/approval checks remain deferred.

The broad 2206-case pass predates the new presentation changes. Focused post-change checks qualify those deltas; do not relabel the earlier full run as a full run of the final source. No issue state or acceptance checkbox is changed merely from these totals.

## Final artifacts and publication boundary

| Artifact | SHA-256 / status |
| --- | --- |
| Final hidden-progress-fix app APK | `26267c937f4d9f2e7e2072b2c2bf378d934d8a0109de4a2f12eb9fd23c40e386` — built, **NOT_INSTALLED** |
| Test APK | `412630bb8c96b9e8127f64015af95f87f86b8c55d6c5dda65d710e43c219da18` |
| Last phone-installed app | `52993c500e992b5051dde03e1b09e7af8ed89586505af004087da7b37aa3f5ce` |
| Build source witness | `fc9a1b0f50bab521b60281557376d479b84d92500927f88fe3b0f104242314e0` — all covered production/test files checked unchanged before publication cleanup |

After that build, documentation was finalized and whitespace-only blank EOF lines were removed from the four locale `workflow_strings.xml` files. XML element trees were checked identical before/after; no functional source change or repeated build/test is claimed for that cleanup. The complete staged whitespace audit then passed.

The APKs retain their actual baseline-plus-dirty build provenance; a later Git commit does not rewrite their embedded revision. [Sanitized gate](issue62/2026-10-28/closure-gate.json) and [deferred phone checklist](ISSUE-CLOSURE-PENDING-DEVICE-2026-10-28.md) preserve what can and cannot support issue closure. All raw logs, failed attempts, profiles, database readbacks and captures remain in the machine-local evidence folder.
