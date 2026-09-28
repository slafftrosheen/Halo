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

Implemented in RUNTIME-017 (source-verified, not yet hardware-validated): the portable tool
fabric — `ToolDescriptor` metadata separate from provider-facing `ToolDefinition`, semantic
categories, descriptor-driven risk/confirmation/replay policies, per-tool execution policy,
`ToolRetryRules` non-replay guarantees, `CapabilityCatalogue`/`CapabilityPublisher` semantic
capability advertisement (Android adapter first), and bounded `ToolDiscoveryService` per-turn
exposure via the `halo_discover_tools` meta-tool.

Implemented in RUNTIME-018 (IMPLEMENTED / UNVALIDATED): durable execution. An agent run is a
durable execution whose exact state survives process death:

```text
User Interaction
       │
       ▼
CognitiveSession  (submit / resume — one execution path)
       │
       ▼
Agent Run
       │
       ▼
Durable Execution Journal (append-only AgentStep evidence)
       │
 ┌─────┼─────────────────┐
 │     │                 │
 ▼     ▼                 ▼
Provider Turn        Tool Invocation
(ProviderTurnStarted/   │
 ProviderTurnCompleted) Execution Claim
                        │ (ToolExecutionClaimed)
                        │
                  Tool Outcome
                  (ToolOutcomeRecorded)
       │
       ▼
Recovery Planner (deterministic, journal-only)
       │
 ┌─────┼───────────┬────────────┐
 │     │           │            │
resume wait input reconcile   uncertain
```

> Process death is an execution state transition, not an instruction to repeat the
> user's request.

Recovery truth table (core engineering rule):

| Durable evidence                          | Recovery                        |
| ----------------------------------------- | ------------------------------- |
| Provider started, no response             | retry provider turn             |
| Tool prepared, not claimed                | safe to continue                |
| SAFE tool claimed, no result              | may retry                       |
| Idempotent tool claimed                   | retry same identity             |
| Reconcile-required tool claimed           | reconcile first                 |
| Never-replay tool claimed                 | uncertain, user action required |
| Tool result exists, no provider follow-up | continue provider               |
| Approval pending                          | restore pending approval        |
| Required transient context missing        | request context again           |
| Final response recorded, not committed    | commit idempotently             |

Implemented in RUNTIME-019 (IMPLEMENTED / UNVALIDATED): portable interaction surfaces. Human
interaction is a Runtime-owned semantic protocol, independent of any host UI:

```text
                   HALO RUNTIME

             Durable Agent Execution
                      │
               AgentInputRequest
                      │
                      ▼
             Interaction Surface
              (renderable projection)
                      │
          ┌───────────┼────────────┐
          │           │            │
       Android      Desktop      Wearable
       Compose       future       future
          │           │            │
          └───────────┼────────────┘
                      │
              InteractionResponse
                      │
                      ▼
      resolveInputIfPending (atomic CAS)
                      │
                      ▼
             CognitiveSession.resume()
```

The Runtime owns what is asked, why, the legal responses, the run binding and expiry; hosts own
look, placement and input collection. Surface kinds are semantics (CONFIRMATION, CHOICE,
TEXT_INPUT, TEMPORAL_INPUT, FILE_SELECTION, PROGRESS, RESULT, ERROR) — never DIALOG/SHEET.
Surfaces are the projection; the journal stays the only durable truth, and projections are
rebuilt after process death. Dismiss ≠ deny; cancel-input ≠ cancel-run; first valid resolution
wins atomically; result truth (`ActionTruth`) comes from the journal, and UNKNOWN stays visibly
unknown. Android Compose is the first renderer; voice reuses the existing speech runtime with
origin-device affinity; a future Linux/Windows/AR host implements the same response contract
without touching cognition.

Implemented in RUNTIME-020 (IMPLEMENTED / UNVALIDATED): Resource Fabric, Capability Affinity, and Multi-Device Arbitration.

```text
                           HALO RUNTIME

                  Capability Requirement
                           │
                           ▼
                    Capability Resolver
                           │
                   selected provider
                    + selected owner
                           │
                           ▼
                    Resource Planner
                           │
             ┌─────────────┼─────────────┐
             │             │             │
          audio          camera       compute
          input          sensor        heavy
             │             │             │
             └─────────────┼─────────────┘
                           ▼
                  ResourceLeaseManager
                           │
                atomic ownership decision
                           │
                     ResourceLease
                           │
                           ▼
                   capability executes
                           │
                           ▼
                      lease released


HOST / DRIVER INTERNAL ARBITRATION REMAINS BELOW THIS:

Android audio routing
HeyCyanResourceArbiter
LiteRT-LM model lifecycle
Bluetooth transport
...
```

The core rule: A capability answers *who can do something*. A resource lease answers *who is allowed to use the scarce underlying thing right now*. Resource ownership is managed centrally by `ResourceLeaseManager` (`sdk/runtime-sdk/.../resource/`), `DeviceCapabilityResolver`, `ResourcePlanner`, and `ToolResourceCoordinator`. Subsystems (`HaloAudioSessionCoordinator`, `NativeResourceCoordinator`, leased driver capabilities) bridge onto generic resource leases, while physical driver arbitration (`HeyCyanResourceArbiter`) remains below Runtime.

## RUNTIME-021 multimodal execution order

The implemented target order is:

```text
interaction + origin + policy
          │
          ▼
    workload plan
(required AI + context)
          │
          ▼
 compatible target
(provider/model capability truth)
          │
          ▼
 context acquisition
(exact interaction/origin; active capture only if needed)
          │
          ▼
 target-aware budget/assembly
          │
          ▼
 pinned provider turn
          │
          ▼
 bounded tool rounds on same target
```

The dependency rule is the important part: provider selection is based on the workload that will be
executed, not only on modalities already present in the initial `AiRequest`.
