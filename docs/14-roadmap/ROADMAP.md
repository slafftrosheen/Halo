# HPS-8000: Evidence-Driven Development Roadmap

This roadmap takes Halo from stable architecture to a verified wearable cognitive runtime. The HeyCyan track is deliberately staged: discovery first, then real evidence, then decoding, commands, device context, and audio.

## Frozen foundation

During PR-016 through PR-020, do not redesign these foundations unless a real hardware requirement proves the change necessary:

- Runtime Kernel and lifecycle model.
- Provider architecture.
- Host abstraction and SDK contracts.
- Capability Registry.
- AudioPipeline API.
- Capture format and replay tooling.
- Driver lifecycle and DI decomposition.
- Deterministic simulator host and tests.

## PR-015 — Physical BLE Discovery

**Status:** Implementation complete; physical acceptance pending.

The discovery workflow is an independent developer workflow:

```text
BLE scan
  -> explicit address selection
  -> raw GATT connection
  -> complete topology discovery
  -> sequential CCCD subscriptions
  -> raw capture
  -> disconnect/failure persistence
```

It must not invoke `HaloRuntime`, cognition, speech, providers, automatic host connection, notification decoding, or protocol commands. The UI must call the workflow **BLE PROTOCOL DISCOVERY** and must not claim arbitrary nearby devices are HeyCyan.

Acceptance:

- Unfiltered scan; multiple devices visible; no auto-selection.
- Explicit selected address is the only address passed to GATT.
- Services, characteristics, descriptors, properties, permissions, requested MTU, negotiated MTU, and sequential subscriptions are recorded.
- Advertisements, connection transitions, GATT metadata, CCCD writes, raw RX/TX, ready, disconnect, and failure state are persisted where observed.
- Discovery Mode cannot access a decoder or command encoder.

## PR-016 — First Physical Capture and Protocol Analysis

This is a hardware investigation PR, not a protocol implementation PR.

Capture controlled scenarios:

1. `heycyan-boot-001.json`: power-on, connect, ready, idle 30–60 seconds, disconnect.
2. `heycyan-button-single-001.json`: one single press.
3. `heycyan-button-double-001.json`: one double press.
4. `heycyan-button-long-001.json`: one long press.
5. `heycyan-reconnect-001.json`: disconnect and explicit reconnect.
6. `heycyan-battery-001.json`: battery and, if possible, charging transition.

Deliver:

- Immutable raw JSON artifacts.
- Separate analysis sidecars with capture ID, packet sequence, characteristic, payload range, trigger, and confidence.
- Replay success for every capture.
- GATT topology and candidate correlations.
- Unknown packet preservation.

Do not promote a decoder or command in PR-016. A finding must be observed in a real capture and correlated with controlled actions before implementation.

Acceptance:

- At least one real boot capture.
- At least one real button capture.
- At least one real reconnect capture.
- Every capture replays offline with unchanged bytes.
- GATT topology is documented.
- Candidate interpretations are explicitly labeled and cited.

## PR-017 — Evidence-Based Event Decoder

Implement only after PR-016 produces repeatable real evidence. Start with Ready, BatteryUpdated, ButtonPressed, and ChargingState only if supported.

Rules:

- Every definition references real capture IDs and packet sequences.
- Unknown traffic becomes immutable `UnknownEvent`.
- Synthetic fixtures stay test-scoped and cannot affect production.
- Replay-backed positive and negative tests are required.
- A decoder result must be deterministic for the same capture.

## PR-018 — Evidence-Based Command Layer

Implement only commands supported by observed TX behavior or authoritative vendor documentation. Use an explicit `VerifiedCommandSet` whitelist. Everything else throws `UnsupportedOperationException`.

Every outbound command must be captured with its target characteristic, raw payload, response, and outcome. Application code must have no arbitrary raw-write path.

## PR-019 — HeyCyan Device Context

Expose verified connection, battery, charging, wearing, and button observations through the host `DeviceContext`. Runtime policy may use this information for deterministic choices such as avoiding expensive inference on low battery. No guessed device fields.

## PR-020 — HeyCyan Audio Investigation

Determine the actual audio path instead of assuming BLE control traffic carries audio:

- A: glasses expose microphone PCM;
- B: glasses expose an Android Bluetooth audio profile;
- C: HeyCyan SDK exposes another audio API;
- D: glasses do not expose usable microphone streaming and Halo uses the phone microphone.

This investigation is separate from BLE protocol decoding.

## PR-021 — HeyCyan AudioSource

Implement the selected evidence-backed audio adapter behind the host-agnostic `AudioSource` contract. Measure format, lifecycle, latency, packet loss, and power behavior.

## PR-022 — End-to-End Wearable Speech

Exercise the complete verified path:

```text
Audio -> STT -> Observation -> Reasoning -> Planning -> Execution -> TTS -> wearable output
```

The simulator must cover the flow deterministically before physical acceptance. Hardware acceptance must cite captures and measured results.

## Promotion rule

No HeyCyan protocol implementation may be promoted from hypothesis to production based on synthetic captures, naming conventions, common BLE UUIDs, or assumptions from another application. Promotion requires a reproducible real-device capture or authoritative vendor documentation.

## Global guardrails

- Do not implement guessed commands, UUID roles, packet fields, or event meanings.
- Do not run cognition, reasoning, speech, or LLM behavior as part of discovery.
- Preserve raw bytes, transport metadata, and capture provenance.
- Imported replay artifacts are immutable; annotations belong in sidecars.
- Synthetic captures are offline development anchors only.
- Use offline replay for decoder development after the physical glasses have been captured.

See [HeyCyan BLE protocol documentation](../protocols/heycyan/BLE_PROTOCOL.md) and [the top-level roadmap](../../ROADMAP.md).

## Legacy foundation sprints

The original foundation sequence remains complete: monorepo, Gradle modules, runtime kernel, buses, SDKs, providers, simulator, and documentation. New work follows PR-015 through PR-022 rather than opening new architecture tracks in parallel.

## Product implementation status

- [x] Deterministic simulator host, driver, clock, audio source, and audio output.
- [x] Runtime, SDK, provider, host, driver, and capability foundations.
- [x] BLE discovery, capture, persistence, and replay tooling.
- [ ] Real physical captures and evidence-backed protocol promotion.
- [ ] Physical audio transport investigation and implementation.
- [ ] Measured latency and battery budgets on representative hardware.
- [ ] End-to-end wearable speech acceptance.
