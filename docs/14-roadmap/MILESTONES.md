> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# Development Roadmap

Halo is built in milestones, establishing foundation before intelligence.

## Milestone 1: The Runtime
* Runtime and Engine architecture.
* Driver abstraction (HeyCyan & Bluetooth).
* Basic audio routing.
* *No AI*. Just solid device connection.

## Milestone 2: Voice Pipeline
* Speech Recognition (Whisper.cpp / Android Speech).
* Wake word detection (Porcupine / OpenWakeWord).
* Text-to-Speech (Piper / Android TTS).

## Milestone 3: Local Intelligence
* Local `llama.cpp` integration.
* Simple question answering and text generation completely offline.

## Milestone 4: Memory
* SQLite (Room) Database integration.
* Short, Medium, and Long-term memory storage and retrieval.

## Milestone 5: Plugins
* Plugin SDK and dynamic loading.
* Calendar, Notes, and basic system control plugins.

## Milestone 6: Notifications
* Android Notification interception.
* Summarization and delivery via wearable.

## Milestone 7: Automation
* Rules-based scheduling (IF -> THEN).
* Context-aware triggers (Location, Time, Bluetooth).

## Milestone 8: Vision
* Camera integration via glasses.
* Vision models (local or cloud) for object recognition.

## Milestone 9: AR Integration
* AR rendering modules.
* Spatial anchors and visual overlays.
