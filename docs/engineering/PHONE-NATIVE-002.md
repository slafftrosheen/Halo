# PHONE-NATIVE-002 — Android clock lifecycle

Date: 2026-10-13. Based on official AlarmClock API documentation and Halo's existing host-first architecture.

## Implemented
- Typed, provider-independent speech commands for showing alarms/timers, snoozing a ringing alarm, dismissing the next alarm and dismissing expired timers.
- `halo_clock_view` (show operations) and `halo_clock_modify` (clock state-change requests).
- App owns the clock state. Every successful Intent launch is DISPATCHED, never CONFIRMED.
- All modification tools require mandatory user confirmation and never automatically replay.
- The next-alarm dismissal requests `ALARM_SEARCH_MODE_NEXT`; expired timers use `ACTION_DISMISS_TIMER`, available on API 28+.
- The camera, contacts and calendar are outside this scoped batch.

## Important platform limits
- `ACTION_SNOOZE_ALARM` can be a no-op when no alarm is ringing.
- `ACTION_DISMISS_TIMER` without a timer deeplink dismisses **expired timers only**, not active countdowns.
- Clock handlers differ by manufacturer. No assumption of compatibility, persistent changes or success may be made based on dispatch.
- No direct database read of the user's alarms. No broad alarm-delete support.

## Validation pending
Build and tests were **not executed** in this environment. After all native additions land, run the complete local Gradle test suite and physical-device matrix including missing clock handler, approval denial and action truth.

Official reference: https://developer.android.com/reference/android/provider/AlarmClock
