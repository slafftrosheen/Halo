# Model setup ownership and recovery #39

Source base: `017f3278bd18d3845b9dbdbdd4a81785ca494f5b`, freshly fetched with no divergence before the source freeze. This batch repairs the existing installer; it does not qualify or recommend a model.

## Feature and canonical owners

Manual setup and assistant requests share `LocalRolePackInstaller` through `LocalRolePackControl`. LiteRT, Sherpa and Supertonic retain their existing acquisition/storage owners. Each new setup journal has a canonical transaction UUID. Artifact receipts bind acquisition to that transaction; baseline differences never confer ownership. Legacy or changed-plan receipts have an explicit keep-files recovery path.

The five registered operations are `halo_local_setup_status`, `halo_local_setup_start` (also resumes), `halo_local_setup_pause`, `halo_local_setup_rollback`, and `halo_local_setup_keep_files`. Their v1 operability owner is `LocalRolePackInstaller`, capability `host.models.manage`, result encoding JSON, durable result ID `transactionId`. Discovery explains status-first operation, exact observed token, no uncertain replay, and acquisition versus qualification. Native skill `halo.models` uses the existing registry and policy owners.

## Typed contract and recovery

Status accepts no arguments. Mutations accept only the exact observed `transactionId`, with `NONE` for a fresh transaction. Unknown keys, arbitrary URLs/paths and malformed tokens are rejected. The installer rechecks the token under its transaction lock before asynchronous mutation. Accepted requests report `DISPATCHED`; status supplies actual progress. Installation never changes role selections or grants qualification.

Start is sensitive and follows user policy. Rollback is destructive, always requires existing confirmation, and never replays. Pause can stop only the observed setup's own acquisition; keep-files ends its rollback receipt without removing any files. Both are non-replayable. Existing CognitiveSession policy, capability, timeout/cancellation and privacy/Power boundaries remain authoritative.

Owned deletion verifies receipt and physical identity, retaining independent imports, changed bytes, selected/resident/pinned models and unknown ownership. LiteRT selected-model protection projects canonical general, role, intent and embedding selections. Sherpa VAD failure/cancellation restores runtime reload. Structured cancellation and scoped worker tags avoid canceling unrelated acquisitions. Restart preserves the transaction UUID and reconciles current physical inventory; removed baseline components no longer bypass storage preflight or reacquisition checks.

Manual recovery controls have 19 matching EN/LV/RU resource keys. Internal transaction IDs are not shown as ordinary setup copy.

## Functional workflows

| Workflow | Desktop typed owner | Typed on device | Spoken on device | Permitted Power | Durable readback |
|---|---|---|---|---|---|
| Status then explicit pinned setup/resume | PASS (isolated owner/adapter fixtures) | NOT_RUN | NOT_RUN | NOT_RUN | PASS (isolated owner/adapter fixtures) |
| Pause exact acquisition and keep its files | PASS (isolated owner/adapter fixtures) | NOT_RUN | NOT_RUN | NOT_RUN | PASS (isolated owner/adapter fixtures) |
| Approved exact-owned rollback, retaining unrelated/selected files | PASS (isolated owner/adapter fixtures) | NOT_RUN | NOT_RUN | NOT_RUN | PASS (isolated owner/adapter fixtures) |

## Evidence and acceptance

Production and regression sources were completed before the first focused gate. Read-only independent follow-up review found no remaining concrete blockers after selected-unloaded LiteRT and VAD recovery corrections. Static localized XML parity and patch whitespace checks passed.

Focused regression sources cover owner-scoped artifact/partial removal, independent import retention, changed bytes, selected-unloaded protection, borrowed publication, VAD cancellation/failure recovery, bounded legacy journal parsing, conditional WorkManager payload/tag identity and typed setup dispatch/rejection. Build/test and device fixture results are recorded below.

#39 remains OPEN: real multi-role installation under storage/interruption/process-death conditions, full audited provenance/license/backend coverage and device/role/language quality acceptance are not established by these fixtures. Actual speech and permitted provider-route results remain NOT_RUN unless separately measured. Do not promote candidates to Tested or Recommended based on acquisition success.


## Executed checks

- Initial integrated app gate: PASS, 15m03s; both APKs plus 20 desktop cases in four classes. Speech-provider gate: PASS, 46s; 14 cases in four classes.
- Initial LiteRT gate: 32 existing/new cases passed; four new ownership cases rejected the undersized fixture before exercising operations. Correcting the fixture exposed a real `IllegalMonitorStateException` in two cases: the existing thread-owned artifact lease crossed a suspending inventory operation. Replaced its local thread lock with synchronized operation-owned reader/writer state while retaining the cross-process file lock. Added cross-thread release/double-close coverage and obtained a clear source follow-up review.
- Final LiteRT gate: PASS, 1m26s, 38 cases in five classes, including all four ownership and two lease cases. No catalog guard was weakened.
- A native run on serial `f01fe673`, API35 passed all five queued #52/#53/#54 cases in 13.892s: PDF annotation/export/original preservation, RU table/dialog controls at 200% font, photo → Studio → Canvas → Study → Chat persistence, and exact attached Study/Canvas semantic owners. These are isolated native fixtures, not actual spoken/provider acceptance.
- That first native run installed app SHA256 `e905dd36010b96ff134aa271e4bd2a452e0f5fe8c8ec64c06de14d21d59d7a41` and test SHA256 `a10f21120fb028a883f7909fd4abdce3d4e0fc0c3f7effb09a8c71a72149b97b`; installed base APK readback matched. The later lease correction requires final packaging and a fresh installation; do not treat the first native run as proof of that correction.

Raw gate/XML/phone records are in `artifacts/HALO-MODEL-SETUP-039-2026-10-23/`. Final registry review and final APK/device identity are recorded below.


## Final source and package record

- Final app registry test: PASS, 4 cases. `registry.operabilityReport` for all five setup descriptors returns `[]`; exact rollback ALWAYS confirmation and mutation NEVER_REPLAY assertions pass. Discovery may include companion tools, so its assertion selects the requested definition. The first added assertion incorrectly assumed a singleton list; corrected without changing production.
- Final runtime gate: PASS, 1m, 8 cases across native skill ownership and on-demand guidance. A pre-existing Notebook wording assertion was updated to the shipped Workspace wording; every native skill, including `halo.models`, has loadable operational guidance. Across the selected gates there are 81 distinct passing desktop cases: app21, Sherpa14, LiteRT38, runtime8.
- Final app and instrumentation assembly tasks completed in the 7m28s packaging/registry invocation. That invocation failed on the discovery test assumption above; the corrected app registry and runtime tests subsequently passed. No production edits followed this assembly. Final app SHA256 `3ce32d6d2765a409d79514b4ccdb98b2f4864c649aa9259b4542a0908c306360`; test SHA256 `a10f21120fb028a883f7909fd4abdce3d4e0fc0c3f7effb09a8c71a72149b97b`.
- Final installation attempt failed because ADB inventory became empty. The phone still has the first package recorded above, not the final lease correction. The five passing native cases remain evidence for their own isolated Workspace workflows. Final model setup native, real spoken, permitted Power, multilingual quality and fresh/pressure/process-death pack acceptance remain NOT_RUN.
- Compiled/packaged production trees: app `a15061cb762664a70df17c5b9b45672de3127863`, LiteRT `262370e556c0d11b1447f142e14182a1e5a28748`, Sherpa `ba21223b3374bd553c78a6bc405439820c7bd567`, cognition `f377f4ca4d80a477f71e2e794d6eba239eeffd75`.

No CI/Actions or model downloads/selection/qualification were performed. #39 remains OPEN for its outstanding acceptance, while verified source is publishable to main.
