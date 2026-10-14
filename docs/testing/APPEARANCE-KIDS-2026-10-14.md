# Appearance and Kids mode — 2026-10-14

Scope: two colour choices per light/dark profile, and a simplified learning workspace. Base revision: c696e3885974267b6f1da3f5bf36717d537bbf94 (pulled before implementation).

## Use

Settings → Appearance retains System / Light / Dark display mode. Choose Light colours or Dark colours to edit that profile, then choose Accent colour (Mint, Blue, Lilac, Coral) and Background tone (Natural, Warm, Cool). The preview shows the edited profile. Changes persist independently and apply through the shared Halo theme.

Settings → Kids mode enables a single learning page. Choose Maths, Nature, Reading, or your own topic; start a guided primary-level Study session; submit attempts, ask questions, advance, or read available responses aloud. Ask Halo also accepts normal questions and tool requests. My tools provides timer, camera, and device-information shortcuts. Colours opens the same appearance controls. Full Halo restores the standard navigation.

This is a presentation mode on the existing account and conversation. Existing tool permissions and durable approvals remain in force, including the global interaction renderer. It does not establish a child account, parental lock, content filter, or separate conversation history. Entering the page does not automatically display an earlier answer. Stop cancels current Home/Study work; leaving the lesson cancels its generation and reading. Backgrounding stops microphone input and lesson reading.

## Validation scope

Palette contrast checks cover 24 combinations and five foreground/background pairs at 4.5:1. Preference tests cover independent light/dark persistence, a user edit during initial loading, invalid stored values, and Kids mode persistence. Screenshot fixtures exercise the production Kids header, learning card and topic controls at 320 dp in light/dark and Russian/Latvian at 200% text. They do not run a live tutor or tool action. English, Russian and Latvian catalogue generation is checked.

Build/test results and the design assessment are recorded after the final verification run. Physical Study/provider/TTS quality and native action outcomes require the connected phone; an APK build does not establish these outcomes.

## Design assessment

Refactoring UI diagnostic score: 7/8 verified in the captured component fixtures. Hierarchy, whitespace, label emphasis, shared spacing, constrained widths, foreground contrast, and appropriate flat/card/sheet elevation pass. The grayscale/blur diagnostic remains unverified rather than being counted as a pass. No failed rows remain in the reviewed fixtures after repairing dark-header contrast, profile-selector wrapping, and the large-text header layout. This score does not certify every existing app page.

The default mint theme is retained. Branding and semantic error colours retain their existing meaning. Enlarged text stacks the Kids face above the heading; other choices wrap instead of shrinking text. Content scrolls on small screens. Screenshot animation is deliberately frozen through the existing reduced-motion presentation flag; the production face remains animated.

The first recording run was stopped after a continuous face animation prevented the recorder from reaching idle. Subsequent recordings use reduced motion. No live provider/model or action result is inferred from the fixtures.

## Final results

- Final focused gate: 24 tests, 0 failures, 0 errors, 0 skipped. Both `:app:assembleDebug` and `:app:assembleDebugAndroidTest` succeeded on the final production sources.
- EN/RU/LV catalogue check: 2357 strings; `git diff --check` passed.
- Seven current component captures and suite counts are saved in `artifacts/APPEARANCE-KIDS-2026-10-14/`.
- Debug APK SHA-256: `5143CFDDDB8567620E8BBE72065E2777C9F81DE644DB65C19E4BD1C41B81F798`.
- Android test APK SHA-256: `2F88DC9AA73B24BFA0512E7B7A900C020A7A779522ED4CC15B87A0D9EE0B3122`.
- ADB reported no connected devices on the final checks. Installation and physical tutor/voice/tool acceptance were not run for this batch.

Reproduce with:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*Palette*Test' --tests '*Kids*Test' --tests '*AppearancePreferencesTest' --tests '*FontScaleTest' --tests '*SettingsComfortSnapshotTest' :app:assembleDebug :app:assembleDebugAndroidTest '-Proborazzi.test.record=true' --no-parallel --max-workers=1 --no-daemon '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m'
python scripts/generate_ui_languages.py --check
```
