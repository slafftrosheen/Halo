# PHONE-ASSISTANT-002: host-first runtime

Baseline: `8dcd5adfacd1a0b17d411959fb5709b3d23576ef`. Implementation date: 2026-10-01.

## Execution authority

SpeechRule emits semantic native intents with resolved values. CognitiveSession sends deterministic actions to NativeCapabilityDispatcher before provider selection. The dispatcher has no AI dependency and starts a durable NATIVE run. NativeCapabilityRequest maps to a declared host tool through SkillExecutionService. The existing tool turn remains responsible for mandatory confirmation, user policy, capability admission, resource leases and journal receipts. Agentic turns use the same authority.

Native approval resume restores the original prepared tool call and run identity. Terminal and unknown-outcome runs cannot silently replay. Wearable origin retains the device speech target and DISALLOW_PHONE fallback. Dispatch receipts are reported as dispatched; only observed results are confirmed. Missing or invalid native slots request clarification without invoking inference.

CHAT answers use ToolUseMode.NONE. Compound reasoning uses AGENTIC. Image workloads select VISION before either tools or a preferred CHAT role. Unbound AGENTIC and VISION use independent automatic selection; explicit inherit remains a user option. Neither lane changes persisted CHAT selection.

## Host integration

Contacts, media, sharing, calendar, alarms/timers and settings have dedicated manifests; the historical phone umbrella remains compatible but is hidden from the main skill catalogue. The audit also connected previously orphaned notebook screen capture, study, research/delegation, conversation/payload reading, routines and procedures to their owning skills. The catalogue contains 28 bundled skill manifests. Media observation requires notification access while control retains its platform fallback.

Per-tool permission requirements survive built-in manifest merging. Calendar reads and event writes have separate Android permissions. Event creation and notification dismissal require confirmation. Notification listing uses live NotificationListenerService state; RemoteInput reply remains bounded by per-source notification policy.

Skill Check is read-only and runs on IO. Research and Vision checks resolve role setup without inference; native checks do not consult an AI target. The audit reports declared tools without executors, orphan executors, duplicate definitions and unavailable tools. Host definitions remain discoverable when permission is missing so that granting permission does not require restart.

The voice service uses public isActiveService truth, retains a lightweight session UI and does not instantiate HomeViewModel. Host execution blocks sensitive actions while locked. Keyguard launch is disabled in the service XML pending physical verification. Assistant context and screenshots remain tied to the active interaction.

Home projects immutable dashboard state from its ViewModel. Quick actions are Open app, Timer, New note and Vision. New note uses the shared durable skill authority. Attached image input links to Vision setup and privacy settings before submission.

Android 16 AppFunctions use Jetpack annotations and KSP-generated service/schema. The exported surface is bounded create-note, search-notebook, add-working-set and status, running host dispatch on Dispatchers.IO. It shares policy, journal and lease enforcement. See [Android AppFunctions integration](https://developer.android.com/ai/appfunctions/add-appfunctions).

BuildConfig exposes HALO_GIT_COMMIT and HALO_GIT_DIRTY. Commit resolution is GIT_COMMIT environment, local Git short hash, then unknown; Git absence does not fail a build.

Fresh ASR acquisition asks active local LLM computation to cancel cooperatively outside the coordinator mutex. LiteRT cleanup releases its execution lease; idle cache residency remains separate and need not be unloaded to admit ASR. Existing ASR warm policy remains 90 seconds.

## Verification boundary

Automated results are recorded in the physical and Vision acceptance documents after validation. Phone role invocation with the app closed, keyguard behavior, real provider calls, notification RemoteInput delivery, calendar visibility, wearable output and device latency require physical checks. Compilation and fake HTTP responses do not establish those results.

## Automated matrix scope

NativeCapabilityDispatcherTest executes 13 commands across five persisted CHAT labels (LiteRT, unavailable Gemini, OpenAI, concrete OpenRouter and OpenRouter free) through the real shared tool turn and durable coordinator with a fake host executor. It verifies host calls, completed NATIVE journals, zero ProviderTurnStarted entries, no terminal replay, read-only Check, disabled skill rejection and lock rejection. These are routing and authority checks; they do not establish physical phone control or real provider outage behavior.

HostFirstSkillAuditTest initializes the host graph without HomeViewModel and requires zero orphan, unresolved, duplicate and permission-mismatched tools. AndroidCapabilityPublisherTest checks that repeated unchanged checks cannot create a catalogue refresh loop. Manifest tests preserve independent calendar read/write and notification permissions through loading and merging.

## Automated validation — 2026-10-01

The complete requested Gradle suite and `:app:assembleDebug` succeeded. JUnit totals: **610 tests, 0 failures, 0 errors, 0 skipped**. The app suite contains 172 tests, including the actual Gemini/OpenAI/OpenRouter image wire test, public assistant eligibility, provider and ElevenLabs settings persistence, and the complete host graph audit without HomeViewModel.

Audit: **28 skills, 81 registered tools, 0 orphan tools, 0 unresolved tools, 0 duplicate tools, 0 permission mismatches**. The bundled manifests declare 75 unique tool names; internal tools account for the remaining registered definitions. Native matrix coverage is 13 commands across five CHAT labels plus disabled-skill and locked-device rejection. Native resource tests include cooperative ASR preemption without idle-cache eviction.

The Gemini and OpenAI module test tasks have no standalone JUnit cases; their actual provider serialization is exercised by the app wire test. Physical and live-provider acceptance remains pending.

BuildConfig: `HALO_GIT_COMMIT=8dcd5adfacd1`, `HALO_GIT_DIRTY=true`. Changes remain in the working tree. The APK includes all 28 manifests and KSP-generated AppFunctions XML with createNote, searchNotebook, addToWorkingSet and getHaloStatus.

Artifact: `releases/halo-alpha-debug.apk`, 228,588,002 bytes. SHA-256: `134fbbb522b5fb424b76ed7075294e2038523d664602982bfba4cb7ea52ba5ed`. It matches `app/build/outputs/apk/debug/app-debug.apk`. `git diff --check` passes.

```powershell
./gradlew.bat :sdk:provider-sdk:test :sdk:runtime-sdk:test :runtime:cognition:test :engines:interpretation:test :engines:reasoning:test :engines:planning:test :engines:execution:test :engines:ai:test :host-android:testDebugUnitTest :providers:gemini:test :providers:openrouter:test :providers:openai:test :providers:litert:testDebugUnitTest :libraries:halo-cognition-language:test :app:testDebugUnitTest :app:assembleDebug --continue --console=plain
```

ADB reports no connected devices. An emulator definition is present, but no physical acceptance or installation was performed. Phone role invocation, locked-device behavior, real notification delivery/calendar visibility, live model recognition, wearable audio and device latency still require the physical acceptance checks.
