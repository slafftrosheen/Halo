# Protocol Verification Plan and Open Questions

## Goal

Convert the current reverse-engineering corpus from "good correlations" into a verified wire protocol.

## Experiment 1 — Capture TX + RX for photo

Perform exactly one photo operation.

Capture:

```text
TX characteristic write
RX notifications
timestamp of physical shutter action
```

Expected SDK operation:

```text
02 01 01
```

Questions:

- Does the literal payload appear in TX?
- What BLE framing surrounds it?
- Is `BC 73 08` an acknowledgement, state event, or independent notification?
- Is the two-byte field after `BC 73 08 00` a CRC/checksum?

## Experiment 2 — Video start/stop

Two isolated captures:

```text
02 01 02
02 01 03
```

Do not start/stop in the same capture.

Determine:

- exact TX packet
- first RX after TX
- work-mode response
- whether `BC 73` family changes between start and stop

## Experiment 3 — Audio recording start/stop

Capture:

```text
02 01 08
```

then separately:

```text
02 01 0C
```

For each:

- capture the TX write
- record first 10 RX frames
- record last 10 RX frames
- save timestamps
- preserve the entire `BC 59 28` stream

This is the highest-value experiment for determining whether `BC 59 28` is actual audio transport.

## Experiment 4 — AI photo

Use a fixed thumbnail size:

```text
02 01 06 02 02 02
```

Capture:

- TX
- immediate RX
- thumbnail transfer
- any subsequent `BC 73` events

Repeat with thumbnail sizes `0`, `1`, `2`, `3`, `4`, `5`, `6`.

If a corresponding byte in `BC 73 08` follows the same sequence, we can promote it from candidate to verified field.

## Experiment 5 — Swipe mapping

Do one gesture at a time.

For each:

```text
LEFT
RIGHT
UP
DOWN
```

Capture only one gesture per connection/session if practical.

The current `BC 73 0E` data shows a changing byte around offset 10.

Determine:

```text
byte[10] -> direction / magnitude / action / sequence?
byte[19] -> source / gesture type / state?
```

The changing bytes at offsets 4–5 must also be tested for CRC/checksum behavior.

## Experiment 6 — Panel double tap

Repeat exactly one double tap.

Compare byte-by-byte with:

- swipe
- single tap
- long press

Especially watch:

```text
byte[10]
byte[19]
bytes[4..5]
```

## Experiment 7 — CRC hypothesis

Collect at least 20 frames from each stable family.

For each candidate:

```text
bytes[4..5]
```

test against:

- additive sum
- one's complement
- two's complement
- XOR
- CRC-8 variants
- CRC-16 variants
- sequence-dependent checksum

Do not modify device traffic while doing this; analysis should be offline.

## Experiment 8 — Sequence hypothesis

Check whether bytes 4–5 correlate with:

- monotonically increasing sequence number
- gesture number
- time
- packet counter
- payload checksum

The fact that these values change while the rest of the frame remains stable makes them high-value unknowns.

## Experiment 9 — Volume

The vendor documents a separate `0x12` notification with exact volume fields:

```text
[8..10]  music min/max/current
[12..14] call min/max/current
[16..18] system min/max/current
[19]     volume mode
```

Perform:

1. read current volume
2. increase one step
3. decrease one step
4. repeat at several volume levels

Capture all notifications.

This should let us distinguish the vendor SDK's semantic volume event from the manually observed touch/swipe family.

## Experiment 10 — Media count

Invoke:

```text
02 04
```

with known media counts.

Record:

```text imageCount
videoCount
recordCount
```

Then compare raw RX bytes against the returned object fields.

## Current open questions

### Q1. What is the exact BLE framing around `glassesControl()`?

UNRESOLVED.

### Q2. Are `BC 73` frames RX acknowledgements, device events, or both?

UNRESOLVED.

### Q3. What are bytes 4–5 in `BC 73 0E`?

RESOLVED. They are the CRC16 checksum for the payload, based on the `LargeDataHandler` structure.

### Q4. What is byte 10 in `BC 73 0E`?

RESOLVED (Legacy). `0x0E` is just the payload length. The payload itself starts at byte 6. The byte previously at offset 10 is actually offset 4 of the payload.

### Q5. What is byte 19 in `BC 73 0E`?

RESOLVED (Legacy). As with Q4, this was based on a misunderstanding of the frame structure. Byte 19 is offset 13 of the payload.

### Q6. What is `BC 59 28`?

RESOLVED. This is the real-time audio transport stream from the glasses' microphone, mapped to `ACTION_GPT_UPLOAD` (0x59) in the vendor SDK.
- The `BC 59` identifies it as the `ACTION_GPT_UPLOAD` action.
- `28 00` (length 40 bytes) and the two-byte CRC surround exactly 40 bytes of audio payload.
- The payload itself is a flawless, standard **RFC 6716 Code-3 Opus packet** (TOC byte `0x4B` or `0x48`, indicating a 48kHz sampling rate, mono, frame count and padding).
- It can be fed *directly* into standard Opus decoders (like Android's `c2.android.opus.decoder`) without stripping the Opus-level padding, yielding perfect 16-bit 48kHz PCM audio.
- The trigger is autonomous: the glasses initiate it after sending a `0x73` payload `0x03` event.

### Q7. Does `BC 73 08` encode photo and AI results in the same message family?

Observed yes; exact field semantics unresolved.

### Q8. What are the `BC 73 02` events?

Observed around AI/recording interaction; semantics unresolved.

### Q9. Are the manually observed packets generated by the glasses firmware independently of the SDK command?

RESOLVED. Yes. The `0x73` (115, `ACTION_DEVICE_DATA_REPORTING`) events like `0x03` (voice start) and `0x0A` (voice end), as well as `0x59` (`ACTION_GPT_UPLOAD`), are generated autonomously by the firmware in response to physical interactions, without requiring an SDK trigger command.

## Production implementation rule

Until these questions are answered, Halo should implement a raw capture/parser layer rather than a guessed command decoder.

Recommended model:

```kotlin
data class RawFrame(
    val timestampNs: Long,
    val direction: Direction,
    val characteristicUuid: String,
    val bytes: ByteArray
)

enum class Direction {
    TX,
    RX
}
```

Then a separate semantic layer:

```kotlin
data class DecodedObservation(
    val family: String,
    val fields: Map<String, Int>,
    val confidence: Confidence
)
```

This keeps protocol discovery reversible and prevents incorrect assumptions from becoming device-control code.
