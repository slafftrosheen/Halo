# Driver SDK Specification

The Driver Layer makes Halo a platform by abstracting hardware differences.

## Responsibilities
Driver authors implement exactly five responsibilities:
1. **Discovery**
2. **Connection**
3. **Streaming**
4. **Capabilities**
5. **Diagnostics**

Nothing else. Drivers never know about AI or intent resolution.

## Driver Lifecycle
`Discover` -> `Pair` -> `Authenticate` -> `Capabilities` -> `Initialize` -> `Ready` -> `Streaming` -> `Sleep` -> `Disconnect`

## Device Capabilities
Drivers must report capabilities during the `Capabilities` phase.
* `audioInput`
* `audioOutput`
* `buttons`
* `camera`
* `display`
* `imu`
* `vibration`

## Driver Isolation
SDK crashes must never crash Halo. 
* Every driver is wrapped in a dedicated coroutine scope or process.
* Exceptions are converted to standardized Event Bus failures.
* Timeouts are strictly enforced on all blocking or long-running SDK calls.
