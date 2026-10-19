# HALO-STABILITY-140 — maintainer handoff

Source-only implementation on `main`, starting from `004edaba18563a04de0d4a2f5334435c15849858` after a successful fast-forward pull. Reviewed `39b4a62`, `ca039dc`, `1e7e73d`, `7e2a8d5`, `84c9030`, and `004edab`. Their mastery-policy version, single tutor preflight, phone-only provenance, exact provider verification and serialized recommendation safeguards are retained.

**No builds, Gradle tasks, tests, instrumentation, device operations or CI were executed for this batch.** No new APK was produced or installed. Earlier device/build receipts do not qualify these commits. Static checks below are not compilation or behavioral proof.

## Changes by batch

| Batch | Implementation commit | Behavior |
| --- | --- | --- |
| A | `092e64d6` | Queued generations retain the runtime instance and residency version they were admitted against. Cancellation targets the request's child job; cancelled requests suppress later tokens. The queue waits for backend cleanup before releasing the generation mutex. Failed load cleanup retains native ownership until unload succeeds. |
| B | `0d433e95` | Adds an optional exact provider/model pin to the existing workload contract. The router reuses its pinned-target readiness/capability/privacy validation and disables fallback for pins. Role recommendation application compares the pre-verification selection under the router's selection monitor; newer manual assignments win. |
| C | `351e9253` | Study executes the preflight target, checks source permission again on completion, and refuses unverified off-phone results when remote permission was denied or revoked. Review transactions compare the complete card snapshot before schedule/event writes. Stale card screens, completed sessions, superseded attempts and cancelled tutor jobs cannot record a new accepted result. Session-start cancellation propagates. |
| D | `11e7c3e8` | Kokoro and Supertonic capture stop epochs before waiting for native ownership. Stopping Kokoro throws cancellation, preventing synthesis fallback from reviving an old response. Registry pins reject stopped work and late PCM; local voice close/release happens under its existing lock with noncancellable cleanup. |
| E | This document's implementation commit | Native LiteRT unload no longer swallows close failures; ownership, failed readiness and resource accounting survive until retry. Embedding retains a failed teardown owner and refuses replacement until cleanup succeeds. Cancellation cleanup reaches the native conversation even when its caller is cancelled. Speech close attempts every provider, actual fallback metadata follows Android, and offline policy is refreshed before Android synthesis. Router diagnostics preserve newer in-flight requests and cancellation, and Study uses existing normalized error presentation with recovery guidance. |

## Existing owners retained

- `LocalModelSession` and `LiteRtRuntime` retain their per-session load/generation mutexes. No global inference lock or second lifecycle manager was added. Session/activity states already distinguish idle/configured/loading/ready/generating/unloading/error; this batch fixes failed-ready projections rather than adding a competing state enum.
- CHAT remains the persisted conversation selection. FAST, REASONING, AGENTIC and VISION remain `AiModelRole` bindings. EMBED stays in the existing `LocalWorkload.EMBED` / real embedding adapter; STT and TTS retain the speech selector and their model-pack owners. No text-only model gains image, vector, reasoning-quality or structured-tool proof from this change.
- Physical artifact verification, exact SHA/runtime/device/backend/context/thread qualification identities and workload environment evidence remain authoritative. Recommendations do not install, qualify or silently probe a model. No qualification epochs were changed because the runtime/configuration evidence contract is unchanged.
- Study still uses Room sessions, source versions, lesson history, persisted learner preferences, deterministic stage policy and the existing review/mastery policy. The non-JSON local practice-card format is retained. Generated explanations and semantic comparisons remain unverified learning evidence.
- The existing reloadable STT lifecycle serializes recognize/replacement/cleanup; no alternate STT dispatcher was introduced. Audio route ownership and playback remain in the current output coordinator. Home/Mini direct conversation microphones are unchanged.
- No layout, BrandBook assets, onboarding, BLE/device protocol, model download or CI configuration changed.

## Added regression tests — NOT EXECUTED

`LocalModelSessionResidencyTest`:

- `failedLoadCleanupRetainsOwnerUntilNativeTeardownSucceeds`
- `cancelledCallerCancelsItsBackendJobAndDoesNotPoisonNextRequest`
- `replacementWaitsForActiveInferenceBeforeDisposingRuntime`

`AiProviderRouterTest`:

- Preflight pin survives a settings change without mutating the newer selection.
- Pinned failure cannot silently execute on another provider.
- Pinned target cannot bypass phone-only privacy.
- Old completion cannot overwrite newer in-flight diagnostics.

`StudySourcesTest`:

- `selectionChangeAfterVisionPreflightKeepsTheVerifiedTarget`
- `revokedSourceWhileInferenceRunsCannotBecomeAcceptedLesson`
- `staleReviewSnapshotCannotDuplicateMasteryOrScheduleUpdates`
- `recreatedRepositoryRestoresStagePreferencesAndIdempotentAttempt`

`SpeechPlaybackLifecycleTest`:

- `stoppedPinnedResponseCannotStartSynthesisOrFallback`
- `latePcmFromStoppedProviderIsDiscardedWithoutFallback`
- `closeFailureDoesNotSkipOtherSpeechProviders`

Fourteen new test cases in total. Repository recreation in a Room test is not an Android process-death test. Fake runtime tests establish intended ownership invariants only after the maintainer executes them; they do not simulate successful native model execution.

## Review performed

Read source and call sites, reviewed diffs against the synchronized baseline, checked new API references against existing contracts, and checked changed sources for valid UTF-8, replacement characters and duplicate imports. `git diff --check` is the whitespace gate. Remote state was refreshed before implementation commits; no concurrent incoming commits were overwritten.

## Maintainer and alpha acceptance still required

1. Compile the provider SDK, AI engine, LiteRT provider and app, then execute the four named regression classes and their existing neighboring suites. Nothing in this handoff claims they pass.
2. With actual supported artifacts: cancel during initialization, first-token wait and generation; change role/model/backend while a request is running; apply rapid recommendations alongside manual assignments; repeat memory-pressure eviction. Confirm native close retries do not double-dispose or admit a second mapped engine.
3. Verify CPU/GPU fallback and context downshift on device. Unpublished-engine initialization cleanup and generation-time GPU rebuild still use existing best-effort native close helpers; injected native-close failures in those specific paths are not qualified by the session tests. Hard native crashes cannot be recovered within the same process.
4. Requalify changed artifacts/runtime/backend/configurations through existing explicit actions. Check actual image input, complete tool protocol, embedding dimensions and retrieval identity. No new quality/capability qualification is supplied by this batch.
5. Kill and recreate the Android process during Study; verify source revision/permission loss, deleted/partial sources, card edits, two concurrent review screens, language/level/policy restoration and local non-JSON card extraction. Permission checks cannot retract material already sent before revocation; subsequent dispatch/results must be refused.
6. Exercise Kokoro/Supertonic version, voice and language changes, queued/cached synthesis, barge-in, stop during fallback, route loss and shutdown on actual hardware. JNI work that ignores cooperative cancellation still requires backend/device verification. Check that pending-teardown errors lead to unload/retry or process restart rather than false readiness.
7. Confirm the existing source-safety and child cloud restrictions across Study, Notebook and assistant reading. Review new recovery copy in EN/RU/LV during alpha; dynamic normalized errors are not a complete localization acceptance pass.

The implementation is handed off for maintainer compilation and alpha testing; full acceptance remains unverified under the requested execution restriction.
