# Resumed physical acceptance — 2026-10-28

Latest continuation: [non-voice qualification and preview/Study fixes, 2026-10-29](ISSUE-CLOSURE-NONVOICE-2026-10-29.md). The historical results below retain their original binary and evidence boundaries.

Status: **IN_PROGRESS**. The owner resumed phone testing with “u have the phone continue to close all pending verif issues.” The earlier phone deferral no longer applies. Open issue inventory was refreshed: 55–62 remain open. No issue is closed by this intermediate checkpoint.

## Source, installation and restoration

- Pulled `main` before testing: already current at `37635608d494bc02ff67476d2648e87522db097b`, with a clean checkout.
- Authorized physical Redmi 23124RA7EO, Android 15, ADB serial `f01fe673`; device was awake and unlocked.
- Restored the outstanding `accelerometer_rotation` override from 0 to its original 1 and read it back before installation. Density/font/animator overrides had already been restored in the earlier session.
- Replacement-installed both final APKs. Independently read the installed `base.apk` checksum: `26267c937f4d9f2e7e2072b2c2bf378d934d8a0109de4a2f12eb9fd23c40e386`. Test APK: `412630bb8c96b9e8127f64015af95f87f86b8c55d6c5dda65d710e43c219da18`.
- These artifacts retain the build provenance described in the preceding ledger; publication did not rewrite their embedded baseline-plus-dirty stamp. No credentials, provider choices, language, appearance or user documents were replaced.

## Idle rendering regression — PASS for the measured scope

Actual UI path: Home Chat/Screen control → Screen → contextual settings/list → Screen ambient entry. Private cards were visibly masked and Exit remained available. The phone then remained untouched during a complete five-minute ambient sample, with normal system motion settings.

| Measurement | Observed result |
| --- | --- |
| Process CPU, 21 samples over 299.829 seconds | **9.92899% of one core**; `<10%` diagnostic regression assertion passed |
| Settled interval, from sample at 45 seconds to the end | **1.36471% of one core** |
| PSS snapshots, KiB | 278947, 343186, 281042, 281108, 281017, 280956 |
| Separate subsequent Simpleperf capture | 14.2307 seconds recorded; 46 CPU-clock samples; zero lost |

The first two intervals were 83.755% and 67.267%; those early values were initially interpreted too quickly as continued failure. The complete interval shows the spike settled. The preceding installed build's five-minute results were 81.59% and 92.77%, with 90.85% on the original baseline. The final hidden-progress correction is now physically exercised and its idle regression passes for this debug build/device/flow.

The small subsequent CPU profile supports low settled activity; its 46 samples cannot reliably rank small hotspots or prove absence of every function. Kernel symbol addresses were restricted; no security setting was changed to expose them. The saved gfxinfo snapshot includes startup and navigation before the idle interval because its counters were not reset; it does **not** establish scoped frame/jank acceptance. This is neither a release energy benchmark nor a memory-leak qualification.

Raw local evidence remains outside Git under `C:\Users\slavd\HaloEco\verification-2026-10-28\closure-pass`: `resumed-final-ambient-sample.json`, `resumed-performance-summary.json`, `resumed-final-ambient-gfx-final.txt`, `resumed-final.perf.data`, `resumed-final-simpleperf.txt`, and the `resumed-*` UI captures. Do not publish unreviewed captures or database copies containing unrelated user content.

## Remaining execution

The installed final app/test pair passed **17/17 instrumented cases**, zero failures, in 93.535 seconds. Classes: `MainActivityScreenIntentPhysicalTest` (1), `InterfaceFourDeviceTest` (6), `NoteEditorControlsDeviceTest` (1), `NoteAttachmentControlsDeviceTest` (1), `StudyLearnerControlsDeviceTest` (1), `WorkspaceContinuityDeviceTest` (2), `StudioDeviceTest` (2), `PrivateDispatchOwnerTraceTest` (2), `LocalRolePackRecoveryDeviceTest` (1). Required `runId` and `sourceStamp` arguments were supplied correctly. The main-Activity case exercises real warm/resumed navigation; component/owner cases explicitly use isolated fixtures, and privacy traces use loopback-only synthetic credentials. These are not 17 live provider or microphone journeys. Raw output: `resumed-instrumentation.log`; invocation: `resumed-instrumentation-manifest.json`.

Device regressions and actual user-facing cross-tool chains are in progress. Keep isolated native fixtures, production Activity interaction, live free-provider requests, real microphone/heard speech and artifact qualification separate. The outstanding source-reference failure and the other issue-specific gates in the preceding ledger are not changed to PASS by the CPU result.

## Live source-reference recovery — PASS for the insertion and open-original boundary

Public OpenRouter metadata was refreshed during this resumed session; the effective Power profile and inherited AGENTIC role both selected `nvidia/nemotron-3.5-lightning:free`, with zero prompt/completion prices and tools supported. No provider selection, credential or paid fallback was changed. A paced, exact-draft-checked typed action beginning “Add the note” entered the real action pipeline.

Run `d360348d-9750-4216-9116-d4a777532a0b`, interaction `dfd9f197-3940-4e16-9835-eff9de7fc0e9`, completed. Two malformed discovery calls were rejected as INVALID_ARGUMENTS; the provider then recovered, discovered the real schemas, described both current objects and executed `halo_workspace_insert` with a CONFIRMED outcome. Canonical readback found Canvas `e145715f-9ba8-45db-9045-8abd3c877baa` at revision **7**, containing NOTE node `d35eeccf-c22e-427e-b302-9a58d110d9f6` with exact `refObjectId` `1e543729-b284-4d79-8226-1f20f4ccc618`. The original source remained revision 1 with its exact river/leaves body. The earlier failed TEXT card remains as evidence.

Actual UI then opened that board from Library, displayed both cards, and **Open source** on the new reference opened the original note with the exact body and title. Captures: `resumed-board-open` and `resumed-linked-original`. This qualifies the real reference insertion/open-original boundary; it does not qualify the subsequent export/Study journey. The bounded run's final prose said it stopped after six tool steps; successful persistence is established by the typed receipt and readback, not that generic prose.

## Additional defects and recovery work

- Fast ADB key injection shortened a 337-character requested draft to 154 characters, and another 338-character burst to 282. Paced input preserved a full 337-character literal and subsequent exact 219/222-character requests. The harness now checks the full draft before sending. Human IME/paste versus burst behavior requires the focused physical composer regression; no product fix is inferred from pacing alone.
- Two explanation-routed free-provider attempts returned EMPTY_RESPONSE at a 256-token output budget. They produced no tool effects. Their durable runs (`02c91baa-87ec-43bc-aede-b19b99552954`, `11e9432c-83d0-4e33-8c6b-20b4264ead16`) incorrectly remained EXECUTING after the provider failure. The request's QA prefix/unsupported leading phrasing did not constitute positive action routing; the later ordinary “Add the note” request did.
- A new host regression reproduced the durable failure defect: expected FAILED, actual EXECUTING. `CognitiveTurnExecutor` now calls the existing coordinator for the exact failed provider run before rethrowing to the session. Cancellation/deadline handling stays on its established path. The existing coordinator preserves an unknown write as INTERRUPTED and does not replay it; a second regression covers that boundary.
- Affected host gate: **31/31 PASS** before adding the unknown-write case. Subsequent **full cognition gate: 396/396 PASS**, zero failures/errors/skips across 96 classes, and both APKs built successfully in **7m36s**. These counts overlap and are not cumulative unique tests. Installation and fresh physical failure/recovery qualification are tracked separately.
- The submitted literal was again visible in the composer after the successful live run. A focused real-MainActivity test now checks canonical clear propagation and long hardware-key input. Its canonical clear is an explicit fixture operation, not a fabricated provider submission. Results remain pending at this checkpoint.

The owner has confirmed availability for the remaining genuine spoken commands and listening checks. Testing continues across midnight into 2026-10-29 Europe/Riga; the document name retains the resumed session's start date.

## New runtime APK and current spoken proof

Installed the runtime correction APK, independently matched by installed `base.apk` SHA-256: `0ecef26967ee49884e417c42f1bab4f7800e948d358bab0c88f3d90dd19dfe96`. Source is baseline `37635608d` plus the recorded local runtime correction, not a published new commit yet. `resumed-runtime-build-manifest.json` records artifact identities. The first focused composer device invocation failed twice during setup with `StaleObjectException` while a navigation transition replaced accessibility nodes; neither case reached its product assertion. The observation loop now retries that specific transient node-read failure. No UI production fix is attributed to this harness correction.

On this new app, the owner tapped the actual ring, said **“Open Workspace”**, and confirmed **“all good”** in direct response to the question asking both navigation and heard reply. The actual production Library was then captured. Native trace: interaction `ea3b8154-9068-4e5a-b023-1a9961319a57`, run `aa9eaf8c-9403-4f7b-afdd-932893eed783`, `halo_open_screen`, native result DISPATCHED, 3144ms. The rendered Library and human confirmation qualify the presentation/audibility boundary beyond dispatch. Speech log records requested `provider.elevenlabs.tts` but effective **`provider.android.tts`** with `fallbackAllowed=false`; no ElevenLabs synthesis claim is made.

The QA Canvas is now prepared for an actual spoken export and, if requested by the runtime, a delayed human approval. That scenario is **IN_PROGRESS**, not yet PASS.

### Spoken export attempt — FAIL; speech-language mismatch identified

The owner reported that Halo only described what it would do and nothing happened. Scoped readback identified run `a3884766-9c5e-48ac-bdb3-16d3dc613aca`, interaction `214c3585-13d0-4c2b-95e9-9fd248e72d59`. Its actual recognized request was **“экспорт дисканвас, как новая нотка, которая называется Voice QA Xport.”** The agent had incorrectly given the participant an English test phrase while the recognizer was operating in Russian. Device trace explicitly records `HALO_STT_BEGIN ... model=whisper-small ... language=ru` and `HALO_STT_DONE ... language=ru`, 5240ms audio, 4460ms load, 6368ms decode, 10922ms total. These are one measured cold recognition attempt, not language/performance qualification.

The malformed final text entered `ConversationIntent` / DIRECT_RESPONSE / ToolUseMode.NONE. The run completed a conversational reply with no tool invocation or export effect. This is **not** a completed export, a delayed approval pass, or evidence that the normal Russian imperative is incorrectly routed. The initial diagnosis of a routing defect was revised after reading the actual transcript. The next reproduction must use the configured recognition language; no routing policy is weakened to accept garbled instructions. Do not change the owner's saved speech/UI language for test convenience.

ADB disconnected before the retry could be prepared. A reconnection request is pending; the owner has not deferred tests again. Automatic rotation was restored at the beginning of the resumed session and no later rotation/density/font override was introduced.

### Composer cause isolated while the phone is disconnected

A rendered host regression now reproduces a newer canonical revision arriving before the page's captured text. The old composer accepts the old literal under that new revision, then refuses the later correct clear as an equal-revision echo. This matches the observed sent text remaining visible until navigation/process recreation. The 13-case interaction suite produced **12 PASS / 1 expected product regression failure**, with EditableText still `Already sent literal` after the canonical clear. Exact red XML is retained under `resumed-composer-lagged-red-results`.

`HomeComposer` now reads text and revision together from one synchronous immutable snapshot of the existing canonical draft owner before reconciling its ephemeral IME buffer. Standalone unrevisioned callers keep their existing fallback. No second draft store, execution path, submission, speech or permission owner was added. The grouped affected composer/draft/submission checks and APK build are running; this is a source correction awaiting green and physical verification. The fast-injection symptom is not relabeled PASS without its device reproduction.

An earlier one-case probe found an actually empty EditableText but incorrectly asserted all merged text was empty, including the legitimate placeholder. That harness assertion was corrected to inspect EditableText specifically; it is distinct from the later genuine stale-literal regression above.

## Latest grouped gate — 2026-10-29

The grouped app build completed in **4m32s** with **47/47 affected host cases PASS**, zero failures/errors/skips: `ComposerDraftStoreTest` 17, `HomeComposerEditBufferTest` 5, `HomeComposerInteractionTest` 13, `HomeComposerPriorityTest` 2, `TextSubmissionStateTest` 10. The genuine lagged-page regression is now green. This is an affected gate, not a new full-app-suite claim. The full cognition **396/396** gate still qualifies the unchanged runtime correction.

The physical test's observation predicates were also tightened so a missing field cannot count as a visibly empty field. The final **test APK only** build passed in 41s. The app APK was verified byte-identical across that test-only change; no second production-app rebuild or new functional source change is implied.

| Artifact | SHA-256 | Current device status |
| --- | --- | --- |
| Both-fixes app | `46e9b75a7c420cbf4097c0bc859adc9f1363a585ba0bb2dbd0552e64ca3d59b6` | **NOT_INSTALLED — device disconnected** |
| Final test APK | `8f92083281aa0bc27cb35345d8bc284d8845dde6f5dc3fde11e9c258fc186fe1` | **NOT_INSTALLED — device disconnected** |
| Last installed runtime-fix app | `0ecef26967ee49884e417c42f1bab4f7800e948d358bab0c88f3d90dd19dfe96` | Actual voice navigation/audibility verified; does not contain the later composer fix |

[Sanitized current gate](issue62/2026-10-29/resumed-gate.json) records SHA-256 values for each changed production/test source as well as artifact identities. Raw green results and manifests remain in the local evidence folder. The existing 17-case device pass was on app `26267c93…`; it is not relabeled a pass of this newly built app.

### Next phone steps after reconnection

1. Verify the actual authorized/unlocked target and current user settings. The earlier automatic-rotation override was already restored; do not overwrite newer user choices.
2. Install the exact both-fixes app and final test APK above; independently check installed `base.apk` SHA-256.
3. Run `com.halo.app.ui.shell.MainActivityComposerPhysicalTest` for actual canonical clear/newer-edit protection and whole-literal hardware-key input. The prior failures were setup/harness failures, not product assertions; the corrected cases remain **NOT_RUN**.
4. Qualify a fresh provider error/cancellation and canonical durable outcome on the new binary, preserving UNKNOWN write handling and newer unsent drafts. Do not alter credentials, make paid calls or replay the earlier runs just to produce an error.
5. Prepare the QA Canvas at its freshly read revision. With the current **Russian** recognizer, ask the participant: **“Экспортируй этот холст в новую заметку с названием Voice QA Export.”** If the runtime requests approval, leave it open for a measured delay, then have the participant explicitly approve and read back the exact saved export. Do not count description-only prose or navigation as export.
6. Continue exact source→export→Study and Studio/Workspace workflows, actual source revisions and saved learning artifacts; include the applicable guarded/negative/restart paths. Complete the issue-specific typed/spoken recipes and Screen routine/continuation/layout gates, not only the isolated owner fixtures.
7. Finish remaining active-capture/interrupt/Mini continuity, supported-language and accessibility/responsive checks, and installed offline artifact qualification where required. Retain explicit unavailable hardware/provider limits and genuine human speech boundaries.

**Issues 55–62 remain open.** The reconnection request is pending; no renewed user deferral or permission to waive acceptance has been received. Publishing these validated fixes does not establish full issue completion.

## Reconnected qualification — 2026-10-29

The owner reconnected the same authorized Redmi. `main` was refreshed and remains at `a3513d4eff51b6304c15053286bf937d87a59e7d`, with zero divergence. Replacement installation of the prepared app `46e9b75a…` and test APK `8f920832…` succeeded; installed `base.apk` independently matches the full app SHA-256 in the table above. No app rebuild was required. Current density 440 and font scale 1.0 were read without changing them; automatic rotation remains available.

The first two-case invocation could not observe Home because Android's notification shade was open. After collapsing that system overlay and observing the actual Home field, the same installed tests ran in 66.601 seconds: **one PASS, one FAIL**. `canonicalClearUpdatesTheRealComposerAndCannotReplaceANewerEdit` passed, including full accessibility/IME literal input, canonical guarded clear reaching the visible field, and protection of a newer unsent edit. `hardwareKeyBurstPreservesTheWholeLiteralInTheRealShell` failed its exact visible-literal assertion. This is not a complete composer PASS. A single-case diagnostic reproduction also failed in 49.409 seconds; concurrent external UIAutomator dumps were killed and supplied no usable text observation. Further evidence must come from the test's own accessibility connection.

The initially suspected landscape Library clipping was checked through its actual scrollable container: a UI-tree-derived swipe revealed the QA board and other saved Canvas cards. The top controls consume initial space but the list is reachable; do not label this as an inaccessible-list defect or change the layout on that premise.

The owner explicitly answered **“Voice testing later.”** Real speech/export/heard-response acceptance is deferred again. Continue independent native/source/UI qualification without requesting more speech or treating that deferral as a waiver of issue acceptance. Exact logs and installation manifest remain outside Git under `closure-pass/reconnected-*`; unrelated notification content is not eligible for publication.

### Actual touch export — PASS for local persistence, separate from assistant approval

Actual UI path on app `46e9b75a…`: Workspace → Canvas filter → scroll to `HALO-QA-20261028-BOARD` → open board → Tools → scroll sheet → Export to note. The editor opened the saved note and displayed its on-phone saved status. Canonical readback identifies new ACTIVE note `976cc0b3-febc-4bde-a315-b2699fbdeb9c`, revision 1, titled `HALO-QA-20261028-BOARD`. Its exact body is:

```text
# HALO-QA-20261028-BOARD

Reference to note: 1e543729-b284-4d79-8226-1f20f4ccc618

## HALO-QA-20261028-SOURCE

Explicit note reference: Amber leaves gather beside a quiet river. The river flows north.
Source: 1e543729-b284-4d79-8226-1f20f4ccc618
```

The original source `1e543729-b284-4d79-8226-1f20f4ccc618` remains ACTIVE, revision 1, with its unchanged river/leaves body. This advances the real note → Canvas → exported-note chain for the touch path; Study, assistant export, delayed human approval and cancellation/stale/interrupted integration acceptance remain separate. Evidence: `reconnected-canvas-touch-export` capture and `reconnected-touch-export-readback.json` outside Git.

### Composer input diagnosis and final native gate — 2/2 PASS

Test-owned diagnostic readback found the same 72-character prefix in the visible field and canonical draft, out of the requested 332 characters. Scoped Android logs from that invocation show repeated `InputDispatcher: Dropped event because it is stale` and failed injection from the shell input process. Those raw `input text` events shared their original timestamp. This establishes an automation input-delivery failure; it does not establish that Halo dropped delivered characters. The failing raw-burst result remains recorded above.

The hardware test now generates the same complete literal as actual keyboard events, timestamps each event immediately before injection, asserts Android's admission of every event, and verifies exact UI and canonical text. It does not replace the input using accessibility setText, mutate the owner to simulate typing, or relax the literal assertion. The prior accessibility/IME clear case remains separate. Test-only compile succeeded in 44 seconds; no production source changed, and the app APK remains byte-identical at `46e9b75a…`.

Installed test APK SHA-256: `9851ed50ad08b294862493949fdb4cd0d64759bc83e29f1bf86b6e978e02767b`. Final physical invocation: **2 tests, 0 failures, 0 ignored, 70.501 seconds**, `OK (2 tests)`. Cases: `canonicalClearUpdatesTheRealComposerAndCannotReplaceANewerEdit` and `freshHardwareKeysPreserveTheWholeLiteralInTheRealShell`. This qualifies those two native composer boundaries; it is not a full human-typing, input-latency or whole-app acceptance claim. Local evidence: `reconnected-composer-fresh-keys-20261029.log` and scoped `reconnected-keyburst-stale-input-evidence.txt`.

### Exported-note → saved Study setup and restart — PASS for the observed boundaries

Reopened the exported note through Library's Notes filter, then used the actual shell full list → Think with halo → Study this note. The four-step setup was navigable. In short landscape its form viewport is small but scrollable; no inaccessible-form failure is established by its initial progress-only viewport. Portrait setup checks temporarily locked orientation inside a `try/finally`; original `accelerometer_rotation=1` and `user_rotation=0` were restored and independently read back after both scripts.

Selected **On this phone** for this QA lesson, then Start lesson. Canonical readback identifies ACTIVE session `35af7be0-c1eb-4430-8e7f-c6c3d358a0a4`, schema 3, ADULT/GUIDED/SOURCE_ONLY, tutorStage ASK, source revision 1. Both `sourceObjectIdsJson` and `sourcesJson` identify the exact exported note `976cc0b3-febc-4bde-a315-b2699fbdeb9c`; sourcesJson records revision `1`. The current exercise, conversation, run and interaction IDs are null. This is real persisted setup, not generated tutor output or completed practice.

Force-stopped Halo, launched Study through its real screen route, scrolled past due reviews, and resumed the newest note lesson. The actual UI returned to the same “What do you already know?” stage with one material. Its source inspector reported **1 available / 0 requiring attention**. ADB disconnected before the expanded inspector's exact title could be read; exact identity is established by the preceding canonical record, not an invented on-screen title observation. Evidence: `reconnected-export-study-readback.json`, `reconnected-export-study-started`, `reconnected-study-resumed`, and `reconnected-study-resumed-source` outside Git.

The note → live Canvas reference → touch-exported note → saved source-linked Study setup chain now has concrete individual persisted effects. Tutor follow-ups, exercise/progress creation, review/practice, stale/rejected/recovery paths, real assistant export and delayed human approval remain **NOT_VERIFIED**. The phone is disconnected again; voice remains deferred by the owner. No issue closure is justified by this partial chain.
