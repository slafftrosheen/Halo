# Target Architecture

Status: DESIGN TARGET. Reviewed 2026-09-24.

This document describes the architectural targets we are intentionally building toward. (For what is currently implemented, see [ARCHITECTURE.md](ARCHITECTURE.md)).

## Interaction Flow

```text
                    User
                      │
             Invocation Sources
      ┌───────────────┼─────────────────┐
     Phone          Wearable          Host
   assistant        buttons          UI/input
      │               │                 │
      └───────────────┼─────────────────┘
                      ▼
             InteractionSession
                      │
                Context Layer
       ┌──────────────┼───────────────┐
     audio          visual          screen
     files         Notebook         sensors
       └──────────────┼───────────────┘
                      ▼
                 Halo Runtime
         observe → understand → plan
                     → act
                      │
            ┌─────────┴─────────┐
            │                   │
      Provider Router      Capability Router
            │                   │
       AI models            Host + Devices
            │                   │
            └─────────┬─────────┘
                      ▼
                Output Routing
                      │
                 originating
               interaction device
```

## Architectural Concepts

The following concepts represent our target abstractions:

- **InteractionSession**: Manages the lifecycle of a discrete user interaction, maintaining continuity and origin.
- **AudioSessionCoordinator**: Orchestrates audio input, output, focus, and routing across phone and wearables.
- **ContextAcquisitionPolicy**: Determines what context (screen, audio, location) should be gathered for a given invocation.
- **ResourceLeaseManager**: Handles concurrent access to hardware like cameras, microphones, or heavy local models.
- **DeviceCapabilityResolver**: Determines which connected devices are capable of fulfilling a requested action.
- **InteractionRoutePolicy**: Dictates how input and output should be routed (e.g., returning audio to the earbud that captured the wake word).
- **HostCapability**: Abstractions over OS-level features (e.g., dialer, alarms).
- **WearableCapability**: Abstractions over hardware features (e.g., WearSensor, DisplayTarget).

These are targets for future batches, not currently implemented contracts.
