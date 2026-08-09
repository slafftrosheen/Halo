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
- [ ] **PR-012 — Evidence-based protocol documentation:** import and annotate real captures, then identify framing, packet boundaries, battery messages, button packets, acknowledgements, heartbeat, and notification traffic in documentation only. Analysis tooling is present; findings remain pending real captures.
- [ ] **PR-013 — Verified event decoding:** implement battery, button, connection, and state events only from packet definitions supported by multiple captures.

### Guardrails

- Do not implement guessed commands or packet fields.
- Do not add cognition, reasoning, speech, or LLM behavior to discovery sessions.
- Preserve raw bytes, transport metadata, and capture provenance.
- Every decoded byte must reference a capture.
- Use offline replay for decoder development after the physical glasses have been captured.

See [HeyCyan BLE protocol documentation](docs/protocols/heycyan/BLE_PROTOCOL.md) and the [detailed development roadmap](docs/14-roadmap/ROADMAP.md).
