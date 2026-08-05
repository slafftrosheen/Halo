# Architecture Specification v3

## Internal Engine Design & Component Contracts

---

## 1. Fundamental Rule

Everything in Halo is an **Engine**. Not managers, not helpers, not utilities. Engines.
Each engine owns exactly one responsibility.

Example:
- **Voice Engine** does voice.
- **Memory Engine** does memory.
- **Plugin Engine** does plugins.
Nothing else.

---

## 2. Engine Hierarchy

```text
Halo Runtime
├── Device Engine
├── Voice Engine
├── Audio Engine
├── Context Engine
├── Intent Engine
├── Workflow Engine
├── Automation Engine
├── Provider Engine
├── Memory Engine
├── Plugin Engine
├── Notification Engine
├── Privacy Engine
├── Diagnostics Engine
└── Update Engine
```

No engine knows implementation details of another. Communication occurs only through Events and Commands.

---

## 3. Runtime

The Runtime is effectively the kernel.
Responsibilities:
- start engines
- dependency graph
- lifecycle
- shutdown
- restart
- health monitoring

Pseudo sequence:
```text
Boot -> Load Configuration -> Initialize Engines -> Initialize Drivers -> Load Plugins -> Ready
```

---

## 4. Engine Lifecycle

Every engine implements:

```kotlin
interface Engine {
    suspend fun initialize()
    suspend fun start()
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    suspend fun destroy()
}
```

No exceptions.

---

## 5. Three Bus Architecture

Halo has three buses. Never mix them.

1. **Event Bus**: Something happened (e.g., `WakeWordDetected`).
2. **Command Bus**: Do something (e.g., `StoreMemoryCommand`).
3. **Query Bus**: Retrieve something (e.g., `FindMemory`).

Benefits:
- logging
- replay
- testing
- metrics
- undo
- future distributed execution

**Event Naming**: Always past tense (`MemoryStored`, `AudioStarted`).
**Commands**: Describe intent, return success/failure (`StoreMemory`, `StartRecording`).
**Queries**: Read only, never modify data (`CurrentContext`, `BatteryLevel`).

---

## 6. Domain Model

Everything revolves around six entities:
`User`, `Device`, `Conversation`, `Memory`, `Plugin`, `Provider`.
Everything else is metadata.

- **Device Object**: Never contains SDK objects.
- **Provider Object**: Self-describes capabilities (latency, context window, vision, cost, privacy). No hardcoded checks.
- **Capability Flags**: e.g., `supportsStreaming`, `supportsVision`.

---

## 7. Context & Cognitive State

- **Voice Session**: State object (`Listening`, `Recognizing`, `Processing`, `Generating`, `Speaking`, `Finished`).
- **Context Snapshot**: Immutable object generated every second or on event (Time, Location, Movement, Battery, current App, Notifications).
- **Attention Score**: 0-100 calculated per second based on activity (Walking, Driving, Media). Influences verbosity.
- **Cognitive Load**: Low, Medium, High. Assistant adapts response length based on user's current activity (e.g., Driving -> High).

---

## 8. Intent & Workflows

- **Intent Object**: Strongly typed (Type, Confidence, Entities, Priority, Source).
- **Entity Extraction**: Parsing out `Action`, `Person`, `Date`, etc. without requiring an LLM for simple commands.
- **Workflow Graph**: Represent interactions as nodes (`Capture -> Recognize -> Intent -> Plugin -> Provider -> Response -> Speak`). Each node is measurable.

---

## 9. Isolation & Sandboxing

- **Audio Buffers**: Separate buffers for input, recognition, playback, streaming.
- **Driver Isolation**: SDK crashes never crash Halo. Exceptions converted, timeouts enforced.
- **Plugin Sandbox**: Plugins receive Context, Commands, Events, Queries. No direct Android APIs or database access.
- **Provider Sandbox**: Providers receive Prompt, Options, Attachments. Return Result. No access to Android or Drivers.
- **Privacy Firewall**: Every outgoing request passes through one gate: `Request -> Permission -> Consent -> Log -> Send`.

---

## 10. Boot & Shutdown Sequences

**Boot**:
1. Verify configuration.
2. Initialize logging.
3. Initialize database.
4. Start Event, Command, and Query buses.
5. Load engines.
6. Detect devices.
7. Load providers.
8. Load plugins.
9. Restore scheduled jobs.
10. Enter Ready state.

**Shutdown**:
1. Stop accepting new commands.
2. Finish active workflows.
3. Flush logs.
4. Persist runtime state.
5. Disconnect drivers.
6. Stop engines in reverse dependency order.

---

## 11. The North Star: Microkernel OS

Think of Halo as a tiny operating system with services.

```text
                 HALO RUNTIME
                        │
 ┌──────────────────────┼────────────────────────┐
 │                      │                        │
Device Service     Audio Service         Context Service
 │                      │                        │
 ├──────┐         ┌─────┴─────┐          ┌───────┴────────┐
 │      │         │           │          │                │
HeyCyan Meta    Whisper    Piper      Memory      Notifications
 │
 └──────────────────────────────────────────────────────────────┐
                                                                │
                     Assistant Service                          │
                              │                                 │
                     Intent Service                             │
                              │                                 │
                     Workflow Service                           │
                              │                                 │
                 Provider Abstraction                           │
            ┌──────────┬──────────┬──────────┐                  │
            │          │          │          │                  │
        llama.cpp   Ollama    Gemini     OpenAI           Future...
```

The Assistant is just another service. In the future, multiple assistants (Notification, Coding, Navigation) could run simultaneously, sharing the same Runtime, Context, Memory, Audio, and Device services. Supporting new hardware becomes writing a driver; supporting new AI models becomes writing a provider.
