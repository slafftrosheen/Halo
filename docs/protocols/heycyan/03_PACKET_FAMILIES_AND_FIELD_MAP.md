# HeyCyan Observed Packet Families and Field Map

## 1. Current known families

### Family A — `BC 73 0E`

Observed length: 20 bytes in the swipe/touch examples.

Template:

```text
00  BC
01  73
02  0E
03  00
04  ??
05  ??
06  12
07  01
08  00
09  10
10  XX
11  02
12  00
13  0F
14  0F
15  03
16  00
17  10
18  0A
19  XX
```

Strong observations:

- bytes 0–3 are invariant in the captured family.
- bytes 6–9 are invariant in the captured swipe sequence.
- byte 10 changes in the swipe sequence.
- bytes 11–19 are mostly invariant for the swipe sequence.
- byte 19 differs between the captured swipe and panel-double-tap example.
- bytes 4–5 change independently across frames.

### Family B — `BC 73 08`

Observed in camera and AI-related captures.

Photo example:

```text
BC 73 08 00 0A C7 01 07 00 05 00 03 00 01
```

AI-related example:

```text
BC 73 08 00 FF 06 01 07 00 06 00 04 00 01
```

The packet body is short and highly structured. The two examples differ in the bytes after the four-byte prefix as well as in the final fields.

The AI example containing `06` is interesting because the vendor's documented AI command begins with:

```text
02 01 06 ...
```

but this is only a correlation clue, not proof of direct byte mapping.

### Family C — `BC 73 05`

Example:

```text
BC 73 05 00 9D 66 02 00 1C 02 00
```

Observed in the AI button double-press capture.

### Family D — `BC 73 02`

Examples observed around AI/recording interaction:

```text
BC 73 02 00 47 59 0B 23
BC 73 02 00 06 9B 0B 24
```

These are short discrete events. Their exact semantic role is unresolved.

### Family E — `BC 59 28`

Observed as repeated high-rate frames during audio/recording activity.

Template:

```text
BC 59 28 00 ...
```

The frames are substantially longer than the short `BC 73` events and contain rapidly changing payload data.

Current hypothesis: audio/data stream.

Evidence level: OBSERVED + strongly correlated with recording experiment, but codec/protocol not yet decoded.

## 2. Field naming policy

Use neutral names in implementation until verified:

```text
unknown0
unknown1
candidateAction
candidateSource
candidateChecksum
candidateSequence
```

Do not use:

```text
volume
swipeDirection
timestamp
crc
sequenceNumber
audioCodec
```

unless a controlled experiment proves the meaning.

## 3. Why the vendor SDK matters

The vendor SDK exposes semantic operations above the wire layer:

```text
02 01 01  -> photo
02 01 02  -> video start
02 01 03  -> video stop
02 01 08  -> record start
02 01 0C  -> record stop
02 01 06 ... -> AI / thumbnail
02 04     -> media counts
```

It also explicitly documents notification fields and work-mode values.

This gives us semantic anchors, but not enough information to derive the complete BLE wire framing.

## 4. Working abstraction

The current evidence supports this architecture:

```text
Halo application
      |
      v
Vendor-style operation
      |
      |  glassesControl(...)
      v
SDK command layer
      |
      v
proprietary BLE transport
      |
      +-----------------------+
      |                       |
      v                       v
BC 73 discrete events     BC 59 28 stream
      |
      +--> camera / AI / touch / button candidates
```

The transport boundary should remain explicit in Halo code.
