# Issue #63 — snoozed alarm deadline across reboot (2026-11-03)

## Scope and status

Code-only review of the existing organizer timing owner. **Production Kotlin, four pure-time JUnit regressions and this record** are committed to main. Gradle, APK build, real clock adjustments, Android AlarmManager delivery, Doze, locked boot and audio are **NOT_RUN** in this GitHub-connected pass. No new scheduler, background worker, alarm permission or tool interface is introduced.

## Defect

`OrganizerDeliveryTiming.latenessMillis` correctly selected `snoozeElapsedDeadlineMs` when the receipt's `snoozeBootId` matched the active boot. After reboot, the fallback comment promised to use the stored `snoozedUntil`, but the implementation always compared the current clock to `receipt.triggerAt`. A restored/partial-failure receipt whose original trigger differed from its persisted snooze deadline could be marked overdue, fired early or discarded as a failed/missed schedule before the snooze expired. This value is consumed by both regular and locked-boot delivery reconciliation, as well as failed snooze retry planning.

## Correction

When the receipt has a snooze time but the saved monotonic clock is from another boot, lateness is computed against `receipt.snoozedUntil`; ordinary unsnoozed deliveries still use `receipt.triggerAt`. Same-boot monotonic precedence is unchanged. The stored occurrence identity, version and approval/scheduling owner remain intact.

## Authored tests — NOT_RUN

`OrganizerDeliveryTimingTest` covers: stale original trigger vs persisted reboot snooze deadline (before/at/after), same-boot clock jump with elapsed-time timing, failed snooze retry before the actual deadline, and unchanged ordinary unsnoozed receipt timing.

Local command:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.halo.app.everyday.organizer.OrganizerDeliveryTimingTest'
```

Additional device acceptance: snooze a real alarm, reboot before the snooze deadline, check no early audible alarm and one delivery near the saved deadline under permitted exact-alarm/notification policy. Repeat with a manual clock adjustment while keeping the same boot and verify user-visible precision limitations. Preserve #63 open until full OS/human acceptance is recorded.
