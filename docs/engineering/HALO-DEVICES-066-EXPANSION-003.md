# HALO-DEVICES-066-EXPANSION-003 — Frame status and native operation coverage

2026-11-11. Based on main `4b9c7fbd77de220d7ee4fe40a76d265cdf272ba9`. #66 remains open; this is source implementation, not physical or full application acceptance.

## Change

Added the first Brilliant Labs Frame protocol adapter through the existing native adapter registry, session manager, pairing UI, saved-device lifecycle and status tools. Its actual scope is **hardware identity, firmware and battery**. Brilliant Labs Halo is a different family and is rejected by this handshake. Full Frame display/camera/audio/IMU work remains pending.

- `FrameStatusProtocol`: three fixed vendor-documented Lua read expressions, random per-request nonce, bounded reply parsing across notification boundaries, raw-data separation and strict field validation. No arbitrary Lua API.
- `BrilliantFrameDriver`: READY only after exact `Frame` hardware identity plus firmware and battery responses; no break/reset, file or callback changes. Timeout/cancel/EOF retire the link. Camera/AI-photo capability stays unavailable.
- `BrilliantFrameAndroidLink`: selected BLE address, bond/permission handling, exact GATT endpoints, observed MTU before command transmission, sequential CCCD/ATT operations and stale-callback fencing. A background reconnect without an existing bond fails before GATT.
- `NativeDeviceDriver.supportedActions`: controls and session dispatch use adapter coverage. Frame gets battery/info, without storage/capture/recording/time-sync controls. EyeVue and TuneBuds retain their implemented actions.
- `NativeSnapshot.sourceVersion`: generated on state change and consumed under the workflow lease before explicit requests. Both existing native drivers and Frame reject stale/concurrent observed requests. Unknown battery charging state is no longer converted into a false charging observation.
- `NativeDeviceStatusActions`: common UI/assistant owner resolves only an exact saved, matching native session. No scanning, session creation, connection or pairing from status calls.
- Native retry now consults the same installed adapter registry in saved-device actions, fixing the older HeyCyan/Buddy-only retry list.
- EN/RU/LV explain Frame's present scope and idle-REPL requirement during pairing and control.

Protocol sources and hardware journey: [Frame adapter](../hardware/BRILLIANT_FRAME_STATUS_ADAPTER.md). Compatibility and HIL matrices updated. COLMI A2 remains HeyCyan; XREAL remains the user's exact xbx a01+ with generic Android presentation only; Huion remains Android-delivered input. No additional qualification is claimed for them.

## Assistant operability review

| Item | Contract/evidence |
|---|---|
| Canonical owner | `NativeDeviceStatusActions` → exact existing `NativeWearableSession.driver`; the two UI refresh controls call this owner. |
| Registered functions | `halo_native_device_status`, `halo_native_device_refresh`; HostTools additional executor, DEVICE descriptors, capability `halo.devices.native.status`. |
| Metadata | `ToolOperability` v1, QUERY, JSON, TEXT + VOICE_FINAL through the existing runtime. |
| Arguments/results | Closed schemas. Exact deviceId (1–200 chars), UUID sourceVersion, operation enum battery/information. Bounded native metadata and action enum; no raw program text. |
| Discovery | First use `halo_saved_devices`, select exact ID, read native status; then request battery or information refresh with its returned token. Ambiguous devices require user selection. |
| Freshness | Cached query is marked `freshResponse=false`. Explicit correlated read returns `freshResponse=true`, `ActionTruth.ACKNOWLEDGED`; `hardwareQualified=false` always. It does not attest physical acceptance. |
| Source guards | Driver source tokens expire on connect/disconnect/state updates and before request dispatch; double submissions cannot both write. Owner rechecks session identity and access after suspensions. |
| Policy | Foreground, unlocked, loaded grown-up settings; remote calls additionally require existing Cloud/Power disclosure settings. The owner repeats access gates and the driver rechecks inside its workflow lease. Android permissions remain host-owned. |
| Confirmation/replay | Cached read READ_ONLY/NONE/SAFE; explicit BLE refresh EXTERNAL/USER_POLICY/NEVER_REPLAY, 40-second runtime budget, no retry policy. Status tools do not change auto-connect or initiate pairing. |
| Process death | Driver/session revisions are ephemeral. No request is restored or replayed. Existing saved identities and auto-connect remain with their established owners. |
| Exceptions | Android pairing approval is an OS/user gesture. Unsupported Frame capture/display/IMU operations are absent, not represented by navigation or a raw command escape. |
| Registered feature test | `NativeDeviceStatusToolsTest.registeredContractsAndPoliciesAreClosed` calls the real descriptor catalog + ToolRegistry operability report and expects zero findings. Authored; **NOT_RUN** here because the full Android test environment is absent. |

Examples for local acceptance: “What battery level do my Frame glasses report?”, “Refresh the firmware information on this saved Frame”, and “Check this saved EyeVue, then refresh its battery.” Neither synthetic transport checks nor source registration establish actual spoken intent or permitted remote-provider acceptance.

| Workflow | Typed Android | Spoken Android | Typed permitted Power | Spoken permitted Power | Persistence/readback |
|---|---|---|---|---|---|
| Select saved Frame and read cached status | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | Saved identity existing owner; status ephemeral |
| Refresh Frame battery/info using fresh token | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | Ephemeral reply; NOT_RUN on hardware |
| Refresh EyeVue/TuneBuds; reject stale token | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | Ephemeral reply; NOT_RUN on hardware |

## Focused checks

Production source and transport regression cases were written together; no Actions/CI or repeated APK build loop.

| Check | Result |
|---|---|
| `tools/wearables/check-frame-status.sh` | **19 PASS**: fixed commands, exact family/model, reply fragmentation/noise/raw marker, malformed/bounded data, MTU, busy REPL, unsupported operations, version/revocation/concurrency, cancellation/EOF/reconnect. Synthetic links only. |
| `tools/wearables/check-native-sessions.sh` | **15 PASS**: prior EyeVue/TuneBuds checks + consumed source/concurrent request/access guard regression. |
| `tools/wearables/check-device-expansion.sh` | **21 PASS**: registered adapter boundaries and existing display/pagination regressions. |
| Isolated Android source compile | **PASS**: actual Frame link + native contracts/drivers against local Android 14 API jar and coroutines. Not compileSdk 37, full app, Compose, Gradle or APK acceptance. |
| Changed Kotlin syntax parser | **PASS** at focused review; syntax only. |
| EN/RU/LV Frame resource XML/keys and `git diff --check` | **PASS**. |
| New status tool / saved retry Robolectric tests | Authored; **NOT_RUN**. Includes registered operability review, closed arguments, access/disclosure, exact target, stale token, truthful result and native registry retry. |
| Full compileSdk 37 app, APK/device, STT-final, remote provider and BLE/HIL | **NOT_RUN**. No Android SDK 37 or attached hardware here. |

Desktop checks use Kotlin 1.9.24/JDK 21 with the bundled coroutines jar; repository Kotlin is 2.2.10. The API slice and fake links cannot validate phone Bluetooth firmware, bond dialogs, actual Frame REPL behavior or UI rendering. Run the [hardware acceptance cases](../hardware/BRILLIANT_FRAME_STATUS_ADAPTER.md#local-acceptance-still-needed) before calling the adapter qualified. Keep #66 open.
