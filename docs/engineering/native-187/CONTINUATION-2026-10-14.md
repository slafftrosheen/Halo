# Native capabilities continuation — 2026-10-14

Pulled and verified `45c056b02cc6509428b5c62d5f9f21069e66080f` before editing.

## Changes

- NATIVE-035: fixed Google Tasks reminder-app destination with EN/LV/RU command phrases. Uses the installed package's launcher; absent/disabled handlers reject clearly. Opening the app does not create a reminder. Package identity checked against [Google's published app](https://play.google.com/store/apps/details?id=com.google.android.apps.tasks).
- NATIVE-046: fixed call-log UI handoff using Android's [CallLog contract](https://developer.android.com/reference/android/provider/CallLog.Calls). No call-log read permission, caller-controlled URI, contact extraction, or database access is added. Handler/role failures remain explicit.
- Repeating-alarm days now appear on `halo_set_alarm`, rather than `halo_timer`. Numeric day duplicates cannot bypass uniqueness through different number types. Timer inputs reject fractional/nonfinite/overflowing durations, unknown fields, and oversized labels.
- Native picker/consent ownership uses a monotonic deadline and a 32-request capacity. It rejects overflow without cancelling existing user interactions. All four request-launching tool families handle capacity rejection. Stop and late callbacks cannot resurrect requests.
- Completion receipts retain the latest 64 results rather than erasing every result at capacity. Expired requests are cancelled during cleanup.
- Multi-file import failures report IDs already saved and warn against repeating the whole import. Successful photo import transfers ownership; cancelled pending-photo cleanup runs outside a finishing Activity's cancelled lifecycle scope.
- Corrected 28 stale ledger entries against actual source, including these two new destinations. This is a source audit, not physical acceptance. Ten targets remain explicitly Pending, and other rows retain partial/restricted/setup-dependent conditions.

## Skill assessment

Applied [clean-architecture](C:/Users/slavd/.agents/skills/clean-architecture/SKILL.md), scoped to this continuation: **8/10, five of seven diagnostic rows satisfied**.

| Diagnostic | Assessment |
|---|---|
| Business rules testable without Android/framework | Partial: semantic commands and request ownership are JVM-testable; clock argument guards still require the Android executor fixture |
| Dependencies point inward | Yes: semantic destinations contain no Android imports; platform adapters depend on them |
| Database can change without changing these command rules | Yes: this batch introduces no database coupling |
| Use cases independent of delivery mechanism | Partial: batch-import orchestration and completion formatting remain inside Activity Result delivery |
| Framework confined to outer layers | Yes: Android intents, package lookup and lifecycle delivery remain in app adapters |
| No new component cycles | Yes: no module dependencies added |
| Composition root wires dependencies | Yes: existing host registration and graph remain authoritative; no alternate executor/approval path added |

Specific remaining inversions: move clock argument validation into a pure typed policy consumed by the Android executor; move import sequencing/partial results into a use case returning typed completion data to the Activity. These are architectural follow-ups, not missing permission gates.

## Validation

Regression coverage includes shared EN/LV/RU destination parsing, absent Google Tasks, the call-log UI contract, request-capacity preservation, receipt eviction, Stop/late callbacks, alarm schema placement, numeric day duplicates, and timer bounds.

- 43 language tests and 13 focused app tests passed, zero failures/errors.
- Debug app APK built successfully in 6m45s after repairing the capture-cleanup coroutine boundary and rebuilding the final enum revision.
- Android test APK, including `Native187CapabilityInspectionTest`, built successfully in 50s.
- Built source stamp: `45c056b02cc6+dirty`. Debug APK SHA-256: `8D365B517BE75906120B0474ED90905F8349677985F240FAD60EA6CD21FF227B`.
- Phone `f01fe673` appeared briefly during work, then disconnected. Reconnect and installation retries require the device to be visible. No physical action acceptance or successful new-APK installation is claimed here.
- The new phone inspection test only records advertised handlers and checks the call-log contract; it does not establish successful reminders, actual call-history display, or any external side effect.
