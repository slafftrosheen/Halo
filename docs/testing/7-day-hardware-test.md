> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# 7-Day Real-Hardware Test Protocol — HeyCyan Glasses

## Overview
This document outlines the test procedure for the 7-day hardware validation period of the **Halo Ecosystem Release Candidate (v1.0.0-RC1)** with physical HeyCyan BW-AG1 smart glasses.

---

## Operating Rules
> [!IMPORTANT]
> **Test Week Freeze Policy**
> - **No Feature Additions**: Do not introduce new features, AI providers, or speculative device commands during the 7-day hardware test window.
> - **Targeted Fixes Only**: Only resolve P0 (crashes, safety, data loss) and P1 (core path blockers) issues.
> - **Backlog Requests**: All UI polish requests or feature ideas must be logged to the backlog for post-test batches.

---

## Test Categories & Daily Agenda

### 1. DAY-TO-DAY (Daily Workflows)
- **Morning Startup**: Power on glasses, open Halo app, confirm auto-connect and transition to `READY`.
- **Speech Output Routing Invariant**: A voice turn initiated on a wearable returns speech to that wearable unless the user has explicitly enabled another fallback policy.
- **Normal Usage**: Trigger photo capture, video recording start/stop, and audio recording.
- **Provider Selection**: Exercise the real provider set — on-device LiteRT-LM (default), OpenRouter, and Ollama — and confirm the selected provider/model tuple is the one that runs. Android Assistant is a *handoff* target, not an inference provider; it is not selectable as an AI fallback.
- **LiteRT Development Model Matrix**: for each installed artifact (`qwen3-0.6b-dynamic-int4`,
  `qwen3-1.7b-dynamic-int4`, `qwen2.5-1.5b-instruct-q8`, `deepseek-r1-distill-qwen-1.5b-q8`) record
  download, hash verify, select, AUTO load, smoke generation #1, generation #2 (no restart), unload,
  reload and generation #3 — then repeat where feasible for explicit GPU and explicit CPU, recording
  differences separately. Afterwards switch models sequentially without restarting Halo and run
  `speech -> Whisper -> selected LiteRT -> TTS` at least twice. Classify each result as CATALOGUED,
  DOWNLOAD VERIFIED, ENGINE VERIFIED, GENERATION VERIFIED or PHYSICAL DEVICE VERIFIED — never
  conflate them. Checklist: `docs/08-providers/LITERT-DEVELOPMENT-MATRIX.md`.
- **Explicit Selection Is Sticky**: With a provider explicitly selected, force a failure (bad key, cooldown, unreachable endpoint) and confirm Halo still reports that provider as selected. No silent substitution is acceptable.
- **Recharging Lifecycle**: Place glasses on charger, observe `BatteryUpdated` and `charging` state, remove from charger and verify live battery telemetry updates.
- **Phone Lock/Unlock**: Lock phone for >30 minutes, unlock phone, verify BLE session auto-reconnect backoff and state recovery.

### 2. STRESS TESTING
- **Repeated Reconnect**: Disconnect/connect 10 consecutive times.
- **Rapid Photo Intent Execution**: Trigger Photo capture repeatedly while previous captures complete.
- **Provider Outages**: Simulate provider loss (turn off Wi-Fi/Ollama or invalidate the API key). Verify Halo reports the failure truthfully, keeps the user's provider selected, and does **not** silently reroute to another provider or to Android Assistant. Provider failure is not authorization to change the privacy boundary.
- **Rate-Limit Semantics**: Against a mock endpoint returning `429` with `Retry-After`, verify the reported wait matches the header, repeated attempts during the cooldown do not hammer the endpoint, and the provider recovers after the window without an app restart.
- **AI Thumbnail Fast Path**: Trigger the glasses AI thumbnail capture 3–5 times and record `command -> ready`, `ready -> final byte`, dimensions and byte size. Report median/min/max; a single run is not a throughput measurement.
- **Bluetooth Toggling**: Disable phone Bluetooth while `READY`, re-enable Bluetooth, verify bounded backoff reconnect without ghost session buildup.

### 3. FAILURE RECOVERY
- **Out of Range / Power Loss**: Turn off glasses while recording; verify state degrades to `DISCONNECTED` with last-known telemetry displayed.
- **Memory Insufficient**: Trigger device memory warnings (Action 0x73 Subtype 0x0E); verify warning banner renders on Devices screen without app crash.
- **Device Unbind**: Simulate device unbind notification (Subtype 0x0D); verify session invalidation and re-pair requirement.

### 4. TELEMETRY & OBSERVATION AUDIT
- **Battery Telemetry Accuracy**: Verify percentage matches physical device state; verify stale timestamp badge ("Last reported X ago") appears if disconnected >5 minutes.
- **Notification Matrix**: Verify `AiImageRecognition`, `MicrophoneActive`, and `VoicePlaybackPause` events trigger timeline entries in Activity.

### 5. UI & UX ACCESSIBILITY
- **Screen Recreation**: Rotate screen, background app, return to foreground; confirm no duplicate coroutine collectors or UI state corruption.
- **No Phone Fallback**: For a wearable-origin turn, confirm the answer is spoken from the glasses and the phone stays silent. If the wearable route cannot be proven, the visual answer remains and TTS fails or pends explicitly.
- **False Barge-In / Echo**: While Halo is speaking, keep quiet and confirm no new turn starts, no photo is captured and no inference runs. Then activate the glasses' voice control mid-answer and confirm the old output stops before new capture begins and a new interaction id is created.
- **Preload Latency**: Measure interaction start -> first token and interaction start -> first audible answer with and without early model preload. Record the numbers rather than assuming preload helps.
- **Interactive Control Verification**: Ensure every button and card on `HeyCyanControlScreen`, `DevicesScreen`, and `IntelligenceScreen` provides functional feedback or explicit state indications.

---

## Issue Severity Definitions

| Severity | Definition | Target Resolution |
| :--- | :--- | :--- |
| **P0 (Critical)** | Application crash, BLE stack lockup, data loss, safety regression | Immediate blocker fix before RC2 |
| **P1 (High)** | Core workflow unusable (e.g. Photo capture fails permanently, auto-connect loops indefinitely) | Fix within 24 hours |
| **P2 (Medium)** | Non-blocking feature failure, provider recovery delay, minor state sync lag | Schedule for targeted fix batch |
| **P3 (Low)** | Cosmetic visual artifact, minor typography misalignment | Log to backlog |

---

## Bug Report Format

When logging an issue during the 7-day test, format entries as follows:

```markdown
### [P0/P1/P2/P3] Short Issue Description

- **Date / Time**: 2026-08-XX HH:MM
- **Build Version**: 1.0.0-RC1 (Build 104)
- **Environment**: Android 14 / HeyCyan BW-AG1 Pro
- **Action Performed**: [Exact steps leading to issue]
- **Expected Outcome**: [What should have happened]
- **Actual Outcome**: [What actually occurred]
- **Diagnostic ID / Log snippet**: [Attach DiagnosticsExport text or Logcat snippet]
```
