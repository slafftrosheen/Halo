# HALO ENGINEERING CONSTITUTION - Core Directives for Agents

The following directives MUST govern all architectural and implementation choices within this repository.

1. **Architecture & Kernel:** Halo is a deterministic Cognitive Runtime. The Runtime Kernel owns lifecycle, event bus, scheduling, DI, module registration, runtime state, and health monitoring. Everything else plugs into it.
2. **Runtime Stability:** The core runtime architecture is stable. Extend it rather than redesigning its execution model without a production requirement.
3. **Runtime Responsibilities:** The Runtime owns perception, cognition, planning, execution, reflection, timeline, and recovery.
4. **Host Responsibilities:** The Host owns audio capture/playback, buttons, sensors, Bluetooth, cameras, permissions, and SDK integration.
5. **Dependency Rules:** Runtime code must not depend on Android SDK or HeyCyan internals; hosts must not depend on runtime internals.
6. **Driver Layer:** Drivers are first-class modules. Hosts assemble drivers behind SDK contracts.
7. **Platform Services:** Use interfaces for platform services such as Clock, Logger, Storage, Scheduler, Network, and Permissions. Do not use blocking APIs in Runtime.
8. **Audio Pipeline:** `PCM -> preprocessing -> VAD -> wake word -> speech recognition -> intent observation`.
9. **Runtime State Machine:** `Idle -> Passive Listening -> Wake Detected -> Capturing -> Recognizing -> Thinking -> Planning -> Executing -> Speaking -> Passive Listening`.
10. **Orchestration:** Runtime coordinates; engines perform work and do not orchestrate one another.
11. **Simulator:** The simulator host is the canonical deterministic host for development and tests without physical hardware.
12. **Discovery boundary:** Physical BLE discovery is a separate developer workflow. It must not invoke `HaloRuntime`, cognition, speech, providers, or automatic host connection.
13. **Raw-only discovery:** Discovery records transport evidence only. It must not decode notifications or send commands. The selected BLE address must be explicit.
14. **Protocol evidence gate:** Never promote HeyCyan protocol behavior from hypothesis to production based on synthetic captures, naming conventions, common BLE UUIDs, or assumptions from another application. Promotion requires a reproducible real-device capture or authoritative vendor documentation.
15. **Unknown preservation:** Unknown protocol data remains observable, immutable, and replayable. Imported capture JSON is immutable; annotations belong in sidecars.
16. **PR-015 gate:** Before merge, verify unfiltered scan, explicit selection, complete GATT metadata, MTU observations, sequential CCCD subscriptions, raw RX/TX capture, disconnect/failure persistence, and no runtime/decoder/command involvement in Discovery Mode.
17. **Roadmap lock:** Follow `PR-015 Discovery -> PR-016 Capture Analysis -> PR-017 Event Decoder -> PR-018 Command Layer -> PR-019 Device Context -> PR-020 Audio Investigation -> PR-021 AudioSource -> PR-022 End-to-End Speech`. Keep the kernel, providers, host abstraction, capability registry, audio API, capture format, replay, lifecycle, and DI decomposition frozen until real hardware requires a change.

Before implementing a change, ask: **Does this make the cognitive runtime stronger, or am I only making it bigger?**
