# Testing map and acceptance

Status: CURRENT SOURCE MAP / DESIGN TARGET. Reviewed 2026-09-18.

Test sources exist across runtime, providers, drivers and app.
[drivers/simulator](../../drivers/simulator) provides simulator helpers.
[App build configuration](../../app/build.gradle.kts) declares Robolectric, Roborazzi
and Compose testing dependencies. Presence is not proof of complete coverage or passing tests.

See [CI](CI_CD.md) for declared commands and gaps. No fresh tests were run for this
documentation correction.

Acceptance targets: contract/cancellation tests, provider wire fixtures, simulator flows,
UI state/accessibility testing, Android lifecycle/permission/audio instrumentation and
physical glasses acceptance. Synthetic inputs validate code, not hardware truth.
Record actual command, commit, environment and result for every verification claim.


## UI visual regression

The app module now contains a deterministic Roborazzi visual-language contract:
`app/src/test/java/com/halo/app/ui/HaloVisualLanguageSnapshotTest.kt`.

It renders the shared workspace primitives and Halo Presence in both explicit light and dark
themes. It intentionally avoids booting the runtime graph so visual regressions can be isolated
from provider, permission and hardware setup.

Record baselines:

```bash
./gradlew :app:recordRoborazziDebug
```

Verify against recorded baselines:

```bash
./gradlew :app:verifyRoborazziDebug
```

Normal app unit tests, including `AppearancePreferencesTest`, remain under:

```bash
./gradlew :app:testDebugUnitTest
```

A committed test or dependency is not evidence of a passing run. Record the command, commit,
environment and generated diff/result before marking visual acceptance complete.


## UI action wiring regression coverage

The app now includes:
- `PermissionsScreenTest`: verifies the visible microphone grant action invokes the host callback.
- `RuntimeAiPolicyTest`: verifies persisted policy keys map to the runtime AiContextPolicy.
- `CognitiveSessionToolTest.configured default policy is used when submit omits policy`: verifies
  UI-configured default policy reaches context/tool assembly rather than falling back silently.

These complement the source audit; they do not replace device-level interaction testing.


The interaction-wiring acceptance rule is now: a visible interactive control must have a real
host/ViewModel/runtime action. If the runtime capability is absent (for example memory context or
agent mode in the current session path), the control stays hidden instead of shipping as a placebo.
