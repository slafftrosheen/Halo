# Issue 59 source coverage - 27 October 2026

Scope: current uncommitted Android source batch; live issue #59 read on this date.
No build, lint, test, ADB, emulator, native inference, download, provider request,
benchmark, commit, push or issue mutation was performed for this increment.
The earlier batch is preserved. Source presence is not runtime acceptance.

## Concrete additions in this increment

1. `app/src/main/java/com/halo/app/ai/LocalRolePackInstaller.kt` now persists an
   explicit source/license disclosure acknowledgement against the full manifest
   fingerprint. Both starter and optional conversation START/resume fail closed
   without it. Changes to pins, declared license, runtime or source invalidate
   the acknowledgement. Pause/rollback/keep-files still work without consent to
   another download. This records review, not license certification, role
   qualification or an inference permission. Direct UI calls and typed calls
   reach the same admission check.
2. `app/src/main/java/com/halo/app/ui/screens/settings/PrivateOfflinePackDetails.kt`
   exposes that persisted acknowledgement with an accessible combined checkbox
   and label, shared by Settings and onboarding. The incomplete license audit is
   visible before acknowledgement. Optional download stays disabled until review;
   a starter request without review shows an actionable localized message.
   `app/src/main/res/{values,values-ru,values-lv,values-pl}/halo_offline_pack59.xml`
   contains the matching EN/RU/LV/PL strings.
3. `app/src/main/java/com/halo/app/di/tools/LocalModelSetupToolExecutor.kt` exposes
   `offlinePack.sourceReviewAcknowledged` and accepts START's optional
   `reviewedManifestFingerprint` plus `sourceReviewSummary`, matching the current
   manifest and the complete canonical owner-generated review text. Fingerprint
   alone cannot mint acknowledgement. Status `view=SOURCE_REVIEW` with `scope=STARTER` or
   `scope=CONVERSATION_EXTENSION` (default STARTER) returns this
   disclosure separately from action status; its serialized JSON is capped at
   3,800 characters. `PrivateOfflinePack.sourceReviewSummary` includes every
   artifact/source/revision/license/byte declaration, exact manifest fingerprint,
   explicit chosen download scope, localized EN/RU/LV/PL headings and disclosures,
   scope distinction and incomplete-license/qualification disclaimer. Text over
   2,000 characters is unavailable rather than truncated. The parent integration
   sets START to mandatory durable approval and displays `sourceReviewSummary`
   through the existing reviewArgument presentation. Previously acknowledged
   native/typed review permits omission of both fields. Transaction checks and
   never-replay remain; no new registration or DI owner is introduced.
4. `providers/sherpa-stt/src/main/java/com/halo/providers/sherpa/stt/WhisperModelDownloader.kt`
   now accounts for the largest unpublished model's full temporary copy, remaining
   download bytes and the safety reserve. Previously a complete `.download` could
   have zero remaining bytes but need hundreds of MB for atomic publication.
   `LocalRolePackInstaller.downloadPlan` uses that same owner staging estimate,
   including the shared speech-volume budget and cancellable physical checks.
5. `providers/sherpa-stt/src/main/java/com/halo/providers/sherpa/stt/SherpaModelInstaller.kt`
   accepts the caller's cancellation check during each copy/hash chunk and before
   publication; bounds copies by declared size; retains the old target until
   validated atomic replacement. Pause during publication cleans its temporary
   copy and retains the resumable download. Legacy import callers retain the
   default callback. No new downloader or rollback owner was introduced.

## Every issue workstream mapped to production owners

Paths below use these prefixes: `app` = `app/src/main/java/com/halo/app`,
`litert` = `providers/litert/src/main/java/com/halo/providers/litert`,
`speech` = `providers/sherpa-stt/src/main/java/com/halo/providers/sherpa/stt`.

| Issue requirement | Existing/added source coverage | Remaining empirical or owner boundary |
| --- | --- | --- |
| A: exact models, revisions, weights, roles, runtime, storage, languages | `app/ai/PrivateOfflinePack.kt`, `CuratedLocalCatalog.kt`; `litert/models/LiteRtModelCatalog.kt`; `speech/SherpaSpeechModelCatalog.kt`; `app/audio/SupertonicPackStore.kt`. Canonical fingerprints and separate optional conversation acquisition. | Speech license/revision omissions stay unknown. Publisher/redistribution audit is still needed before Tested/Recommended. Actual working-memory, backend and language suitability cannot be inferred from file size or declared runtime. No invented candidate or benchmark replaces this audit. |
| B: curated explicit download, storage, cancel/resume/retry, damaged/interrupted recovery, acknowledgement, unsupported states | `app/ai/LocalRolePackInstaller.kt`; `litert/models/LiteRtModelManager.kt`; `speech/SpeechModelInventoryRepository.kt`, `WhisperModelDownloader.kt`, `SherpaModelInstaller.kt`; `app/audio/SupertonicPackStore.kt`. Durable ownership receipts; exact source-review admission; fresh bytes; corrected publication space; chunk cancellation. `app/ui/screens/settings/OnDeviceModelsScreen.kt` retains developer-only broad discovery. | Exact fresh/upgrade/low-disk/process-death/recovery/native-unload behavior awaits execution. Pack availability requires the existing source owners; insufficient/unknown space never implies support. RAM disclosure is an evaluation target, not certified eligibility. |
| C: deterministic safe commands; advisory qualified classifier; 2s limit; clarification instead of forced cloud | `app/ai/LocalIntentModelRouter.kt` requires exact qualification, resident matching model, idle inference and a 2,000 ms budget. `app/ai/WorkloadQualificationStore.kt` owns intent-specific evidence. Existing native intent and ToolRegistry/ToolExecutor authorization remain in charge. | Wrong classification, ambiguity, duplicate/replay, permission and audible acceptance remain unexecuted. Historical 9,580 ms CPU result remains negative scoped evidence. Installer cannot promote it. |
| D: exact-device/backend performance, coexistence, cancellation, pressure, eviction/switching | `litert/runtime/LocalModelSession.kt`, `LocalModelPreloadCoordinator.kt`, `LiteRtMobileBackendPolicy.kt`, `LiteRtBackendQualification.kt`, `LocalModelBenchmarkRunner.kt`, `LocalModelRoleQualificationRunner.kt`; `app/audio/HaloAudioSessionCoordinator.kt`; `app/ai/LocalRoleEvidenceController.kt`, `WorkloadQualificationStore.kt`. Reused owners; no automatic qualification during setup. | Cold/warm, TTFT, endpoint-to-final-to-first-audible, 10-turn, peak RAM, thermal and energy characterization are still empirical work. No source-only percentile or GPU suitability claim. Existing runners do not themselves establish every requested measurement. |
| E: hard Private boundaries; independent persisted profiles; actionable missing-role status; manual tools | `app/ai/PrivacyModeStore.kt`, `ModeProfileStorage.kt`, `SpeechSynthesisRegistry.kt`; existing runtime provider/context dispatch owners. Pack status distinguishes missing physical roles, selection and unattested qualification; it never selects a route or enables cloud. | Network traces and process-death/profile-switch acceptance pending. This focused installer audit does not certify every AI/STT/embedding/context dispatch boundary. Parent/runtime owner retains end-to-end policy responsibility. |
| F: download/verify/conversation/optional benchmark, exact readiness, accessibility, EN/RU/LV/PL | `app/ui/onboarding/HaloSetupScreen.kt` (including `LocalAnswerSetup`); `app/ui/screens/settings/PrivateOfflinePackDetails.kt`, `OnDeviceModelsScreen.kt`; shared source disclosure, explicit inventory inspection and exact estimates; existing local answer/model-role/probe owners. Source review is persisted and localized. | A usable first offline conversation and supported speech languages require real model and microphone/audible evidence. The UI intentionally does not label installed files Ready. No claim that translated controls establish multilingual speech. |

## Every acceptance item and evidence boundary

| Issue acceptance item | Relevant source/record | Status |
| --- | --- | --- |
| Fresh/upgrade install; wrong SHA; low disk; pause; kill/resume; deletion; failed unload; preserve old working file | Installer transaction/manager ownership, exact Sherpa and Supertonic byte checks; staging-space and cancellation additions above | Source implemented for acquisition paths; all device scenarios NOT_RUN |
| Airplane conversation, 10 turns, 5 typed and voice journeys, local playback, no hidden network | Existing CognitiveSession/native tools, local providers, speech coordinator and Private policy; no alternate execution route added | NOT_RUN; network trace and native voice evidence required |
| Separate latency stages, median/p95, RAM/thermal/energy, backend on 8GB target | Existing exact role/workload qualification and benchmark owners | NOT_RUN; no generated metrics or Recommended status |
| Preserve 2s classifier limit; wrong classification/no model/clarification/permissions/replay/interruption | `LocalIntentModelRouter`, workload proof owner, existing tool authorization and audio ownership | Limit retained; empirical acceptance NOT_RUN |
| Independent recognition/voice quality for usable languages; no false Ready | Manifest declarations separate from integrity inventory and qualification; explicit UI caveats | NOT_RUN; EN/RU/LV/PL resource coverage is source only |
| Exact pack/device/API/RAM/APK/model fingerprints, real mic/audible evidence, regressions/limits | Versioned pack fingerprint, canonical model pins, exact workload environment owners, earlier `ISSUE-59-CODING-2026-10-26.md` plus this report | Pack/source identity exists; new APK/device/audio evidence NOT_RUN |

## Boundaries that code cannot certify

The publisher license/redistribution audit, chosen supported device/backend set,
first working conversation, supported speech languages and performance thresholds
still need external evidence and owner decisions. Keep issue #59 open. This report
is not a statement of 100% product acceptance or a licensed/Recommended pack.
PocketPal/ChatterUI code was not reused; no dependency or license claim was added.
Any comparative product research remains separate from this source implementation.

Deferred regression source was updated in `LocalModelSetupToolExecutorTest.kt`
and `LocalRolePackRecoveryTest.kt`: existing valid-start fixtures carry explicit
review state; new cases cover fingerprint-only refusal, complete localized bounded
scope-specific status, wrong-scope/modified review refusal, exact extension
admission, durable review and revocation. These are source additions only; none
were run. Staging-space/cancellation execution and all prior empirical gates
remain NOT_RUN.
