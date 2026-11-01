# Authorized Samsung acceptance

Status: Final bounded phone checks passed; participant and full assistive acceptance remain pending.

The user authorized this phase with “u have the phone.finalise the task”. Device: Samsung SM-G990B2, Android 16, 1080×2340, serial R5CTA0TKLZZ. Installation uses updates and preserves application data. The phone started in Russian, with system font scale 1.0 and no enabled accessibility service.

## Package and final verification

Final P05/P06 package verification passed. `phone-final-host2.log` finished BUILD SUCCESSFUL in 13m 1s: **217 tests / 34 suites / zero failures, errors or skips**, plus app and instrumentation APK assembly. `phone-final-host-results.json`, `phone-final-junit-xml.zip` and `phone-final-capture-manifest.json` preserve 47 matching native host captures and 158 changed-source hashes.

Final app APK: **290,860,864 bytes**, SHA-256 **`01f9d8c407badd602b85399f2ad1bc47bf69aa67ec88e533dd498ca4c42a2673`**. The release copy and installed phone base.apk match. The test APK hash and install/restoration record are in `phone/installed-final-apk.json`. **All 10 native Android tests and all 3 actual MainActivity tests pass** in `phone/native-device-p05-p06.log` and `phone/main-activity-p05-p06.log`. The unchanged Studio test passes all three preview/typing/Cancel/warm-navigation cycles.

Build history is retained: `phone-final-host.log` was stopped before acceptance when the final localization check found the uncatalogued Canvas “Review draft” label; four localized resources now replace it. `studio-empty-host2.log` failed because P06 test source was read against the preceding main compilation, and is diagnostic history, not a passing test result.

The preceding matching host4 snapshot passed 216 tests in 34 suites with 46 captured cases. Its APK SHA-256 was `361ee95689837a110256ebd848fcad141dd4a87eafdbd454215c75bc94fd39d6`; the installed base.apk independently matched. `q01-q16-capture-manifest.json` and archived XML preserve this snapshot. The later P05/P06 results above supersede this historical snapshot for the final source.

## Completed real-app journeys

Screenshots, XML trees and timestamped actions are under `phone/`. Every manual tap used current UI-tree bounds. These are actual MainActivity/system-UI journeys, distinct from synthetic native fixtures.

- Created `HALO-QA-Predictable-20261101` in HaloNotes; typed and saved `HALO-QA saved locally and reopened on the Samsung phone.` Back, reopening, application update and a later process restart retained the exact body and saved state (14–19, 53).
- Exported a real managed TXT copy, cancelled an external Android destination picker and received an explicit no-new-copy result, retried and saved to Downloads, reopened it in Android HTML Viewer, then returned to Halo’s export receipt and original managed output (21–32). Readback of `/sdcard/Download/HALO-QA-Predictable-20261101.txt` matched the synthetic text.
- Continued that TXT source into Study setup, selected the actual source and reached the Text ready state. Background/resume retained the setup. Real system font scale 2.0 recreated the Activity; the setup and selected material remained. This did not generate a provider-dependent lesson (33–40).
- Deliberately pinned Study Material, recreated the Activity at system font scale 2.0, reopened Actions, and used the retained pin to open the current material picker. Restored scale 1.0 and the original empty pin selection (43–48).
- Checked Library and global navigation on host4’s installed APK: Russian Workspace group naming and wrapped identifying metadata/date were visible (49–51).

- Verified P05 New project at normal and real 200% system text. Opened its dialog, typed with the keyboard visible, cancelled above the keyboard, and returned to the empty page. Font scale returned to 1.0 (55–60).
- Created `HALO-QA-Studio-20261101` from the Postcard preset, added a text layer through its actual editor, and applied `HALO QA saved locally`. The UI showed the three-layer local draft. Undo returned to two saved layers; Redo restored three draft layers; Save acknowledged three saved layers (62–75).
- Exported the Studio composition to PNG, inspected its saved 54.9 KB file detail, opened it in Samsung Gallery for this invocation only, and returned to the same saved project (76–80). The raw test artwork intentionally overlaps the added text with the preset title; this is a layer/export check, not a finished design.
- Force-stopped the app only after saving, relaunched Studio, selected the owned project and confirmed its saved three-layer state after cold process recreation (81–83).

The owned synthetic note, TXT outputs, Studio project and PNG are retained for inspection. No existing user document was changed. No provider request, microphone capture, app-data clearing or external publication was performed.

## Native Android and real-Activity checks

On the host4 APK, `phone/native-device-final.log` passed 10 tests: Studio real PNG render/export/reopen and original-byte preservation; Russian 200% layer review; File Tools and continuity owners; Canvas/Workspace operability; Study learner controls; themed orb frames; and Kids target/Back/Help/parent-boundary/keyboard layout. These use synthetic isolated owners and data. They are not a completed real child learning session.

`phone/main-activity-final.log` passed both composer tests (canonical clear versus newer edit and full literal hardware-key input), but the Studio preview test initially failed to find a visible New project command on the empty page. P05 adds the empty-only choice using the existing naming/creation owner. The unchanged test passed on the final package and repeated typing, cancelling and warm Library/Studio navigation three times.

The independent render review found P06: stale Canvas organization previews left Approve enabled despite transaction rejection. The correction disables stale editing/approval, latches source/edge version conflicts, explains Cancel/reopen and scope choice, and tests both selected-card and no-selection whole-board recovery through a durable current-source commit.

## Restoration and limits

The recorded baseline system font scale was restored to 1.0 and the temporary Study pin was removed. Test-owned composer conversations restore the prior conversation in cleanup. Final package verification records scale 1.0 again. `phone/final-diagnostics.json` records no app crash process in the crash buffer since the final regression start. This is not an ANR or performance qualification. Twelve fresh Android-renderer captures are archived in `phone/native-final/` with SHA-256 provenance.

This evidence does not establish population completion percentages, novice comprehension, a human TalkBack/Switch Access session, pre-reader/caregiver acceptance, a provider-enabled lesson, or low-end-phone latency. The full roadmap rubric retains those evidence requirements. An expert assessment of implemented behavior must be reported separately from those unobserved outcomes.

## Supplemental frame sample

A subsequent 20-cycle Actions/Back sample on the same saved QA Studio project and APK completed without changing the process or saved three-layer state. `phone/frame-profile-result.json` and raw `frame-profile-gfxinfo.log` record 5,580 aggregate rendered frames, 246 janky (4.41%), and P50/P90/P95/P99 frame times of 7/9/12/125 ms. The reported MainActivity window separately records 3,603 frames, 120 janky (3.33%), and 6/7/7/73 ms. Do not combine these scopes.

This approximately 96.6-second sample includes ADB/UIAutomator overhead and idle animation. It is neither jank-free nor a measure of touch-to-visible-acknowledgement latency, motion comfort, participant ease or whole-app performance. The user subsequently deferred participant testing until later. No participant outcome is inferred from this automated sample.
