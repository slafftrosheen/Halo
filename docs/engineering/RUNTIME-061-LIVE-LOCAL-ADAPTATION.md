# RUNTIME-061 - Hot-path purity, input truth, and live local adaptation

Base: `d98a2c68d5d19d0bb3520f0c61596de73b8b15a6`. Date: 2026-10-05.

## Input semantics

`InteractionInputModality` is portable TEXT / VOICE / UNKNOWN. Exactly one turn resolver
checks canonical `inputModality`, structured microphone/speech route, then legacy
`input=keyboard`. Response delivery never supplies input authority. Speech response
style remains governed by response delivery. TEXT and UNKNOWN use the text budget.
Home emits TEXT in both direct submission and observation paths. The STT perception
bridge emits VOICE, including handset/HeyCyan/Assistant microphone routes. The HeyCyan
visual-question test button emits TEXT: its prewritten request is not spoken input.
RecognitionService delivers recognition to its caller; it is not a cognition submitter.
Widgets, AppFunctions, sharing, and Notebook/Study have no additional direct
`CognitiveTurnRequest`/`cognitiveSession.submit` host boundaries in this baseline.
A typed Assistant request without speech-origin evidence remains UNKNOWN/text policy.
Device/package/screen names do not manufacture modality.

FAST is limited to eligible fresh spoken questions. Action/vision/reasoning, delegated,
and pinned recovery/continuation ownership retain their existing authority. Voice
EXPECT_LLM hints use the same input resolver. Telemetry includes inputModality,
responseDelivery, and bounded profileLookupMs independently.

## Artifact authority and cache

Inventory verification is not per-turn hashing. LiteRtModelManager alone owns physical
SHA truth; explicit refresh and explicit verification bypass its physical SHA cache,
including same-size/same-timestamp replacement. Cold load, explicit model selection/re-selection, and explicit Verify consume
physical verification. Resident artifact SHA is tracked independently of current inventory.
Benchmarks continue verifying before and after recording; stale calibration is rejected.

The app adapter's trusted identity reads current Ready descriptors only. It does not
resolve/refresh inventory, stat/open model files, scan directories, or hash model bytes.
A 32-entry process cache keys provider/model/SHA/device/runtime/epoch/backend/context/
threads/GPU layers/inventory revision/calibration revision. Misses query Room on IO;
warm cache hits avoid both Room and dispatcher hops. Negative entries expire after
2 seconds. Benchmark save/artifact invalidation increments calibration revision;
inventory refresh increments a monotonic inventory revision. Configuration changes
change the effective key; restart clears all process cache state. No durable cache table.

## Live health and policy

Benchmark calibration is durable; live runtime health is process-local. Cold TTFT is
not warm TTFT. Unknown residency does not count as warm. Each of 32 bounded execution
keys contains provider/model/backend plus opaque artifact/runtime/config identity.
Valid successful warm observations update EWMA with alpha 0.25. Three warm samples
are required for a latency-derived tier; three consecutive slow observations can
make a proven tier more conservative. Recovery requires five consecutive qualifying
warm samples and EWMA <7000 ms for VERY_SLOW -> SLOW, <3500 ms for SLOW -> GOOD.
Each improvement resets the recovery streak. A pessimistic cold calibration therefore
recovers gradually toward the default rather than expanding context.

One model-health failure leaves policy unchanged; two degrade a tier; three force
VERY_SLOW. Successful generation resets the failure streak, while degraded policy
recovers through warm hysteresis. Cancellation, preemption, and resource contention
are excluded. Backend is reported only when engine residency establishes it; unknown
backend failures can overlay a known path only with matching opaque execution identity.
No prompt, output, exception message, or device/account identity enters this tracker.

The single performance composition orders small-context model eligibility, calibration/
default baseline, committed runtime tier, failure overlay, then live memory pressure.
Voice budgets never exceed 512 history / 128 output; text never exceeds 1024 / 256.
Memory LOW enforces at least SLOW, CRITICAL enforces VERY_SLOW. GPU qualification is
unchanged and cannot be promoted by latency, model name, memory, or device type.

## Product visibility and catalogue repair

System Check and model Details label measured benchmark, live session, and effective
interactive policy separately. Model Details subscribe to the shared tracker. System
Check refresh captures a copyable local diagnostics snapshot containing source version,
model/artifact prefix, backend/context, modality/delivery, profile lookup time, calibration,
cold/warm sample counts/EWMA/failures, memory pressure and effective voice/text budgets.
Device fingerprints, prompts, transcript, conversation, screen/memory contents, tool
arguments, and notification text are omitted. Live EWMA is never written to Room.

The shared catalogue adapter projects installed inventory plus the existing pinned
LiteRT downloadable/importable entries. Previously only the legacy Intelligence view
added downloadable entries, leaving the shared browser empty on a fresh install.
Refresh scans local inventory explicitly, and download/import/delete updates its flow.
An uninstalled model routes to provider setup/Manage local models; selection requires
installation. No new model artifacts were added.

## Verification

Serial requested SDK/runtime/observer/engine/provider/app tests and debug assembly:
passed: 649 tests, zero failures/errors; debug APK assembled. The gate used
`--no-parallel --max-workers=1 --no-daemon` with a 2 GB JVM heap.
No device acceptance is claimed. Tests cover input/delivery matrix,
Home speech-output regression, no voice hint for typed Home, profile cache/invalidation,
no inventory resolve/refresh/verification on warm lookup, explicit same-size mutation,
health sample gates/recovery/failure exclusions/backend isolation/memory pressure, and
fresh-install/install/delete catalogue projection.

| Module | Tests | Failures/errors |
| --- | ---: | ---: |
| sdk/provider-sdk | 16 | 0 |
| sdk/runtime-sdk | 43 | 0 |
| runtime/cognition | 207 | 0 |
| runtime/observer | 11 | 0 |
| engines/ai | 64 | 0 |
| providers/litert | 89 | 0 |
| providers/sherpa-stt | 18 | 0 |
| app | 201 | 0 |
| Total | 649 | 0 |

`git diff --check` passed. Required new sources are included with the batch; no
required source is omitted. Source and automated acceptance are complete; physical
development acceptance remains pending.

## Physical acceptance after merge

Both development phone and S22 Ultra should run the same build. Benchmark explicitly,
then ask one cold question and five questions within about 60 seconds; pause 30 seconds
and ask again; pause at least 90 seconds and ask again. Capture coldStart, TTFT, warm
EWMA, effective tier, budgets, memory pressure, profileLookupMs and backend. Copy local
AI diagnostics for each trace. Physical results remain pending and do not block this
implementation. Choose any subsequent role/residency/GPU batch from those traces.
