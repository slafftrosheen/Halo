# Open Halo issues coding pass Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement these source batches. User instruction on2026-10-25 overrides test execution steps: code now; do not build, run tests, poll/reconnect the phone or qualify hardware until the user's directive.

**Goal:** Implement remaining Android work across#55–#62 using the existing owners, then resume deferred verification and issue closure.

**Architecture:** Extend WorkspaceContextSource/WorkspaceContextStore for revision-bound reads; keep CognitiveSession, ToolRegistry and the run journal authoritative for execution. Reuse the existing pack installer, Screen store and Study lesson/session stores. No second database, scheduler, model manager or executor.

**Tech Stack:** Kotlin, coroutines, existing Room/Storage, Compose and current Android host adapters.

**Spec:** GitHub#55–#62 and docs/15-brand/v4/INTERFACE_4_0.md. The user's latest instruction permits coding ahead of the post-alpha qualification gate; it does not turn pending evidence into a PASS.

## Global constraints

- Android first; no CI/Actions, paid fallback, automatic model downloads, extra permissions or cloud acknowledgements.
- Existing typed actions, privacy/Kids checks, exact revisions, approval and replay policies remain authoritative.
- Source implementation, compiled tests and physical acceptance remain separate. Publication continues to follow the prior checked-commit policy; new work stays unverified until testing resumes.
- Preserve checkpoint23's published evidence and the subsequent locale/fixture diagnostic failures. Do not rerun them while testing is deferred.

## Review focus

- A source changed during a suspended read must not enter a turn under an old revision.
- Unknown legacy source provenance stays unknown; no fabricated revision/qualification.
- Saved workflow data cannot grant authority or execute arbitrary code.
- Download completion cannot qualify an offline role or language; the known slow intent candidate remains unqualified.
- Screen/Study presentation must not outlive source access, lock state, cancellation or a newer learner choice.

## Source design choices during the unattended pass

The user explicitly opted out of questions/verification and deferred test execution. Use existing Kotlin/Compose owners and BrandBook controls. Source inspection is a read-only journal projection shared by Activity and a typed query; it does not create a new content cache or execution path. Show literal current titles with recorded-version comparison and an explicit current-source opening path, rather than implying archived content is available. Missing receipts remain unknown coverage. Remote inspection conservatively requires both Workspace and memory sharing; the query does not grant either.

Learning detail is an explicit per-lesson presentation choice in LearningJourney, not a guessed learner level or global personal-memory update. Keep the controls secondary and initially collapsed. Saving the choice neither starts inference nor changes grading or the scheduler. Future narrative requests consume it through the existing tutor gateway while preserving source, help-stage and word limits. The existing local Kids voice field maps explicit final commands to the same StudyViewModel; no extra recognizer or speech owner is added.

## Batches

- [ ]#55/#62: finish the typed mastery metadata localization source and retain the pending geometry-fixture diagnostics. No more validation until directed.
- [ ]#57: add revision-consistent snapshot reads to WorkspaceContextSource, override them with the current Room transaction owner, and consume them in ContextAcquisitionCoordinator. Carry actual source revision in ContextCandidate; add regression sources for mutation, expected-version mismatch and legacy unknowns without running them.
- [ ]#58: inspect the current journal/capability/routine/custom-skill foundations and implement reviewed, bounded workflow persistence/admission through those owners; no shadow executor. Add schema/source/replay regression sources for later execution.
- [ ]#59: extend the current curated candidate/installer readiness presentation using exact artifact evidence. Keep chat, intent and speech role readiness distinct; no downloads, inference or Recommended promotion in this coding pass.
- [ ]#60: extend source-backed Screen continuation/freshness and ambient privacy through LocalScreenStore and existing widget/action owners; preserve layout preview/undo and lock defaults.
- [ ]#61: extend the current LearningJourney/StudyLessonStore progression and existing Kids guards; no new session/scheduler or inferred mastery.
- [ ]#56: reconcile the umbrella checklist with implemented slices and remaining qualification. Close child/umbrella issues only when the final acceptance disposition supports closure.

## Deferred verification

Source progress is recorded in `docs/testing/OPEN-ISSUES-CODING-2026-10-25.md`. These are partial implementation slices, all unverified; the unchecked stage items above remain the complete scope. In particular, saved-procedure hardening is not the complete My Workflows implementation and bundle acquisition is not offline-role qualification.

When the user directs testing: first inspect the complete diff, then run relevant regression sources and one integrated APK gate. Only then deploy to an available unlocked target, verify exact hashes, execute the source/voice/privacy/persistence journeys and publish truthful evidence. Do not use the old2052/375 pass as proof of these newer untested changes.

Retain the user's automated-only preference. Do not ask for human voice/playback verification or wait on it as a new approval gate. Any unmeasured human-dependent evidence stays explicitly unmeasured.
