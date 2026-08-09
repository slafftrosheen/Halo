# lab/

The **Lab** is the Protocol Laboratory subsystem for Halo. It provides reusable, device-agnostic tooling for capturing, replaying, and analysing BLE transport sessions.

## Rationale

BLE capture, replay, analysis, hex viewing, and protocol annotation tooling was originally colocated with `drivers/heycyan/codec/`. Once a second hardware driver requires the same tooling (e.g., Meta Ray-Ban, USB HID, serial, or TCP replay), those types should move here so they are not duplicated.

## Submodules

| Module | Purpose |
|---|---|
| `lab:capture` | Session format, `BleCapture`, `CaptureSessionStore`, `CaptureImporter` |
| `lab:replay` | `CaptureReplayPlayer`, `PacketTimeline` |
| `lab:analysis` | `CaptureAnalysis`, `PacketAnnotation`, `JsonCaptureAnalysisStore` |
| `lab:protocol` | Evidence-gated decoder/encoder definition contract |
| `lab:diagnostics` | `HexViewer`, `LoggingPacketTracer`, `PacketTracer` |

## Current Status

These modules are **scaffolded** — the source types still live in `drivers:heycyan:codec`. Migration happens when a second driver needs the same tooling. The module boundaries are established now so the migration is a mechanical move, not a redesign.

## Constitution Compliance

Lab is a pure JVM Kotlin module. It:
- Has no Android SDK dependency
- Has no runtime dependency (it only depends on `lab:*` siblings and `drivers:heycyan` temporarily)
- Is usable from desktop, simulator, and test contexts

See [HALO ENGINEERING CONSTITUTION](../CONSTITUTION.md) directive 6 (Driver Layer) and the [Architecture Analysis](../docs/04-architecture/).
