# Agent Guide — Next Batch

## Mission
Reconcile Halo's current HeyCyan implementation against the independent alternative-app evidence. Do not add speculative protocol semantics.

## 1. Audit semantic mappings
Search the whole repo for:
ToggleAudio, RECORD_START, RECORD_STOP, SetMode(AUDIO), 02 01 04, D0 53, 02 01 0B, 02 01 04 01, MEDIA_COUNT, 02 04, GLASSES_CONTROL.

Classify each occurrence as raw protocol, vendor SDK semantic, Halo abstraction, test fixture, or documentation.

## 2. Correct documentation
Mark as UNVERIFIED/WRONG if currently treated as facts:
- 02 01 04 == audio toggle (officially dead, UNRESOLVED)
- D0 53 == audio command
- SetMode(AUDIO)
- BC73-02 final byte == audio state
- BLE media count == authoritative inventory

Integrate:
- [02 01 04 01] Wi-Fi transfer trigger
- [02 01 0B] voice/SCO preparation
- voice events 0x03/0x0A
- Wi-Fi media transfer architecture
- BLE count flakiness

## 3. Trace the actual encoder
Find the exact code that produces:
BC 41 03 00 D0 53 02 01 04
Document where D0 53 comes from. Do not infer its meaning.

## 4. Compare with alternative implementation
Trace exact handling/wire framing for:
glassesControl([02 01 04 01])
glassesControl([02 01 0B])
glassesControl([02 04])
voice notify handling
photo notify handling
P2P startup

Compare bytes, not method names.

## 5. Preserve raw evidence
Transaction model must retain timestamp, direction, UUID, raw bytes, operation label, CRC, response association, confidence.

## 6. Prepare (do not execute) hardware tests
A: MEDIA_COUNT x3.
B: COUNT -> PHOTO -> COUNT -> Wi-Fi media.config.
C: COUNT -> VIDEO action -> wait -> VIDEO action -> COUNT -> media.config.
D: COUNT -> AUDIO_ACTION_1 -> COUNT -> AUDIO_ACTION_2 -> COUNT.
E: physical voice button; capture all BLE notifications; correlate 0x03/0x0A and 0x0B.
F: verify exact [02 01 04 01] wire path, then P2P + HTTP.

For audio, never label actions START/STOP in raw instrumentation until proven.

## 7. Tests
Add golden vectors for verified PHOTO, VIDEO, MEDIA_COUNT, raw audio-labelled transaction, 02 01 0B if represented, voice events, and Wi-Fi trigger once exact framing is established.

## Definition of done
1. No false audio semantics in docs/code names.
2. D0 53 encoder fully traced.
3. Independent evidence integrated with attribution.
4. MEDIA_COUNT parser preserves raw payload and avoids unsupported offsets.
5. Voice/SCO separate from audio-file recording. 02 01 0B verified.
6. Wi-Fi media transfer documented.
7. Hardware test matrix ready.
8. Existing tests pass except known pre-existing media-sync failure.
9. No speculative TX commands.
10. Produce changed-files report + remaining unknowns.
