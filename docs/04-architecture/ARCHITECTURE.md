# Current architecture

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

## Modules and composition

[settings.gradle.kts](../../settings.gradle.kts) defines app, host-android, runtime/*,
engines/*, sdk/*, drivers/*, providers/*, libraries/* and lab/*.
The old core/domain/application directory tree was not this repository's layout.

[ManualDependencyGraph](../../app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt)
composes InfrastructureGraph, ProviderGraph, HostGraph, EngineGraph and ObserverGraph.
It also creates CognitiveSession with in-memory conversation storage, a context assembler,
ToolRegistry and authorizer. ViewModels are lazy properties of this process graph.

[App HaloRuntime](../../app/src/main/java/com/halo/app/HaloRuntime.kt) initializes/starts
interpretation, reasoning, planning, execution and audio engines, command response,
speech session/bridge and speaker. A mutex serializes lifecycle changes; partial-start
failure runs cleanup. This is source behavior, not proof of every failure scenario.

## Execution paths

- Engine/EventBus/Blackboard is the app's declared production command pipeline.
- CognitiveSession handles provider conversations, streaming, bounded tool rounds and results.
- The app ToolRegistry contains echo and current-time executors. Android capability registration
  is separate and does not automatically expose model tools.
- The older HaloMicrokernel/ProcessManager path remains for compatibility/tooling and is
  explicitly excluded from normal phone startup.

HostGraph injects AndroidAudioSource into the phone audio engine. HeyCyan construction is lazy.
An existing HybridAudioSource is not proof it is selected in that production path.

## Boundaries and limitations

There are multiple bus/state abstractions, not one SharedFlow for every subsystem.
Providers own native/network adapters; their interfaces are not security sandboxes.
Universal use-case layering, immutable state, privacy interception, persistent recovery
and platform independence are not demonstrated repository-wide properties.

See [UI](../11-ui/UI-ARCHITECTURE.md), [storage](DATABASE.md),
[providers](../ai/AI_PROVIDER_ARCHITECTURE.md) and [security](../12-security/THREAT_MODEL.md).
Portability beyond Android remains direction, not verified production host support.
