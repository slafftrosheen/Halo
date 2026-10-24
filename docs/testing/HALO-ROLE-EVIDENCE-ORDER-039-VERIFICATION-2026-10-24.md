# Role evidence supersession — #39

Date: 2026-10-24. Base `17e856296f30472b69a94e6da8db54f02689a0ce`, fetched 0/0 with a clean checkout before changes.

## Problem and complete source correction

Role qualification DAO history sorted by wall-clock completion time and random UUID; recommendation projection passed the whole history to a policy choosing the maximum timestamp. An older pass could hide a newly saved failure after clock rollback or at equal times. Current projection also read the UI flow during explicit application, allowing a just-saved failure to remain unseen until asynchronous flow delivery.

The canonical append-only role table now reads newest SQLite insertion first. Production has no row deletion/replacement or live-history VACUUM path for this table. Projection resolves the first exact identity before validating its timestamp and sends only those current rows to the existing portable ranking policy. Invalid latest time yields unknown, not an older pass. A subsequent valid retest can qualify again. The full history remains available.

The existing repository serializes suite publication with a fresh durable-history callback. Explicit recommendation application uses that callback for the fresh DAO read and synchronous existing router write after physical-artifact preflight. It retains its existing router revision/configuration checks and per-controller apply mutex. File verification stays outside the short evidence lock. This serializes the canonical in-process owner; it is not a claim about arbitrary external database writers.

No schema/version change, new persisted field, replacement runtime, tool, permission, execution/approval owner, provider request or model selection during validation. Existing assistant/UI operation contracts and role-selection authorities are unchanged. The portable policy remains unchanged; the canonical Android owner supplies its latest resolved evidence.

## Verification

Production and five new regression cases completed before the first gate: rollback plus successful retest, equal times, future latest failure, actual file-backed Room close/reopen, and publication versus explicit application serialization. The tests exercise the canonical DAO, repository, projection, portable recommendation and existing explicit apply guard with synthetic measurements. Existing repository and apply tests are selected too. Source review: **CLEAR**.

One focused `:app:testDebugUnitTest` gate **PASS**, exit 0, 6m22s, 403 tasks (9 executed, 394 up-to-date), serial one-worker / 3 GB heap. **11 cases**: five new `RoleOrderTest`, two existing role repository and four existing explicit-apply cases, zero failures/errors/skips. All seven captured source/test/schema hashes matched afterward, including the unchanged generated Room 30 schema. [Raw log, three JUnit XML files and input identities](artifacts/ROLE-EVIDENCE-ORDER-039-2026-10-24/) are retained. No APK assembly or physical acceptance.

## Remaining acceptance

Keep #39 open. Desktop synthetic measurement metadata is not physical model-quality qualification, actual typed/spoken workflow, permitted Power acceptance, OS process death or full model-pack onboarding. No APK assembly/install, native inference, model campaign, CI/Actions or spoken tests in this batch; the human deferred spoken tests.
