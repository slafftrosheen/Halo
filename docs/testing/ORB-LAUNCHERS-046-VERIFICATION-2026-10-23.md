# Orb launchers — issue #46

Home, Screen and Mini-halo now share a quiet Open Ring launcher. Its configurable
Create, Find, Notebook and Phone shortcuts reveal the existing typed Home Tools
catalog. Notebook selects the existing Work entry. Browsing never executes a
tool, starts a microphone, grants permission, or calls a provider.

The orb opens its chooser while idle. During capture/work its primary action
uses the existing Stop owner; speaking uses the existing interruption path;
pending review opens Activity. A separate labelled disclosure remains reachable.
Actual input capture determines microphone-open copy; requested startup and
recognition have distinct preparation/processing states. Review retains Stop.

Shortcut selection is bounded presentation data in AppearancePreferences, with
independent asynchronous persistence and generation protection. Empty selection
retains All tools. Disclosure starts collapsed and closes on lifecycle stop or
phase change. Narrow, short, large-text, reduced-motion and touch-exploration
layouts use a scrollable native sheet with critical Stop outside scrolling.
Wide layouts use inline shortcuts. Decorative thermal/battery animation limits
do not change menu placement. EN/LV/RU resources reuse the supplied Open Ring.

The implementation adds no executor, voice/session/approval owner, permission
store, overlay service or AOD capability. Mini-halo uses its normal private
activity entry from Settings; its catalog handoff returns to Home and preserves
the existing draft. Screen shortcuts leave Screen mode and reveal the same
catalog. The separate Screen redesign remains issue #37.

## Verification

Source and regression cases were completed and independently reviewed before
execution. The first serial gate passed 210 app cases with zero failures/errors/
skips. Visual inspection then found that thermal animation fallback was also
selecting modal placement. The final change separates actual reduced-motion
preference from the face's motion budget; native tests explicitly distinguish
the inline root from the modal root.

A physical landscape/200% check then exposed an existing Home follow-latest
threshold: a short viewport could not accumulate small upward gestures to reach
the orb. The observer now releases following on any upward scroll and re-arms
at the actual bottom. The existing Latest action and unseen-reply projection
remain authoritative. Three actual Compose short-viewport cases cover repeated
gesture progress, following versus paused reply growth, and re-arming.

The final serial debug assembly and focused app gate passed **213 tests, zero
failures/errors/skips**, with **22 native renders**, in **8m 35s**. The gate uses
one worker, no parallel tasks, native Roborazzi recording and a local 3 GB heap
override. No GitHub Actions ran. The manifest identifies the compiled app tree
and names every class and render; XML and the gate log are included.

Final APK: **288,236,023 bytes**, SHA256
`d77b312b2d3f9a95aba4cab0dfc4c1dade5ef167d766167cfa3decdf998575aa`.
The installed APK hash matched. The binary is not committed.

Evidence: [manifest](artifacts/ORB-LAUNCHERS-046-2026-10-23/manifest.json),
[test XML](artifacts/ORB-LAUNCHERS-046-2026-10-23/tests),
[native renders](artifacts/ORB-LAUNCHERS-046-2026-10-23/renders),
and [named phone captures](artifacts/ORB-LAUNCHERS-046-2026-10-23/device).

## Named phone observations

Samsung SM_G990B2, Android API 36, RU, existing Private mode. The earlier APK,
SHA256 `8adec25904a4eb719c5f1c988c67b82e1d9cbb67a64e11941c7a6befe28ac244`,
was installed and read back with a matching hash. Named observations passed:

- Idle Home orb discovery and Find → canonical Find tools, with no execution.
- Change Find/Phone shortcut selection, force-stop/relaunch, confirm persistence
  and collapsed disclosure, then restore all four original selections exactly.
- Screen orb → Notebook detail in the shared catalog, then return to Home.
- Settings → nearby Halo → Open Mini-halo, then Mini orb → shared Find catalog.

On the final `d77b312b...` APK, Home → Find, Screen → Notebook detail, and the
normal Settings → Mini-halo → Find handoffs passed again. The chooser was
readable at 200% text at 360dp and approximately 412dp (density 419). Confirmed
landscape rotation/200% text reproduced the short viewport and, after one short
upward gesture, exposed the orb disclosure and opened its scrollable chooser.
Captures that remained portrait after a delayed settings change were rejected;
only the rotation-1 hierarchy is accepted for landscape. One transient Android
UIAutomator null-root capture was retried successfully with the app still alive.

Original shortcut selections were restored exactly. Final settings read-back:
physical density 480 with no override, font 1.0, rotation free, auto-rotation 1,
user-rotation 0. No provider question, execution action, paid request or new
permission/spending approval was sent. Existing user data was preserved.

Raw personal Screen/Settings hierarchies and unrelated launcher captures are
excluded from committed evidence. A warm ADB screen-extra attempt stayed on Home
and is not accepted as a Mini entry observation; the normal Settings entry and
its catalog handoff were observed. Android rejected a shell request to the
private Mini activity; no export/permission bypass was added.

Physical voice capture/Stop/barge-in, approval execution, TalkBack operation,
permission interruption, paid/online providers, process death during active
work, physical tablets and the wider API matrix remain unqualified under #43.
Native state fixtures verify presentation and callback ownership, not those
physical behaviors. Source delivery closes #46's implementation only; #43
remains open for release qualification.
