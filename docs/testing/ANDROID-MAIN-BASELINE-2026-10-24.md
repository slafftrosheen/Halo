# Android main consolidation and baseline — 2026-10-24

Issue: [#55 Android Cloud Alpha Acceptance](https://github.com/slafftrosheen/Halo/issues/55). This is a baseline pass, not alpha sign-off.

## Source and Git delivery

- Fetched starting remote main: `e6e59b939799dfcff96fb7262e84d100609f997c`.
- Tested source tree committed and pushed as `144919ec6649b69f00f2dc40308f5397be877b44`.
- Audited all four linked checkouts: Halo-main, Halo-main-hml, Halo-issues, Halo-ai132-tests. All were clean after consolidation; no local branch tip remained outside main ancestry.
- Preserved two pending setup-guide regression tests alongside the newer main tests.
- Reconciled remaining AI-132 physical-test sources and historical evidence. Historical reports do not count as current acceptance.
- Recorded the superseded DevKit 1.2 UI branch without reverting current DevKit 2.0 / Brand Book 3.0 behavior. Preserved its remaining assets and review files.
- Archived the unused old Canvas arrangement helper under `historical-sources/CanvasArrangementMotion.kt.txt`: its old state type was removed, and the current editor already owns animation. Updated historical language/Mini Halo test references to current APIs.

## Build and exact installed artifacts

Physical Redmi `23124RA7EO`, Android 15 / API 35, approximately 8 GB RAM. ADB was authorized. Both APKs were installed with `adb install -r`, preserving app data, and device-side SHA-256 matched local build files.

| Artifact | SHA-256 |
| --- | --- |
| app-debug.apk | `8018a0ad8ffc13bdc4c0687d0949b2ff214a21eeb3fb2036fa36dd7abec37a4d` |
| app-debug-androidTest.apk | `eddfc1793096fc509b8e198bdd1f6cddaf948dda0aabc6b82e8932c44d6f4524` |

Build began on `30b8ae8b9` with the compatibility corrections subsequently committed as `144919ec6`. The APKs correspond to that corrected source tree; do not infer their embedded Git metadata equals the later commit. Instrumentation source stamp: `30b8ae8b9-compatibility-fix`.

## Current results

| Scenario | Result | Evidence / limits |
| --- | --- | --- |
| Production and test APK builds | PASS | `:app:assembleDebug :app:assembleDebugAndroidTest` |
| Focused JVM regressions | PASS | 34 tests, zero failures/errors/skips: setup progress 13, tool descriptors 10, presence assets 6, settings navigation 3, Canvas motion 2 |
| Physical Android instrumented fixtures | PASS | 26 tests across 13 classes; `OK (26 tests)`, approximately 30 seconds. Isolated Android owners/databases and synthetic inputs; not cloud or spoken E2E |
| Native workspace / files / Studio / reopen | PASS | WorkspaceOperabilityDeviceTest, WorkspaceContinuityDeviceTest, WorkspaceFileToolsDeviceTest, StudioDeviceTest |
| Native note / attachments / deletion / Study controls | PASS | NoteEditorControlsDeviceTest, NotePurgeDeviceTest, NoteAttachmentControlsDeviceTest, StudyLearnerControlsDeviceTest |
| Native Canvas / PDF / input adapters | PASS | AndroidInkInputTest, CanvasDraftReceiptDeviceTest, CanvasPreviewDeviceTest, CanvasPdfInspectionDeviceTest. Synthetic events do not qualify a physical stylus |
| Polish large-text locale fixture | PASS | PolishLanguageDeviceTest; does not establish full four-language accessibility acceptance |
| Real microphone → cloud answer → audible TTS | PASS, one-turn smoke | UI reached listening and speech conversion; recognized `Привет Хейлу, сколько будет 2+2?`; cloud reply `Привет! 2 + 2 будет 4.` User explicitly confirmed it answered aloud correctly |
| Free route for observed voice turn | PASS | Current public provider metadata reports zero prompt/completion pricing for `openrouter/free`. Runtime trace at 17:19:54–55 Europe/Riga: provider=openrouter, model=openrouter/free, location=REMOTE, fallbackUsed=false, DIRECT_RESPONSE, toolsIncluded=false |

The Full Power profile initially pointed to local AI despite OpenRouter credentials being configured. Selected OpenRouter using the existing provider UI, retained Free Models Router, and used a fresh conversation before the spoken cloud test. No paid model, purchase, key extraction, or paid fallback was used. This configuration change remains selected on the phone.

## Reproduction and evidence

JVM/build command:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests com.halo.app.ui.onboarding.SetupProgressStoreTest `
  --tests com.halo.app.ui.presence.PresenceArtworkContractTest `
  --tests com.halo.app.di.tools.HostToolDescriptorDispatchTest `
  --tests com.halo.app.ui.screens.settings.SettingsNavigationTest `
  --tests com.halo.app.ui.screens.notebook.CanvasArrangementMotionTest `
  :app:assembleDebug :app:assembleDebugAndroidTest `
  --no-daemon --no-parallel --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx4g' --console=plain
```

Native execution used `adb shell am instrument -w -r`, the 13 classes in the accompanying native-test-plan.json, `runId=main-consolidation-20261024`, and the source stamp above. See `main-baseline-2026-10-24/` for sanitized test results, artifact fingerprints and routing diagnostics. Raw local build logs and UI captures remain in the sibling `verification-2026-10-24-main-consolidation` folder and `main-consolidation-validation-20261024*.log` files; private conversation captures are not published here.

## Remaining #55 acceptance

NOT_RUN in this pass: 10+ turn conversation, barge-in/mute/replay, microphone permission denial/recovery, Bluetooth/background voice, three real cloud cross-tool workflows in typed and spoken modes, full network fault/Private denial campaign, process-death and memory-pressure campaign, complete EN/RU/LV/PL / TalkBack / tablet matrix, and commerce acceptance. Physical stylus and actual Play transactions remain equipment/owner-dependent. No cloud tool-use claim follows from this DIRECT_RESPONSE voice turn. Issue #55 remains open.
