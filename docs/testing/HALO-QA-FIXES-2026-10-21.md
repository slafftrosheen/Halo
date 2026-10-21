# BrandBook regression and image-access fixes — 2026-10-21

Base: `b29fe8d502318f746a8c88da19e4053b5312d113`, synchronized with `origin/main` before edits.

## Changes

- Restored the twelve supplied Learning static SVG variants; made decorative motion fail safely when the platform thermal listener is unavailable.
- Aligned UI regression fixtures with the current BrandBook composer, settings destinations, developer opt-in, localized branding, and artwork contracts. Isolated UI fixtures use the hostless theme and injected appearance preferences instead of starting the application runtime.
- Updated database migration fixtures to include schema 27 and tool descriptor expectations to match existing executor ownership.
- Rejected contradictory request/output/interaction identities in content-free voice tracing. Preserved final-input classification before treating an arbitrary “open …” phrase as an app command.
- Added explicit remote-media spending consent to the remote Images setting, with restricted-mode checks, persistence failure handling, background dismissal, and access to Costs and limits. Privacy permission alone does not grant spending consent or dispatch a request.
- Automatic vision under a free OpenRouter default can select an advertised compatible `:free` image model. Per-candidate filtering continues to reject paid targets and the saved chat model stays unchanged.
- Removed the incorrect requirement that Gemma's text backend must be GPU for images: the existing engine configuration assigns the image encoder to GPU independently. Existing capability qualification and runtime admission remain in place.

## Verification

The baseline had 56 failures across five modules. The 55 failures in enabled suites now pass. The remaining local-model profile migration fixture was corrected in source but deliberately excluded from execution, along with other local-model tests, as requested.

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest haloVerify --init-script tools/testing/skip-local-models.gradle --continue --no-daemon --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8'
python scripts/test_generate_ui_languages.py
python scripts/generate_ui_languages.py --check
git diff --check
```

- Full enabled gate: **BUILD SUCCESSFUL**, 2,073 tests, zero failures/errors/skips within enabled tasks, across 21 result-bearing tasks. Unchanged tasks reused their valid Gradle results; changed suites executed. Machine-readable counts: [results](HALO-QA-FIXES-2026-10-21-results.json).
- Five new permission regressions passed, including restricted-mode rejection and failed persistence. Two new free-vision routing regressions passed. A new contradictory TTS-output identity regression passed.
- Localization: two Python regressions passed; 3,728 EN/RU/LV strings current.
- The first complete attempt encountered a Windows Robolectric native SQLite finalizer crash. Windows app test workers now recycle after sixteen classes, serially; the subsequent complete run passed with the native driver and all enabled tests retained.

## Installed phone

Samsung SM-G990B2, Android API 36: app and test APK updated using `adb install -r`, preserving app data.

- Explicit `com.halo.app.ink.AndroidInkInputTest`: **10/10 passed**. These are synthetic Android input adapter checks, not physical pen qualification.
- Settings and Appearance open successfully. Searching `context` finds What halo can use.
- Remote Images opens the explicit spending-consent dialog. Cancel leaves effective remote-image access off; backgrounding dismisses an unapproved prompt. Costs and limits opens. The pre-existing privacy flag was retained, while paid-media consent remained absent/default false; no spending opt-in was granted by QA.
- Existing pending user review was left untouched. An existing read-aloud queue resumed at startup and was stopped.

APK: `releases/checkpoints/halo-qa-fixes-2026-10-21-debug.apk` (ignored local checkpoint), 283,897,237 bytes.

SHA-256: `258a79e2a52f31bc98e70f41192164d1dfc420c545026d5b8d872cf5ad61c2ad`. The installed `base.apk` has the same digest.

## Boundaries

No local-model execution, download, benchmark, or vision qualification was run. Gemma's image-path change compiled, and its regression fixture was updated but excluded from execution. This does not establish successful Gemma image inference on the phone; a model lacking existing vision proof still requires its explicit qualification check. No live cloud image request or paid operation was made. Android lint is outside the repository's `haloVerify` gate and was not claimed as passing. No GitHub Actions were requested.
