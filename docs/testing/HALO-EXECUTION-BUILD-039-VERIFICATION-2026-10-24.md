# Installed-build qualification boundaries — #39 / #33

Date: 2026-10-24. Base: `a54691b00671a1646add32aa9ad6b3aa366987e9`, fetched 0/0 and clean before changes.

## Complete source batch

The shared workload correction in [the preceding record](HALO-WORKLOAD-EVIDENCE-039-VERIFICATION-2026-10-24.md) did not cover three independent positive qualification owners. This batch addresses those owners together:

- Role recommendations carry an additive execution-build identity through the portable evidence contract, Room history, repository, current projection and existing explicit apply checks. Room 29→30 adds a nullable column; legacy rows stay readable as unscoped history. Unknown package metadata cannot publish new role measurements or supply current recommendation identities. Historical measurements are not relabelled as measurements of the new build.
- Vision proof keys and persisted records are scoped to the installed execution build. Old unscoped v1 records remain stored but cannot admit non-probe vision. Same-build proof can reopen; another installation needs a new proof. Missing package identity remains probe-required.
- GPU positive/in-flight qualification requires the installed build that produced it. Existing native-crash quarantine, reasons and ambiguous-exit strikes remain effective after replacement. Missing build identity cannot start or complete positive qualification. Ordinary AUTO remains CPU; this is stability admission, not comparative performance evidence.

The shared Android identity hashes package name/version, installation update time and Android build ID. It reads package metadata only; it does not hash APK/model files, inspect user content, read an account/serial, start inference, change role selections or authorize a tool. Runtime, artifact and device identities remain independently scoped by each existing owner. Native tool proof is already process-local and was not changed.

## Owners and functional operability

Existing `LocalRoleQualificationRepository`, `LocalRoleEvidenceController`, `LiteRtBackendQualificationStore` and `LiteRtLocalProvider` retain their responsibilities. No new ToolRegistry operation, execution engine, permission, network flow or consent owner. Existing assistant/UI operation contracts are unchanged; changing evidence eligibility does not grant execution authority. Explicit role application still verifies source bytes/configuration and current router revision through its established owner.

## Verification

Implementation and regression sources precede the focused gate. New cases cover package identity, same-install vision reopen versus replacement/legacy/unknown identity, GPU requalification and preserved negative history, role-history stamping/unknown refusal, Room migration preservation, old-build recommendation exclusion and stale apply refusal.

Source review: **CLEAR** before the gate. One combined serial gate **PASS**, exit 0, 11m30s, 410 tasks (52 executed, 358 up-to-date), one worker / 3 GB heap:

- `:sdk:provider-sdk:test`: 12 role-recommendation cases.
- `:providers:litert:testDebugUnitTest`: 11 GPU qualification cases.
- `:app:testDebugUnitTest`: 39 cases across vision/build identity (3), role repository (2), role migration (2), explicit apply (4), existing intent qualification (17) and shared workload qualification (11).

**62 total cases**, zero failures/errors/skips. All sixteen captured compiled-input hashes match after the gate. The generated Room 30 schema differs from 29 only in `local_role_qualifications`, adding the nullable TEXT/default-NULL identity column. The migration fixture preserves the legacy pass, fingerprint and record ID with a NULL build identity. [Raw log, all eight JUnit XML files, source identities and schema-delta witness](artifacts/EXECUTION-BUILD-039-2026-10-24/) are retained; the generated schema is committed with source. No APK assembly/install, native inference, model campaign, CI/Actions, actual spoken test or provider acceptance in this batch.

## Remaining original acceptance

Keep #39 and #33 open. This fixes source qualification scoping, not inference quality, full multi-role onboarding, audible/physical voice acceptance, exact model license/export provenance or measured concurrent performance. The human requested continued coding and deferred spoken tests. Phone availability and physical acceptance remain separate.
