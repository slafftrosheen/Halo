# HeyCyan Command Correlation Table

Status: v0.1 — evidence-based, not yet a final wire-protocol specification.

## Legend

- **DOCUMENTED** — vendor SDK explicitly defines it.
- **OBSERVED** — packet exists in a manual capture.
- **CORRELATED** — physical operation and packet are associated by capture context.
- **INFERRED** — field meaning is plausible but not proven.

## Correlation matrix

| Physical / logical operation | SDK command | Observed packet family | Observed raw example | Current interpretation | Confidence |
|---|---|---|---|---|---|
| Camera single press / photo | `02 01 01` | `BC 73 08` | `BC 73 08 00 0A C7 01 07 00 05 00 03 00 01` | Photo-related device event | HIGH semantic / MEDIUM wire correlation |
| Camera single photo capture | `02 01 01` | `BC 73 08` | Same family as above | Capture explicitly named `cam.btn.single.photo` | HIGH |
| AI button long press / AI photo | `02 01 06 <size> <size> 02` | `BC 73 08` | `BC 73 08 00 FF 06 01 07 00 06 00 04 00 01` | AI/photo/thumbnail-related event | MEDIUM |
| AI button long press preceding events | — | `BC 73 02` | `BC 73 02 00 47 59 0B 23`; repeated `BC 73 02 00 06 9B 0B 24` | Button/AI state or acknowledgement candidates | MEDIUM |
| AI button double press | — | `BC 73 05` | `BC 73 05 00 9D 66 02 00 1C 02 00` | Distinct double-press event family | HIGH observed / semantic from capture label |
| Audio recording start | `02 01 08` | `BC 59 28` + short `BC 73` events | `BC 59 28 00 ...` repeated stream | Recording/audio transport strongly suspected | HIGH for stream association; LOW for exact encoding |
| Audio recording stop | `02 01 0C` | `BC 73 02` around recording end | short `BC 73 02 ...` events | Candidate recording state transition | LOW/MEDIUM |
| Swipe sequence | not documented as `glassesControl()` | `BC 73 0E` | repeated 20-byte frames | Structured touch/control event | HIGH structural |
| Panel double tap | not documented as `glassesControl()` | `BC 73 0E` | `BC 73 0E 00 9B 78 12 01 00 10 05 02 00 0F 0F 03 00 10 0A 01` | Same control family as swipe; final field differs | HIGH structural |
| Video start | `02 01 02` | no decisive capture yet | — | Documented command only | DOCUMENTED |
| Video stop | `02 01 03` | no decisive capture yet | — | Documented command only | DOCUMENTED |
| AI thumbnail size | `02 01 06 size size 02` | `BC 73 08` AI-related capture | see AI row | Candidate size/control fields require TX capture | MEDIUM |
| Media count | `02 04` | no decisive capture yet | — | Documented SDK operation | DOCUMENTED |

## Critical `BC 73 0E` observation

A manually captured swipe sequence shows:

```text
BC 73 0E 00 5B 6C 12 01 00 10 01 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 AB 63 12 01 00 10 02 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 4B 7C 12 01 00 10 04 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 EA B6 12 01 00 10 06 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 BB 73 12 01 00 10 07 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 8B 43 12 01 00 10 08 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 DA 86 12 01 00 10 09 02 00 0F 0F 03 00 10 0A 03
```

The important invariant is:

```text
BC 73 0E 00 ?? ?? 12 01 00 10 XX 02 00 0F 0F 03 00 10 0A 03
                                      ^^
                                   changes
```

The `XX` field advances with the gesture sequence. This makes it a strong candidate for an action/state/sequence parameter, but its exact semantics are not yet proven.

## Panel double-tap comparison

Observed:

```text
BC 73 0E 00 9B 78 12 01 00 10 05 02 00 0F 0F 03 00 10 0A 01
```

Compared with the swipe structure:

```text
BC 73 0E 00 ?? ?? 12 01 00 10 05 02 00 0F 0F 03 00 10 0A 03
```

This suggests at least two independent meaningful fields:

```text
byte[10] = candidate action/value
byte[19] = candidate interaction/source/type
```

These names are intentionally provisional.

## `BC 73 05`

Observed for AI button double press:

```text
BC 73 05 00 9D 66 02 00 1C 02 00
```

This is structurally distinct from `BC 73 08` and `BC 73 0E`.

## `BC 59 28`

The recording-related capture contains repeated 46-byte frames beginning:

```text
BC 59 28 00 ...
```

Examples include:

```text
BC 59 28 00 60 07 4B 41 0D 80 30 A0 CC ...
BC 59 28 00 91 DD 4B 41 0C 80 41 EC 38 22 ...
BC 59 28 00 A2 E3 4B 41 14 00 A1 EA ED 70 ...
```

A recurring `4B 41` sequence is visible in the early payload area, while later bytes vary substantially.

This is consistent with a high-rate data stream and is much less like a discrete button notification. Exact codec/framing remains unverified.

## Important checksum/sequence warning

Many `BC 73 0E` frames have two changing bytes immediately after the header:

```text
BC 73 0E 00 XX YY ...
```

They change while the rest of the structure stays stable.

Do not label these bytes as CRC, sequence, timestamp, or checksum until the candidate function is tested against all available frames.
