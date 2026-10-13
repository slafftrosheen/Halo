# HALO-ALPHA-DOGFOOD — 13 October 2026

Scope: existing Android app resolution, speech routing, streamed-speech lifecycle and private STT diagnostics. No provider replacement, architecture rewrite, new user feature or hosted CI run.

## Evidence boundaries

The supplied `Pasted text.txt` contains 4,396 lines from 11:40:58.471 through 11:43:49.554. It does not contain the reported 11:34:53 `halo_open_app` rejection, five `NO_AUDIBLE_CHUNK` events, Bluetooth route events, ElevenLabs events or STT events. Those earlier observations come from the task brief, not this capture.

4,090 lines have the MI-SF tag. SurfaceFlinger/MIUI rendering error severity alone is not a Halo runtime error. The capture preserves concrete target resolution: VISION selected local `gemma-4-e2b` at 11:41:15 and 11:41:20; AGENTIC selected `openrouter` / `aion-labs/aion-2.0` at 11:41:31 and 11:42:30. These target-selection records do not prove response success, speech quality, Free routing or a pinned binding.

## Changes and traced causes

### App resolution

`ApplicationTools` previously called the nullable resolver and collapsed NotFound and Ambiguous to `app_missing_or_ambiguous`. It now consumes distinct resolution results and emits distinct `app_not_found`, `app_ambiguous` and `app_visibility_unavailable` diagnostic reasons. Rejected calls retain no action outcome and native truth remains NONE; dispatch remains DISPATCHED, never confirmation.

The manifest already declares MAIN/LAUNCHER package visibility. No broad package permission was added. Launcher activities are deduplicated by package, a missed cached lookup refreshes the catalogue, and list-apps includes system launcher apps. Exact packages and labels resolve first; fixed aliases select only actually visible installed packages. Partial names remain suggestions and require an exact target rather than choosing an app. The resolved package is validated and passed unchanged to deterministic execution.

Visibility-unavailable means Android denied catalogue access or returned no usable launcher catalogue. A NameNotFound observation cannot prove whether an app is absent or hidden by Android visibility; feedback explicitly says not visible to Halo, rather than claiming an uninstallation.

### Native voice route

NativeCapabilityDispatcher formerly assigned Phone to all non-device origins, which explicitly requests PHONE_SPEAKER. It now uses the same responseTarget policy as conversation: phone-origin input uses HostDefault and lets Android select A2DP/default media routing; device-origin input retains the exact device endpoint with DISALLOW_PHONE. Explicit phone/device selections continue to be enforced by the existing route controller. No wearable-to-phone override was added.

### Stream terminal lifecycle and playback

A completed stream previously remained referenced. Later Stop/New response could append CANCEL to the completed transaction. Completion now retires that reference, stream completion/abandonment is idempotent, and a completed TTS publication cannot subsequently publish cancellation during cleanup. Duplicate finalization cannot drain the prefix digest or resend a suffix.

NO_AUDIBLE_CHUNK remains intentional final-authority playback when nothing was audible, including short or buffered replies. The old unstarted stream is abandoned before full-response playback; late start callbacks are rejected. Once audible, only the final suffix is eligible, and a mismatching final response stops instead of replaying the answer. Receipt guards retain one final response per transaction. The five reported physical events require the earlier capture/retest to classify individually.

Stream diagnostics retain full request, interaction and capture IDs, chunk counters and existing timings. HostDefault is labelled SYSTEM_DEFAULT instead of DEVICE. TTS exception logs expose only error class and transaction ID rather than arbitrary exception content. ElevenLabs selection and synthesis behavior are unchanged.

### STT

The existing ReloadableSpeechRecognitionProvider already serializes teardown and keys recognizers by model, language, revision and effective CPU configuration. Equivalent language formatting is now normalized so `en` and ` EN ` do not churn the same recognizer. Tests cover Omnilingual en → ru → lv → en and repeated unchanged refreshes. Native model residency remains subject to the existing resource manager, not permanently pinned.

The existing private Speech Lab already reports cold/warm load/decode/total/RTF, audio/VAD evidence, transcript gate and optional reference-based WER/CER. Its summary now explicitly labels absent provider confidence as `not provided`. Sherpa returns null confidence; no confidence is synthesized. Audio/results remain in memory, and copy excludes transcripts/reference unless explicitly selected.

No numerical latency improvement is claimed. The supplied crop has no STT/stream timing evidence, so cold-load policy and provider handoff were not speculatively changed.

## Manual retest matrix

| Check | Retest | Pass evidence |
|---|---|---|
| App launch | Speak exact installed label and known alias; repeat with explicit package | Requested package only; one launch dispatch; no provider turn; DISPATCHED until actual foreground verification |
| Missing / ambiguous | Missing app/package; two equal labels; partial app name | Distinct not-found/ambiguous feedback and diagnostic reason; no launch; actionTruth=NONE |
| Visibility | Restricted/unavailable catalogue where reproducible | Visibility-specific feedback; no launch or guessed substitute; record observed visibility boundary |
| Languages / STT | Omnilingual en → ru → lv → en; repeat unchanged language/model | Changed-language recognizer reload; unchanged configuration reuses delegate; Speech Lab cold/warm load and quality evidence; confidence absent when unsupported |
| Bluetooth | A2DP connected: conversational reply, then native app/action reply | Phone-origin replies use HostDefault and observed A2DP; explicit phone speaker remains explicit; wearable origin uses its endpoint, no unauthorized phone fallback |
| Streaming | Long multi-sentence reply and short buffered reply | One audible answer; no replayed prefix or missing normal suffix; NO_AUDIBLE_CHUNK fallback occurs once when no chunk was audible |
| Stop | Stop mid-synthesis/playback; repeat Stop; Stop after completed reply | One active cancellation; no late speech; no CANCEL following COMPLETE for the same completed stream |
| OpenRouter Free | Select Free and repeat conversation + native app command | Concrete resolved model recorded; real conversational response; native dispatch independent of provider |
| Pinned model | Pin one available concrete OpenRouter model and repeat | PINNED binding and requested concrete model in evidence; same native/action/route truth boundaries |

Capture only Halo runtime tags and endpoint/model/timing/correlation metadata for acceptance; do not export speech, transcripts, credentials or provider payloads. Full physical routing, audible continuity, language accuracy and provider acceptance remain unverified by unit tests.

## Automated validation

Results are appended after the focused local gate. GitHub Actions are not invoked.

## Changed files

- `app/src/main/java/com/halo/app/audio/HaloSpeechOutputCoordinator.kt`
- `app/src/main/java/com/halo/app/audio/StreamingVoiceResponse.kt`
- `app/src/main/java/com/halo/app/di/tools/host/ApplicationTools.kt`
- `app/src/main/java/com/halo/app/speech/diagnostics/SpeechDiagnosticRunner.kt`
- `app/src/test/java/com/halo/app/audio/ConversationalVoiceTest.kt`
- `app/src/test/java/com/halo/app/ui/screens/settings/SpeechRecognitionProbeTest.kt`
- `host-android/src/main/java/com/halo/host/android/capability/AndroidApplicationCapability.kt`
- `host-android/src/main/java/com/halo/host/android/resolver/AndroidApplicationResolver.kt`
- `libraries/halo-cognition-language/src/main/java/com/halo/cognition/language/AppCommandIntent.kt`
- `providers/sherpa-stt/src/main/java/com/halo/providers/sherpa/stt/ReloadableSpeechRecognitionProvider.kt`
- `providers/sherpa-stt/src/test/java/com/halo/providers/sherpa/stt/ReloadableModelSelectionTest.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/Outcome.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/native/NativeCapabilityDispatcher.kt`
- `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/ToolTurnExecutor.kt`
- `runtime/cognition/src/test/java/com/halo/runtime/cognition/context/ContextAssemblerTest.kt`
- `runtime/cognition/src/test/java/com/halo/runtime/cognition/native/NativeCapabilityDispatcherTest.kt`
- `app/src/test/java/com/halo/app/di/tools/host/ApplicationResolutionRegressionTest.kt`
- `docs/engineering/HALO-ALPHA-DOGFOOD-2026-10-13.md`

The existing context-assembler tests had two stale named `policy` arguments for the three-argument API; those now use `basePolicy`. The spoken-command regression also required recognizing `and make` as an action boundary rather than part of an app name.

### Exact final results

| Suite | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| Cognition | 17 | 0 | 0 | 0 |
| Spoken commands | 12 | 0 | 0 | 0 |
| Sherpa | 8 | 0 | 0 | 0 |
| App | 36 | 0 | 0 | 0 |

Total: 73 tests, zero failures/errors/skips. `:app:assembleDebug` passed; BUILD SUCCESSFUL in 11m 2s. Log: `C:/Users/slavd/HaloEco/halo-alpha-dogfood-verified-build.log`.

APK: `app/build/outputs/apk/debug/app-debug.apk`

SHA-256: `42252df4176f9a29fd7f13c189152064e84812f5973c26d3b2e03a0510cceba5`

The first run stopped on the existing context-assembler test named-argument compilation errors. The second stopped on the existing unsupported compound-app-name regression. Both were repaired and included in the final passing gate. No GitHub Actions or live provider/device acceptance runs were performed. Changes remain local and unpushed.

Command: `gradlew.bat :runtime:cognition:test --tests "*NativeCapabilityDispatcherTest" --tests "*ContextAssemblerTest" :libraries:halo-cognition-language:test --tests "*NativeCommandParserTest" --tests "*PoliteCommandTest" :providers:sherpa-stt:testDebugUnitTest --tests "*ReloadableModelSelectionTest" --tests "*SpeechModelMatrixTest" :app:testDebugUnitTest --tests "*ApplicationResolutionRegressionTest" --tests "*DogfoodHostToolsTest" --tests "*ConversationalVoiceTest" --tests "*SpeechRecognitionProbeTest" :app:assembleDebug --no-parallel --max-workers=1 --no-daemon -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=768m"`
