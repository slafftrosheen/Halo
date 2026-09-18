> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../CURRENT-STATE.md).

# Devices Management Architecture

## The Devices Surface

**Devices** is a permanent, first-class management surface in Halo. It represents a user's saved and persisted embodiment identities, not merely a list of currently connected peripherals.

The generic `Devices` screen acts as the user's **embodiment management layer** and scales to accommodate multiple integrated devices.

### Generic Device Model
The `Devices` page itself remains strictly generic. It tracks high-level state such as:
- `Device`
- `DeviceId`
- `DeviceType`
- `ConnectionState`
- `Battery`
- `Firmware`
- `Capabilities`
- `AutoConnect`
- `LastSeen`

```text
DEVICES
│
├── HeyCyan BW-AG1 Pro
│   ├── Connected ●
│   ├── Auto-connect ON
│   ├── Battery 78%
│   ├── Firmware / protocol info
│   └── >
│
├── BuddyBot
│   ├── Offline
│   ├── Auto-connect ON
│   ├── Last seen ...
│   └── >
│
└── Add device
```

**CRITICAL:** The generic Devices UI must *not* contain device-specific controls. It does not know that HeyCyan requires resolution settings or BuddyBot requires a joystick.

## Capability-Driven Control Screens

Selecting a device opens its **device-specific control/configuration surface**.

This architecture strictly dictates that **capabilities should drive what the device surface can expose**. A device-specific screen is owned by the corresponding device integration, not by the generic Devices UI.

```text
Device
   ↓
DeviceType / Capabilities
   ↓
Device-specific UI
```

This model is scalable:
```text
Devices
   │
   ├── HeyCyan
   │      └── HeyCyanControlScreen
   │
   ├── BuddyBot
   │      └── BuddyBotControlScreen
   │
   ├── Bluetooth headset
   │      └── HeadsetControlScreen
   │
   └── Future device
          └── Its own surface
```

### Examples of Device-Specific Screens

#### HeyCyan
```text
HeyCyan
│
├── Connection
│   ├── Auto-connect
│   ├── Forget device
│   └── Reconnect
│
├── Camera
│   ├── Resolution
│   ├── Video mode
│   ├── Stabilisation
│   └── Photo/video behavior
│
├── Controls
│   ├── AI button
│   ├── Camera button
│   ├── Touch gestures
│   └── Remapping where supported
│
├── Audio
│   ├── Volume
│   └── Microphone behavior
│
└── Diagnostics
    ├── Battery
    ├── Firmware
    ├── Protocol
    └── Connection diagnostics
```
*Note: If the hardware doesn't support a capability (e.g., `controls.remapping = unsupported`), Halo must not expose it as functional.*

#### BuddyBot
```text
BuddyBot
│
├── Connection
│   ├── Auto-connect
│   ├── Transport
│   └── Forget
│
├── Manual Control
│   ├── Joystick
│   ├── Speed limit
│   ├── Stop
│   └── Emergency stop
│
├── Configuration
│   ├── Hardware profile
│   ├── Sensors
│   ├── Motion
│   └── Accessories
│
├── Safety
│   ├── Current safety state
│   ├── Range sensor status
│   ├── Motion limits
│   └── Safety diagnostics
│
└── Diagnostics
    ├── Firmware
    ├── Runtime state
    ├── Sensors
    └── Logs
```

## Manual Control vs. Cognitive Execution
Manual Control is an explicit mode, distinctly separate from normal cognitive execution.

**Cognitive Control:**
```text
Halo cognitive control
        │
        │ RobotIntent
        ▼
   SafetySupervisor
```

**Manual Control:**
```text
Human
  │
  ▼
Manual Control UI
  │
  ▼
BuddyBot
  │
  ▼
SafetySupervisor
```

**CRITICAL SAFETY REQUIREMENT:** Manual control must **never bypass the embodiment's SafetySupervisor**. Manual override signifies human-directed control, not unsafe direct motor access. The joystick cannot override safety limits or estop conditions.

---
*Added as a P1 UI/product requirement during Batch 02.*
