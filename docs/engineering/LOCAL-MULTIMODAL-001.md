# Local multimodal hardening and settings consolidation

Reviewed 2026-10-11. Extends the existing LiteRT session, artifact verification, and accelerator qualification boundaries.

## Official references

- [LiteRT-LM 0.18.0 release](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.18.0) and [Google Maven versions](https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml).
- [Kotlin API at the shipped 0.18.0 tag](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/docs/api/kotlin/getting_started.md), [ModelInfo contract](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/ModelInfo.kt), and [engine configuration](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/Config.kt).
- [Google Gallery model allowlist](https://github.com/google-ai-edge/gallery/blob/main/model_allowlists/1_0_16.json) and [multimodal encoder wiring](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt).
- [Gemma audio preparation and clip limits](https://ai.google.dev/gemma/docs/capabilities/audio).

## Artifact additions

All revisions, byte sizes, and SHA-256 values were checked against publisher Hub LFS metadata at the immutable revision. The catalogue pins portable Android artifacts, excluding Web and vendor-specific NPU variants.

| Artifact | Bytes | Revision | Acquisition | Upstream RAM guidance |
| --- | ---: | --- | --- | --- |
| Gemma 4 E2B | 2,588,147,712 | `6e5c4f1e395deb959c494953478fa5cec4b8008f` | Open download, Apache-2.0 | 8 GB |
| Gemma 4 E4B | 3,659,530,240 | `28299f30ee4d43294517a4ac93abd6163412f07f` | Open download, Apache-2.0 | 12 GB |
| Gemma 3n E2B INT4 | 3,655,827,456 | `ba9ca88da013b537b6ed38108be609b8db1c3a16` | Publisher license flow or licensed import | 8 GB |
| Gemma 3n E4B INT4 | 4,919,541,760 | `297ed75955702dec3503e00c2c2ecbbf475300bc` | Publisher license flow or licensed import | 12 GB |

Publisher metadata endpoints use `/api/models/{publisher}/{repo}/revision/{revision}?blobs=true`; the exact SHA-256 pins are in `LiteRtModelCatalog`. These are declared, experimental capabilities, not phone acceptance. New models have no native tool-calling claim. Gemma 4 keeps the stable non-MTP artifact rather than enabling unqualified speculative decoding.

## Runtime changes

- Pin the SDK to 0.18.0; qualification identity and native caches include that version.
- Inspect the actual file before heavyweight initialization: reject non-generation models, missing requested modalities, unsupported declared backends, excessive contexts, and newer runtime requirements. Older absent backend metadata does not itself invent compatibility.
- Initialize encoders only for requested media. Text uses the existing CPU-safe qualification policy. Images require qualified or explicitly selected GPU; audio uses CPU. CPU thread settings now reach both CPU text and audio executors.
- Carry encoded audio through provider generation, streaming, diagnostic configuration, SDK contents, installed inventory capabilities, and the downloadable catalogue.
- Reject empty and oversized inputs before native conversations. Encoded audio is inspected in memory with Android metadata APIs and must have a readable duration of at most 30 seconds and occupy at most 20 MiB. Restrict context recovery to actual allocation failures. Keep cancellation, partial-output retry boundaries, and manual tool execution ownership.
- Include system instructions and audio contents in generation configuration equality and hashing.
- Update download states atomically so inventory refreshes and concurrent downloads cannot erase progress or license failures.

## Daily-use UI

Settings exposes everyday controls directly, separates setup/support, and removes duplicate answer/voice setup links and the second developer disclosure. Intelligence owns model/provider configuration and diagnostics; Voice owns recognition, output, and conversation preferences. The local audio test selects an installed model explicitly, sends audio only on Transcribe, uses no tools, changes no model selection, and saves no conversation.

Settings and Intelligence share destination rows. Workspace and ordinary screens share header rendering. Voice toggles expose switch semantics. New labels have English, Russian, and Latvian resources; provider/model names and generated transcripts retain their source text. Vision candidate refresh starts on entry and preserves cancellation.

Context privacy owns the shared runtime and remote-sharing policies; Advanced intelligence no longer repeats those controls. Changing interface language preserves a separately selected speech-recognition language. Shared workspace toggle rows use a real switch with one click owner.

## Validation boundary

The complete gate passed: 145 LiteRT tests, 28 provider SDK tests, and 586 app tests, with zero failures, errors, or skips (759 total). `:app:assembleDebug` succeeded. Coverage includes audio/image SDK contents, request propagation, encoder residency, runtime version parsing, input limits, capability gates, concurrent download state preservation, settings navigation, switch semantics, and rendered settings in light/dark themes and Russian at 200% text size.

The screenshot review also identified a missing standalone privacy background; that screen now paints its own themed surface. All 586 app tests and the debug APK build passed again after this correction, and the corrected render was visually inspected. `git diff --check` passed.

Rendered previews: [Settings](../../app/build/outputs/roborazzi/com.halo.app.ui.screens.settings.SettingsComfortSnapshotTest.everydaySettingsLight.png), [dark Settings](../../app/build/outputs/roborazzi/com.halo.app.ui.screens.settings.SettingsComfortSnapshotTest.everydaySettingsDark.png), [Russian at 200%](../../app/build/outputs/roborazzi/com.halo.app.ui.screens.settings.SettingsComfortSnapshotTest.russianSettingsLargeText.png), and [privacy](../../app/build/outputs/roborazzi/com.halo.app.ui.screens.settings.SettingsComfortSnapshotTest.privacyHasOneHome.png).

ADB intermittently listed the phone, but the final `adb install -r` attempt failed with `no devices/emulators found`. The new APK was not installed or physically accepted in this task.

## Continuation, 2026-10-11

The earlier APK subsequently installed successfully and MainActivity launched on `R5CTA0TKLZZ`. Settings navigation was checked in Russian on the physical phone. A screenshot exposed white status icons against the light background; MainActivity now explicitly follows Halo's appearance preference for system-bar icons using [Android's documented controller](https://developer.android.com/develop/ui/compose/system/insets-views-compose).

Audio and vision diagnostics share a single-operation owner. Cancelled requests retain ownership until asynchronous cleanup completes, errors release busy state, and follow-up operations can start after completion. Vision now exposes progress and Cancel, disables comparison until a source and models are selected, removes stale candidate selections on refresh, and localizes its everyday controls. Both diagnostic screens paint their own themed surfaces. Added regression tests cover overlap, cancellation cleanup, failure, and subsequent success.

The local audio route was opened on the phone. It correctly showed no installed audio-capable model and disabled Transcribe. No audio file, inference request, or download was started. Privacy, provider connection/proof states, audio diagnostics, and vision controls received missing EN/RU/LV translations. Probe ownership is allocated before ingest, and both probe release and pending-camera disposal run through cancellation-safe cleanup.

All 588 app tests passed after the diagnostic cleanup and translation changes. The debug APK installed successfully; the system-bar contrast correction, translated connection status, privacy label, and audio/vision entry screens were checked on the phone. Audio and vision routes also retain the Settings tab selection, with a focused navigation regression check.

The focused navigation regression passed (one test, zero failures/errors/skips), its APK rebuild succeeded, and the final APK installed and launched successfully. ADB UI hierarchy confirms the Settings tab is selected on the local audio screen. No native multimodal generation was accepted: the phone has no installed audio-capable model. Changes remain local and uncommitted.

Physical acceptance of new artifacts, encoder behavior, accuracy, latency, RAM use, cancellation, and repeated turns remains separate. These checks cannot be inferred from JVM tests or successful APK installation.
