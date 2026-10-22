# HALO Ink 028 verification update — 2026-10-22

Source revision: `8045542df782bf6dba5b48b64e3188fd30dff35b` (`main`).

## Results

The focused Ink source tests passed on the attached Samsung SM-G990B2:

- `:app:testDebugUnitTest --tests '*InkV2Test' --tests '*CanvasSketchCodecTest'`: 29 tests, 0 failures, 0 errors, 0 skipped.
- `:app:connectedDebugAndroidTest` filtered to `com.halo.app.ink.AndroidInkInputTest`: 10 instrumentation tests, 0 failures, 0 errors, 0 skipped.
- The connected instrumentation task also assembled the debug APK successfully.

The instrumentation tests synthesize Android pointer events. They cover ordered batched samples, cancellation, finger tap fallback, stylus precedence and pointer-ID ordering, hover, eraser and barrel button behavior, mouse secondary-button behavior, and pressure normalization. They validate the adapter's behavior under test-generated events on Android; they do not validate physical pen hardware.

## Qualification still open

No physical pressure-sensitive stylus or eraser was used. No tablet layout, real pen/finger palm contact, rotation, process-death draft recovery, long-session capacity/performance, accessibility, or saved-v1 sketch visual preservation was assessed on a physical pen device. Keep Issue 28 open until an owner/alpha tester qualifies those scenarios on a compatible pen device; no Huion or other model compatibility is claimed.

No CI or GitHub Actions were used.
