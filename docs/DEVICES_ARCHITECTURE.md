# Device Capability Architecture

Status: DESIGN TARGET. Reviewed 2026-09-24.

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

- **Bluetooth headset**: Exposes `AudioInput`, `AudioOutput`, `MediaButtons`.
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
