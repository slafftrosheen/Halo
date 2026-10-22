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
