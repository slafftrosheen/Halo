# HALO-STABILITY-141 — maintainer handoff

Source-only implementation directly on `main`. Initial fast-forward pull reported already up to date at `761e8ba5c28f7a14f0221ff53c727cac180267b3`. Reviewed batch 140 commits `092e64d6`, `0d433e95`, `351e9253`, `11e7c3e8`, and `761e8ba5` and retained their pinned execution, cancellation, idempotency and privacy contracts.

**No builds, Gradle tasks, tests, instrumentation, emulator, ADB, device operations, GitHub Actions or CI were executed.** No new APK was produced or installed. Source inspection and static hygiene checks do not establish compilation, native runtime behavior or alpha acceptance.

Static hygiene review covered 28 changed files: UTF-8 decoding, replacement characters and duplicate Kotlin imports reported no findings; `git diff --check` reported no whitespace errors.

## Implementation groups

| Group | Commit | Implemented behavior |
| --- | --- | --- |
| A | `e97c31ec` | Artifact protection includes initializing and failed native owners. Readiness reconciles cached verified artifacts against current file metadata and re-verifies changed files. Missing selections update provider status. Pending generation admission is bounded to 32 requests with a retryable failure. |
| B | `79f01f8c` | Semantic indexing, retrieval and embedding diagnostics share the selected model's embedding executor instead of constructing separate native owners. Preprocessing selection requires exact pinned artifact metadata. Explicit source consent still gates indexing and retrieval. |
| C | `549951a0` | Study persists bounded drafts, submission identities and pending tutor request identities using the existing lesson store. Reopening restores saved work and identifies interrupted replies without automatic redispatch. Review retries reuse the submission identity; draft edits invalidate it. Late completion cannot clear a newer pending request or recreate deleted recovery data. |
| D | `1f3b4b95` | Study narration cancellation addresses its owned transaction through the existing speech coordinator. Stopping an idle reader cannot stop unrelated Home speech. Passage changes have independent generation counters, and terminal events clear only their matching owner. Direct speech admission is serialized with Stop. |
| E | This commit | Router configuration revisions reject intervening A-to-B-to-A edits during recommendation preflight. Exact model readiness also filters cached role candidates. Missing/invalid artifacts and native load memory failures receive distinct safe recovery guidance. Study restores saved lesson state even when material lookup fails. Reader Stop cancels pending event publication. Cancelled native loads check cancellation before publishing readiness and release late allocations. |

## Source paths reviewed

- Conversation requests retain the existing UI/workload/router path. Exact provider/model pins flow through the existing role requirements and target eligibility checks; the provider serializes load and generation through its load mutex and the session owns native lifecycle/generation serialization. Text requests validate physical inventory before capability checks. Generation and stream errors retain typed outcomes. No protected-workload cloud fallback was added.
- CHAT, FAST, REASONING, VISION and AGENTIC retain the existing role consumers and exact target validation. EMBEDDING remains a dedicated embedding adapter consumed by semantic indexing/querying and workload diagnostics, not a chat completion. Role candidate checks are metadata/readiness checks, not native initialization. Family-specific embedding preprocessing is only selected for a matching pinned artifact. Names such as LFM, Qwen, Gemma, Granite and Bonsai do not grant capabilities.
- Study retains repository-owned source permissions/revisions, deterministic exercise checking, review idempotency and stage/mastery persistence from the existing implementation and batch 140. New recovery storage complements those owners rather than replacing them. Explanation, source and strongest-language preferences remain independent. Child remote inference still requires the child grant in addition to general cloud consent.
- Voice retains the existing microphone/STT ownership and direct speak-to-answer flow. The new wiring concerns the existing Study read-aloud bridge and host speech coordinator. Home, Notebook and Canvas retain their existing entry points. No duplicate microphone controls or confirmation step was added. Existing Supertonic/Kokoro provider selection, version identity, language checks and batch-140 replacement cancellation remain in place; no new multilingual performance assertion is made.
- Structured error codes drive safe display/spoken memory and artifact recovery text. Native failure causes remain attached for developer diagnostics; Study uses the safe presenter for these provider failures. Existing timeout, cancellation, permission and unsupported-capability classification remains in place.

## Added regression coverage — NOT EXECUTED

Thirteen focused regression cases were added in this batch:

1. `LocalModelSessionResidencyTest.initializingAndFailedNativeOwnersKeepTheirArtifactProtected`.
2. `LocalModelSessionResidencyTest.cancelledNativeLoadCompletingLateCannotPublishOverReplacement`.
3. `LiteRtInventoryTest`: physically deleted cached artifact stops resolving as ready.
4. `WorkspaceEmbeddingRoutingTest.selectedEmbeddingExecutesIndexAndQueryWithOneOwnerAndExplicitSourceConsent`.
5. `StudyLessonCountTest.interruptedAttemptKeepsDraftAndSubmissionIdentityAcrossStoreRecreation`.
6. `StudyReaderRecoveryTest.interruptedGenerationRestoresDraftWithoutReplayingTheTutor`.
7. `SpeechPlaybackLifecycleTest.studyStopIsScopedAndDifferentPassageMayRestartItsGenerationCounter`.
8. `SpeechPlaybackLifecycleTest.scopedStopBeforeAdmissionSuppressesQueuedReadWithoutStoppingCurrentOutput`.
9. `AiProviderRouterTest`: configuration revision detects intervening assignments even when values return to A.
10. `RoleModelFabricTest.cachedInstalledRoleCandidateStillRequiresExactModelReadiness`.
11. `LiteRtLocalProviderStreamTest.missingArtifactIsNotMisreportedAsUnsupportedTextInEitherExecutionPath`.
12. `AiErrorPresenterTest`: structured memory/artifact recovery does not expose native traces.
13. `StudySourcesTest.childLessonCannotUseAdultCloudConsentWithoutTheChildGrant`.

Existing batch-140 source regressions also cover switching during active inference, cancellation followed by another request, pinned-provider failure without fallback, stale review outcomes, source revocation, duplicate mastery scheduling, queued speech cancellation and provider replacement during synthesis. These were inspected, not executed again.

## Maintainer alpha scenarios — pending execution

1. For supported pinned local artifacts, try cold/warm text requests, cancel during native initialization, then select another model. Observe that the obsolete allocation is released before replacement and that the next request retains its exact identity. Repeat cancellation during generation; check later tokens are suppressed and the next request works.
2. Remove/replace an installed artifact outside Halo, revisit roles and request it through both generation APIs. Expect missing/invalid-artifact recovery; no unsupported-text misclassification or remote substitution. Try deletion during loading and failed teardown; Halo must preserve native ownership protection.
3. Change role selection A-to-B-to-A while measured recommendation preflight is waiting. Expect rejection of the old recommendation. Change backend/context/evidence and verify stale qualification cannot be applied. Confirm vision/agentic rejection for targets without verified capabilities.
4. Select a supported embedding artifact, explicitly allow one real source, index it, then retrieve related context. Inspect exact artifact SHA, dimension, preprocessing and native resource ownership. Disable consent and revoke/delete the source; no unauthorized excerpts may return. Confirm no chat provider request occurs.
5. Start a topic-only and a source-grounded Study session, enter an answer, leave/reopen and restart the process. Interrupt tutor generation, reopen and retry explicitly. Expect restored draft/history/stage and no automatic generation replay. Repeat review submission around process death: one attempt/mastery/review update per persisted submission identity.
6. Delete/revoke a source during tutor execution and while reopening a lesson. Expect saved lesson/draft recovery and an actionable source notice, never accepted stale feedback. Check deterministic versus AI-generated feedback labels and the help ladder. Switch explanation/strongest languages without changing source wording or progress.
7. With a child lesson and adult cloud consent only, attempt a remote tutor turn. Expect no remote request until the child-specific grant is present. Verify safe visual and spoken errors do not expose internal traces.
8. Start Home speech, stop an idle Study reader, and confirm Home continues. Read a passage, immediately replace it with another lesson/passage with a lower playback counter, then Stop/barge in before and during synthesis/playback. Expect only the current owner to stop and no stale queued read or fallback. Repeat Stop while event publication is backpressured.
9. Replace Supertonic/Kokoro provider or model version during synthesis. Check obsolete buffers cannot play, audio focus is released correctly, language support is honored and the next utterance works. Exercise current Notebook/Canvas read/dictation entry points and direct speak-to-answer without transcript confirmation.
10. Exercise memory pressure, provider timeout, invalid artifact, denied microphone/source permission and native teardown failures. Check safe recovery text, retained developer diagnostics, cancellation classification and subsequent request recovery.

## Remaining limitations and acceptance boundary

- Compilation and every regression above remain unverified by instruction. Physical STT/TTS, audio focus, latency, model-family behavior, native memory use, provider quality and end-to-end alpha acceptance require execution later. Do not mark device/runtime acceptance complete from these commits.
- Some lower-level native initialization/fallback paths still use best-effort close helpers. Session ownership retention and strict unload do not establish that every native SDK close failure is recoverable. Native close/fallback failure injection and device checks remain necessary; this batch does not claim universal native resource safety.
- Changed-file readiness uses file existence/size/mtime as a fast invalidation signal, followed by existing physical verification. It does not continuously hash unchanged resident files. Recommendation application explicitly verifies the physical artifact.
- Recovery drafts are bounded to 4,000 characters in the existing local Storage owner. This adds no transactional write spanning Storage and Room. Repository submission idempotency remains the authority across retries; forced process death around cross-store operations still needs alpha verification.
- New recovery notices follow the existing dynamic error-text path. Complete EN/LV/RU localization of those messages is not established here. No BrandBook/DevKit assets, visual layout, kernel architecture, BLE protocol or CI configuration changed.

Do not close broader issues solely on this source handoff. Close only acceptance items supported by their required evidence.
