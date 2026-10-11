# Local models and skill wiring expansion

Reviewed 2026-10-11. Builds on SKILLS-DAILY-001 and LOCAL-MULTIMODAL-001.

## Shipped behavior

- Skills can create an editable workspace, phone-app, study, or calendar helper. The starter copies its live built-in descriptor's tool identities, requirements, and declared permissions, with risk at least as strict as every source tool. Drafting only produces a reviewable package; installation uses the same strict parser, atomic storage, and disabled enablement as imports. No instructions execute during creation or review.
- Built-in tools retain their canonical skill owner regardless of custom skill ordering. A custom guide sharing a native tool cannot replace its approval policy or revive a disabled native owner. Loading a guide exposes only tools whose canonical owner is currently enabled and available. Custom extension tools retain their own ownership.
- Host readiness is published before model-role checks. Slow provider resolution no longer leaves every skill row in Checking. Disabled skills retain their disabled status.
- On-device models can be filtered by text-only, image, audio, or declared tool capability. Filtering applies to pinned catalogue and imported artifacts, combines with search, and resets list position. Empty states explain the next step. Declared tool capability remains a candidate until the tool loop is proven.
- Local-model setup links directly to the existing audio and vision diagnostics. These diagnostics retain explicit selection, tool-free requests, cancellation, and permission boundaries.
- Missing or damaged artifacts cannot gain ranking or a current generation indicator from old model-ID evidence. Historical diagnostics stay under Details. Model purpose and ordinary installation status are visible without technical logs.
- New labels have English, Russian, and Latvian translations; user names and instructions retain their authored text.

## Official references

[LiteRT-LM Kotlin API at the shipped 0.18.0 tag](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/docs/api/kotlin/getting_started.md) documents multimodal content and tool support. Artifact metadata and device qualification remain separate from generic SDK capabilities.

[Android AppFunctions](https://developer.android.com/ai/appfunctions) requires supported platform functionality, discovery permission, and enabled app functions. Halo continues to resolve actual host-supported interfaces; starter guidance does not invent app capabilities or grant permissions.

## Validation

The final serial gate succeeded: `:runtime:cognition:test :app:testDebugUnitTest :app:assembleDebug`, one worker, no daemon/parallel, 2 GB Gradle heap, normal test heap, Roborazzi recording enabled. Results: 276 runtime tests and 606 app tests, zero failures, errors, or skips; debug APK built successfully. Regression coverage includes canonical native ownership, disabled/permission-blocked tool exposure, extension tool ownership, bounded starter packages, capability filtering, stale generation evidence, and readiness while a provider check is pending.

The first runtime regression used compatibility defaults instead of Android manifest permission declarations; it was corrected to reproduce the shipped per-tool permissions. All 276 runtime tests then passed.

The first full app run found a non-idling layout in the initial editor form; increasing the test heap isolated the layout problem instead of resolving it. A bounded dialog still failed to settle. The shipped editor now uses a full-screen form with Android back handling, a scrollable body, fixed instruction field height, saved editor state, and keyboard spacing. The native-rendered interaction test passed the full edit, review, and explicit install flow; the readiness tests also passed. The complete app gate follows these focused checks.

The full gate log is `C:/Users/slavd/HaloEco/halo-wiring-accepted-build.log`. The skill editor screenshot was inspected and its complete interaction test passed in the full suite. UTF-8 decoding and `git diff --check` passed. ADB reports no attached device, so this revision was not installed on the phone. Native image/audio inference, provider quality, and external action effects remain physical acceptance gates. All changes remain local and uncommitted.
