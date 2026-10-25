# Composer draft recovery — Android #55

Authority: user-authorized autonomous issue execution and #55 P0 draft persistence/recovery. Baseline main7d609afb2. The phone cold-restart failure is recorded in the dated acceptance ledger. Previously untouched ComposerDraftStore stub/tests are directly relevant to this recovery; original bytes are retained under ignored local evidence. Preserve their intended public API and original three tests when completing the implementation.

1. Establish the failing physical restart and narrow original-store tests. Do not call rememberSaveable a durable store.
2. Complete one shared composer-draft owner behind HomeViewModel and both existing editors. Use existing Keystore/AtomicFile secret storage through a narrow adapter; ordinary Graph.storage draft keys would be plaintext. Restore text only, with no submission/tool/approval replay.
3. Guard stale writes/loads, conditional accepted-submission clearing, per-conversation isolation and temporary-ID migration. Preserve newer edits. Handle corruption/write failure honestly, bound persisted records and encoded text safely, and clear drafts with deleted conversations. Keep provider/privacy settings untouched.
4. Verify meaningful races and original tests without the unfinished-test exclusion, affected Home/Shell/localization regressions, assembly and installed APK hash. Repeat actual cold restart, per-conversation and Home↔Orb-editor behavior on the phone; verify encrypted-at-rest fixture handling without logging user content. Publish coherent code/evidence and keep unrelated alpha gaps open.

A successful write acknowledgement must follow durable persistence; asynchronous precommit loss windows must be explicit. Do not invent automatic-save success or clear newer text based on an older accepted run. Do not alter model/provider/permission settings or add another cognition/session/storage backend.

The checkpoint13 free-cloud control also exposed two existing action-adapter gaps: singular “one” missed the shared create-action cue, and create-note exposed only body although the canonical repository already supports an independent title. Add the bounded singular cue and optional 120-character title to the existing adapter, preserve legacy defaults, and verify real saved title/body/id. Both are covered by focused regressions; no provider, budget, authority or owner change.

Independent review reproduced a restore/cleanup I/O race. Preserve newer post-restore revisions across successful or failed cleanup; keep old deleted identities revoked. The new blocked-write tests must fail before the production fix and pass with it.
