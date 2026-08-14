# Changelog — Halo Ecosystem

## [1.0.0-RC1] - 2026-08-14

### Release Candidate Highlights
- **HeyCyan Smart Glasses Embodiment**: Full native protocol integration for HeyCyan BW-AG1 smart glasses. Supports real hardware discovery, GATT transport, semantic state management (`READY`, `CAPTURING`, `RECORDING`), and photo/video execution.
- **Photo Golden Path**: 4-state photo execution flow (`READY` → `CAPTURING` → `COMPLETED` / `FAILED`) with 5s hardware acknowledgement timeout and double-click locking during capture.
- **Video State Machine**: Semantic state machine (`IDLE`, `STARTING`, `RECORDING`, `STOPPING`) guarding video workflows against invalid duplicate dispatches.
- **Notification Observation Pipeline**: Proprietary `0x73` payload decoder projecting AI Image Recognition, Voice Activation (Microphone Active/Inactive), Voice Playback Pause, Memory Fault warnings, and Battery telemetry into `ObservationUpdated` timeline events over the `EventBus`.
- **Battery Telemetry Age & Stale Indicators**: Live battery telemetry displaying level % and timestamped age ("Updated 2m ago" vs "Last known battery"). Stale indicators render for telemetry older than 5 minutes.
- **Provider Setup UX & Fallback**: Standardized AI provider status model (`NOT_CONFIGURED`, `CONFIGURING`, `CHECKING`, `READY`, `UNAVAILABLE`, `ERROR`) across Ollama, Llama.cpp, OpenRouter, and System Assistant. Password masking enabled for API key input.
- **Crash Resilience & Security**: Out-of-bounds frame protection on BLE byte parsing, session invalidation on device unbind, and zero plain secret logging.
- **Diagnostics Export**: Exportable flat text diagnostic report (`halo_diagnostics_*.txt`) for TestFlight participants.
