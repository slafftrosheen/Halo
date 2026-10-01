# DOGFOOD-001 — Integration Truth + First-Run Reliability

**Status:** IMPLEMENTED & COMPILED / UNIT VERIFIED  
**Baseline SHA:** `32d20ca1cd8f7e447983eec9740f20be6aa92ebc`  
**Date:** 2026-09-30

---

## Overview & Objectives

DOGFOOD-001 is the first Halo integration stabilization batch focused on telemetry truth, error visibility, direct-chat transient failure retry, LiteRT main-thread StrictMode elimination, speech rejection observability, user feedback, build provenance, and diagnostic prefix cleanup.

The goal of this batch is simple:
**A Halo interaction must either work or tell us exactly why it did not work.**

---

## Summary of Changes

### 1. P0 — Provider Failure Truth
- **Files Modified:**
  - `sdk/provider-sdk/src/main/java/com/halo/sdk/provider/AiProviderException.kt`
  - `providers/openrouter/src/main/java/com/halo/providers/openrouter/OpenRouterProvider.kt`
  - `runtime/cognition/src/main/java/com/halo/runtime/cognition/session/CognitiveSession.kt`
  - `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/ProviderTurnExecutor.kt`
  - `app/src/main/java/com/halo/app/ai/ProviderControlRepository.kt`
  - `app/src/main/java/com/halo/app/ui/screens/settings/ProviderDiagnosticsPanel.kt`
- **Behavior:**
  - Added `sanitizedSummary(requestedModel)` to `AiProviderException` to build clear, credential-safe failure descriptions.
  - Expanded `[HALO-COGNITION-FAIL]` in `CognitiveSession.kt` to record normalized semantic failure fields: `errorType`, `httpStatus`, `providerCode`, `recoverability`.
  - Stored `lastFailure` and `lastFailedModel` in `OpenRouterProvider` and exposed sanitized error descriptions on `ProviderStatus` for Provider Diagnostics / System Check.
  - Emitted `HALO_PROVIDER_FAILURE` with normalized error fields (`errorType`, `httpStatus`, `providerCode`, `recoverability`, `retryAfterMs`, `attempt`, `partialOutput`).

### 2. P0 — Safe Direct-Chat Retry
- **Files Modified:**
  - `runtime/cognition/src/main/java/com/halo/runtime/cognition/turn/ProviderTurnExecutor.kt`
  - `runtime/cognition/src/test/java/com/halo/runtime/cognition/turn/ProviderTurnExecutorTest.kt`
- **Behavior:**
  - Refactored `DefaultProviderTurnExecutor` into `executeWithRetry` -> `executeAttempt` -> `maybeRetrySameTarget`.
  - Implemented exactly one bounded retry (maximum 2 total attempts) for `DIRECT_RESPONSE` (`ToolUseMode.NONE` + `INITIAL` turn + `RETRY_IMMEDIATELY` + no partial text/tool output).
  - Enforced zero automatic retries for `AUTHENTICATION_FAILED`, `RATE_LIMITED` with Retry-After, `INVALID_REQUEST`, `CONTEXT_TOO_LARGE`, `UNSUPPORTED_CAPABILITY`, partial output failures, `TOOL_CONTINUATION`, agentic turns, or deterministic actions.
  - Added telemetry events: `HALO_PROVIDER_ATTEMPT`, `HALO_PROVIDER_RETRY`, `HALO_PROVIDER_SUCCESS`, `HALO_PROVIDER_FAILURE`.

### 3. P0 — Fix LiteRT StrictMode Violation
- **Files Modified:**
  - `providers/litert/src/main/java/com/halo/providers/litert/models/LiteRtModelManager.kt`
  - `app/src/main/java/com/halo/app/ui/screens/settings/IntelligenceViewModel.kt`
  - `app/src/main/java/com/halo/app/ui/screens/settings/OnDeviceModelsScreen.kt`
- **Behavior:**
  - Removed `viewModel.storagePreflight(model)` call from Compose rendering / `remember(model.id)` in `OnDeviceModelsScreen.kt`.
  - Moved preflight computation to `IntelligenceViewModel` on `Dispatchers.IO`. Available disk space is evaluated once per state update via `StatFs` and derived model preflights are exposed as `storagePreflightLines` in `IntelligenceUiState`.
  - Opening the "On-device models" screen now produces ZERO disk reads or `StatFs` calls on the main UI thread.

### 4. P0 — Fix Misleading LiteRT Telemetry
- **Files Modified:**
  - `providers/litert/src/main/java/com/halo/providers/litert/LiteRtLocalProvider.kt`
- **Behavior:**
  - Changed developer backend override logging in `setDeveloperBackend` from `HALO_LITERT_SELECT` to `HALO_LITERT_BACKEND_OVERRIDE`.
  - Reserved `HALO_LITERT_SELECT` strictly for actual active model/target selection.

### 5. P1 — Speech Rejection Observability & User Feedback
- **Files Modified:**
  - `engines/speech/src/main/java/com/halo/engines/speech/SpeechTranscriptGate.kt`
  - `runtime/speech/src/main/java/com/halo/runtime/speech/SpeechPerceptionBridge.kt`
  - `app/src/main/java/com/halo/app/ui/screens/home/HomeViewModel.kt`
  - `app/src/main/java/com/halo/app/ui/screens/home/HomeScreen.kt`
  - `engines/speech/src/test/java/com/halo/engines/speech/SpeechTranscriptGateTest.kt`
- **Behavior:**
  - Extended `RepetitionAnalysis` and `SpeechGateResult.Rejected` to carry structural repetition metrics: `tokenCount`, `normalizedChars`, `uniqueTokenRatio`, `dominantPhraseRatio`, `repeatedRuns`, `repetitionSource` (`CLAUSE`, `CHAR_NGRAM`, `BOTH`).
  - Hardened `analyzeCharacterNgrams()` / `analyzeRepetition()`: natural short human repetitions ("no no no", "yes yes yes", "very very good", "wait wait wait", "thank you thank you") are accepted while synthetic loops are rejected.
  - Logged non-content repetition diagnostics in `SpeechPerceptionBridge.kt` when rejected (no transcript text or prefixes logged).
  - Wired `SpeechRejected` event handling in `HomeViewModel` and rendered a lightweight, non-modal user-visible card ("Didn't use that speech input · Reason: ...") in `HomeScreen` that ages out automatically after 5 seconds.

### 6. P1 — Build Provenance in Every Trace
- **Files Modified:**
  - `app/build.gradle.kts`
  - `app/src/main/java/com/halo/app/HaloApplication.kt`
  - `app/src/main/java/com/halo/app/diagnostics/DiagnosticsExport.kt`
- **Behavior:**
  - Populated `GIT_COMMIT` and `GIT_DIRTY` `BuildConfig` fields via Gradle using `git rev-parse --short HEAD` and `git status --porcelain`. Falls back safely to `"unknown"` and `false` if `.git` is absent.
  - Emitted `[HALO_BUILD]` log at application startup (`onCreate` in `HaloApplication.kt`).
  - Included build identity (versionName, versionCode, variant, commit, dirty) in `DiagnosticsExport.kt`.

### 7. P1 — Clean Transaction Log Names
- **Files Modified:**
  - `libraries/logging/src/main/java/com/halo/libraries/logging/ConsoleLogger.kt`
- **Behavior:**
  - Updated `ConsoleLogger.formatTag` to check if a category already starts with `HALO-` or `HALO_`, avoiding `[HALO-HALO-TXN]` duplicate prefixes in logcat.

---

## Verification & Build Results

### Gradle Build
- Executed: `./gradlew :app:assembleDebug`
- Status: **SUCCESS**

### Unit Tests
- Executed unit tests for `:runtime:cognition`, `:engines:speech`, `:providers:openrouter`, `:providers:litert`:
  - `ProviderTurnExecutorTest`: Verified single-retry for direct chat, non-retry for partial output/agentic turns/auth errors, and attempt telemetry.
  - `SpeechTranscriptGateTest`: Verified acceptance of short natural human repetitions ("no no no", "yes yes yes", etc.) and rejection of synthetic hallucination loops.
- Status: **SUCCESS**

---

## Physical Device Verification Status

- **Pending:** Hardware trace validation on physical devices for OpenRouter transient retry behavior and SpeechRejection UI card visibility under live physical voice input.
