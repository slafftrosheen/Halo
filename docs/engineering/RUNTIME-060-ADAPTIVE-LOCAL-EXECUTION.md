# RUNTIME-060 — Adaptive Local Execution + Voice Fast Path

Date: 2026-10-05. Base: `98af69e5ce82c3555a3f44bed0253665c0e5f4d0`.

Implementation connects explicit benchmark calibration to local turn preparation.
Automated validation is in progress; physical acceptance remains pending on both phones.
No model additions, automatic benchmark, GPU qualification change or device-name policy.

## Calibration and compatibility

`DeviceRuntimeIdentity` hashes only coarse manufacturer/model/SoC, SDK, 64-bit ABIs
and memory class. Only its bounded SHA-256 fingerprint enters the profile table.
No ANDROID_ID, serial, account, advertising or telephony identifier is read.

The app adapter resolves an installed artifact and verifies its physical SHA-256 on IO.
An unbenchmarked target exits before hashing; a calibrated lookup never trusts just a
filename or model ID. Missing, changed, corrupt or unreadable artifacts return no profile.
This deliberately favors compatibility truth over a cached digest; calibrated lookup
adds file-read cost that must be included in physical end-to-end voice latency measurements.
Runtime version and compatibility epoch must match. The active or safely resolved
backend, effective context, CPU threads and GPU layers must also match. Unknown model
context without a resident effective value is not guessed. A GPU measurement cannot
make a CPU execution eligible for GPU or cause qualification.

The explicit Benchmark action displays the original development result and persists
only identity, numeric measurements, completion proof and bounded failure code.
Artifact fingerprints must match before and after the run. Stable means completed,
non-empty visible generation, not a permanent guarantee. Throughput is estimated from
text, not tokenizer counts. Peak heap is an estimated Java-heap sample, not native RAM.
Unmeasured prompt throughput is represented by the existing numeric zero sentinel.

Room migration 23 -> 24 preserves rows, makes unobserved effective context nullable,
and adds stability to the unique variant index. Native model-default context is stored
as unknown when the engine cannot report it and cannot select adaptive calibration. A failed variant
coexists with a stable one. Preferred selection requires stable text proof and orders
by TTFT, estimated throughput and load duration within compatible variants. Targeted
proof updates match the full profile identity; remeasurement preserves independently
proven tool/continuation flags for that exact identity. Failure persistence contains
only bounded codes, never prompt, output, arguments, exception messages or stacktraces.
A failed run whose artifact cannot be verified is not attributed to guessed bytes.

## Portable execution policy

`LocalInferenceProfileSource` and `LocalInferenceObservationSink` are cognition contracts.
Room, Android identity and LiteRT configuration stay in the app adapter. Preparation
reads the profile asynchronously and takes live `NativeMemoryPressure` from the coordinator.

The provisional policy still applies only to on-device models with a known context at
most 4096 tokens. Larger/unknown model contexts and remote models retain existing behavior.
These history budgets are latency controls, not model context limits.

| Tier (normal pressure) | Voice history / output | Text history / output |
| --- | --- | --- |
| UNKNOWN or GOOD (TTFT <= 4000 ms) | 512 / 128 | 1024 / 256 |
| SLOW (4000 < TTFT <= 8000 ms) | 384 / 112 | 768 / 224 |
| VERY_SLOW (TTFT > 8000 ms) | 256 / 96 | 512 / 192 |

LOW shifts UNKNOWN/GOOD to SLOW and SLOW to VERY_SLOW. CRITICAL always chooses
VERY_SLOW. Unstable or invalid measurements behave as UNKNOWN. The output cap never
increases an explicit request cap. Only historical projection is expendable: current
request, system contract, required Working Set/screen/image context, approval state,
unresolved durable state and current tool continuation remain intact. This changes
next-turn projection; it does not unload an active generation.

## Voice role ownership

Before routing, fresh speech-and-visual conversation with no tools, image, screen,
workspace/document context, delegation, continuation or explicit role prefers FAST.
Typed conversation stays CHAT. Existing action planning/native dispatch, AGENTIC,
VISION, explicit REASONING and pinned recovery/continuation retain ownership. FAST
uses existing explicit/automatic/inherit/disabled role semantics. FAST is a role,
not a lower quality mode or another provider configuration. It can inherit the default.
Protocol reporting uses the exact routed model, including when a role selects a model
other than the provider's default.

## Runtime truth and diagnostics

`HALO_LOCAL_INTERACTION_PROFILE` contains only interaction ID, provider/model, surface,
profile source/tier, numeric benchmark metrics, pressure, history/output budgets and role.
The observation sink emits only bounded IDs, backend, timing, estimated counts,
success and optional cold-start truth. Production observations do not update benchmark
calibration. No startup, list-open, provider-change or unlock benchmark exists.

LiteRT `runtimeResidency=COLD|WARM` and `AiResponse.coldStart` derive from first generation
attempt since actual engine creation. Engine reload/fallback resets this state; latency
is not used to infer residency. Observations include generation lifecycle/timing from
both streaming and non-streaming responses. Unsupported providers leave lifecycle null.

EXPECT_LLM refreshes existing idle WARM_EJECTABLE LLM residency only under NORMAL
pressure. No model is loaded or pinned by the hint. ASR preemption and coordinator
admission remain authoritative, and LOW/CRITICAL do not extend residency. The fresh
local voice preparation path issues the hint after the target is known.

Model Details and System Check show compatible benchmark facts, estimated throughput
labels and the provisional interactive decision. Missing calibration is explicit.
The agentic probe uses one resolved local provider/model for both turns, pairs the tool
response with the real call ID, and updates the unchanged matching runtime profile only
after every phase passes. It marks the observed protocol independently and then
agentic continuation; it never claims both protocols from one run.

## Automated verification

Required serial gate:

```text
:sdk:provider-sdk:test :sdk:runtime-sdk:test :runtime:cognition:test
:runtime:observer:test :engines:ai:test :providers:litert:testDebugUnitTest
:app:testDebugUnitTest :app:assembleDebug
--no-parallel --max-workers=1 --no-daemon
```

Focused coverage includes all latency/pressure tiers, role ownership, complete profile
compatibility, failure coexistence, preference ordering, targeted stale-proof rejection,
protocol-specific continuation provenance, observations and residency hints.
The required serial gate passed on 2026-10-05, with the additional
`:providers:sherpa-stt:testDebugUnitTest` regression suite: 641 tests, zero failures,
errors or skips. Debug APK assembly passed. Final gate: BUILD SUCCESSFUL in 6m 7s
(497 tasks). Room migration 23 to 24, nullable native context, same-size artifact
replacement, and explicit AUTO benchmark CPU qualification are covered.
Physical latency and repeated-voice acceptance remain pending.

## Physical acceptance — pending

Use the development phone and S22 Ultra without device-specific tuning. Run the same
explicit benchmark and record model, SHA, effective backend/context, load, TTFT,
estimated tok/s, live pressure, adaptive tier and voice/text history budgets.
Then compare the same prompts:

- What is the capital of Japan?
- Explain what RAM is in one sentence.
- What is 17 multiplied by 8?
- Give me three uses for a Raspberry Pi.

Run five spoken turns within 60 seconds and distinguish cold from warm generation.
Benchmark profile != permanent device capability. Measured profile != GPU qualification.
Estimated token throughput != tokenizer throughput. Adaptive history budget != model
context limit. Runtime profiles are device/model/artifact/runtime specific.

Use these measurements to choose a subsequent batch: production EWMA, qualified GPU,
separate local role assignments or more aggressive residency. This batch implements none
of those speculative expansions and does not claim physical acceptance.
