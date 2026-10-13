# PHONE-NATIVE-001 — deterministic native command dogfood repair

Date: 2026-10-13. Based on `main` 3d0a9e453a58de990167386cd23599c8c30c331d and the Xiaomi (sapphiren) physical test report.

## Observed versus proven

Physical report: Chrome and lowercase Settings launched; capitalized Settings, Home, and flashlight requests were classified as conversation; LiteRT, headset voice and Stop were blocked. **Code inspection contradicts the report's case-sensitivity diagnosis**: `NativeCommandParser` already lowercases text and already has both flashlight phrasings. Those failures cannot be attributed to the cited regex without checking the installed APK/source revision. Do not claim they are fixed on hardware by this source change.

## Batch scope

- Normalize non-payload command whitespace (including nonbreaking spaces) and case consistently in the two deterministic parsers. Preserve original dictated text payloads and exact screen labels.
- Map bounded Home utterances to `NativeScreenActionIntent(HOME)`; reuse the existing Android accessibility global Home action through normal skill authorization, live snapshot, available system-action checks, redacted journal and postcondition receipt. No new permissions or LLM fallback.
- Extend effect evidence to distinguish Home from Back; dispatch alone is not confirmation.
- Add regression tests for the exact reported utterances in native parsing, the registered interpretation-rule pipeline, dispatcher tool mapping, and operation-specific postcondition evidence. Protect explanatory language from accidental action dispatch.

## Acceptance and limits

This commit is source-and-test coverage only; **no Gradle build, APK installation or physical verification was performed in this environment**.

Run locally:

```bash
./gradlew :libraries:halo-cognition-language:test :engines:interpretation:test :runtime:cognition:test :app:testDebugUnitTest :app:assembleDebug
```

Verify the installed APK's `HALO_GIT_COMMIT` matches the new commit. Repeat the exact phrases in voice and text: `Open Settings`, `open settings`, `Go home`, `go to the home screen`, `turn on flashlight`, `Turn on the flashlight`, `How do I turn on the flashlight?`.

Native actions must not produce provider-turn records. Home must report CONFIRMED only after launcher observation; an unsuccessful/unknown dispatch must not be described as success.

Next batch: native registry inventory and settings/clock actions, after P0 physical retest. Do not conflate model download UX, fallback choices, Bluetooth or Stop acceptance with this parser batch.
