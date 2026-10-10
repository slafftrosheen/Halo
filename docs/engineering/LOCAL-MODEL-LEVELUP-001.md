# LOCAL-MODEL-LEVELUP-001 — managed local models

2026-10-10. Baseline main: `4bb341e689c8254672aea23cf33901d6e91623a5`.

## Changes

The on-device model surface now offers explicit Hugging Face sync and search, a retained offline catalog, device-fit ordering, download/resume, an accessible answer test and conversation selection. Development matrices and backend controls remain available through Advanced/Details. The shared model library routes downloadable local entries to acquisition, including from a role picker. Model roles have plain-language names; existing role bindings remain the execution authority for conversation, quick replies, reasoning, tools and images.

Hub discovery reads public `litert-community` text-generation repositories (up to 60 search results and 100 pinned files per sync). It accepts portable top-level `.litertlm` files with immutable revision, exact LFS size and SHA-256; it excludes obvious accelerator-specific variants, unpinned files and other formats. Cache is bounded to 1,000 entries and stored outside backup. A failed sync retains existing entries. Equal fingerprints retain their existing canonical identity; different bytes receive a distinct identity. Search is sent to Hugging Face only after Sync; prompts, notes and speech are not sent. This is discovery of candidates, not a promise of LiteRT runtime compatibility. Unknown context uses the artifact's runtime default; conservative budgeting is not an upstream context-window claim. Discovered artifacts default to CPU and no claimed vision/tool capability. Existing measured GPU/role qualification is retained.

The legacy `GATED_IMPORT_ONLY` enum is retained for serialized identity and public-only defaults; the acquisition owner adds an explicit authenticated path. Gated acquisition requires the user's upstream license acknowledgment and a valid Hugging Face read token. The publisher enforces access. The token is masked, kept only in memory for that transfer, sent only to HTTPS huggingface.co and never stored in catalog, work data, model sidecars or logs. Redirect handling does not copy credentials to another host. Import remains available for unpublished/manual artifacts. Gated downloads require renewed authorization after process death.

Public LiteRT transfers run as unique persistent WorkManager data-sync foreground work, with a pause notification and network constraint. Work input contains only the catalog model ID; the worker re-resolves source and checksums from the owned catalog. UI progress selects the newest work record for each model; old failed/canceled attempts do not override a retry. Selection, inference and native initialization are not part of download work. Active acquisition prevents delete/discard. Android foreground-service/quota behavior still needs a real-device background test.

Two concrete resume defects were fixed: a matching pinned prefix survives a lagging progress checkpoint after process death, and storage admission budgets remaining bytes rather than counting the prefix twice. A complete matching file is rehashed and installed without another fetch. Wrong pins, invalid range responses and checksum failures cannot become installed. Cancellation interrupts the underlying LiteRT HTTP call. Current generation-proven and installed artifacts sort first; storage estimates apply to downloads. RAM/storage ordering is guidance (weight-memory estimate, current free RAM, declared RAM floor and storage reserve), distinct from actual artifact/runtime-qualified generation and role evidence.

Kokoro now offers an explicit in-app complete English pack download from the sherpa-onnx publisher, with byte/SHA pin, progress, pause, retained partials, range restart handling, bounded tar/bzip2 conversion and the existing native validation/atomic installation path. It retains the old pack until validation succeeds. No selection or playback happens automatically: choose Kokoro under Speech output, then Play test phrase through the existing routed player. English v0.19 supports the existing 11 voices; Russian/Latvian still require a configured compatible alternative such as Android speech. A lone ONNX file or arbitrary Kokoro repository is not a complete voice pack. Apache Commons Compress 1.28.0 (Apache-2.0) supplies archive decoding; no reference-app code was copied.

Speech model acquisition now keeps file-specific partials, honors Range, restarts safely when Range is ignored, retains short interrupted downloads, validates before promotion and skips already installed files. Voice settings exposes Pause. Its existing model selection, capture, recognition and resource owners remain authoritative. Kokoro/speech acquisition remains owned by the explicit screen/view-model job; interrupted files resume on another explicit install. This batch does not add persistent background speech-model installation or automatic retry/loading.

## Reference mining

* [OneBox LocalModelDownloader](https://github.com/wangzhishou/OneBox/blob/654ceadf044db44515dcc3302a99d0b37d6a8d92/feature/settings/src/main/java/com/wanbaohe/setting/local/download/LocalModelDownloader.kt): a direct local setup surface, stream progress and cancel tied to the real HTTP call. Halo retains checksums and resumable prefixes instead of adopting its delete-on-cancel policy.
* [opendroid ModelDownloadWorker](https://github.com/yashab-cyber/opendroid/blob/6ff5a061755b597b0558fed1f565587837ed4d51/app/src/main/java/com/opendroid/ai/core/llm/ModelDownloadWorker.kt): registry-owned work identity, verification and foreground acquisition. No external URL/path is admitted through worker input.
* [OGAM Hugging Face service](https://github.com/off-grid-ai/OGAM/blob/3118e75f5c47715c2c6ae792d86a4b2eaad60688/src/services/huggingface.ts) and [compatible setup catalog](https://github.com/off-grid-ai/OGAM/blob/3118e75f5c47715c2c6ae792d86a4b2eaad60688/src/services/autoSetupCatalog.ts): repository file discovery and device-budget guidance. Halo's installed/resident/selected/qualified facts stay separate; no GGUF/MNN backend or automatic selection was introduced.

## Validation

Final frozen serial gate passed in 6m 4s: debug Kotlin compilation, APK assembly and 958 tests (537 app, 129 LiteRT, 20 Sherpa STT, 21 speech, 251 cognition), zero failures/errors/skips. Sixteen new regression tests passed. Unchanged speech/cognition results were reused by Gradle. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. `git diff --check` passed; localization generation is deterministic. Commands:

```powershell
python scripts/generate_ui_languages.py
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :providers:litert:testDebugUnitTest :providers:sherpa-stt:testDebugUnitTest :engines:speech:test :runtime:cognition:test :app:assembleDebug --no-parallel --max-workers=1 --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m'
```

New regressions cover Hub pins/gates/portable formats/cache restoration, memory/storage guidance, persisted work without credentials, checkpoint lag and complete-file reuse, Kokoro checksum/range/reuse/archive traversal, and speech range plus verified-file reuse. Generated catalog: 1,550 EN/RU/LV entries (33 added); foreground notification strings have EN/RU/LV provider resources sourced from the same canonical rows.

Host acquisition downloaded the real publisher archive: 319,625,534 bytes; SHA-256 `912804855a04745fa77a30be545b3f9a5d15c4d66db00b88cbcd4921df605ac7`; 398 entries, 369,315,617 expanded bytes, zero links, expected complete layout. Current upstream Hub metadata was fetched for all seven built-in repositories; portable file metadata exists and Gemma remains gated. One-byte Range probes to all six public built-in URLs returned HTTP 206 with their exact expected total sizes. This establishes upstream discovery/archive availability, not native inference/synthesis or licensed access.

At the software gate no phone or Hugging Face gated token was available. After source commit `2932c04f6a0c0afcfe61b116f0c7aba082f9dbef` was pushed, its committed-source APK assembled in 3m 27s, installed with `adb install -r` on Samsung SM-G990B2 and launched successfully. Existing data was not cleared; selected models were not changed. A Russian setup surface was visible. The phone disconnected before model-library navigation, catalog sync, download or native inference/speech could be exercised. This proves APK installation/startup only. Still validate: low-storage/resume, process kill and notification pause, large-font/one-hand usability, license rejection and renewed token, exact model selection then Home/Study/Workspace/role generation, stable CPU/GPU qualification, Kokoro download/install then routed audible speech, and EN/RU/LV recognition accuracy. Background quota, provider-host failures and model-specific native incompatibility may remain hardware/provider limitations. No GitHub Actions modified or manually run; commit uses `[skip ci]`. Existing user IDE changes are excluded.

## Exact file manifest

40 files; existing IDE changes excluded.

```text
CURRENT-STATE.md
app/build.gradle.kts
app/src/main/java/com/halo/app/ai/LocalModelCatalogRepository.kt
app/src/main/java/com/halo/app/ai/LocalModelDeviceFit.kt
app/src/main/java/com/halo/app/audio/KokoroModelDownload.kt
app/src/main/java/com/halo/app/audio/KokoroSpeechProvider.kt
app/src/main/java/com/halo/app/di/ProviderGraph.kt
app/src/main/java/com/halo/app/language/UiCatalogue.kt
app/src/main/java/com/halo/app/ui/screens/settings/IntelligenceViewModel.kt
app/src/main/java/com/halo/app/ui/screens/settings/ModelBrowserScreen.kt
app/src/main/java/com/halo/app/ui/screens/settings/ModelBrowserViewModel.kt
app/src/main/java/com/halo/app/ui/screens/settings/OnDeviceModelsScreen.kt
app/src/main/java/com/halo/app/ui/screens/settings/SpeechOutputSettings.kt
app/src/main/java/com/halo/app/ui/screens/settings/VoiceSettingsScreen.kt
app/src/main/java/com/halo/app/ui/screens/settings/VoiceSettingsViewModel.kt
app/src/main/java/com/halo/app/ui/shell/AppShell.kt
app/src/main/localization/ui.tsv
app/src/main/res/values-lv/ui_strings.xml
app/src/main/res/values-ru/ui_strings.xml
app/src/main/res/values/ui_strings.xml
app/src/test/java/com/halo/app/ai/LocalModelDeviceFitTest.kt
app/src/test/java/com/halo/app/audio/KokoroModelDownloadTest.kt
docs/08-providers/LITERT-DEVELOPMENT-MATRIX.md
docs/10-features/LOCAL-MODELS.md
docs/engineering/LOCAL-MODEL-LEVELUP-001.md
providers/litert/src/main/java/com/halo/providers/litert/LiteRtLocalProvider.kt
providers/litert/src/main/java/com/halo/providers/litert/models/HuggingFaceLiteRtCatalog.kt
providers/litert/src/main/java/com/halo/providers/litert/models/LiteRtCatalogModel.kt
providers/litert/src/main/java/com/halo/providers/litert/models/LiteRtDownloadWorker.kt
providers/litert/src/main/java/com/halo/providers/litert/models/LiteRtModelDownloader.kt
providers/litert/src/main/java/com/halo/providers/litert/models/LiteRtModelManager.kt
providers/litert/src/main/res/values-lv/model_download_strings.xml
providers/litert/src/main/res/values-ru/model_download_strings.xml
providers/litert/src/main/res/values/model_download_strings.xml
providers/litert/src/test/java/com/halo/providers/litert/models/HuggingFaceLiteRtCatalogTest.kt
providers/litert/src/test/java/com/halo/providers/litert/models/LiteRtDownloadResumeTest.kt
providers/litert/src/test/java/com/halo/providers/litert/models/LiteRtDownloadWorkTest.kt
providers/sherpa-stt/src/main/java/com/halo/providers/sherpa/stt/SherpaSpeechModelCatalog.kt
providers/sherpa-stt/src/main/java/com/halo/providers/sherpa/stt/WhisperModelDownloader.kt
providers/sherpa-stt/src/test/java/com/halo/providers/sherpa/stt/SpeechDownloadResumeTest.kt
```
