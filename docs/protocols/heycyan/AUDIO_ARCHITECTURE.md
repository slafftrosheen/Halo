# HeyCyan Audio Architecture — PR-021A

**Status:** STRUCTURAL_VALIDATION
**Source:** Physical Discovery (BLE Packet Capture & Decoder Probing)

---

## Executive Summary

The HeyCyan glasses **DO** provide a live real-time audio stream over BLE via the `de5bf729` GATT characteristic, using the `BC 59` notification header. This stream was successfully decoded into 48kHz mono PCM using Android's `MediaCodec` configured for Opus.

However, the stream's exact structural compliance with RFC 6716 (Opus) is currently under forensic validation, as the proprietary `4B 41` framing bytes contradict strict Opus Code-3 frame count/padding rules.

Additionally, the physical or protocol trigger that initiates this stream is unknown. It is currently treated as a **passive, device-originated stream**.

---

## Audio Data Path

1. **GATT Characteristic:** `de5bf729-d711-4e47-af26-65e3012a5dc7`
2. **Notification Header:** `BC 59 28 00` (Audio stream action, 40 bytes)
3. **Payload Structure:** 
   - Byte 0: `4B` (TOC byte, config 9, Code 3)
   - Byte 1: `41` (Contradicts strict Opus M-frame byte, currently under analysis)
   - Bytes 2-39: Compressed data + trailing zero padding
4. **Cadence:** ~20ms continuous transmission.

---

## Protocol Decoding (Current Status)

| Finding                                       | Status                                                 |
| --------------------------------------------- | ------------------------------------------------------ |
| `BC 59` is real-time device-originated stream | **VERIFIED**                                           |
| Fixed 40-byte payload                         | **VERIFIED — 330/330**                                 |
| ~20 ms packet cadence                         | **VERIFIED OBSERVATION**                               |
| `4B` is structurally compatible with Opus TOC | **STRONG**                                             |
| `4B` config = 9                               | **VERIFIED byte interpretation**                       |
| Config 9 = SILK-only WB / 20 ms               | **VERIFIED RFC interpretation**                        |
| `41` = 1 frame + padding                      | **FALSE**                                              |
| `41` = RFC Code-3 frame-count byte            | **CONTRADICTED**                                       |
| Entire 40 bytes are RFC-6716 packet           | **UNPROVEN**                                           |
| Android MediaCodec produces PCM               | **VERIFIED EXPERIMENTALLY**                            |
| ~48 kHz mono PCM output                       | **STRONG OBSERVATION; must read actual output format** |
| Live glasses microphone data                  | **VERIFIED at transport level**                        |
| Exact command that starts BC59                | **UNKNOWN**                                            |
| Production `AudioSource`                      | **NOT YET (Pending strict parser & trigger analysis)** |

---

## Halo Architecture Decision

Because the exact trigger for the stream is unknown, we **do not** implement a fake "Start Audio Stream" command.

The pipeline architecture will be:

```text
HeyCyan BC59 RX
       ↓
HeyCyanOpusFrameParser (Strict RFC6716 validator)
       ↓
HeyCyanAudioStreamDecoder
       ↓
AudioFrame
       ↓
HeyCyanAudioSource (Passive consumer)
       ↓
Halo AudioPipeline (Not yet connected)
```

The device also supports **local recording** (audio saved to file and transferred via Wi-Fi), which is separate from this live BLE stream.