# Android acceptance continuation — 2026-10-25

[#55](https://github.com/slafftrosheen/Halo/issues/55) remains **IN PROGRESS**. This continues the historical [2026-10-24 ledger](ISSUE-55-ANDROID-ACCEPTANCE-2026-10-24.md); earlier voice/BT/cloud results belong to their earlier APK fingerprints.

Baseline main for this continuation: `8a66b5e756587650da2671fd489075c2f2ef6d3d`. Physical Redmi23124RA7EO / Android15/API35 / serialf01fe673. Prior tested APK `ff00a95371eb747f3041d0972aa8288b8560412aa8e61323c77a02fb744aeeaf`.

| Actual Android-owned scenario | Status | Evidence / boundary |
|---|---|---|
| Create and rename a literal note; autosave | PASS | `HALO-ACCEPT-20261025-NOTE-A`, exact synthetic body read back |
| Rotate note to actual landscape; force-stop/relaunch/search/reopen | PASS | Exact title and body survived; search had one matching item |
| Create separate named Canvas; link exact fixture note | PASS | `HALO-ACCEPT-20261025-BOARD-A`, one reference card and the intended note title |
| Export Canvas to a new note | PASS | UI readback includes full literal source body and actual source ID `0343d899-48f7-4656-827a-c587e8e9e8a1` |
| Study setup from the exported note | PASS for setup only | Material step displays the exact exported title and Text ready; no tutor request/lesson-quality claim |
| Keep Canvas/reference through APK upgrade | PASS | Exact named board and intended source reference read back after replacement install |
| Workspace Search in landscape | FAIL then FIXED | Before: field below Tools/organize, requiring scroll. After: field immediately visible without scroll in measured rotation1 |
| Three assistant typed/spoken cloud workflows | IN_PROGRESS | Checkpoint13 free text round trip passed; real note tool saved the body but missed the requested independent title. The three full workflows remain incomplete |
| Positive current-binary spoken input / audible output / barge-in | NOT_ESTABLISHED | Private acoustic attempt and previous-fingerprint evidence are in the Interface4/historical ledgers |
| Free cloud text route | PASS for text only | Current selected free OpenRouter model returned a real response; no paid fallback. Image/audio and broader workflows are unqualified |
| Kids and wider state/permission recovery | NOT_RUN in this continuation | No invented PASS |
| Stylus/tablet/TalkBack/real Play-owner transactions | NOT_RUN / external equipment-owner conditions | No fixture substitutes for hardware/store acceptance |

Checkpoint13 presentation recovery moves existing Library controls directly after its header while preserving Hub order/callbacks; uses the shorter Russian Import label; and adds reviewed RU/LV/PL copies for six stable speech-rejection reasons that the real microphone attempt had exposed as untranslated detail. No routing, permissions, sharing, model or cognition owner changed.

Checkpoint13 command:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --tests "com.halo.app.ui.screens.notebook.*" --tests "com.halo.app.ui.screens.home.*" --tests "com.halo.app.language.*" :app:assembleDebug :app:assembleDebugAndroidTest "-Proborazzi.test.record=true" --max-workers=2 --console=plain --continue -I .artifacts/interface4/exclude-unfinished-draft.init.gradle
```

Actual: **309 affected cases, zero failures/errors/skips; both APKs built**. Native actual Main repeated/resumed navigation and isolated Polish large-text/draft/IME context fixture: **2 PASS**. The locale fixture uses memory preferences and does not change the user's language. These tests overlap earlier gates and are not summed as unique coverage. Generated catalogue check:4095 entries per EN/RU/LV/PL; Polish Android resources:4922.

Installed main SHA-256 `6a627749e9cda5b1bb15f15b9ab87b7b9e11cb396bc209c71107565bf9e644ab`; instrumentation `c1654921e7ac3210d6cf4ce48a1f891d7086a9b15d75fe381d788740746a2d42`. Both installed base APKs were read back. This checkpoint used one successful main and one test replacement install. The prior Interface4 checkpoint history remains separately counted.

[Compact sanitized results and artifact identity](issue55/2026-10-25/) retain synthetic fixture content only. Raw UI hierarchies/screenshots remain ignored locally. The source was built with the preserved untracked draft stub present and its unfinished test excluded, as already documented in the [Interface4 provenance ledger](INTERFACE-4-VERIFICATION.md). Neither file was changed in this presentation batch.

Power mode, font1.0, physical density440, free sensor rotation/default fixed policy, accelerometer1/userrotation0, original unset animator scale and existing OS grants were preserved/restored. No paid requests, credential reads, model downloads, new sharing acknowledgement, source-data reset, external message or new permission grant occurred. Test-created note/board/export objects remain clearly named for inspection; existing source objects were not edited.

## Newly reproduced Android-owned P0

On checkpoint13, the empty Home composer accepted the literal unsent fixture `HALO-UNSENT-DRAFT-20261025`; no Send/model/provider request occurred. After background → force-stop → relaunch, the composer was empty: **FAIL_ANDROID_DRAFT_LOSS** ([exact result](issue55/2026-10-25/draft-restart-baseline.json)). Saved note/Canvas objects survived separately. At checkpoint13, Home and Orb editors kept independent rememberSaveable maps; the pre-existing untracked ComposerDraftStore was an unreferenced save/load stub. Checkpoint13 preserved those files. The follow-up below completes the shared owner and verifies the original failure on the phone; no persistence PASS applies retrospectively to checkpoint13.

## Shared draft and action-adapter recovery

This follow-up deliberately completes the two previously preserved draft files, with their original bytes backed up locally and original three tests retained. Production uses one Graph-owned store through HomeViewModel and both editors, backed only by the existing Keystore/GCM AtomicFile secret storage under noBackup. It restores text without replaying submissions or actions. Exact rendered conversation/revision/literal identity guards admission, accepted clear and failure recovery; temporary startup text moves only through a guarded identity receipt or explicit recovery. Corrupt saved bytes remain untouched until explicit replacement. Oversized text stays editable with an error; durable storage is bounded to 128 records, 100,000 characters per record and 700,000 encoded bytes. Acknowledgement follows atomic commit; a process stop before asynchronous commit can still lose an uncommitted edit.

Checkpoint14 compiled both APKs and passed 230 affected app cases plus 51 language and 26 interpretation cases, all with zero failures/errors/skips. It used no unfinished-draft test exclusion and included 15 store cases. The candidate was not installed: independent review then found a restore/cleanup I/O race. Two deterministic blocked-write cases reproduced it. Three additional named-note/schema cases reproduced the missing optional-title adapter; legacy behavior and invalid-argument rejection remained green. Focused RED: 7 tests, 5 expected assertion failures, zero errors/skips.

The minimal follow-up preserves newer restored typing across successful or failed cleanup and queues; old deleted identities remain invalid. Existing create-note gains the same optional 120-character title already supported by its canonical repository and rename action. Shared action cues now recognize bounded singular one/1 without plural/count ambiguity. Provider, budget, privacy, permission and authority settings are unchanged. Checkpoint15 completed the grouped build and fresh-device readback below.

[Sanitized free-cloud checkpoint13 receipts](issue55/2026-10-25/free-cloud-checkpoint-13.json) separate the genuine ping, failed singular-one route, and partial create-a-note control. The actual note id is `a76643a7-2b64-4f03-94db-df16ac3bf64a`; body and body-derived title were read back in the real editor. Its final cloud response displayed that id. These are current-binary observations, not a three-workflow or alpha qualification.

Checkpoint15 command (no exclusion init script):

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.halo.app.ui.screens.home.*" --tests "com.halo.app.ui.shell.*" --tests "com.halo.app.data.workspace.*" --tests "com.halo.app.di.tools.*" --tests "com.halo.app.language.*" :libraries:halo-cognition-language:test :engines:interpretation:test :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2 --console=plain
```

Actual: **594 app cases, zero failures/errors/skips**, including all 17 draft cases and 5 independent-title cases; both APKs built in 7m17s. Language 51 and interpretation 26 remain unchanged and UP_TO_DATE from their zero-failure checkpoint14 results. These overlapping gates are not summed as unique coverage. Generated catalogue check: 4109 entries per EN/RU/LV/PL, 4936 Polish Android strings. Final focused review found no remaining defect in the reported prune/restore race.

Installed main SHA-256 `052e24546607ad2f06cc7f9e30768b1633955af06ca0346cbe4eb55918264662`; test `34596c9b91597749c5c84e7e965c306f84a214958067f3dfc4db860c426c3e8e`. One main and one test replacement install; both installed base APK hashes matched. This binary was built from the validated working patch after 7d609afb21dbe60e8770b335ddae7d9c28604ff3; its compiled Git metadata names that pre-commit base. Publication identifies the tested source patch.

| Checkpoint15 physical scenario | Status | Exact boundary |
|---|---|---|
| Unsent draft → background → force-stop → cold launch | PASS | Exact `HALO_DRAFT_20261025_A`; no Send. Save was allowed 2s before stopping |
| Encrypted local handling | PASS for inspected fixture | Only `no_backup/secrets/composer.drafts.v1.bin` read; valid existing GCM frame, opaque payload, exact app decrypt/readback; no credential files read |
| Home → Workspace Orb editor → edit → Home | PASS | Same A, then exact `HALO_DRAFT_20261025_A_ORB`; no submit |
| Landscape1 → portrait0 | PASS | Exact draft retained in both measured rotations; free/default sensor policy and original settings restored |
| Two actual conversations and restart | PASS | A and `HALO_DRAFT_20261025_B` remained distinct through switching and another process restart |
| Accepted prompt / subsequent draft | PASS | Submitted prompt cleared; B typed while cloud run was active survived the final reply |
| Original singular-one prompt and independent note title | PASS | Real free AGENTIC/NATIVE route, no fallback; `halo_create_note` CONFIRMED. Real editor readback has exact title `HALO-AI-20261025-NOTE-B` and body `Violet lanterns glow at dawn.`; returned id `da21e339-c234-4870-b05f-43792826a92b` |
| Native regression run | 13 PASS, 1 NOT_RUN | Orb 6, synthetic privacy-owner 2, Mini Activity 3, actual Main 1, isolated Polish 1. Explicit quiet-capture opt-in was absent; no microphone PASS |

[Draft device results](issue55/2026-10-25/draft-device-15.json), [free note receipts](issue55/2026-10-25/free-note-device-15.json), [automated gate](issue55/2026-10-25/automated-gate-15.json), [RED proof](issue55/2026-10-25/review-regressions-red.json), [native boundaries](issue55/2026-10-25/native-15.json). Exception/corruption/bounds and blocked cleanup cases are isolated tests, not live-data corruption trials. A malformed discovery call was rejected before effects, then the model recovered; this is included in the cloud receipt.

#55 and #62 remain open. Three full cross-tool journeys, current spoken/audible/barge-in acceptance, and remaining Android-owned recovery/Study/Kids/accessibility work are incomplete. Existing paid/provider/permission settings are unchanged. The next focused issue is repeated app-side catalogue work: two note samples show about 41s before first provider dispatch and about 20s inside discovery, while provider calls themselves take roughly 2–6s. Source review found nested complete registry/skill rebuilds; a scoped snapshot fix still needs measured regression proof. No overall performance or alpha qualification is claimed.

## Catalogue recovery — checkpoint 16

The next canonical fix removes nested registry/skill reconstruction from metadata discovery. Every public catalogue/search operation captures fresh descriptors, registered definition IDs/names, capabilities and bulk skill/permission availability once; bootstrap recommendation and summary share a scan. There is no TTL/global cache and no ToolRegistry replacement. Live scalar authorization, privacy, permissions and ALWAYS approval still recheck at execution. Independent review found no concrete boundary regression.

TDD: nine regressions initially had 4 assertion failures (search 25 vs 1 registry enumerations, bootstrap 51 vs 2, summary and inconsistent permission answers), zero errors. The fix passes all 362 runtime cases and 594 affected app cases, zero failures/errors/skips. Language 51/interpretation 26 unchanged passing results remain UP_TO_DATE. Full app/APK build 8m6s; subsequent instrumentation-only harness/archive rebuilds 34/32/32/34s did not change production bytes. Final native suite 13 PASS /1 explicit quiet-capture NOT_RUN. Overlapping counts are not unique cumulative coverage.

Installed/read-back main SHA-256 `73f008a026a5db6439d4cff7b13dd0f87b8d3b72aab8bf7bc2bf8d1885d5555c`; final test `539b1c7702c8e2da45240f2cca213c8e1e8b8dd1230363c57042ae40a07b023d`. Main source was the validated working patch after 048d8cef7fd739719fd222ad20d44dee63c6fa44; compiled Git metadata names that base. Checkpoint16 had one main replacement and five test replacements including experimental diagnostics and final removal; intermediate fingerprints are retained in the automated record.

| Same-phone free-model note path | Before checkpoint15 | After checkpoint 16 |
|---|---|---|
| Role resolution → first provider turn | 41,322ms | 2,330ms |
| Discovery execution | 20,784ms | 325ms |
| Actual saved note | Exact B title/body | Exact `HALO-PERF-20261025-NOTE-E` / `Silver kites rest at noon.` |

These are individual uncontrolled samples, not percentiles, battery/leak or throughput qualification. Model rounds differ (including safely rejected malformed discovery calls), so no provider-speed claim follows. The current run used the same inherited default free NVIDIA/OpenRouter model, NATIVE/REQUIRED, no fallback, with zero-price/tools endpoint metadata checked before dispatch; actual editor readback confirmed the saved title/body.

[Source counters/device sample](issue55/2026-10-25/catalogue-device-16.json), [automated gate](issue55/2026-10-25/automated-gate-16.json), [RED proof](issue55/2026-10-25/catalogue-red.json), [final native boundaries](issue55/2026-10-25/native-16.json).

### Additional attempted workflows and input diagnostics

The genuine checkpoint15 note→Canvas compound attempt read the correct note but stopped at the six-step safety limit after three rejected discovery calls. Only read/discovery receipts were confirmed; local title search found no matching board. No uncertain creation was replayed. It remains **INCOMPLETE**, not a cross-tool PASS ([record](issue55/2026-10-25/canvas-attempt-15.json)). No budget/model/provider policy was changed to force success.

Long rapid shell-key injection also produced incomplete prefixes. Experimental isolated tests were kept separate: initial 8 setup failures involved placeholder semantics; repaired run 6 PASS/2 failures; extra frame drain 5 PASS/3 failures; real-clock controls 1 PASS/3 failures (two focus setup failures); individual real-clock repeats failed both local and shared controls. Ordinary whole-string IME commits and paced actual Home/Orb entry have passing observations. A shared-store-specific cause was not established; no speculative production editor projection/change was implemented. **Rapid shell stress remains unqualified and overall keyboard acceptance is not promoted to PASS.** Latest sources and all result categories are preserved in the [diagnostic archive](issue55/2026-10-25/diagnostic-src/); they were removed from the compiled qualifying suite rather than relabelled passing. Raw logs and exact experimental APK stay ignored locally.

#55/#62 remain KEEP_OPEN for their full acceptance. Current positive spoken/audible/barge-in checks, three complete cross-tool journeys and remaining owned presentation/recovery/Study/Kids/accessibility work are still incomplete. No paid dispatch, new sharing acknowledgement, permission grant, credential read by the agent, model download or source-data reset occurred in this checkpoint.
