# Organizer delivery — bounded correctness and recovery review

Status: Independent bounded review; OD01–OD07 addressed in source, with matching focused app host evidence: 30 delivery/persistence tests passed. Rendered/native acceptance remains pending. No full UX, 10/10, issue-63 or device acceptance is claimed.

Reviewer: `/root/ux_ui_review`, 1 November 2026. Scope: OrganizerDeliveryCoordinator/Timing/UseCases, Android delivery/queue/receiver/activity, direct-boot mirror and unlock handoff, Room delivery mutations, HaloApplication bootstrap and legacy reminder migration. No production edits, builds, tests or phone operations were performed. The parent accepted OD01–OD06 and began corrections during this review; this document records the inspected mechanisms, not an assertion that in-flight changes remain defective. OD07 was sent at the end of the pass. Further review waits for the corrected source.

The architecture provides useful safeguards: durable record/outbox ownership, version/trigger-fenced callbacks, claimed delivery before notification publication, monotonic timer/snooze timing within a boot, redacted device-encrypted state and deliberate separation of normal provider startup from Direct Boot. Redaction removes titles/details/location/links/source information from the mirrored record and occurrence overrides. These are source observations. Physical pre-unlock behavior, native notification/audio behavior, rendered accessibility and OEM recovery are untested here.

All paths below are repository-relative. The initial native alarm activity is an integrated delivery surface; full organizer forms/routes, Library/tools/backup, Screen and Android widget work remain pending.

## Findings and acceptance cases

### OD01 — P1: consuming a locked one-shot can strand the shared wake queue

`app/src/main/java/com/halo/app/everyday/organizer/android/OrganizerLockedDelivery.kt`, `fire`, `action` and `schedule`: after the due occurrence is consumed, `schedule(id)` returns immediately if the planner finds no next occurrence. It does not remove that record from `OrganizerAlarmQueue` or rearm the next remaining record. The shared queue still contains the consumed past item; the fired OS alarm itself has already been consumed. Later pre-unlock alarms can remain unscheduled, or the stale past item can keep winning when another record updates the queue. Stop on a one-shot follows the same path. The locked fire path also lacks the normal coordinator's co-due drain.

Remove the consumed record's derived queue entry and reselect the next wake even when the record has no future plan. Drain eligible co-due locked items before rearming rather than relying on a second immediate wake. Preserve receipt consumption so duplicate broadcasts cannot replay sound.

Acceptance: two one-shot alarms before first unlock; fire/Stop the first and verify the second is the next wake. Repeat with equal trigger times, a timer and a repeating alarm, duplicate first callbacks, and a consumed record with no future plan.

### OD02 — P1: first reconciliation can silently skip an already-due saved item

`OrganizerDeliveryCoordinator.reconcileLocked` only catches up overdue **existing receipts**. `OrganizerSchedulePlanner.next` in `OrganizerUseCases.kt` skips ordinary triggers earlier than `now`. If a new alarm/reminder's trigger passes between durable save and its first scheduling worker, there is no receipt to inspect; the planner returns no occurrence and the coordinator acknowledges the outbox with neither delivery nor MISSED outcome.

The timer branch needs the corresponding original-deadline check: an exhausted timer without a prior receipt can be planned at `now`, obscuring how late it is after a reboot. Do not make an old timer look freshly due by replacing its original wall fallback deadline.

Classify initial due items using the persisted intended trigger and explicit catch-up policy: deliver eligible recent items or record a truthful missed outcome. Keep future recurrence enabled and avoid replaying occurrences already consumed in another revision.

Acceptance: persist a one-shot due shortly, delay the first reconciliation across its trigger, then check recent and older-than-window cases. Repeat after process death before initial scheduling, with recurrence and an exhausted timer after a changed boot. Each saved item must have a visible actual outcome rather than a cleared outbox with silent omission.

### OD03 — P1: a late locked callback can write after unlock import

`OrganizerAlarmReceiver.onReceive` reads `UserManager.isUserUnlocked` before entering `OrganizerLockedDelivery.fire/action`. The latter wait on `handoff`, but do not recheck unlock inside that lock. A callback classified locked can wait behind `importAfterUnlock`, then write a fresh locked journal after the import has acknowledged the old journal. `HaloApplication.awaitOrganizerBootstrap` subsequently sets `organizerRecovered`; it does not import that late journal again in the same process. Room and the locked mirror can disagree about delivered, dismissed or snoozed state, allowing lost actions or duplicate recovery.

Recheck or seal authority at the locked-owner boundary after obtaining the handoff lock. A callback after cutover must route through the unlocked owner outside that lock; avoid calling bootstrap while holding the lock that bootstrap itself needs.

Acceptance: latch a callback after its initial locked check but before handoff acquisition; complete unlock import, then release fire, Stop and Snooze variants. Each action must land exactly once in Room and leave no unimported late journal. Also test callbacks that finish before import and process death during import acknowledgement.

### OD04 — P1: concurrent edits can republish revoked locked-boot authority

`MirroredOrganizerDelivery.schedule` in `OrganizerDirectBootStore.kt` saves the caller's record snapshot into the mirror. A coordinator can write a v1 PENDING receipt, then a user edit/delete can invalidate the mirror and commit v2, followed by the older schedule call saving v1 back into the mirror. The coordinator's final `putReceipt` correctly rejects v1, but its cleanup only calls `port.cancelSchedule(id)`; that operation does not remove the stale mirror. A later locked reboot reconciliation can recreate the old edited/deleted alarm.

Fence mirror publication against the record's current durable authority/invalidation generation, and clear rejected stale mirror state. A simple earlier read of the Room version is insufficient if invalidation and publication can still interleave afterward.

Acceptance: hold the schedule between pending-receipt persistence and mirror publication; edit, delete or cancel the record, then release it and simulate locked reconciliation. The old version must not exist as deliverable mirror/queue authority. Retain a valid later version and its future schedule. Also exercise failure after invalidation but before the Room transaction commits so old valid schedules have a defined recovery path.

### OD05 — P1: an approximate item can delay an exact alarm; fallback is misreported

`OrganizerAlarmQueue.replace` chooses the earliest trigger and selects the OS API solely from that item's flags. An approximate event/task notification at 10:00 can become the only inexact wake while a precise alarm at 10:01 waits behind it. Android may delay the inexact wake, so the precise alarm becomes late or missed even though `AndroidOrganizerDelivery.schedule` reports EXACT for it. Android explicitly permits delayed delivery for `setAndAllowWhileIdle`; this is a platform-supported outcome, not an invented OEM failure. [Android alarm scheduling documentation](https://developer.android.com/develop/background-work/services/alarms).

Additionally, the queue catches exact-scheduling `SecurityException` and falls back to an inexact API, but the adapter independently recomputes EXACT from current permission instead of receiving the actual fallback result.

Preserve the earliest precise deadline when coalescing approximate work, for example with bounded separate alarm-clock/exact/approximate wake lanes. Return the actual adapter scheduling outcome, including permission-race fallback, and persist the corresponding truthful receipt.

Acceptance: approximate-before-exact and equal-time mixed schedules; exact access denied, revoked during scheduling and exact API rejection; later removal of each lane's earliest item. An item marked EXACT must not depend solely on an earlier approximate wake, and fallback must be reported as approximate/blocked/failed as appropriate.

### OD06 — P1: one broken migration item can block unrelated delivery recovery

`ReminderRecordStore.migrationIds` includes raw preference keys, while `claimMigration` parses JSON without per-item isolation. The older `all()` skipped malformed rows. `LegacyOrganizerReminders.migrate` has no per-item recovery, and `HaloApplication.awaitOrganizerBootstrap` runs it before `reconcileAll` and setting `organizerRecovered`. One malformed legacy record or one failed target save can therefore prevent all normal organizer callbacks—each awaits this bootstrap—from reaching existing valid organizer delivery.

Retain/quarantine the failing source with an actionable migration issue and retry ownership, while allowing valid existing organizer schedules and other migration items to recover. Do not acknowledge or discard a legacy item whose target is not durable.

`OrganizerAlarmActivity.reload` also lacks a catch around bootstrap/Room reads; the same recovery failure can escape its lifecycle coroutine instead of presenting a nonbusy Retry state. Handle loading failure on this initial surface without falsely saying the alert finished.

Acceptance: valid organizer alarm plus malformed legacy JSON; a mixed legacy set with one storage failure; migration crash after claim and after target save; reopen the alarm activity while bootstrap/read fails. Valid alerts remain operable, failed source data remains recoverable, duplicates stay suppressed, and the activity exposes Retry rather than crashing or spinning indefinitely.

### OD07 — P2: delivery presents a series title instead of the edited occurrence

`OrganizerDeliveryCoordinator.deliverLocked` sends the master `OrganizerRecord` to `port.present`. `OrganizerAlarmActivity.reload` also loads the master record directly. The schedule planner accounts for replacement timing, and `OrganizerRecurrence`/ICS preserve replacement title/details, but the unlocked notification/activity never projects the delivered occurrence's replacement title. A user who renames one moved occurrence receives the series title instead of the reviewed occurrence title.

Resolve presentation from the exact immutable occurrence identity and current version, applying permitted occurrence overrides consistently. Preserve generic redaction before first unlock and while the device is locked; do not copy private display fields into device-encrypted timing storage. [Android Direct Boot storage guidance](https://developer.android.com/privacy-and-security/direct-boot).

Acceptance: rename and move one recurring occurrence, deliver it and open its alert, and verify the edited title. The following ordinary occurrence retains the series title; stale versions cannot supply old text. Repeat the same alert while locked to verify private text is withheld.

## Evidence and stopping boundary

These findings come from inspected source control flow. No current delivery build/test result, rendered/native screenshot, actual notification, sound, Doze or pre-unlock action was produced or inspected for this review. Earlier organizer foundation and network-policy host archives do not validate this new delivery code. Full organizer UI, Library, tools, backup, Screen/widgets and the complete issue-63 acceptance remain pending. This review stops here until the parent supplies the accepted corrections; it assigns no numerical UX/UI score.

## Corrective source disposition

The follow-up inspected only OD01–OD07 mechanisms and `app/src/test/java/com/halo/app/data/everyday/OrganizerDeliveryTest.kt`. The reviewer compared the 30 files in `organizer-delivery-source.json` (captured 19:29:43 UTC) with current files: **30 matched, no mismatches**. The parent reports the coherent `organizer-delivery.log` host run in progress; this review does not claim it passed or execute any build/test/device action.

| Finding | Current source disposition | Associated regression source read |
|---|---|---|
| OD01 | Addressed: locked `schedule` removes the consumed entry before looking for a future plan; locked fire drains other due receipts and rearms each record. | `consumedLockedAlarmDoesNotStrandTheNextAlarm`; existing co-due and bounded native queue cases. |
| OD02 | Partially addressed: `OrganizerInitialDelivery.overdue` preserves authored ordinary/timer deadlines and creates missing normal-process receipts before catch-up. Locked recovery does not yet use it; residual below. | `firstDelayedReconciliationDeliversRecentAlarmAndRecordsAncientTimerMiss`. |
| OD03 | Addressed: locked reconcile/fire/action recheck unlock under the handoff mutex and route to normal recovery only after releasing it. | `callbackWaitingBehindUnlockImportUsesNormalOwnerAndCreatesNoLateJournal`. |
| OD04 | Addressed: mirror scheduling captures the invalidation generation, verifies the Room record and conditionally publishes under the same generation; rejected outcome cleanup revokes only the stale version. | `editDuringMirrorPublicationRevokesOldGenerationWithoutRemovingSuccessor`. |
| OD05 | Addressed: separate bounded alarm-clock, precise and approximate wake lanes preserve precise scheduling independently of approximate work. Platform scheduling returns actual accepted precision, including fallback, and the adapter uses it. | `approximateWakeCannotHideAlarmClockAndActualFallbackIsReturned`; durable-intent/fallback and bounded queue cases. |
| OD06 | Migration isolation addressed: individual failures retain source/journal plus a migration issue while other rows continue. Activity catches read/bootstrap failures and provides Retry. Unknown outcome still uses finished/stopped presentation; residual below. | `malformedLegacyRecordIsRetainedWithoutBlockingValidMigration`; existing claim/retry migration case. The new Activity failure rendering has no matching executed evidence. |
| OD07 | Addressed: `forDelivery` selects replacement fields by exact record/occurrence identity; both normal notification presentation and Activity reload apply it without mutating the series. Direct-boot redaction remains separate. | `renamedMovedOccurrenceUsesReviewedTitleAndPreservesSeriesTitle`. |

### OD02 residual — P1: locked catch-up still skips a recent unrecorded recurrence

`OrganizerInitialDelivery.overdue` is called by the normal coordinator only. `OrganizerLockedDelivery.reconcile` still examines existing mirrored receipts, then invokes the future-only planner. Example: a daily alarm is mirrored before shutdown with a receipt for day 1; the phone reboots and stays locked until one minute after day 3's due time. Locked reconciliation marks the old day-1 receipt MISSED, then schedules day 4, silently skipping the eligible recent day-3 occurrence because it has no receipt. The ordinary corrected coordinator would discover it.

Use the same authored-deadline discovery/classification for locked entries, preserving redaction and consumed identities. Acceptance: later recurring occurrence shortly overdue before first unlock, older-than-window occurrence, moved occurrence, duplicate locked reconciliation, and subsequent unlock import. Recent eligible delivery must occur once; old missed and future schedules must remain truthful.

### OD06 residual — P2: read failure still asserts an inactive alert

`OrganizerAlarmActivity.reload` now catches failure by clearing record/receipt and setting a retry error. The content at lines 58–59 still maps null record/receipt to “This alert is no longer active”, and the face maps it to Stopped. A failed read does not establish that the alarm has ended; its notification may still be active. Retry availability fixes the crash path but not the false terminal receipt.

Keep load failure/unknown distinct from a confirmed missing or inactive alert. Render explicit inability to read the state, retain Retry, and avoid an inactive/stopped claim until a successful read establishes it. Acceptance: inject bootstrap/Room failure while an alert is active, then retry successfully; no false inactive message should appear and correct actions should return after recovery.

No other concrete residual was identified in this scoped corrective pass. Test source has been read, not executed by this reviewer. Rendered/native behavior, complete organizer surfaces, full policy matrix and issue-63 completion remain outside this disposition; no numerical UX/UI score is assigned.

## Locked catch-up and unknown-load follow-up

**OD02 is now addressed in source.** Locked reconciliation invokes `OrganizerInitialDelivery.overdue`, journals its discovered receipts, reloads the current entry and consumes those due receipts before choosing future schedules. The new `lockedRebootDiscoversTodaysUnrecordedRecurrenceAndFailedLoadIsUnknown` regression source creates a daily series whose earlier occurrence is already consumed and whose later occurrence is recently overdue; it expects the later receipt to be FIRED. This directly covers the reported missing-locked-recurrence mechanism. No execution result for that new case is claimed yet.

**OD06 failure presentation is corrected, but Retry-in-flight still needs a state transition.** The Activity now uses `organizerAlertViewState`; an unsuccessful load with null receipt becomes UNAVAILABLE with generic title, unknown status, warning face and Retry. Confirmed missing/inactive state remains FINISHED. However, the actual Retry button clears `error` and invokes `reload`, while `reload` does not set `loaded=false`. Immediately after Retry, the existing null record/receipt and `loaded=true` therefore select FINISHED until the asynchronous read completes. Set LOADING at the start of reload and clear stale errors consistently, then test failure → Retry in flight → successful resolution. This is the remaining branch of the same unknown-versus-finished finding, not a new UI scope.

The reviewer read [organizer-delivery-intermediate-results.json](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/organizer-delivery-intermediate-results.json): **BUILD FAILED in 4m 26s** at the app Kotlin `maxHeight` receiver error; **20 host-android tests in 6 suites passed with 0 failures/errors/skips, and app tests did not run**. The archive reports 30 matching source files for that earlier snapshot. Source now captures the Activity's constrained height explicitly; that is a read correction, not a passing compile claim. New catch-up/unknown-state tests remain outside the earlier passing host-android evidence. No builds, test execution, production edits or phone actions were performed by the reviewer.

### OD06 Retry transition — source closure

The reviewer narrowly confirmed that `OrganizerAlarmActivity.reload()` now sets `loaded = false` synchronously before cancelling or launching the read. The regression sequence explicitly distinguishes UNAVAILABLE → LOADING → resolved FINISHED. Both inspected Activity/test hashes match the amended `organizer-delivery-recovery-source.json`, whose `preCompileCorrection` records the change before app Kotlin compilation.

This addresses the final reported OD06 branch. OD01–OD07 are now source-addressed; no additional review scope or passing build/test claim is introduced. Matching app host results, rendered/native behavior and broader issue-63 acceptance remain pending. No production edit, build, test run or phone action was performed by the reviewer.

## Matching focused delivery/persistence host evidence

The reviewer read [organizer-delivery-cas-results.json](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/organizer-delivery-cas-results.json): **BUILD SUCCESSFUL in 8m 2s; 30 tests in 2 app suites (18 delivery, 12 persistence), 0 failures, 0 errors, 0 skipped; all 31 manifest source files matched.** Matching [JUnit](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/organizer-delivery-cas-junit.zip), [source archive](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/organizer-delivery-cas-source.zip) and `organizer-delivery-cas-source.json` are retained alongside the result.

The preceding [recovery result](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/organizer-delivery-recovery-results.json) remains **BUILD FAILED in 8m 12s: 87 tests, 1 persistence failure, 0 errors/skips**. The parent reports that failure exposed an older-progress receipt regression, corrected by explicit expected-receipt compare-and-set: a clock rollback update is permitted only when the actual current row matches its expected receipt, while legacy older updates remain rejected. The focused 30-test result validates that correction within its stated delivery/persistence scope; it does not turn the preceding 87-test run into a green run. This evidence append does not claim a separate source audit of the receipt change.

This supersedes earlier pending-app-host statements for the matching focused delivery/persistence batch. OD01–OD07 retain their source-addressed disposition with supporting host evidence. No reviewer build, test execution, phone or production operation was performed. Actual Android alert/sound/Doze/pre-unlock behavior, rendered accessibility/localization, full organizer UI and broader issue-63 integration/acceptance remain pending; no overall or numerical UX/UI score is assigned.
