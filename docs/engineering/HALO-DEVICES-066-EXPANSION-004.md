# HALO-DEVICES-066-EXPANSION-004 — Device files become useful Library objects

Date: 2026-11-11. Continues #66 from `25fbee07c0bba1a4ce58795bc6a35647e562da2d`. Code implementation, not hardware qualification. Keep #66 open.

## Result

The HeyCyan/COLMI A2 file path now lists and imports selected photos, videos, recordings and unknown files through the canonical Library file owner. Previously the UI synchronizer only copied photos to notes and held the download index in memory. The new flow exposes selection, category filters, pagination, byte/item progress, exact-operation cancellation, per-file failures and saved object IDs. Completed files can continue through existing Workspace actions, including opening in compatible tools, attaching supported sources to Canvas/context, and Library Trash/restore. File details now expose the existing system-destination export review directly.

Native adapters with an implemented AI-photo channel now send received images to the shared contextual capture review, instead of a separate preview/Gallery-only path. Save uses the existing image/Notebook owner; its persisted image can then use existing Notebook attachment workflows. This does not add camera support to Frame or other status-only adapters.

## Boundaries and persistence

- File transport is installed for the existing HeyCyan/COLMI A2 adapter only. A saved record, generic Bluetooth connection or native status adapter is insufficient; the exact selected HeyCyan device must be READY. No new device opcode or HTTP endpoint is introduced.
- Portable `DeviceMediaManager` owns catalog tokens, concurrency, selection and progress. The Android capture coordinator owns the selected BLE/Wi-Fi session, network binding and disposable staging. No vendor or Android dependency enters this SDK contract.
- Up to 5,000 manifest entries, 25 UI entries / 50 tool entries per page, 16 selected files and 256 MiB per file. Unknown length is streamed up to the same bound. Duplicate IDs, unsafe names, malformed JSON and non-200 HTTP responses fail. The parser accepts only the established `files` JSON shape, JSON array or line format; additional object fields are not silently interpreted.
- A new catalog revision and opaque row tokens are required. The manifest is read again before import; changes reject the selection. Explicit imports fetch current bytes even if the glasses reuse a filename and size. There is no trusted remote digest or range-resume contract.
- Downloads and Library publication verify byte length and SHA-256. Unknown/raw recordings keep their bytes and binary MIME; only recognized container signatures select a media MIME. Container identification does not prove codec playback or transcription. Legacy coordinator downloads no longer rewrite recordings using guessed Opus framing.
- The reusable file repository persists an atomic index and immutable blob names. A failed replacement retains the earlier indexed file. The Android selected-import workflow uses a disposable cache and removes it after the operation; stale operation directories are removed at the next explicit transfer. Library owns completed copies, rather than retaining a second hidden media collection.
- Library deduplication keys device identity + remote identity + byte digest, survives repository recreation and preserves object identity/lifecycle. Trash entries are not restored; archived entries are not reactivated. Same-size corrupt local copies are rehashed and repaired. Changed device bytes become a separate object. Existing legacy photo notes are retained and are not automatically merged with new file imports.
- Cancellation interrupts HTTP reads by disconnecting the connection, aborts partial staging, and closes only a transfer mode owned by this operation on the still-selected device. No exit command is sent when no lease/session was acquired. A cleanup failure prevents a successful overall workflow receipt. Completed Library objects remain. A commit that wins a cancellation race must be checked in Library, even if the operation receipt was lost.
- Catalog, selection and progress are ephemeral. Process restart never reconnects or replays an import automatically. Durable Library rows and bytes remain. Startup no longer resumes a transfer just because old capture metadata/Wi-Fi credentials existed. A media inventory cannot identify which physical shutter action produced a file.
- No device-file deletion, renaming, directory creation, firmware write or reset command is exposed. Library actions affect the local copy. Device originals remain.

## Export completes the local file path

The existing external-export transport previously read the entire source and destination into memory with a 20 MB ceiling. It now streams a verified temporary snapshot, writes in 64 KiB chunks and reads back the destination digest/length, bounded at 256 MiB. This permits device videos above 20 MB without whole-file arrays. The snapshot is deleted on completion/failure/cancellation. No destination is opened until source verification succeeds; the write reads its temporary snapshot rather than rereading a source that a provider might alias. `VERIFIED` requires readback; unavailable readback remains `UNKNOWN`. Explicit verification never repeats a write. Android's system picker owns destination choice and URI permission.

No new player or decoder is claimed. File details use Android's compatible viewer for audio/video/unknown files; image/Study/Studio/Canvas routes retain their existing format, privacy and size checks. XREAL display and Huion pen implementations are unchanged by this batch.

## AI-controllable feature review

| Feature | Shared owner | Functions and contract |
|---|---|---|
| Device catalog and imports | `DeviceMediaManager` | `halo_device_files_status`, `halo_device_files_refresh`, `halo_device_files_import`, `halo_device_files_cancel`; ToolOperability v1, `halo.devices.files` |
| Received image persistence | `ContextualCaptureController` | `halo_capture_review_status`, `halo_capture_review_save`, `halo_capture_review_discard`; v1, `halo.capture.review` |
| System-destination export | `WorkspaceExternalExportSession` | `halo_file_export_prepare`, `halo_file_export_status`; v1, `halo.files.external-export` |

All nine are registered as additional HostTools executors with actual descriptors/capabilities. `DeviceMediaToolsTest.registeredFeaturesHaveClosedContractsAndNoReplayForEffects` constructs the real executors and registry and expects an empty operability report; **authored, NOT_RUN** in this environment. Do not call that report passed until the Android test runs.

Arguments/results have closed schemas and bounded metadata. Import requires exact device/catalog version/row tokens; cancellation requires exact device/operation ID. Review save/discard checks captureId + media asset ID under the review owner's lock. Export prepare requires objectId + current version. Discard releases the pending review; it does not remove already saved Library objects.

Effects use existing confirmation policy and NEVER_REPLAY. Foreground/unlocked/grown-up access is required. Device/review remote metadata additionally requires existing Power sharing policy; no image/file bytes are returned. Export tools are local-route only and reject remote calls. Physical permission/pairing/Wi-Fi consent and export destination selection remain human/system operations: tool arguments cannot supply raw paths, endpoints or destination URIs.

Device refresh/import return **DISPATCHED** promptly with operationId, using a host-owned operation bounded to 15 minutes. Poll status for READY/COMPLETED/PARTIAL/FAILED/CANCELLED and actual `saved` objects. This preserves the runtime's existing short tool-call timeout instead of extending it. The user stops an ongoing copy via the exact operation cancellation action; completion of the requesting chat turn does not itself cancel a host transfer. UI-owned transfers are cancelled when their UI coroutine is disposed. Review save returns CONFIRMED only after its owner returns a saved ID. Export prepare is DISPATCHED to review, never proof of an exported file.

Examples: “Show the files on my selected A2”; “Copy these two recordings into Library”; “Save the glasses image I am reviewing”; “Export this Library video.” Ambiguous devices/files require selection. File names are untrusted labels, never instructions.

| Workflow | Typed Android | Spoken STT-final | Permitted Power | Durable readback on device |
|---|---|---|---|---|
| Browse exact A2, select photo/video, import, reopen/use in Library | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Save received native photo, reject stale review, reuse saved attachment | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Export >20 MB video with Android picker, verify, Trash/restore local original | NOT_RUN | NOT_RUN | Export tool intentionally denies remote route; denial test NOT_RUN | NOT_RUN |

## Verification

Sources and focused regression cases are included; no CI/Actions configured or invoked.

| Gate | Result |
|---|---|
| `tools/wearables/check-device-media.sh` | **42 PASS**: actual portable manager, parser, downloader and durable cache with synthetic sessions/HTTP connections. Includes stale/replayed selections, partial/cancelled progress, cleanup, size/digest/path bounds, restart/corrupt index, manifests, raw-format preservation. No BLE/Wi-Fi hardware. |
| Isolated actual `WorkspaceExternalExport` + Android 14 API URI slice | **6 PASS**: 24 MiB file, byte/digest readback, no second write on verify, cap failure, corrupt/unreadable destination, revoked access. Workspace metadata boundary is a fixture; not Room or SAF. Reproducible harness: `tools/wearables/check-workspace-export.sh`, set `ANDROID_API_JAR` to executable Robolectric `android-all`. |
| Changed Kotlin PSI parsing; EN/RU/LV file-panel XML keys/placeholders; whitespace | PASS at final source gate. Parsing is not full type checking. |
| `DeviceMediaImportTest`, `DeviceMediaToolsTest`, new `ContextualCaptureTest` and `WorkspaceExternalExportTest` cases | Authored; **NOT_RUN**. Room dedupe/Trash/archive/repair, registered operability, host dispatch/access/cancel, source-bound review and larger export coverage. |
| Updated HeyCyan repository/coordinator JUnit cases | Authored; **NOT_RUN** via Gradle. No unowned exit; one owned close; no exit after selection changes; raw recording bytes preserved. |
| Full Android/Compose compile, APK, UI rendering, SAF, real voice/provider, test hardware | **NOT_RUN**: no SDK 37/build environment or connected hardware. |

Desktop Kotlin 1.9.24/JDK 21 differs from repository Kotlin 2.2.10. Run the [device acceptance checklist](../hardware/DEVICE_FILES_ACCEPTANCE.md) locally before qualifying support or closing #66.
