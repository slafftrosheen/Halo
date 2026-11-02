# Issue #59 — source review withdrawal and intent parsing (2026-11-02)

Status: **production code and regression sources committed to main**; Android build, JUnit, native install, actual model acquisition, network-cancellation trace, inference and real spoken turns **NOT_RUN** in this connector-only pass.

## Source-level failures

1. `LocalRolePackInstaller.acknowledgeSourceReview(false)` cleared the stored consent flag but did not stop already admitted starter/optional extension acquisition jobs or manager-owned transfers. A review withdrawal must not silently leave a background download running. We now cancel the two existing owner-scoped jobs and cancel downloads **only using exact existing acquisition-owner tokens**; partial/installed user data is retained. This is a cancellation request, not proof a device network transport stopped without a packet trace.
2. `LocalRolePackInstaller.request(START_OR_RESUME)` could report accepted even when the required Supertonic provider or pinned intent catalogue was absent. The direct and typed starter admission now refuses unavailable mandatory components before scheduling anything; optional conversation extension retains independent availability.
3. `LocalIntentModelRouter.parse` uppercased model-returned English labels using the device's default locale. On Turkish devices `uncertain` becomes `UNCERTAİN` and valid advice is rejected. Parsing now uses `Locale.ROOT`; exact ACTION/ANSWER/UNCERTAIN output grammar and all authorization boundaries remain unchanged.

## Focused test sources (NOT_RUN)

- Consent revocation cancels both owned jobs and retains an existing user file.
- Unavailable starter reports no accepted dispatch even after source review.
- Turkish system locale accepts exact canonical labels and still rejects explanatory prose.

Run locally:
```bash
./gradlew :app:testDebugUnitTest --tests 'com.halo.app.ai.LocalRolePackRecoveryTest' --tests 'com.halo.app.ai.LocalIntentModelRouterTest'
```

**Remaining evidence:** Test cancellation while an actual owned LiteRT download, Whisper/VAD install and Supertonic download are in progress; verify old model/partial files remain; read back durable review flag after restart; observe Local/Power zero-network behavior; test exact resolved model identity on an 8GB Android device. Historical 9,580ms Qwen-classifier failure is unchanged; this patch does not qualify any model or language and does not close issue #59.
