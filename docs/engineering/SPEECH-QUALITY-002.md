# SPEECH-QUALITY-002 — Real STT Probe and Speech Lab

Implemented on 2026-10-01 on top of SPEECH-QUALITY-001. Hardware recognition quality remains unvalidated.

## Capture and isolation

`SpeechDiagnosticRunner` is an app composition component with no EventBus, cognition, selector, tool, history, or storage dependencies. It calls `AudioPerceptionEngine.captureDiagnosticAudio`, which reserves the engine's transaction mutex and uses the canonical phone AudioSource and the production preprocessor. It rejects a normal capture or decode already in progress. Normal capture requests during diagnostic capture or decode/cleanup are rejected immediately. Recognition comparisons reserve the same engine transaction until native cleanup completes, and reject an active normal capture/decode. Samples are capped at 15 seconds, downmixed/resampled to 16 kHz mono PCM without gain changes, and the source is stopped on completion, cancellation, and failure. Engine stop also cancels and joins diagnostic capture.

Diagnostic PCM is held only in memory and zeroed when the session is replaced or the screen leaves. Cancellation during blocking native decode waits for decode and stream cleanup before wiping PCM. No diagnostic transcript reaches production speech events, cognition, tool dispatch, or conversation history.

## System Check

Separate rows report microphone permission, processed PCM capture, Silero detector availability, selected STT, verified selected files, and recognition. Permission does not imply capture success; captured PCM does not imply STT success. `CAPTURE PCM` captures without requiring a recognition model. `RUN STT PROBE` captures then invokes the selected buffered provider. Only a nonempty decoded transcript passes recognition. Gate admission is shown separately and can reject a decoded result that still passes the recognition probe.

The prompt is “Say: Halo speech test” or “Saki: Halo runas tests”. Capture ends after seven seconds or the explicit Finish action. Transcripts appear only in local UI state and are absent from System Check summary exports. The old MediaRecorder/AMR file probe has been removed.

## Speech Lab

Settings → Voice → Speech recognition → Speech Lab. Install verified models in Speech recognition first. Capture one sample, optionally select a local reference phrase, then choose installed models. Every comparison receives the same AudioBuffer and sample contents. Comparisons use transient recognizers, not selected-provider switching. Model selection and the persisted language mode are never changed.

Each model/language variant gets one fresh cold decode, followed by one or three warm decodes using the same transient recognizer. Native stream cleanup completes between decodes. Cold and warm statistics are grouped separately; each row reports load, decode, total, and RTF, with decode median/min/max per group. Warm cached runs report loadMs=0. If external resource pressure causes a reload before a warm run, its result is labeled as a cold reload and excluded from warm statistics. Models run serially, with close and native quiescence completed before creating the next recognizer. Both normal reloadable STT and Speech Lab use `SherpaSpeechRecognizerFactory`. They share the native resource coordinator; no second HEAVY_ASR owner is allowed to run concurrently.

The optional Whisper language comparison repeats the same PCM with fixed language and auto. The language shown is configuration, not a claimed detected language. Omnilingual CTC does not gain a language detector or new decoding capabilities from the Lab.

Each session records an id, timestamp, source, requested language, reference, PCM metadata, acoustic estimate, and local result rows. No session files or reference/transcript persistence are created. Copy summary excludes transcripts and the reference unless the explicit “Include transcripts and reference in copy” checkbox is enabled; it defaults off.

## Quality evidence and metrics

`RecognitionQuality` holds acoustic/gate evidence. Provider-neutral `SpeechRecognitionMetrics` holds provider/model identity, configured language, load/decode/total time, and audio duration. RTF is decode time divided by audio duration and is unavailable for zero duration. Metrics propagate through RecognitionResult, speech events, accepted observations, and rejected Activity entries without Sherpa types in runtime contracts. Activity retains one accepted observation per transaction and expandable execution/acoustic/gate details.

Audio diagnostics show duration, original/processed sample rate, peak, RMS in PCM16 units, voiced duration/ratio, segment count, longest voiced segment, and estimator source. Conservative warnings identify low amplitude, possible clipping, little detected voice, and energy fallback. Explicit diagnostic capture has zero pre-roll; leading/trailing silence is not fabricated from aggregate estimates. No AGC, amplitude normalization, gate tuning, or ASR-family additions are included.

WER and CER use deterministic Levenshtein distance after NFC Unicode normalization, Locale.ROOT lowercasing, punctuation/symbol removal, and whitespace collapse. Latvian diacritics remain significant. Reference phrases include six English and six Latvian everyday requests. Empty references are not automatically scored in the UI.

Failures identify CAPTURE, PREPROCESS, VAD, MODEL_RESOLVE, RESOURCE_ACQUIRE, MODEL_LOAD, DECODE, RESULT, or TRANSCRIPT_GATE where known. Cancelling JNI requests discards the late result, waits for cleanup, and starts no further model. Failed cold loads do not produce misleading warm statistics.

## Validation and earlier failures

Required commands: `:providers:sherpa-stt:test :engines:speech:test :runtime:speech:test :app:testDebugUnitTest :app:assembleDebug`. Observer tests are also run because Activity provenance changed. Final combined validation completed successfully (`BUILD SUCCESSFUL`). All required commands passed, together with observer tests:

| Suite | Tests | Failures |
| --- | ---: | ---: |
| providers:sherpa-stt | 18 | 0 |
| engines:speech | 17 | 0 |
| runtime:speech | 2 | 0 |
| runtime:observer | 11 | 0 |
| app:testDebugUnitTest (unfiltered) | 143 | 0 |
| **Total** | **191** | **0** |

`:app:assembleDebug` passed. APK: `app/build/outputs/apk/debug/app-debug.apk`. `git diff --check` also passed. Compile/test failures encountered during implementation were fixed and the complete command rerun; no failing checks remain. These results establish compilation and unit behavior, not physical recognition accuracy or real JNI timing.

The six app-test failures reported in 001 are repaired: migration tests share the complete production migration registry and current database version, and accessibility ownership expectations include the three existing tools. No migration SQL or accessibility behavior changed. Their targeted tests pass.

Tests cover microphone ownership and preprocessing, no capture events, stop/cancellation, identical PCM across targets, serial cleanup, cold/warm grouping, blocking-decode cancellation, private summary exports, stage failures, decode-versus-gate verdicts, scoring/diacritics, and neutral metrics propagation for accepted and rejected observations.

## Physical-device acceptance (pending)

1. Grant microphone permission and run capture with no model installed: PCM may pass; STT must not pass.
2. Install Tiny/Base, choose lv, run the Latvian prompt, and inspect local transcript and gate separately.
3. Capture each provided reference once; compare installed Tiny/Base/Small and optional Omnilingual models serially. Record cold and warm latency independently.
4. Compare fixed Latvian versus Whisper auto on that same PCM. Do not report auto as a detected language.
5. Cancel capture, cancel a long native decode, and leave the Lab during decode. Verify microphone release, eventual native cleanup, no next-model load, and no conversation/tool activity.
6. Copy a summary with the checkbox off and confirm no transcript or reference is present; explicitly opt in and confirm the local content is included.
7. Confirm real speech Activity still shows one transaction, the model/language/RTF, acoustic estimator, and accepted/rejected gate details.

These are acceptance instructions, not claimed device results or recognition-quality measurements.
