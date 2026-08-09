# HPS-8000: Development Roadmap

This document outlines the path from conceptual architecture to a fully functional Halo Platform.

## Sprint 0: Foundation
* Create the monorepo.
* Set up Gradle modules (Core, Runtime, Drivers, Providers, Plugins).
* Configure CI/CD.
* Publish the documentation site (versioned specifications).
* Establish coding standards and RFC process.

## Sprint 1: Platform Skeleton
* Implement the HAL Runtime Kernel (HRK).
* Implement Event, Command, and Query buses.
* Implement Service Registry and Lifecycle Management.
* Add foundational Logging and Diagnostics (Observatory).

## Sprint 2: SDKs & Contracts
* Finalize Driver SDK (Discovery, Connection, Events, Capabilities).
* Finalize Provider SDK (Streaming, Vision, Embeddings, Memory).
* Finalize Plugin SDK (Manifests, Permissions, Lifecycle, Commands).
* Build sample implementations and a Fake Device Simulator for testing.

## Sprint 3: First Working Vertical Slice
* **Driver:** Integrate `HeyCyan` driver.
* **Provider (Local):** Local `llama.cpp` provider.
* **Speech:** Whisper/Moonshine STT and Piper TTS.
* **Workflow:** "Press button -> Ask question -> Receive spoken answer."

Once Sprint 3 is complete, Halo ceases to be an idea and becomes a platform. Every subsequent feature (translation, memory, GitHub, automation) becomes another vertical slice built on the same proven foundation.

## Current Track: HeyCyan Protocol Discovery

The HeyCyan integration is now an observation platform rather than a guessed protocol implementation. The host, runtime, and provider architecture should remain stable while real-device evidence drives the next milestones.

### PR-010 — BLE Discovery Platform (complete)

- Unfiltered BLE scanning before service UUID assumptions.
- Complete GATT discovery, including services, characteristics, descriptors, properties, and permissions.
- Serialized CCCD writes to avoid concurrent descriptor operations.
- `Ready` only after discovery and subscriptions complete.
- MTU negotiation and tracing.
- Raw TX/RX packet tracing without payload interpretation.

### PR-011 — Protocol Capture & Replay (complete)

Deliverables:

- Persistent capture-session format.
- Session recorder containing raw packets and GATT metadata.
- Hex viewer and packet timeline.
- Replay loader for offline analysis and decoder development.
- Advertisement observations, including name, RSSI, advertised services, and manufacturer data.
- Requested and negotiated MTU values.

Acceptance path:

```text
Real HeyCyan glasses
        ↓
Capture session
        ↓
Persisted artifact
        ↓
Offline replay
        ↓
Decoder development
```

### PR-012 — Evidence-Based Protocol Documentation

Import and annotate multiple captures before documenting—without implementing—packet framing, packet boundaries, battery messages, button packets, acknowledgements, heartbeat, reconnect behavior, charging behavior, and notification traffic. The capture-analysis sidecar keeps annotations separate from raw JSON and requires capture ID, packet sequence, and optional byte offset/length references. Each conclusion must reference one or more captures. The repository currently has no real capture artifact, so all application-level findings remain unknown.

### PR-013 — Verified Event Decoding

Implement `EventDecoder` behavior only after PR-012 has produced repeatable packet definitions. Initial targets are battery, buttons, connection, and state events. No command implementation should precede sufficient capture evidence.

### Discovery guardrails

- Do not implement guessed commands, UUID roles, packet fields, or event meanings.
- Do not run cognition, reasoning, speech, or LLM behavior as part of a discovery session.
- Preserve raw bytes and transport metadata before decoding.
- Keep unknown packets visible and label hypotheses explicitly.
- Treat [the HeyCyan BLE protocol document](../protocols/heycyan/BLE_PROTOCOL.md) as an evidence log, not a normative specification.
