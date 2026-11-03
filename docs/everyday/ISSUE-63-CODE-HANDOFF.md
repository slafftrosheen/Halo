# Issue 63 code handoff

Current correction batch: all ten audit findings have authored production fixes and targeted regression sources. See [ten-finding correction record](/home/pi/Projects/Halo/docs/testing/ISSUE-63-AUDIT-FIXES-2026-11-03.md). Source review and the matching test/build gate are tracked there; the audit and release history below must not be treated as current executable evidence.

Current status, 3 November: all ten audited code/contract findings have production corrections and targeted regression sources in `416a9b302`. The whole-batch independent source review and correction pass are complete. Matching host tests are INTERRUPTED (power instability; deferred by the user); APK, device, real voice and provider acceptance are not yet verified for this code. See the current correction record above for results; the historical results below do not validate this revision.

User-requested navigation follow-up: [Organizer/menu/family-switch implementation](../testing/ISSUE-63-ORGANIZER-MENU-FAMILY-SWITCH-2026-11-03.md) groups the Everyday instruments under Workspace's Organizer and restores a shell-owned, credential-gated Kids/full Halo menu switch. Its test/build/device verification is **NOT_RUN** in the code-first phase.

3 November source audit follow-up: [completion audit](../testing/ISSUE-63-COMPLETION-AUDIT-2026-11-03.md) records conditional-cache, empty-attention briefing and calendar-interchange corrections plus updated privacy regression sources. New tests/builds are **NOT_RUN** by the user's request to implement first on the Raspberry Pi. The historical green results below do not validate that follow-up.

The table below inventories existing A–H owners; it does not certify complete behavior. The historical requirements audit describes `ba177d110`; the current correction record above tracks its resolved source findings and matching verification. Device acceptance remains separate.

| Scope | Implemented owners / delivery |
|---|---|
| A organizer | Persistent events/tasks/subtasks/reminders/alarms/timers, recurrence/DST/zones, conflict/search/edit/undo, delivery receipts, reboot/time recovery, ICS, existing Notes and related Activity/Timeline links. |
| B collectors | Typed registry, passive local adapters, guarded public feed/weather/alerts/ICS, validated starter catalogue, explicit previews/manual city, conditional caching, provenance, budgets/backoff/cancellation and WorkManager. |
| C Your Day | Deterministic offline morning/on-demand/evening/changes, immutable baseline, priorities, freshness/attribution, quiet hours and optional existing speech owner; age-appropriate visibility. |
| D UI5 instruments | Organizer, sources/catalogue/readers/weather, clocks, brief and setup; Halo Zone/Orb ownership, semantic theme/fonts, accessible drafts/recovery, EN/RU/LV/PL. |
| E Screen | Shared real-data faces, stable independent IDs, configuration, arrangement/size/visibility, Apply/Cancel/CAS/Undo, presets and process restore; existing device owner retained. |
| F native widgets | Eight providers, Halo palettes/fonts/contrast, responsive minimum faces with distinct purpose, live host TextClock, independent atomic settings/receipts, privacy-safe output, typed click routing, previews and bounded updates. |
| G AI/voice | Twenty-two closed typed Everyday commands and Screen configuration/query; canonical repositories, central permissions/descriptors/disclosure, exact versions, outcome truth and no uncertain mutation replay. |
| H integration | Room v34/archive v5 compatibility, bounded portable domain/Screen restore and truthful partial completion, source retention/purge, Local/Power owner matrix/transport closure, metadata-only canonical disclosure fences and frozen tests/review evidence. |

Architecture, retention, disclosure and Android limitations: EVERYDAY-INTEGRATION.md, COLLECTORS.md, BRIEFING.md, ANDROID-CONSTRAINTS.md and LOCAL-POWER-MATRIX.md. Detailed history, failed runs and corrective results are retained in IMPLEMENTATION-LEDGER.md and adjacent test artifacts.

Historical independent widget review found no remaining concrete native-widget production defect within its scope. Expert usability/implementation 8.0/10 and visual clarity 9.2/10 on the original rubrics; fresh frames closed compact-identity/localization findings. These are bounded expert ratings for that snapshot, not a whole-product 10/10 or observed launcher/assistive behavior. AUD63-07 identified a Halo Screen touch-persistence gap, corrected in the current source batch; native persistence acceptance remains separate.

Later phone acceptance includes notification/exact/full-screen permission behavior, actual alarm/audio/Doze, launcher placement/resize/background refresh/lock presentation and TalkBack/Switch Access. Human participant sessions remain deferred by the user. Correct the known source gaps and run the matching host/build gate before requesting that acceptance; do not defer missing code to those sessions.

Final release run: PASS, 306/306 tests, zero failures/errors/skips, 5m51s, 3,745 matching frozen source hashes; release-final-results.json and adjacent source/JUnit/render ZIPs. Final APK build: PASS, assembleDebug in 6m17s from code commit `f7efa32f76f6c16dee62a3dac5d9cf0c91fb431d`. Installed with data retained on Xiaomi 23124RA7EO; MainActivity returned Status ok and running PID 22558. APK SHA-256 `923d4f040ddfc061ef2cae0df067920a453ca0514bc5ce698ee8b2bccf21e413`. This is installation/launch evidence only. The phone is now handed to the user; no further automated hardware feature tests were run.


Startup correction: the first installed handoff exposed a missing Everyday built-in manifest and was not ready for testing. Code commit892930a16 packages the complete declaration, aligns Screen's asset and adds a preBuild presence guard. All18 targeted loader/parity/starter/assistant tests pass; exact APK assets are verified. The corrected APK was installed with data retained, and the user confirmed it opens. See STARTUP-MANIFEST-RECOVERY-2026-11-02.md and HALO-STARTUP-MANIFEST-2026-11-02 evidence. Earlier installation/launch evidence must not be read as successful shell startup.
