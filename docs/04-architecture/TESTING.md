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


## Accessibility interaction checks

`HaloWorkspaceInteractionTest` verifies disabled rows expose no click action, selected interactive
rows report selection plus a click action, and disabled buttons report disabled state.

The visual-language snapshot suite also includes a 1.30x font-scale contract. This is a regression
guard, not proof of full accessibility. Device acceptance must still cover TalkBack traversal,
focus order, 200% font-size behavior where supported, keyboard/IME use, and physical touch targets.


## Recovery regression coverage

This source pass adds/extends:
- `BluetoothPrerequisitesTest`: verifies pre-Android-12 location permission vs Android 12+
  BLUETOOTH_SCAN/BLUETOOTH_CONNECT discovery permissions.
- `HomeViewModelTest.test13_providerDiagnosticProjectsRecoveryState`: verifies provider diagnostic
  state/message reach the Home recovery projection.
- `WhisperModelInstallAndProviderTest`: verifies reloadable STT availability is false without the
  model and becomes true after install + refresh.

These tests cover state projection and wiring, not OEM Bluetooth, permission-dialog, radio or
network behavior. Physical acceptance should still exercise Bluetooth off/on, permission deny/grant,
provider offline/online, speech model missing/install, background/foreground and process recreation.


## Pre-burn-in platform and device coverage

The exact physical/operator procedure is now
[REAL_DEVICE_BURN_IN.md](REAL_DEVICE_BURN_IN.md). Use that matrix instead of ambiguous shorthand
such as "cold start" or "Bluetooth test"; each scenario defines setup, action, expected UI/runtime
state, Logcat evidence and fail criteria.

Additional regression guards in this pass:
- `AndroidPlatformStateMonitorTest`: microphone permission grant/revoke becomes observable after
  explicit platform-state refresh.
- `DeviceAutoConnectCoordinatorTest`: only saved records with `autoConnect=true` and the
  matching `DeviceKind` are selected, and the earlier HeyCyan identity corruption shape is
  normalized.
- `PairingIdentityTest`: pair/re-pair always persists canonical
  `deviceType=HEYCYAN_GLASSES` regardless of display name.

Important runtime contract: `RECORD_AUDIO` is not an application startup prerequisite.
`AudioPerceptionEngine.start()` installs the command/pipeline lifecycle, while Android
`AudioRecord` is created only when a real `StartListening` action occurs. Device acceptance
must therefore verify both sides: Halo remains usable when microphone permission is denied, and a
voice action requests/requires permission before capture.
