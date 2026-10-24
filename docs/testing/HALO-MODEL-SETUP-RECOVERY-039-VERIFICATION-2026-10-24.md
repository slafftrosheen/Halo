# #39 existing setup owner recovery

Base836e96d3f6db5c97baa69183ed40102fa2a7989d, fetched main0/0clean before edits. Original #39 acceptance remains open. Sources/regressions precede the first focused gate.

## Feature and canonical owners

Settings/onboarding and assistant use LocalRolePackInstaller through the existing LocalRolePackControl/LocalModelSetupToolExecutor, registered in ManualDependencyGraph. Existing host.models.manage capability and ToolOperabilityv1 cover halo_local_setup_status/start/pause/rollback/keep_files. No new operation, store, acquisition engine, permission, provider, role or qualification owner. The existing registered-feature review and destructive rollback ALWAYS/NEVER_REPLAY assertions are included in the focused executor tests.

Unreadable stored journal is preserved as unknown ownership rather than discarded as idle. Legacy/mismatched-plan ownership exposes keep-only recovery. Owner status now truthfully exposes hasSetupRecord/recoveryRequired/canKeepFiles; Settings gains the existing Keep-files action and both UIs suppress unavailable start/rollback. Two plain-language EN/RU/LV/PL keys explain recovery and failed clearing. Generated coverage/parity checks preserve existing values and lookup identities.

## Typed operation contract

Existing closed transactionId argument schema and bounded JSON receipts remain; new capability flags have declared boolean types in the existing result schema. Query confirms state; mutators report DISPATCHED only and require subsequent canonical status/readback. Keep captures the observed content-free record fingerprint before queuing, checks it again under the existing transaction mutex and requires matching stored bytes. It cannot clear or overwrite presentation for a newer canonical receipt with the same token. Fingerprints cover exact UTF16 code units, including malformed surrogate data, with bounded temporary encoding. Unknown data is never exported in messages or logs.

Storage writes/removal precede successful in-memory publication. Failed install/rollback progress or terminal writes expose nonbusy keep-only recovery while retaining the last durable receipt. Rollback cannot remove files before its admission write succeeds. A failed new setup cannot rewrite a previous completed setup into rollback eligibility. Cancellation propagates; unavailable sources do not acquire or remove files. Existing Private/Power, Kids, Android permission, policy/confirmation and replay owners are unchanged. Clearing a setup receipt never selects/qualifies a model or removes model assets.

## Source review and regressions

Fifteen actual-owner desktop cases plus4 codec/4 executor cases cover malformed/oversized/empty record preservation, typed/direct no-acquisition guards, exact Keep and retained files/choices, failed clear/recovery/progress/rollback writes, canonical/external same-token replacement, malformed Unicode, valid interrupted ownership, legacy/changed-plan recovery and prior-completed-pack protection. Review found mutable queued stamps and remaining write-failure busy states before execution; fixes and dedicated regressions preceded the first gate. Final production review clear.

The isolated Android fixture uses the canonical AndroidStorage with a fresh named preference file and cache directory, verifies committed XML before/after, reconstructs owners and invokes the same typed Keep operation. Only fixture receipt/files/choices are used. No global setup, selection or actual acquisition/qualification; owner reconstruction is not OS process death. Read-only native fixture review clear. All15 pre-gate compiled-input identities still match after validation.

Localization preflight PASS:8 generator/7 Polish tests, generated-source check,4802 required PL strings. All4019 existing values per EN/RU/LV/PL locale and4036 lookup keys/IDs remain exact, with2 new keys. No translation source changed after these checks.

## Three functional workflows

| Workflow | Typed on device | Spoken on device | Typed permitted Power | Spoken permitted Power | Durable readback |
|---|---|---|---|---|---|
| Fresh full role pack | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Interrupted real acquisition/resume | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Consequential real pack rollback | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

The narrow isolated recovery fixture does not complete these full journeys. No CI/Actions or repeated intent/embedding/speech qualification campaign.

First serial gate88814 PASS8m42s,511 tasks (31 executed/480 up-to-date), both APKs assembled. LocalRolePackRecoveryTest15/15 in6.207s, LocalRolePackJournalTest4/4 in0.095s, LocalModelSetupToolExecutorTest4/4 in3.486s; zero failures/errors/skips. All raw XML preserved. This includes the existing registered-feature report, five tool contracts and destructive rollback approval/replay assertions.

Physical phone f01fe673 /23124RA7EO /API35: host35194 exit0, one isolated native case PASS0.307s, run89cfa5aeb4014f3eaa94c2f2dc81a102. Real committed AndroidStorage XML preserves the damaged fixture record across owner reconstruction, then the existing typed Keep operation clears it durably while retaining fixture files and choices. Acquisition/removal counters0, no fixture use of global setup/selection; model qualification NOT_TESTED, actual spoken recognition/OS process death NOT_RUN. MainActivity resumed.

Installed package readbacks match local artifacts: production SHA `8b9859d73277c73ffda07b1ba0755d1ad8f7a06312c3e77239dc819817ffd545`,288516147bytes; test SHA `88f823076c728e34f8d61f4bca28c29c18cb2850fb632b03d2b714b68ed1a103`,1963640bytes. This combined build also installs the previously published Ink geometry sources; no additional physical Ink visual/pen or real spoken claim.

## Evidence and remaining acceptance

[Source identities and bounded raw results](artifacts/MODEL-SETUP-RECOVERY-039-2026-10-24) are retained. Only content-free exact-run evidence, fixture host command and terminal results are included; no APKs, model binaries or device-wide private journals. Raw tool transcripts retain producer whitespace. Keep #39/#54 open for complete fresh/storage/process-death pack acceptance, provenance/license/backend/per-device/role/language qualification and actual spoken/permitted-provider workflows. Source review, desktop storage faults and isolated native receipt recovery cannot prove those requirements.
