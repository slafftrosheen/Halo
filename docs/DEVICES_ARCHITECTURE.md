# Device Capability Architecture

Status: IMPLEMENTED generic Android audio and nearby Wear OS foundation; future capabilities remain design targets. Reviewed 2026-10-08.

Devices in Halo are first-class participants in the cognitive ecosystem. However, the Runtime must remain agnostic to specific hardware identities.

## Capability Doctrine

The Runtime must never ask:
- `isHeyCyan()`
- `isMeta()`

except within the relevant driver/adapter boundary.

Runtime-level questions are capability questions. A device exposes what it can do:

- `AudioInput`
- `AudioOutput`
- `StillImageSource`
- `VideoFrameSource`
- `DisplayTarget`
- `ButtonInput`
- `TouchInput`
- `GestureInput`
- `WearSensor`
- `MotionSensor`
- `Location`
- `MediaStorage`
- `Mobility`
- `Manipulator`

### Examples

- **Android audio accessory**: Exposes live `AudioInput` and/or `AudioOutput` only when Android reports them. Communication capability is independent. Media/volume buttons remain system-owned; native assistant invocation uses the Android default assistant.
- **Future AR glasses**: Exposes `DisplayTarget`, `VideoFrameSource`, `AudioInput`.
- **BuddyBot**: Exposes `Mobility`, `Manipulator`, `Sensors`.

The architecture must support all of these without reshaping the Runtime.

### Resource Ownership Invariant (RUNTIME-020)

No subsystem may assume access to a scarce cross-runtime resource merely because it has a capability reference. Capability resolution must precede resource acquisition, and successful resource acquisition must precede use.

Device-driver safety and protocol arbitration (`HeyCyanResourceArbiter`, `SafetySupervisor`) remain authoritative below the generic Runtime lease layer.

> **HeyCyan is reference hardware, not an architectural category.**

## Runtime capability publication

Connected device sessions are projected into the common capability catalogue through
`RuntimeDeviceCapabilityBridge`. The bridge publishes semantic instances with stable owner/endpoint
identity; driver protocol evidence remains inside each driver.

Examples:

| Device ability | Runtime capability | Scarce Runtime resource |
|---|---|---|
| live microphone | `device.audio.input` | `audio.input@device` |
| speaker/output | `device.audio.output` | `audio.output@device` |
| still capture | `device.camera.still` | `camera.sensor@device` + `camera.capture@device` |
| media transfer | `device.media.transfer` | `media.transfer@device` |
| drive | `device.mobility` | `mobility.drive@device` |
| manipulator | `device.manipulator` | `manipulator.motion@device` |

Capability selection never replaces device-driver safety. HeyCyan protocol arbitration and BuddyBot
SafetySupervisor remain authoritative below Runtime resource ownership.

## Multimodal context source boundary

RUNTIME-021 adds a portable visual-context boundary. Cognition requests a current image for an
origin device; Android host composition resolves the concrete `StillImageSource`.

For HeyCyan the source path is:

```text
ContextNeed(VISUAL_IMAGE, ORIGIN_DEVICE)
        → VisualContextSource
        → HeyCyanStillImageSource
        → LeasedStillImageSource
        → Runtime camera.sensor/camera.capture lease
        → HeyCyan thumbnail coordinator
        → HeyCyanResourceArbiter
```

This keeps vendor protocol types outside Runtime cognition. A fresh image already associated with
the interaction/origin is reused before issuing another software capture. Android phone-camera still
capture is intentionally advertised unavailable until a real controlled CameraX source is wired;
assistant screenshots are screen context, not the physical host camera.

## FEATURE-072 accessory classes

System audio accessories are owned by Android and require no Halo driver/session. Managed companions remain driver/session owned (HeyCyan reference hardware). Native Wear OS is a nearby presentation/control endpoint using existing interaction surfaces. Exact association deduplicates a managed Android audio facet; product names never establish identity. See [implementation and acceptance boundary](engineering/WEARABLE-INTERACTION-002.md).

## FEATURE-074 controls - 2026-10-08

Portable evidenced control envelopes, a timing interpreter and one mapping/dispatcher pipeline now connect managed camera and scoped speech media controls to existing owners. Headset keys are session-scoped, not attributed by product name. Native HeyCyan voice remains reserved to the existing capture bridge. FEATURE-073 watch/PTT integration is absent; FEATURE-074 remains a separate unmerged branch.

See [implementation and open integration gates](engineering/WEARABLE-CONTROLS-003.md).
