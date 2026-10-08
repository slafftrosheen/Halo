# FEATURE-072 / WEARABLE-INTERACTION-002

Implemented source: 2026-10-08, continuing FEATURE-071 baseline bb27bc5765e03ddf6df66baed8b73cd2c6dfa852. Physical acceptance is recorded separately in [WEARABLE-ACCESS-PHYSICAL-001](../testing/WEARABLE-ACCESS-PHYSICAL-001.md).

## Three accessory classes

| Class | Connection owner | Capabilities and identity |
|---|---|---|
| System audio accessory | Android; AndroidAudioAccessoryRegistry projects live platform devices | Bluetooth Classic A2DP/SCO, BLE headset, wired and USB input/output. No Halo pairing, driver or DeviceSessionManager session. Validated Bluetooth hardware address plus transport family when available; otherwise opaque session identity. USB/wired port addresses are correlated only within a session. Display names never prove identity. |
| Managed companion | Existing Halo driver/session and presence owners | HeyCyan remains verified audio/camera/control reference hardware. Its exact saved Android audio association deduplicates the system facet. No display capability is invented. |
| Native Wear OS companion | Optional Google Play services wearable API and small watch app | Nearby visual interaction, typed controls and haptic feedback. Session identity; no second assistant Runtime. |

## Audio ownership and evidence

`HostDefault` follows Android's current output for an ordinary phone-origin response. `Phone` is an explicit handset speaker request. `Device` requires the selected live exact endpoint; external targets prohibit phone fallback unless an existing explicit policy permits it. The shared classifier covers A2DP/SCO/BLE headset, wired headphones/headset and USB device/headset without requiring an address.

The existing routed PCM playback starts muted, verifies AudioTrack.routedDevice against every available ID/type/address, pins the actual system-default route before unmute, and retains that evidence across streamed response chunks. A route change stops playback; route failure is not audible success. Labels are presentation only. AudioRecord uses the exact input endpoint independently of output. The existing communication router acquires/restores communication state only for a communication-capable microphone path; A2DP output does not acquire full duplex.

Native default-assistant invocation remains the headset assistant route. Volume and idle PLAY/PAUSE remain system-owned. No global media receiver or second Media3 playback/session is added: short assistant speech already has an authoritative player/focus owner. The portable bounded accessory-control contract preserves WearableHost.buttons compatibility; HeyCyan maps only its already verified voice/camera events and drivers never execute runtime actions.

## Continuity and persistence

Portable endpoints distinguish Phone, AndroidAssistant, AudioAccessory, ManagedDevice and WearOs, with capabilities, persistence and availability. Input, audio output and presentation are separate state. Headphone handoff changes audio; Wear OS or Android assistant presentation handoff leaves headset audio untouched. Existing conversation, run, interaction and atomic first-wins response ownership remain intact. Input-preference handoff is deliberately unavailable in this batch.

Journal JSON records handoff channel and generic audio kind. Legacy WEARABLE decodes as ManagedDevice and old handoffs default to AUDIO_OUTPUT. HostDefault has an explicit encoding. No Room migration, new conversation store or run store is introduced. Session-only IDs are rediscovered after process restart; stale exact IDs fail safely.

## Wear OS privacy and lifecycle

The `:wear` module uses the same application ID and debug signing as the phone. Its native foreground surface shows bounded result, confirmation, choice and text/clarification content, typed replies, cancellation, haptic feedback and Open on phone. The phone validates each response through InteractionResponseHandler. The watch has no direct tool/run-resume API, credentials, model/provider Runtime, memory synchronization or microphone transport.

Wear OS user-content transport is nearby-only in FEATURE-072. Each content send and response checks a currently connected node's isNearby. MessageClient carries a versioned explicit allowlist, bounded IDs/text/options and expiry; persistent DataClient is not used for Halo conversations. No Halo conversation/memory data is intentionally placed into Data Layer persistent storage. Truncated confirmation detail cannot be approved on the watch. A result preview is transient and expires; disconnect/unavailability clears the endpoint projection. Optional wearable API failure does not prevent headphone use or core startup.

CompanionDeviceManager exposes explicit association/presence seams (API36 association-based presence, legacy supported address API). Existing companion service events feed the existing presence repository. No permanent generic BLE scanner is added.

## User surfaces and validation

Devices separates System audio, Halo devices and Wear OS. Live input/output/communication capability labels are independent; system accessories have no vendor firmware/forget workflow. Home displays input/reply/view endpoints. Settings can disable connected accessories or nearby watch integration. System Check and exports include content-free accessory counts and wearable availability, with no new raw addresses or user-content logs.

Automated coverage includes exact route matching without MAC, wrong-route rejection, address/session identity projection, output-only capability, bounded controls, legacy journal decoding, independent handoff channels, phone/watch first-wins and nearby-only allowlisted transport. Phone and Wear APK assembly are separate from acoustic/hardware acceptance. See the physical report for the final gate result and available Sony WH-CH520 route evidence.

Wear microphone streaming/ChannelClient/PTT and framing/backpressure belong to FEATURE-073. Configurable gestures/mappings belong to FEATURE-074.

## Platform references inspected

- [Official Wear OS Data Layer sample](https://github.com/android/wear-os-samples/tree/main/DataLayer)
- [Wearable client types](https://developer.android.com/training/wearables/data/client-types)
- [Media3 receiver source](https://github.com/androidx/media/blob/release/libraries/session/src/main/java/androidx/media3/session/MediaButtonReceiver.java) and [session demo](https://github.com/androidx/media/blob/release/demos/session/src/main/java/androidx/media3/demo/session/PlaybackService.kt)
- [Media session playback controls](https://developer.android.com/media/media3/session/control-playback)
- [CompanionDeviceManager](https://developer.android.com/reference/android/companion/CompanionDeviceManager)
- [Association-based presence requests](https://developer.android.com/reference/android/companion/ObservingDevicePresenceRequest.Builder)
- [Wearable dependency releases](https://developers.google.com/android/guides/releases)
