# A2 refresh, retention and video/audio transfer

Continuation of [A2 transport qualification](A2-TRANSPORT-REPAIR-2026-11-12.md). Phone: Redmi 23124RA7EO, Android 15. Existing app data, keys and Android pairing are retained. The user authorized five-second video/audio test clips of a non-private scene in a quiet area. No cloud submission, source-deletion command, reset, unbind or OTA is authorized or introduced.

## Evidence and root cause

- The vendor's `research/evidence/vendor/Android_SDK_Development_Guide_CN.pdf`, page 13, documents `02 04` and response data type 4 as **unsynced** image/video/record counts. This is not an inventory of all retained originals.
- The opt-in native probe sent the existing inventory query to the exact selected READY A2. It captured `bc410b001d490204000000000000010000` twice, but timed out waiting for `MediaCountsUpdated`. The real production router returned only `DecodedProprietaryFrame` for this known reply. Raw RX evidence remains ignored; the identifier-free count response is a regression fixture.
- `HeyCyanPendingInventoryCaptureTest` reproduced that event loss (one failure among three tests), then passed after the narrow count-routing fix. CRC-invalid, truncated and unrelated-characteristic packets cannot authorize an empty list. Other unknown payloads retain the existing path.
- The earlier fresh JPEG copied successfully, but subsequent manifest HTTP closed without headers and that exact download path returned 404. No source deletion was requested. The new fix does not reinterpret arbitrary EOF as an empty manifest.

## Canonical owners and typed contract

`HeyCyanTransferCoordinator` acquires the existing transfer lease, obtains a fresh selected-device count, and skips Wi-Fi/HTTP only for a valid type-4 reply with all three counts zero. Missing replies retain the normal HTTP path. Device/access changes and cancellation reject the result. The host creates an HTTP client only when a file download actually needs one.

UI and assistant still use the same `DeviceMediaManager` through `HeyCyanCaptureCoordinator`. Existing descriptors are `halo_device_files_status`, `halo_device_files_refresh`, `halo_device_files_import` and `halo_device_files_cancel`; operability metadata uses supported contract-schema version 1 under `halo.devices.files`. This version is the review schema, not a feature revision. The refresh description explains new/unsynced files and the limits of retention evidence. Schemas, exact source-version/file tokens, dispatch/status receipts, no-replay policy, host visibility, Kids/Private/Power and Android approvals remain with their existing owners. Human Wi-Fi peer selection remains an explicit platform/security prerequisite.

The new fresh-count regression covers subscribe-before-query, repeated queries, missing/old replies, invalid reply types/counts, changed selection and cancellation. Coordinator cases cover empty inventory without Wi-Fi, ownership cleanup and the normal path for unknown/positive counts. An additional defect found before clip testing prevented Stop when a successful owned recording start lost its mode reply. Stop now remains available to that controller's owned start; no unowned operation is granted. The recording probe uses a bounded five-second interval and a mandatory stop in `finally`.

## Gates and physical checks

The second red run reproduced nine failures across the new refresh and owned-stop cases. All 233 HeyCyan tests then passed. Both APKs built successfully in 15m46s, and main APK `2d529513331650ae6bd976a7e7f5a179016fd215a3b7564d01ab2df5103e0801` was installed with `-r`, preserving data. The earlier test JPEG still matched SHA-256 `4e48605d022d55bab8345266205c137a7604cae3670e2cd0f5ba99283a21ff64` after installation.

Native five-second recordings then passed on that APK: video inventory changed from 0 to 1 (30.347s test), followed by audio from 0 to 1 while video remained 1 (16.860s test). Raw replies were `bc410500fc4c020102ffff` / `bc4105006c0d020103ff02` for video start/stop and `bc410500dc4e020108ffff` / `bc410500dc0902010cff08` for audio. The vendor guide pp9-10 distinguishes these start/end acknowledgements from busy replies. The router had also been losing these replies, leaving recording controls without updated state. Four capture-driven regressions failed before the fix; all 238 driver tests then passed. The new narrow route preserves unknown/invalid replies, updates only the acknowledged recording state and publishes the existing semantic mode confirmation for the exact SDK session. No new wire command or guessed idle wire value is introduced.

The first combined app/core gate built the wording update but failed the actual registered-feature review: 60 app tests, one failure. It correctly rejected unsupported operability schema version 2. The descriptor is corrected to supported schema version 1; the review now includes its problem list in assertion diagnostics. Final rebuild and rerun remain pending. The opt-in native probe now additionally requires the live start and stop states, with mandatory stop in `finally` even when a start-state assertion fails.

Wi-Fi listing/import of the two clips has not yet run: foreground app changes interrupted navigation before the request. The QA helper now checks foreground ownership before snapshots, taps and scrolling. Raw logs and test media stay local. Final APK identity, clip readback, empty refresh and final reinstall are pending; earlier passing builds are not substituted for these checks. USB mass-storage support is not established, and no USB retention claim is made.

| Workflow | Desktop | Native phone/A2 | Spoken/provider |
|---|---|---|---|
| Refresh after transfer, fresh zero count | New regressions pass | Pending updated APK | NOT_RUN |
| Video/audio recording and selected import | Owned-stop and captured acknowledgement regressions pass; existing streaming/digest gates retained | Both bounded recordings and fresh counts pass; import pending | NOT_RUN |
| Source retention after copy | No source deletion command; no inference from zero count | Earlier JPEG endpoint 404; physical storage retention unknown | Not applicable |

The opt-in native probe requires an explicit expected BLE address and never selects/pairs a different device. Raw logs, identifiers and test media remain ignored local evidence. No GitHub issue is closed by this report.

## Latest verification and reconnect repair

The corrected final gate passed: app 60, HeyCyan 240, Android host 20 and core 850 tests (1,170 total, zero failures/errors/skips). Main APK SHA-256 `a1577d5675ce9b42db1d9d1019283496436d518c6c667ec0708c13f8a0768369` and its test APK installed successfully with replacement preserving app data. The strengthened native recording probe passed for video (27.68s) and audio (18.641s), including actual start/stop states. The device then reported two videos and two audio recordings.

After a normal glasses restart, the real Wi-Fi manifest listed all four authorized test clips. Copying originally reopened a second Wi-Fi session and timed out. This reproduced the browse-to-copy lifecycle defect; the repaired physical run below supersedes that failure.

The installed revision keeps the verified browse connection for at most 60 seconds, bounded by exact BLE transport identity, foreground adult access and Wi-Fi connection state. Import revalidates the manifest on that same connection. Download, cancellation, permission/device loss and idle expiry release the transfer lease; there is no background reconnect. Wi-Fi-off now fails before discovery with localized Android Wi-Fi settings guidance. This is a host lifecycle repair, not a new protocol claim.

Cleanup distinguishes foreground access from ownership of the original BLE connection. Losing foreground access ends an already-owned transfer on that same connection; a replaced or reconnected device is never sent that cleanup command. The new regression covers both cases.

## Completed copy and refresh acceptance, 2026-11-13

The combined gate passed 1,233 tests: application 76, OpenAI 7, OpenRouter 39, HeyCyan 241, Android host 20 and core 850; zero failures, errors or skips. Both APKs built and installed with `-r`. Main APK SHA-256 `acf98a048386389e0a25c5bc7833511761907493a5309c572f31b5b3192e3a2a` was read back from the phone and matched. Settings and credentials were retained.

`A2FileTransferPhysicalTest` passed in 37.168 seconds on the selected phone/A2. It listed and copied exactly the four authorized clips, asserted `sameConnection=true`, closed the transfer connection, and refreshed successfully through a new connection without restarting the glasses. Receipt: `files=4 failures=0`; refresh: `available=0 physicalRetention=UNKNOWN`. The earlier probe timed out before the file operation because instrumentation had not brought Halo into the foreground; the successful run explicitly foregrounded Halo.

| Test file | Bytes | SHA-256 | Local decode |
|---|---:|---|---|
| `20261113131045809.mp4` | 6,447,078 | `a198846d1511abb594641138162816bf9cab3d20f1297d16ec65586a33852514` | Passed |
| `20261113134802806.mp4` | 6,556,037 | `a6b99c113b56409bb674e0289095b1ecef650f802b64ea879b4e0aee5af0b506` | Passed |
| `20261113131124597.opus` | 7,240 | `f595cbfd0202a03fe6edaef539c3bf34cddb93948f9d21ac0eb393e57ba9f9bd` | Passed after QA container wrapping; 3.62 seconds |
| `20261113134904618.opus` | 7,160 | `c4b4855ef8cd481fc99f300e7c7222286c580fd0925e18d46c7f66a140f341f8` | Passed after QA container wrapping; 3.58 seconds |

Every local readback hash matches the phone's saved original. The audio is headerless mono Opus in 40-byte packets. The official app's existing `OpusManager.decodeFile` path explicitly sets `hasHead=false`, sample rate 16000, packet size 40 and one channel. Both real captures match that framing and decode without errors after lossless Ogg encapsulation under [RFC 7845](https://www.rfc-editor.org/rfc/rfc7845.html). The diagnostic wrapping preserves every encoded packet; raw originals remain unchanged. This establishes audio integrity, **not generic Android playback of the raw `.opus` files**. No production audio conversion or playback acceptance is claimed.

Physical originals on the glasses remain **unknown**. The empty unsynced list and previous post-sync HTTP 404 do not prove physical deletion or retention. Halo sent no source-delete command. No USB-storage support is established. Raw captures, audio/video files and QA wrappers remain ignored local artifacts; none were played, uploaded or committed.
