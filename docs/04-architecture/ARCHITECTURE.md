# Software Architecture Specification (v2)

## Enterprise-Level Architecture

---

## 1. Design Philosophy

Halo is not an Android application.
Halo is a platform.
Android is merely the first host.

Everything should be written so that the business logic could theoretically run on:
- Android
- Android XR
- Linux
- Desktop
- Automotive
- Embedded ARM

Only adapters change.

---

## 2. Architecture

Use a variation of **Clean Architecture + Hexagonal Architecture + Event Driven Design**.

```text
                Android UI
        Jetpack Compose Screens
──────────────────────────────────────
Presentation Layer
ViewModels
UI State
Navigation
──────────────────────────────────────
Application Layer
Use Cases
Command Bus
Query Bus
Workflow Engine
──────────────────────────────────────
Domain Layer
Assistant
Memory
Voice
Intent
Plugins
Automation
Context
──────────────────────────────────────
Infrastructure Layer
SQLite
BLE
HeyCyan SDK
Retrofit
llama.cpp
Android APIs
Filesystem
Notifications
──────────────────────────────────────
External Devices
HeyCyan
Meta
Bluetooth
Future Devices
```

Everything points inward.
Nothing in the Domain knows Android exists.

---

## 3. Project Structure

```text
halo/
  app/
  core/
    domain/
    application/
    presentation/
    infrastructure/
  drivers/
  plugins/
  providers/
  testing/
  docs/
  samples/
```

---

## 4. Core Packages

```text
core/
  assistant/
  voice/
  intent/
  memory/
  automation/
  plugin/
  device/
  provider/
  context/
  events/
  permissions/
  diagnostics/
  privacy/
  logging/
```

Every package must be independently testable.

---

## 5. Domain Objects

Never expose mutable objects.

Example:
```kotlin
data class AssistantState(
    val listening: Boolean,
    val speaking: Boolean,
    val connected: Boolean,
    val provider: ProviderInfo,
    val currentIntent: Intent?,
    val battery: Int
)
```

Everything immutable.

---

## 6. Dependency Rule

```text
UI -> Application -> Domain -> Interfaces -> Infrastructure
```

Never reverse.

**Bad:**
```text
Assistant -> HeyCyan SDK
```

**Good:**
```text
Assistant -> Device Interface -> HeyCyanDriver
```

---

## 7. Event Driven System

Almost everything becomes an event.

```text
WakeWordDetected
       ↓
SpeechCaptured
       ↓
SpeechRecognized
       ↓
IntentResolved
       ↓
ActionExecuted
       ↓
ResponseGenerated
       ↓
SpeechPlayed
```

No direct coupling.

---

## 8. Event Bus

One SharedFlow. Every subsystem subscribes.

Examples:
- `NotificationReceived`
- `PluginInstalled`
- `MemoryStored`
- `ProviderChanged`
- `ConnectionLost`
- `WakeWordDetected`
- `BatteryLow`
- `CallStarted`
- `MediaPlaying`
- `BluetoothConnected`

Every event timestamped.

---

## 9. Voice Pipeline

```text
Mic -> Noise Suppression -> Echo Cancellation -> Voice Activity Detection -> Wake Word -> Streaming Buffer -> Speech Recognition -> Intent Resolver -> Workflow Engine -> Provider -> Response -> TTS -> Speaker
```

Each box replaceable.

---

## 10. Workflow Engine

One of the most important systems.

Example: User says "Remember this."

```text
Listening -> Recognizing -> Extract Context -> Store Memory -> Generate Confirmation -> Speak
```

Instead of gigantic functions, every interaction becomes a workflow.

---

## 11. Workflow Definition

Example:
```text
Start -> Capture Audio -> Recognize Speech -> Determine Intent -> Validate -> Execute Skill -> Generate Response -> Output Audio -> Finish
```

Each node independent.

---

## 12. Assistant State Machine

```text
Idle -> Listening -> Thinking -> Speaking -> Waiting -> Idle
```

Never allow illegal transitions.

---

## 13. Connection State Machine

```text
Disconnected -> Scanning -> Connecting -> Connected -> Streaming -> Disconnected
```

Reconnect automatically.

---

## 14. Audio Manager

Responsibilities:
- Bluetooth routing
- Microphone selection
- Echo cancellation
- Sample rate conversion
- Audio focus
- Volume normalization
- Latency estimation

This deserves its own module.

---

## 15. Driver Manager

Instead of: `HeyCyanDriver`

Have:
```text
DriverManager -> Current Driver -> Driver Interface
```

Allows runtime switching.

---

## 16. Driver Lifecycle

```text
Discover -> Pair -> Authenticate -> Capabilities -> Initialize -> Ready -> Streaming -> Sleep -> Disconnect
```

---

## 17. Capability Discovery

Every driver reports:
- SupportsAudioInput
- SupportsAudioOutput
- SupportsButtons
- SupportsTouch
- SupportsIMU
- SupportsCamera
- SupportsBattery
- SupportsDisplay

Assistant adapts automatically. No if-statements for devices.

---

## 18. Device Capability Object

```kotlin
DeviceCapabilities(
    audioInput,
    audioOutput,
    buttons,
    camera,
    display,
    imu,
    vibration,
    firmwareVersion
)
```

---

## 19. Context Engine

Runs continuously. Updates every second or on event.

Produces `ContextSnapshot` containing:
Time, Location, Movement, Current App, Calendar, Media, Phone State, Bluetooth, Battery, Network, Device, Recent History.

---

## 20. Intent Resolution

Pipeline:
```text
Speech -> Normalize -> Entity Extraction -> Intent Classification -> Slot Filling -> Confidence -> Routing
```

Don't immediately call an LLM. Simple commands stay local.

---

## 21. Confidence Scoring

Every intent 0-100%
- Above 90: Execute
- 70-90: Confirm
- Below 70: Ask again

---

## 22. AI Router

Decision tree:
```text
Simple? -> Android
Complex? -> LLM
Vision? -> Vision Provider
Offline? -> Local Provider
```

No provider-specific logic.

---

## 23. Memory Architecture

Three layers:
- Short: Conversation buffer
- Medium: Recent interactions
- Long: Persistent knowledge

Every layer searchable.

---

## 24. Search Pipeline

```text
User Query -> Embedding -> Vector Search -> Ranking -> Context Window -> LLM
```

Everything local if possible.

---

## 25. Plugin Architecture

Plugins never modify core. They register: Commands, Intents, Automation rules, Settings, Permissions, Capabilities.

---

## 26. Plugin Lifecycle

```text
Install -> Validate -> Load -> Initialize -> Ready -> Running -> Unload -> Disabled
```

---

## 27. Permissions

Plugin manifest:
Calendar, Notifications, Network, Location, Speech, Memory, Filesystem.

Users can deny any permission.

---

## 28. Command Bus

Instead of: `Plugin calls Memory directly`

Do:
```text
Plugin -> Command Bus -> Handler -> Memory
```

Every action logged.

---

## 29. Query Bus

Same idea. Read operations separated. Much easier to debug.

---

## 30. Scheduling Engine

Supports: Immediate, Delayed, Recurring, Context based.

Examples: At Work, After Sunset, Bluetooth Connected, Calendar Ends, Battery Low.

Entirely local.

---

## 31. Notification Pipeline

```text
Android Notification -> Normalize -> Classify -> Importance -> Plugin Hooks -> Summarize -> Deliver
```

Every stage measurable.

---

## 32. Privacy Layer

Every external request intercepted.

```text
Provider -> Privacy Gateway -> Permission Check -> Network
```

Impossible to bypass.

---

## 33. Privacy Gateway

Logs: Destination, Payload size, Reason, Plugin, Provider, Duration, Timestamp.

Not payload contents.

---

## 34. Diagnostics

Every subsystem exports metrics: Speech latency, BLE latency, Wake word delay, LLM speed, Memory usage, Battery drain, Audio dropouts, Plugin execution.

---

## 35. Metrics Engine

Produces `Health Report`.

Example:
```text
Wake Word: 78ms
Speech: 0.92s
LLM: 17 tok/sec
Battery: 1.3%/hour
BLE RSSI: -58 dBm
```

---

## 36. Crash Recovery

Persistent state. Recover Connection, Session, Automation, Memory queue. Nothing lost.

---

## 37. Security

Encrypted local database (optional), Android Keystore, Encrypted provider keys, Secure preferences. No plaintext secrets.

---

## 38. Networking

Single gateway.

```text
Every provider -> Gateway -> TLS -> Logging -> Retry -> Result
```

Never let providers open sockets directly.

---

## 39. Configuration

One immutable configuration object `HaloConfiguration` contains:
Speech, Wake Word, Provider, Privacy, Drivers, Plugins, Memory, Automation, Theme, Language.

---

## 40. Versioning

Everything semantic. Drivers, Plugins, Providers, API, Database. Separate versions.

---

## 41. API Stability

Never break Plugin API, Driver API, Provider API. Deprecate slowly.

---

## 42. Performance Targets

- Cold start: < 2 seconds
- Wake word: < 150 ms
- Intent recognition: < 300 ms
- Local command execution: < 500 ms
- Speech response start: < 1 second
- BLE reconnect: < 3 seconds
- Idle battery drain: < 1.5% per hour
- Foreground service RAM: < 200 MB (without loaded LLM)

These targets should be treated as regression gates in CI, not "nice to have" goals.

---

## 43. Coding Standards

- Kotlin only for production code.
- Prefer interfaces over inheritance.
- Constructor injection everywhere.
- No global mutable state.
- No singleton objects except DI-managed services.
- All public APIs documented with KDoc.
- Every suspend function must support cancellation.
- Avoid reflection unless unavoidable.
- No business logic inside Activities, Fragments, or Compose UI.

---

## 44. Repository Strategy

A monorepo is the best fit. Keep architecture decision records (ADRs) under `/docs/adr/` so major design choices are documented and future contributors understand why something exists, not just how it works.

---

## 45. CI/CD

Minimum pipeline:
1. Static analysis (Detekt, ktlint)
2. Unit tests
3. Instrumentation tests
4. Build all modules
5. API compatibility check
6. Generate documentation
7. Produce signed debug artifact

Every pull request should run the full pipeline before review.

---

## 46. Long-Term Vision

Halo should evolve into a **Wearable Intelligence Platform**, not merely an application.

The architecture should make it possible to add:
- New wearable drivers without touching the Assistant Core.
- New AI providers without changing workflows.
- New plugins without recompiling the app.
- Future AR rendering modules alongside today's audio-only devices.
- Additional host platforms (Desktop, Android XR, Automotive) with only adapter-layer changes.

If the architecture is successful, replacing an AI model, adding a new pair of glasses, or introducing a completely different interaction modality should require implementing interfaces, not rewriting the core. That is the hallmark of a platform designed to survive the next decade rather than the next product cycle.
