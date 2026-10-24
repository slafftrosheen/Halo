# Workload evidence invalidation — #39 / #33

Date: 2026-10-24. Base: `ad7f26f72647da07d3d6ee582786ed74f77349ae`, fetched 0/0, clean before changes.

## Problem and implemented correction

The general workload environment included runtime/device/settings but omitted the installed package identity. Replacing Halo without changing the LiteRT version or tuning could therefore reuse a previous build's workload proof. The specialized intent path already included package/source identity. General workload fingerprints now also include installation update time, Halo source ID and Android build ID. Historical records remain stored, but do not match the replacement environment. Missing installation identity prevents current proof projection and role-suite publication.

The same general store chose the greatest wall-clock timestamp and filtered future records before choosing. A later failed measurement with an equal/earlier timestamp, or an invalid future timestamp, could expose an older pass to automatic ranking. Projection now selects the last appended record for each matching target/environment/workload/backend first. Invalid latest timestamps yield no proof; a matching later failure remains a failure. Independent backends, workloads and environments retain their separate scopes. Intent's existing specialized latest-result checks remain in place.

## Canonical owners and operability boundary

`WorkloadQualificationStore` remains the evidence owner consumed by Workload Lab and preparation/ranking. Existing provider/configuration, model acquisition, residency, privacy, consent and tool authority owners are unchanged. No new user-facing operation, ToolRegistry descriptor or execution path is introduced; all existing semantic tool contracts remain unchanged. This is evidence eligibility, not a new model recommendation, selection or permission grant.

## Regression and review

Production and eleven new regression cases completed before the focused gate. They cover six general workload identities across package replacement, same-installation reopen, unknown package read/write refusal, equal timestamp and clock rollback, future latest failure and later valid requalification, scope separation, and actual automatic-ranking refusal after revocation. Existing intent qualification regressions are included to cover its shared environment.

Focused results: **PASS**, exit 0, 12m13s, 403 tasks (53 executed, 350 up-to-date). `:app:testDebugUnitTest` selected `WorkloadQualificationStoreTest` and `LocalIntentQualificationTest`: **28 cases** (11 new general-workload, 17 existing intent), zero failures/errors/skips. Source review CLEAR. All four captured production/test/fixture input hashes matched after the gate. [Raw log, both JUnit XML files and source identities](artifacts/WORKLOAD-EVIDENCE-039-2026-10-24/) are retained. Serial Gradle, one worker, 3 GB heap; no APK assembly.

The separate qualification-owner audit found additional unresolved package-invalidation gaps in `LocalRoleQualificationRepository` recommendations, persisted vision proof and GPU stability qualification. Those owners are not changed or declared fixed by this shared-workload correction. Native tool proof is process-local and does not have the same persisted-proof gap. Preserve negative GPU quarantine history when repairing positive qualification.

## Remaining acceptance

Native installation/process death, inference quality, language/device/backend qualification, actual typed/spoken and permitted Power journeys: **NOT_RUN** in this batch. No APK assembly/install, model download/campaign, live provider, CI/Actions or issue closure. Keep #39 and #33 open for their original acceptance; this does not resolve the model distribution provenance questions or the measured intent latency failure.
