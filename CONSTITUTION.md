# HALO ENGINEERING CONSTITUTION

## Version
Alpha-W1

## Owner
Chief Engineer

---

# 0. Mission
Halo is no longer an architectural experiment.

Halo is no longer an AI demo.

Halo is no longer a collection of engines.

Halo is now a **Wearable Cognitive Runtime**.

Every commit must move Halo toward a runtime that is:
* deterministic
* observable
* portable
* debuggable
* extensible

---

# 1. Architecture & Kernel
Halo is a deterministic Cognitive Runtime. Instead of a monolithic runtime, Halo is a small **Runtime Kernel** surrounded by pluggable modules. The Kernel owns ONLY lifecycle, event bus, scheduling, DI, module registration, runtime state, and health monitoring. Everything else (Perception, Memory, Reasoning, Planning, Execution, Reflection, Drivers, Hosts, Capabilities) plugs into it.

---

# 2. Runtime Stability
The core runtime architecture is considered stable. Major redesigns require strong justification based on production requirements. New capabilities should extend the runtime rather than alter its core execution model.

---

# 3. Boundaries & Responsibilities
The **Runtime** strictly owns:
* perception
* cognition
* planning
* execution
* reflection
* timeline
* recovery

The **Host** strictly owns:
* audio capture
* audio playback
* buttons
* sensors
* bluetooth
* cameras
* permissions
* SDK integration

If two modules own the same thing -> Architecture bug.

---

# 4. Dependency Rules
Adhere to strict dependency inversion:
* Allowed: `runtime/` -> `core/` -> `interfaces/` -> `drivers/` -> `host-*`
* **NEVER**: `host-*` -> `runtime internals`
* **NEVER**: `runtime` -> `Android SDK`
* **NEVER**: `runtime` -> `HeyCyan SDK`

---

# 5. Driver Layer & Hosts
Drivers are first-class citizens (`drivers/audio`, `drivers/bluetooth`, `drivers/camera`, `drivers/buttons`, `drivers/sensors`). 

Hosts (e.g., `host-heycyan`, `host-android`) assemble these drivers.

---

# 6. Platform Services
Use interfaces for all platform-specific logic: `Clock`, `Logger`, `Storage`, `PowerManager`, `Scheduler`, `Network`, `Permissions`. 

**Never** call `System.currentTimeMillis()` inside the runtime.

---

# 7. Audio Pipeline
The audio flow is exactly:
`PCM` -> `Audio Preprocessing` -> `Voice Activity Detection` -> `Wake Word` -> `Speech Recognition` -> `Intent Observation`

---

# 8. Runtime State Machine
The runtime lifecycle flows through explicitly defined states:
`Idle` -> `Passive Listening` -> `Wake Detected` -> `Capturing` -> `Recognizing` -> `Thinking` -> `Planning` -> `Executing` -> `Speaking` -> `Passive Listening`

---

# 9. Power Modes
Wearables require power awareness. The runtime adapts via `RuntimeMode`: `PERFORMANCE`, `BALANCED`, `LOW_POWER`, `CHARGING`.

---

# 10. Alpha-W1 Success Criteria
1. HeyCyan connects
2. Runtime recognizes connection
3. Button event received
4. Wake word detected
5. PCM streamed
6. Observation created
7. Goal produced
8. Plan executed
9. Android capability invoked
10. Speech synthesized
11. Runtime returns to Passive Listening
12. No Android SDK references inside runtime
13. Host swap requires no runtime code changes

---

# 11. NEVER BUILD
* Android-specific logic in Runtime
* UI logic inside Runtime
* Device-specific code inside Runtime
* Blocking APIs inside Runtime
* Singleton state
* Mutable global state
* SDK wrappers outside host modules
* LLM-specific assumptions inside planning
* Vendor-specific APIs leaking into interfaces

---

# 12. Orchestration
Engines never orchestrate. Engines perform work. Runtime coordinates.

Runtime is conductor. Engines are musicians.

---

# 13. Engine Contract
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

# 14. Execution
`Capability` interface remains tiny. Capabilities remain stateless.

Capability implementations are resolved through the capability registry. Plugins
may contribute capabilities, but planning and runtime orchestration must not depend
on concrete capability class names.

---

# 15. Unified Input & Output
* Every input becomes `Observation`, regardless of source.
* Every execution returns `Outcome`. Never Boolean.

---

# 16. Rule Pipeline
Never call the LLM first.

Pipeline: `Hard Rules` -> `Context Rules` -> `Semantic Rules`

Exit immediately when matched. LLM is last resort.

---

# 17. PR Checklist
Every PR answers:
* Does this reduce complexity?
* Does this improve observability?
* Does this improve runtime stability?
* Does this improve wearable readiness?
* Does this improve one complete user flow?
If not, do not merge.

---

# 18. Product Implementation Rules

1. Runtime modules must not import platform SDKs.
2. Drivers own hardware protocols and preserve raw observations.
3. Hosts adapt drivers to runtime contracts.
4. Providers implement AI capabilities only; provider-specific APIs do not leak
   into runtime contracts.
5. Protocol implementations require capture evidence. Guessed packet meanings and
   speculative commands are forbidden.
6. Unknown protocol data must remain observable and reproducible.
7. Imported replay artifacts are immutable evidence; annotations belong in sidecars.
8. Every new abstraction must have a measurable integration or product benefit.
9. The simulator is a supported host for deterministic development and tests.
10. Every new working flow must be executable without physical hardware unless the
    behavior itself is hardware-specific.
11. Discovery Mode is an independent developer workflow. It must not invoke
    `HaloRuntime`, cognition, speech, providers, or automatic host connection.
12. Discovery Mode is raw-only: it may record transport observations but must not
    decode notifications or send commands.
13. No HeyCyan protocol implementation may be promoted from hypothesis to
    production based on synthetic captures, naming conventions, common BLE UUIDs,
    or assumptions from another application. Promotion requires a reproducible
    real-device capture or authoritative vendor documentation.

---

**"Does this make the cognitive runtime stronger, or am I just making it bigger?"**
If it only makes it bigger, defer it. Favor deleting code over adding abstractions.

# 19. Evidence-Driven Hardware Sequence

The HeyCyan work is intentionally staged:

`PR-015 Discovery` -> `PR-016 Capture Analysis` -> `PR-017 Event Decoder` ->
`PR-018 Command Layer` -> `PR-019 Device Context` -> `PR-020 Audio Investigation` ->
`PR-021 AudioSource` -> `PR-022 End-to-End Wearable Speech`

Runtime Kernel, provider architecture, host abstraction, capability registry,
audio pipeline API, capture format, replay, driver lifecycle, and DI decomposition
remain frozen throughout PR-016–PR-020 unless a real hardware requirement proves a
change necessary.

---

# 20. Discovery Acceptance Gate

Before merging physical discovery:

* Scan is unfiltered; multiple devices can appear; none is auto-selected.
* The selected address is the only address passed to GATT.
* Discovery never invokes `HaloRuntime` and sends no protocol commands.
* Services, characteristics, descriptors, properties, permissions, requested MTU,
  negotiated MTU, and sequential subscriptions are recorded.
* Advertisement, connection lifecycle, GATT metadata, CCCD writes, RX/TX, ready,
  disconnect, and failure capture state are persisted where observed.
* Synthetic definitions cannot affect discovery.
