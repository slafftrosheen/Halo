# Halo Product Vision

Status: DESIGN TARGET. Reviewed 2026-09-24.

**Halo is a host-native personal cognitive runtime.**

It connects a user's operating system, AI models, persistent workspace and surrounding devices into one coherent assistant.

## The Halo Concept

Halo should feel like one intelligence that follows the user across the host OS and the devices around them.

```
Halo
=
Portable Cognitive Runtime
+
Host-native system integration
+
Persistent cognitive workspace
+
Capability-driven device ecosystem
```

Wearables remain a major target, but they are not the boundary of the product. 
Devices may include:
- smart glasses
- earbuds/headphones
- watches
- rings
- pendants
- cameras
- sensors
- robots
- future AR devices
- devices not yet designed

*(HeyCyan must appear only as an example/reference implementation, not as the definition of Halo hardware.)*

## Host Model

```
Halo Runtime
      │
      ├── Android Host       CURRENT
      ├── Linux Host         FUTURE
      └── Windows Host       FUTURE
```

Host implementations consume native operating-system APIs. Android-specific implementation belongs below the host boundary. The Runtime itself must not become Android-shaped.

## "One Halo App" Rule

**Halo core functionality must never require a paid helper application, automation application or companion utility to complete Halo.**

Explicit examples of what we avoid:
- No required Tasker
- No required AutoInput
- No required Gemini app
- No required Google Assistant
- No required vendor companion app once Halo owns the required protocol
- No required Halo cloud account

**External integrations extend Halo. They never complete Halo.**

For host functionality, the priority is:
1. Native OS API
2. Structured OS integration
3. Accessibility / UI automation (as a fallback, not the primary operating model)

## Perpetual Ownership

A legitimately installed copy of Halo should remain useful even if every Halo-operated online service disappeared.

Core Halo remains useful with:
- Halo
- Host OS
- Local model

There is no mandatory Halo account, no mandatory Halo-operated cloud, and no subscription dependency for basic runtime operation. Remote models and services remain optional and user-selected.

## Assistant vs Provider Model

**The Android assistant role is a host integration. It is not an AI provider.**

Halo should eventually implement its own host-owned assistant shell through supported Android APIs. External assistant launch/handoff may remain as optional compatibility behavior but is not a core inference route.

## Interaction-Origin Continuity

An interaction should remember where it began.

If a user speaks through a wearable, Halo should prefer that wearable for the response when its capabilities allow it. 
- `HeyCyan microphone -> Halo -> HeyCyan speakers`
- `Earbud microphone -> Halo -> Earbud speakers`
- `Phone -> Halo -> Phone`

Keep input/output/context with the originating interaction device when its capabilities permit it. Fall back deliberately and visibly, not accidentally.
