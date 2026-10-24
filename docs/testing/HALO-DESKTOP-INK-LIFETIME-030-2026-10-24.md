# Desktop Ink input lifetime (#30)

Base: freshly fetched main `34bc271c3bdcf0a15fd484ce459c8a958e263274`,
clean and 0/0 before edits. This is a correction to the gated desktop host source,
not Windows/Linux hardware qualification or a production activation decision.

## Problem and correction

`DesktopPenSource` previously invoked the blocking native reader directly inside
an I/O coroutine. Cancelling the owner scope changed its Job state but did not
call `stopNative`; completion callbacks could not run until that reader exited.
An idle Windows input window or Linux input loop could therefore outlive its
cancelled owner. Natural window/input termination also left the network sender
alive until a separate close or timeout.

The host now installs a cancellation handler around the blocking reader. It
requests stop immediately on cancellation, including cancellation at dispatcher
handoff, and retains the existing exclusive native-owner reservation until the
worker actually exits. Stop remains scoped to the matching native owner, so a
late cleanup cannot stop another instance. Terminal reader cleanup closes the
sender and clears the in-progress local stroke. Completed local recovery files
remain owned by the existing atomic draft store. Production activation stays
closed and no new discovery, pairing, tool or permission operation is introduced.

## Verification

Two host regressions use an actual blocked I/O worker and latches. One proves that
cancellation requests stop before the worker is released, and that the owner Job
cannot complete while that worker is still alive. The other proves the stop
callback itself unblocks the reader and allows cancellation to complete. These
are host coroutine/lifecycle checks, not native JNI or physical pen evidence.

The complete source and regressions preceded a focused serial gate:
`:host-ink:test :app:assembleDebug :app:assembleDebugAndroidTest`, one worker,
3GB, no build cache, no CI. **PASS in 9m54s**: 486 tasks, 23 executed. All
**21 host cases PASS**, zero failures/errors/skips, including both new blocked-I/O
regressions. Both app and test APKs assembled. Unchanged Android/SDK/runtime
regressions were not rerun merely to accumulate a larger count.

Source stamp: `2383adbd3aabda84bf07a389bced41e8e770236f3bac8422b0e7a1a160cb11b7`.
App SHA256: `ed7e8276bce399447917cb07d18d6695ff1133894d52847fac41011a07bb3722`.
Test APK SHA256: `aeb28fca52a9ea9af428d46456428904aec7d296fc15fced26f9ea0a8f51f959`.
The [raw build/XML/source/binary evidence](artifacts/DESKTOP-INK-LIFETIME-030-2026-10-24/)
is retained. The phone disconnected before installation; ADB was empty at the
final check. These latest APKs are **NOT_INSTALLED / NOT_PHONE_TESTED**. The earlier
installed pair and its 35-case pass remain separate historical evidence.

## Remaining original acceptance

Keep #30 open. Actual Windows/Linux target builds, native stop/window/device
behavior, physical input/pressure/eraser, tablet plus second host, same-session
speech and measured latency/loss/memory/battery still require their original
[acceptance protocols](open-issues-2026-10-24/issue-30.md). The prior exact-APK
35-case phone result remains scoped to its recorded APKs and does not prove this
desktop native path.
