# Issue 66 — readable display documents and peripheral input

Date: 2026-11-11 (Europe/Riga). Baseline: `501a2342438705358838fa7bcdeb9afc608d9f16`.
Extends [batch 001](HALO-DEVICES-066-EXPANSION-001.md); #66 remains open.

## Implemented behavior

### External text display

The shared Android presentation owner now accepts up to 8,000 characters, splits text at word/newline boundaries into pages of at most 400 characters and 12 lines, and bounds a document to 80 pages. Excessive line breaks can exceed that page limit and are rejected. Line endings are normalized; surrogate pairs are not split. Text is not persisted or returned by metadata queries.

Devices provides manual text/dictation entry, explicit Send, a neutral test card, page indicator, Previous/Next and Stop. The owner keeps the current document/page ephemeral. Background, disconnect, configuration change and Stop clear it. Reconnecting never resumes the old text. Drafts and spoken previews clear on the panel's lifecycle/display reset. Fixed controls use EN/RU/LV resources; host outcome text still uses English.

`halo_external_display_page` selects an absolute 1-based page on the exact active token and sourceVersion. Show/list/page/clear share the same owner and expose currentPage/pageCount; stopped state is 0/0. An old page or Stop request cannot affect a replacement document. USER_POLICY confirmation and NEVER_REPLAY apply to every output mutation. Access/disclosure is checked again on Android's dispatch thread after any dispatcher handoff.

This supplies readable text output for the confirmed **XREAL xbx a01+** through Android's generic presentation path. It does not add a vendor USB driver, tracking, stereo, camera, automatic screen mirroring or automatic access to a note. The user or an already-authorized content owner supplies the text. `DISPATCHED` remains an Android submission receipt, not physical visibility.

### USB and input inventory

New portable `PeripheralInventory` and Android `AndroidPeripheralInventory` owners report OS-advertised input roles, source-specific motion ranges and USB interface/endpoint metadata. The inventory refreshes on device events and foreground lifecycle. It handles pen/mouse/touch/keyboard/gamepad/joystick/rotary roles without hardcoding a manufacturer. USB and input endpoints remain separate even if VID/PID or names match.

The ordinary Devices panel hides virtual/internal button devices, shows a compact relevant list, and puts VID/PID and technical ranges/interfaces behind Details. Pressure/tilt text requires a valid advertised stylus-source axis. “Not reported” never becomes “unsupported” or “hardware tested.” Refresh and detail views use this same inventory as the assistant.

No USB connection is opened, HID interface claimed, permission requested, serial read or packet transmitted. Raw OS device paths and input descriptors are not exposed. USB permission, when reported, is only current Android grant state. A display-only connection may legitimately have no USB endpoint.

Bounds: 32 input and 16 USB observations; 16 axes, 8 interfaces and 8 endpoints/interface in each detail response. Counts and truncation flags are explicit. Invalid/nonfinite ranges are omitted and flagged. API 26–28 leave external-vs-built-in input location unknown because `InputDevice.isExternal` is public from API 29.

### Pen, mouse, pan and selection

Input ownership now includes device ID, pointer ID and gesture downTime. New pen contact/hover may cancel unfinished finger/mouse input across devices; unrelated updates and stale UP/CANCEL cannot finish the replacement. Pan and selection use the same identity rules. Gesture cancellation covers disconnect, device change, pause/stop, source/document changes, viewport resize, mode and zoom changes, and releases parent interception.

Only valid pointer-class events enter canvas coordinates. Unknown tool types are not fabricated into finger ink; Android mouse-source UNKNOWN keeps mouse fallback. Invalid/nonfinite coordinates and invalid/out-of-range pressure/angle measurements remain unavailable. Reported calibration can exceed pressure 1.0; no pressure is synthesized from velocity. Historical samples and existing persisted Ink/document ownership remain intact.

The changes apply to the shared Canvas/Studio surface, including the user's Huion Inspiroy 2. They do not qualify a Huion size, pressure resolution, tilt or programmable keys without physical tests.

## Assistant operability evidence

Canonical owners: `AndroidExternalDisplayPresenter`, `AndroidPeripheralInventory`, existing Ink editor/surface.

| Tool | Operation | Selection/version contract | Output truth |
|---|---|---|---|
| `halo_external_displays` | QUERY | Current bounded endpoint inventory | ACKNOWLEDGED metadata |
| `halo_external_display_show` | PRESENT | endpointToken + sourceVersion + supplied text | DISPATCHED |
| `halo_external_display_page` | PRESENT | Active endpointToken + sourceVersion + absolute page | DISPATCHED |
| `halo_external_display_clear` | PRESENT | Active endpointToken + sourceVersion | CONFIRMED dismissal of app presentation |
| `halo_peripherals` | QUERY | Empty closed argument object | ACKNOWLEDGED OS metadata |
| `halo_peripheral_details` | QUERY | Exact token + sourceVersion from inventory | ACKNOWLEDGED OS_ADVERTISED, hardwareQualified=false |

Both families carry version-1 ToolOperability metadata and TEXT/VOICE_FINAL declarations. Discovery descriptions explain ambiguity, source identity, limitations and no implicit connection. Runtime descriptors register through existing DEVICE/DEVICE_INSIGHT kinds and capabilities `host.display.external` / `host.peripherals.inspect`. No new runtime execution path is introduced. Read-only peripheral operations preserve READ_ONLY/SAFE semantics. Output mutations retain the normal timeout, confirmation and replay policy.

Both tool families require a current RunToolScope and foreground unlocked grown-up access. Remote reads require the existing acknowledged Power mode and both Screen/Workspace disclosure policy. These tools cannot grant those policies. Peripheral queries recheck access after snapshot acquisition. User content remains governed by its existing source owner before supplying text to Show.

`ExternalDisplayToolsTest` and `PeripheralToolsTest` build the actual registered descriptor catalogue and assert an empty `registry.operabilityReport(...)`. These tests and their extra policy/stale-source regressions are **authored, NOT_RUN** here. A successful source review is not a clean runtime operability report. Human pen/pan/selection gestures remain an explicit physical-input exception; no tool claims to synthesize physical pressure or hardware events.

| Workflow | Typed device | Spoken device | Typed permitted Power | Spoken permitted Power | Restart/readback |
|---|---|---|---|---|---|
| Send instructions and select next page on XREAL | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| List peripherals and read exact Huion axes/USB detail | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Replace display text; reject stale page/Stop; unplug/reconnect | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

## Checks actually executed

- Display/adapter portable suite: **21 PASS** (includes 8 additional pagination and active-document cases).
- Ink ownership/axis portable suite: **7 PASS**.
- Peripheral inventory portable suite: **13 PASS**, including default-count truncation regression found in review.
- Real Android display, ink and peripheral owner source slices compiled with Kotlin CLI 1.9.24, bundled coroutines and the Android 14 `android-all` API jar. This is **not** the project's Kotlin 2.2.10/SDK 37 build, an APK, Compose compilation, or a hardware test.
- EN/RU/LV resource XML/key/format checks and whitespace checks passed. Independent source review covered both Devices panels and gesture changes. The reused Devices-panel proof caught a heading-weight deviation; it does not prove rendering, focus, screen fit or large-font behavior.
- Full Gradle build, Robolectric registry/tool tests, Android instrumentation, actual STT/provider workflows and hardware tests: **NOT_RUN**. No configured SDK 37/device exists in this workspace. No Actions/CI added or invoked.

Reproduce portable checks with `KOTLINC` set if needed:

```sh
bash tools/wearables/check-device-expansion.sh
bash tools/ink/check-input-contracts.sh
bash tools/hardware/check-peripheral-inventory.sh
```

Local consolidated gate after pulling:

```sh
./gradlew :app:testDebugUnitTest --tests com.halo.app.di.tools.ExternalDisplayToolsTest --tests com.halo.app.di.tools.PeripheralToolsTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.halo.app.ink.AndroidInkInputTest
```

Physical acceptance belongs in [WEARABLE_HIL_MATRIX.md](../hardware/WEARABLE_HIL_MATRIX.md). Start with the available COLMI A2 regression; then XREAL video/USB enumeration separately, and Huion input axes plus concurrent touch, pan, selection, unplug and restart. Record the real host/hub/cable/firmware/build. Do not mark a device tested from advertised descriptors.

Primary Android references inspected: [InputDevice](https://developer.android.com/reference/android/view/InputDevice), [UsbManager](https://developer.android.com/reference/android/hardware/usb/UsbManager), [UsbDevice](https://developer.android.com/reference/android/hardware/usb/UsbDevice), [Presentation](https://developer.android.com/reference/android/app/Presentation), [MotionEvent](https://developer.android.com/reference/android/view/MotionEvent).
