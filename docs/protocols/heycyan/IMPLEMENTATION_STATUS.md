# HeyCyan implementation status

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.
This describes code paths, not newly verified device behavior.

## Current sources

- [RealHeyCyanDriver](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/RealHeyCyanDriver.kt)
  constructs scanner/GATT client, capture recorder, response router and device-state store.
- [Wire encoder](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/codec/HeyCyanWireEncoder.kt)
  and [response decoder](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/codec/HeyCyanResponseDecoder.kt)
  exist. “All production packets are unknown” and “there is no protocol implementation”
  are obsolete descriptions.
- [Command catalog](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/protocol/HeyCyanCommandCatalog.kt)
  lists identifiers; its own contract distinguishes identifiers from executable payloads.
- [Capability catalog](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/capability/HeyCyanCapabilityCatalog.kt)
  has implemented/limited/blocked entries. These are code metadata, not hardware acceptance.
- [HeyCyanAudioSource](../../../drivers/heycyan/src/main/java/com/halo/drivers/heycyan/HeyCyanAudioSource.kt)
  observes active voice sessions and feeds RawAudioFrame payloads to RawOpusAudioDecoder.
- [HostGraph](../../../app/src/main/java/com/halo/app/di/HostGraph.kt) injects AndroidAudioSource
  into the production phone speech engine. Do not infer wearable speech integration merely
  from the separate driver audio classes.

## Contradictions and limits

| Topic | Current evidence | Required interpretation |
|---|---|---|
| Live microphone | AudioSource/decoder code exists; capability catalog marks audio.live_input unsupported | Code metadata and implementation disagree; neither proves device acceptance |
| Framing | Wire encoder exists; some catalog explanations still say checksum/framing unresolved | Record drift, inspect selected execution path before changing claims |
| Date/time | Action constant exists, catalog labels payload blocked | Identifier is not supported executable time sync |
| Voice trigger/codec | Voice-session/decoder logic exists; historical notes disagree on trigger and packet interpretation | Code shows attempted handling, not universal codec/firmware truth |
| Hardware captures | Raw/annotated artifacts exist under research/evidence/captures | Do not say no evidence exists; also do not infer complete current-build acceptance |
| Tests | Test sources exist | Old “238 PASS” count is not a current run result |

## Protocol terminology correction

The current command catalog uses volume 0x51, sound switch 0x52, wear 0x46 and
wear-support 0x47. Earlier docs rendered decimal values 81/82/70/71 as hexadecimal
0x81/0x82/0x70/0x71; those are different bytes. Use the actual constants for Halo behavior.

Firmware/protocol assertions require raw bytes, characteristic, capture/session identity
and provenance. Vendor documentation is evidence about the vendor, not proof that a current
Halo build or a connected device implements every command.

See [hardware confirmation matrix](HARDWARE_CONFIRMATION_MATRIX.md) for planned tests.
No physical acceptance or current test pass is certified by this review.
