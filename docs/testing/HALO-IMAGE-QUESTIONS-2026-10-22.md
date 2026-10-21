# Phone image questions — 2026-10-21/22

Base synchronized before work: `7477316fd3b4c0f8a5bc9f0e71180108c05951c9` on `main`.
Physical device: Xiaomi 23124RA7EO, Android 15 / API 35. This is a different phone from the preceding Samsung QA run.

## Reproduced and fixed

1. Gemma E2B's image load failed with `VISION does not support GPU in this artifact`. Native metadata inspection of its verified 2,588,147,712-byte artifact (`181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`) reports text backends `CPU,GPU`, vision backend `CPU`, and audio backend `CPU`.
2. Engine preflight now selects the image backend from artifact metadata, independently of the text decoder. GPU remains preferred when supported; CPU-only image encoders use CPU. Unsupported configurations still fail closed. Qualification records the actual selected image backend, including `TEXT_CPU;IMAGE_CPU`, and remains tied to the exact artifact/runtime/input contract.
3. After encoder repair, E4B answered diagnostic images but its normal chat request hit the generic 90-second interactive limit. Default local image-only, tool-free turns now receive a bounded 240-second run deadline. Remote/text/tool-enabled turns, injected custom limits, existing guards, and delegated/non-interactive work retain their prior bounds. All other token, repetition, tool, UI-action, thermal, and cancellation controls remain owned by the existing guard.

## Device evidence

The explicit instrumentation entry point is `com.halo.app.capture.ImageQuestionPhoneTest`. It generates only synthetic images, checks a random word/number probe, and checks red/blue answers. It exercises media ingestion, preparation, object permission, exact routing, and real provider inference. Separate chat methods use the production `CognitiveSession` in a new named QA conversation with personal context disabled; they restore the original conversation, VISION binding, and cloud-consent settings afterward.

| Check | Result |
| --- | --- |
| OpenRouter free vision, red and blue images | Both correct |
| OpenRouter free vision through chat, first run | Correct |
| Gemma E2B random image probe, red and blue | All correct |
| Gemma E4B random image probe, red and blue | All correct |
| E2B chat after process restart, final APK | Correct; about 57 seconds for the question |
| E4B chat before deadline fix | Failed at about 93 seconds with the interactive timeout response |
| E4B chat after deadline fix, final APK | Correct; about 113 seconds for the question |
| Final OpenRouter rechecks | Blocked by `SocketException: Connection reset` during TLS handshake, before HTTP; waking the phone and retrying did not resolve it |
| Alternate Gemini / OpenAI chat checks | Authentication unavailable on this phone; no successful inference |

Cloud's successful model was `minimax/minimax-m3.1-flash:free`. The final TLS failures are not a passing cloud acceptance result; the earlier successful image and chat runs are preserved separately. No TLS/certificate checks were weakened and no credentials or network settings were changed.

Gemma E4B's verified artifact is 3,659,530,240 bytes, SHA-256 `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`. Both Gemma variants retained valid qualification across process restart. These small English synthetic fixtures establish this device/artifact image path, not general visual accuracy, every language, other phones, or GPU text performance.

Content-free stage/answer-verdict evidence, including the initial failures and later fixes: [phone results](HALO-IMAGE-QUESTIONS-2026-10-22-phone-results.json). The initial all-model inventory export was stopped after locating the installed Gemma files; it is not claimed as completed inventory qualification.

## Host verification and installed artifact

Final `haloVerify`: **2,350 tests, zero failures/errors/skips within result-bearing tasks**, including local-provider suites and the previously excluded model-profile migration test. Unchanged Gradle tasks reused valid results. [Host counts](HALO-IMAGE-QUESTIONS-2026-10-22-host-results.json).

The backend regression and 3 deadline regressions passed. Deadline tests verify the finite four-minute ceiling, unchanged other limits, remote/text/tool/non-interactive exclusions, custom ceilings, and inability to extend an existing run. Localization: 2 Python tests passed and 3,728 EN/RU/LV strings current. `git diff --check` passed. Android lint is outside `haloVerify` and is not claimed as passing.

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8'
./gradlew.bat haloVerify --continue --no-daemon --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8'
```

Run individual phone methods only, with explicit opt-in. Example local chat check (requires an already qualified installed artifact):

```powershell
adb -s <serial> shell am instrument -w -e class com.halo.app.capture.ImageQuestionPhoneTest#localGemmaChatImageQuestion -e allowImageInference true -e modelId gemma-4-e2b -e runId <unique-id> -e sourceStamp <source-id> com.aistudio.braintool.xkqzps.test/androidx.test.runner.AndroidJUnitRunner
```

For first local qualification use `#localGemmaImageQuestions`. For cloud use `#cloudImageQuestions` or `#cloudChatImageQuestion`, add `-e allowCloudMedia true`, and optionally supply `providerId`/`modelId`; the default chooses an advertised free OpenRouter image model. Tests never change the saved chat model or invoke tools. The chat checks leave labeled synthetic QA conversations for review.

Final app and test APKs were installed with `adb install -r`, preserving app data. The original selected provider/model and sharing settings were restored; paid-media consent remains off. The final installed `base.apk` matches the built APK:

- Local ignored checkpoint: `releases/checkpoints/halo-image-questions-2026-10-22-debug.apk`
- Bytes: 285,322,890
- SHA-256: `bedb930ec2f1d9b92abfcffb3de3d971f2fc1c69eb822ef483ec246f1b199832`

Earlier backend-only image runs used APK SHA-256 `08d57b84f39a47a4aba13d036f2119801da7ccffbd6b5782ea9cee4c90d3479d`; final chat runs use the artifact above. No hosted CI was requested.
