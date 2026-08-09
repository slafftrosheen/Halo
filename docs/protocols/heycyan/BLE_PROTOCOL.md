# HeyCyan BLE Protocol

**Status:** Discovery in progress  
**Scope:** Evidence-backed observations of the HeyCyan Bluetooth Low Energy transport.  
**Protocol authority:** This document is not normative until observations are backed by saved capture sessions.

> **Reverse-engineering rule:** observe first, interpret second. Every decoded byte or protocol claim must reference a capture. Unknown values are preferable to plausible but unverified values.

## Current discovery boundary

PR-010 establishes the transport-observation foundation without asserting a HeyCyan protocol:

- BLE scanning is unfiltered so advertisements from every nearby BLE device can be observed before a service UUID is assumed.
- GATT discovery reports services, characteristics, descriptors, characteristic properties, and permissions.
- CCCD writes are queued and serialized rather than issued concurrently.
- The client requests an MTU of 517 and records the negotiated MTU through the tracer.
- The transport reaches `Ready` only after service discovery and queued notification/indication subscriptions complete.
- Raw TX and RX payloads are traced with their characteristic UUIDs.
- No protocol command, packet field, or event meaning is considered verified by this document.

PR-011 promotes packet logging into a complete JSON capture session. A session contains advertisement observations, GATT inventory, requested and negotiated MTU values, application/device metadata, ordered transport events, and raw packet payloads encoded as uppercase hexadecimal. Each packet has a recorder-assigned monotonic sequence number in addition to the all-event timeline sequence. The capture can be loaded and replayed without opening Bluetooth.

PR-012 adds a lossless import path and separate analysis sidecars. An annotation
references a capture ID, packet sequence, and optional byte offset/length; it
never overwrites the raw packet or capture document. No real capture is present
in this repository yet, so no application-level protocol finding is promoted
beyond `Unknown`.

The PR-013/014 codec boundaries are now implemented without promoting any
unverified findings: unmatched notifications become observable unknown events,
and commands are rejected unless a capture-backed definition supplies both target
characteristic and payload. No verified command or event mapping is currently
shipped because this repository still has no real HeyCyan capture artifact.

## Advertisement

### Observed facts

| Field | Observation | Evidence | Status |
| --- | --- | --- | --- |
| Scan filter | The scanner uses an empty BLE filter list. | `drivers/heycyan/src/main/java/com/halo/drivers/heycyan/ble/HeyCyanBleScanner.kt` | Implemented |
| Device identity | The current scanner flow emits each newly observed device address once. | `drivers/heycyan/src/main/java/com/halo/drivers/heycyan/ble/HeyCyanBleScanner.kt` | Implemented |
| HeyCyan advertisement signature | Not established. | No saved capture | Unknown |
| Service UUID in advertisement | Not established. | No saved capture | Unknown |
| Manufacturer data | Captured for the first observation of each device address in a scan session. | `BleCapture.advertisements[].manufacturerData` | Implemented |
| RSSI/name behavior | Captured for the first observation of each device address in a scan session. | `BleCapture.advertisements[]` | Implemented |

### Questions

- Which advertisement identifies the physical HeyCyan glasses reliably?
- Is the advertised name stable across boot, reconnect, and charging states?
- Are manufacturer data, service UUIDs, or local-name fields useful for device identification?
- Do both glasses, a case, or other accessories advertise separately?

## PR-012 analysis status

The repository currently contains no imported JSON sessions under `captures/` or
test resources. The analysis workflow and evidence reference format are defined
in [`CAPTURE_ANALYSIS.md`](CAPTURE_ANALYSIS.md), but the following findings have
no capture evidence and remain unknown:

| Finding | Status | Evidence |
| --- | --- | --- |
| Connection handshake | Unknown | No imported capture |
| Heartbeat/keep-alive | Unknown | No imported capture |
| ACK/NACK frames | Unknown | No imported capture |
| Battery updates | Unknown | No imported capture |
| Button presses | Unknown | No imported capture |
| Application-level state transitions | Unknown | No imported capture |

GATT connection lifecycle events recorded by the transport are not treated as
application-level protocol findings.

## Services

No HeyCyan service UUID is documented yet. Populate this table from a saved discovery session; do not copy values from an assumed or unrelated device.

| Service UUID | Primary/secondary | Evidence capture | Notes |
| --- | --- | --- | --- |
| — | — | — | No verified device observation yet |

## Characteristics

The transport records characteristic UUIDs, properties, and permissions during complete GATT discovery. The role of each characteristic remains unknown until captures correlate metadata with observed behavior.

| Service UUID | Characteristic UUID | Properties | Permissions | Evidence capture | Tentative role |
| --- | --- | --- | --- | --- | --- |
| — | — | — | — | — | Unknown |

### Property vocabulary

Use the platform-reported values in captures and retain the raw integer bitmask. Interpretations such as “command endpoint” or “event endpoint” require packet evidence.

- Read
- Write
- Write without response
- Notify
- Indicate
- Signed write
- Extended properties

## Descriptors

CCCDs and all other descriptors should be recorded with their characteristic UUID, descriptor UUID, permissions, write status, and the subscription value used.

| Service UUID | Characteristic UUID | Descriptor UUID | Permissions | Subscription/write result | Evidence capture |
| --- | --- | --- | --- | --- | --- |
| — | — | — | — | — | No verified device observation yet |

The CCCD UUID used by the Android transport is the standard Client Characteristic Configuration descriptor. Its presence does not identify the HeyCyan protocol or the meaning of the associated characteristic.

## MTU

| Session/capture | Requested MTU | Negotiated MTU | Timing/behavior | Status |
| --- | ---: | ---: | --- | --- |
| — | — | — | — | — | No verified device observation yet |

Record the negotiated value, not only the requested value. Compare packet lengths and fragmentation behavior across sessions with different MTUs when that evidence becomes available.

## Notification Types

This section intentionally has no decoded entries yet. Add one row per observed notification or indication pattern and link it to the raw capture.

| Type ID | Characteristic UUID | Delivery | Length/pattern | Proposed meaning | Confidence | Capture reference |
| --- | --- | --- | --- | --- | --- | --- |
| — | — | — | — | Unknown | — | — |

Potential scenarios to capture include:

- Boot and initial subscription traffic
- Button press and release
- Battery level changes
- Charging start, charging stop, and charging while connected
- Reconnect and disconnect
- Notifications generated while the companion application is active
- Heartbeat or keep-alive traffic

## Known Packets

A packet belongs here only after its framing, direction, characteristic, trigger, and field interpretation have been supported by one or more captures.

| Packet ID | Direction | Characteristic UUID | Trigger | Payload layout | Acknowledgement | Confidence | Capture reference |
| --- | --- | --- | --- | --- | --- | --- | --- |
| — | — | — | — | Not established | — | — | — |

## Unknown Packets

Keep unknown traffic visible. Do not discard it merely because it cannot yet be decoded.

| Observation ID | Direction | Characteristic UUID | Length | Trigger/context | Current hypothesis | Next test | Capture reference |
| --- | --- | --- | ---: | --- | --- | --- | --- |
| — | — | — | — | — | None | Capture a controlled reproduction | — |

## Capture References

No real HeyCyan capture sessions are committed yet. The PR-011 recorder writes one JSON document per completed session using a stable layout such as:

```text
captures/
└── heycyan/
    ├── boot-001.json
    ├── button-press-001.json
    ├── battery-change-001.json
    └── reconnect-001.json
```

The final session format should retain, at minimum:

- Session start time and scenario label
- Device identity and advertisement observations
- GATT services, characteristics, descriptors, properties, and permissions
- Requested and negotiated MTU
- Ordered packet events with timestamps, direction, characteristic UUID, and raw payload
- Descriptor write/subscription results
- Connection, discovery, subscription, and disconnection events
- Capture format and application/device metadata needed to reproduce the observation

### Session record shape

`JsonCaptureSessionStore` writes a complete session document. The essential shape is:

```json
{
  "schemaVersion": 2,
  "captureId": "button-press-001",
  "scenario": "button-press",
  "deviceAddress": "AA:BB:CC:DD:EE:FF",
  "metadata": {
    "haloVersion": "1.0",
    "gitCommit": null,
    "androidVersion": "Android 15 (API 35)",
    "phoneModel": "Example Model",
    "bluetoothAdapter": "Example Adapter",
    "glassesFirmware": null
  },
  "advertisements": [{
    "timestamp": 0,
    "address": "AA:BB:CC:DD:EE:FF",
    "name": "HeyCyan",
    "rssi": -42,
    "serviceUuids": [],
    "manufacturerData": {}
  }],
  "requestedMtu": 517,
  "mtu": 247,
  "packets": [{
    "sequence": 0,
    "timestamp": 1,
    "direction": "RX",
    "characteristic": "00000000-0000-0000-0000-000000000000",
    "payload": "00FF"
  }],
  "services": [],
  "timeline": []
}
```

The packet payload remains raw and must not be treated as a decoded protocol field. `schemaVersion` is the capture format version. `metadata` records context rather than protocol interpretation; values unavailable at capture time remain `null`. `timeline` records the ordering of metadata and packet events; packet events point into `packets` by index. Older format-1 captures without packet sequences or metadata remain loadable; their packet-array indices are used as deterministic fallback sequences.

## Open Questions

- What advertisement pattern identifies HeyCyan glasses?
- Which GATT services and characteristics are stable across devices and firmware versions?
- Which characteristics carry notifications, indications, reads, or writes?
- Is traffic fragmented at the GATT layer, and how does that relate to negotiated MTU?
- What packet framing and boundaries are used?
- Which packets represent buttons, battery, charging, reconnects, notifications, acknowledgements, and heartbeat?
- Are there separate left/right-glass or case endpoints?
- Does the device require pairing, bonding, authentication, or an application-level handshake?
- Which observations are invariant across repeated sessions?
- What is the smallest capture set needed to distinguish correlation from causation?

## Evidence policy

### Confidence levels

- **Observed:** directly present in a saved capture or deterministic transport log.
- **Correlated:** repeated across controlled captures with the same trigger.
- **Verified:** independently reproduced and consistent with neighboring transport evidence.
- **Hypothesis:** plausible interpretation awaiting a controlled test.
- **Unknown:** no reliable evidence yet.

### References

- [Protocol discovery roadmap](../../14-roadmap/ROADMAP.md)
- [Protocol documentation index](../README.md)
- [`HeyCyanBleScanner`](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/ble/HeyCyanBleScanner.kt)
- [`HeyCyanGattClient`](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/ble/HeyCyanGattClient.kt)
- [`PacketTracer`](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/codec/PacketTracer.kt)
- [`PacketCapture`](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/codec/PacketCapture.kt)
- [`CaptureImport`](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/codec/CaptureImport.kt)
- [`CaptureAnalysis`](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/codec/CaptureAnalysis.kt)