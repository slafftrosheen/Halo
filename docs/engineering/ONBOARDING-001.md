# ONBOARDING-001 — Guided first-run setup (#27)

Implemented against synchronized main `370dc275deb15e996a4441032fc492a224b58b57`. This is software delivery for tester readiness, **not completed human acceptance**.

## What changed

A nine-stage guide opens when setup has not been completed: welcome/language, basic use, permissions, answers, speech, optional devices, privacy, readiness and an actual first interaction. Stage, intention, limited/verified outcome and exact interaction identity persist through the existing Storage owner. Back is bounded; Save and leave preserves progress. Settings provides full rerun and individual answer/voice/readiness entry points without clearing providers, language, notes or devices. A return-to-setup affordance remains visible while using an existing configuration screen. It pops back to the existing guide entry, preserving saved checks/draft instead of stacking a new wizard. Normal navigation is hidden only on the guide itself.

The primary Continue/Open Halo action stays in a bottom panel (56 dp minimum); other controls are at least 48 dp. Labels, headings, selectable state and polite response announcements support screen readers. The guide leaves safe drawing/keyboard insets to AppShell, avoiding double padding in its nested surface. EN/RU/LV interface changes apply immediately, with matching speech-language selection. Intention is a starting preference, not a feature restriction.

Permissions are explicit and contextual: microphone and notifications individually; Bluetooth/nearby is requested by the existing Devices pairing owner; document/photo pickers avoid blanket library access. Denial never prevents text/Workspace use. Android app-permission settings remain available after denial. No permission request is launched merely by entering a step.

Recommended online setup uses the existing provider configuration owner. OpenRouter, Gemini, OpenAI and Claude have provider-specific key guidance and direct key-page links. Input is masked, supports explicit paste, is bounded and **never rememberSaveable**. Save invokes connection validation and model refresh; neither action selects a provider/model automatically. A user selects an actual model explicitly. Errors are generic human messages; endpoints, headers, OAuth and other provider details remain in Advanced. Existing secret-backed storage retains credentials; no key or payload is added to setup logs/report/state. Service billing and privacy remain the user's choice. Official guidance checked: [OpenRouter](https://openrouter.ai/docs/quickstart), [Gemini](https://ai.google.dev/gemini-api/docs/api-key), [OpenAI](https://developers.openai.com/api/docs/quickstart), [Claude](https://platform.claude.com/docs/en/get-started).

The local path presents bounded existing downloadable catalog entries, size, explicit download/cancel, select and qualification test, through IntelligenceViewModel/model-manager authorities. No model, provider, GPU default, benchmark or download was added or enabled automatically. No model is claimed to work before qualification. Advanced retains the existing development matrix.

Speech offers EN/RU/LV, an explicit existing Whisper Base download (about 161 MB), actual microphone capture, gated recognized text and a localized phone-speaker phrase. Capture uses SpeechDiagnosticRunner's host/resource owner and closes the session on success, error or cancellation. Background/step departure stops capture and test playback; it does not cancel unrelated runtime requests. Native recognition remains noninterruptible until its owner returns. Accepted transcripts require the actual runner gate `Accepted`. Recognition proof is bound to tested language/model selection. Captured text stays transient and is never exported. Audio completion and user-confirmed audibility are separate. Alternative listening models/voices are available. Device names and connection state come from existing owners; audio availability is not described as audibility. Phone-only use requires no wearable.

Privacy explains local notes/conversations/context, explicit allowed provider sharing, memory review and manual backup. No mandatory account or automatic sync is introduced.

Readiness separates actual microphone/STT tests, selected-service connection and explicitly tested model generation, audible output confirmation and optional device availability. Connection readiness is **not generation proof**. Test an answer explicitly invokes the existing tool-free, empty-context generation probe, and binds successful proof to the current provider/model; its cost is explained before use. Limited continuation is labeled on the primary action, not silently implied by skipped checks. Fix routes and an explicit limitations choice remain available. The first request is submitted through HomeViewModel's existing observation→interpretation→runtime path, not a diagnostic fake or direct provider call. Completion binds the exact interaction to a committed, nonpending reply. Agent journal evidence additionally excludes pending/failed/rejected/unconfirmed/dispatched-only actions; merely committing an error reply cannot prove action success. Native clarification replies without a confirmed tool outcome are not successful native actions. Local facts can complete without AI; this does not qualify online/local model generation. A setup exit lacking checks or a verified first interaction is recorded as LIMITED, not VERIFIED. No request is automatically replayed after restart. Activity continues approval; normal Home remains the conversation owner.

Settings puts guided language/answer/voice/readiness choices first, existing provider/integration internals behind Advanced, and runtime details behind Developer. This is progressive disclosure, not a rewrite of all settings.

Diagnostics are off by default. Opt-in stores at most 50 enum-only stage/outcome/error entries on this phone; disabling clears them. Explicit sharing exports only stage/outcome/error enums and check booleans, never identity, device names, prompts, speech, notes, keys or provider payloads. Corrupt/free-text diagnostic entries are dropped. No automatic analytics upload or new runtime logging is added.

## Automated validation

Final serial gate passed in 4m 41s: debug Kotlin compilation, APK assembly and 822 tests (531 app, 19 Sherpa STT, 21 speech, 251 cognition), with zero failures, errors or skips. App tests executed against the final source; unchanged speech/cognition task results were reused by Gradle. Nine onboarding regression tests passed. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Translation generation was deterministic and `git diff --check` passed. Commands:

```powershell
python scripts/generate_ui_languages.py
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :providers:sherpa-stt:testDebugUnitTest :engines:speech:test :runtime:cognition:test --no-parallel --max-workers=1 --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m'
```

Regression coverage includes actual NavController entry reuse after configuration/review, rerun without duplicate entries or discarded Home, restore/resume with unrelated configuration intact, safe back/limited completion, exact committed interaction binding, pending/failed/dispatched action truth, corrupt-state recovery, opt-in bounded diagnostics and sanitized reporting. Existing Home/voice/provider tests remain in the gate. An initial compile caught a missing Compose delegate import; corrected before delivery. Generated translations add 126 keys (1,517 per language); all new prose/labels have EN/RU/LV parity. No GitHub Actions changed or manually invoked; delivery uses `[skip ci]`. User IDE files are excluded.

## Release gate still open

A Samsung SM-G990B2 briefly became connected, then disconnected before installation. No APK installation or device startup acceptance is claimed; existing app data was not cleared. JVM success and APK assembly do not establish permission UX, BLE pairing, native recognizer/voice readiness, provider credentials/billing, process-death navigation restoration or human usability. No provider key was supplied and no model downloaded for this task.

Run a clean-install acceptance with one experienced tester, one ordinary nondeveloper adult and ideally an older/less technical tester. Record stage, time/hesitation, observed outcome and recovery; do not record private speech/keys in setup analytics:

1. Switch EN/RU/LV, use large fonts/TalkBack and one hand; check scrolling, fixed action panel, keyboard and back navigation.
2. Deny microphone/notifications/Bluetooth, including Don't ask again; recover through Android settings. Continue using text and notes without access.
3. Kill/restart during each step and while in a configuration sub-screen. Resume without duplicate downloads, credential exposure, automatic recording or replaying an action.
4. Enter a bad online key, lose network mid-check, repair or choose local/limited mode; select a real model and complete a real provider-backed request. Record generation separately from connection check.
5. Explicitly download a listening model, test a consenting short reference phrase in each supported language; compare reference/recognized text. Test cancel/background, occupied microphone and silent audio. Confirm only audible output; try an alternative model/voice on failure.
6. Skip devices; separately pair an available audio/wearable endpoint and test its actual supported action. No HeyCyan requirement or new wearable capability is implied.
7. Complete a harmless actual request, then a supported action; induce a rejected action and pending approval. Verify only the matching committed successful interaction enables completion, while dispatched-only actions remain unqualified. Check Activity continuation and normal Home entry.
8. Reopen individual setup sections from Settings; prove other configuration/notes are unchanged. Enable diagnostics, induce one failure, export, inspect for only enum/boolean fields, then disable and verify deletion.

Heuristic target is 10/10 across discoverability, evaluation, recovery, mapping and constraints. An observed usability score is **not established**: first-use hesitation and physical recovery tests remain required. Issue #27's tester-readiness/release gate must not be represented as physically accepted based on this source batch.

## Exact implementation file manifest

19 files; user IDE changes are excluded.

```text
CURRENT-STATE.md
app/src/main/java/com/halo/app/audio/HaloSpeechOutputCoordinator.kt
app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt
app/src/main/java/com/halo/app/language/UiCatalogue.kt
app/src/main/java/com/halo/app/ui/onboarding/HaloSetupScreen.kt
app/src/main/java/com/halo/app/ui/onboarding/SetupInteractionEvidence.kt
app/src/main/java/com/halo/app/ui/onboarding/SetupProgressStore.kt
app/src/main/java/com/halo/app/ui/screens/settings/SettingsScreen.kt
app/src/main/java/com/halo/app/ui/shell/AppShell.kt
app/src/main/java/com/halo/app/ui/shell/GuidedSetupNavigation.kt
app/src/main/localization/ui.tsv
app/src/main/res/values-lv/ui_strings.xml
app/src/main/res/values-ru/ui_strings.xml
app/src/main/res/values/ui_strings.xml
app/src/test/java/com/halo/app/ui/onboarding/GuidedSetupNavigationTest.kt
app/src/test/java/com/halo/app/ui/onboarding/SetupInteractionEvidenceTest.kt
app/src/test/java/com/halo/app/ui/onboarding/SetupProgressStoreTest.kt
docs/10-features/FIRST-RUN-SETUP.md
docs/engineering/ONBOARDING-001.md
```
