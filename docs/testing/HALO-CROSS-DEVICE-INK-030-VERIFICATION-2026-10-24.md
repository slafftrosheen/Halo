# Cross-device Ink #30: source and verification

Baseline: `32f02942e337dccce29aa3fb9c855b3b0777ecb0`, fetched with 0/0 divergence
before edits. Human decision: implement source now, keep activation gated. The
later human instruction explicitly requests compilation and a complete open-issue
verification pack. #30 physical acceptance remains separate from source delivery.

## Implemented source

- Portable device-registry `InkEndpoint`/trusted certificate association, separately
  identified input/display/processing session owners, bounded versioned envelopes,
  full-document checkpoints, ephemeral previews/heartbeats and source selections.
- `DeviceSessionManager.ink` owns explicit association/start/stop/forget and
  rechecks activation, certificate, device, session, interaction, target, lifetime,
  timestamps, sequencing/rate and current source. It has no tool dispatcher.
- Host-owned, pinned mutually authenticated TLS 1.2/1.3, bounded frames and socket
  deadlines/cancellation. BuddyBot's unauthenticated WebSocket is not reused as
  secure ink. Public pairing and connection files require explicit local actions;
  no discovery injection, vendor cloud, functional server or automatic reconnect.
- Existing Canvas transaction atomically compares source revision, rejects local
  recovery conflicts, saves the canonical sketch and retry receipt. Same retry
  returns its original revision; conflicting sequence/content does not overwrite.
- Android non-exportable protected identity, exact key-alias selection, paired-file
  Devices flow, same Canvas stylus editor, host-scoped atomic local recovery,
  explicit review before syncing recovered content, observable connection states,
  accessible Stop and background disconnect. Canvas renders bounded temporary ink
  separately from saved strokes. Local completed edits persist before transmission.
- Windows WM_POINTER pen and explicit read-only Linux evdev adapters plus JNI/CMake
  source. No remote HID injection, shell action or vendor protocol. Linux dropped
  input cancels/resynchronizes; unsupported axes remain absent. These native
  adapters are SOURCE_ONLY until actual toolchain/hardware verification.
- `halo_ink_status/document/start/stop/selection` share the domain owners and
  current ToolExecutionContext. Start requires direct authority and mandatory
  approval. Pairing is a protected human security exception. Runtime/bundled skill
  metadata, Home discovery, WORKSPACE privacy provenance and bounded output
  budgets align. Region references never authorize tools or cloud transfer.

## Review and regression scope

Independent source review identified and corrected cancellation cleanup under
owner-lock contention, accepted-socket handoff ownership, local recovery/CAS races,
terminal collector lifetime, host-scoped recovery, output truncation and privacy/
skill registration. Final follow-up: CLEAR before the first combined gate.

The complete production/regression source batch preceded the gate. New cases
exercise protocol corruption/quotas, gate/auth/source/session/interaction guards,
preview ordering/rate/expiry, revision conflicts, canonical recovery and atomic
receipt rollback/reopen, TLS identity/authentication/cancellation, durable local
files, cross-host isolation, exact publisher retry/receipt handling, registration/
approval/privacy and maximum valid selection output.

Serial validation uses one worker and 3GB. The first gate established SDK21 and
runtime provenance2 passing cases. Six app fixtures initially attempted to create
empty new sketches, which the canonical owner correctly rejects; fixtures now use
real original strokes and assert their preservation. A subsequent gate was
cancelled to correct local-save lifecycle and exact remote receipt tracking. The
next attempt exposed a JUnit non-void test signature, corrected to explicit Unit.
One build process later ended without a terminal result; its log is retained as
incomplete evidence. These attempts do not prove APK completion.

The focused gate completed successfully in **16m55s**: **19 host + 40 app cases
PASS**, zero failures/errors/skips, and both APKs assembled. The unchanged 13
SDK/runtime witness inputs were rehashed before reusing their 23 passing cases:
**82 regression cases total**. The exact log/XML/witness is retained in
`artifacts/CROSS-DEVICE-INK-030-2026-10-24/final-gate/`.

The first exact installed APK pair was read back byte-for-byte on phone
`f01fe673`, model `23124RA7EO`, API35, and launched. The complete native suite
executed 35 cases: **34 PASS, 1 FAIL**. Workspace7, notes/Study4, display9 and
setup/privacy3 passed; the ink group had 11 passes and one protected-key TLS
handshake failure. Raw evidence and that binary's manifest are preserved in
`artifacts/CROSS-DEVICE-INK-030-2026-10-24/phone1-protected-key-digest-failure/`.

Root cause: protected RSA keys omitted authorization for the TLS stack's
prehashed/raw signing operations. The correction adds DIGEST_NONE and raw padding
while retaining SIGN/VERIFY purposes and non-exportable keys. The native fixture
now checks both prehashed PKCS#1 and raw private-key signing before actual mutual
TLS. Both corrected APKs rebuilt successfully in **3m12s** and were installed,
read back exactly and launched after a transient USB disconnect during the first
readback attempt. The complete corrected native suite passed **35/35**: ink12,
workspace7, notes/Study4, display9, setup/privacy3. No skips or failures.
Evidence: `artifacts/CROSS-DEVICE-INK-030-2026-10-24/phone2-corrected-pass/`.
The initial digest-only rebuild was cancelled before completion when the Android
35 source confirmed the additional raw padding requirement.

References: [Android key digest authorization](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setDigests(java.lang.String...))
and [Android 35 Conscrypt delegated RSA signing](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-constraintlayout-release/android-35/com/android/org/conscrypt/CryptoUpcalls.java).

The fixture-only `RemoteInkPhoneTest` uses temporary key aliases and loopback,
then deletes only its own keys. It proves no physical pen, second host, production
activation, actual speech, provider transfer, latency, battery or platform support.
Final source stamp: `d7f63af55a3888c197abd45992984b26694c110076c6e28fcb189b43c351012a`.
App SHA256: `0b8c192d36a938f87da3e31d115f672e08e5b92d00f6f07cfee5066fa2bf219d`.
Test APK SHA256: `d7e31faa3f5486ae35d6240591cdd75ef1f8f79d786af4051f384a823431d889`.
The source witness, build log and binary manifest are retained under
`artifacts/CROSS-DEVICE-INK-030-2026-10-24/corrected-android-build/`.
Only AndroidInkIdentity and its native regression changed after the 82-case
regression gate; all other 56 witnessed inputs were rehashed unchanged. The
corrected key paths passed the real Android native run rather than desktop mocks.

## Operability acceptance (template applied)

Canonical owners: DeviceSessionManager/RemoteInkSessionOwner,
CrossDeviceInkController and NotebookCanvasRepository. ToolOperability v1 covers
query, create-session and stop; closed arguments, exact expectedRevision, bounded
JSON results and session/source IDs. The actual registration report is tested.
Start is SENSITIVE/ALWAYS/NEVER_REPLAY; queries are read-only and WORKSPACE-scoped;
stop is safe/reversible. Existing Kids/Private/Power/OS/runtime approval owners
remain in charge. Local security approval and real pen gestures are explicit
human exceptions, not simulated assistant operations.

| Workflow | Typed native | Actual spoken native | Permitted Power | Durable/source readback |
|---|---|---|---|---|
| Exact trusted-device session start/stop | NOT_TESTED | NOT_TESTED | NOT_TESTED | Desktop fixture only |
| Remote selected region in exact authorized interaction | NOT_TESTED | NOT_TESTED | NOT_TESTED | Desktop fixture only |
| Draw/erase, interrupt, explicit conflict/recovery | NOT_TESTED | NOT_TESTED | NOT_TESTED | Desktop fixture only |

Full acceptance protocols and every original requirement are retained in
[the all-open-issue pack](open-issues-2026-10-24/README.md). Keep #30 OPEN until
paired tablet/second-host, physical pen/eraser, same-interaction speech/selection,
all lifecycle/conflict/authentication cases and actual latency/memory/loss/battery
and platform compatibility evidence satisfy the original issue.
