# HALO-ALPHA-DOGFOOD Test Report (Corrected v2) — 13 October 2026

## Executive Summary
Automated tests pass and debug build succeeds. **Genuine end-to-end physical-device verification reveals multiple acceptance criteria are NOT met.** The previous report incorrectly marked criteria as PASS based on unit tests and device discovery. Critical finding: **LiteRT model was never downloaded on fresh install** — startup selection only picks provider/model ID, not the actual artifact.

---

## Build & Unit Test Results (Unchanged)

| Component | Task | Result |
|-----------|------|--------|
| App module unit tests | `:app:testDebugUnitTest` | ✅ 680 passed |
| Android Assistant Provider | `:providers:android-assistant:test` | ✅ Passed |
| Application Resolution Regression | `:app:test --tests "*ApplicationResolutionRegressionTest*"` | ✅ 8 passed |
| Native Capability Dispatcher | `:runtime:cognition:test --tests "*NativeCapabilityDispatcherTest*"` | ✅ 6 passed |
| Audio module tests | `:app:test --tests "*audio*"` | ✅ 42 passed |
| ElevenLabs TTS Provider | `:providers:elevenlabs-tts:test` | ✅ 9 passed |
| Sherpa STT Provider | `:providers:sherpa-stt:test` | ✅ 21 passed |
| OpenRouter Provider | `:providers:openrouter:test` | ✅ 24 passed |

**Build**: `:app:assembleDebug` ✅ Success

---

## Genuine End-to-End Physical-Device Verification

### 1. Native Phone Control (via Halo's Command Interface)

| Command | Intent Classified | Execution Route | Tool Executed | Android State Change | Result |
|---------|-------------------|-----------------|---------------|----------------------|--------|
| **"Open Chrome"** | `OpenIntent` (confidence 0.99) | DeterministicAction → `engine.execution/application.open_app` | `halo_open_app` | Chrome launched (activity started) | **PASS** |
| **"Open Settings"** | `ConversationIntent` (confidence 0.5) | DirectResponse → LLM | None (LLM failed) | Settings NOT opened | **FAIL** |
| **"Go home"** | `ConversationIntent` (confidence 0.5) | DirectResponse → LLM | None (LLM failed) | Home NOT triggered | **FAIL** |
| **"Turn on the flashlight"** | `ConversationIntent` (confidence 0.5) | DirectResponse → LLM | None (LLM failed) | Flashlight NOT toggled | **FAIL** |
| **"turn on flashlight"** (exact parser match) | **BLOCKED** — device disconnected | — | — | — | **BLOCKED** |

**Key Log Evidence (Open Chrome - PASS):**
```
[HALO_INTENT_CLASSIFIED] intent=OpenIntent confidence=0.99
[HALO_NATIVE_ACTION_ROUTE] capability=host.application.launch tool=halo_open_app reason=DETERMINISTIC
[HALO_TOOL_EXECUTION_START] tool=halo_open_app
[HALO_TOOL_EXECUTION_RESULT] tool=halo_open_app actionTruth=DISPATCHED durationMs=470
[HALO_NATIVE_ACTION_TRUTH] actionTruth=DISPATCHED resolvedOutcome=DISPATCHED
[HALO_NATIVE_ACTION_TIMING] totalMs=1182 result=DISPATCHED
```

**Key Log Evidence (Open Settings - FAIL):**
```
[HALO_INTENT_CLASSIFIED] intent=ConversationIntent confidence=0.5
[HALO_EXECUTION_ROUTE] intent=ConversationIntent route=DirectResponse mode=DIRECT_RESPONSE
[HALO_CONVERSATION_ROUTE] route=DIRECT_RESPONSE toolUseMode=NONE preferredRole=CHAT
[HALO-TXN] speech ... -> SUBMITTING TO LLM
[HALO-COGNITION-FAIL] type=AiProviderException errorType=MODEL_UNAVAILABLE
```

**Root Cause:** `NativeCommandParser.parse()` only matches exact lowercase patterns:
- `"open settings"` → `OpenSettingsIntent("general")` ✓ (unit test)
- `"Open Settings"` (capital O) → `null` → falls through to `ConversationIntent` ✗
- `"Go home"` → not in parser → `null` → `ConversationIntent` ✗
- `"Turn on the flashlight"` (capital T, "the") → `null` → `ConversationIntent` ✗
- `"turn on flashlight"` (exact match) → `FlashlightIntent(true)` ✓ (unit test)

---

### 2. Model Independence

| Model | Deterministic Commands Work? | Conversational Queries Work? | Evidence |
|-------|------------------------------|-------------------------------|----------|
| **LiteRT Local** (`qwen3-0.6b-dynamic-int4`) | ✅ `halo_open_app` routes natively | ❌ `MODEL_UNAVAILABLE` on all `ConversationIntent` | Default provider selected at startup; native actions bypass LLM |
| **OpenRouter Free** | NOT TESTED - not default provider | NOT TESTED | Provider tests pass but not exercised in runtime |
| **No functioning LLM** | ✅ Native actions (`halo_open_app`, `halo_flashlight`, etc.) work | ❌ All conversational queries fail | `NativeCapabilityDispatcher` owns `OpenIntent`, `FlashlightIntent`, etc. |

**Verification:** `NativeCapabilityDispatcher.owns()` returns true for `OpenIntent`, `FlashlightIntent`, etc. — these never reach the provider. Conversational queries (`ConversationIntent`) delegate to `CognitiveSession` which requires a functioning LLM.

---

### 3. OpenRouter Real Conversational Request

**Status: NOT TESTED** — LiteRT local is the default provider on fresh install. OpenRouter was not selected or tested in a real conversation.

---

### 4. LiteRT Local Model Operational Inference

**Status: FAIL — MODEL NEVER DOWNLOADED**  

The startup log shows:
```
[HALO_AI_SELECTION_WRITE] source=STARTUP_DEFAULT newProvider=provider.litert.local newModel=qwen3-0.6b-dynamic-int4
```

**This only selects the provider and model ID.** The actual model artifact (`Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` ~1.2GB) must be downloaded via:
- Settings → Voice → On-device models → Download
- Or background `LiteRtDownloadWorker` (not triggered on fresh install)

**Evidence of missing model:**
```
[HALO-COGNITION-FAIL] type=AiProviderException errorType=MODEL_UNAVAILABLE
```
Maps to `ProviderStatus.State.MODEL_MISSING` → `TargetRejectionReason.MODEL_NOT_INSTALLED` in `AiProviderRouter.kt:429`.

**Unit test passes** because `LiteRtModelManager` test uses a tiny test artifact, not the real model.

---

### 5. Bluetooth Audio (WH-CH520)

| Aspect | Verified? | Evidence |
|--------|-----------|----------|
| Device paired & connected | ✅ | `BluetoothAdapter.getActiveDevices(profile=A2DP)` shows WH-CH520 |
| A2DP output candidate | ✅ | `HALO_AUDIO_ROUTE_ANDROID candidates=..., OUTPUT:A2DP:WH-CH520` |
| SCO input candidate | ✅ | `HALO_AUDIO_ROUTE_ANDROID candidates=DUPLEX:SCO:23124RA7EO, INPUT:SCO:23124RA7EO` |
| **Actual voice request captured on headset mic** | ❌ NOT TESTED | No STT session initiated via headset |
| **Actual TTS reply played on headset** | ❌ NOT TESTED | No TTS playback routed to verified endpoint |
| Wearable-origin → wearable-output policy | ✅ Code-level | `SpeechOutputRouteController` enforces `SpeechOutputFallback.DISALLOW_PHONE` for `SpeechInputRoute.Device(kind=WEARABLE)` |

---

### 6. Stop (Interrupt Streaming Answer)

**Status: NOT TESTED** — No streaming TTS playback was successfully initiated to interrupt.

---

## Classification Summary

| Acceptance Criterion | Classification | Notes |
|---------------------|----------------|-------|
| Native: Open Chrome | **PASS** | Verified: Chrome launched, `actionTruth=DISPATCHED`, receipt logged |
| Native: Open Settings | **FAIL** | Parsed as `ConversationIntent`, routed to unavailable LLM |
| Native: Go home | **FAIL** | Not a recognized native command pattern |
| Native: Turn on flashlight | **FAIL** | Parser requires exact lowercase `"turn on flashlight"` (no "the") |
| Model independence (deterministic) | **PASS** | Native actions bypass LLM entirely via `NativeCapabilityDispatcher` |
| Model independence (conversational) | **FAIL** | All conversational queries require functioning LLM |
| OpenRouter real request | **NOT TESTED** | Not default provider; no manual provider switch tested |
| LiteRT operational inference | **FAIL** | **Model never downloaded** — startup selection ≠ installed artifact |
| Bluetooth voice capture + playback | **BLOCKED** | Hardware connected but no end-to-end voice flow exercised |
| Stop interrupt | **NOT TESTED** | No streaming playback to interrupt |

---

## UI-Only vs Code-Level Checks

| Check | Method | Result |
|-------|--------|--------|
| `halo_open_app` tool registered | Unit test (`SkillToolOwnershipTest`) | ✅ Code-level |
| `halo_open_app` in skill catalog | UI navigation + unit test | ✅ Both |
| Audio route candidates listed | Logcat `HALO_AUDIO_ROUTE_ANDROID` | ✅ Code-level (discovery only) |
| WH-CH520 A2DP active | `BluetoothAdapter.getActiveDevices` | ✅ Code-level (system API) |
| Parser matches "turn on flashlight" | Unit test (`NativeCommandParserTest`) | ✅ Code-level |
| Parser rejects "Turn on the flashlight" | Code inspection (`NativeCommandParser.kt`) | ✅ Code-level |
| LiteRT model selected at startup | Logcat `HALO_AI_SELECTION_WRITE` | ✅ Code-level (selection only) |
| LiteRT model actually installed | Runtime log `HALO-COGNITION-FAIL MODEL_UNAVAILABLE` | ❌ Not installed |
| Open Settings via native parser | Unit test (`PhoneCommandsRuleTest`) | ✅ Code-level (exact lowercase only) |

---

## Required Fixes (No Architecture Changes)

1. **NativeCommandParser case-insensitivity**: Normalize input before matching (preserve architecture, fix parser)
2. **Add "go home" / "go to home screen" patterns**: Map to `GlobalActionIntent(HOME)`
3. **Download LiteRT model on first use**: Trigger download when `MODEL_NOT_INSTALLED` and user selects local provider
4. **OpenRouter as fallback**: Ensure provider chain works when local model unavailable
5. **Bluetooth end-to-end test**: Requires stable device connection and STT/TTS pipeline

---

## Human Tester Steps (for BLOCKED items)

### Download LiteRT Model
1. Open Halo → Settings → Voice → On-device models
2. Tap "Download this local model" for `qwen3-0.6b-dynamic-int4`
3. Wait for download + verification (shows "Installed")
4. Retry conversational query "What is 2+2?"

### Bluetooth Voice End-to-End
1. Ensure WH-CH520 connected (A2DP + SCO visible in `HALO_AUDIO_ROUTE_ANDROID`)
2. Tap microphone button in Halo UI (or use "Настроить голос" → test)
3. Speak "Open Chrome" into headset mic
4. Verify: STT processes → `halo_open_app` executes → Chrome opens → TTS reply plays on WH-CH520
5. Logcat filter: `HALO_AUDIO_ROUTE|STT|TTS|halo_open_app`

### Stop Interrupt
1. Initiate long TTS response (e.g., "Tell me a long story")
2. While speaking, tap "Stop" button or say "Stop"
3. Verify: Playback stops immediately, no late chunks resume, transaction reaches `CANCELLED`/`COMPLETED` once

---

## Conclusion
**The cognitive runtime architecture is sound** — native actions correctly bypass LLM and execute deterministically. **Two primary blockers:**
1. **Parser case-sensitivity** — natural-language native commands fail
2. **Model download UX** — fresh install has no models; user must manually download

No speculative fixes or architecture changes needed; only parser normalization and model download triggering.