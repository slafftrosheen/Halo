# HALO ENGINEERING CONSTITUTION - Core Directives for Agents

The following directives MUST govern all architectural and implementation choices within this repository.

1. **Architecture & Kernel:** Halo is a deterministic Cognitive Runtime. Instead of a monolithic runtime, Halo is a small **Runtime Kernel** surrounded by pluggable modules. The Kernel owns ONLY lifecycle, event bus, scheduling, DI, module registration, runtime state, and health monitoring. Everything else plugs into it.
2. **Runtime Stability:** The core runtime architecture is considered stable. Major redesigns require strong justification based on production requirements. New capabilities should extend the runtime rather than alter its core execution model.
3. **Runtime Responsibilities:** The Runtime strictly owns: perception, cognition, planning, execution, reflection, timeline, and recovery.
4. **Host Responsibilities:** The Host strictly owns: audio capture, audio playback, buttons, sensors, bluetooth, cameras, permissions, and SDK integration.
5. **Dependency Rules:**
   - Allowed: `runtime/` -> `core/` -> `interfaces/` -> `drivers/` -> `host-*`
   - **NEVER**: `host-*` -> `runtime internals`
   - **NEVER**: `runtime` -> `Android SDK`
   - **NEVER**: `runtime` -> `HeyCyan SDK`
6. **Driver Layer:** Drivers are first-class citizens (`drivers/audio`, `drivers/bluetooth`, etc.). Hosts assemble these drivers.
7. **Platform Services:** Use interfaces for all platform-specific logic (`Clock`, `Logger`, `Storage`, `PowerManager`, `Scheduler`, `Network`, `Permissions`). Never call `System.currentTimeMillis()` inside the runtime.
8. **Audio Pipeline:** The audio flow is: `PCM` -> `Audio Preprocessing` -> `Voice Activity Detection` -> `Wake Word` -> `Speech Recognition` -> `Intent Observation`.
9. **Runtime State Machine:** The runtime lifecycle flows: `Idle` -> `Passive Listening` -> `Wake Detected` -> `Capturing` -> `Recognizing` -> `Thinking` -> `Planning` -> `Executing` -> `Speaking` -> `Passive Listening`.
10. **Power Modes:** Wearables require power awareness. The runtime adapts via `RuntimeMode` (`PERFORMANCE`, `BALANCED`, `LOW_POWER`, `CHARGING`).
11. **Alpha-W1 Success Criteria:** HeyCyan connects, runtime recognizes connection, button event received, wake word detected, PCM streamed, observation created, goal produced, plan executed, Android capability invoked, speech synthesized, runtime returns to Passive Listening. No Android SDK references inside runtime. Host swap requires no runtime code changes.
12. **NEVER BUILD:**
    - Android-specific logic in Runtime
    - UI logic inside Runtime
    - Device-specific code inside Runtime
    - Blocking APIs inside Runtime
    - Singleton state
    - Mutable global state
    - SDK wrappers outside host modules
    - LLM-specific assumptions inside planning
    - Vendor-specific APIs leaking into interfaces
13. **Orchestration:** The Runtime coordinates. Engines (Reasoning, Planning, Execution, etc.) NEVER orchestrate or call each other directly.
14. **Engine Contract:** Engines must strictly implement `CognitiveEngine`. Never expose implementation details.
15. **Capabilities:** `Capability` interface is tiny and stateless.
16. **The Golden Question:** "Does this make the cognitive runtime stronger, or am I just making it bigger?" If it only makes it bigger, defer it. Favor deleting code over adding abstractions.
