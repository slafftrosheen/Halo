> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# Device Reporting and Observed BC73 Traffic

## 1. Structural interpretation

All observed frames beginning `BC 73` follow the vendor frame structure:

```text
BC | 73 | payload length LE | CRC16 LE | payload
```

Therefore a frame such as:

```text
BC 73 08 00 1A 07 01 06 00 05 00 03 00 01
```

contains an 8-byte payload:

```text
01 06 00 05 00 03 00 01
```

A frame such as:

```text
BC 73 0E 00 9B 78 12 01 00 10 05 02 00 0F 0F 03 00 10 0A 01
```

contains a 14-byte payload:

```text
12 01 00 10 05 02 00 0F 0F 03 00 10 0A 01
```

## 2. Swipe corpus

Observed sequence from `swipes leftright I presume with val.txt` includes:

```text
BC 73 0E 00 5B 6C 12 01 00 10 01 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 AB 63 12 01 00 10 02 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 FA A6 12 01 00 10 03 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 4B 7C 12 01 00 10 04 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 1A B9 12 01 00 10 05 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 EA B6 12 01 00 10 06 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 BB 73 12 01 00 10 07 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 8B 43 12 01 00 10 08 02 00 0F 0F 03 00 10 0A 03
BC 73 0E 00 DA 86 12 01 00 10 09 02 00 0F 0F 03 00 10 0A 03
```

The payload field at offset 4 of the payload (wire offset 10) takes values `01..09` in this corpus. Its semantic meaning is still UNKNOWN.

The final payload byte (wire offset 19) is `03` in this swipe corpus. Do not label it as a gesture type without a controlled comparison.

## 3. Double-tap comparison

Observed:

```text
BC 73 0E 00 9B 78 12 01 00 10 05 02 00 0F 0F 03 00 10 0A 01
```

This has the same 14-byte payload structure but final payload byte `01`.

This establishes a controlled byte difference in the observed corpus, but not the exact semantic meaning.

## 4. AI double press

Observed:

```text
BC 73 05 00 9D 66 02 00 1C 02 00
```

This means:

```text
BC | 73 | 05 00 | 9D 66 | 02 00 1C 02 00
```

The old label `BC73_05` should therefore be replaced by structural `BC73` with payload length 5.

## 5. Photo / AI-related reporting

Observed examples include:

```text
BC 73 08 00 1A 07 01 06 00 05 00 03 00 01
BC 73 08 00 FF 06 01 07 00 06 00 04 00 01
```

Their exact semantic fields require correlation with the vendor response models and physical actions.

## 6. BC59 stream

Observed recording-related traffic includes repeated 46-byte frames beginning:

```text
BC 59 28 00 ...
```

Example:

```text
BC 59 28 00 C6 58 4B 41 13 07 F3 A7 0F 76 A4 7F 2B 01 FE 8E F6 9B 7A 73 95 F6 F0 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
```

The `BC59` family is structurally consistent with the same six-byte framing convention:

```text
BC | 59 | length LE | CRC16 LE | payload
```

The payload is still an unresolved high-rate data stream. The project has separate evidence that official app recordings are `.opus` and may be raw Opus packets, with packet size 40 used by the official app; this does NOT prove that the live BC59 payload itself is directly encoded Opus. Keep those two facts separate.
