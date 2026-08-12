# Cognitive Kernel v1

## Canonical Runtime Pipeline

This v1 pipeline is frozen. New architectural layers require an implementation
constraint that cannot be addressed within these contracts.

```text
Reality
  │
  ▼
Audio / Camera / Sensors
  │
  ▼
Perception Engines
  │
  ▼
Observation Bus
  │
  ▼
Interpretation Engine
  │
  ▼
Blackboard (Knowledge Graph)
  │
  ├── Working Memory
  ├── Reasoning
  └── Memory
         │
         ▼
  Planning Engine
         │
         ▼
  Execution Engine
         │
         ▼
Android / Glasses Host
```

The Blackboard is the source of truth for cognition. Engines publish immutable
knowledge records; they do not mutate existing records or keep competing
authoritative state.

## Package Ownership

`runtime:cognition` owns the shared cognitive contracts:

- `blackboard`: immutable knowledge, confidence, query, relations, graph, and
  the streamed `DefaultBlackboard` implementation.
- `workingmemory`: Blackboard-derived views and immutable snapshots.
- `context`: the `CognitiveContext` consumed by downstream engines.

The interpretation engine owns rules and orchestration. It depends on the
cognitive contracts, but cognition does not depend on an interpretation-engine
implementation.

## Knowledge and Blackboard

Every `Knowledge` record has a unique identity, typed payload, semantic
confidence, provenance, creation time, and optional expiry. Payloads are sealed
and cover observations, interpretations, goals, tasks, and decisions.

`DefaultBlackboard` is append-only. Its query and latest views are protected
from concurrent access, while `stream()` emits each published record for
diagnostics and reactive consumers. `KnowledgeGraph` stores immutable
relationships such as `DERIVED_FROM` and `REFERS_TO`.

## Working Memory and Context

Working memory owns no data. `DefaultWorkingMemory` derives the current goal,
active task, latest observation, latest interpretation, focused person, and
current location from the Blackboard.

`DefaultContextAssembler` creates a `CognitiveContext` for each observation:

```text
Observation
  │
  ▼
Working-memory snapshot + recent knowledge
  │
  ▼
Goals + active tasks
  │
  ▼
CognitiveContext
```

The snapshot is immutable, so rules see a stable view throughout a single
interpretation pass.

## Interpretation

The default engine performs a small orchestration flow:

```text
Observation → ContextAssembler → RulePipeline → Interpretation
            → Knowledge → Blackboard → EventBus
```

Rules are registered independently and execute in descending priority order.
The pipeline merges entities, slots, intent candidates, and the lexical,
contextual, and semantic confidence dimensions into a typed interpretation.

The deterministic `SpeechRule`, `ContactRule`, and `TimeRule` handle local
commands such as `Call John tomorrow at 9` without an AI provider. The result
contains a person entity, normalized time entity, target/time slots, and a
`Call` candidate.

An LLM is a fallback service, not a runtime owner. The optional `LlmRule`
executes only when the merged interpretation confidence is below its threshold.
Its client receives the `CognitiveContext` (observation, current goals,
working-memory snapshot, and recent knowledge) and must convert provider JSON
into the typed interpretation model before returning. Untyped provider objects
never enter the Blackboard.

## Developer Console

The developer console exposes three cognitive views:

- **Knowledge**: append-only records, their source, confidence, and timestamp.
- **Interpretation**: entities, candidates, slots, and confidence dimensions.
- **Reasoning**: current goal, available alternatives, latest decision, and
  its Blackboard-backed explanation.

These views make the cognitive timeline inspectable without coupling the
runtime to Android-specific tooling.

## v1 Implementation Status

The runtime contracts, engine lifecycle, event and command buses, provider and
driver abstractions, scheduler, service registry, observation model, cognitive
Blackboard, working-memory views, context assembly, and rule-based
interpretation are in place.

Reasoning, planning, execution, AI-provider integration, long-term memory, and
live debugger behavior remain separate engine implementations. They must
consume the typed cognitive contracts rather than introduce parallel state.

## AI Provider Integration

AI is an external cognitive service, accessed via the `AiProvider` abstraction.

```text
AiRequest (Multimodal)
   │
   ▼
AiProviderRouter
   │
   ▼
Selected AiProvider (Local, Assistant, etc.)
   │
   ▼
AiResponse
```

### Multimodal Abstraction

The `AiRequest` and `AiResponse` models in `sdk:provider-sdk` support text, image, audio, and video modalities. This allows Halo to remain provider-agnostic while supporting advanced AI capabilities.

### Provider Router

The `AiProviderRouter` in `engines:ai` manages registered providers and selects the best one based on:
1. User preference (manual selection).
2. Provider availability (`isAvailable()`).
3. Capability matching (e.g., ensuring a provider supports `IMAGE` if an image is attached).

### Android Assistant Integration

The `AndroidAssistantProvider` acts as a bridge to the system's default assistant. It supports "assistant handoff" using standard Android `ACTION_ASSIST` intents. This distinction is critical:
- **Assistant Handoff**: Halo triggers the system assistant UI for the user to continue the interaction. Halo does not necessarily receive the result back.
- **AI Response**: A provider returns a structured response directly to Halo's reasoning layer.
