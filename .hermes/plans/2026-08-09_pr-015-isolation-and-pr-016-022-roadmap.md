# PR-015 Isolation and PR-016–PR-022 Roadmap Implementation Plan

**Goal:** Make physical BLE discovery mechanically raw-only and independent from the cognitive runtime, remove blocking construction-time lifecycle work, isolate synthetic protocol fixtures, and lock the evidence-driven hardware roadmap.

**Architecture:** Discovery owns its own `HeyCyanDiscoverySession` and an explicitly selected BLE address. `RealHeyCyanDriver` receives a required connection mode for selected connections: `DISCOVERY` records transport evidence only; `RUNTIME` is the only mode allowed to decode events or send evidence-backed commands. The normal runtime composition root uses the deterministic simulator until a verified physical host can be selected explicitly.

**Constraint:** All implementation and documentation changes are completed before the final test/lint/build pass. No physical protocol behavior is promoted from synthetic evidence.

---

## Phase 1 — Discovery/runtime boundary

1. Add `HeyCyanConnectionMode` with `DISCOVERY` and `RUNTIME` values.
2. Change selected `RealHeyCyanDriver.connect` to require the mode.
3. Start the incoming decoder only in `RUNTIME` mode.
4. Suppress application-level decoded `DeviceEvent.Ready` in `DISCOVERY`; retain transport readiness in the capture timeline.
5. Reject `sendCommand` unless the active connection is `RUNTIME`.
6. Pass `DISCOVERY` explicitly from `HeyCyanDiscoverySession`.
7. Add mode policy tests and update codec tests to use a clearly named synthetic fixture seam.

Files:
- Create: `drivers/heycyan/src/main/java/com/halo/drivers/heycyan/HeyCyanConnectionMode.kt`
- Modify: `drivers/heycyan/src/main/java/com/halo/drivers/heycyan/RealHeyCyanDriver.kt`
- Modify: `drivers/heycyan/src/main/java/com/halo/drivers/heycyan/HeyCyanDiscoverySession.kt`
- Create/modify: `drivers/heycyan/src/test/java/com/halo/drivers/heycyan/ConnectionModePolicyTest.kt`

## Phase 2 — Runtime lifecycle and composition

1. Remove `runBlocking` from `HostFactory`.
2. Make `HostGraph` choose a deterministic `SIMULATOR` runtime host at the composition root for development; discovery still constructs its own physical driver and never touches `ManualDependencyGraph`.
3. Make `HaloRuntime.start()` require an already-connected host instead of selecting/connecting a physical device.
4. Connect the configured runtime host in the application bootstrap before starting `HaloRuntime`.
5. Keep `RealHeyCyanDriver.connect()` without an explicit address as a hard failure so a physical device can never be auto-selected.
6. Add a runtime lifecycle test seam if needed; preserve existing simulator integration behavior.

Files:
- Modify: `app/src/main/java/com/halo/app/di/HostFactory.kt`
- Modify: `app/src/main/java/com/halo/app/di/HostGraph.kt`
- Modify: `app/src/main/java/com/halo/app/HaloRuntime.kt`
- Modify: `app/src/main/java/com/halo/app/MainActivity.kt`

## Phase 3 — Synthetic/verified protocol wall

1. Move synthetic protocol definitions out of production sources into test fixtures.
2. Rename the fixture object to `SyntheticHeyCyanProtocolFixtures`.
3. Keep production `HeyCyanEventDecoder()` and `HeyCyanCommandEncoder()` empty by default.
4. Preserve the explicit command whitelist behavior: unsupported commands fail and no arbitrary raw write is available.
5. Update tests and protocol documentation to state that real captures or authoritative vendor documentation are required for promotion.

Files:
- Move/rename: `drivers/heycyan/src/main/java/.../HeyCyanProtocolDefinitions.kt` → `drivers/heycyan/src/test/java/.../SyntheticHeyCyanProtocolFixtures.kt`
- Modify: `drivers/heycyan/src/test/java/.../HeyCyanCodecTest.kt`
- Modify: `docs/protocols/heycyan/BLE_PROTOCOL.md`
- Modify: `docs/protocols/heycyan/CAPTURE_ANALYSIS.md`
- Modify: `.agents/AGENTS.md`
- Modify: `CONSTITUTION.md`

## Phase 4 — Honest discovery UI and acceptance documentation

1. Rename the screen heading to `BLE PROTOCOL DISCOVERY`.
2. State that the selected device is not assumed to be HeyCyan and that no commands are sent.
3. Update the manual checklist and capture analysis language to match the UI and mode boundary.
4. Add the exact PR-015 acceptance checklist from the review.

Files:
- Modify: `app/src/main/java/com/halo/app/ui/screens/discovery/HeyCyanDiscoveryScreen.kt`
- Modify: `docs/protocols/heycyan/DISCOVERY_CHECKLIST.md`
- Modify: `docs/protocols/heycyan/CAPTURE_ANALYSIS.md`

## Phase 5 — Freeze the next hardware sequence

Document the following sequence in both roadmap locations:

- PR-015 — Physical BLE Discovery
- PR-016 — First Physical Capture and Protocol Analysis
- PR-017 — Evidence-Based Event Decoder
- PR-018 — Evidence-Based Command Layer
- PR-019 — HeyCyan Device Context
- PR-020 — HeyCyan Audio Investigation
- PR-021 — HeyCyan AudioSource
- PR-022 — End-to-End Wearable Speech

Include scope, deliverables, acceptance criteria, what must remain frozen, and the promotion rule. Do not fabricate capture artifacts.

Files:
- Modify: `ROADMAP.md`
- Modify: `docs/14-roadmap/ROADMAP.md`

## Final verification — after all implementation phases

Run only after every code/documentation change above is complete:

```text
./gradlew testDebugUnitTest
./gradlew :drivers:heycyan:testDebugUnitTest :drivers:simulator:test :runtime:cognition:test :runtime:plugins:test :runtime:testing:test :sdk:runtime-sdk:test
./gradlew lintDebug
./gradlew assembleDebug
```

Also verify:

- `runBlocking` is absent from application production sources.
- Discovery explicitly passes `HeyCyanConnectionMode.DISCOVERY`.
- No production source references the synthetic fixture object.
- The debug APK exists and is copied to `releases/halo-alpha-debug.apk`.
- The only expected generated repository change is the rebuilt ignored/release APK artifact according to the project workflow.
- No physical capture files are invented or committed.

## Risks and open hardware dependencies

- No real HeyCyan capture is currently available; PR-016 remains a manual physical-device task.
- The Android emulator cannot validate BLE behavior without a suitable emulator/device setup; unit tests and the simulator cover deterministic development.
- A verified runtime host with an explicitly selected physical address should be introduced only after PR-016–PR-018 produce real evidence.
- Audio transport remains unknown and must not be inferred from BLE control traffic.
