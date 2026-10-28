# Issue 59 — Private Offline Pack coding record

Baseline: `7257b4706`. Scope: Android source only, per explicit user instruction.
No builds, tests, ADB, downloads, inference, provider calls, benchmarks or physical
qualification were run. No commit, push or issue closure is part of this batch.

Read: issue #59 and predecessor gate #55, `.agents/AGENTS.md`,
`docs/10-features/LOCAL-MODELS.md`,
`docs/engineering/HALO-MODELS-039-CURATED-SETUP.md`,
`docs/04-architecture/PRIVATE-POWER-MODES-IMPLEMENTATION.md`, BrandBook v4
Android implementation notes, and existing model/speech installer owners.
The user's instruction to implement the other issues authorizes this source
work ahead of physical alpha acceptance; it does not qualify either issue.

## Implemented source

`PrivateOfflinePack` projects the existing canonical Qwen intent, Whisper Small,
Silero VAD and Supertonic archive pins into a versioned manifest. It reports exact
file names, sizes and hashes, original sources/revisions when provided, declared
license when present, runtime declarations and upstream language declarations.
The fingerprint covers all these fields. Missing speech license/revision metadata
stays unknown. This is an acquisition manifest, not an audited redistribution
license, backend measurement, language recommendation or newly invented artifact.
CONVERSATION remains explicitly absent from the combined starter download.
An optional SmolLM2 360M conversation extension is exposed in the same curated
model list and manifest, using the already pinned Apache-2.0 source artifact
inspected in `LOCAL-MODEL-ROLE-002.md` (revision
`507c99cfe6541ba2bcd84818786f7b025935e5e1`, 373719040 bytes, SHA-256
`8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e`).
It remains UNQUALIFIED: independent explicit acquisition through the existing
LiteRT manager, no automatic starter download, role assignment or language/device
recommendation. Existing cancel/pause/resume/discard/retry controls apply.

Optional CHAT acquisition also has full typed/voice parity through the existing
setup owner. The existing `halo_local_setup_start`, `_pause`, `_rollback` and
`_keep_files` commands accept optional `scope=CONVERSATION_EXTENSION` (default
STARTER preserves existing callers). Status includes the extension's own exact
observed `transactionId`, physical identity, phase, remaining byte/space estimate
and recovery state. The same LocalRolePackInstaller persists a bounded ownership
receipt and requests the existing LiteRT downloader; it never selects or loads
the artifact. START covers initial download, resume and retry. Pause cancels only
owner-tagged transfer work and keeps the partial. Rollback delegates checksum,
selected/in-use and acquisition-owner protection to the manager, retaining
independent files. Keep-files clears only this receipt. A corrupt/stale receipt
requires explicit keep-files recovery; it cannot authorize deletion or restart.
Settings and Speech onboarding call these exact owner operations for the optional
extension, with explicit download/space disclosure and sufficient-space admission.
Existing command descriptors retain their approval and NEVER_REPLAY policies;
no new registry entry, tool name or permission bypass is needed.

The existing `LocalRolePackControl` now exposes an optional read-only inventory
operation. The concrete installer reuses physical LiteRT verification and the
speech inventory owner, and rehashes Supertonic assets through its existing pack
store. It distinguishes intact files, selected local output and the selected
voice's existing audible acknowledgement. It never selects a model, changes a
mode, grants a role or runs qualification. Its qualification contract is
`NOT_ATTESTED_BY_PACK_INSPECTION`: it has no injected exact device/backend evidence
owner and cannot declare a future measured candidate failed or qualified.
Existing LocalRoleEvidenceController/WorkloadQualificationStore remain authoritative.
Historical Qwen CPU negative evidence (9580 ms against 2000 ms) is reported with
its historical scope separately, not generalized to every backend or later result.

Added explicit `verifyInstalled` to the speech inventory owner, implemented by
fresh exact per-file size/SHA checks under existing transfer/file mutation locks,
with cancellation checks for each read chunk. The previous Silero legacy path
accepted presence/size and could not prove its pinned bytes. The starter uses
this stronger owner check for inspection, baseline, estimates and completion.
The downloader also rehashes pinned installed files before skipping them, and
its installation/storage preflight now accounts for damaged files needing repair.
Legacy unpinned optional models retain their existing management behavior.
Unsupported owner implementations fail closed instead of claiming integrity.

Fixed an acquisition integrity gap: the starter previously counted Supertonic
manifest/presence as verification. Its baseline, download/storage estimate,
stage completion and final completion now require matching every recorded
per-file SHA and the combined installed fingerprint. A damaged pre-existing pack
blocks a new transaction with an actionable repair/remove message and remains
untouched. Damaged bytes cannot satisfy completion merely by retaining their
file names, lengths and manifest. Existing metadata inventory remains available
for removal/import recovery; no new downloader or deletion owner is introduced.
Imported voice packs passing these checks are intact relative to their own
manifest; they are reported separately as compatible pack fingerprints/count.
Only the existing `downloadOfficial` path, after checking the archive's exact
publisher SHA, writes the official-source proof in the current pack metadata.
Import cannot supply that proof from archive content. Existing equivalent files
receive proof only after rehashing them against the verified archive's extracted
fingerprint; damaged files are retained for explicit repair. Starter completion
and the canonical SPEECH_OUTPUT integrity role now require this official proof
plus fresh byte checks. Compatible independently imported packs remain manageable
and selected separately; they cannot masquerade as the pinned manifest artifact.
Pack selection flags explicitly describe Supertonic pack/voice selection, not the
active synthesis provider. Publisher license audit remains a separate question.

Settings and the Speech onboarding step reuse expandable source/hash disclosure,
an explicit disk-only inventory check, the 8 GB evaluation target and measured
device-memory disclosure. They call out the separate conversation extension, incomplete
license audit, unqualified intent and unmeasured EN/RU/LV/PL speech quality.
New strings are supplied in EN/RU/LV/PL. Existing install/pause/resume/rollback
buttons remain with their current domain owner and transaction recovery. Both
surfaces require an available storage/download estimate with sufficient space
before enabling install; Speech setup has an explicit estimate refresh action.
Read-only baseline verification publishes PREFLIGHT before hashing, permits
Pause, and restores the prior state if canceled before creating a receipt.
Damaged speech baseline failures expose a localized repair-and-retry state and a
safe recovery hint through the existing status tool.

`halo_local_setup_status` exposes compact physical inventory/control status by
default, including null when no current inspection is available, role gaps and
unattested qualification plus incomplete license-audit disclosure. Optional
`manifestArtifactIndex` returns one bounded artifact page instead of control
status; full language/hash/source lists cannot overrun the existing tool-result
budget and hide transaction IDs. A manifest page includes its view marker and
`actionStatusIncluded=false`. It makes no measured-language-coverage assertion.
Typed
and voice invocations continue through the existing ToolRegistry registration,
ToolExecutor and CognitiveSession; no new action or bypass is added.

## Requirement boundary

| Workstream | Existing/reused or added source | Remaining work |
| --- | --- | --- |
| A: exact candidate catalog | Canonical per-file pins and source declarations; independently downloadable pinned Apache-2.0 SmolLM2 360M chat extension | Speech publisher/redistribution license audit; qualifying conversation/intent measurements; actual backend, language and working-memory evidence |
| B: curated installer | Existing explicit estimates, ownership journal, pause/resume/rollback; added fresh speech/TTS installed-byte revalidation, damaged-baseline refusal, cancellable preflight, admitted-space UI gate and optional CHAT owner/typed lifecycle | Fresh/upgrade, interrupted/wrong-SHA/low-disk/cancel/process-kill/deletion/native-unload acceptance; optional chat has its own exact ownership receipt and rollback scope |
| C: fast actions | Existing deterministic/native intent/tool authorization remains; status never promotes the installed Qwen candidate | Measured 2s/accuracy qualification; ambiguous recognition and safe clarification acceptance |
| D: resources | Existing residency/memory manager reused; no auto benchmark or GPU promotion | Exact device/artifact/backend timing, thermal, energy, 10-turn coexistence, pressure/eviction/switching/cancel measurements |
| E: Private policy | Existing PrivacyModeStore/profile and provider resolver remain authoritative; pack does not select routes or enable cloud | Airplane-mode network trace and profile/process-death acceptance; this batch does not assert all dispatch boundaries passed |
| F: onboarding | Shared localized source/gap review, explicit file inspection, actual byte/space estimate and refresh; optional conversation candidate in local-answer setup | First offline conversation, speech and supported-language acceptance; no quality claim from source |

## Operability / evidence ledger

- Feature: starter manifest and physical file inventory. Owner:
  `LocalRolePackInstaller` / `SupertonicPackStore`. Existing callable operation:
  `halo_local_setup_status`, empty typed schema, same registered owner and policy.
- UI effect: explicit inspect installed files; expands read-only disclosure.
  Receipt reports current physical inspection, never an inference or successful
  voice conversation. Existing install commands retain observed transaction IDs.
- Recovery: unavailable/busy inspection returns no readiness proof; cancellation
  propagates. Pre-existing corrupt voice assets are retained for explicit repair.
  Optional acquisition captures its operation owner and cancels that exact
  owner-tagged transfer again on coroutine cancellation, covering a Pause racing
  receipt persistence or transfer registration. No unrelated acquisition is canceled.
- Production source: implemented. Automated/unit/instrumented tests: NOT_RUN.
  Native device, microphone, audible quality, network traces: NOT_RUN.
  No operation has been labeled PASS from source inspection.

Keep #59 open. There is no measured Recommended/Ready pack in this batch.

## Changed owners / integration

- `app/.../ai/PrivateOfflinePack.kt`, `CuratedLocalCatalog.kt`,
  `LocalRolePackInstaller.kt`.
- `app/.../audio/SupertonicPackStore.kt`.
- `providers/sherpa-stt/.../SherpaModelInstaller.kt`,
  `SpeechModelInventoryRepository.kt`, `WhisperModelDownloader.kt`.
- `app/.../di/tools/LocalModelSetupToolExecutor.kt` (existing registration).
- `app/.../ui/screens/settings/PrivateOfflinePackDetails.kt`,
  `CuratedLocalModelGuidance.kt`, `OnDeviceModelsScreen.kt`,
  `app/.../ui/onboarding/HaloSetupScreen.kt`.
- Dedicated `halo_offline_pack59.xml` in values, values-ru, values-lv, values-pl.

No shared DI or tool-registry edit is required. Future testing fakes for
`SpeechModelInventoryRepository` that exercise successful pack acquisition must
implement the new exact verification contract; the production owner implements it.
