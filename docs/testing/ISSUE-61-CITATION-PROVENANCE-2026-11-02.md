# Issue #61 — citation provenance guard (2026-11-02)

Scope: source-only Study citation and practice-draft integrity. Implementation-only checkpoint on `main`; no Android build, JUnit run, model call or device/voice acceptance was performed in this GitHub-connected pass.

## Defect and fix

Previously `StudyCitationMarkers.citations` rejected missing sources, but `displayText` still rendered `[S1]` as `[source]` when the indexed source had not been supplied. `unknownCount` also treated such references as valid, masking the missing-evidence warning. An excerpt flagged FULL but containing only whitespace could be cited and used as a practice-card source. The JSON and lightweight local-card parsers had the same empty-text gap.

Now one `hasSuppliedMaterial` predicate applies to citation extraction, visible markers, unsupported-marker count and both practice-card grammars. Only actually readable non-MISSING text or an **explicitly supplied visual** can authorize `[source]` and a card's source pointer. Source indexes remain strictly S1–S4. Missing/empty/unselected material is still available to the ordinary unavailable-source UI, but no longer impersonates evidence in generated output.

## Added regression cases (written, NOT_RUN)

- Missing and whitespace-only material: no citations, no visual `[source]` marker, two unsupported-marker counts.
- Missing visual versus explicitly supplied visual: the first is unsupported; the second retains page locator and source identity.
- Structured JSON and local one-line cards: reject nonexistent/blank evidence and retain the explicitly supplied-visual case.

Run locally: `./gradlew :app:testDebugUnitTest --tests 'com.halo.app.study.StudySourcesTest'`. Then run the existing #61 selected-note/source-only passage, explicitly unavailable visual, and review/save/restart flows on the installed APK.

**Open limitation:** Citation identity does not prove the *semantic truth* of a model-written statement. The real October 30 source-only response introducing “surface tension” is still a FAIL. This patch does **not** claim that provider grounding, full #61 acceptance, real spoken journeys or model reliability are solved. Retain #61 open for semantic verification and physical acceptance.
