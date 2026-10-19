# Issue 33 voice pipeline P0 implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan inline, task by task.

**Goal:** Produce trustworthy correlated voice-stage evidence before changing execution concurrency.

**Architecture:** Extend AudioPipeline's existing timing event and add a passive observer. The process runtime activates it before engines start. Existing Advanced System Check exports its bounded snapshot. The runtime kernel, action authority and native lease owner remain stable.

**Tech Stack:** Kotlin/JVM, coroutines StateFlow/EventBus, Android Compose host wiring, JUnit, existing serial Gradle gates.

**Spec:** `docs/engineering/ISSUE-33-RUNTIME-SPEC.md`, approved implementation scope from issue 33 and the owner's direct implementation instruction.

## Global constraints

- No GitHub Actions or CI. No new model downloads for initial physical benchmarks.
- No prompt, transcript, generated text, tool arguments or credentials retained by telemetry.
- Unknown metrics remain null/NOT_MEASURED. No latency or GPU-performance claim from synthetic tests.
- Preserve correlation, cancellation, child cloud privacy and existing model/provider settings.

## Review focus

- Interleaved turns and stale terminal events must not join by recency.
- Actual streaming playback may start before generation finishes; show overlap accurately.
- Partial/unmatched events, absent endpoint signals and wall-clock jumps must not produce fabricated timings.
- Token floods and abandoned turns must have bounded storage and lookup maps.
- Reports must exclude user content and never initialize a model or start a diagnostic.

### Task 1: Correlated passive trace

Files: create `runtime/observer/src/main/java/com/halo/runtime/observer/VoicePipelineTraceRecorder.kt` and its test; use existing runtime SDK events.

- [ ] Add failing tests for interleaving, output correlation, streaming overlap, missing boundaries, clock discontinuity, duplicate first-token events, bounds and content exclusion.
- [ ] Run `:runtime:observer:test --tests '*VoicePipelineTraceRecorderTest'` and observe the missing implementation.
- [ ] Implement a bounded store plus one ordered EventBus consumer, returning immutable snapshots and a content-free report.
- [ ] Run observer tests and inspect the result.

### Task 2: Production boundary and report wiring

Files: AudioPipeline, ManualDependencyGraph, AppShell, SystemCheckViewModel; add a speech-engine regression test using existing fixtures.

- [ ] Stamp capture start and buffered endpoint using InteractionLatencyUpdated, before recognition, without manufacturing a live endpoint.
- [ ] Start the observer with the process runtime before speech engines; inject its report into explicit System Check.
- [ ] Verify cancellation does not create a final transcript or a successful voice result; run speech/observer tests and app Kotlin compilation serially.

### Task 3: Evidence and handoff

- [ ] Record exact checks and phase status in `docs/engineering/ISSUE-33-RUNTIME-STATUS.md`.
- [ ] Commit/push a coherent P0 batch to main with `[skip ci]`; keep issue 33 open until full acceptance.
- [ ] Continue P1-P4 from the existing owners after P0 establishes the measurement contract. Physical latency improvement remains unverified while the phone is disconnected.

Ruling: execute inline under the owner's explicit implementation instruction; no repeated approval request. The issue already defines purpose, phases and constraints. P0 is the first production batch, not completion of issue 33.
