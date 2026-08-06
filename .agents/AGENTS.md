# HALO ENGINEERING CONSTITUTION - Core Directives for Agents

The following directives MUST govern all architectural and implementation choices within this repository.

1.  **Architecture:** Halo is a deterministic Cognitive Runtime. The canonical loop is frozen: `Input -> Observation -> Interpretation -> Goal -> ExecutionPlan -> Capability -> Outcome -> Timeline -> Idle`.
2.  **Orchestration:** The Runtime coordinates. Engines (Reasoning, Planning, Execution, etc.) NEVER orchestrate or call each other directly.
3.  **Engine Contract:** Engines must strictly implement `CognitiveEngine`. Never expose implementation details.
4.  **UI & State:** `RuntimeCommand` for inputs. `StateFlow<UiState>` for screens. `RuntimeUiMapper<T>` for mapping. Compose NEVER talks to Runtime directly.
5.  **Platform Isolation:** Android belongs ONLY in `host-android` and `drivers`. It NEVER bleeds into `runtime`, `reasoning`, `planning`, or `ontology`.
6.  **Capabilities:** `Capability` interface is tiny and stateless.
7.  **PR Checklist:** Every change must reduce complexity, improve observability/stability, and keep wearable readiness in mind.
8.  **The Golden Question:** "Does this make the cognitive runtime stronger, or am I just making it bigger?" If it only makes it bigger, defer it. Favor deleting code over adding abstractions.
