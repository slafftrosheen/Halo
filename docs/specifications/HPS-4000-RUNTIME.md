---
title: HPS-4000-RUNTIME
status: Draft
maturity: Alpha
---

> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# HPS-400: HAL Runtime Specification (HRS)

## Runtime Responsibilities
The Runtime owns absolutely everything. Nothing exists outside Runtime.
It behaves like a kernel: it knows interfaces (SpeechProvider, DeviceDriver, LLMProvider, Plugin, Storage), but never specific implementations.

## Runtime Tick
Everything updates through deterministic ticks (e.g., 100 Hz):
`Scheduler -> Update Context -> Dispatch Events -> Run Automations -> Cleanup`

## Runtime Services
The 12 permanent services:
1. **Device Service**: Pairing, reconnect, capability discovery.
2. **Audio Service**: Microphone, playback, routing, buffering.
3. **Speech Service**: STT, wake word.
4. **Provider Service**: AI requests.
5. **Memory Service**: Storage, retrieval.
6. **Workflow Service**: Running workflows.
7. **Plugin Service**: Loads plugins, sandbox, permissions.
8. **Context Service**: Produces ContextSnapshot.
9. **Automation Service**: Runs automations.
10. **Notification Service**: Interacts with Android.
11. **Diagnostics Service**: Measurables and profiling.
12. **Privacy Service**: Intercepts everything.

## Runtime Priorities
1. Realtime
2. Voice
3. Connection
4. Notifications
5. Automation
6. Background
7. Cleanup
