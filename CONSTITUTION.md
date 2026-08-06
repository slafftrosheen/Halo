# HALO ENGINEERING CONSTITUTION

## Version
Alpha

## Owner
Chief Engineer

---

# 0. Mission
Halo is no longer an architectural experiment.
Halo is no longer an AI demo.
Halo is no longer a collection of engines.
Halo is now a **Cognitive Runtime**.

Every commit must move Halo toward a runtime that is:
* deterministic
* observable
* portable
* debuggable
* extensible

---

# 1. The Golden Rule
Everything in Halo must answer exactly one question.
**"Who owns this responsibility?"**
If two modules own the same thing -> Architecture bug.

Example of Correct Ownership:
`Reasoning` -> `GoalManager` -> `Goal` -> `Planning` -> `Execution` -> `Outcome`

---

# 2. Runtime owns orchestration
Engines never orchestrate. Engines perform work. Runtime coordinates.
Runtime is conductor. Engines are musicians.

---

# 3. Engine Contract
Every engine must implement exactly this contract.
```kotlin
interface CognitiveEngine {
    val state: StateFlow<EngineState>
    val health: StateFlow<EngineHealth>
    suspend fun initialize()
    suspend fun start()
    suspend fun stop()
    suspend fun dispose()
}
```
Nothing more. Never expose implementation.

---

# 4. Runtime Command Pattern
Every UI action becomes a RuntimeCommand.
```kotlin
runtime.execute(RuntimeCommand.StartListening)
```
Runtime owns state. UI reflects state.

---

# 5. Runtime Event Pattern
Everything publishes events. Never direct engine calls.
Every step observable.

---

# 6. Canonical Alpha Loop
This loop is frozen:
`Input` -> `Observation` -> `Interpretation` -> `Goal` -> `ExecutionPlan` -> `Capability` -> `Outcome` -> `Timeline` -> `Idle`
No additional cognitive stages before Alpha.

---

# 7. Speech Session
Speech becomes runtime. Not engine.
Audio engine never owns conversation.

---

# 8. Unified Input
Every input becomes `Observation`, regardless of source (Phone, Glasses, Bluetooth, Developer Injection). No special cases.

---

# 9. Unified Output
Every execution returns `Outcome`. Never Boolean.

---

# 10. Rule Pipeline
Never call the LLM first.
Pipeline: `Hard Rules` -> `Context Rules` -> `Semantic Rules`
Exit immediately when matched. LLM is last resort.

---

# 11. Goal Simplification
Alpha owns exactly one Goal class. No Goal inheritance explosion.

---

# 12. Planning
Planner converts `Goal` -> `ExecutionPlan`. Nothing else. No optimization. No replanning.

---

# 13. Execution
Capability interface remains tiny. Capabilities remain stateless.

---

# 14. Android Isolation
Android belongs only in `host-android` and `drivers`.
Never inside `runtime`, `reasoning`, `planning`, `ontology`.

---

# 15. UI Ownership
Compose never talks to Runtime.
Always: `Composable` -> `ViewModel` -> `UseCase` -> `Runtime` -> `Engine`

---

# 16. State
Every screen `StateFlow<UiState>`. Never expose Runtime objects directly.

---

# 17. Home Screen
Mission Control. Contains Runtime State, Microphone, Timeline Preview, Recent Activity, Runtime Summary. Nothing else.

---

# 18. Timeline
Timeline is first-class. Every cognitive step creates one event.

---

# 19. Diagnostics
Developer Console becomes Runtime Inspector.
Everything live.

---

# 20. Runtime Health
Every engine reports `EngineMetrics`. No hidden state.

---

# 21. Dependency Rule
Allowed: `UI` -> `SDK` -> `Runtime` -> `Engines` -> `Ontology`
Forbidden: `Runtime` -> `Android`

---

# 22. Module Rule
One concept. One module. Never duplicate models.

---

# 23. Alpha Commands
Freeze to a core set (Open Camera, Spotify, Play, Pause, Flashlight, Volume, Navigate Home, Remember This, Help). Perfect these. Do not add fifty more.

---

# 24. Performance
Cold Start <2s, Rule Pipeline <20ms, Reasoning <100ms, Speech <150ms, UI 60fps.

---

# 25. Testing
Every Capability, Rule, Runtime State, Planner, Engine Lifecycle must have tests.

---

# 26. Delete List
Remove: Generated build artifacts, Duplicate docs, Duplicate Goal classes, Dead providers, Unused dependencies, Mock runtime state, Fake battery/network values, TODOs in runtime path.

---

# 27. PR Checklist
Every PR answers:
* Does this reduce complexity?
* Does this improve observability?
* Does this improve runtime stability?
* Does this improve wearable readiness?
* Does this improve one complete user flow?
If not, do not merge.

---

# 28. Wearable Contract
Can this run without Android UI? If no, architecture probably needs adjustment.

---

# 30. Runtime Mapper Pattern
Every screen gets its own mapper (`RuntimeUiMapper<T>`). Keeps ViewModels extremely small.

---

# 31. Event Definitions
Freeze the core event set for Alpha. No engine-specific event leakage into the UI.

---

# 32. Acceptance Criteria for Every Sprint
* App installs on physical device.
* Existing flows work.
* No public API became more complicated without justification.
* No new architectural concept introduced.
* At least one user-visible flow became better.

---
**"Does this make the cognitive runtime stronger, or am I just making it bigger?"**
If it only makes it bigger, defer it until after Alpha.
