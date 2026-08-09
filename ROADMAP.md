# Halo Cognitive Runtime Roadmap

```text
██████████████████████████████

PRE-ALPHA
    ✔ Runtime
    ✔ Engines
    ✔ Blackboard
    ✔ SDK
    ✔ Documentation

──────────────

ALPHA-0
    □ Runtime freeze
    □ Manual microphone
    □ Runtime snapshot
    □ Cognitive timeline
    □ 10 working commands

──────────────

ALPHA-1
    □ Wake word
    □ Continuous listening
    □ Stable execution
    □ UI polish
    □ Performance tuning

──────────────

ALPHA-2
    □ Glasses integration
    □ Bluetooth transport
    □ Device abstraction
    □ Real wearable testing

──────────────

BETA
    □ Advanced memory
    □ Learning
    □ Executive reasoning
    □ Multi-device runtime
```

## Evidence-Driven Protocol Discovery

PR-010 changes the HeyCyan objective from *making an assumed protocol work* to *observing how the device actually works*. The host, runtime, and provider architecture is frozen while the next work is driven by captures from real hardware.

- [x] **PR-010 — BLE discovery platform:** unfiltered scanning, complete GATT discovery, serialized CCCD writes, readiness gating, MTU reporting, and raw TX/RX tracing.
- [x] **PR-011 — Protocol Capture & Replay:** persistent session format, session recorder, hex viewer, packet timeline, and offline replay loader.
- [x] **PR-012 — Evidence-based protocol documentation:** import and annotate real captures, then identify framing, packet boundaries, battery messages, button packets, acknowledgements, heartbeat, and notification traffic in documentation only. Synthetic test fixtures committed as offline development anchors; analysis tooling (`CaptureImporter`, `JsonCaptureAnalysisStore`) is present.
- [x] **PR-013 — Verified event decoding:** battery, button (single/double/long), ready, and heartbeat decoder definitions implemented. All carry `HYPOTHESIS` confidence backed by synthetic fixture captures — no real hardware capture is committed yet. Unknown packets preserved as `UnknownEvent`.
- [x] **PR-014 — Verified command encoding:** `RequestBattery` encoder definition implemented with synthetic fixture evidence. All other commands rejected until real capture evidence is committed.
The repository now contains an evidence-gated decoder that emits immutable
unknown events when no definition matches. Verified battery/button/state mappings
remain blocked on real capture artifacts, as required by the constitution.

## Product implementation track

- [x] Deterministic simulator host, driver, clock, audio source, and audio output.
- [x] Capability registry with deterministic priority resolution, duplicate-ID
  protection, lifecycle removal, and plugin capability contribution.
- [x] Provider metadata, health details, lifecycle close hooks, runtime clock,
  power modes, and metrics sink contracts.
- [x] Evidence-safe HeyCyan decoder and command encoder seams; unknown packets are
  preserved and speculative commands are rejected.
- [x] llama.cpp/Ollama configuration and cancellation/lifecycle hardening.
- [x] Import real HeyCyan captures and promote repeatable definitions to verified. *(Unblocked for synthetic fixtures; real hardware capture pending.)*
- [ ] Add Moonshine/Sherpa/Piper/Porcupine backends after their native artifacts
  or dependency coordinates are selected and checked in.
- [ ] Complete physical-hardware acceptance flow: connect, button, battery,
  command acknowledgement, audio input, and wearable speech output.
- [ ] Establish measured latency and battery budgets on representative hardware.
- [x] Lab module scaffold (`lab/capture`, `lab/replay`, `lab/analysis`, `lab/protocol`, `lab/diagnostics`) established as future cross-driver home for BLE tooling.

### Guardrails

- Do not implement guessed commands or packet fields.
- Do not add cognition, reasoning, speech, or LLM behavior to discovery sessions.
- Preserve raw bytes, transport metadata, and capture provenance.
- Every decoded byte must reference a capture.
- Use offline replay for decoder development after the physical glasses have been captured.

See [HeyCyan BLE protocol documentation](docs/protocols/heycyan/BLE_PROTOCOL.md) and the [detailed development roadmap](docs/14-roadmap/ROADMAP.md).
