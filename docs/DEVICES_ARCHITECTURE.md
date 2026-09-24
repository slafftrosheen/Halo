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

> **HeyCyan is reference hardware, not an architectural category.**
