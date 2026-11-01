# Predictable Halo evidence

Status: All known local implementation findings are closed; final host and authorized Samsung checks pass. **217 host tests / 34 suites / 47 matching captures; 13 phone tests passed.** The installed APK matches the frozen source/build record. Completed real-app journeys, restoration and limits are in [PHONE-ACCEPTANCE.md](PHONE-ACCEPTANCE.md). Earlier snapshots below remain historical.

The [independent final review](INDEPENDENT-FINAL-IMPLEMENTATION-REVIEW.md) records **8.5/10 UX and 9.4/10 visual clarity**, not 10/10. No remaining known local production defect was identified; the original roadmap still requires participant, human assistive and broader device evidence. Passing automation does not establish those outcomes.

Current APK SHA-256: `01f9d8c407badd602b85399f2ad1bc47bf69aa67ec88e533dd498ca4c42a2673`. Current evidence names start with `phone-final-*`; `phone/installed-final-apk.json` and the two `phone/*p05-p06.log` files identify the matching device run.

This package records implementation on `codex/predictable-ux` from `261e7d681`. The [work register](../../../design/halo-zone5/PREDICTABLE-UX-IMPLEMENTATION.md) maps the supported journeys, actual state owners, recovery and deferred acceptance. The [roadmap](../../../design/halo-zone5/ROADMAP-TO-10-2026-11-01.md) keeps its original scoring and validation requirements.

The historical independent [pre-device review](INDEPENDENT-PRE-DEVICE-REVIEW.md) finds no unresolved local blocker in its reviewed scope and awards **8.3/10 usability and 9.3/10 visual clarity** on the original rubric. This is not 10/10 acceptance. A subsequent requirement audit reopened local work for action pins, command synonyms, Canvas pending-write status and remaining terminology; see the implementation register.

`final-capture-manifest.json` records source hashes, 36 matching native captures under `renders/`, and the final APK: SHA-256 `6989493e6b873afae13699a87f0368dd05d3b9b87cf9ac17d306b712568eccf5`, 292,350,404 bytes. `releases/halo-alpha-debug.apk` matches `app/build/outputs/apk/debug/app-debug.apk`.

## Reading the evidence

Historical baseline and follow-ups are retained as observations of their own source snapshots. Their findings must be read with the later closure map; they are not claims that every historical defect is still present.

- `INDEPENDENT-BASELINE.md`: independent initial implementation review.
- `INDEPENDENT-FOLLOWUP-01.md` through `INDEPENDENT-FOLLOWUP-04-LIFECYCLE.md`: successive bounded reviews and concrete findings.
- `broad-host-results.json` / `all-host.log`: 2,388 tests, one Kids geometry failure on an earlier snapshot. The implementation was corrected without weakening that test.
- `sixth-host-results.json` / `sixth-recovery-shell-host.log`: 144 focused tests passed; debug APK and Android test Kotlin compilation passed.
- `seventh-host-results.json` / `seventh-recovery-shell-host.log`: 148 tests, one preference-fixture isolation failure. The test now creates an isolated owner and checks restoration through a fresh owner; it retains the direct persistence assertion.
- `eighth-cancelled-for-dialog-fix.log`: compilation interrupted deliberately after the review identified lost application font scaling in Android dialogs. It is not a pass.
- `ninth-host-results.json` / `ninth-dialog-host.log`: 153 tests / 25 suites passed, including genuine 200% dialog text-layout assertions and Notes export bytes. Debug assembly and Android test Kotlin compilation passed.
- `tenth-cancelled-for-catalog-override.log`: stopped before acceptance to put the Polish grammar correction in its owning translation override. `eleventh-memory-pressure.log`: interrupted when the long-lived Gradle process reported JVM heap pressure; neither is a passing run. The final script uses one worker and a fresh single-use Gradle process.
- `verify-final-recovery-shell.ps1`: repeatable final focused native/owner checks, debug assembly and Android-test Kotlin compilation. The final regenerated-translation snapshot passed 153 tests / 25 suites, with debug assembly and Android-test Kotlin compilation. See `final-host-results.json`, `final-junit-xml.zip` and `final-recovery-shell-host.log` (13m 10s).

Native screenshots use synthetic data and Robolectric's native renderer. They establish controlled source-level layout and state evidence. They do not establish real Android nonlinear system scaling, physical touch, assistive focus, external provider behavior, performance, participant comprehension or completion rates. Those remain the explicitly deferred phone and participant phase. No device installation or test was performed at that earlier snapshot. The later authorized phone phase is recorded separately.

The final full app unit-test run (`verify-final-all-host.ps1`, `final-all-host.log`) exercised **2,408 tests / 505 suites**, with **one failure and zero errors/skips**. The sole failure was an old test expecting a separate Kids title instead of the new complete title/status summary. `final-broad-host-results.json` and `final-broad-junit-xml.zip` preserve that run (24m 43s).

Only the test was updated: it now checks the exact full summaries and rejects the old lesson name anywhere in the displayed text. **All four Kids binding tests then passed** (`verify-kids-summary.ps1`, `kids-summary-host.log`, `kids-summary-host-results.json`, `kids-summary-junit-xml.zip`; 3m 10s). That record includes the changed test's source hash. All 103 files in the focused source manifest still match their hashes; no production source or APK changed. The broad run is not relabelled as a zero-failure run. No local test failure remains unresolved.

Static verification: `catalog-generation-check.log`, `polish-localization.log` (5,332 strings), `resource-parity.json` (six feature sets, all four languages), and `changed-document-check.log` pass. The latter checks the changed documents only; the wider checker references an absent baseline `PROJECT-DIRECTION.md`.
