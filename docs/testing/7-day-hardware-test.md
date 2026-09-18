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
- **Normal Usage**: Trigger photo capture, video recording start/stop, and audio recording.
- **Provider Switching**: Toggle between Ollama, OpenRouter, and System Assistant.
- **Recharging Lifecycle**: Place glasses on charger, observe `BatteryUpdated` and `charging` state, remove from charger and verify live battery telemetry updates.
- **Phone Lock/Unlock**: Lock phone for >30 minutes, unlock phone, verify BLE session auto-reconnect backoff and state recovery.

### 2. STRESS TESTING
- **Repeated Reconnect**: Disconnect/connect 10 consecutive times.
- **Rapid Photo Intent Execution**: Trigger Photo capture repeatedly while previous captures complete.
- **Provider Outages**: Simulate provider loss (turn off Wi-Fi/Ollama), verify runtime fallback to System Assistant.
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
- **Interactive Control Verification**: Ensure every button and card on `HeyCyanControlScreen`, `DevicesScreen`, and `IntelligenceScreen` provides functional feedback or explicit state indications.

---

## Issue Severity Definitions

| Severity | Definition | Target Resolution |
| :--- | :--- | :--- |
| **P0 (Critical)** | Application crash, BLE stack lockup, data loss, safety regression | Immediate blocker fix before RC2 |
| **P1 (High)** | Core workflow unusable (e.g. Photo capture fails permanently, auto-connect loops indefinitely) | Fix within 24 hours |
| **P2 (Medium)** | Non-blocking feature failure, provider fallback delay, minor state sync lag | Schedule for targeted fix batch |
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
