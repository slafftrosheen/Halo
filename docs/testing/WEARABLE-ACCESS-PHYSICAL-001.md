# WEARABLE-ACCESS-PHYSICAL-001

Consolidated FEATURE-072 acceptance report, 2026-10-08.

**Software: PASS. Physical acceptance: FAIL (incomplete; blocked before execution).**

876 unit tests passed, with zero failures, errors or skips. Phone, Wear and opt-in instrumentation APKs assembled. The final Home UI packaging build passed. The phone APK installed with existing data preserved and MainActivity started (process observed). No inference/role/model benchmarks were run.

Android rejected installation of the new test package with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`, including a user-requested retry and explicit test-APK flag. No instrumentation runner is installed. The APK was copied to phone Downloads as `Halo-Wearable-Access-Physical-001.apk`; direct installer intent was unavailable. User approval through Files remains necessary. **No physical audio suite was executed.**

The allowed report statuses have no NOT_RUN/PENDING value. FAIL rows below therefore mean an unfulfilled acceptance criterion caused by this blocker or absent observation; they are not observed audio malfunctions. No phone-leak or acoustic result is invented.

## Inventory and boundaries

Phone: 23124RA7EO, Android API 35. Baseline Halo commit: `bb27bc5765e03ddf6df66baed8b73cd2c6dfa852`; candidate working-tree APK hashes are recorded in the machine report. Halo is the default assistant. Sony WH-CH520 is exposed as available A2DP output plus SCO input/output. No saved Halo devices were found. No BLE Audio, wired/USB, separate output-only, HeyCyan or Wear OS hardware was reported or connected.

Initial phone/default-assistant inventory preceded changes. The sanitized available-port and saved-device snapshots were refreshed after phone update/startup and before any physical audio operations; this timing is explicit in inventory.json. Port IDs are digested; raw addresses are not stored. Available communication-device enumeration through the app harness remains unexecuted. Platform port inventory is not evidence that a person heard speech.

## All physical cases

| Case | Path | Status | Evidence or missing criterion |
|---|---|---|---|
| A | Phone-only baseline | FAIL | Acceptance not executed: physical-test APK installation blocked with INSTALL_FAILED_USER_RESTRICTED; human acoustic/interaction observations also absent. |
| B | Phone mic + ordinary Sony output | FAIL | Acceptance not executed: physical-test APK installation blocked with INSTALL_FAILED_USER_RESTRICTED; human acoustic/interaction observations also absent. |
| C | Sony headset microphone + same-headset reply | FAIL | Acceptance not executed: physical-test APK installation blocked with INSTALL_FAILED_USER_RESTRICTED; human acoustic/interaction observations also absent. |
| D | Output-only physical accessory | NOT_AVAILABLE | Only the Sony WH-CH520 combined headset was reported; no separate output-only unit. |
| E | Disconnect during speech; stop/no phone leak | FAIL | Acceptance not executed: physical-test APK installation blocked with INSTALL_FAILED_USER_RESTRICTED; human acoustic/interaction observations also absent. |
| F | Native Sony assistant gesture | FAIL | Acceptance not executed: physical-test APK installation blocked with INSTALL_FAILED_USER_RESTRICTED; human acoustic/interaction observations also absent. |
| G | System media buttons, idle and during speech | FAIL | Acceptance not executed: physical-test APK installation blocked with INSTALL_FAILED_USER_RESTRICTED; human acoustic/interaction observations also absent. |
| H | HeyCyan activation/capture/reply/barge-in | NOT_AVAILABLE | No HeyCyan connected/reported; saved Halo device inventory is empty. |
| I | Audio-only handoff to headphones | FAIL | Acceptance not executed: physical-test APK installation blocked with INSTALL_FAILED_USER_RESTRICTED; human acoustic/interaction observations also absent. |
| J | Explicit return to phone audio | FAIL | Acceptance not executed: physical-test APK installation blocked with INSTALL_FAILED_USER_RESTRICTED; human acoustic/interaction observations also absent. |
| K | Nearby Wear OS surface/response/first-wins | NOT_AVAILABLE | No Wear OS watch or emulator was reported/connected. |
| L | Process restart + rediscovery + HostDefault reply | FAIL | Acceptance not executed: physical-test APK installation blocked with INSTALL_FAILED_USER_RESTRICTED; human acoustic/interaction observations also absent. |
| M | HeyCyan durable continuity after restart | NOT_AVAILABLE | No HeyCyan connected/reported. |

For every audio row, route verification, actual output type, hashed actual input/output ID, phone leak observed, abbreviated interaction ID and unchanged-run evidence remain null until executed. The machine report preserves those fields explicitly.

## One-suite execution

The opt-in `WearableAccessPhysicalTest.consolidatedAvailablePaths` inventories live Android accessories and saved Halo devices before playing audio; it tests phone mic, explicit phone baseline, Android-selected output, every exact available output, and each available accessory microphone/communication path. It clears microphone buffers and saves no captured audio. Playback starts muted and uses the production exact AudioTrack verifier. One intentional handset-speaker baseline is followed by headphone-route phrases. Completed AudioTrack playback is recorded separately from human hearing acceptance.

After installation, run once:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-toolsdb.exe" shell am instrument -w -r -e class com.halo.app.audio.WearableAccessPhysicalTest -e wearablePhysical 1 com.aistudio.braintool.xkqzps.test/androidx.test.runner.AndroidJUnitRunner
```

Retrieve `files/wearable-access-physical-001.json` with run-as and integrate it into this same summary. The runner may finish successfully while individual platform rows report FAIL; inspect those rows. The direct route harness does not establish STT, a normal provider response, native assistant-button delivery, disconnect acoustics, media-button ownership or same-run handoff. Those manual observations belong to A-M in this same suite. Do not relabel the entire suite PASS from installation, inventory, unit tests or the runner alone.

Sony documents an assistant gesture using two presses of its play/call button within 0.5 seconds; Android delivery to Halo still requires physical verification. A single PLAY/PAUSE press is not a substitute for that assistant gesture. [Sony WH-CH520 guide](https://helpguide.sony.net/mdr/2958/v1/en/contents/TP1000777018.html).

## Automated evidence

| Module | Tests |
|---|---|
| sdk/host-sdk | 2 |
| sdk/runtime-sdk | 52 |
| host-android | 20 |
| runtime/cognition | 233 |
| runtime/speech | 3 |
| drivers/heycyan | 206 |
| app | 360 |

Gate: runtime/host SDK, host Android, cognition, speech, HeyCyan, app unit tests; phone/Wear/instrumentation assembly. Serial worker, no daemon/parallel execution, 2 GiB heap. Coverage includes identity without product-name guessing, session-only USB port identity, no-address exact route matching, streamed route retention, legacy WEARABLE decoding, independent presentation/audio handoffs, atomic phone/watch first-wins, bounded controls and nearby-only allowlisted watch transport.

Machine evidence: `.artifacts/wearable-access-physical-001/summary.json`, `inventory.json`, `software-tests.json`, `software-gate.log`, `final-packaging.log`. APK hashes identify the actual artifacts installed/prepared; no hardware acceptance is claimed for the Wear APK.
