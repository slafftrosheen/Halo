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

BETA
    □ Advanced memory
    □ Learning
    □ Executive reasoning
    □ Multi-device runtime
```

## Evidence-Driven HeyCyan Sequence

Halo treats the physical wearable integration as an evidence workflow, not a guessed protocol implementation. Discovery, capture analysis, decoding, command transmission, device context, and audio are separate stages. Runtime Kernel, provider architecture, host abstraction, capability registry, audio pipeline API, capture format, replay, driver lifecycle, and DI decomposition are frozen during the evidence stages unless real hardware proves a change necessary.

### PR-015 — Physical BLE Discovery

**Status:** Implementation complete; physical acceptance pending.

Scope:

- Unfiltered BLE scan.
- No automatic device selection.
- Explicit address selection.
- Complete GATT services, characteristics, descriptors, properties, and permissions.
- MTU request and negotiated-MTU capture.
- Sequential CCCD notification/indication subscriptions.
- Raw advertisement, connection, metadata, RX/TX, ready, disconnect, and failure capture.
- Discovery Mode is raw-only and never invokes `HaloRuntime`, a decoder, or a command encoder.
- The UI calls this **BLE PROTOCOL DISCOVERY** and does not claim that an arbitrary selected BLE device is HeyCyan.

Acceptance checklist:

- [ ] Scan is unfiltered.
- [ ] Multiple BLE devices can appear.
- [ ] No device is auto-selected.
- [ ] User explicitly selects one address.
- [ ] Only the selected address is passed to GATT.
- [ ] Discovery does not invoke runtime connection.
- [ ] No protocol commands are sent.
- [ ] Services, characteristics, descriptors, properties, and permissions are recorded.
- [ ] Requested and negotiated MTU values are recorded.
- [ ] Notify/indicate subscriptions complete sequentially.
- [ ] Advertisement, connection lifecycle, GATT metadata, CCCD writes, RX/TX, disconnect, and failure state are persisted.

### PR-016 — First Physical Capture and Protocol Analysis

**Objective:** Obtain reproducible real-device evidence before promoting any protocol interpretation.

Controlled captures:

1. `heycyan-boot-001.json`: power-on, explicit selection, connect, ready, 30–60 seconds idle, disconnect.
2. `heycyan-button-single-001.json`: connect, idle, one single press, idle, disconnect.
3. `heycyan-button-double-001.json`: connect, idle, one double press, idle, disconnect.
4. `heycyan-button-long-001.json`: connect, idle, one long press, idle, disconnect.
5. `heycyan-reconnect-001.json`: ready, disconnect, explicit reselect, reconnect, ready, disconnect.
6. `heycyan-battery-001.json`: observe battery and, if physically possible, charging transition.

Deliverables:

- Raw JSON captures under the agreed capture archive.
- `CAPTURE_ANALYSIS.md` annotations or sidecars with capture ID, packet sequence, characteristic, payload, trigger, and confidence.
- Replay success for every capture.
- Unknown packets retained and raw bytes unchanged.
- No decoder or command implementation promoted by this PR.

Acceptance:

- [ ] One real boot capture.
- [ ] One real button capture.
- [ ] One real reconnect capture.
- [ ] Every capture replays offline.
- [ ] GATT topology is documented.
- [ ] Candidate correlations are documented without overclaiming interpretation.

### PR-017 — Evidence-Based Event Decoder

Only after PR-016 produces repeatable real evidence. Promote the smallest useful set: Ready, BatteryUpdated, ButtonPressed, and ChargingState only when supported. Every definition references real capture IDs and packet sequences. Unknown packets remain `UnknownEvent`. Synthetic fixtures remain test-only and cannot be used as production evidence.

Tests must include positive replay-backed decoding, unknown-packet preservation, wrong-characteristic negatives, and deterministic repeatability.

### PR-018 — Evidence-Based Command Layer

Only after outbound behavior is observed in captures or documented by an authoritative vendor source. Implement only commands proven safe and necessary. Use an explicit `VerifiedCommandSet`; all other commands throw `UnsupportedOperationException`. Every outbound command is captured with its response, and there are no arbitrary raw writes from application code.

### PR-019 — HeyCyan Device Context

Feed verified connection, battery, charging, wearing, and button observations into `DeviceContext`. Use this context for deterministic runtime decisions such as avoiding expensive local inference on low battery. No guessed fields are allowed.

### PR-020 — HeyCyan Audio Investigation

Determine the actual audio transport without assuming that BLE control traffic carries PCM:

- A: glasses expose microphone PCM directly;
- B: glasses expose an Android Bluetooth audio profile;
- C: HeyCyan SDK exposes another audio API;
- D: glasses provide no usable microphone stream and Halo falls back to the phone microphone.

### PR-021 — HeyCyan AudioSource

Implement the selected, evidence-backed audio adapter and validate PCM format, lifecycle, latency, loss, and power behavior. Keep the `AudioSource` contract host-agnostic.

### PR-022 — End-to-End Wearable Speech

Only after the device control/state and audio transport are understood: audio input -> STT -> observation -> reasoning -> planning -> execution -> TTS -> wearable output, with simulator coverage and physical acceptance evidence.

## Product implementation track

- [x] Deterministic simulator host, driver, clock, audio source, and audio output.
- [x] Capability registry with deterministic priority resolution, duplicate-ID protection, lifecycle removal, and plugin capability contribution.
- [x] Provider metadata, health details, lifecycle close hooks, runtime clock, power modes, and metrics sink contracts.
- [x] Evidence-safe HeyCyan capture, replay, decoder, and encoder seams; unknown packets are preserved and speculative commands are rejected.
- [x] BLE discovery workflow with explicit address selection and raw capture.
- [ ] Import real HeyCyan captures and promote repeatable definitions to verified. *(Blocked on physical hardware capture.)*
- [ ] Add Moonshine/Sherpa/Piper/Porcupine backends after native artifacts or dependency coordinates are selected and checked in.
- [ ] Complete physical-hardware acceptance flow: connect, button, battery, command acknowledgement, audio input, and wearable speech output.
- [ ] Establish measured latency and battery budgets on representative hardware.
- [x] Lab module scaffold (`lab/capture`, `lab/replay`, `lab/analysis`, `lab/protocol`, `lab/diagnostics`) established as future cross-driver home for BLE tooling.

## Global guardrails

- Do not implement guessed commands, UUID roles, packet fields, or event meanings.
- Do not add cognition, reasoning, speech, or LLM behavior to discovery sessions.
- Preserve raw bytes, transport metadata, and capture provenance.
- Every decoded byte must reference a reproducible real capture or authoritative vendor documentation.
- Synthetic captures are offline test fixtures only.
- Use offline replay for decoder development after physical glasses have been captured.
- Audio is a separate transport investigation and must not be inferred from BLE control traffic.

See [HeyCyan BLE protocol documentation](docs/protocols/heycyan/BLE_PROTOCOL.md) and the [detailed development roadmap](docs/14-roadmap/ROADMAP.md).
