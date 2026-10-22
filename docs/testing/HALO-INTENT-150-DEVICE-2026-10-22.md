# HALO-INTENT-150 verification update — 2026-10-22

Source revision under verification: `44f21ccf7110419f48360c529969feb2425da68a` (`main`).

## Automated source gates

The following focused test tasks passed with zero failures, errors, or skipped tests:

| Module/task | Filter | Cases |
|---|---|---:|
| `:engines:interpretation:test` | `SmallIntentSpeechRuleTest` | 8 |
| `:providers:litert:testDebugUnitTest` | `HaloPromptToolProtocolTest`, `HaloToolMarkupStreamGuardTest` | 19 |
| `:runtime:cognition:test` | `InterpretationRouteTest` | 7 |
| `:libraries:halo-cognition-language:test` | `PoliteCommandTest` | 5 |
| `:app:testDebugUnitTest` | `LocalIntentModelRouterTest` | 2 |
| **Total** |  | **41** |

`:app:assembleDebug` succeeded. APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `3b67d11ee33dccd888a78011678342e84fa8412211ed8284ebbe8e230774c4fc` (282,150,557 bytes).

## Attached-device action

The APK was installed with `adb install -r` on the attached Samsung SM-G990B2 (`R5CTA0TKLZZ`), package `com.aistudio.braintool.xkqzps`; installation returned `Success`. The app process was present after launch. At the time of inspection Android reported the notification shade as the focused window, so foreground visual interaction was not confirmed.

## Acceptance still open

No Qwen intent inference was run. No EN/RU/LV/PL live classification matrix, warm/cold latency, memory/residency, cancellation, STT/TTS contention, native or screen action, confirmation, no-duplicate-effect, tool-markup speech/history, restart/model-removal, or on-device clarification journey was qualified. The connected-device installation is evidence of package install only, not behavior acceptance. Polish UI localization remains incomplete. Keep Issue 36 open pending those owner/alpha checks.

No CI or GitHub Actions were used.

## Follow-up: safe settings launch and Home receipt — 2026-10-22

After the run above, the Home progress projection was corrected for `halo_open_settings`: a successful dispatch is shown as `Settings · Action confirmed`, because opening the settings screen is the complete requested effect. Other dispatched actions still show `Delivery not confirmed` unless they have confirmed effect evidence.

The updated debug APK was built from the follow-up source change and installed on the same Samsung SM-G990B2 (`R5CTA0TKLZZ`); SHA-256 `49F6AAE6F0554E126259FB45E9E6FF9DCE013327B84F001C6EBA447DF97C548F`. On-device, sending `Open Settings` brought `com.android.settings/.homepage.SettingsHomepageActivity` to the foreground. Returning to Halo showed `Settings · Action confirmed` with the completed check. This verifies one deterministic, non-mutating native action and its user-facing receipt only.

The focused `HomeTaskProgressTest` suite passed (6 tests), and `:app:assembleDebug` succeeded in the same final Gradle run. This follow-up supersedes the earlier statement above that no native action was exercised. The Qwen model was not installed, so no Qwen inference, language matrix, latency/residency, or broader action-loop checks were run; Issue 36 remains open for those acceptance gates. No CI or GitHub Actions were used.
