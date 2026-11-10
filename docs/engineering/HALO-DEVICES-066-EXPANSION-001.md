# Issue 66 — device adapter, external display and pen batch

Implementation date: 2026-11-10. Baseline: `d66ac7e5f9319ebcc4ff5c714e79dfd84c52855c`.
Issue: https://github.com/slafftrosheen/Halo/issues/66

This is an implementation batch within the broadened issue. Issue 66 remains open.
An Android presentation endpoint is not proof of XREAL identity or physical readiness.

## Hardware targets and actual coverage

| Hardware available to Slaff | This batch | Still unverified or unimplemented |
|---|---|---|
| COLMI A2 | Preserves the existing exact COLMI_A2 → HeyCyan family alias and existing readiness gates. | A2-specific GATT/firmware evidence and physical regression. No new COLMI wire protocol is claimed. |
| XREAL **xbx a01+** (https://www.xreal.com/xbxa01) | Standard Android presentation-display listing, explicit selection, neutral test card, bounded assistant text cards and Stop. | Actual video output on the host/cable/adapter; USB identity, vendor controls, tracking/pose, stereo, audio and camera. This is not an XREAL-specific driver. |
| Huion Inspiroy 2 (size not specified) | Stroke ownership includes Android device ID; a second input device cannot append/finish/cancel it. Device removal/change cancels unfinished ink, and removes its hover suppression. Shared Canvas/Studio surface registers the input listener. | Physical pressure, tilt, hover, barrel button and mapping per tablet/host. No guessed VID/PID, pressure maximum or S/M/L qualification. |

The existing EyeVue/TuneBuds host now uses an explicit adapter registry. Duplicate and UNKNOWN registrations fail. An unsupported family cannot fall through into the TuneBuds factory. `NativeDeviceDriver` is the shared session boundary; family-specific media workflows remain on their existing concrete driver. Existing wire encoders/decoders were not changed. Retry eligibility uses the same registry in the Devices list and auto-connect coordinator.

## External display behavior

`AndroidExternalDisplayPresenter` is the canonical host owner. It implements the portable `ExternalDisplayOutput` contract. Both Devices UI and assistant tools call it; the runtime has no Android imports.

- `DisplayManager.DISPLAY_CATEGORY_PRESENTATION` supplies eligible endpoints. Names are bounded data, never driver selectors. The phone's default screen is excluded.
- At most eight displays are listed. No first-device default, scanning command, automatic mirroring or automatic output.
- Tokens are random and scoped to the current connection. Disconnect, display-change callbacks, background and process death invalidate them. A reconnect that reuses Android's numeric display ID gets a new token.
- Cards contain 1–400 characters and are submitted only while Halo is resumed, unlocked and outside Kids mode. The user selects a display in Devices before sending the neutral test card. Fixed panel labels/test cards have EN/RU/LV resources; host status messages currently use English.
- Leaving the foreground dismisses the presentation and clears its selection. Enabling Kids mode clears output. Resume/reconnect never restores content.
- Output is a plain high-contrast text presentation. No chat history, screen capture, private document, or persisted card is automatically attached. Submission reports `DISPATCHED`; it does not attest that a wearer saw the pixels. Stop confirms the app dismissed its own presentation, not panel power-off.

Android references:
- https://developer.android.com/reference/android/app/Presentation
- https://developer.android.com/reference/android/hardware/display/DisplayManager

## Assistant operability review

Feature: external text display. Shared owner: `AndroidExternalDisplayPresenter`.
Actual composition: `ManualDependencyGraph` → `HostTools` additional executor → `ExternalDisplayTools`; `ExecutorKind.DEVICE` → `HaloToolDescriptors.device`; capability publisher → `host.display.external`.

| Registered tool | Operation | Arguments | Receipt |
|---|---|---|---|
| `halo_external_displays` | QUERY | Empty closed object | Bounded endpoint list, foreground, activeToken, sourceVersion; ACKNOWLEDGED |
| `halo_external_display_show` | PRESENT | endpointToken, sourceVersion, text | endpointToken, resulting sourceVersion, status/message; DISPATCHED |
| `halo_external_display_clear` | PRESENT | Current activeToken supplied as endpointToken, sourceVersion | endpointToken, resulting sourceVersion, status/message; CONFIRMED dismissal |

All have version-1 ToolOperability metadata and typed/text + VOICE_FINAL channel declarations. Output schemas are bounded, and write arguments are closed. Show/clear require the exact connection token and current revision. A stale Stop cannot dismiss a newer card. Duplicate/uncertain writes are NEVER_REPLAY; list is read-only. The normal ToolRegistry timeout and USER_POLICY confirmation owners apply. Text is marked sensitive in presentation metadata.

The tools require a RunToolScope, foreground unlocked grown-up access, and the existing acknowledged Power + Screen/Workspace disclosure policy before returning display metadata to a remote route. No new network path or permission grant is introduced. Availability means the Android service exists; it does not mean a cable or external display is connected. Endpoint disappearance returns unavailable; callers list/select again rather than guessing an ID.

`ExternalDisplayToolsTest.registryHasClosedContractsAndNeverReplaysOutput` builds the actual descriptor catalogue and registry, then checks `registry.operabilityReport(...)` is empty. **Authored; NOT_RUN here**, so a clean registered-feature runtime report is still an acceptance gate. Other authored cases cover missing scope, denied access, remote disclosure, stale revisions, oversized/extra arguments, truthful dispatch and stale Stop. Metadata and authored tests alone do not establish spoken or provider parity.

Human pen trajectories are an explicit gesture exception: no tool pretends to generate physical pressure/tilt. Existing Canvas/Studio semantic editing owners remain unchanged. The neutral test-card button uses the same display show operation as the assistant.

Examples: “Which external displays are connected?” → list; “Show ‘Next step: drill the mounting holes’ on that display” → select/approve/show; “Clear that display” → read activeToken/revision, clear. Ambiguous display names require selection; user-editable names grant no authority.

## Verification performed

| Check | Result |
|---|---|
| `tools/wearables/check-native-sessions.sh` | **PASS, 13 checks**; existing EyeVue/TuneBuds handshake, timeout, readiness, capture and receipt regressions |
| `tools/wearables/check-device-expansion.sh` | **PASS, 13 checks**; explicit registration, unknown/duplicate rejection, selection freshness, disconnect/reused ID, background, changed display and bounded inventory |
| Android display owner + portable display model compile | **PASS** with Kotlin CLI 1.9.24, bundled coroutines and Android 14 `android-all` API jar |
| Android ink adapter + real InkDocument/InkEditor/InkCodec/InkSelectionContext compile | **PASS**, same compiler/API jar |
| EN/RU/LV resource XML and diff whitespace | **PASS** |
| Devices panel independent source review | **PASS within source scope**. Temporary review copy caught a planted heading-weight deviation; this does not establish rendered spacing, focus or large-font layout. |
| Full app/Gradle compile, new Robolectric tool tests and actual registered-feature report | **NOT_RUN**; this workspace has no configured Android SDK 37/build environment. Isolated API compilation is not an APK build. |
| Android motion-event instrumentation | **NOT_RUN**. Added cross-device pointer collision and removal cases in `AndroidInkInputTest`. |
| Real COLMI/XREAL/Huion and actual STT-final/provider behavior | **NOT_RUN** |

The project uses Kotlin 2.2.10/Android compile SDK 37. The isolated lower-version compiler/API checks above prove only the listed source slices; they do not replace the project's Gradle gate. No CI/Actions added or invoked.

## Local acceptance handoff

Run one focused local gate after pulling, using the project's configured SDK:

```sh
./gradlew :app:testDebugUnitTest --tests com.halo.app.di.tools.ExternalDisplayToolsTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.halo.app.ink.AndroidInkInputTest
```

| Workflow | Typed on device | Spoken on device | Typed permitted Power | Spoken permitted Power | Restart/readback |
|---|---|---|---|---|---|
| List, select and show on XREAL | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Replace card; reject stale Stop; stop current card | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Disconnect/background/reconnect; reject old token | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

1. Connect xbx a01+ to a host with working video output. Devices → External display → select the observed endpoint → Show test card. Record the actual host/cable/adapter and whether the wearer sees it. A missing endpoint means the Android presentation path is unavailable; do not report a successful connection.
2. Repeat with two displays, swap/reconnect, change configuration, background/foreground, rotation, lock and Kids mode. Confirm no output is restored automatically and old tool tokens/revisions fail. Process death must not restore the card.
3. With Huion, draw pressure/tilt/button/hover samples in Canvas and Studio; unplug mid-stroke and verify no partial stroke is committed. Use touchscreen/mouse concurrently and confirm pointer-ID reuse does not splice strokes. Record S/M/L and actual Android input ranges before claiming qualification.
4. Regress COLMI A2/HeyCyan and the pre-existing EyeVue/TuneBuds routes separately. Transport connection alone must never count as READY.

Remaining expansion work includes the evidence-backed vendor adapters already listed in #66, deeper XREAL USB/pose work after exact-device evidence, and physical qualification. Do not close #66 from this batch's portable checks.
