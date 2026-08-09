# HeyCyan Capture Analysis Workflow

**Status:** PR-012 tooling foundation; PR-013/014 evidence-gated codec seams
**Evidence status:** No real HeyCyan capture has been imported into this repository yet.

This document defines how transport evidence is imported and annotated without
changing the original capture. It is an analysis workflow, not a protocol
specification.

## Import

Use `CaptureImporter.importFiles(...)` with one or more JSON capture files
produced by PR-011. Import performs these steps:

1. Parse and validate the source through `JsonCaptureSessionStore`.
2. Resolve the capture ID from the parsed document.
3. Copy the source JSON byte-for-byte into the managed capture directory as
   `heycyan-<capture-id>.json`.
4. Refuse a different document that reuses an existing capture ID.

The importer does not load and save the document again. This preserves raw bytes,
formatting, and fields produced by a newer capture tool. Re-importing the same
capture is idempotent.

## Analysis sidecars

Annotations are saved separately as:

```text
captures/
└── heycyan/
    ├── heycyan-boot-001.json
    └── analysis-heycyan-001.json
```

`JsonCaptureAnalysisStore` validates references before writing the sidecar and
uses the same temporary-file/atomic-replace persistence pattern as captures.
The raw `heycyan-*.json` file is never modified by analysis.

The sidecar shape is intentionally small:

```json
{
  "schemaVersion": 1,
  "analysisId": "heycyan-analysis-001",
  "captureIds": ["boot-001"],
  "annotations": [{
    "annotationId": "boot-packet-000",
    "target": {
      "captureId": "boot-001",
      "packetSequence": 0,
      "offset": 0,
      "length": 2
    },
    "kind": "UNKNOWN",
    "confidence": "OBSERVED",
    "note": "Raw packet observed during boot.",
    "evidence": []
  }]
}
```

### Evidence reference rules

Every annotation target and supporting evidence reference must identify:

- `captureId`
- packet `sequence`
- optional byte `offset`
- optional byte `length`

The capture ID must be declared in `captureIds`; the packet sequence must exist
in that capture; and an offset/length range must fit inside the raw payload.
An omitted range refers to the complete packet. A reference is an address into
evidence, not a decoded value.

## Analysis procedure

For each controlled session:

1. Import the capture without editing it.
2. Review `PacketTimeline` in event order, not timestamp order alone.
3. Add annotations using `UNKNOWN` or `HYPOTHESIS` when the interpretation is
   not established.
4. Compare matching packet references across at least two controlled captures
   before using `CORRELATED` or `VERIFIED`.
5. Update `BLE_PROTOCOL.md` with the capture ID, packet sequence, and byte offset
   for every documented field.

Do not add decoder or command behavior from an annotation. PR-013 begins only
after the protocol finding is repeatable and documented.

## Current findings

There are currently no repository capture artifacts under `captures/` or test
resources. Consequently the following remain **Unknown**:

| Finding | Status | Evidence |
| --- | --- | --- |
| Connection handshake | Unknown | No imported capture |
| Heartbeat/keep-alive | Unknown | No imported capture |
| ACK/NACK frames | Unknown | No imported capture |
| Battery updates | Unknown | No imported capture |
| Button presses | Unknown | No imported capture |
| Connection/state transitions at protocol level | Unknown | No imported capture |

The decoder and encoder now enforce this boundary in code. A decoder without
definitions returns an immutable `UnknownEvent` containing the characteristic,
timestamp, raw payload, and reason. An encoder without a capture-backed command
definition rejects the command instead of transmitting guessed bytes. Definitions
must include at least one `PacketEvidenceReference` before they can be installed.

The transport lifecycle events already recorded by PR-011 describe what the
client observed at the GATT layer. They are not evidence for a HeyCyan
application-level packet meaning.