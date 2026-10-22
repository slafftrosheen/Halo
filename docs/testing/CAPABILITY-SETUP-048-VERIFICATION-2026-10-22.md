# Guided Android capability setup — issue #48

## Implemented behavior

The welcome wizard and Settings use the same Permissions destination and the existing
`SetupProgressStore`. Start enters an optional guided pass; each Continue explains and
launches the current Android request. Skip advances without requesting access. There is
no extra allowance modal when opening a tool, no grant-all claim, and no setup launch on
a Private/Full Power change. Typed chat and local notes do not require completing setup.

The guide persists the current capability, attempts, skips and pending request as one
bounded, versioned storage value. It records a pending attempt before opening Android.
On recreation it waits for the user's Continue rather than replaying an interrupted
request. Android results advance the guide but never store a successful-grant flag.
Every return to the foreground rechecks Android access, including later revocation.
Approximate location is usable and explicitly labelled; it is never shown as precise.

The unrelated global pending-task pill is suppressed on Permissions and the welcome
wizard so it cannot consume the setup viewport at large text. Approval state is not
changed; Activity remains available for reviewing the pending action.

Each special access opens its own explained Settings entry. Opening or returning from
Settings is not a grant. Existing parent-gated navigation protects the Permissions
destination; setup does not change Kids policy, provider profiles, payment consent or
action approvals. No recording or other protected operation is started by setup.

## Installed capability inventory

| Access | Shipped purpose | Request owner |
| --- | --- | --- |
| Microphone | Explicit voice input | Android runtime permission |
| Notifications (API 33+) | Reminders and finished tasks | Android runtime permission |
| Contacts | Requested contact lookup | Android runtime permission |
| Read/write calendar | Read events / approved event creation | Separate runtime requests |
| Camera | Capture and flashlight | Android runtime permission |
| Location | Requested local results | Coarse + fine together on API 31+ |
| Bluetooth | Selected headset/glasses | Nearby permissions, location on older Android |
| Nearby Wi-Fi (API 33+) | Supported device media transfer | Android runtime permission |
| Local network (API 37+) | Configured local server / nearby transfer | Android runtime permission |
| Notification listener | Summaries and approved replies | Existing Android permission checker / Settings |
| Accessibility | Approved screen assistance | Existing Android permission checker / Settings |
| Write system settings | Requested brightness changes | Existing Android permission checker / Settings |
| Notification policy | Requested silent mode | Existing Android permission checker / Settings |
| Overlay | Floating Halo | Android overlay grant / Settings |
| Default assistant (API 29+) | Explicit assistant enrollment | Android role truth / Settings |
| Exact alarms (API 31+) | Exact reminder timing | Existing `ExactAlarmAccessController` |

Capabilities without hardware or a resolvable Settings entry are unavailable and skipped.
Files, photos and backup folders continue using scoped system pickers. No broad storage
or all-files request is introduced. Direct-call permission stays contextual to the
approved call in `NativeDirectCallActivity`; unknown-source installation stays contextual
to opening an APK through the existing installer. These are deliberate action-time
exclusions, not grants implied by setup. No background-location permission is requested.

## Verification

The complete delivery source assembled successfully and passed 33 focused cases, with
no failures, errors or skips: `BUILD SUCCESSFUL in 8m 15s`. No CI/Actions were run.

The delivered APK is 286,402,821 bytes, SHA-256
`154b65ea28aded0fec4a6341e3ea4297e256a6ac6f06c2359eaba7971a0a92f8`.
It was installed with `adb install -r`, launched, and the installed base APK hash was
read back from the phone and matched exactly. Test reports, a compact manifest and
accepted Permissions screenshots are in
[`artifacts/CAPABILITY-SETUP-048-2026-10-22`](artifacts/CAPABILITY-SETUP-048-2026-10-22/manifest.json).

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest `
  --tests 'com.halo.app.ui.shell.GuidedCapabilitySetupTest' `
  --tests 'com.halo.app.ui.shell.CapabilitySetupGuideTest' `
  --tests 'com.halo.app.ui.shell.PermissionsScreenTest' `
  --tests 'com.halo.app.ui.shell.BrandPermissionAbilitiesTest' `
  --tests 'com.halo.app.ui.onboarding.*' `
  --no-daemon --no-parallel --max-workers=1 `
  '-Dorg.gradle.jvmargs=-Xmx2g' '-Dfile.encoding=UTF-8'
```

## Observed phone behavior

Android 15 / API 35, model `23124RA7EO`, 1080 x 2400, original density 440 and font scale
1.0. The existing app was upgraded with `adb install -r`; no app data was cleared. The
installed pre-pill-fix APK had SHA-256
`a42200f9b3f3adc9e66de06d0d2890929a94415d28fdedb9d1fee57ac330bc3c`.

- The Russian Permissions route opened and showed existing grants as allowed. Start
  entered the first missing special capability without launching an Android dialog.
- Force-stop and relaunch preserved the same screen-assistance step and did not open
  Settings or replay an OS request automatically.
- The exact-reminder Settings entry reached Android's `AlarmsAndRemindersAppActivity`.
  Returning without changing access advanced the guide. Accessibility remained disabled;
  the guide did not treat a Settings visit as a successful grant.
- With all available setup requests completed or attempted, the actual Start button
  reported `enabled=false` in the UI hierarchy and the saved-progress explanation showed.
- Revoking the already-granted microphone permission caused the guide to show Declined
  and a Settings repair entry, without re-prompting. The original microphone grant was
  restored and verified with Android; other pre-existing runtime grants stayed intact.
- At 360dp / font scale 2.0, the existing unrelated task pill obscured too much of setup.
  The scoped fix was rebuilt and the final Permissions screenshot has no task pill.
  A separate cold-launch capture confirmed Permissions at 412dp with the disabled Start
  and progress explanation. Density and font scale were restored after capture.
- Some warm configuration-change capture attempts landed on Activity rather than
  Permissions. Those screenshots are excluded from setup evidence. Configuration-change
  route retention and the complete 200% text journey remain qualification work under #43;
  the narrow screenshot demonstrates the pill fix, not every large-text interaction.

These observations cover an existing installation on one API-35 phone. They do not
establish a fresh-install interactive Deny/permanent-denial matrix on Android 13/14,
TalkBack, every locale, or Private/Full Power network traces.

| Qualification | Status |
| --- | --- |
| Source inventory, parent navigation and mode separation review | Reviewed; independent review fixes included |
| Debug APK and focused host regressions | PASS — 33 tests, no failures/errors/skips, final source |
| Existing-grant flow, process restart, special Settings return, microphone revocation/repair | PASS on the API-35 phone for the observations above |
| Android 13/14, fresh-install runtime Deny / permanent denial | NOT TESTED on hardware |
| Process kill/rotation while an OS request is open, then resume | Host pending-request regression passed; NOT TESTED on hardware |
| Russian 412dp normal-text Permissions capture | PASS for the captured existing-grant state |
| Russian 360dp / 200% text | Task-pill suppression observed; complete journey NOT QUALIFIED |
| EN/LV, TalkBack, configuration-change route retention | NOT QUALIFIED on hardware |
| Private/Full Power switching with Android network/permission trace | NOT TESTED on hardware |

Physical acceptance remains part of #43. Host tests and a compiled APK do not establish
those device results. Android's request semantics are documented at
[Request runtime permissions](https://developer.android.com/training/permissions/requesting).
