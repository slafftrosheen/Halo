# ALPHA REFERENCE IMPLEMENTATIONS

These are canonical patterns. Every engine, provider, and capability must eventually resemble these implementations.

---

# 1. Canonical Engine Interface

Every engine should look identical.

```kotlin
interface CognitiveEngine {
    val id: EngineId
    val name: String
    val version: Version
    val state: StateFlow<EngineState>

    suspend fun initialize()
    suspend fun start()
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    suspend fun dispose()
}
```

Every engine. No exceptions.

---

# 2. Engine State

```kotlin
sealed interface EngineState {
    data object Initializing : EngineState
    data object Ready : EngineState
    data object Running : EngineState
    data object Idle : EngineState
    data object Paused : EngineState
    data object Recovering : EngineState
    data class Failed(val error: Throwable): EngineState
    data object Stopped : EngineState
}
```

Never use Booleans.

---

# 3. Runtime Health

```kotlin
data class RuntimeHealth(
    val healthy: Boolean,
    val enginesRunning: Int,
    val totalEngines: Int,
    val lastError: Throwable?,
    val uptime: Duration,
    val cpuUsage: Float,
    val memoryUsage: Long
)
```

Displayed directly in DevTools.

---

# 4. Blackboard Contract

Everything publishes through one interface.

```kotlin
interface Blackboard {
    suspend fun publish(knowledge: Knowledge)
    suspend fun <T:Knowledge> query(type: KClass<T>): List<T>
    suspend fun latest(type: KClass<out Knowledge>): Knowledge?
}
```

No engine talks directly to another.

---

# 5. Event Bus

```kotlin
interface EventBus {
    suspend fun publish(event: Event)
    fun subscribe(type: KClass<out Event>): Flow<Event>
}
```

No callbacks. No listeners. Everything Flow-based.

---

# 6. Capability Contract

```kotlin
interface Capability {
    val id: CapabilityId
    suspend fun supports(task: Task): Boolean
    suspend fun execute(task: Task): TaskResult
}
```

Android integrations should ONLY exist here.

---

# 7. Provider Contract

Ollama. Whisper. OpenAI. Future providers. Exactly same interface.

```kotlin
interface AIProvider {
    suspend fun complete(request: CompletionRequest): CompletionResponse
    suspend fun health(): ProviderHealth
}
```

---

# 8. Speech Input

Current architecture should become:
`Audio -> VAD -> Speech Recognition -> Wake Word -> Interpretation`

Not: `Speech -> Everything`
Separate responsibilities.

---

# 9. Wake Word

Wake word should NOT execute commands.
It should only generate `WakeWordDetected`.
Then Runtime decides. Huge architectural difference.

---

# 10. Interpretation Output

Never return String. Return:
```kotlin
data class Interpretation(
    val intent: Intent,
    val entities: List<Entity>,
    val confidence: Float,
    val source: Observation
)
```

---

# 11. Goal

Canonical Goal.
```kotlin
data class Goal(
    val id: GoalId,
    val priority: Priority,
    val intent: Intent,
    val createdAt: Instant,
    val confidence: Float
)
```
Planning consumes Goals.

---

# 12. Plan

```kotlin
data class ExecutionPlan(
    val tasks: List<Task>,
    val estimatedDuration: Duration,
    val confidence: Float
)
```

---

# 13. Execution

Never `Boolean`.
Always `sealed interface TaskResult`:
`Success`, `Failure`, `Retry`, `Cancelled`, `Partial`.

---

# 14. Timeline Event

This becomes UI.
```kotlin
data class TimelineEvent(
    val timestamp: Instant,
    val stage: CognitiveStage,
    val title: String,
    val subtitle: String,
    val duration: Duration
)
```

---

# 15. Engine Metrics

Every engine reports:
```kotlin
data class EngineMetrics(
    val latency: Duration,
    val processedEvents: Long,
    val failures: Long,
    val uptime: Duration,
    val queueDepth: Int
)
```
Displayed in DevTools.

---

# 16. Repository Layer

No engine talks directly to storage.
`MemoryRepository`, `SettingsRepository`, `ProviderRepository`, `GoalRepository`.

---

# 17. Android Layer

Everything Android-specific behind `host-android`.
Never import `android.*` inside cognition modules.

---

# 18. Coroutine Pattern

Every engine:
```kotlin
private val scope = CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineName(name)
)
```
No GlobalScope. Ever.

---

# 19. Error Handling

Every engine:
```kotlin
CoroutineExceptionHandler { _, throwable ->
    logger.error(...)
    state.value = Failed(throwable)
}
```
Never crash runtime.

---

# 20. Dependency Direction

Never violate this.
`Android Host -> SDK -> Runtime -> Engines -> Libraries -> Ontology`
Never upwards.

---

# 21. Module Ownership

One concept. One owner.
Example: `Goal -> ontology`
NOT reasoning, planning, execution. Only one module defines Goal.

---

# 22. UI State

Every screen: `data class HomeUiState(...)`
Never expose runtime models directly.

---

# 23. Compose Pattern

`Screen -> ViewModel -> UseCase -> Runtime -> Engine`
No runtime access from Composable.

---

# 24. Developer Dashboard

Every page updates through StateFlow. Never polling.

---

# 25. Logging

Canonical:
```kotlin
logger.info { "Speech recognized" }
```
Structured. Never println.

---

# 26. Runtime Boot

Exactly: `Runtime -> Registry -> EventBus -> Blackboard -> Providers -> Engines -> Ready`
Never random initialization.

---

# 27. Runtime Shutdown

Reverse order. Always.

---

# 28. Runtime State Machine

`Initializing -> Ready -> Running -> Paused -> Recovering -> Stopping -> Stopped`
Explicit.

---

# 29. Manual Mic Flow

`Mic Button -> Speech Session -> Recognition -> Interpretation -> Reasoning -> Planning -> Execution -> Timeline -> Idle`

---

# 30. Passive Wake Flow

`Passive Listen -> Wake Word -> Capture -> Recognition -> Reasoning -> Execution -> Passive Listen`

---

# 31. Developer Injection

`Text -> Interpretation -> Reasoning -> Planning -> Execution`
Never bypass reasoning.

---

# 32. Future Glasses

The single most important contract.
The runtime must **never care** whether an event came from:
* phone microphone
* smart glasses
* Bluetooth headset
* future AR device
* keyboard
* developer injection

Everything becomes `Observation`. That abstraction makes glasses integration trivial.

---

> **From this point onward, Halo should evolve like a kernel, not like an application.**
> 
> Every new implementation should make the runtime more deterministic, observable, and portable. Android is the first host, not the final destination. Speech is the first perception modality, not the only one. The glasses should eventually become another producer of `Observation` objects, just as Android execution should become one consumer of `ExecutionPlan` objects.
> 
> During Alpha, optimize for one thing above all else:
> **When the glasses arrive, the runtime should already exist. The hardware should simply plug into it.**
