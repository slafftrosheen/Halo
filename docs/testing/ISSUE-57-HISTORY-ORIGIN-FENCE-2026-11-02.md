# Issue #57 — previous-lesson provenance fence (2026-11-02)

Code-only checkpoint. Device/Gradle/host unit suites, voice, remote provider and real Privacy switching: **NOT_RUN**.

## Failure mode

`AiRouterTutorGateway` previously accepted a prior lesson turn if `recentLessonOrigins` was present but an **empty list**. Kotlin `all {}` over an empty list is true, so empty-provenance historical tutor text could enter the new prompt and, under separately permitted Power execution, become outgoing Workspace context. A history entry naming an object outside the *current* supplied source set could also reach a provider if its individual object permission check happened to allow it. Reproducing and closing either gap must not depend on the UI filtering correctly.

## Source changes

- Historical turns with absent **or empty** origins are omitted, never treated as proof of authorized context.
- A nonempty origin list is checked against the **currently supplied readable text or selected visual** source IDs before prompt construction/provider selection. A foreign/removed/unavailable source produces `Unavailable` with no provider call rather than attributing that old history to the active lesson.
- The preexisting local/remote `permitsText` policies still apply to recognized origins, including a second check before execution. No new mode, provider, persistence owner or auto-refresh is introduced.
- Fresh source revisions remain the responsibility of `StudyViewModel`/`StudyUseCases` sourceVersion guards. This change is a defense-in-depth fence for the gateway, not a replacement for their revision checks.

## Authored tests — NOT_RUN

Four `StudySourcesTest` cases cover empty-origin suppression before an allowed remote dispatch, foreign-origin hard refusal with zero provider requests, an unavailable old source refusing despite a readable sibling, and valid same-source history continuity. Existing `localOnlyLessonHistoryCannotBecomeRemoteContext` and `unknownLegacyHistoryIsOmittedFromNewPrompts` remain relevant.

Local command: `./gradlew :app:testDebugUnitTest --tests 'com.halo.app.study.StudySourcesTest'`.

Remaining issue #57 gates: full stale/permission/replay/error matrix, typed and actual spoken UI routes, correlated Private→cloud observation when relevant, and exact device/runtime evidence. This source patch is **not** a whole-issue PASS. Cross-tool source identity remains version-guarded by the existing canonical owners.
