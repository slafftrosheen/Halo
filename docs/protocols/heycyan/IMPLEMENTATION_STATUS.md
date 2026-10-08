# HeyCyan implementation status

## FEATURE-071 / CONTINUITY-001 — 2026-10-08

Phone-hosted conversations now bind each fresh turn and durable run to an exact existing conversation; background commits never activate another conversation. Explicit Continue on… changes delivery only, preserving run/interaction/origin and exact wearable no-phone-fallback policy. Recovery is tracked, missing conversations require explicit rebind, and pending cross-surface input uses the existing atomic first-wins handler. Home projects recent capture metadata into an exact Notebook link; System Check stays passive. No migration, new device owner, driver protocol, inference owner or automatic connection is added. Process death preserves journal truth without surprise execution. Automated and physical acceptance status are recorded in the [implementation report](../../engineering/CONTINUITY-001-PHONE-WEARABLE.md).


Status: CURRENT SOURCE MAP. Reviewed 2026-09-24.
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
The supplied 2026-09-21 trace physically validates callback-gated GATT command serialization and
HeyCyan microphone transport. Android speaker routing remains unvalidated on the current post-fix build.

## Audio association boundary

`HeyCyanAudioPairingCoordinator` uses CompanionDeviceManager approval, public bonding APIs,
persistent BLE-control-to-audio identity mapping, bond observation and continuous audio endpoint
observation. Route acceptance requires the mapped Bluetooth address; a newly appearing or unrelated
headset is not sufficient. The Devices detail view reports BLE control and audio separately. Persisted audio association restoration is also invoked by the process-level speech-routing path, so a fresh process does not require opening the Devices tab before a wearable response can resolve its saved Classic-audio identity.

This source implementation is not evidence that the media profile appears on the target hardware.

Wearable speech output uses a separate strict PCM prototype: Android TTS synthesizes chunks without
playing them, AudioTrack requests the mapped device, silent probe audio establishes `routedDevice`,
and only an exact address match permits `TtsStarted` and audible chunks. Route loss during playback
fails the utterance. Normal phone speech retains the ordinary Android TTS path.


## Current semantic control path

Reachable device controls do not use the generic raw `EmbodimentTransport.send(...)` methods.
For photo/video/audio-recording/media-inventory intents, the current source path is:

```text
DevicesViewModel
  -> HeyCyanSession.dispatch()
  -> HeyCyanOperation
  -> HeyCyanCapabilityControllerAdapter
  -> HeyCyanCapabilityController
  -> DefaultHeyCyanCommandExecutor
  -> HeyCyanGattCommandTransport
  -> HeyCyanGattClient.write()
```

The command executor accepts only catalogued/evidence-backed mappings and emits
`HALO-TX-TRACE` before the verified GATT write. Session success is not inferred merely from a
successful write. Photo/video/audio-recording operations wait for the corresponding mode transition,
and media inventory waits for `MEDIA_COUNTS_UPDATED`. An acknowledged dispatch with no expected
confirmation becomes `UNCONFIRMED`, not synthetic success and not transport failure.

`HeyCyanEmbodimentTransport.send(String)` and `send(ByteArray)` remain unsupported generic
transport methods in current source and return false. They are not the command path used by the
reachable semantic controls above. Do not connect generic raw-send UI to those stubs without a
separately verified protocol contract.

## Wi-Fi media transfer source path

The Android media path now preserves the device-reported transfer endpoint end to end:
`HeyCyanResponse.GlassControl.P2pIp -> HeyCyanEvent.WiFiIpReceived -> HeyCyanDeviceState.wifiIp`.
The transfer-side IP discovery probes that announced address first and keeps the historical candidate
scan only as a fallback. It must not substitute an Android Wi-Fi/P2P group-owner address for the
glasses endpoint.

On API 29+, HeyCyan HTTP probes and `/files/media.config` / media downloads are opened through the
specific `android.net.Network` obtained for the glasses connection. The transfer path no longer
binds the entire Halo process to the glasses network, so unrelated provider/plugin traffic keeps its
normal routing. This is a source-wiring statement; physical BLE -> Wi-Fi -> HTTP acceptance still
requires a real-device rerun.

## Session and auto-connect composition

`HeyCyanHost` is now constructed with the shared process `DeviceSessionManager`. Saved glasses
therefore use the same registered `HeyCyanSession` lifecycle as Devices UI actions.

Auto-connect ownership is process-level rather than constructor-driven:
- `DeviceAutoConnectCoordinator` inspects persisted records and only opts a device kind into
  startup/recovery when `autoConnect=true`;
- Bluetooth-on can trigger a serialized retry through `DeviceSessionManager.autoConnectDevice`;
- enabling Auto-connect from the device UI triggers an immediate retry;
- constructing `HeyCyanHost` by itself registers saved sessions but does not start a transport
  connection;
- records created during the earlier Pairing constructor-order bug are normalized to canonical
  `deviceType=HEYCYAN_GLASSES`.

Physical pass/fail for these flows is defined in
[REAL_DEVICE_BURN_IN.md](../../04-architecture/REAL_DEVICE_BURN_IN.md). The source path above is not
a claim that a particular firmware/device completed the command successfully.

## FEATURE-072 / WEARABLE-INTERACTION-002 — 2026-10-08

HeyCyan remains managed reference hardware. Existing verified voice/camera events map to generic controls without unknown packet inference. Exact saved Bluetooth association deduplicates its system audio facet. No display capability is invented. Legacy managed journal continuity remains compatible. No HeyCyan was reported for the physical suite; unit regression evidence is separate from hardware acceptance.

See `docs/engineering/WEARABLE-INTERACTION-002.md` and `docs/testing/WEARABLE-ACCESS-PHYSICAL-001.md`.
