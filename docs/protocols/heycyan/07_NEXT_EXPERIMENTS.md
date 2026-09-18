> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](IMPLEMENTATION_STATUS.md).

# Next Experiments

## Priority 1 — Physical TX verification

Trigger exactly one PHOTO through Halo and capture the actual GATT write.

Expected vendor-derived bytes:

```text
BC 41 03 00 10 50 02 01 01
```

Capture both:

```text
TX de5bf72a... <bytes>
RX de5bf729... <bytes>
```

If Halo emits different bytes, preserve the discrepancy and trace the transport path rather than modifying the expected frame.

## Priority 2 — Video

Capture separately:

```text
VIDEO_START -> BC 41 03 00 50 51 02 01 02
VIDEO_STOP  -> BC 41 03 00 91 91 02 01 03
```

## Priority 3 — Recording

Capture:

```text
RECORD_START -> BC 41 03 00 D0 56 02 01 08
RECORD_STOP  -> BC 41 03 00 D1 95 02 01 0C
```

Also record first/last BC59 frames.

## Priority 4 — AI

Generate frames for thumbnail sizes 0..6 and compare against vendor behavior.

## Priority 5 — Device reports

Decode the payloads after removing the verified six-byte wire header. Correlate response payloads with the vendor response classes and documented `loadData` fields.

## Priority 6 — Touch

Do controlled one-gesture captures and compare the payload fields, not the old wire offsets.

## Priority 7 — BC59

Determine whether the payload is a live transport stream, audio framing, or another data stream. Do not assume codec from file-transfer evidence.
