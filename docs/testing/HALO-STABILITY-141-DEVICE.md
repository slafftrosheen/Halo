# HALO-STABILITY-141 — build and physical verification

Date: 2026-10-19. Device: Redmi 23124RA7EO, serial `f01fe673`, Android 15. The follow-up request explicitly authorized builds, installation, testing and fixes; the original source-only restriction no longer governed this pass. No GitHub Actions or hosted CI was used.

## Fixes found by execution

- `1bad40bb`: the first regression run caught role candidate enumeration making 15 readiness calls where the browsing contract requires zero provider calls. Restored cached-metadata browsing; exact readiness/artifact validation remains at execution and recommendation preflight. The replacement regression checks missing/restored artifact metadata without provider calls.
- `c6c6ece2`: added an opt-in physical Supertonic EN/RU/LV preview check. It verifies non-empty PCM and unchanged selection/approval state, then releases the engine. It does not mark output audibly confirmed.
- `f2eb4177`: that physical test reproduced a fatal JNI abort in Supertonic. Sherpa requested `invoke(float[]): Integer` on an indy-generated lambda that did not expose that concrete method. Renamed the existing kept Kokoro callback to shared `SherpaGenerationCallback` and used it in both providers. The existing reflection/cancellation contract now covers the shared JNI bridge. The same physical Supertonic case passed after this fix.

## Build, tests and installation

- Initial debug build at `59fe2a3e` succeeded and was installed with `adb install -r`.
- After the browsing fix, 159 focused JVM/Robolectric cases passed: 55 AI/router/error, 36 LiteRT lifecycle/inventory/stream and 68 app embedding/Study/speech/recommendation tests.
- After the JNI fix, the callback contract and all 18 speech playback lifecycle cases passed again (19 cases in that rerun). Debug app and instrumentation APK assembly succeeded.
- Final production source stamp: `f2eb41772f4a`, `HALO_GIT_DIRTY=false`. Later report-only changes do not alter that APK's production source.
- Final debug and instrumentation APKs were installed successfully. Host and installed application APK SHA-256 both equal `607bfad65ee533be5c6e2028b1d9c547d1b3816346acbffec9af6a2cf5cb29ed`.

## Physical observations

On the initial APK, Home returned `4` for the arithmetic smoke prompt through the configured cloud provider. Notebook and Study navigation opened. A new topic-only Study lesson was created; its draft survived a forced process restart and reopening. An explicit question from the recovered lesson produced tutor feedback displayed as unverified. Study read-aloud entered preparation and Stop returned it to idle. Microphone activation entered Listening and Stop returned it to idle.

The user then performed a real Russian speech check and confirmed **“Answers aloud without confirmation.”** Content-free logs correlated capture, Whisper Small recognition, accepted speech, provider generation, ElevenLabs synthesis and verified phone playback route. This verifies the direct speak-to-answer interaction for that run. It is not a general latency or provider-quality claim, and the human voice check preceded the later isolated Supertonic callback fix.

On the final APK, `installedSupertonicPreviewProducesPcmWithoutChangingApprovalOrSelection` passed on the physical phone: EN/RU/LV produced non-empty PCM and selection/approval state remained unchanged. This is synthesis evidence, not a human listening confirmation for those three previews.

Kokoro's physical synthesis check could not meet its precondition: no complete Kokoro pack was installed (only an incomplete download was present). No pack was downloaded, removed or silently substituted to turn that result into a pass.

The explicit installed Qwen CPU/provider verification result is recorded below when the run completes.

## Evidence and limits

Local receipts are in ignored `build/hil/stability-141/`: assembly/regression logs, restored-draft/tutor UI snapshots, `voice-safe-trace.txt`, before/after Supertonic runner receipts, native crash trace, and `installed-apk.json`. Raw UI snapshots are local-only and are not committed.

This is a focused verification pass, not exhaustive acceptance of every model family, backend, tool, wearable, privacy permutation or screen. Child restrictions, review idempotency, embedding execution and source revocation were covered by focused automated tests; physical child PIN flows and wearable audio were not exercised. Kokoro remains blocked on an installed pack. Do not close those broader acceptance items from this report.
