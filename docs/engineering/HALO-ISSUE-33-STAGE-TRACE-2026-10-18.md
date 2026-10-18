# HALO issue #33 — voice stage telemetry and cancellation continuation

Date: 2026-10-18
Design authority: `halo-devkit-v1.4/IMPLEMENTATION.md` (BrandBook v2.3, Round 2).
Repository: `slafftrosheen/Halo`, branch `main`.

## Implemented in this continuation

1. Live and buffered speech recognition now report `STT/speech-recognition` in the existing `WorkloadStageTimeline` using `WorkloadStageEvent`. Events reflect actual provider-running, success, failure and cancellation boundaries. They carry no transcript, audio samples, tool arguments or prompt contents. Live STT deliberately has no estimated end-of-speech timestamp.
2. `VoicePipelineTrace` (app audio) correlates the existing capture latency updates, STT and cognitive workload events, first visible token, and `TtsStarted` emitted by the audio owner. It exports a capped in-process `StateFlow` (64 traces) and a `HALO_VOICE_LATENCY` log at first actual audio, with nulls for unavailable milestones. Aliases reconcile late conversation/capture IDs without retaining speech content.
3. Obsolete TTS transactions cannot report a late completion, cancellation or start as the newer output owner. Trace updates for high-frequency token events only happen on the first token, and stage lifecycle events only update summaries on terminal measurements.
4. The existing `WorkloadPlanScheduler` stops speculative warmup after an authoritative required-stage failure, joins cancelled stages to honor resource cleanup, and prevents telemetry sink exceptions from controlling inference outcomes. It does **not** force GPU use or override existing provider/JNI lease ownership.
5. The earlier #33/#34 batch introduced bounded parallel tool/task execution, latest-only provisional intent classification, Kids Stop ownership, theme/target sizing and EN/RU/LV text sync. #34 was already closed on explicit owner direction; do not reopen it without a new decision.

## Commits

- `8280084` — STT stage boundaries.
- `a28c0c9` — correlated, privacy-safe voice latency snapshots.
- `33c644e` — fail-fast required stages and telemetry isolation.
- `fc9787d` — stale-output trace ownership.
- `c4d01c8` — avoid per-token observer fanout.

These commits were authored directly against the latest `main`; no Gradle build, tests, hosted CI or deployment was run. Newly authored tests describe expected behavior only; there is no claim they passed.

## Interpretation of metrics

- `eosToAudioMs`: actual buffered STT endpoint to first `TtsStarted`, available only when both boundaries are observed; this is **not** audio-response quality or audible-perception verification.
- `eosToTranscriptMs`: buffered endpoint to published final transcript.
- `transcriptToTokenMs`: published transcript to first visible generation token, not to model warmup.
- `captureToAudioMs`: capture initialization to output-start; includes user speaking time and must not be compared as an end-of-speech latency.
- `stages`: terminal measured stage durations, not a sum of non-overlapping stages; context and eligible prewarming may overlap.
- `null`: not measured, never zero, never inferred.
- `TtsStarted` is emitted from the audio owner after the playback start callback. No assertion is made about listener perception, Bluetooth acoustic transit latency or exact speaker propagation.

## Manual acceptance (owner / alpha testers)

1. Inspect `adb logcat -s HALO_VOICE_LATENCY HALO_VOICE_STREAM` on an installed build; exercise the same real locally installed model and voice, prompt/context and audio route in serial baseline and with the intended overlap.
2. Capture at least 30 warm, 10 cold and 10 interrupted interactions per scenario; compare median/p95 of `eosToAudioMs` using only interactions where that value is not null. Record null coverage and failures separately. Do not claim a percentile for live STT without a real endpoint measurement.
3. Exercise rapid live partials → final, partials → error, and Stop during partial interpretation. Confirm hint events are advisory only, no premature tool dispatch or stale final response.
4. Exercise failed required source/context acquisition during model warmup and inspect `WorkloadStageTimeline` for `REQUIRED_STAGE_FAILED` cancellation of speculative work. Native engine load may not be instantly interruptible; observe memory/thermal recovery.
5. Switch speech model/provider while playback starts and while already playing, barge in, leave/re-enter Kids/Study, disconnect Bluetooth and return from background. Verify no stale audio or stale trace terminal takes ownership from the latest transaction.
6. Compare CPU and GPU using identical model bytes, quantization, context, token targets, input and thermal conditions. Confirm actual delegate/backend evidence; unsupported GPU must be rejected or transparently fall back.
7. Compare source with current BrandBook/DevKit 1.4: interactive motion follows real listening/thinking/speaking, reduced-motion static fallbacks, selected accent and explicit cloud consent.

## Still open under issue #33

A fully integrated multi-stage speech/retrieval/TTS execution graph, real per-provider/multilingual STT endpoint metrics, measured CPU/GPU/NPU selection, device-specific residency/budget calibration, curated one-click first-run packs, user-visible advanced trace visualization, and statistically demonstrated median/p95 gains remain pending or unverified. Full Kids/Study journeys A–D and Polish localization acceptance are separate remaining alpha checks. No claim of complete issue #33 acceptance is made.

## Further source continuation (2026-10-19)

- `312455b`: optional/speculative cancellation no longer escapes the completion selector as owner cancellation. `Job.onJoin` distinguishes cancelled child from cancelled interaction; owner cancellation still propagates.
- `1d5043a`, `f4f6a64`: introduced `VoiceLatencyReport` and source-level regression cases. First-audio and complete/cancelled/failed counts are separate. Percentiles remain `NOT_ESTABLISHED` until **30** real buffered endpoint-to-first-audio measurements exist in the rolling 64-trace window. Standalone TTS and typed interactions are excluded.
- `b70e624`, `5c1c2d6`, `e99c3c6`, `13b6f5c`, `4fa7df0`, `c0ac332`: read-only voice latency panel added under **System Check → Technical tools**, including copyable content-free diagnostics, with EN/RU/LV catalogue/resources synchronized. No hidden benchmark, background run or automatic model download.
- `af90188`: offline `tools/hil/compare_voice_latency.py` compares two manually captured, matched-condition logcat files. It reports descriptive median/P95 only when each file has at least 30 valid first-audio/endpoint measurements, and states that first-audio-only logging **cannot determine failure rate**.

### Safe manual collection

Capture baseline and candidate sessions separately on the *same device* with the same installed model artifact/quantization, actual backend, context length, prompt set, mic route, TTS voice, and temperature/residency conditions. Change only the scheduling configuration under comparison. For example:

```bash
adb logcat -c
# Perform manually controlled baseline voice sessions here.
adb logcat -d -v threadtime -s HALO_VOICE_LATENCY:I '*:S' > baseline.log
# Repeat under the comparison runtime configuration, after resetting logcat.
python tools/hil/compare_voice_latency.py baseline.log candidate.log
```

The script is an analyzer only; it does not run ADB, invoke providers or write model settings. No comparison can be made until a **real serial control** and the explicitly configured candidate have been run. There is currently no claim that the app exposes a consumer serial/concurrent toggle or that the two conditions have been physically benchmarked.

### Acceptance caveats

The added Kotlin test source and offline analyzer are **not executed** in this batch; compilation, actual Android voice latency, live-STT endpoint, provider lifecycle, provider crashes, data persistence and 150%/200% translated UI acceptance remain pending. `VoiceLatencyReport` is observational and cannot be used for model/backend automatic promotion. The metric is output-start callback time, not acoustic sound-at-ear; Bluetooth transport latency remains unmeasured. One rolling window is not a long-term performance study.
