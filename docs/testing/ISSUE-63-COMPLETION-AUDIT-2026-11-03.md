# Issue 63 implementation completion audit — 3 November 2026

Status: source changes and regression tests authored; execution deliberately **NOT_RUN** at the user's request. This document does not close issue #63 or assert phone/voice/provider acceptance.

Superseded completion assessment: the subsequent [full requirements audit](/home/pi/Projects/Halo/docs/testing/ISSUE-63-REQUIREMENTS-AUDIT-2026-11-03.md) at `ba177d110` identifies outstanding code gaps. Its AUD63-03 also identifies a regression in this document's empty-attention change: an unavailable/unobserved owner currently becomes indistinguishable from a successfully empty read. The observation stamp needs explicit availability before this correction can be considered complete. The conditional-cache and ICS changes below remain authored, unexecuted source changes.

## Starting point and scope

Audited against [issue #63](https://github.com/slafftrosheen/Halo/issues/63), its [repository snapshot](../everyday/ISSUE-63-SOURCE.md), [implementation ledger](../everyday/IMPLEMENTATION-LEDGER.md), and [code handoff](../everyday/ISSUE-63-CODE-HANDOFF.md). Baseline: `78018b0ed90c9ad8f0d210000e64d0bca1ff753e`.

The existing implementation already supplies the A–H features; this pass extends those owners instead of creating another organizer, privacy mode or agent path. Historical tests and phone installation results in the ledger apply only to their recorded snapshots. The recent snooze-reboot and conditional-destination follow-ups remain in the baseline and in the deferred regression gate.

| Workstream | Existing implementation reused | This pass |
|---|---|---|
| A organizer | `everyday/organizer`, `data/everyday/RoomOrganizerRepository`, existing Workspace Notes | Correct ICS duration semantics and withhold unsupported/ambiguous series. |
| B collectors | `CollectorCoordinator`, `PublicHttpCollector`, Room reservations, registry/catalogue and WorkManager | Require actual matching conditional request evidence before accepting 304 freshness. |
| C Your Day | `EverydayBriefEngine`, `EverydayBriefService`, canonical Daily Assistant | Observe empty attention snapshots so new/resolved items compare truthfully. |
| D UI5 instruments | `ui/everyday`, existing Zone/Orb routes, localized presentation | Shared domain corrections reach the current instruments; no new screens or visual contracts. |
| E Screen | `screen` and `everyday/presentation` real-data cards | Same canonical corrected collector/brief data; card identity/configuration unchanged. |
| F launcher widgets | `nativeactions/EverydayWidget*` | Same passive snapshots; no refresh cadence or launcher authority changes. |
| G typed AI/voice | `di/tools/EverydayToolExecutor`, runtime descriptors and provenance | Add explicit privacy regression coverage for all 22 Everyday commands; preserve current permissions. |
| H engineering | Existing migrations, policy matrix, retention, test suites and limitations | Add this source/evidence handoff and defer expensive execution until the implementation batch is ready. |

This is a focused source audit of the changed boundaries, not a new independent acceptance of every existing workstream.

## Corrected behaviors

### Conditional cache integrity

Previously a saved body with no usable validators could receive an unsolicited 304 and have its verification/expiry advanced. Headers removed or replaced before the final request had the same problem. `PublicHttpCollector` now requires the final request's validator to match the corresponding cached validator. If-None-Match takes precedence over If-Modified-Since. Blank/control-containing/oversized validators cannot establish proof. The existing final-URL fence is retained.

Rejected responses keep the cached payload and original retrieval/verification/expiry, drop unusable validators through the existing source-version/reservation-token fence, and enter ordinary backoff. Recovery uses a later budgeted request. No extra transport attempt or Local bypass is added. See [HTTP 304 semantics](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4.5).

Two new `CollectorIntegrationTest` methods cover unsolicited 304 with Room publication/backoff/recovery, Local zero-dispatch, stripped/replaced/blank validators, and successful ETag-only/Last-Modified-only verification.

### Empty attention is a successful observation

The directly read Daily Assistant was previously stamped as observed only when it supplied a visible item. An empty morning baseline could not report its first added item, and resolving its last item looked like unavailable source data. The engine now retains its successful observation stamp even when empty. Facts still use the same child-local IDs; observing an owner does not grant access to adult facts.

Two new `EverydayBriefTest` methods cover empty → nonempty → empty comparison and filtered child presentation. Stored historical baselines lacking this stamp remain conservative until the next morning baseline; no archive format migration is needed.

### Calendar interchange preserves supported semantics

The importer previously used Java Duration directly, treating P1D as 24 elapsed hours and rejecting P1W. The bounded parser now adds nominal calendar days/weeks before elapsed time, handles positive week/day/time forms, and rejects malformed, nonpositive, oversized or all-day time-component durations. This keeps all-day midnight boundaries over both DST transitions. See [RFC 5545 duration semantics](https://www.rfc-editor.org/rfc/rfc5545.html#section-3.3.6).

The existing organizer represents timed recurring event durations as elapsed time. Consequently timed recurring nominal day/week durations in zones with transitions are explicitly withheld for manual review rather than imported with changed meaning. Timed floating nominal durations are also withheld: a later device-zone change can change their elapsed length. All-day recurrence, nonrecurring fixed-zone nominal durations and fixed-offset timed recurrence remain supported. This is an explicit bounded interchange limitation, not a placeholder or silently simplified recurrence.

Computed duration ends and explicit DTEND instants must survive conversion to the organizer's stored local-time representation. A later DST overlap offset that would be lost is withheld for manual review instead of shortening the event. This does not reject the representable earlier overlap endpoint.

Recurrence properties on an occurrence override are now rejected with the whole series withheld. An invalid master also withholds another master with the same UID regardless of input order. Ambiguous multiple-UID properties withhold all referenced identities. Eight new `OrganizerIcsTest` methods cover these cases, spring/fall all-day recurrence, nominal-vs-elapsed time and weeks, malformed durations, unsupported recurrence, floating zone changes and later-overlap endpoints.

### Privacy regression coverage

The local setup's prior core run stopped on two obsolete assertions in `PersonalConfigurationPolicyTest`: an aggregate count of 78 despite 101 current personal-tool identities, and provenance expectations predating Everyday location/activity/device fields. Replace the unrelated aggregate count with explicit Everyday identities. Assert the exact 22-tool membership and exact scoped categories, including Screen configuration's LOCATION category and unknown-prefix exclusion. Existing Workspace/Screen/local-only/operating-mode denial assertions remain intact. No production policy is relaxed.

## Verification contract and next phase

Source whitespace/diff inspection is separate from executable verification. Thirteen new JUnit methods and updated existing expectations are authored. **No Gradle task, Kotlin/Java compilation, unit test, APK build, live source request or device test was run in this coding phase.** The old built APK is not an artifact of these changes.

An independent read-only code review identified two important duration representation boundaries (floating time-zone changes and the later overlap offset). Both are addressed in source with additional rejection/representable-success regressions. Runtime verification of the review fixes is also NOT_RUN; they have not been relabeled as tested fixes.

Run serially after this coding phase, from the repository root with the local toolchain environment loaded:

```bash
source /home/pi/.config/halo/env.sh
./gradlew :runtime:cognition:test --tests '*PersonalConfigurationPolicyTest' --console=plain
./gradlew :app:testDebugUnitTest --tests '*CollectorIntegrationTest' --tests '*EverydayBriefTest' --tests '*BriefPersistenceTest' --tests '*OrganizerIcsTest' --tests '*OrganizerDeliveryTimingTest' --console=plain
```

Then run the established policy/typed-tool/widget/migration suites and `./gradlew haloAppBuild` once against the final source. Preserve failures and source identity; do not reuse the old setup run as evidence for this batch.

These changes do not add a tool ID/schema, permission or new interactive owner. Calendar preview/import, source refresh and briefing still use the registered Everyday contracts; their generated facts and metadata change at the shared domain boundary. Registered-feature/typed dispatch execution is pending with the broader gate, as are actual STT-final and permitted remote-provider workflows in the [operability verification template](AI-CONTROLLABLE-TOOL-VERIFICATION-TEMPLATE.md).

Android alarms/Doze/reboot/permissions/audio, launcher placement/resize/lock-screen behavior, assistive technology and real spoken recognition remain device acceptance. Keep issue #63 open until the appropriate execution/device evidence is available. No new dependency, network service, backend or account is required for these corrections.
